package app.obdgolf.core

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Like the owner's Golf Mk6 log: on 7DF the engine and the DSG both answer mode 01, with different rpm.
 * On 7E0 only the engine answers. [countSuffix] says whether the adapter understands "010C1".
 */
private class TwoUnitCar(private val countSuffix: Boolean = true, private val engineOnly: Boolean = true) : ElmIo {
    val sent = mutableListOf<String>()
    var header = "7DF"

    override suspend fun exchange(command: String, timeoutMs: Long): String {
        sent += command
        if (command.startsWith("ATSH")) { header = command.removePrefix("ATSH"); return "OK\r" }
        if (command == "ATZ") return "ELM327 v2.3\r"
        if (command == "ATDPN") return "A6\r"
        if (command.startsWith("AT") || command.startsWith("ST")) return "OK\r"
        val base = if (command.length == 5) {
            if (!countSuffix) return "?\r"
            command.dropLast(1)
        } else command
        val engine = when (base) {
            "0100" -> "4100BE3FA813"
            "010C" -> "410C1378"
            "010D" -> "410D30"
            "0104" -> "410432"
            "0111" -> "411127"
            "0105" -> "410581"
            "010F" -> "410F46"
            "0146" -> "414629"
            "03" -> "4300"
            else -> return "NO DATA\r"
        }
        val gearbox = when (base) {
            "0100" -> "410098180001"
            "010C" -> "410C13F0"
            "03" -> "4300"
            else -> null
        }
        return when {
            header == "7E0" && engineOnly -> "$engine\r"
            header == "7E0" -> "NO DATA\r"
            gearbox != null -> "$gearbox\r$engine\r"
            else -> "$engine\r"
        }
    }

    override fun close() {}
}

class EngineHeaderTest {
    @Test fun liveValuesComeFromTheEngineOnly(): Unit = runBlocking {
        val io = TwoUnitCar()
        val s = ObdSession(io).also { it.initialize() }
        assertEquals("7E0", s.homeHeader)
        assertTrue(s.singleReply)
        assertEquals(1246.0, s.poll().first().rpm) // 0x1378 / 4, not the gearbox's 0x13F0
        assertTrue("010C1" in io.sent)
    }

    @Test fun adapterWithoutReplyCount(): Unit = runBlocking {
        val io = TwoUnitCar(countSuffix = false)
        val s = ObdSession(io).also { it.initialize() }
        assertEquals("7E0", s.homeHeader)
        assertFalse(s.singleReply)
        assertEquals(1246.0, s.poll().first().rpm)
        assertTrue("010C" in io.sent)
    }

    @Test fun engineNotOn7E0StaysOnBroadcast(): Unit = runBlocking {
        val io = TwoUnitCar(engineOnly = false)
        val s = ObdSession(io).also { it.initialize() }
        assertEquals("7DF", s.homeHeader)
        assertEquals("7DF", io.header)
        assertFalse(s.singleReply)
    }

    @Test fun faultsAskEveryUnitThenGoBack(): Unit = runBlocking {
        val io = TwoUnitCar()
        val s = ObdSession(io).also { it.initialize() }
        io.sent.clear()
        s.readFaults()
        assertEquals("ATSH7DF", io.sent.first())
        assertEquals("ATSH7E0", io.sent.last())
        assertEquals("7E0", io.header)
    }
}
