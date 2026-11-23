package app.obdgolf.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DriveRecorderTest {
    private fun drive(rec: DriveRecorder, fromMs: Long, seconds: Int, speed: Double, rpm: Double): Long {
        var t = fromMs
        repeat(seconds * 4) {
            assertNull(rec.feed(CarData(rpm = rpm, speedKmh = speed, coolantC = 90.0, batteryV = 14.1), t))
            t += 250
        }
        return t
    }

    @Test fun recordsOneDrive() {
        val rec = DriveRecorder()
        var t = 1_000_000L
        t = drive(rec, t, 60, 0.0, 800.0)      // 1 min idle
        t = drive(rec, t, 360, 60.0, 2000.0)   // 6 min at 60 km/h = 6 km
        // Engine off: nothing until 30 s have passed.
        var done: Drive? = null
        while (done == null) {
            done = rec.feed(CarData(rpm = 0.0, speedKmh = 0.0), t)
            t += 1_000
            assertTrue(t < 1_000_000L + 600_000L)
        }
        assertEquals(6.0, done.distanceKm, 0.05)
        assertEquals(60.0, done.maxSpeedKmh)
        assertEquals(60.0, done.avgMovingSpeedKmh, 0.5)
        assertEquals(2000.0, done.maxRpm)
        assertTrue(done.idleSec in 59..61)
        assertTrue(done.durationSec in 419..421)
        assertNotNull(done.samples.firstOrNull())
        assertEquals(14.1, done.minBatteryV)
    }

    @Test fun dropsShortDrive() {
        val rec = DriveRecorder()
        drive(rec, 0, 10, 0.0, 800.0)
        assertNull(rec.finish())
    }

    @Test fun nothingWithEngineOff() {
        val rec = DriveRecorder()
        assertNull(rec.feed(CarData(rpm = 0.0), 0))
        assertEquals(false, rec.isRecording)
    }
}
