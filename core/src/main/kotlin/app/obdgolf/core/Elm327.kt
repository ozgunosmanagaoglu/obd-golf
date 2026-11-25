package app.obdgolf.core


/** Status words an ELM327 (or clone / STN chip) prints instead of data. */
enum class ElmError(val token: String, val linkLost: Boolean) {
    NO_DATA("NODATA", false),          // the car did not answer this PID: normal for unsupported ones
    UNABLE_TO_CONNECT("UNABLETOCONNECT", true), // no OBD protocol found: ignition off or car not OBD-II
    BUS_INIT_ERROR("BUSINIT:...ERROR", true),
    CAN_ERROR("CANERROR", true),
    BUS_ERROR("BUSERROR", true),
    BUS_BUSY("BUSBUSY", false),
    DATA_ERROR("DATAERROR", false),
    BUFFER_FULL("BUFFERFULL", false),
    FB_ERROR("FBERROR", true),
    LOW_VOLTAGE_RESET("LVRESET", true),
    STOPPED("STOPPED", false),         // our next command interrupted a slow reply
    UNKNOWN_COMMAND("?", false);       // clones answer "?" to commands they do not know

    companion object {
        /** The first status word in [raw], or null when the reply carries data or OK. */
        fun of(raw: String): ElmError? {
            val lines = ElmParser.clean(raw)
            for (line in lines) {
                if (line == "?") return UNKNOWN_COMMAND
                entries.firstOrNull { it != UNKNOWN_COMMAND && line.contains(it.token) }?.let { return it }
            }
            return null
        }
    }
}

/** OBD-II bus protocols by their ELM327 number (ATDPN). */
enum class ObdProtocol(val number: Char, val label: String) {
    J1850_PWM('1', "SAE J1850 PWM"),
    J1850_VPW('2', "SAE J1850 VPW"),
    ISO_9141('3', "ISO 9141-2"),
    KWP_5BAUD('4', "ISO 14230 KWP (5 baud)"),
    KWP_FAST('5', "ISO 14230 KWP (fast)"),
    CAN_11_500('6', "CAN 11 bit 500k"),
    CAN_29_500('7', "CAN 29 bit 500k"),
    CAN_11_250('8', "CAN 11 bit 250k"),
    CAN_29_250('9', "CAN 29 bit 250k"),
    J1939('A', "SAE J1939 CAN");

    val isCan: Boolean get() = number in '6'..'9'

    /** 11-bit CAN, where control units are addressed as 7E0..7E7 (needed for maker-specific requests). */
    val isCan11: Boolean get() = this == CAN_11_500 || this == CAN_11_250

    companion object {
        /** Parses the reply to ATDPN, e.g. "A6" (found automatically) or "6". */
        fun fromDpn(raw: String): ObdProtocol? {
            val line = ElmParser.clean(raw).firstOrNull() ?: return null
            val n = line.removePrefix("A").firstOrNull() ?: return null
            return entries.firstOrNull { it.number == n }
        }
    }
}

/** What we learned about the adapter and the car while connecting. */
data class AdapterInfo(
    /** e.g. "ELM327 v1.5". Many cheap clones claim v1.5 or v2.1 whatever they really are. */
    val elmVersion: String? = null,
    /** STN chip id (OBDLink, vLinker), e.g. "STN2120 v5.6.19". Null on plain ELM327 and clones. */
    val stnId: String? = null,
    val protocol: ObdProtocol? = null,
    /** What the probe found; null when it could not run. */
    val check: AdapterCheck? = null,
) {
    /** Short text for the connection screen. */
    val label: String
        get() = listOfNotNull(stnId ?: elmVersion ?: "ELM327", protocol?.label).joinToString(" · ")
}

/**
 * Which mode 01 PIDs the car answers, from the "PIDs supported" bitmaps 0100, 0120, 0140...
 * A range that was never read counts as supported, so an odd adapter never hides data.
 */
class SupportedPids(private val known: Map<Int, Set<Int>> = emptyMap()) {

    fun isSupported(pid: Int): Boolean {
        val base = ((pid - 1) / 0x20) * 0x20
        val set = known[base] ?: return true
        return pid in set
    }

    fun isSupported(pid: Pid): Boolean = isSupported(pid.code.toInt(16))

    /** True only when the car's bitmap explicitly lists [pid] (used for optional extras). */
    fun isKnownSupported(pid: Pid): Boolean {
        val base = ((pid.number - 1) / 0x20) * 0x20
        return known[base]?.contains(pid.number) == true
    }

    /** True when the bitmap at [base] says the next range (base + 0x20) exists. */
    fun hasNextRange(base: Int): Boolean = known[base]?.contains(base + 0x20) == true

    fun with(base: Int, pids: Set<Int>) = SupportedPids(known + (base to pids))

    val all: Set<Int> get() = known.values.flatten().toSortedSet()

    companion object {
        /** Reads the 4-byte bitmap in the reply to "01xx" (xx = [base]); bit 0 (MSB) = PID base+1. */
        fun parseBitmap(base: Int, raw: String): Set<Int>? {
            val header = "41%02X".format(base)
            // Several ECUs may answer (engine and gearbox): merge what they support.
            var found = false
            val pids = sortedSetOf<Int>()
            for (line in ElmParser.clean(raw)) {
                val idx = line.indexOf(header)
                if (idx < 0) continue
                val hex = line.substring(idx + header.length)
                if (hex.length < 8) continue
                val bits = hex.substring(0, 8).toLongOrNull(16) ?: continue
                found = true
                for (i in 0 until 32) if (bits shr (31 - i) and 1L == 1L) pids += base + 1 + i
            }
            return if (found) pids else null
        }
    }
}

/** The adapter works but the car does not answer: ignition off, wrong socket, or not an OBD-II car. */
class VehicleNotRespondingException(val error: ElmError?) :
    LocalizedIOException(Str.VEHICLE_NO_REPLY, error?.let { " (${it.token})" } ?: "")

/** The adapter answers nothing at all: not an ELM327, or it hung and needs replugging. */
class AdapterNotRespondingException : LocalizedIOException(Str.ADAPTER_NO_REPLY)
