package app.obdgolf.core

import kotlin.math.roundToInt

/**
 * What one drive says about the driving and the car (Faz 5.6). Rule based and offline: the same drive
 * always gives the same result, and every threshold is a named constant that the tests pin down.
 */
data class DriveAnalysis(
    /** 0–100, driving style only (hard braking, hard acceleration, cold revs, idling). */
    val score: Int,
    val findings: List<Finding>,
) {
    enum class Kind(val label: Str, val warning: Boolean) {
        HARSH_BRAKE(Str.DA_HARSH_BRAKE, false),
        HARSH_ACCEL(Str.DA_HARSH_ACCEL, false),
        COLD_REVS(Str.DA_COLD_REVS, false),
        LONG_IDLE(Str.DA_LONG_IDLE, false),
        /** Car health: shown even when the driving was perfect. */
        OVERHEAT(Str.DA_OVERHEAT, true),
        NEVER_WARM(Str.DA_NEVER_WARM, true),
        LOW_CHARGING(Str.DA_LOW_CHARGING, true),
        SMOOTH(Str.DA_SMOOTH, false),
    }

    /**
     * [count]: events, seconds or minutes, as the sentence says. [value]: a temperature in °C or a voltage,
     * converted to the user's units by the screen. [percent]: share of the drive.
     */
    data class Finding(val kind: Kind, val count: Int = 0, val value: Double? = null, val percent: Int = 0)

    companion object {
        /** ≈0.35 g. Telematics insurers use 0.3–0.4 g for a hard brake. */
        const val HARSH_BRAKE_MS2 = 3.5
        /** ≈0.3 g; a 1.4 TSI at full throttle in first gear gets there, normal driving does not. */
        const val HARSH_ACCEL_MS2 = 3.0
        /** Samples further apart than this (adapter gap) are not compared. */
        const val MAX_GAP_SEC = 5
        const val COLD_C = 60
        const val COLD_RPM = 3000
        /** Idling counts against the drive when it is both this long and this large a share. */
        const val IDLE_MIN_SEC = 180
        const val IDLE_MIN_PERCENT = 20
        const val OVERHEAT_C = 110.0
        /** A drive this long should bring a healthy engine above [WARM_C]. */
        const val WARM_AFTER_SEC = 15 * 60
        const val WARM_C = 70.0
        /** With the engine running the alternator keeps the battery well above this. */
        const val LOW_CHARGING_V = 12.0

        fun of(d: Drive): DriveAnalysis {
            val s = d.samples
            var brakes = 0
            var accels = 0
            var inBrake = false
            var inAccel = false
            var coldRevSec = 0
            for (i in 1 until s.size) {
                val a = s[i - 1]
                val b = s[i]
                val dt = b.tSec - a.tSec
                if (dt <= 0 || dt > MAX_GAP_SEC) {
                    inBrake = false; inAccel = false
                    continue
                }
                val accel = (b.speedKmh - a.speedKmh) / 3.6 / dt
                // A hard stop spread over two samples is one event, not two.
                val braking = accel <= -HARSH_BRAKE_MS2
                if (braking && !inBrake) brakes++
                inBrake = braking
                val accelerating = accel >= HARSH_ACCEL_MS2
                if (accelerating && !inAccel) accels++
                inAccel = accelerating
                val c = b.coolantC
                if (c != null && c < COLD_C && b.rpm > COLD_RPM) coldRevSec += dt
            }
            val duration = d.durationSec.coerceAtLeast(1)
            val idlePercent = (d.idleSec * 100.0 / duration).roundToInt()
            val longIdle = d.idleSec >= IDLE_MIN_SEC && idlePercent >= IDLE_MIN_PERCENT

            val findings = buildList {
                if (brakes > 0) add(Finding(Kind.HARSH_BRAKE, brakes))
                if (accels > 0) add(Finding(Kind.HARSH_ACCEL, accels))
                if (brakes == 0 && accels == 0 && s.size >= 2) add(Finding(Kind.SMOOTH))
                if (coldRevSec > 0) add(Finding(Kind.COLD_REVS, coldRevSec))
                if (longIdle) add(Finding(Kind.LONG_IDLE, d.idleSec / 60, percent = idlePercent))
                d.maxCoolantC?.let { if (it >= OVERHEAT_C) add(Finding(Kind.OVERHEAT, value = it)) }
                d.maxCoolantC?.let {
                    if (duration >= WARM_AFTER_SEC && it < WARM_C) add(Finding(Kind.NEVER_WARM, (duration / 60).toInt(), value = it))
                }
                d.minBatteryV?.let { if (it < LOW_CHARGING_V) add(Finding(Kind.LOW_CHARGING, value = it)) }
            }.sortedByDescending { it.kind.warning }

            val penalty = minOf(30, brakes * 6) + minOf(20, accels * 4) + minOf(20, coldRevSec / 5) + (if (longIdle) 10 else 0)
            return DriveAnalysis((100 - penalty).coerceIn(0, 100), findings)
        }
    }
}
