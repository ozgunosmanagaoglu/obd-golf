package app.obdgolf.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import app.obdgolf.core.ElmIo
import app.obdgolf.core.StreamElmIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

data class PairedDevice(val name: String, val address: String) {
    /** Names the Vgate and most ELM327 clones advertise. */
    val looksLikeObd: Boolean
        get() = listOf("OBD", "VGATE", "ICAR", "ELM", "V-LINK").any { name.uppercase().contains(it) }
}

/** Classic Bluetooth (SPP) link to an ELM327 adapter such as the Vgate iCar Pro BT 3.0. */
@SuppressLint("MissingPermission") // callers check BLUETOOTH_CONNECT first
class BluetoothConnector(context: Context) {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter

    val isAvailable: Boolean get() = adapter != null
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    fun pairedDevices(): List<PairedDevice> =
        adapter?.bondedDevices.orEmpty()
            .map { PairedDevice(it.name ?: it.address, it.address) }
            .sortedByDescending { it.looksLikeObd }

    suspend fun connect(address: String): ElmIo = withContext(Dispatchers.IO) {
        val a = adapter ?: throw IOException("Bu telefonda Bluetooth yok")
        val device = a.getRemoteDevice(address)
        // Stopping a running scan makes the connection faster and more reliable, but on
        // Android 12+ it needs BLUETOOTH_SCAN. Without it we just skip this step.
        try {
            if (a.isDiscovering) a.cancelDiscovery()
        } catch (_: SecurityException) {
        }
        val socket: BluetoothSocket = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
        } catch (e: IOException) {
            // Some cheap adapters only accept an insecure socket.
            device.createInsecureRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
        } catch (e: SecurityException) {
            throw IOException("Bluetooth izni yok. Ayarlar > Uygulamalar > Golf OBD > İzinler'den \"Yakındaki cihazlar\" iznini aç.")
        }
        StreamElmIo(socket.inputStream, socket.outputStream) { runCatching { socket.close() } }
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
