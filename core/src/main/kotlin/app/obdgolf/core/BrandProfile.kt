package app.obdgolf.core

/**
 * A maker-specific value, read with UDS service 22 (ReadDataByIdentifier) from one control unit.
 * It is not part of the OBD standard and differs between models and years, so every car is probed
 * once ([ObdSession.initialize]) and only values that answer inside [range] are shown.
 */
class BrandValue(
    /** Stable id for logs and later settings, e.g. "vag.oil_temp". */
    val id: String,
    val label: Str,
    /** CAN 11-bit request id of the control unit, e.g. "7E0" engine, "7E1" gearbox. */
    val header: String,
    /** 2-byte data identifier in hex, e.g. "11BE". */
    val did: String,
    val bytes: Int,
    /** Metric unit, converted for display like [Pid.unit]. */
    val unit: String,
    val decimals: Int = 0,
    /** A reply outside this range means this car uses the identifier for something else: hidden. */
    val range: ClosedFloatingPointRange<Double>,
    val parse: (IntArray) -> Double,
) {
    val request: String get() = "22$did"

    override fun toString() = id
}

/** The extra values of one group of makers that share electronics. */
class BrandProfile(val group: MakerGroup, val values: List<BrandValue>)

object BrandProfiles {
    /**
     * Volkswagen group (VW, Audi, Škoda, SEAT, Cupra, Porsche), MQB-era cars (about 2013 on).
     * Older PQ35 cars (e.g. Golf Mk6) mostly use KWP over VW TP2.0 instead and simply do not answer;
     * the probe then hides these values.
     */
    val VAG = BrandProfile(
        MakerGroup.VAG,
        listOf(
            BrandValue("vag.oil_temp", Str.BRAND_OIL_TEMP, "7E0", "11BE", 2, "°C", 1, -40.0..180.0) {
                (it[0] * 256 + it[1] - 2731) / 10.0
            },
            BrandValue("vag.gearbox_temp", Str.BRAND_GEARBOX_TEMP, "7E1", "2104", 1, "°C", 0, -40.0..160.0) {
                it[0].toDouble()
            },
        ),
    )

    val all = listOf(VAG)

    fun forGroup(group: MakerGroup?): BrandProfile? = all.firstOrNull { it.group == group }

    /**
     * Parses a positive reply "62 <did> <data>". Null for a negative reply ("7F 22 31": not supported),
     * NO DATA, a reply for another identifier, too few bytes, or a value outside the value's range.
     */
    fun parse(value: BrandValue, raw: String): Double? {
        val header = "62${value.did.uppercase()}"
        for (line in ElmParser.clean(raw)) {
            val idx = line.indexOf(header)
            if (idx < 0) continue
            val hex = line.substring(idx + header.length)
            if (hex.length < value.bytes * 2) continue
            val data = IntArray(value.bytes) { i -> hex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null }
            return value.parse(data).takeIf { it in value.range }
        }
        return null
    }
}
