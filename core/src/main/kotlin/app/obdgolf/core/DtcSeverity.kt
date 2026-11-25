package app.obdgolf.core

/**
 * How urgent a fault code is, in the words a driver needs: stop now, get it checked soon, or it can wait.
 * A guide from the code alone (not a diagnosis): when in doubt a code is rated [SOON], never [LATER].
 */
enum class DtcSeverity(val label: Str, val advice: Str) {
    /** Driving on can damage the engine or gearbox: overheating, no oil pressure. */
    STOP(Str.DTC_SEV_STOP, Str.DTC_ADVICE_STOP),
    /** Drive gently and have it checked within days. */
    SOON(Str.DTC_SEV_SOON, Str.DTC_ADVICE_SOON),
    /** Safe to drive; fix at the next service (may fail an emissions inspection). */
    LATER(Str.DTC_SEV_LATER, Str.DTC_ADVICE_LATER);

    companion object {
        /** Codes where driving on risks real damage. */
        private val STOP_CODES = setOf(
            "P0217", // engine over temperature
            "P0218", // gearbox over temperature
            "P0524", // engine oil pressure too low
        )

        /**
         * Emission-only and sensor-heater codes: the engine runs normally. Ranges are inclusive,
         * on the number after the letter (e.g. P0420 → 0x0420).
         */
        private val LATER_RANGES = listOf(
            0x0030..0x0064, // oxygen sensor heaters and circuits
            0x0110..0x0114, // intake air temperature sensor
            0x0128..0x0128, // thermostat (engine warms up slowly)
            0x0130..0x0167, // oxygen sensors
            0x0410..0x0419, // secondary air
            0x0420..0x0439, // catalytic converter efficiency
            0x0440..0x0469, // evaporative emission (EVAP) system
            0x0470..0x0479, // exhaust pressure sensor
        )

        fun of(code: String): DtcSeverity {
            val c = code.uppercase()
            if (c in STOP_CODES) return STOP
            if (c.length != 5 || c[0] != 'P' || c[1] != '0') return SOON
            val n = c.substring(1).toIntOrNull(16) ?: return SOON
            return if (LATER_RANGES.any { n in it }) LATER else SOON
        }

        /** Misfire codes get an extra warning: a flashing engine light means stop. */
        fun isMisfire(code: String): Boolean {
            val c = code.uppercase()
            return c.length == 5 && c[0] == 'P' && (c.substring(1).toIntOrNull() ?: return false) in 300..312
        }

        /** The most urgent of [codes]; null when there are none. */
        fun worst(codes: List<Dtc>): DtcSeverity? = codes.map { of(it.code) }.minByOrNull { it.ordinal }
    }
}
