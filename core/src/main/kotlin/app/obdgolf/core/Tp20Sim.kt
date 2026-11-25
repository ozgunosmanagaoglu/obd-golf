package app.obdgolf.core

/**
 * A pretend VW TP2.0 car behind an ELM327, for the demo and tests. Handles the adapter settings
 * the channel needs (ATH, ATCAF, ATSH, ATCRA) and answers measuring block reads of [units] from [blocks]:
 * unit address → group → the (type, a, b) bytes after "61 GG". Unknown groups get a negative reply.
 */
class Tp20Sim(private val units: Set<Int>, private val blocks: (unit: Int, group: Int) -> IntArray?) {
    var raw = false
        private set
    var header = "7DF"
        private set
    private var headers = false
    private var unit = -1
    private var ecuSeq = 0
    private var open = false

    /** Null when the command is not ours (the normal OBD fake answers it). */
    fun exchange(command: String): String? {
        when {
            command == "ATCAF0" -> { raw = true; return "OK\r" }
            command == "ATCAF1" -> { raw = false; return "OK\r" }
            command == "ATH1" -> { headers = true; return "OK\r" }
            command == "ATH0" -> { headers = false; return "OK\r" }
            command.startsWith("ATSH") -> { header = command.removePrefix("ATSH"); return "OK\r" }
            command.startsWith("ATCRA") -> return "OK\r"
        }
        if (!raw) return null
        val bytes = command.chunked(2).map { it.toIntOrNull(16) ?: return "?\r" }
        fun line(canId: Int, data: List<Int>) = "%03X".format(canId) + data.joinToString("") { "%02X".format(it) } + "\r"
        if (header == "200") {
            if (bytes.size == 7 && bytes[1] == 0xC0 && bytes[0] in units) {
                unit = bytes[0]
                ecuSeq = 0
                open = true
                return line(0x200 + unit, listOf(0x00, 0xD0, 0x00, 0x03, 0x40, 0x07, 0x01))
            }
            return "NO DATA\r"
        }
        if (header != "740" || !open) return "NO DATA\r"
        val op = bytes[0] and 0xF0
        return when {
            bytes[0] == 0xA0 -> line(0x300, listOf(0xA1, 0x0F, 0x8A, 0xFF, 0x4A, 0xFF))
            bytes[0] == 0xA8 -> { open = false; "NO DATA\r" }
            op == 0xB0 -> "NO DATA\r"
            op == 0x10 -> {
                val payload = bytes.drop(3)
                val answer = when (payload.firstOrNull()) {
                    0x10 -> intArrayOf(0x50, 0x89)
                    0x21 -> blocks(unit, payload[1])?.let { intArrayOf(0x61, payload[1]) + it } ?: intArrayOf(0x7F, 0x21, 0x31)
                    else -> intArrayOf(0x7F, payload.firstOrNull() ?: 0, 0x11)
                }
                val frames = Tp20.dataFrames(answer, ecuSeq)
                ecuSeq = (ecuSeq + frames.size) and 0x0F
                val ack = line(0x300, listOf(0xB0 or (((bytes[0] and 0x0F) + 1) and 0x0F)))
                ack + frames.joinToString("") { "300$it\r" }
            }
            else -> "NO DATA\r"
        }
    }
}
