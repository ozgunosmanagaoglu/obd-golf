package app.obdgolf.core

/** One snapshot of everything the app shows. Null means "not read yet / not supported". */
data class CarData(
    val rpm: Double? = null,
    val speedKmh: Double? = null,
    val coolantC: Double? = null,
    val intakeC: Double? = null,
    val ambientC: Double? = null,
    val loadPct: Double? = null,
    val throttlePct: Double? = null,
    val batteryV: Double? = null,
    val refreshHz: Double? = null,
    /** Other standard PIDs the car supports, read in turns (Veriler screen). */
    val extra: Map<Pid, Double> = emptyMap(),
    /** Maker-specific values (e.g. VW group oil temperature) the car answered while connecting. */
    val brand: Map<BrandValue, Double> = emptyMap(),
) {
    fun with(value: BrandValue, v: Double?): CarData = copy(brand = if (v == null) brand - value else brand + (value to v))

    fun with(pid: Pid, value: Double?): CarData = when (pid) {
        Pid.RPM -> copy(rpm = value)
        Pid.SPEED -> copy(speedKmh = value)
        Pid.COOLANT_TEMP -> copy(coolantC = value)
        Pid.INTAKE_TEMP -> copy(intakeC = value)
        Pid.AMBIENT_TEMP -> copy(ambientC = value)
        Pid.ENGINE_LOAD -> copy(loadPct = value)
        Pid.THROTTLE -> copy(throttlePct = value)
        else -> copy(extra = if (value == null) extra - pid else extra + (pid to value))
    }
}
