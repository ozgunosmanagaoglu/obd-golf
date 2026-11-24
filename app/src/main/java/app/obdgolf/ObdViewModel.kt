package app.obdgolf

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.obdgolf.bt.BluetoothConnector
import app.obdgolf.bt.PairedDevice
import app.obdgolf.core.CarData
import app.obdgolf.core.DemoElmIo
import app.obdgolf.core.Drive
import app.obdgolf.core.DriveRecorder
import app.obdgolf.core.ElmIo
import app.obdgolf.core.FaultReport
import app.obdgolf.core.ObdSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LinkState {
    data object Disconnected : LinkState
    data class Connecting(val name: String) : LinkState
    data class Connected(val name: String, val adapterVersion: String?) : LinkState
    data class Failed(val message: String) : LinkState
}

sealed interface FaultState {
    data object Idle : FaultState
    data class Busy(val text: String) : FaultState
    data class Ready(val report: FaultReport, val cleared: Boolean = false) : FaultState
    data class Error(val message: String) : FaultState
}

class ObdViewModel(app: Application) : AndroidViewModel(app) {
    private val bt = BluetoothConnector(app)
    private val prefs = app.getSharedPreferences("obd", Context.MODE_PRIVATE)

    private val _data = MutableStateFlow(CarData())
    val data: StateFlow<CarData> = _data.asStateFlow()

    private val _link = MutableStateFlow<LinkState>(LinkState.Disconnected)
    val link: StateFlow<LinkState> = _link.asStateFlow()

    private val _devices = MutableStateFlow<List<PairedDevice>>(emptyList())
    val devices: StateFlow<List<PairedDevice>> = _devices.asStateFlow()

    private val _autoConnect = MutableStateFlow(prefs.getBoolean("auto", true))
    val autoConnect: StateFlow<Boolean> = _autoConnect.asStateFlow()

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
        if (it == null) _faults.value = FaultState.Error("Önce adaptöre bağlan (ya da Bağlantı sekmesinden demo modunu aç).")
    }

    fun readFaults() {
        val s = connectedSession() ?: return
        if (_faults.value is FaultState.Busy) return
        viewModelScope.launch {
            _faults.value = FaultState.Busy("Arıza kodları okunuyor…")
            _faults.value = try {
                FaultState.Ready(s.readFaults())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FaultState.Error("Okunamadı: ${e.message ?: "bağlantı hatası"}")
            }
        }
    }

    fun clearFaults() {
        val s = connectedSession() ?: return
        if (_faults.value is FaultState.Busy) return
        viewModelScope.launch {
            _faults.value = FaultState.Busy("Kodlar siliniyor…")
            _faults.value = try {
                val ok = s.clearFaults()
                if (!ok) FaultState.Error("Araba silme komutunu onaylamadı. Kontak açık, motor kapalı olmalı.")
                else FaultState.Ready(s.readFaults(), cleared = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FaultState.Error("Silinemedi: ${e.message ?: "bağlantı hatası"}")
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
            _autoConnect.value -> prefs.getString("last", null)?.let { addr ->
                _devices.value.firstOrNull { it.address == addr }?.let { connect(it) }
            }
        }
    }

    /** The user said no to a Bluetooth permission: tell them how to fix it. */
    fun onPermissionDenied() {
        if (job == null) {
            _link.value = LinkState.Failed(
                "Bluetooth izni verilmedi. Ayarlar > Uygulamalar > Golf OBD > İzinler'den \"Yakındaki cihazlar\" iznini aç."
            )
        }
    }

    fun refreshDevices() {
        _devices.value = if (bluetoothReady) runCatching { bt.pairedDevices() }.getOrDefault(emptyList()) else emptyList()
    }

    fun connect(device: PairedDevice) {
        prefs.edit().putString("last", device.address).apply()
        start(device.name) { bt.connect(device.address) }
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
        _faults.value = FaultState.Idle
    }

    private fun startDemo() = start("Demo") { DemoElmIo() }

    private fun start(name: String, open: suspend () -> ElmIo) {
        disconnect()
        recorder = DriveRecorder(demo = name == "Demo")
        job = viewModelScope.launch {
            var attempt = 0
            while (true) {
                _link.value = LinkState.Connecting(name)
                try {
                    val s = ObdSession(open()).also { session = it }
                    s.initialize()
                    _link.value = LinkState.Connected(name, s.adapterVersion)
                    attempt = 0
                    s.poll().collect {
                        _data.value = it
                        saveDrive(recorder?.feed(it, System.currentTimeMillis()))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    session?.close()
                    session = null
                    saveDrive(recorder?.finish())
                    _link.value = LinkState.Failed(e.message ?: "Bağlantı hatası")
                    // Car off or out of range: keep retrying quietly, slower each time.
                    attempt++
                    delay((2_000L * attempt).coerceAtMost(15_000L))
                }
            }
        }
    }

    override fun onCleared() = disconnect()
}
