package app.obdgolf.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import app.obdgolf.core.ElmIo
import app.obdgolf.core.StreamTransport
import app.obdgolf.core.TransportElmIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.obdgolf.core.LocalizedIOException
import app.obdgolf.core.Str
import java.io.IOException
import java.util.UUID

data class PairedDevice(val name: String, val address: String) {
    /** Names the Vgate and most ELM327 clones advertise. */
    val looksLikeObd: Boolean
        get() = listOf("OBD", "VGATE", "ICAR", "ELM", "V-LINK").any { name.uppercase().contains(it) }
}

/** Android blocked the connection because a "Nearby devices" permission is missing. */
class BluetoothPermissionException(val details: String) : LocalizedIOException(Str.BT_BLOCKED, details)

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
        val a = adapter ?: throw LocalizedIOException(Str.NO_BLUETOOTH)
        val device = a.getRemoteDevice(address)
        // Stopping a running scan makes the connection faster and more reliable, but on
        // Android 12+ it needs BLUETOOTH_SCAN. Without it we just skip this step.
        try {
            if (a.isDiscovering) a.cancelDiscovery()
        } catch (_: SecurityException) {
        }
        // Try the normal way first, then the ways cheap ELM327 clones sometimes need.
        // Every failure is kept so the screen can show what Android really said.
        val attempts: List<Pair<String, () -> BluetoothSocket>> = listOf(
            "secure" to { device.createRfcommSocketToServiceRecord(SPP_UUID) },
            "insecure" to { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
            "channel 1" to {
                device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, 1) as BluetoothSocket
            },
        )
        val errors = mutableListOf<Throwable>()
        var socket: BluetoothSocket? = null
        for ((label, make) in attempts) {
            var s: BluetoothSocket? = null
            try {
                s = make()
                s.connect()
                socket = s
                break
            } catch (e: Exception) {
                runCatching { s?.close() }
                val cause = (e as? java.lang.reflect.InvocationTargetException)?.targetException ?: e
                errors += IOException("$label: ${cause.javaClass.simpleName}: ${cause.message}", cause)
            }
        }
        val sock = socket ?: run {
            val details = errors.joinToString("\n") { it.message.orEmpty() }
            if (errors.all { it.cause is SecurityException }) throw BluetoothPermissionException(details)
            throw LocalizedIOException(Str.BT_CONNECT_FAILED, details)
        }
        TransportElmIo(StreamTransport(sock.inputStream, sock.outputStream) { runCatching { sock.close() } })
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
