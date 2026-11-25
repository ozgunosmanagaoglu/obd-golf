package app.obdgolf.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import app.obdgolf.core.BleChannel
import app.obdgolf.core.BleProfiles
import app.obdgolf.core.ElmIo
import app.obdgolf.core.GattChar
import app.obdgolf.core.GattService
import app.obdgolf.core.PushTransport
import app.obdgolf.core.TransportElmIo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID

/** A Bluetooth LE device seen while scanning. */
data class BleDevice(val name: String, val address: String) {
    val looksLikeObd: Boolean get() = BleProfiles.looksLikeObd(name)
}

/**
 * Bluetooth LE OBD adapters (Vgate iCar Pro BLE, vLinker BLE, OBDLink CX, Veepeak BLE, clones).
 * These are the only Bluetooth adapters an iPhone can use, so they matter for the iOS version too.
 * BLE adapters are not paired in the phone settings: the app scans for them itself.
 */
@SuppressLint("MissingPermission") // callers check BLUETOOTH_SCAN / BLUETOOTH_CONNECT (or location on Android 11-) first
class BleConnector(private val context: Context) {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter

    /** Emits the growing list of named BLE devices, OBD-looking ones first, until the collector stops. */
    fun scan(): Flow<List<BleDevice>> = callbackFlow {
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
            ?: throw IOException("Bluetooth kapalı")
        val found = linkedMapOf<String, BleDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: result.device.name ?: return
                if (found.put(result.device.address, BleDevice(name, result.device.address)) == null) {
                    trySend(found.values.sortedByDescending { it.looksLikeObd })
                }
            }

            override fun onScanFailed(errorCode: Int) {
                close(IOException("BLE taraması başlamadı (kod $errorCode). Bluetooth'u kapatıp açmayı dene."))
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    suspend fun connect(address: String): ElmIo {
        val a = adapter ?: throw IOException("Bu telefonda Bluetooth yok")
        if (!a.isEnabled) throw IOException("Bluetooth kapalı")
        return GattLink(a.getRemoteDevice(address)).open()
    }

    /** One GATT connection. Android allows one GATT operation at a time, so each waits for its callback. */
    private inner class GattLink(private val device: BluetoothDevice) {
        private val connected = CompletableDeferred<Unit>()
        private val discovered = CompletableDeferred<Int>()
        private var descriptorDone = CompletableDeferred<Int>()
        private var writeDone = CompletableDeferred<Int>()
        private val writeLock = Mutex()
        private var gatt: BluetoothGatt? = null
        private var channel: BleChannel? = null
        private var transport: PushTransport? = null

        private val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    connected.complete(Unit)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connected.completeExceptionally(IOException("BLE adaptöre bağlanılamadı (durum $status). Kontak açık mı?"))
                    transport?.onClosed("Adaptör bağlantısı koptu")
                    g.close()
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) { discovered.complete(status) }

            override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
                descriptorDone.complete(status)
            }

            override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                writeDone.complete(status)
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
                received(c, value)
            }

            @Deprecated("Android 12 and older")
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) received(c, c.value ?: return)
            }
        }

        private fun received(c: BluetoothGattCharacteristic, value: ByteArray) {
            if (c.uuid.toString().equals(channel?.notifyUuid, ignoreCase = true)) transport?.onBytes(value)
        }

        suspend fun open(): ElmIo {
            val g = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                ?: throw IOException("BLE bağlantısı başlatılamadı")
            gatt = g
            try {
                withTimeout(12_000) { connected.await() }
                if (!g.discoverServices()) throw IOException("BLE servisleri okunamadı")
                withTimeout(10_000) { discovered.await() }

                val services = g.services.map { s ->
                    GattService(s.uuid.toString(), s.characteristics.map { GattChar(it.uuid.toString(), it.properties) })
                }
                val ch = BleProfiles.pick(services)
                    ?: throw IOException("Bu BLE cihazı bir OBD adaptörü gibi görünmüyor (OBD servisi yok).")
                channel = ch
                val service = g.getService(UUID.fromString(ch.serviceUuid))
                val writeChar = service.getCharacteristic(UUID.fromString(ch.writeUuid))
                val notifyChar = service.getCharacteristic(UUID.fromString(ch.notifyUuid))

                val t = PushTransport(
                    sendChunk = { bytes -> write(g, writeChar, bytes) },
                    onClose = { runCatching { g.disconnect() }; runCatching { g.close() } },
                )
                transport = t
                enableNotifications(g, notifyChar)
                return TransportElmIo(t)
            } catch (e: Exception) {
                runCatching { g.disconnect() }
                runCatching { g.close() }
                throw if (e is TimeoutCancellationException) IOException("BLE adaptör cevap vermedi. Kontak açık mı, adaptör yakında mı?") else e
            }
        }

        @Suppress("DEPRECATION")
        private suspend fun enableNotifications(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            g.setCharacteristicNotification(c, true)
            val cccd = c.getDescriptor(CCCD) ?: return // some clones notify without it
            val value = if (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            descriptorDone = CompletableDeferred()
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, value) == 0
            } else {
                cccd.value = value
                g.writeDescriptor(cccd)
            }
            if (started) withTimeoutOrNull(3_000) { descriptorDone.await() }
        }

        @Suppress("DEPRECATION")
        private suspend fun write(g: BluetoothGatt, c: BluetoothGattCharacteristic, bytes: ByteArray) = writeLock.withLock {
            val type = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            repeat(3) { attempt ->
                writeDone = CompletableDeferred()
                val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeCharacteristic(c, bytes, type) == 0
                } else {
                    c.writeType = type
                    c.value = bytes
                    g.writeCharacteristic(c)
                }
                if (started) {
                    withTimeoutOrNull(2_000) { writeDone.await() }
                    return@withLock
                }
                // The stack is still busy with the previous packet: wait a little and try again.
                delay(30L * (attempt + 1))
            }
            throw IOException("BLE adaptöre yazılamadı")
        }
    }

    companion object {
        /** Client Characteristic Configuration descriptor: turns notifications on. */
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
