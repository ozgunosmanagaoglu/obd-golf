package app.obdgolf.core

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ElmParserTest {
    @Test fun rpm() = assertEquals(1726.0, ElmParser.parsePid(Pid.RPM, "41 0C 1A F8\r\r"))
    @Test fun rpmCompact() = assertEquals(1726.0, ElmParser.parsePid(Pid.RPM, "410C1AF8"))
    @Test fun speed() = assertEquals(72.0, ElmParser.parsePid(Pid.SPEED, "410D48"))
    @Test fun coolant() = assertEquals(90.0, ElmParser.parsePid(Pid.COOLANT_TEMP, "SEARCHING...\r410582\r"))
    @Test fun noData() = assertNull(ElmParser.parsePid(Pid.RPM, "NO DATA\r"))
    @Test fun wrongPid() = assertNull(ElmParser.parsePid(Pid.RPM, "410D48"))
    @Test fun truncated() = assertNull(ElmParser.parsePid(Pid.RPM, "410C1A"))
    @Test fun twoEcus() = assertEquals(72.0, ElmParser.parsePid(Pid.SPEED, "7E8 03 41 0D 48\r410D48\r"))
    @Test fun voltage() = assertEquals(14.2, ElmParser.parseVoltage("14.2V\r"))

    @Test fun demoSessionProducesData(): Unit = runBlocking {
        val session = ObdSession(DemoElmIo())
        session.initialize()
        assertEquals("ELM327 v2.3", session.adapterVersion)
        val d = session.poll().take(2).toList().last()
        assertNotNull(d.rpm); assertNotNull(d.speedKmh); assertNotNull(d.coolantC); assertNotNull(d.batteryV)
    }
}
