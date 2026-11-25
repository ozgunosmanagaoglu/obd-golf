package app.obdgolf.core

enum class SpeedUnit(val label: String, val speed: String, val distance: String) {
    METRIC("km/h · km", "km/h", "km"),
    IMPERIAL("mph · mil", "mph", "mi"),
}

enum class TempUnit(val label: String) { CELSIUS("°C"), FAHRENHEIT("°F") }

enum class PressureUnit(val label: String) { KPA("kPa"), BAR("bar"), PSI("psi") }

/**
 * The user's display units. Everything inside the app (OBD values, drive log) stays metric;
 * only what is shown is converted.
 */
data class Units(
    val speed: SpeedUnit = SpeedUnit.METRIC,
    val temp: TempUnit = TempUnit.CELSIUS,
    val pressure: PressureUnit = PressureUnit.KPA,
) {
    fun speed(kmh: Double?): Double? = kmh?.let { if (speed == SpeedUnit.IMPERIAL) it / KM_PER_MILE else it }
    fun distance(km: Double?): Double? = speed(km)
    fun temp(c: Double?): Double? = c?.let { if (temp == TempUnit.FAHRENHEIT) it * 9 / 5 + 32 else it }

    fun pressure(kpa: Double?): Double? = kpa?.let {
        when (pressure) {
            PressureUnit.KPA -> it
            PressureUnit.BAR -> it / 100
            PressureUnit.PSI -> it * PSI_PER_KPA
        }
    }

    val speedUnit: String get() = speed.speed
    val distanceUnit: String get() = speed.distance
    val tempUnit: String get() = temp.label
    val pressureUnit: String get() = pressure.label

    /** Converts a value given in the PID table's metric [unit]; units without an alternative pass through. */
    fun convert(value: Double, unit: String): Pair<Double, String> = when (unit) {
        "km/h" -> speed(value)!! to speedUnit
        "km" -> distance(value)!! to distanceUnit
        "°C" -> temp(value)!! to tempUnit
        "kPa" -> pressure(value)!! to pressureUnit
        else -> value to unit
    }

    /** One more decimal where the converted number gets small (bar). */
    fun decimalsFor(unit: String, metricDecimals: Int): Int =
        if (unit == "kPa" && pressure == PressureUnit.BAR) maxOf(metricDecimals, 2) else metricDecimals

    companion object {
        const val KM_PER_MILE = 1.609344
        const val PSI_PER_KPA = 0.145037738

        /** A sensible first choice from the phone's region: the US uses mph/°F/psi, the UK mph only. */
        fun defaultFor(country: String): Units = when (country.uppercase()) {
            "US", "LR", "MM" -> Units(SpeedUnit.IMPERIAL, TempUnit.FAHRENHEIT, PressureUnit.PSI)
            "GB" -> Units(SpeedUnit.IMPERIAL, TempUnit.CELSIUS, PressureUnit.PSI)
            else -> Units()
        }
    }
}
