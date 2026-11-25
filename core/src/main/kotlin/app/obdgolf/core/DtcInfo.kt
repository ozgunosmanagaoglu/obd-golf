package app.obdgolf.core

/**
 * Fault code descriptions. Exact texts for common generic (SAE) codes; everything else gets the
 * description of its group. English is the base; other languages fall back to it per code.
 */
object DtcInfo {
    private val EN = mapOf(
        // Air / fuel
        "P0010" to "Camshaft (intake) actuator circuit",
        "P0011" to "Intake camshaft timing over-advanced",
        "P0016" to "Crankshaft and camshaft sensors out of sync (timing chain stretch possible)",
        "P0030" to "Oxygen sensor heater circuit (sensor 1)",
        "P0036" to "Oxygen sensor heater circuit (sensor 2)",
        "P0068" to "Manifold pressure / throttle position mismatch (air leak)",
        "P0087" to "Fuel rail pressure too low",
        "P0088" to "Fuel rail pressure too high",
        "P0089" to "Fuel pressure regulator performance",
        "P0101" to "Mass air flow (MAF) sensor range / performance",
        "P0102" to "Mass air flow (MAF) sensor signal low",
        "P0103" to "Mass air flow (MAF) sensor signal high",
        "P0106" to "Manifold pressure (MAP) sensor range / performance",
        "P0107" to "Manifold pressure sensor signal low",
        "P0108" to "Manifold pressure sensor signal high",
        "P0111" to "Intake air temperature sensor range / performance",
        "P0112" to "Intake air temperature sensor signal low",
        "P0113" to "Intake air temperature sensor signal high",
        "P0116" to "Coolant temperature sensor range / performance",
        "P0117" to "Coolant temperature sensor signal low",
        "P0118" to "Coolant temperature sensor signal high",
        "P0121" to "Throttle position sensor A range / performance",
        "P0128" to "Engine not warming up enough (thermostat may be stuck open)",
        "P0130" to "Oxygen sensor circuit (sensor 1)",
        "P0133" to "Oxygen sensor slow response (sensor 1)",
        "P0136" to "Oxygen sensor circuit (sensor 2)",
        "P0141" to "Oxygen sensor heater circuit (sensor 2)",
        "P0171" to "System too lean (air leak, MAF or injector)",
        "P0172" to "System too rich",
        "P0191" to "Fuel rail pressure sensor range / performance",
        "P0201" to "Cylinder 1 injector circuit",
        "P0202" to "Cylinder 2 injector circuit",
        "P0203" to "Cylinder 3 injector circuit",
        "P0204" to "Cylinder 4 injector circuit",
        "P0221" to "Throttle position sensor B range / performance",
        "P0234" to "Turbo overboost",
        "P0236" to "Boost pressure sensor range / performance",
        "P0237" to "Boost pressure sensor signal low",
        "P0238" to "Boost pressure sensor signal high",
        "P0243" to "Turbo wastegate solenoid circuit",
        "P0245" to "Turbo wastegate solenoid signal low",
        "P0299" to "Turbo underboost: leak, wastegate or turbo",
        // Ignition
        "P0300" to "Random / multiple cylinder misfire",
        "P0301" to "Cylinder 1 misfire (spark plug or coil)",
        "P0302" to "Cylinder 2 misfire (spark plug or coil)",
        "P0303" to "Cylinder 3 misfire (spark plug or coil)",
        "P0304" to "Cylinder 4 misfire (spark plug or coil)",
        "P0321" to "Engine speed sensor range / performance",
        "P0322" to "Engine speed sensor no signal",
        "P0324" to "Knock control system error",
        "P0327" to "Knock sensor signal low",
        "P0335" to "Crankshaft position sensor circuit",
        "P0340" to "Camshaft position sensor circuit",
        "P0341" to "Camshaft position sensor range / performance",
        "P0351" to "Cylinder 1 ignition coil circuit",
        "P0352" to "Cylinder 2 ignition coil circuit",
        "P0353" to "Cylinder 3 ignition coil circuit",
        "P0354" to "Cylinder 4 ignition coil circuit",
        // Emissions
        "P0401" to "EGR flow insufficient",
        "P0411" to "Secondary air system incorrect flow",
        "P0420" to "Catalytic converter efficiency below threshold",
        "P0441" to "Evaporative emission (EVAP) system incorrect purge flow",
        "P0442" to "Small leak in the evaporative emission system",
        "P0443" to "EVAP purge valve circuit",
        "P0455" to "Large leak in the evaporative emission system (check the fuel cap)",
        "P0456" to "Very small leak in the evaporative emission system",
        // Speed / idle / electrics
        "P0500" to "Vehicle speed sensor malfunction",
        "P0501" to "Vehicle speed sensor range / performance",
        "P0506" to "Idle speed lower than expected",
        "P0507" to "Idle speed higher than expected",
        "P0560" to "Battery / system voltage malfunction",
        "P0562" to "System voltage low",
        "P0563" to "System voltage high",
        "P0571" to "Brake switch circuit",
        "P0600" to "Engine computer communication error",
        "P0601" to "Engine computer memory (checksum) error",
        "P0606" to "Engine computer processor fault",
        "P0627" to "Fuel pump control circuit open",
        "P0638" to "Throttle actuator range / performance",
        "P0641" to "Sensor reference voltage A circuit",
        "P0651" to "Sensor reference voltage B circuit",
        // Transmission
        "P0700" to "Transmission control system malfunction (the transmission computer has a code)",
        "P0705" to "Transmission range sensor circuit",
        "P0715" to "Transmission input shaft speed sensor",
        "P0720" to "Transmission output shaft speed sensor",
        "P0730" to "Incorrect gear ratio",
        "P0841" to "Transmission fluid pressure sensor",
        "P0868" to "Transmission fluid pressure low",
        "P087A" to "Transmission fluid pressure sensor",
        "P0811" to "Excessive clutch slippage",
        // Newer generic codes
        "P2004" to "Intake manifold runner flap stuck open",
        "P2015" to "Intake manifold runner position sensor range / performance",
        "P2088" to "Camshaft actuator control circuit low",
        "P2096" to "Post-catalyst fuel trim too lean",
        "P2097" to "Post-catalyst fuel trim too rich",
        "P2101" to "Throttle actuator control circuit range / performance",
        "P2122" to "Accelerator pedal sensor D signal low",
        "P2127" to "Accelerator pedal sensor E signal low",
        "P2138" to "Accelerator pedal sensors D/E disagree",
        "P2177" to "System too lean off idle",
        "P2178" to "System too rich off idle",
        "P2181" to "Cooling system performance (thermostat)",
        "P2187" to "System too lean at idle",
        "P2188" to "System too rich at idle",
        "P2279" to "Intake air system leak",
        "P2293" to "Fuel pressure regulator performance",
        "P2563" to "Turbo (wastegate) position sensor range / performance",
        // Network
        "U0001" to "CAN communication bus fault",
        "U0100" to "Lost communication with the engine computer",
        "U0101" to "Lost communication with the transmission computer",
        "U0121" to "Lost communication with the ABS computer",
        "U0140" to "Lost communication with the body control module (BCM)",
        "U0155" to "Lost communication with the instrument cluster",
    )

    private val TR = mapOf(
        // Air / fuel
        "P0010" to "Eksantrik (emme) ayar valfi devresi arızası",
        "P0011" to "Emme eksantrik zamanlaması fazla avans",
        "P0016" to "Krank ve eksantrik sensörü uyumsuz (zincir uzaması olabilir)",
        "P0030" to "Oksijen sensörü ısıtıcı devresi (sensör 1)",
        "P0036" to "Oksijen sensörü ısıtıcı devresi (sensör 2)",
        "P0068" to "Emme manifoldu basıncı / gaz kelebeği uyumsuz (hava kaçağı)",
        "P0087" to "Yakıt rayı basıncı çok düşük",
        "P0088" to "Yakıt rayı basıncı çok yüksek",
        "P0089" to "Yakıt basınç regülatörü performans sorunu",
        "P0101" to "Hava akış (MAF) sensörü ölçüm aralığı dışında",
        "P0102" to "Hava akış (MAF) sensörü sinyali düşük",
        "P0103" to "Hava akış (MAF) sensörü sinyali yüksek",
        "P0106" to "Emme manifoldu basınç (MAP) sensörü ölçüm aralığı dışında",
        "P0107" to "Emme manifoldu basınç sensörü sinyali düşük",
        "P0108" to "Emme manifoldu basınç sensörü sinyali yüksek",
        "P0111" to "Emme havası sıcaklık sensörü ölçüm aralığı dışında",
        "P0112" to "Emme havası sıcaklık sensörü sinyali düşük",
        "P0113" to "Emme havası sıcaklık sensörü sinyali yüksek",
        "P0116" to "Su sıcaklık sensörü ölçüm aralığı dışında",
        "P0117" to "Su sıcaklık sensörü sinyali düşük",
        "P0118" to "Su sıcaklık sensörü sinyali yüksek",
        "P0121" to "Gaz kelebeği sensörü A ölçüm aralığı dışında",
        "P0128" to "Motor yeterince ısınmıyor (termostat açık kalmış olabilir)",
        "P0130" to "Oksijen sensörü devresi (sensör 1)",
        "P0133" to "Oksijen sensörü yavaş tepki veriyor (sensör 1)",
        "P0136" to "Oksijen sensörü devresi (sensör 2)",
        "P0141" to "Oksijen sensörü ısıtıcı devresi (sensör 2)",
        "P0171" to "Karışım çok fakir (hava kaçağı, MAF ya da enjektör)",
        "P0172" to "Karışım çok zengin",
        "P0191" to "Yakıt rayı basınç sensörü ölçüm aralığı dışında",
        "P0201" to "1. silindir enjektör devresi",
        "P0202" to "2. silindir enjektör devresi",
        "P0203" to "3. silindir enjektör devresi",
        "P0204" to "4. silindir enjektör devresi",
        "P0221" to "Gaz kelebeği sensörü B ölçüm aralığı dışında",
        "P0234" to "Turbo aşırı basınç (overboost)",
        "P0236" to "Turbo basınç sensörü ölçüm aralığı dışında",
        "P0237" to "Turbo basınç sensörü sinyali düşük",
        "P0238" to "Turbo basınç sensörü sinyali yüksek",
        "P0243" to "Turbo wastegate selenoidi devresi",
        "P0245" to "Turbo wastegate selenoidi sinyali düşük",
        "P0299" to "Turbo düşük basınç (underboost): kaçak, wastegate ya da turbo",
        // Ignition
        "P0300" to "Rastgele / birden fazla silindirde tekleme",
        "P0301" to "1. silindirde tekleme (buji ya da bobin)",
        "P0302" to "2. silindirde tekleme (buji ya da bobin)",
        "P0303" to "3. silindirde tekleme (buji ya da bobin)",
        "P0304" to "4. silindirde tekleme (buji ya da bobin)",
        "P0321" to "Krank devir sensörü ölçüm aralığı dışında",
        "P0322" to "Krank devir sensörü sinyali yok",
        "P0324" to "Vuruntu kontrol sistemi hatası",
        "P0327" to "Vuruntu sensörü sinyali düşük",
        "P0335" to "Krank konum sensörü devresi",
        "P0340" to "Eksantrik konum sensörü devresi",
        "P0341" to "Eksantrik konum sensörü ölçüm aralığı dışında",
        "P0351" to "1. silindir ateşleme bobini devresi",
        "P0352" to "2. silindir ateşleme bobini devresi",
        "P0353" to "3. silindir ateşleme bobini devresi",
        "P0354" to "4. silindir ateşleme bobini devresi",
        // Emissions
        "P0401" to "EGR akışı yetersiz",
        "P0411" to "İkincil hava sistemi akışı hatalı",
        "P0420" to "Katalitik konvertör verimi düşük",
        "P0441" to "Benzin buharı (EVAP) sistemi akış hatası",
        "P0442" to "Benzin buharı sisteminde küçük kaçak",
        "P0443" to "Kanister (EVAP) valfi devresi",
        "P0455" to "Benzin buharı sisteminde büyük kaçak (depo kapağını kontrol et)",
        "P0456" to "Benzin buharı sisteminde çok küçük kaçak",
        // Speed / idle / electrics
        "P0500" to "Araç hız sensörü arızası",
        "P0501" to "Araç hız sensörü ölçüm aralığı dışında",
        "P0506" to "Rölanti devri beklenenden düşük",
        "P0507" to "Rölanti devri beklenenden yüksek",
        "P0560" to "Akü / sistem voltajı hatalı",
        "P0562" to "Sistem voltajı düşük",
        "P0563" to "Sistem voltajı yüksek",
        "P0571" to "Fren pedalı anahtarı devresi",
        "P0600" to "Motor beyni iletişim hatası",
        "P0601" to "Motor beyni hafıza (checksum) hatası",
        "P0606" to "Motor beyni işlemci hatası",
        "P0627" to "Yakıt pompası kontrol devresi açık",
        "P0638" to "Gaz kelebeği motoru ölçüm aralığı dışında",
        "P0641" to "Sensör referans voltajı A devresi",
        "P0651" to "Sensör referans voltajı B devresi",
        // Transmission
        "P0700" to "Şanzıman kontrol sisteminde arıza (şanzıman beyninde kod var)",
        "P0705" to "Vites konum sensörü devresi",
        "P0715" to "Şanzıman giriş mili devir sensörü",
        "P0720" to "Şanzıman çıkış mili devir sensörü",
        "P0730" to "Yanlış vites oranı",
        "P0841" to "Şanzıman yağ basınç sensörü",
        "P0868" to "Şanzıman yağ basıncı düşük",
        "P087A" to "Şanzıman yağ basınç sensörü",
        "P0811" to "Debriyaj aşırı kayıyor",
        // Newer generic codes
        "P2004" to "Emme manifoldu klapesi açık kalmış",
        "P2015" to "Emme manifoldu klapesi konum sensörü ölçüm aralığı dışında",
        "P2088" to "Eksantrik ayar valfi devresi düşük",
        "P2096" to "Katalizör sonrası karışım çok fakir",
        "P2097" to "Katalizör sonrası karışım çok zengin",
        "P2101" to "Gaz kelebeği motoru kontrol hatası",
        "P2122" to "Gaz pedalı sensörü D sinyali düşük",
        "P2127" to "Gaz pedalı sensörü E sinyali düşük",
        "P2138" to "Gaz pedalı sensörleri birbiriyle uyumsuz",
        "P2177" to "Rölanti dışında karışım çok fakir",
        "P2178" to "Rölanti dışında karışım çok zengin",
        "P2181" to "Soğutma sistemi performansı (termostat)",
        "P2187" to "Rölantide karışım çok fakir",
        "P2188" to "Rölantide karışım çok zengin",
        "P2279" to "Emme sisteminde hava kaçağı",
        "P2293" to "Yakıt basınç regülatörü performansı",
        "P2563" to "Turbo (wastegate) konum sensörü ölçüm aralığı dışında",
        // Network
        "U0001" to "CAN iletişim hattı arızası",
        "U0100" to "Motor beyni ile iletişim yok",
        "U0101" to "Şanzıman beyni ile iletişim yok",
        "U0121" to "ABS beyni ile iletişim yok",
        "U0140" to "Konfor (BCM) beyni ile iletişim yok",
        "U0155" to "Gösterge paneli ile iletişim yok",
    )

    private val tables: Map<Lang, Map<String, String>> = mapOf(Lang.EN to EN, Lang.TR to TR)

    /** Codes with an exact text, per language (for tests). */
    internal fun codes(lang: Lang): Set<String> = tables[lang]?.keys.orEmpty()

    fun isManufacturerSpecific(code: String): Boolean {
        if (code.length != 5) return false
        return if (code[0] == 'P') code[1] == '1' || code[1] == '3' else code[1] == '1' || code[1] == '2'
    }

    fun describe(code: String, lang: Lang = Lang.EN): String {
        (tables[lang]?.get(code) ?: EN[code])?.let { return it }
        val s = Strings(lang)
        if (isManufacturerSpecific(code)) return s.format(Str.DTC_MANUFACTURER, s[group(code)])
        return s[group(code)]
    }

    private fun group(code: String): Str = when (code.firstOrNull()) {
        'P' -> when (code.getOrNull(2)) {
            '0', '1', '2' -> Str.DTC_GROUP_FUEL_AIR
            '3' -> Str.DTC_GROUP_IGNITION
            '4' -> Str.DTC_GROUP_EMISSIONS
            '5' -> Str.DTC_GROUP_SPEED_IDLE
            '6' -> Str.DTC_GROUP_ECU
            '7', '8', '9' -> Str.DTC_GROUP_TRANSMISSION
            'A', 'B', 'C' -> Str.DTC_GROUP_HYBRID
            else -> Str.DTC_GROUP_POWERTRAIN
        }
        'C' -> Str.DTC_GROUP_CHASSIS
        'B' -> Str.DTC_GROUP_BODY
        'U' -> Str.DTC_GROUP_NETWORK
        else -> Str.DTC_GROUP_UNKNOWN
    }
}
