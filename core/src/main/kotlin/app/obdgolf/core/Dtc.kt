package app.obdgolf.core

/** Which list a fault code came from. */
enum class DtcKind(val labelKey: Str) {
    STORED(Str.DTC_STORED),         // mode 03: confirmed, usually lights the engine lamp
    PENDING(Str.DTC_PENDING),       // mode 07: seen once, not confirmed yet
    PERMANENT(Str.DTC_PERMANENT),   // mode 0A: only the car itself can clear these
}

data class Dtc(val code: String, val kind: DtcKind) {
    val manufacturerSpecific: Boolean get() = DtcInfo.isManufacturerSpecific(code)
}

/** Engine lamp state from mode 01 PID 01. */
data class MilStatus(val milOn: Boolean, val dtcCount: Int)

/** Engine data the ECU stored the moment the code was set (mode 02, frame 0). */
data class FreezeFrame(
    val dtc: String?,
    val rpm: Double? = null,
    val speedKmh: Double? = null,
    val coolantC: Double? = null,
    val loadPct: Double? = null,
    val throttlePct: Double? = null,
    val intakeC: Double? = null,
)

data class FaultReport(
    val mil: MilStatus?,
    val codes: List<Dtc>,
    val freezeFrame: FreezeFrame?,
    val readAtMs: Long,
    /** Emission self-tests from the same 0101 reply; null when the car did not report them. */
    val readiness: Readiness? = null,
)

object DtcParser {
    /**
     * Turns the reply to mode 03 / 07 / 0A into codes.
     * Handles the CAN format (count byte after the header, maybe split across "0:", "1:" lines)
     * and the older format (three codes per line, no count byte). Several ECUs may answer
     * (engine and DSG on a Golf), so every message is read and duplicates are removed.
     */
    fun parse(raw: String, mode: String): List<String> {
        val header = (mode.toInt(16) + 0x40).toString(16).uppercase()
        val codes = linkedSetOf<String>()
        for (msg in messages(raw)) {
            val idx = msg.indexOf(header)
            if (idx != 0) continue
            var hex = msg.substring(header.length)
            // CAN replies carry a count byte, which makes the payload length odd.
            val bytes = hex.length / 2
            if (bytes % 2 == 1) {
                val count = hex.substring(0, 2).toIntOrNull(16) ?: continue
                hex = hex.substring(2).take(count * 4)
            }
            hex.chunked(4).filter { it.length == 4 && it != "0000" }.forEach { codes += decode(it) }
        }
        return codes.toList()
    }

    /** Splits a raw reply into whole messages, joining multi-frame CAN parts. */
    internal fun messages(raw: String): List<String> {
        val lines = ElmParser.clean(raw).filterNot { it.contains("NODATA") || it.contains("ERROR") || it.startsWith("STOPPED") }
        val out = mutableListOf<String>()
        var multi: StringBuilder? = null
        var multiLen = 0
        fun flush() {
            multi?.let { out += it.toString().take(multiLen * 2) }
            multi = null
        }
        for (line in lines) {
            val frame = Regex("^([0-9A-F]):(.*)$").find(line)
            when {
                frame != null -> {
                    if (frame.groupValues[1] == "0" && multi == null) { multi = StringBuilder(); multiLen = 4095 }
                    multi?.append(frame.groupValues[2])
                }
                line.length == 3 && line.toIntOrNull(16) != null -> {
                    // Byte count line that starts a multi-frame message.
                    flush()
                    multi = StringBuilder(); multiLen = line.toInt(16)
                }
                else -> { flush(); out += line }
            }
        }
        flush()
        return out
    }

    /** "0133" -> "P0133". */
    fun decode(hex4: String): String {
        val first = hex4.substring(0, 1).toInt(16)
        val letter = "PCBU"[first shr 2]
        val digit = first and 3
        return "$letter$digit${hex4.substring(1)}"
    }

    /** Reply to 0101: bit 7 of byte A is the engine lamp, the rest is the code count. */
    fun parseMil(raw: String): MilStatus? {
        for (line in messages(raw)) {
            val idx = line.indexOf("4101")
            if (idx < 0 || line.length < idx + 6) continue
            val a = line.substring(idx + 4, idx + 6).toIntOrNull(16) ?: continue
            return MilStatus(milOn = a and 0x80 != 0, dtcCount = a and 0x7F)
        }
        return null
    }

    /** Reply to 020200: the code that stored freeze frame 0, or null when there is none. */
    fun parseFreezeDtc(raw: String): String? {
        for (line in messages(raw)) {
            val idx = line.indexOf("420200")
            if (idx < 0 || line.length < idx + 10) continue
            val hex = line.substring(idx + 6, idx + 10)
            return if (hex == "0000") null else decode(hex)
        }
        return null
    }

    /** Reply to 02xx00 for a mode 01 PID. */
    fun parseFreezePid(pid: Pid, raw: String): Double? {
        val header = "42${pid.code}00"
        for (line in messages(raw)) {
            val idx = line.indexOf(header)
            if (idx < 0) continue
            val hex = line.substring(idx + header.length)
            if (hex.length < pid.bytes * 2) continue
            val data = IntArray(pid.bytes) { i -> hex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null }
            return pid.parse(data)
        }
        return null
    }
}
