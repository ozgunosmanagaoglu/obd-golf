package app.obdgolf.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Sends one command to an ELM327 and returns its raw reply (everything before the '>' prompt). */
interface ElmIo {
    suspend fun exchange(command: String, timeoutMs: Long = 2_000): String
    fun close()
}

/** ELM327 over any byte stream pair, e.g. a Bluetooth RFCOMM socket. */
class StreamElmIo(
    private val input: InputStream,
    private val output: OutputStream,
    private val onClose: () -> Unit = {},
) : ElmIo {

    override suspend fun exchange(command: String, timeoutMs: Long): String = withContext(Dispatchers.IO) {
        // Drop anything left over from an earlier, timed-out command.
        while (input.available() > 0) input.read()

        output.write("$command\r".toByteArray(Charsets.US_ASCII))
        output.flush()

        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            if (input.available() > 0) {
                val b = input.read()
                if (b < 0) throw IOException("Adapter closed the connection")
                val c = b.toChar()
                if (c == '>') break
                sb.append(c)
            } else {
                if (System.currentTimeMillis() > deadline) throw IOException("Timeout waiting for reply to $command")
                Thread.sleep(5)
            }
        }
        sb.toString()
    }

    override fun close() = onClose()
}
