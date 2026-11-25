package app.obdgolf.core

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VehicleTest {

    @Test fun vinFromCanMultiFrame() = assertEquals(
        "WVWZZZ1KZBP000000",
        Vin.parse("014\r0: 49 02 01 57 56 57\r1: 5A 5A 5A 31 4B 5A 42\r2: 50 30 30 30 30 30 30\r"),
    )

    @Test fun vinFromOldFiveLineFormat() = assertEquals(
        "1G1JC5444R7252367",
        Vin.parse(
            "49 02 01 00 00 00 31\r49 02 02 47 31 4A 43\r49 02 03 35 34 34 34\r" +
                "49 02 04 52 37 32 35\r49 02 05 32 33 36 37\r"
        ),
    )

    @Test fun noVin() = assertNull(Vin.parse("NO DATA\r"))
    @Test fun shortVinIsRejected() = assertNull(Vin.parse("49020157565700\r"))
    @Test fun lettersIOQAreInvalid() = assertFalse(Vin.isValid("WVWZZZ1KZBP00000O"))

    @Test fun decodeGolf() {
        val v = Vin.decode("WVWZZZ1KZBP000000", maxYear = 2027)
        assertEquals("Volkswagen", v.make)
        assertEquals(MakerGroup.VAG, v.group)
        assertEquals(2011, v.modelYear)
        assertEquals("Volkswagen 2011", v.label)
    }

    @Test fun decodeSkodaAndBmw() {
        assertEquals("Škoda", Vin.decode("TMBJJ7NE0K0000000", 2027).make)
        assertEquals(MakerGroup.BMW, Vin.decode("WBA8E1C50GK000000", 2027).group)
    }

    @Test fun twoLetterToyotaPrefix() = assertEquals("Toyota", Vin.decode("JTDKB20U093000000", 2027).make)

    @Test fun unknownMaker() {
        val v = Vin.decode("XYZ12345678900000", 2027)
        assertNull(v.make)
        assertEquals(MakerGroup.OTHER, v.group)
    }

    @Test fun modelYearPicksTheLatestCycle() {
        assertEquals(2011, Vin.modelYear("WVWZZZ1KZBP000000", 2027)) // B: 1981 or 2011
        assertEquals(2001, Vin.modelYear("WVWZZZ1JZ1W000000", 2027)) // 1: 2001 (2031 is in the future)
        assertEquals(2026, Vin.modelYear("WVWZZZ1KZTP000000", 2027)) // T: 1996 or 2026
        assertNull(Vin.modelYear("WVWZZZ1KZUP000000", 2027))         // U is not a year code
    }

    @Test fun extraValueIsStoredAndRemoved() {
        val d = CarData().with(Pid.FUEL_LEVEL, 62.0)
        assertEquals(62.0, d.extra[Pid.FUEL_LEVEL])
        assertTrue(Pid.FUEL_LEVEL !in d.with(Pid.FUEL_LEVEL, null).extra)
    }

    @Test fun newPidFormulas() {
        assertEquals(-3.1, ElmParser.parsePid(Pid.LONG_FUEL_TRIM_1, "41077C")!!, 0.05)
        assertEquals(14.1, ElmParser.parsePid(Pid.MODULE_VOLTAGE, "41423714")!!, 0.001)
        assertEquals(62.0, ElmParser.parsePid(Pid.FUEL_LEVEL, "412F9E")!!, 0.1)
        assertEquals(12.5, ElmParser.parsePid(Pid.FUEL_RATE, "415E00FA")!!, 0.001)
        assertEquals(1.0, ElmParser.parsePid(Pid.LAMBDA, "41448000")!!, 0.001)
    }

    @Test fun knownSupportedNeedsTheBitmap() {
        val s = SupportedPids().with(0x00, setOf(0x0C))
        assertTrue(s.isSupported(Pid.FUEL_LEVEL))       // range 0x20 never read: allowed for core values
        assertFalse(s.isKnownSupported(Pid.FUEL_LEVEL)) // but not polled as an extra
    }

    @Test fun demoSessionReadsVinAndExtras() = runBlocking {
        val s = ObdSession(DemoElmIo(), slowEvery = 1).also { it.initialize() }
        assertEquals("Volkswagen", s.vehicle?.make)
        val last = s.poll().take(6).toList().last()
        assertTrue(last.extra.containsKey(Pid.FUEL_LEVEL), "extras read: ${last.extra.keys}")
        assertEquals(62.0, last.extra[Pid.FUEL_LEVEL]!!, 0.1)
    }
}
