package app.obdgolf.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadinessTest {
    @Test fun petrolAllComplete() {
        // B=07: misfire, fuel, components present and complete. C=65: catalyst, EVAP, O2, O2 heater. D=00.
        val r = assertNotNull(Readiness.parse("41 01 00 07 65 00\r"))
        assertFalse(r.diesel)
        assertEquals(
            listOf(Monitor.MISFIRE, Monitor.FUEL_SYSTEM, Monitor.COMPONENTS, Monitor.CATALYST, Monitor.EVAP, Monitor.O2_SENSOR, Monitor.O2_HEATER),
            r.monitors.map { it.monitor },
        )
        assertTrue(r.allComplete)
    }

    @Test fun petrolAfterClearingCodes() {
        // D=25: catalyst, EVAP and O2 sensor not complete yet.
        val r = assertNotNull(Readiness.parse("4101000765 25"))
        assertEquals(listOf(Monitor.CATALYST, Monitor.EVAP, Monitor.O2_SENSOR), r.incomplete)
        assertEquals(4, r.completeCount)
        assertFalse(r.allComplete)
    }

    @Test fun incompleteInByteB() {
        // B=17: misfire present but not complete.
        val r = assertNotNull(Readiness.parse("4101001700 00"))
        assertEquals(listOf(Monitor.MISFIRE), r.incomplete)
    }

    @Test fun diesel() {
        // B=0F: diesel. C=C2: NOx, particulate filter, EGR. D=40: particulate filter not complete.
        val r = assertNotNull(Readiness.parse("4101000FC240"))
        assertTrue(r.diesel)
        assertEquals(listOf(Monitor.NOX, Monitor.PARTICULATE_FILTER, Monitor.EGR), r.monitors.drop(3).map { it.monitor })
        assertEquals(listOf(Monitor.PARTICULATE_FILTER), r.incomplete)
    }

    @Test fun engineWinsOverGearbox() {
        // The gearbox answers too, with no monitors.
        val r = assertNotNull(Readiness.parse("41010000000000\r410100076500\r"))
        assertEquals(7, r.monitors.size)
    }

    @Test fun noData() {
        assertNull(Readiness.parse("NO DATA\r"))
        assertNull(Readiness.parse("410100\r"))
    }

    @Test fun demoFaultsShowOneIncomplete(): Unit = runBlocking {
        val s = ObdSession(DemoElmIo())
        s.initialize()
        val r = assertNotNull(s.readFaults(0).readiness)
        assertEquals(listOf(Monitor.EVAP), r.incomplete)
    }
}
