package app.obdgolf.core

/** The engine's emission self-tests (OBD "readiness monitors"), as reported in bytes B–D of 0101. */
enum class Monitor(val label: Str) {
    MISFIRE(Str.MON_MISFIRE),
    FUEL_SYSTEM(Str.MON_FUEL_SYSTEM),
    COMPONENTS(Str.MON_COMPONENTS),
    // Petrol engines
    CATALYST(Str.MON_CATALYST),
    HEATED_CATALYST(Str.MON_HEATED_CATALYST),
    EVAP(Str.MON_EVAP),
    SECONDARY_AIR(Str.MON_SECONDARY_AIR),
    AC_REFRIGERANT(Str.MON_AC),
    O2_SENSOR(Str.MON_O2_SENSOR),
    O2_HEATER(Str.MON_O2_HEATER),
    EGR(Str.MON_EGR),
    // Diesel engines
    NMHC_CATALYST(Str.MON_NMHC_CATALYST),
    NOX(Str.MON_NOX),
    BOOST(Str.MON_BOOST),
    EXHAUST_SENSOR(Str.MON_EXHAUST_SENSOR),
    PARTICULATE_FILTER(Str.MON_PARTICULATE_FILTER),
}

data class MonitorState(val monitor: Monitor, val complete: Boolean)

/** Only the monitors this car has; a monitor it does not have is left out. */
data class Readiness(val diesel: Boolean, val monitors: List<MonitorState>) {
    val completeCount: Int get() = monitors.count { it.complete }
    val incomplete: List<Monitor> get() = monitors.filterNot { it.complete }.map { it.monitor }
    val allComplete: Boolean get() = monitors.isNotEmpty() && incomplete.isEmpty()

    companion object {
        // Bit n of bytes C (has it) and D (not yet complete), per SAE J1979.
        private val SPARK = listOf(
            Monitor.CATALYST, Monitor.HEATED_CATALYST, Monitor.EVAP, Monitor.SECONDARY_AIR,
            Monitor.AC_REFRIGERANT, Monitor.O2_SENSOR, Monitor.O2_HEATER, Monitor.EGR,
        )
        private val COMPRESSION = listOf(
            Monitor.NMHC_CATALYST, Monitor.NOX, null, Monitor.BOOST,
            null, Monitor.EXHAUST_SENSOR, Monitor.PARTICULATE_FILTER, Monitor.EGR,
        )

        /**
         * Reads the reply to 0101. Engine and gearbox may both answer; the one with the most monitors is
         * the engine. Null when no reply has the 4 data bytes.
         */
        fun parse(raw: String): Readiness? = DtcParser.messages(raw).mapNotNull(::parseOne).maxByOrNull { it.monitors.size }

        private fun parseOne(msg: String): Readiness? {
            val i = msg.indexOf("4101")
            if (i < 0 || msg.length < i + 12) return null
            fun byte(k: Int) = msg.substring(i + 4 + k * 2, i + 6 + k * 2).toIntOrNull(16)
            val b = byte(1) ?: return null
            val c = byte(2) ?: return null
            val d = byte(3) ?: return null
            val diesel = b and 0x08 != 0
            val list = mutableListOf<MonitorState>()
            // Byte B: bits 0–2 say the car has the test, bits 4–6 that it is not complete yet.
            listOf(Monitor.MISFIRE, Monitor.FUEL_SYSTEM, Monitor.COMPONENTS).forEachIndexed { n, m ->
                if (b shr n and 1 == 1) list += MonitorState(m, complete = b shr (n + 4) and 1 == 0)
            }
            (if (diesel) COMPRESSION else SPARK).forEachIndexed { n, m ->
                if (m != null && c shr n and 1 == 1) list += MonitorState(m, complete = d shr n and 1 == 0)
            }
            return Readiness(diesel, list)
        }
    }
}
