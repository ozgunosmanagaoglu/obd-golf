package app.obdgolf.core

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnitInfoTest {
    @Test fun udsSingleFrame() =
        assertEquals("1K0", UnitIdent.ascii(UnitIdent.udsData("F187", "62F187314B30\r")))

    @Test fun udsMultiFrame() {
        val data = assertNotNull(UnitIdent.udsData("0600", "00A\r0:62060001020304\r1:05060708000000\r"))
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), data.toList())
    }

    @Test fun udsNegativeOrMissing() {
        assertNull(UnitIdent.udsData("F187", "7F2231\r"))
        assertNull(UnitIdent.udsData("F187", "NO DATA\r"))
        assertNull(UnitIdent.udsData("F187", "62F189314B30\r")) // another identifier
    }

    @Test fun udsWaitsThroughResponsePending() =
        assertEquals("AB", UnitIdent.ascii(UnitIdent.udsData("F189", "7F2278\r62F1894142\r")))

    @Test fun kwpIdentTakesReadableRuns() {
        val payload = intArrayOf(0x5A, 0x9B) + "03C906027BH ".map { it.code } + intArrayOf(0, 1) + "1.4l R4/4V TFSI  0010".map { it.code }
        val info = assertNotNull(UnitIdent.kwp(ControlUnit.ENGINE, payload))
        assertEquals("03C906027BH", info.partNumber)
        assertEquals("1.4l R4/4V TFSI  0010", info.systemName)
        assertEquals(UnitInfo.Protocol.KWP2000, info.protocol)
        assertTrue(info.raw!!.startsWith("5A9B"))
    }

    @Test fun kwpNegativeIsNull() = assertNull(UnitIdent.kwp(ControlUnit.ENGINE, intArrayOf(0x7F, 0x1A, 0x31)))

    @Test fun demoReadsUdsAndKwpUnits(): Unit = runBlocking {
        val s = ObdSession(DemoElmIo()).also { it.initialize() }
        val bcm = assertNotNull(s.readUnitInfo(ControlUnit.CENTRAL_ELECTRICS))
        assertEquals(UnitInfo.Protocol.UDS, bcm.protocol)
        assertEquals("5K0937087AC", bcm.partNumber)
        assertEquals("0507", bcm.softwareVersion)
        assertEquals(30, bcm.coding?.size)
        assertEquals("0E1A00070F", bcm.codingHex?.take(10))

        val engine = assertNotNull(s.readUnitInfo(ControlUnit.ENGINE))
        assertEquals(UnitInfo.Protocol.KWP2000, engine.protocol)
        assertEquals("03C906027BH", engine.partNumber)

        assertNull(s.readUnitInfo(ControlUnit.AIRBAG))
        // Live values still work afterwards: the adapter is back in normal OBD mode.
        assertNotNull(s.poll().first().rpm)
    }

    @Test fun logLineHasTheEssentials() {
        val line = UnitInfo(ControlUnit.CENTRAL_ELECTRICS, UnitInfo.Protocol.UDS, "5K0937087AC", "0507", coding = listOf(0x0E, 0x1A)).logLine()
        assertEquals("Unit 09 (UDS), part 5K0937087AC, SW 0507, coding 0E1A", line)
    }
}
