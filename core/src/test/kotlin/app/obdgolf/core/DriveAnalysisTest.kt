package app.obdgolf.core

import app.obdgolf.core.DriveAnalysis.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DriveAnalysisTest {
    /** A recorded-style drive: samples every 2 s from (speed, rpm, coolant) triples. */
    private fun drive(
        points: List<Triple<Int, Int, Int?>>,
        idleSec: Int = 0,
        minBatteryV: Double? = 14.1,
        stepSec: Int = 2,
    ): Drive {
        val samples = points.mapIndexed { i, (v, rpm, c) -> DriveSample(i * stepSec, v, rpm, c) }
        val durationMs = (samples.lastOrNull()?.tSec ?: 0) * 1000L
        return Drive(
            0, durationMs, 1.0, samples.maxOf { it.speedKmh }.toDouble(), 30.0, samples.maxOf { it.rpm }.toDouble(),
            samples.mapNotNull { it.coolantC }.maxOrNull()?.toDouble(), minBatteryV, idleSec, false, samples,
        )
    }

    /** Steady city driving, warm engine. */
    private val calm = List(60) { Triple(40 + (it % 5), 1800, 88) }

    @Test fun smoothDrive() {
        val a = DriveAnalysis.of(drive(calm))
        assertEquals(100, a.score)
        assertEquals(listOf(Kind.SMOOTH), a.findings.map { it.kind })
    }

    @Test fun oneHardStopCountsOnce() {
        // A gentle climb to 80 km/h, then 80 → 50 → 20 km/h in 2 s steps: about 4.2 m/s² over two intervals.
        val climb = (50..80 step 6).map { Triple(it, 2500, 88) }
        val stop = calm + climb + listOf(Triple(50, 1500, 88), Triple(20, 1000, 88), Triple(0, 800, 88))
        val a = DriveAnalysis.of(drive(stop))
        assertEquals(1, a.findings.single { it.kind == Kind.HARSH_BRAKE }.count)
        assertEquals(94, a.score)
    }

    @Test fun normalBrakingIsNotHarsh() {
        // 50 → 0 km/h over 8 s: 1.7 m/s².
        val stop = calm + listOf(Triple(50, 1500, 88), Triple(38, 1200, 88), Triple(25, 1000, 88), Triple(12, 900, 88), Triple(0, 800, 88))
        assertTrue(DriveAnalysis.of(drive(stop)).findings.none { it.kind == Kind.HARSH_BRAKE })
    }

    @Test fun hardAcceleration() {
        // 0 → 25 → 50 km/h in 2 s steps: 3.5 m/s², twice, with a calm stretch in between.
        val go = listOf(Triple(0, 900, 88), Triple(25, 4000, 88), Triple(30, 2000, 88)) + calm + listOf(Triple(20, 1500, 88), Triple(45, 4500, 88))
        val a = DriveAnalysis.of(drive(go))
        assertEquals(2, a.findings.single { it.kind == Kind.HARSH_ACCEL }.count)
        assertEquals(92, a.score)
    }

    @Test fun gapInSamplesIsNotABrake() {
        // The adapter lost a few seconds: 60 km/h, then 0 ten seconds later.
        val samples = listOf(DriveSample(0, 60, 2000, 88), DriveSample(10, 0, 800, 88), DriveSample(12, 0, 800, 88))
        val d = Drive(0, 12_000, 0.1, 60.0, 30.0, 2000.0, 88.0, 14.0, 0, false, samples)
        assertTrue(DriveAnalysis.of(d).findings.none { it.kind == Kind.HARSH_BRAKE })
    }

    @Test fun coldRevs() {
        val cold = List(10) { Triple(50, 3500, 40) } + calm
        val f = DriveAnalysis.of(drive(cold)).findings.single { it.kind == Kind.COLD_REVS }
        assertEquals(18, f.count) // 9 intervals of 2 s
    }

    @Test fun longIdle() {
        val a = DriveAnalysis.of(drive(calm, idleSec = 40)) // 118 s drive, 40 s idle: short, not counted
        assertTrue(a.findings.none { it.kind == Kind.LONG_IDLE })
        val long = List(300) { Triple(if (it < 150) 0 else 40, 800, 88) } // 598 s, 300 s idle
        val f = DriveAnalysis.of(drive(long, idleSec = 300)).findings.single { it.kind == Kind.LONG_IDLE }
        assertEquals(5, f.count)
        assertEquals(50, f.percent)
    }

    @Test fun overheatComesFirst() {
        val hot = calm + listOf(Triple(40, 1800, 112))
        val a = DriveAnalysis.of(drive(hot))
        assertEquals(Kind.OVERHEAT, a.findings.first().kind)
        assertEquals(112.0, a.findings.first().value)
        assertEquals(100, a.score) // a car problem, not the driver's
    }

    @Test fun thermostatStuckOpen() {
        val a = DriveAnalysis.of(drive(List(500) { Triple(70, 2200, 62) })) // 998 s ≈ 16 min, never above 62 °C
        val f = a.findings.single { it.kind == Kind.NEVER_WARM }
        assertEquals(16, f.count)
        assertEquals(62.0, f.value)
        // A short drive in winter is not a thermostat problem.
        assertTrue(DriveAnalysis.of(drive(List(100) { Triple(70, 2200, 50) })).findings.none { it.kind == Kind.NEVER_WARM })
    }

    @Test fun lowCharging() {
        assertTrue(DriveAnalysis.of(drive(calm, minBatteryV = 11.8)).findings.any { it.kind == Kind.LOW_CHARGING })
        assertTrue(DriveAnalysis.of(drive(calm, minBatteryV = 12.6)).findings.none { it.kind == Kind.LOW_CHARGING })
    }

    @Test fun scoreHasAFloor() {
        val wild = List(60) { Triple(if (it % 2 == 0) 0 else 30, 5000, 40) }
        assertEquals(30, DriveAnalysis.of(drive(wild)).score) // capped penalties: 30 + 20 + 20
    }
}
