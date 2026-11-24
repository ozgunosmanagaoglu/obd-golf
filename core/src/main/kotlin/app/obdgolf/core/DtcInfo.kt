package app.obdgolf.core

/**
 * Turkish descriptions for fault codes. Exact texts for codes that are common on a
 * Golf Mk6 1.4 TSI (CAXA/CAVD) and its DSG; everything else gets a description of its group.
 */
object DtcInfo {
    private val known = mapOf(
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
        // Transmission (DSG)
        "P0700" to "Şanzıman kontrol sisteminde arıza (DSG beyninde kod var)",
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
        "U0101" to "Şanzıman (DSG) beyni ile iletişim yok",
        "U0121" to "ABS beyni ile iletişim yok",
        "U0140" to "Konfor (BCM) beyni ile iletişim yok",
        "U0155" to "Gösterge paneli ile iletişim yok",
    )

    fun isManufacturerSpecific(code: String): Boolean {
        if (code.length != 5) return false
        return if (code[0] == 'P') code[1] == '1' || code[1] == '3' else code[1] == '1' || code[1] == '2'
    }

    fun describe(code: String): String {
        known[code]?.let { return it }
        if (isManufacturerSpecific(code)) return "VW'ye özel kod (${group(code)})"
        return group(code)
    }

    private fun group(code: String): String = when (code.firstOrNull()) {
        'P' -> when (code.getOrNull(2)) {
            '0', '1', '2' -> "Yakıt ve hava ölçümü"
            '3' -> "Ateşleme / tekleme"
            '4' -> "Egzoz ve emisyon kontrolü"
            '5' -> "Hız, rölanti ve elektrik"
            '6' -> "Motor beyni ve çıkışları"
            '7', '8', '9' -> "Şanzıman (DSG)"
            'A', 'B', 'C' -> "Hibrit sistemi"
            else -> "Motor / şanzıman"
        }
        'C' -> "Şasi (ABS, ESP, direksiyon)"
        'B' -> "Gövde (airbag, klima, konfor)"
        'U' -> "Beyinler arası iletişim (CAN)"
        else -> "Bilinmeyen kod"
    }
}
