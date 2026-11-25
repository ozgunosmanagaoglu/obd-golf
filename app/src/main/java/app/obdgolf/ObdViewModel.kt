package app.obdgolf

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.obdgolf.ble.BleConnector
import app.obdgolf.ble.BleDevice
import app.obdgolf.bt.BluetoothConnector
import app.obdgolf.bt.BluetoothPermissionException
import app.obdgolf.bt.PairedDevice
import app.obdgolf.core.CarData
import app.obdgolf.core.DemoElmIo
import app.obdgolf.core.Drive
import app.obdgolf.core.DriveRecorder
import app.obdgolf.core.ElmIo
import app.obdgolf.core.ElmLog
import app.obdgolf.core.LoggingElmIo
import app.obdgolf.core.FaultReport
import app.obdgolf.core.Lang
import app.obdgolf.core.Msg
import app.obdgolf.core.Str
import app.obdgolf.core.Strings
import app.obdgolf.core.toMsg
import app.obdgolf.core.ObdSession
import app.obdgolf.core.PressureUnit
import app.obdgolf.core.SpeedUnit
import app.obdgolf.core.TempUnit
import app.obdgolf.core.Units
import app.obdgolf.core.VehicleInfo
import app.obdgolf.core.WifiAddress
import app.obdgolf.wifi.WifiConnector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Link name of the built-in demo adapter (shown as is in every language). */
const val DEMO = "Demo"

sealed interface LinkState {
    data object Disconnected : LinkState
    data class Connecting(val name: String) : LinkState
    data class Connected(val name: String, val adapterVersion: String?) : LinkState
    data class Failed(val message: Msg) : LinkState
}

sealed interface FaultState {
    data object Idle : FaultState
    data class Busy(val text: Msg) : FaultState
    data class Ready(val report: FaultReport, val cleared: Boolean = false) : FaultState
    data class Error(val message: Msg) : FaultState
}

class ObdViewModel(app: Application) : AndroidViewModel(app) {
    private val bt = BluetoothConnector(app)
    private val wifi = WifiConnector(app)
    private val ble = BleConnector(app)

    /** Adapter traffic, shared from the Connect screen when something does not work. */
    val log = ElmLog()
    private val prefs = app.getSharedPreferences("obd", Context.MODE_PRIVATE)

    private val _data = MutableStateFlow(CarData())
    val data: StateFlow<CarData> = _data.asStateFlow()

    private val _link = MutableStateFlow<LinkState>(LinkState.Disconnected)
    val link: StateFlow<LinkState> = _link.asStateFlow()

    /** The connected car as read from its VIN; null when not connected or the car has no VIN. */
    private val _vehicle = MutableStateFlow<VehicleInfo?>(null)
    val vehicle: StateFlow<VehicleInfo?> = _vehicle.asStateFlow()

    private val _devices = MutableStateFlow<List<PairedDevice>>(emptyList())
    val devices: StateFlow<List<PairedDevice>> = _devices.asStateFlow()

    private val _autoConnect = MutableStateFlow(prefs.getBoolean("auto", true))
    val autoConnect: StateFlow<Boolean> = _autoConnect.asStateFlow()

    /** Address of the Wi-Fi adapter, editable on the Connect screen. */
    private val _wifiAddress = MutableStateFlow(
        prefs.getString("wifi_addr", null)?.let(WifiAddress::parse) ?: WifiAddress.DEFAULT
    )
    val wifiAddress: StateFlow<WifiAddress> = _wifiAddress.asStateFlow()

    private val _bleDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val bleDevices: StateFlow<List<BleDevice>> = _bleDevices.asStateFlow()
    private val _bleScanning = MutableStateFlow(false)
    val bleScanning: StateFlow<Boolean> = _bleScanning.asStateFlow()
    /** Last scan problem or result note shown under the BLE list, null when there is nothing to say. */
    private val _bleStatus = MutableStateFlow<Msg?>(null)
    val bleStatus: StateFlow<Msg?> = _bleStatus.asStateFlow()
    private var scanJob: Job? = null

    /** Chosen language, or null to follow the phone. */
    private val _language = MutableStateFlow(Lang.fromCode(prefs.getString("lang", null)))
    val language: StateFlow<Lang?> = _language.asStateFlow()

    /** Texts in the chosen language, or the phone's when it is one we have (English otherwise). */
    fun strings(lang: Lang? = _language.value): Strings = Strings(lang ?: Lang.forLocale(Locale.getDefault()))

    fun setLanguage(lang: Lang?) {
        _language.value = lang
        prefs.edit().putString("lang", lang?.code).apply()
    }

    /** Display units; first launch picks them from the phone's country (mph/°F in the US, mph in the UK). */
    private val _units = MutableStateFlow(loadUnits())
    val units: StateFlow<Units> = _units.asStateFlow()

    private fun loadUnits(): Units {
        val d = Units.defaultFor(Locale.getDefault().country)
        fun <E : Enum<E>> pick(key: String, all: List<E>, def: E) = all.firstOrNull { it.name == prefs.getString(key, null) } ?: def
        return Units(
            speed = pick("unit_speed", SpeedUnit.entries, d.speed),
            temp = pick("unit_temp", TempUnit.entries, d.temp),
            pressure = pick("unit_pressure", PressureUnit.entries, d.pressure),
        )
    }

    fun setUnits(u: Units) {
        _units.value = u
        prefs.edit().putString("unit_speed", u.speed.name).putString("unit_temp", u.temp.name)
            .putString("unit_pressure", u.pressure.name).apply()
    }

    private val _demo = MutableStateFlow(prefs.getBoolean("demo", false))
    val demo: StateFlow<Boolean> = _demo.asStateFlow()

    private val store = DriveStore(app)
    private var recorder: DriveRecorder? = null

    private val _drives = MutableStateFlow<List<Drive>>(emptyList())
    val drives: StateFlow<List<Drive>> = _drives.asStateFlow()

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { _drives.value = store.all() }
    }

    fun deleteDrive(d: Drive) {
        store.delete(d.startMs)
        _drives.value = _drives.value.filterNot { it.startMs == d.startMs }
    }

    private fun saveDrive(d: Drive?) {
        if (d == null) return
        runCatching { store.save(d) }
        _drives.value = (listOf(d) + _drives.value).sortedByDescending { it.startMs }
    }

    private var job: Job? = null
    private var session: ObdSession? = null

    private val _faults = MutableStateFlow<FaultState>(FaultState.Idle)
    val faults: StateFlow<FaultState> = _faults.asStateFlow()

    private fun connectedSession(): ObdSession? = session.takeIf { _link.value is LinkState.Connected }.also {
        if (it == null) _faults.value = FaultState.Error(Msg.of(Str.FAULT_CONNECT_FIRST))
    }

    fun readFaults() {
        val s = connectedSession() ?: return
        if (_faults.value is FaultState.Busy) return
        viewModelScope.launch {
            _faults.value = FaultState.Busy(Msg.of(Str.READING_CODES))
            _faults.value = try {
                FaultState.Ready(s.readFaults())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FaultState.Error(Msg.of(Str.READ_FAILED, e.toMsg()))
            }
        }
    }

    fun clearFaults() {
        val s = connectedSession() ?: return
        if (_faults.value is FaultState.Busy) return
        viewModelScope.launch {
            _faults.value = FaultState.Busy(Msg.of(Str.CLEARING_CODES))
            _faults.value = try {
                val ok = s.clearFaults()
                if (!ok) FaultState.Error(Msg.of(Str.CLEAR_NOT_CONFIRMED))
                else FaultState.Ready(s.readFaults(), cleared = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FaultState.Error(Msg.of(Str.CLEAR_FAILED, e.toMsg()))
            }
        }
    }

    val bluetoothReady: Boolean get() = bt.isAvailable && bt.isEnabled

    /** Called once permissions are granted and on app start. */
    fun onReady() {
        refreshDevices()
        if (job != null) return
        when {
            _demo.value -> startDemo()
            _autoConnect.value -> prefs.getString("last", null)?.let { last ->
                if (last.startsWith(BLE_PREFIX)) {
                    connectBle(BleDevice(prefs.getString("ble_name", null) ?: "BLE adapter", last.removePrefix(BLE_PREFIX)))
                } else if (last.startsWith(WIFI_PREFIX)) {
                    WifiAddress.parse(last.removePrefix(WIFI_PREFIX))?.let { connectWifi(it) }
                } else {
                    _devices.value.firstOrNull { it.address == last }?.let { connect(it) }
                }
            }
        }
    }

    private val _permissionMissing = MutableStateFlow(false)
    /** True while a "Nearby devices" permission is missing; the Connect screen then shows an "Allow" button. */
    val permissionMissing: StateFlow<Boolean> = _permissionMissing.asStateFlow()

    /** The activity re-checks permissions on start, on resume and after the system dialog. */
    fun setPermissionMissing(missing: Boolean) {
        _permissionMissing.value = missing
        if (missing && job == null) {
            _link.value = LinkState.Failed(Msg.of(Str.BT_PERMISSION_DENIED, Str.ALLOW))
        } else if (!missing && _link.value is LinkState.Failed && job == null) {
            _link.value = LinkState.Disconnected
        }
    }

    fun refreshDevices() {
        _devices.value = if (bluetoothReady) runCatching { bt.pairedDevices() }.getOrDefault(emptyList()) else emptyList()
    }

    fun connect(device: PairedDevice) {
        prefs.edit().putString("last", device.address).apply()
        start(device.name) { bt.connect(device.address) }
    }

    fun connectWifi(address: WifiAddress) {
        _wifiAddress.value = address
        prefs.edit().putString("wifi_addr", address.toString()).putString("last", WIFI_PREFIX + address).apply()
        start("Wi-Fi $address") { wifi.connect(address) }
    }

    /** Looks for BLE adapters for 8 seconds; they are not paired in the phone settings. */
    fun scanBle() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            _bleScanning.value = true
            _bleStatus.value = null
            try {
                kotlinx.coroutines.withTimeoutOrNull(8_000) { ble.scan().collect { _bleDevices.value = it } }
                if (_bleDevices.value.isEmpty()) {
                    _bleStatus.value = Msg.of(Str.NO_BLE_FOUND)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _bleStatus.value = e.toMsg(Str.BLE_SCAN_FAILED)
            } finally {
                _bleScanning.value = false
            }
        }
    }

    fun setBleStatus(text: Msg?) { _bleStatus.value = text }

    fun connectBle(device: BleDevice) {
        scanJob?.cancel()
        prefs.edit().putString("last", BLE_PREFIX + device.address).putString("ble_name", device.name).apply()
        start(device.name) { ble.connect(device.address) }
    }

    /** Plain-text report for support: app and phone info, current link, then the adapter log. */
    fun exportLog(): String {
        val app = getApplication<Application>()
        val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
        return log.export(listOf(
            "Revora $version",
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}",
            "Link: ${_link.value}",
        ))
    }

    fun setDemo(on: Boolean) {
        _demo.value = on
        prefs.edit().putBoolean("demo", on).apply()
        disconnect()
        if (on) startDemo()
    }

    fun setAutoConnect(on: Boolean) {
        _autoConnect.value = on
        prefs.edit().putBoolean("auto", on).apply()
    }

    fun disconnect() {
        saveDrive(recorder?.finish())
        recorder = null
        job?.cancel()
        job = null
        session?.close()
        session = null
        _link.value = LinkState.Disconnected
        _vehicle.value = null
        _faults.value = FaultState.Idle
    }

    private fun startDemo() = start(DEMO) { DemoElmIo() }

    private fun start(name: String, open: suspend () -> ElmIo) {
        disconnect()
        recorder = DriveRecorder(demo = name == DEMO)
        job = viewModelScope.launch {
            var attempt = 0
            while (true) {
                _link.value = LinkState.Connecting(name)
                try {
                    log.note("Connecting: $name")
                    val s = ObdSession(LoggingElmIo(open(), log)).also { session = it }
                    s.initialize()
                    log.note("Connected: ${s.adapter.label}; supported PIDs: " +
                        s.supported.all.joinToString(" ") { "%02X".format(it) })
                    _vehicle.value = s.vehicle
                    s.vehicle?.let { log.note("Vehicle: ${it.label}, VIN ${ElmLog.maskVin(it.vin)}") }
                    _link.value = LinkState.Connected(name, s.adapter.label)
                    attempt = 0
                    s.poll().collect {
                        _data.value = it
                        saveDrive(recorder?.feed(it, System.currentTimeMillis()))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: BluetoothPermissionException) {
                    // Retrying cannot help until the user grants the permission.
                    session?.close()
                    session = null
                    saveDrive(recorder?.finish())
                    recorder = null
                    _permissionMissing.value = !hasBluetoothPermissions()
                    _link.value = LinkState.Failed(e.toMsg(Str.NO_BT_PERMISSION))
                    job = null
                    return@launch
                } catch (e: Exception) {
                    log.add(ElmLog.Kind.ERROR, e.message ?: e.javaClass.simpleName)
                    session?.close()
                    session = null
                    saveDrive(recorder?.finish())
                    _link.value = LinkState.Failed(e.toMsg())
                    // Car off or out of range: keep retrying quietly, slower each time.
                    attempt++
                    delay((2_000L * attempt).coerceAtMost(15_000L))
                }
            }
        }
    }

    private fun hasBluetoothPermissions(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN).all {
                ContextCompat.checkSelfPermission(getApplication(), it) == PackageManager.PERMISSION_GRANTED
            }

    override fun onCleared() = disconnect()
}

/** Marks a Wi-Fi adapter in the "last" preference (Bluetooth ones are stored by MAC address). */
private const val WIFI_PREFIX = "wifi:"

/** Marks a Bluetooth LE adapter in the "last" preference. */
private const val BLE_PREFIX = "ble:"
