package app.obdgolf.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last few hundred commands and replies exchanged with the adapter.
 * Shared from the Connect screen when an adapter or car does not work, so we can see what it said.
 * It holds no personal data: only ELM327 traffic (which may include the VIN once we read it).
 */
class ElmLog(private val capacity: Int = 400, private val clock: () -> Long = System::currentTimeMillis) {

    data class Entry(val atMs: Long, val kind: Kind, val text: String)

    enum class Kind(val mark: String) { SENT(">"), RECEIVED("<"), ERROR("!"), NOTE("#") }

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun add(kind: Kind, text: String) {
        entries.addLast(Entry(clock(), kind, text))
        while (entries.size > capacity) entries.removeFirst()
    }

    fun note(text: String) = add(Kind.NOTE, text)

    @Synchronized
    fun snapshot(): List<Entry> = entries.toList()

    @Synchronized
    fun clear() = entries.clear()

    /** Plain text for sharing: [header] lines first, then one line per entry. */
    fun export(header: List<String> = emptyList()): String {
        val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
        return buildString {
            header.forEach { appendLine(it) }
            if (header.isNotEmpty()) appendLine()
            snapshot().forEach { appendLine("${fmt.format(Date(it.atMs))} ${it.kind.mark} ${it.text}") }
        }
    }

    companion object {
        /** Makes control characters visible: "41 0C\r\r" becomes "41 0C\r\r" with literal backslashes. */
        fun visible(raw: String): String = raw.replace("\r", "\\r").replace("\n", "\\n").replace("\u0000", "\\0")
    }
}

/** Writes every command, reply and failure to [log] on the way through. */
class LoggingElmIo(private val io: ElmIo, private val log: ElmLog) : ElmIo {
    override suspend fun exchange(command: String, timeoutMs: Long): String {
        log.add(ElmLog.Kind.SENT, command)
        return try {
            io.exchange(command, timeoutMs).also { log.add(ElmLog.Kind.RECEIVED, ElmLog.visible(it)) }
        } catch (e: Exception) {
            log.add(ElmLog.Kind.ERROR, e.message ?: e.javaClass.simpleName)
            throw e
        }
    }

    override fun close() = io.close()
}
