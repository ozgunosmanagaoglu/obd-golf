package app.obdgolf.core

private fun ab(d: IntArray) = d[0] * 256 + d[1]
private fun pct(d: IntArray) = d[0] * 100.0 / 255
private fun trim(d: IntArray) = (d[0] - 128) * 100.0 / 128

/**
 * Standard SAE J1979 mode 01 PIDs. Every OBD-II car (petrol from 2001, diesel from 2004 in the EU,
 * 1996 in the US) answers a subset; [SupportedPids] tells which.
 * [parse] gets the data bytes after the "41 xx" header. Names are translated via [labelKey].
 */
enum class Pid(
    val code: String,
    val bytes: Int,
    val unit: String,
    val decimals: Int = 0,
    val parse: (IntArray) -> Double,
) {
    // Polled all the time (dashboard, drive log)
    ENGINE_LOAD("04", 1, "%", parse = ::pct),
    COOLANT_TEMP("05", 1, "°C", parse = { it[0] - 40.0 }),
    RPM("0C", 2, "rpm", parse = { ab(it) / 4.0 }),
    SPEED("0D", 1, "km/h", parse = { it[0].toDouble() }),
    INTAKE_TEMP("0F", 1, "°C", parse = { it[0] - 40.0 }),
    THROTTLE("11", 1, "%", parse = ::pct),
    AMBIENT_TEMP("46", 1, "°C", parse = { it[0] - 40.0 }),

    // Read in turns on the Data screen when the car supports them
    SHORT_FUEL_TRIM_1("06", 1, "%", 1, ::trim),
    LONG_FUEL_TRIM_1("07", 1, "%", 1, ::trim),
    SHORT_FUEL_TRIM_2("08", 1, "%", 1, ::trim),
    LONG_FUEL_TRIM_2("09", 1, "%", 1, ::trim),
    FUEL_PRESSURE("0A", 1, "kPa", parse = { it[0] * 3.0 }),
    MAP("0B", 1, "kPa", parse = { it[0].toDouble() }),
    TIMING_ADVANCE("0E", 1, "°", 1, { it[0] / 2.0 - 64 }),
    MAF("10", 2, "g/s", 1, { ab(it) / 100.0 }),
    RUN_TIME("1F", 2, "s", parse = { ab(it).toDouble() }),
    DISTANCE_WITH_MIL("21", 2, "km", parse = { ab(it).toDouble() }),
    FUEL_RAIL_PRESSURE("23", 2, "kPa", parse = { ab(it) * 10.0 }),
    COMMANDED_EGR("2C", 1, "%", parse = ::pct),
    FUEL_LEVEL("2F", 1, "%", parse = ::pct),
    DISTANCE_SINCE_CLEAR("31", 2, "km", parse = { ab(it).toDouble() }),
    BARO("33", 1, "kPa", parse = { it[0].toDouble() }),
    CATALYST_TEMP_1("3C", 2, "°C", parse = { ab(it) / 10.0 - 40 }),
    MODULE_VOLTAGE("42", 2, "V", 2, { ab(it) / 1000.0 }),
    ABSOLUTE_LOAD("43", 2, "%", parse = { ab(it) * 100.0 / 255 }),
    LAMBDA("44", 2, "λ", 3, { ab(it) * 2.0 / 65536 }),
    RELATIVE_THROTTLE("45", 1, "%", parse = ::pct),
    ACCEL_PEDAL_D("49", 1, "%", parse = ::pct),
    ACCEL_PEDAL_E("4A", 1, "%", parse = ::pct),
    THROTTLE_ACTUATOR("4C", 1, "%", parse = ::pct),
    ETHANOL("52", 1, "%", parse = ::pct),
    OIL_TEMP("5C", 1, "°C", parse = { it[0] - 40.0 }),
    FUEL_RATE("5E", 2, "L/h", 1, { ab(it) / 20.0 }),
    DEMANDED_TORQUE("61", 1, "%", parse = { it[0] - 125.0 }),
    ACTUAL_TORQUE("62", 1, "%", parse = { it[0] - 125.0 });

    /** Its name in [Strings]: Str.PID_<name>. */
    val labelKey: Str get() = Str.valueOf("PID_$name")

    val command: String get() = "01$code"
    val number: Int get() = code.toInt(16)

    companion object {
        /** The ones the live loop always reads; everything else is an "extra". */
        val CORE = setOf(ENGINE_LOAD, COOLANT_TEMP, RPM, SPEED, INTAKE_TEMP, THROTTLE, AMBIENT_TEMP)
        val EXTRAS = entries.filter { it !in CORE }
    }
}

object ElmParser {
    /** Removes spaces, line breaks and status noise the adapter adds around a reply. */
    fun clean(raw: String): List<String> = raw
        .replace("SEARCHING...", "")
        .split('\r', '\n')
        .map { it.replace(" ", "").trim().uppercase() }
        // "BUS INIT: ...OK" is printed once while a K-line (ISO 9141 / KWP) car wakes up.
        .map { it.removePrefix("BUSINIT:...OK").removePrefix("BUSINIT:OK") }
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
