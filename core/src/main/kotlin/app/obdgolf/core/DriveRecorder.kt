package app.obdgolf.core

import kotlin.math.max
import kotlin.math.min

/** One point of a drive's timeline, sampled every few seconds. */
data class DriveSample(val tSec: Int, val speedKmh: Int, val rpm: Int, val coolantC: Int?)

/** A finished drive as shown in the History tab. */
data class Drive(
    val startMs: Long,
    val endMs: Long,
    val distanceKm: Double,
    val maxSpeedKmh: Double,
    val avgMovingSpeedKmh: Double,
    val maxRpm: Double,
    val maxCoolantC: Double?,
    val minBatteryV: Double?,
    val idleSec: Int,
    val demo: Boolean,
    val samples: List<DriveSample>,
) {
    val durationSec: Long get() = (endMs - startMs) / 1000
}

/**
 * Turns the live data stream into drives.
 * A drive starts when the engine runs (rpm > 300) and ends after the engine has been off
 * (or the adapter silent) for [endAfterMs]. Drives shorter than [minDurationMs] are dropped.
 */
class DriveRecorder(
    private val demo: Boolean = false,
    private val sampleEveryMs: Long = 2_000,
    private val endAfterMs: Long = 30_000,
    private val minDurationMs: Long = 30_000,
) {
    private var start = 0L
    private var lastTick = 0L
    private var lastRunning = 0L
    private var lastSample = 0L
    private var distanceKm = 0.0
    private var movingMs = 0L
    private var idleMs = 0L
    private var maxSpeed = 0.0
    private var maxRpm = 0.0
    private var maxCoolant: Double? = null
    private var minBattery: Double? = null
    private val samples = mutableListOf<DriveSample>()

    val isRecording: Boolean get() = start != 0L

    /** Feeds one reading; returns a finished drive when this reading closes one. */
    fun feed(d: CarData, nowMs: Long): Drive? {
        val running = (d.rpm ?: 0.0) > 300
        if (!isRecording) {
            if (running) begin(nowMs) else return null
        }
        val finished = if (!running && nowMs - lastRunning > endAfterMs) finish() else null
        if (finished != null || !isRecording) return finished

        val dt = (nowMs - lastTick).coerceIn(0, 5_000)
        lastTick = nowMs
        if (running) lastRunning = nowMs
        val speed = d.speedKmh ?: 0.0
        distanceKm += speed * dt / 3_600_000.0
        if (speed > 2) movingMs += dt else if (running) idleMs += dt
        maxSpeed = max(maxSpeed, speed)
        maxRpm = max(maxRpm, d.rpm ?: 0.0)
        d.coolantC?.let { c -> maxCoolant = max(maxCoolant ?: c, c) }
        // Ignore cranking dips below 9 V and readings with the engine off.
        d.batteryV?.takeIf { running && it > 9 }?.let { v -> minBattery = min(minBattery ?: v, v) }

        if (nowMs - lastSample >= sampleEveryMs) {
            lastSample = nowMs
            samples += DriveSample(
                ((nowMs - start) / 1000).toInt(),
                speed.toInt(), (d.rpm ?: 0.0).toInt(), d.coolantC?.toInt(),
            )
        }
        return null
    }

    /** Ends the current drive now (adapter disconnected, app closing). */
    fun finish(): Drive? {
        if (!isRecording) return null
        val end = lastRunning
        val drive = Drive(
            startMs = start,
            endMs = end,
            distanceKm = distanceKm,
            maxSpeedKmh = maxSpeed,
            avgMovingSpeedKmh = if (movingMs > 0) distanceKm / (movingMs / 3_600_000.0) else 0.0,
            maxRpm = maxRpm,
            maxCoolantC = maxCoolant,
            minBatteryV = minBattery,
            idleSec = (idleMs / 1000).toInt(),
            demo = demo,
            samples = samples.toList(),
        )
        reset()
        return drive.takeIf { it.endMs - it.startMs >= minDurationMs }
    }

    private fun begin(nowMs: Long) {
        reset()
        start = nowMs
        lastTick = nowMs
        lastRunning = nowMs
        lastSample = nowMs - sampleEveryMs
    }

    private fun reset() {
        start = 0L; lastTick = 0L; lastRunning = 0L; lastSample = 0L
        distanceKm = 0.0; movingMs = 0L; idleMs = 0L
        maxSpeed = 0.0; maxRpm = 0.0; maxCoolant = null; minBattery = null
        samples.clear()
    }
}
