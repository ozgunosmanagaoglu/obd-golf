package app.obdgolf.core

/**
 * Standard SAE J1979 mode 01 PIDs that a Golf Mk6 1.4 TSI answers.
 * [parse] gets the data bytes after the "41 xx" header.
 */
enum class Pid(val code: String, val bytes: Int, val parse: (IntArray) -> Double) {
    ENGINE_LOAD("04", 1, { it[0] * 100.0 / 255 }),
    COOLANT_TEMP("05", 1, { it[0] - 40.0 }),
    RPM("0C", 2, { (it[0] * 256 + it[1]) / 4.0 }),
    SPEED("0D", 1, { it[0].toDouble() }),
    INTAKE_TEMP("0F", 1, { it[0] - 40.0 }),
    THROTTLE("11", 1, { it[0] * 100.0 / 255 }),
    AMBIENT_TEMP("46", 1, { it[0] - 40.0 });

    val command: String get() = "01$code"
}

object ElmParser {
    /** Removes spaces, line breaks and status noise the adapter adds around a reply. */
    fun clean(raw: String): List<String> = raw
        .replace("SEARCHING...", "")
        .split('\r', '\n')
        .map { it.replace(" ", "").trim().uppercase() }
        .filter { it.isNotEmpty() }

    /**
     * Finds the first line that answers [pid] and returns its value, or null when the car
     * did not answer (NO DATA, error, or a reply for another PID).
     */
    fun parsePid(pid: Pid, raw: String): Double? {
        val header = "41${pid.code}"
        for (line in clean(raw)) {
            val idx = line.indexOf(header)
            if (idx < 0) continue
            val hex = line.substring(idx + header.length)
            if (hex.length < pid.bytes * 2) continue
            val data = IntArray(pid.bytes) { i -> hex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null }
            return pid.parse(data)
        }
        return null
    }

    /** Parses the reply to ATRV, e.g. "14.2V". */
    fun parseVoltage(raw: String): Double? =
        clean(raw).firstNotNullOfOrNull { line -> line.removeSuffix("V").toDoubleOrNull() }
}
