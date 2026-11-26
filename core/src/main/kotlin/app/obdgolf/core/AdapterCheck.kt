package app.obdgolf.core

/**
 * How far we trust the adapter, from what it says about itself and a few harmless commands (Faz 5.3).
 * Nothing here changes the car; the probe commands only reset adapter settings to their defaults.
 */
data class AdapterCheck(
    val verdict: Verdict,
    val reasons: List<Reason> = emptyList(),
    /** Probe commands the adapter answered with "?" (not supported). */
    val missing: List<String> = emptyList(),
) {
    enum class Verdict(val label: Str) {
        /** STN chip (OBDLink, vLinker): the most reliable kind. */
        STN(Str.ADAPTER_STN),
        OK(Str.ADAPTER_OK),
        /** Probably a clone. Live data usually works; coding is safer with a better adapter. */
        CLONE(Str.ADAPTER_CLONE),
        /** Cannot address single control units: no control units, no coding. */
        LIMITED(Str.ADAPTER_LIMITED),
    }

    enum class Reason(val label: Str) {
        /** ELM Electronics never released v1.5; only clones report it. */
        FAKE_V15(Str.ADAPTER_REASON_V15),
        /** Answers OK to a command that does not exist, so its other answers cannot be trusted. */
        ACCEPTS_ANYTHING(Str.ADAPTER_REASON_ANY_OK),
        MISSING_COMMANDS(Str.ADAPTER_REASON_MISSING),
    }

    /** Coding and control-unit reads need every probe command. */
    val canTalkToUnits: Boolean get() = verdict != Verdict.LIMITED

    fun logLine(): String = "Adapter check: $verdict" +
        (if (reasons.isNotEmpty()) " (${reasons.joinToString()})" else "") +
        (if (missing.isNotEmpty()) "; missing ${missing.joinToString(" ")}" else "")

    companion object {
        /**
         * Harmless commands this app needs for control units (ELM327 v1.4 and newer):
         * reset the receive filter, and the default flow control mode.
         */
        val PROBES = listOf("ATCRA", "ATFCSM0")

        /** No ELM327 knows this; a real chip answers "?". */
        const val BOGUS = "ATZZ9"

        /** Adapters that passed every check on real cars; brand names, not translated. */
        const val RECOMMENDED = "OBDLink MX+ / CX, vLinker MC+ / FS, Vgate iCar Pro"

        fun judge(info: AdapterInfo, probeReplies: Map<String, String>, bogusReply: String?): AdapterCheck {
            val accepted = bogusReply != null && ElmError.of(bogusReply) == null && "OK" in ElmParser.clean(bogusReply).joinToString("")
            // No reply at all (timeout) proves nothing, so only "?" counts as missing.
            val missing = PROBES.filter { cmd -> probeReplies[cmd]?.let { ElmError.of(it) == ElmError.UNKNOWN_COMMAND } == true }
            val reasons = buildList {
                if (info.stnId == null && info.elmVersion?.contains("v1.5") == true) add(Reason.FAKE_V15)
                if (accepted) add(Reason.ACCEPTS_ANYTHING)
                if (missing.isNotEmpty()) add(Reason.MISSING_COMMANDS)
            }
            val verdict = when {
                missing.isNotEmpty() -> Verdict.LIMITED
                info.stnId != null && !accepted -> Verdict.STN
                Reason.FAKE_V15 in reasons || accepted -> Verdict.CLONE
                else -> Verdict.OK
            }
            return AdapterCheck(verdict, reasons, missing)
        }
    }
}
