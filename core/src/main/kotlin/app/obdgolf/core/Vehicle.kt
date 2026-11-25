package app.obdgolf.core

/** Car makers grouped by shared electronics, so later brand profiles (e.g. VW group extras) can be picked. */
enum class MakerGroup { VAG, BMW, MERCEDES, STELLANTIS, RENAULT_NISSAN, FORD, TOYOTA, HYUNDAI_KIA, GM_OPEL, HONDA, MAZDA, VOLVO, JLR, OTHER }

/** What the VIN tells us about the car. */
data class VehicleInfo(val vin: String, val make: String?, val group: MakerGroup, val modelYear: Int?) {
    val label: String get() = listOfNotNull(make, modelYear?.toString()).joinToString(" ").ifEmpty { "Bilinmeyen araç" }
}

object Vin {
    private val VALID = Regex("^[A-HJ-NPR-Z0-9]{17}$")

    fun isValid(vin: String) = VALID.matches(vin)

    /**
     * Parses the reply to 0902. CAN cars answer one multi-frame message ("014", "0:49 02 01 …", "1:…"),
     * older cars five lines "49 02 0n xx xx xx xx". Either way one byte (count or sequence) follows "4902".
     */
    fun parse(raw: String): String? {
        val bytes = StringBuilder()
        for (msg in DtcParser.messages(raw)) {
            val idx = msg.indexOf("4902")
            if (idx < 0) continue
            bytes.append(msg.substring(idx + 6))
        }
        val text = bytes.toString().chunked(2)
            .mapNotNull { it.toIntOrNull(16)?.toChar() }
            .filter { it in 'A'..'Z' || it in '0'..'9' }
            .joinToString("")
        if (text.length < 17) return null
        return text.takeLast(17).takeIf(::isValid)
    }

    private data class Maker(val prefix: String, val make: String, val group: MakerGroup)

    /** World manufacturer identifiers (first 2–3 VIN characters) of the makers most common in Europe and the US. */
    private val makers = listOf(
        Maker("WVW", "Volkswagen", MakerGroup.VAG), Maker("WV1", "Volkswagen", MakerGroup.VAG),
        Maker("WV2", "Volkswagen", MakerGroup.VAG), Maker("WVG", "Volkswagen", MakerGroup.VAG),
        Maker("3VW", "Volkswagen", MakerGroup.VAG), Maker("1VW", "Volkswagen", MakerGroup.VAG),
        Maker("WAU", "Audi", MakerGroup.VAG), Maker("WUA", "Audi", MakerGroup.VAG), Maker("TRU", "Audi", MakerGroup.VAG),
        Maker("TMB", "Škoda", MakerGroup.VAG), Maker("VSS", "SEAT / Cupra", MakerGroup.VAG),
        Maker("WP0", "Porsche", MakerGroup.VAG), Maker("WP1", "Porsche", MakerGroup.VAG),
        Maker("WBA", "BMW", MakerGroup.BMW), Maker("WBS", "BMW M", MakerGroup.BMW), Maker("WBY", "BMW", MakerGroup.BMW),
        Maker("WMW", "MINI", MakerGroup.BMW),
        Maker("WDB", "Mercedes-Benz", MakerGroup.MERCEDES), Maker("WDD", "Mercedes-Benz", MakerGroup.MERCEDES),
        Maker("W1K", "Mercedes-Benz", MakerGroup.MERCEDES), Maker("W1N", "Mercedes-Benz", MakerGroup.MERCEDES),
        Maker("WDF", "Mercedes-Benz", MakerGroup.MERCEDES),
        Maker("VF3", "Peugeot", MakerGroup.STELLANTIS), Maker("VF7", "Citroën", MakerGroup.STELLANTIS),
        Maker("VR3", "Peugeot", MakerGroup.STELLANTIS), Maker("VR7", "Citroën", MakerGroup.STELLANTIS),
        Maker("ZFA", "Fiat", MakerGroup.STELLANTIS), Maker("ZAR", "Alfa Romeo", MakerGroup.STELLANTIS),
        Maker("W0L", "Opel", MakerGroup.GM_OPEL), Maker("W0V", "Opel", MakerGroup.GM_OPEL),
        Maker("VXK", "Opel", MakerGroup.STELLANTIS),
        Maker("VF1", "Renault", MakerGroup.RENAULT_NISSAN), Maker("UU1", "Dacia", MakerGroup.RENAULT_NISSAN),
        Maker("SJN", "Nissan", MakerGroup.RENAULT_NISSAN), Maker("JN1", "Nissan", MakerGroup.RENAULT_NISSAN),
        Maker("VNV", "Nissan", MakerGroup.RENAULT_NISSAN),
        Maker("WF0", "Ford", MakerGroup.FORD), Maker("1FA", "Ford", MakerGroup.FORD), Maker("1FT", "Ford", MakerGroup.FORD),
        Maker("1FM", "Ford", MakerGroup.FORD),
        Maker("JT", "Toyota", MakerGroup.TOYOTA), Maker("SB1", "Toyota", MakerGroup.TOYOTA),
        Maker("VNK", "Toyota", MakerGroup.TOYOTA), Maker("NMT", "Toyota", MakerGroup.TOYOTA),
        Maker("KMH", "Hyundai", MakerGroup.HYUNDAI_KIA), Maker("TMA", "Hyundai", MakerGroup.HYUNDAI_KIA),
        Maker("NLH", "Hyundai", MakerGroup.HYUNDAI_KIA),
        Maker("KNA", "Kia", MakerGroup.HYUNDAI_KIA), Maker("U5Y", "Kia", MakerGroup.HYUNDAI_KIA),
        Maker("JHM", "Honda", MakerGroup.HONDA), Maker("SHH", "Honda", MakerGroup.HONDA),
        Maker("JMZ", "Mazda", MakerGroup.MAZDA), Maker("JM1", "Mazda", MakerGroup.MAZDA),
        Maker("YV1", "Volvo", MakerGroup.VOLVO), Maker("YV4", "Volvo", MakerGroup.VOLVO),
        Maker("SAL", "Land Rover", MakerGroup.JLR), Maker("SAJ", "Jaguar", MakerGroup.JLR),
        Maker("JS", "Suzuki", MakerGroup.OTHER), Maker("TSM", "Suzuki", MakerGroup.OTHER),
        Maker("JMB", "Mitsubishi", MakerGroup.OTHER), Maker("JF1", "Subaru", MakerGroup.OTHER),
        Maker("NM0", "Ford", MakerGroup.FORD), Maker("NMA", "Fiat", MakerGroup.STELLANTIS),
    )

    /** Model year codes (10th VIN character); the same letters repeat every 30 years. */
    private const val YEAR_CODES = "ABCDEFGHJKLMNPRSTVWXY123456789"

    /** The latest year ≤ [maxYear] that the 10th character can stand for. */
    fun modelYear(vin: String, maxYear: Int): Int? {
        val i = YEAR_CODES.indexOf(vin.getOrNull(9) ?: return null)
        if (i < 0) return null
        var year = 1980 + i
        while (year + 30 <= maxYear) year += 30
        return year
    }

    fun decode(vin: String, maxYear: Int = java.time.Year.now().value + 1): VehicleInfo {
        val m = makers.firstOrNull { vin.startsWith(it.prefix) }
        return VehicleInfo(vin, m?.make, m?.group ?: MakerGroup.OTHER, modelYear(vin, maxYear))
    }
}
