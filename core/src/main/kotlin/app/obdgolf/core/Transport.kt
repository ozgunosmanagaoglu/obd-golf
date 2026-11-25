package app.obdgolf.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Moves raw bytes to and from an OBD adapter and knows nothing about ELM327 commands.
 * One implementation per kind of link: classic Bluetooth (SPP), Bluetooth LE (GATT), Wi-Fi (TCP).
 * [TransportElmIo] turns any of them into an [ElmIo].
 */
interface Transport {
    suspend fun write(data: ByteArray)

    /**
     * Returns the bytes that arrived, waiting up to [timeoutMs] for the first ones.
     * An empty array means nothing arrived in time. Throws [IOException] once the link is gone.
     * With [timeoutMs] = 0 it only returns what is already waiting.
     */
    suspend fun read(timeoutMs: Long): ByteArray

    fun close()
}

/**
 * A [Transport] over a stream pair: a classic Bluetooth RFCOMM socket or a Wi-Fi TCP socket.
 * Polls [InputStream.available] so a read never blocks past its timeout.
 */
class StreamTransport(
    private val input: InputStream,
    private val output: OutputStream,
    private val onClose: () -> Unit = {},
) : Transport {

    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        output.write(data)
        output.flush()
    }

    override suspend fun read(timeoutMs: Long): ByteArray = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val n = input.available()
            if (n > 0) {
                val buf = ByteArray(n)
                val got = input.read(buf)
                if (got < 0) throw IOException("Adaptör bağlantıyı kapattı")
                return@withContext buf.copyOf(got)
            }
            if (System.currentTimeMillis() >= deadline) return@withContext ByteArray(0)
            delay(5)
        }
        @Suppress("UNREACHABLE_CODE")
        ByteArray(0)
    }

    override fun close() = onClose()
}
