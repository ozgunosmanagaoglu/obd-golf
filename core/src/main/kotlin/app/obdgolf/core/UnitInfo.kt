package app.obdgolf.core

/**
 * VW group control units whose identification and coding we read (Faz 4d, read only).
 * [udsTx]/[udsRx] are the CAN ids for UDS (newer units, and some on PQ35); units that do not answer
 * UDS are tried over VW TP2.0 / KWP2000 at [address] instead (most units of a 2004–2013 car).
 */
enum class ControlUnit(val address: Int, val label: Str, val udsTx: Int, val udsRx: Int) {
    ENGINE(0x01, Str.MB_UNIT_ENGINE, 0x7E0, 0x7E8),
    GEARBOX(0x02, Str.MB_UNIT_GEARBOX, 0x7E1, 0x7E9),
    BRAKES(0x03, Str.CU_BRAKES, 0x713, 0x77D),
    CLIMATE(0x08, Str.MB_UNIT_CLIMATE, 0x746, 0x7B0),
    CENTRAL_ELECTRICS(0x09, Str.CU_CENTRAL_ELECTRICS, 0x70E, 0x778),
    AIRBAG(0x15, Str.CU_AIRBAG, 0x715, 0x77F),
    INSTRUMENTS(0x17, Str.MB_UNIT_INSTRUMENTS, 0x714, 0x77E),
    GATEWAY(0x19, Str.CU_GATEWAY, 0x710, 0x77A),
    STEERING(0x44, Str.CU_STEERING, 0x712, 0x77C);

    /** "09", as VW workshop tools show it. */
    val code: String get() = "%02X".format(address)
}

/** What one control unit told us about itself. Fields it did not report are null. */
data class UnitInfo(
    val unit: ControlUnit,
    val protocol: Protocol,
    val partNumber: String?,
    val softwareVersion: String? = null,
    val hardwareNumber: String? = null,
    val systemName: String? = null,
    /** Long coding bytes (UDS 0600); null when the unit has none or reports it another way. */
    val coding: List<Int>? = null,
    /** The whole identification reply in hex, kept for units we can only partly decode (KWP2000). */
    val raw: String? = null,
) {
    enum class Protocol { UDS, KWP2000 }

    val codingHex: String? get() = coding?.joinToString("") { "%02X".format(it) }

    /** One line for the adapter log, so a shared log shows what each unit reported. */
    fun logLine(): String = listOfNotNull(
        "Unit ${unit.code} ($protocol)",
        partNumber?.let { "part $it" },
        softwareVersion?.let { "SW $it" },
        hardwareNumber?.let { "HW $it" },
        systemName?.let { "name $it" },
        codingHex?.let { "coding $it" },
        raw?.takeIf { protocol == Protocol.KWP2000 }?.let { "raw $it" },
    ).joinToString(", ")
}

/** Reads replies to identification requests. Nothing here writes to the car. */
object UnitIdent {
    /** VW UDS data identifiers. */
    const val DID_PART = "F187"
    const val DID_SOFTWARE = "F189"
    const val DID_HARDWARE = "F191"
    const val DID_NAME = "F197"
    const val DID_CODING = "0600"

    /** Data bytes of a positive reply "62 <did> …" (single or multi-frame); null for NO DATA or "7F 22 xx". */
    fun udsData(did: String, raw: String): IntArray? {
        val header = "62${did.uppercase()}"
        for (msg in DtcParser.messages(raw)) {
            val i = msg.indexOf(header)
            if (i < 0) continue
            val hex = msg.substring(i + header.length)
            return IntArray(hex.length / 2) { k -> hex.substring(k * 2, k * 2 + 2).toIntOrNull(16) ?: return null }
        }
        return null
    }

    /** Printable text in [bytes], trimmed; null when there is none. */
    fun ascii(bytes: IntArray?): String? =
        bytes?.map { it.toChar() }?.filter { it in ' '..'~' }?.joinToString("")?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * KWP2000 identification ("1A 9B" → "5A 9B …"). The layout differs between units, so we only take
     * the readable text: the first run is the VW part number, the rest (component name, versions) is kept
     * as the system name. The raw bytes stay in [UnitInfo.raw].
     */
    fun kwp(unit: ControlUnit, payload: IntArray): UnitInfo? {
        if (payload.size < 3 || payload[0] != 0x5A || payload[1] != 0x9B) return null
        val runs = mutableListOf<String>()
        val cur = StringBuilder()
        for (b in payload.drop(2)) {
            val c = b.toChar()
            if (c in ' '..'~') cur.append(c) else {
                cur.toString().trim().takeIf { it.length >= 3 }?.let(runs::add)
                cur.clear()
            }
        }
        cur.toString().trim().takeIf { it.length >= 3 }?.let(runs::add)
        return UnitInfo(
            unit, UnitInfo.Protocol.KWP2000,
            partNumber = runs.getOrNull(0),
            systemName = runs.drop(1).joinToString(" · ").takeIf { it.isNotEmpty() },
            raw = Tp20.hex(payload),
        )
    }
}
