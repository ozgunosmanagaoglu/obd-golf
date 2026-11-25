package app.obdgolf.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DtcSeverityTest {
    @Test fun overheatingMeansStop() = assertEquals(DtcSeverity.STOP, DtcSeverity.of("P0217"))
    @Test fun lowOilPressureMeansStop() = assertEquals(DtcSeverity.STOP, DtcSeverity.of("p0524"))
    @Test fun catalystCanWait() = assertEquals(DtcSeverity.LATER, DtcSeverity.of("P0420"))
    @Test fun evapLeakCanWait() = assertEquals(DtcSeverity.LATER, DtcSeverity.of("P0455"))
    @Test fun oxygenHeaterCanWait() = assertEquals(DtcSeverity.LATER, DtcSeverity.of("P0036"))
    @Test fun underboostSoon() = assertEquals(DtcSeverity.SOON, DtcSeverity.of("P0299"))
    @Test fun misfireSoon() = assertEquals(DtcSeverity.SOON, DtcSeverity.of("P0301"))
    @Test fun leanSoon() = assertEquals(DtcSeverity.SOON, DtcSeverity.of("P0171"))

    // Unknown means "get it checked", never "it can wait".
    @Test fun manufacturerCodeSoon() = assertEquals(DtcSeverity.SOON, DtcSeverity.of("P1234"))
    @Test fun chassisSoon() = assertEquals(DtcSeverity.SOON, DtcSeverity.of("C0035"))
    @Test fun networkSoon() = assertEquals(DtcSeverity.SOON, DtcSeverity.of("U0100"))
    @Test fun garbageSoon() = assertEquals(DtcSeverity.SOON, DtcSeverity.of("X"))

    @Test fun misfires() {
        assertTrue(DtcSeverity.isMisfire("P0300"))
        assertTrue(DtcSeverity.isMisfire("P0304"))
        assertTrue(DtcSeverity.isMisfire("P0312"))
        assertFalse(DtcSeverity.isMisfire("P0335"))
        assertFalse(DtcSeverity.isMisfire("P0420"))
    }

    @Test fun worstOfList() {
        val codes = listOf(Dtc("P0420", DtcKind.STORED), Dtc("P0299", DtcKind.STORED))
        assertEquals(DtcSeverity.SOON, DtcSeverity.worst(codes))
        assertEquals(DtcSeverity.STOP, DtcSeverity.worst(codes + Dtc("P0217", DtcKind.PENDING)))
        assertNull(DtcSeverity.worst(emptyList()))
    }

    /** Every code with an exact description gets a rating, and the "can wait" ones are emissions only. */
    @Test fun describedCodesRated() {
        for (code in DtcInfo.codes(Lang.EN)) {
            val sev = DtcSeverity.of(code)
            if (sev == DtcSeverity.LATER) assertTrue(code.startsWith("P0"), code)
        }
    }
}
