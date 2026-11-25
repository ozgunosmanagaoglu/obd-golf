package app.obdgolf.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DtcTest {
    @Test fun decode() {
        assertEquals("P0133", DtcParser.decode("0133"))
        assertEquals("C0300", DtcParser.decode("4300"))
        assertEquals("B1234", DtcParser.decode("9234"))
        assertEquals("U0100", DtcParser.decode("C100"))
        assertEquals("P2015", DtcParser.decode("2015"))
    }

    @Test fun canSingleFrame() = assertEquals(listOf("P0299", "P0301"), DtcParser.parse("43 02 02 99 03 01\r", "03"))
    @Test fun canNoCodes() = assertEquals(emptyList(), DtcParser.parse("4300\r", "03"))
    @Test fun noData() = assertEquals(emptyList(), DtcParser.parse("NO DATA\r", "03"))
    @Test fun oldFormat() = assertEquals(listOf("P0133", "P0171"), DtcParser.parse("43 01 33 01 71 00 00\r", "03"))
    @Test fun pending() = assertEquals(listOf("P0171"), DtcParser.parse("47010171\r", "07"))

    @Test fun canMultiFrameAndSecondEcu() = assertEquals(
        listOf("P0299", "P0301", "P2015"),
        DtcParser.parse("008\r0:43 03 02 99 03 01\r1:20 15 00 00 00 00 00\r4300\r", "03"),
    )

    @Test fun twoEcusWithCodes() = assertEquals(
        listOf("P0299", "P0700"),
        DtcParser.parse("SEARCHING...\r43010299\r43010700\r", "03"),
    )

    @Test fun mil() {
        assertEquals(MilStatus(true, 3), DtcParser.parseMil("41 01 83 07 64 00\r"))
        assertEquals(MilStatus(false, 0), DtcParser.parseMil("4101000764 00"))
        assertNull(DtcParser.parseMil("NO DATA"))
    }

    @Test fun freeze() {
        assertEquals("P0299", DtcParser.parseFreezeDtc("4202000299\r"))
        assertNull(DtcParser.parseFreezeDtc("4202000000\r"))
        assertEquals(2850.0, DtcParser.parseFreezePid(Pid.RPM, "420C002C88\r"))
    }

    @Test fun descriptions() {
        assertTrue(DtcInfo.describe("P0299").contains("Turbo"))
        assertEquals("Manufacturer-specific code (Speed, idle and electrics)", DtcInfo.describe("P1570"))
        assertEquals("Ateşleme / tekleme", DtcInfo.describe("P0399", Lang.TR))
        assertEquals("Turbo aşırı basınç (overboost)", DtcInfo.describe("P0234", Lang.TR))
        assertTrue(DtcInfo.isManufacturerSpecific("U1013"))
        assertFalse(DtcInfo.isManufacturerSpecific("P0420"))
    }

    @Test fun demoReadAndClear(): Unit = runBlocking {
        val s = ObdSession(DemoElmIo())
        s.initialize()
        val r = s.readFaults()
        assertEquals(MilStatus(true, 3), r.mil)
        assertEquals(listOf("P0299", "P0301", "P2015", "P0171"), r.codes.map { it.code })
        assertEquals(DtcKind.PENDING, r.codes.last().kind)
        assertEquals("P0299", r.freezeFrame?.dtc)
        assertEquals(2850.0, r.freezeFrame?.rpm)
        assertEquals(88.0, r.freezeFrame?.coolantC)
        assertTrue(s.clearFaults())
        val after = s.readFaults()
        assertEquals(MilStatus(false, 0), after.mil)
        assertTrue(after.codes.isEmpty())
        assertNull(after.freezeFrame)
    }
}
