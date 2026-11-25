package app.obdgolf.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A Golf Mk6-like car (VW VIN, CAN 11/500) whose engine and DSG speak TP2.0. */
private class Mk6(blocks: (Int, Int) -> IntArray?) : ElmIo {
    val sim = Tp20Sim(setOf(0x01, 0x02), blocks)
    val sent = mutableListOf<String>()

    override suspend fun exchange(command: String, timeoutMs: Long): String {
        sent += command
        sim.exchange(command)?.let { return it }
        return when (command) {
            "ATZ" -> "ELM327 v1.5\r"
            "0100" -> "4100BE3FA813\r"
            "ATDPN" -> "A6\r"
            "0902" -> "014\r0:490201575657\r1:5A5A5A314B5A42\r2:50303030303030\r"
            else -> if (command.startsWith("AT")) "OK\r" else "NO DATA\r"
        }
    }

    override fun close() {}
}

class Tp20Test {
    @Test fun framesWithHeaders() {
        val f = Tp20.frames("201 00 D0 00 03 40 07 01\r").single()
        assertEquals(0x201, f.id)
        assertEquals(listOf(0x00, 0xD0, 0x00, 0x03, 0x40, 0x07, 0x01), f.data.toList())
    }

    @Test fun setupGivesIds() = assertEquals(0x740 to 0x300, Tp20.parseSetup(1, "20100D0000340070 1\r"))
    @Test fun setupFromAnotherUnitIgnored() = assertNull(Tp20.parseSetup(2, "20100D00003400701\r"))
    @Test fun ackIsNextSequence() = assertEquals("B1", Tp20.ack(0))
    @Test fun ackWraps() = assertEquals("B0", Tp20.ack(15))
    @Test fun shortMessageIsOneLastFrame() = assertEquals(listOf("1300022101"), Tp20.dataFrames(intArrayOf(0x21, 0x01), 3))

    @Test fun longMessageIsSplit() {
        val frames = Tp20.dataFrames(IntArray(14) { it }, 0)
        assertEquals(listOf("20000E0001020304", "2105060708090A0B", "120C0D"), frames)
    }

    @Test fun reassembleWaitsForLastFrame() {
        val parts = Tp20.frames("30020000E6101\r30021C8140A\r")
        assertNull(Tp20.reassemble(parts.take(1)))
    }

    @Test fun reassembleRoundTrip() {
        val payload = IntArray(14) { it + 0x40 }
        val frames = Tp20.dataFrames(payload, 5).flatMap { Tp20.frames("300$it\r") }
        assertEquals(payload.toList(), Tp20.reassemble(frames)?.toList())
    }

    @Test fun decodeRpm() = assertEquals(800.0, MeasuringBlocks.decode(0x01, 0xC8, 20).value)
    @Test fun decodeTemperature() = assertEquals(90.0, MeasuringBlocks.decode(0x05, 0x0A, 190).value!!, 1e-9)
    @Test fun decodeTemperatureDifference() = assertEquals(65.0, MeasuringBlocks.decode(0x1A, 0x00, 65).value)
    @Test fun decodeUnknownKeepsBytes() {
        val v = MeasuringBlocks.decode(0x5E, 1, 2)
        assertNull(v.value)
        assertEquals("5E 01 02", v.raw)
    }

    @Test fun parseBlock() {
        val b = MeasuringBlocks.parse(2, 11, intArrayOf(0x61, 11, 0x1A, 0, 65, 0x05, 0x0A, 190))
        assertEquals(listOf(65.0, 90.0), b?.values?.map { it.value?.let { v -> Math.round(v * 10) / 10.0 } })
    }

    @Test fun negativeReplyIsNull() = assertNull(MeasuringBlocks.parse(2, 11, intArrayOf(0x7F, 0x21, 0x31)))

    @Test fun readsDsgBlockOverTp20() = runBlocking {
        val io = Mk6 { unit, group -> if (unit == 2 && group == 11) intArrayOf(0x1A, 0, 65, 0x05, 0x0A, 190, 0x01, 0xC8, 20, 0x10, 0, 0x24) else null }
        val s = ObdSession(io).also { it.initialize() }
        assertTrue(s.supportsMeasuringBlocks)
        val b = assertNotNull(s.readMeasuringBlock(2, 11))
        assertEquals(4, b.values.size)
        assertEquals(65.0, b.values[0].value)
        assertEquals("°C", b.values[0].unit)
        assertEquals(800.0, b.values[2].value)
        // Back in normal OBD mode afterwards.
        assertFalse(io.sim.raw)
        assertEquals("7DF", io.sim.header)
        assertTrue("A8" in io.sent)
    }

    @Test fun twoReadsInARow(): Unit = runBlocking {
        val io = Mk6 { _, g -> if (g == 1) intArrayOf(0x01, 0xC8, 20) else null }
        val s = ObdSession(io).also { it.initialize() }
        assertNotNull(s.readMeasuringBlock(1, 1))
        assertNotNull(s.readMeasuringBlock(1, 1))
    }

    @Test fun missingGroupIsNull() = runBlocking {
        val io = Mk6 { _, _ -> null }
        val s = ObdSession(io).also { it.initialize() }
        assertNull(s.readMeasuringBlock(1, 99))
        assertFalse(io.sim.raw)
    }

    @Test fun missingUnitIsNull() = runBlocking {
        val io = Mk6 { _, _ -> intArrayOf(0x01, 0xC8, 20) }
        val s = ObdSession(io).also { it.initialize() }
        assertNull(s.readMeasuringBlock(0x08, 1))
        assertFalse(io.sim.raw)
    }

    @Test fun demoHasBlocks(): Unit = runBlocking {
        val s = ObdSession(DemoElmIo()).also { it.initialize() }
        assertEquals(3, s.readMeasuringBlock(0x08, 1)?.values?.size?.minus(1))
        assertNotNull(s.readMeasuringBlock(0x02, 11))
    }
}
