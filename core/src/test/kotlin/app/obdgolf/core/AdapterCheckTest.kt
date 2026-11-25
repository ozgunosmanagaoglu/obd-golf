package app.obdgolf.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdapterCheckTest {
    private val ok = mapOf("ATCRA" to "OK\r", "ATFCSM0" to "OK\r")
    private fun info(elm: String? = "ELM327 v2.3", stn: String? = null) = AdapterInfo(elmVersion = elm, stnId = stn)

    @Test fun genuineElm() {
        val c = AdapterCheck.judge(info(), ok, "?\r")
        assertEquals(AdapterCheck.Verdict.OK, c.verdict)
        assertTrue(c.reasons.isEmpty())
        assertTrue(c.canTalkToUnits)
    }

    @Test fun stnChip() = assertEquals(AdapterCheck.Verdict.STN, AdapterCheck.judge(info(stn = "STN2120 v5.6.19"), ok, "?\r").verdict)

    @Test fun v15IsAClone() {
        val c = AdapterCheck.judge(info("ELM327 v1.5"), ok, "?\r")
        assertEquals(AdapterCheck.Verdict.CLONE, c.verdict)
        assertEquals(listOf(AdapterCheck.Reason.FAKE_V15), c.reasons)
        assertTrue(c.canTalkToUnits) // a clone that knows the commands can still try
    }

    @Test fun acceptsAnything() {
        val c = AdapterCheck.judge(info(), ok, "OK\r")
        assertEquals(AdapterCheck.Verdict.CLONE, c.verdict)
        assertTrue(AdapterCheck.Reason.ACCEPTS_ANYTHING in c.reasons)
    }

    @Test fun missingFlowControlIsLimited() {
        val c = AdapterCheck.judge(info("ELM327 v1.5"), mapOf("ATCRA" to "OK\r", "ATFCSM0" to "?\r"), "?\r")
        assertEquals(AdapterCheck.Verdict.LIMITED, c.verdict)
        assertEquals(listOf("ATFCSM0"), c.missing)
        assertFalse(c.canTalkToUnits)
        assertTrue("ATFCSM0" in c.logLine())
    }

    @Test fun timeoutIsNotMissing() =
        assertEquals(AdapterCheck.Verdict.OK, AdapterCheck.judge(info(), mapOf("ATCRA" to "OK\r"), null).verdict)

    @Test fun demoAdapterPasses(): Unit = runBlocking {
        val s = ObdSession(DemoElmIo())
        s.initialize()
        assertEquals(AdapterCheck.Verdict.OK, s.adapter.check?.verdict)
    }
}
