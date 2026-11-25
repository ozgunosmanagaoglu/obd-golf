package app.obdgolf.core


/** Sends one command to an ELM327 and returns its raw reply (everything before the '>' prompt). */
interface ElmIo {
    suspend fun exchange(command: String, timeoutMs: Long = 2_000): String
    fun close()
}

/** The adapter did not finish its reply (no '>' prompt) in time. */
class ElmTimeoutException(val command: String) : LocalizedIOException(Str.ADAPTER_TIMEOUT, command)

/**
 * ELM327 command/reply framing over any [Transport]: write "command\r", collect bytes until '>'.
 * Works the same whether the bytes come from a socket stream or from BLE notifications
 * split into 20-byte pieces.
 */
class TransportElmIo(
    private val transport: Transport,
    private val clock: () -> Long = System::currentTimeMillis,
) : ElmIo {

    override suspend fun exchange(command: String, timeoutMs: Long): String {
        // Drop anything left over from an earlier, timed-out command.
        while (transport.read(0).isNotEmpty()) Unit

        transport.write("$command\r".toByteArray(Charsets.US_ASCII))

        val sb = StringBuilder()
        val deadline = clock() + timeoutMs
        while (true) {
            val left = deadline - clock()
            if (left <= 0) throw ElmTimeoutException(command)
            for (b in transport.read(left)) {
                val c = (b.toInt() and 0xFF).toChar()
                when (c) {
                    '>' -> return sb.toString()
                    '\u0000' -> Unit // some clones pad replies with NUL bytes
                    else -> sb.append(c)
                }
            }
        }
    }

    override fun close() = transport.close()
}
