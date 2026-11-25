package app.obdgolf.core

/**
 * What a gauge shows: one of the always-read values, or any other standard PID.
 * [unit] is metric, converted for display like everything else.
 */
sealed interface Metric {
    val id: String
    val unit: String
    val decimals: Int
    /** A sensible full scale for dials and bars, metric. */
    val range: ClosedFloatingPointRange<Double>
    fun read(d: CarData): Double?

    enum class Core(
        override val id: String,
        val label: Str,
        override val unit: String,
        override val decimals: Int,
        override val range: ClosedFloatingPointRange<Double>,
        private val get: (CarData) -> Double?,
    ) : Metric {
        RPM("rpm", Str.RPM, "rpm", 0, 0.0..7000.0, { it.rpm }),
        SPEED("speed", Str.SPEED, "km/h", 0, 0.0..240.0, { it.speedKmh }),
        COOLANT("coolant", Str.COOLANT_TEMP, "°C", 0, 40.0..130.0, { it.coolantC }),
        INTAKE("intake", Str.INTAKE_TEMP, "°C", 0, -20.0..80.0, { it.intakeC }),
        AMBIENT("ambient", Str.OUTSIDE_TEMP, "°C", 0, -30.0..50.0, { it.ambientC }),
        LOAD("load", Str.ENGINE_LOAD, "%", 0, 0.0..100.0, { it.loadPct }),
        THROTTLE("throttle", Str.ACCELERATOR, "%", 0, 0.0..100.0, { it.throttlePct }),
        BATTERY("battery", Str.BATTERY_VOLTAGE, "V", 1, 11.0..15.0, { it.batteryV });

        override fun read(d: CarData) = get(d)
    }

    /** Any other mode 01 PID; shows "—" until the car has answered it. */
    data class Extra(val pid: Pid) : Metric {
        override val id: String get() = "pid:${pid.name}"
        override val unit: String get() = pid.unit
        override val decimals: Int get() = pid.decimals
        override val range: ClosedFloatingPointRange<Double> get() = EXTRA_RANGES[pid] ?: 0.0..100.0
        override fun read(d: CarData) = d.extra[pid]
    }

    companion object {
        private val EXTRA_RANGES = mapOf(
            Pid.MAP to 0.0..250.0, Pid.OIL_TEMP to 40.0..150.0, Pid.TIMING_ADVANCE to -10.0..50.0,
            Pid.MAF to 0.0..200.0, Pid.FUEL_RATE to 0.0..30.0, Pid.CATALYST_TEMP_1 to 0.0..900.0,
            Pid.BARO to 80.0..110.0, Pid.MODULE_VOLTAGE to 11.0..15.0, Pid.LAMBDA to 0.7..1.3,
            Pid.SHORT_FUEL_TRIM_1 to -25.0..25.0, Pid.LONG_FUEL_TRIM_1 to -25.0..25.0,
            Pid.SHORT_FUEL_TRIM_2 to -25.0..25.0, Pid.LONG_FUEL_TRIM_2 to -25.0..25.0,
            Pid.FUEL_RAIL_PRESSURE to 0.0..20000.0, Pid.RUN_TIME to 0.0..7200.0,
        )

        fun byId(id: String): Metric? =
            Core.entries.firstOrNull { it.id == id }
                ?: id.removePrefix("pid:").takeIf { id.startsWith("pid:") }
                    ?.let { name -> Pid.entries.firstOrNull { it.name == name } }?.let(::Extra)

        /** Everything a user can put on the dashboard, core values first. */
        val all: List<Metric> get() = Core.entries + Pid.EXTRAS.map(::Extra)
    }
}

/** How a value is drawn. */
enum class GaugeStyle(val id: String) { DIGITAL("digital"), ARC("arc"), BAR("bar"), SPARKLINE("sparkline") }

/** Tile size on a 2-column grid: 1×1 is a quarter tile, 2×1 a full-width strip, 2×2 a hero gauge. */
enum class GaugeSize(val id: String, val columns: Int, val rows: Int) {
    S1X1("1x1", 1, 1), S2X1("2x1", 2, 1), S2X2("2x2", 2, 2);
}

/**
 * One tile. Alarm limits are metric; null means the metric's default warning zone
 * (e.g. coolant above 105 °C), [alarms] false turns warnings off for this tile.
 */
data class Gauge(
    val metric: Metric,
    val style: GaugeStyle = GaugeStyle.DIGITAL,
    val size: GaugeSize = GaugeSize.S1X1,
    val alarmAbove: Double? = null,
    val alarmBelow: Double? = null,
    val alarms: Boolean = true,
) {
    val warnAbove: Double? get() = if (alarms) alarmAbove ?: Warnings.above[metric] else null
    val warnBelow: Double? get() = if (alarms) alarmBelow ?: Warnings.below[metric] else null

    fun alarm(d: CarData): Alarm? {
        val v = metric.read(d) ?: return null
        warnAbove?.let { if (v > it) return Alarm(metric, v, it, above = true) }
        warnBelow?.let { if (v < it) return Alarm(metric, v, it, above = false) }
        return null
    }
}

/** Default warning zones, metric. Only values where crossing the line means "look now". */
object Warnings {
    val above: Map<Metric, Double> = mapOf(
        Metric.Core.COOLANT to 105.0,
        Metric.Core.RPM to 6200.0,
        Metric.Extra(Pid.OIL_TEMP) to 130.0,
        Metric.Extra(Pid.CATALYST_TEMP_1) to 850.0,
    )
    val below: Map<Metric, Double> = mapOf(
        Metric.Core.BATTERY to 11.8,
        Metric.Extra(Pid.FUEL_LEVEL) to 10.0,
    )
}

data class Alarm(val metric: Metric, val value: Double, val limit: Double, val above: Boolean)

/** One swipeable page of tiles. */
data class DashboardPage(val gauges: List<Gauge>) {
    fun move(index: Int, by: Int): DashboardPage {
        val to = index + by
        if (index !in gauges.indices || to !in gauges.indices) return this
        return copy(gauges = gauges.toMutableList().apply { add(to, removeAt(index)) })
    }

    fun replace(index: Int, gauge: Gauge) = copy(gauges = gauges.toMutableList().apply { set(index, gauge) })
    fun remove(index: Int) = copy(gauges = gauges.filterIndexed { i, _ -> i != index })
    fun add(gauge: Gauge) = copy(gauges = gauges + gauge)
}

/** The user's dashboard: pages swiped left/right, started from a [Preset] and then edited. */
data class Dashboard(val preset: Preset, val pages: List<DashboardPage>) {
    fun alarms(d: CarData): List<Alarm> = pages.flatMap { it.gauges }.mapNotNull { it.alarm(d) }.distinctBy { it.metric }
    fun updatePage(i: Int, f: (DashboardPage) -> DashboardPage) = copy(pages = pages.mapIndexed { j, p -> if (j == i) f(p) else p })

    companion object {
        val DEFAULT get() = Preset.MINIMAL.dashboard
    }
}

/**
 * Starting points. Driving first: the first page of each has at most four tiles, one hero value,
 * so it reads at arm's length in a glance. More detail lives on later pages.
 */
enum class Preset(val id: String, val label: Str) {
    MINIMAL("minimal", Str.PRESET_MINIMAL),
    SPORT("sport", Str.PRESET_SPORT),
    ECO("eco", Str.PRESET_ECO),
    DIAGNOSTIC("diagnostic", Str.PRESET_DIAGNOSTIC);

    val dashboard: Dashboard get() = Dashboard(this, pages())

    private fun pages(): List<DashboardPage> {
        fun g(m: Metric, style: GaugeStyle = GaugeStyle.DIGITAL, size: GaugeSize = GaugeSize.S1X1) = Gauge(m, style, size)
        fun x(p: Pid) = Metric.Extra(p)
        return when (this) {
            MINIMAL -> listOf(
                DashboardPage(listOf(g(Metric.Core.SPEED, GaugeStyle.ARC, GaugeSize.S2X2), g(Metric.Core.COOLANT, GaugeStyle.BAR), g(Metric.Core.BATTERY, GaugeStyle.BAR))),
                DashboardPage(listOf(g(Metric.Core.RPM, GaugeStyle.ARC, GaugeSize.S2X2), g(Metric.Core.INTAKE), g(Metric.Core.AMBIENT))),
            )
            SPORT -> listOf(
                DashboardPage(listOf(g(Metric.Core.RPM, GaugeStyle.ARC, GaugeSize.S2X2), g(Metric.Core.SPEED), g(Metric.Core.COOLANT, GaugeStyle.BAR))),
                DashboardPage(listOf(
                    g(Metric.Core.THROTTLE, GaugeStyle.BAR, GaugeSize.S2X1), g(x(Pid.MAP), GaugeStyle.SPARKLINE, GaugeSize.S2X1),
                    g(x(Pid.TIMING_ADVANCE)), g(x(Pid.OIL_TEMP)),
                )),
            )
            ECO -> listOf(
                DashboardPage(listOf(g(Metric.Core.SPEED, GaugeStyle.DIGITAL, GaugeSize.S2X2), g(Metric.Core.LOAD, GaugeStyle.BAR), g(x(Pid.FUEL_RATE)))),
                DashboardPage(listOf(g(x(Pid.FUEL_LEVEL), GaugeStyle.BAR, GaugeSize.S2X1), g(Metric.Core.RPM, GaugeStyle.SPARKLINE, GaugeSize.S2X1), g(Metric.Core.COOLANT), g(Metric.Core.BATTERY))),
            )
            DIAGNOSTIC -> listOf(
                DashboardPage(listOf(g(Metric.Core.COOLANT, GaugeStyle.SPARKLINE, GaugeSize.S2X1), g(Metric.Core.BATTERY, GaugeStyle.SPARKLINE, GaugeSize.S2X1), g(Metric.Core.RPM), g(Metric.Core.LOAD))),
                DashboardPage(listOf(
                    g(x(Pid.SHORT_FUEL_TRIM_1), GaugeStyle.SPARKLINE, GaugeSize.S2X1), g(x(Pid.LONG_FUEL_TRIM_1), GaugeStyle.SPARKLINE, GaugeSize.S2X1),
                    g(x(Pid.MAF)), g(x(Pid.LAMBDA)),
                )),
                DashboardPage(listOf(g(Metric.Core.INTAKE), g(Metric.Core.AMBIENT), g(x(Pid.TIMING_ADVANCE)), g(x(Pid.MAP)))),
            )
        }
    }
}

/**
 * Saves a dashboard as plain text (kept in the app preferences; readable in a bug report):
 * line 1 is the preset id, then one line per page with tiles "metric,style,size,above,below,alarms" joined by ";".
 * Unknown metrics or styles (e.g. written by a newer version) fall back instead of failing.
 */
object DashboardCodec {
    fun encode(d: Dashboard): String = (listOf(d.preset.id) + d.pages.map { p ->
        p.gauges.joinToString(";") { g ->
            listOf(g.metric.id, g.style.id, g.size.id, g.alarmAbove?.toString() ?: "", g.alarmBelow?.toString() ?: "", if (g.alarms) "1" else "0")
                .joinToString(",")
        }
    }).joinToString("\n")

    fun decode(text: String?): Dashboard? {
        val lines = text?.lines()?.takeIf { it.size >= 2 } ?: return null
        val preset = Preset.entries.firstOrNull { it.id == lines[0] } ?: Preset.MINIMAL
        val pages = lines.drop(1).map { line ->
            DashboardPage(line.split(';').filter { it.isNotBlank() }.mapNotNull { t ->
                val f = t.split(',')
                val metric = Metric.byId(f[0]) ?: return@mapNotNull null
                Gauge(
                    metric,
                    GaugeStyle.entries.firstOrNull { it.id == f.getOrNull(1) } ?: GaugeStyle.DIGITAL,
                    GaugeSize.entries.firstOrNull { it.id == f.getOrNull(2) } ?: GaugeSize.S1X1,
                    f.getOrNull(3)?.toDoubleOrNull(),
                    f.getOrNull(4)?.toDoubleOrNull(),
                    f.getOrNull(5) != "0",
                )
            })
        }
        return Dashboard(preset, pages.ifEmpty { return null })
    }
}
