package app.obdgolf.core

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A scripted adapter. Unknown AT/ST commands get "?" like a clone, unknown OBD requests "NO DATA".
 * A reply of null means "times out".
 */
private class ScriptedElm(private val script: Map<String, String?>) : ElmIo {
    val sent = mutableListOf<String>()
    private val timeoutsLeft = mutableMapOf<String, Int>()

    /** Makes [command] time out [times] times before answering normally. */
    fun timeOut(command: String, times: Int) = apply { timeoutsLeft[command] = times }

    override suspend fun exchange(command: String, timeoutMs: Long): String {
        sent += command
        val left = timeoutsLeft[command] ?: 0
        if (left > 0) {
            timeoutsLeft[command] = left - 1
            throw ElmTimeoutException(command)
        }
        if (command in script) return script[command] ?: throw ElmTimeoutException(command)
        return when {
            command.startsWith("AT") && command != "ATZ" -> "OK\r"
            command.startsWith("AT") || command.startsWith("ST") -> "?\r"
            else -> "NO DATA\r"
        }
    }

    override fun close() {}
}

/** A Golf Mk6-like car on CAN 11/500 behind a cheap v1.5 clone. */
private val golfClone = mapOf(
    "ATZ" to "\r\rELM327 v1.5\r\r",
    "0100" to "SEARCHING...\r4100BE3FA813\r",
    "0120" to "41208002A011\r",
    "0140" to "4140FED00000\r",
    "ATDPN" to "A6\r",
    "010C" to "410C1AF8\r",
    "010D" to "410D48\r",
    "0104" to "41044D\r",
    "0111" to "411120\r",
    "0105" to "41057B\r",
    "010F" to "410F46\r",
    "0146" to "41462E\r",
    "ATRV" to "12.6V\r",
)

class Elm327Test {

    // --- status words ---

    @Test fun noData() = assertEquals(ElmError.NO_DATA, ElmError.of("NO DATA\r"))
    @Test fun unableToConnect() = assertEquals(ElmError.UNABLE_TO_CONNECT, ElmError.of("SEARCHING...\rUNABLE TO CONNECT\r"))
    @Test fun busInitError() = assertEquals(ElmError.BUS_INIT_ERROR, ElmError.of("BUS INIT: ...ERROR\r"))
    @Test fun canError() = assertEquals(ElmError.CAN_ERROR, ElmError.of("CAN ERROR\r"))
    @Test fun questionMark() = assertEquals(ElmError.UNKNOWN_COMMAND, ElmError.of("?\r"))
    @Test fun dataIsNotAnError() = assertNull(ElmError.of("410C1AF8\r"))
    @Test fun okIsNotAnError() = assertNull(ElmError.of("OK\r"))
    @Test fun busInitOkIsRemovedFromData() = assertEquals(72.0, ElmParser.parsePid(Pid.SPEED, "BUS INIT: ...OK\r410D48\r"))

    // --- protocol ---

    @Test fun protocolAuto() = assertEquals(ObdProtocol.CAN_11_500, ObdProtocol.fromDpn("A6\r"))
    @Test fun protocolKLine() = assertEquals(ObdProtocol.ISO_9141, ObdProtocol.fromDpn("3\r"))
    @Test fun protocolUnknown() = assertNull(ObdProtocol.fromDpn("?\r"))

    // --- supported PIDs ---

    @Test fun golfBitmap() {
        val pids = SupportedPids.parseBitmap(0x00, "4100BE3FA813")!!
        assertEquals(setOf(0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x13, 0x15, 0x1C, 0x1F, 0x20), pids)
    }

    @Test fun bitmapMergesTwoEcus() {
        val pids = SupportedPids.parseBitmap(0x00, "4100BE3FA813\r4100 80 00 00 01\r")!!
        assertTrue(0x01 in pids && 0x20 in pids && 0x0C in pids)
    }

    @Test fun bitmapNoData() = assertNull(SupportedPids.parseBitmap(0x20, "NO DATA\r"))

    @Test fun unreadRangeCountsAsSupported() {
        val s = SupportedPids().with(0x00, setOf(0x0C, 0x0D))
        assertTrue(s.isSupported(Pid.RPM))
        assertFalse(s.isSupported(Pid.COOLANT_TEMP))
        assertTrue(s.isSupported(Pid.AMBIENT_TEMP)) // 0x46 is in the 0x40 range, never read
    }

    // --- initialize ---

    @Test fun cloneAdapter() = runBlocking {
        val io = ScriptedElm(golfClone)
        val s = ObdSession(io).also { it.initialize() }
        assertEquals("ELM327 v1.5", s.adapter.elmVersion)
        assertNull(s.adapter.stnId)
        assertEquals(ObdProtocol.CAN_11_500, s.adapter.protocol)
        assertEquals("ELM327 v1.5 · CAN 11 bit 500k", s.adapter.label)
        assertTrue(listOf("0100", "0120", "0140").all { it in io.sent })
        assertFalse("0160" in io.sent) // the 0140 bitmap says 0160 does not exist
        assertTrue(s.supported.isSupported(Pid.AMBIENT_TEMP))
    }

    @Test fun stnAdapter() = runBlocking {
        val s = ObdSession(ScriptedElm(golfClone + ("STI" to "STN2120 v5.6.19\r"))).also { it.initialize() }
        assertEquals("STN2120 v5.6.19", s.adapter.stnId)
        assertEquals("STN2120 v5.6.19 · CAN 11 bit 500k", s.adapter.label)
    }

    @Test fun resetRetriedOnce() = runBlocking {
        val io = ScriptedElm(golfClone).timeOut("ATZ", 1)
        ObdSession(io).initialize()
        assertEquals(2, io.sent.count { it == "ATZ" })
    }

    @Test fun deadAdapter() {
        val io = ScriptedElm(golfClone).timeOut("ATZ", 2)
        assertFailsWith<AdapterNotRespondingException> { runBlocking { ObdSession(io).initialize() } }
    }

    @Test fun ignitionOff() {
        val io = ScriptedElm(golfClone + ("0100" to "SEARCHING...\rUNABLE TO CONNECT\r"))
        val e = assertFailsWith<VehicleNotRespondingException> { runBlocking { ObdSession(io).initialize() } }
        assertEquals(ElmError.UNABLE_TO_CONNECT, e.error)
    }

    @Test fun carSearchTimesOut() {
        val io = ScriptedElm(golfClone + ("0100" to null))
        assertFailsWith<VehicleNotRespondingException> { runBlocking { ObdSession(io).initialize() } }
    }

    // --- poll ---

    @Test fun pollReadsValues() = runBlocking {
        val s = ObdSession(ScriptedElm(golfClone)).also { it.initialize() }
        val d = s.poll().take(1).toList().last()
        assertEquals(1726.0, d.rpm)
        assertEquals(72.0, d.speedKmh)
        assertEquals(83.0, d.coolantC)
        assertEquals(6.0, d.ambientC)
        assertEquals(12.6, d.batteryV)
    }

    @Test fun pollSkipsUnsupportedPids() = runBlocking {
        // An older car: no ambient temperature (no 0140 range), no throttle PID.
        val car = golfClone + mapOf("0100" to "4100BE3F0000\r")
        val io = ScriptedElm(car)
        val s = ObdSession(io).also { it.initialize() }
        assertFalse(s.supported.isSupported(Pid.THROTTLE))
        s.poll().take(2).toList()
        assertFalse("0111" in io.sent)
    }

    @Test fun pollSurvivesOneTimeout() = runBlocking {
        val io = ScriptedElm(golfClone)
        val s = ObdSession(io).also { it.initialize() }
        io.timeOut("010D", 1)
        val d = s.poll().take(2).toList().last()
        assertEquals(72.0, d.speedKmh)
    }

    @Test fun pollGivesUpWhenTheCarIsGone() = runBlocking {
        val io = ScriptedElm(golfClone)
        val s = ObdSession(io, maxMisses = 6).also { it.initialize() }
        listOf("010C", "010D", "0104", "0111", "0105", "010F", "0146").forEach { io.timeOut(it, 100) }
        val e = assertFailsWith<IOException> { s.poll().take(10).toList() }
        assertTrue(e.message!!.contains("Kontak"))
    }

    @Test fun demoAdapterLabel() = runBlocking {
        val s = ObdSession(DemoElmIo()).also { it.initialize() }
        assertEquals("ELM327 v2.3 · CAN 11 bit 500k", s.adapter.label)
        assertNotNull(s.poll().take(1).toList().last().ambientC)
    }
}
