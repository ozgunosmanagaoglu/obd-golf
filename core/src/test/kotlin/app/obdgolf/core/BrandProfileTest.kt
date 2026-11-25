package app.obdgolf.core

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A VW on CAN 11/500 that remembers the ATSH header, so replies can depend on which control unit is asked.
 * [units] maps "header:command" to a reply; anything else answers like [Elm327Test]'s clone.
 */
private class UnitElm(private val units: Map<String, String>, private val vin: Boolean = true) : ElmIo {
    val sent = mutableListOf<String>()
    var header = "7DF"

    override suspend fun exchange(command: String, timeoutMs: Long): String {
        sent += command
        if (command.startsWith("ATSH")) {
            header = command.removePrefix("ATSH")
            return "OK\r"
        }
        units["$header:$command"]?.let { return it }
        return when (command) {
            "ATZ" -> "ELM327 v1.5\r"
            "0100" -> "4100BE3FA813\r"
            "ATDPN" -> "A6\r"
            "0902" -> if (vin) "014\r0:490201575657\r1:5A5A5A314B5A42\r2:50303030303030\r" else "NO DATA\r"
            "010C" -> "410C1AF8\r"
            "010D" -> "410D48\r"
            "0104" -> "41044D\r"
            "0111" -> "411120\r"
            "0105" -> "41057B\r"
            "010F" -> "410F46\r"
            "0146" -> "41462E\r"
            "03" -> if (header == "7DF") "4300\r" else "WRONG UNIT\r"
            else -> if (command.startsWith("AT")) "OK\r" else "NO DATA\r"
        }
    }

    override fun close() {}
}

class BrandProfileTest {
    private val oil = BrandProfiles.VAG.values.first { it.id == "vag.oil_temp" }
    private val gearbox = BrandProfiles.VAG.values.first { it.id == "vag.gearbox_temp" }

    @Test fun oilTemperature() = assertEquals(90.0, BrandProfiles.parse(oil, "62 11 BE 0E 2F\r")) // 3631 → 90.0 °C
    @Test fun gearboxTemperature() = assertEquals(65.0, BrandProfiles.parse(gearbox, "622104 41\r"))
    @Test fun negativeReplyIsNull() = assertNull(BrandProfiles.parse(oil, "7F 22 31\r"))
    @Test fun noDataIsNull() = assertNull(BrandProfiles.parse(oil, "NO DATA\r"))
    @Test fun otherIdentifierIsNull() = assertNull(BrandProfiles.parse(oil, "622104 41\r"))
    @Test fun tooShortIsNull() = assertNull(BrandProfiles.parse(oil, "6211BE0D\r"))
    @Test fun outOfRangeIsHidden() = assertNull(BrandProfiles.parse(oil, "6211BEFFFF\r")) // 6280 °C: means something else
    @Test fun vagHasAProfile() = assertEquals(BrandProfiles.VAG, BrandProfiles.forGroup(MakerGroup.VAG))
    @Test fun othersHaveNone() = assertNull(BrandProfiles.forGroup(MakerGroup.BMW))

    @Test fun probeKeepsOnlyAnsweringValues() = runBlocking {
        val io = UnitElm(mapOf("7E0:2211BE" to "6211BE0E2F\r", "7E1:222104" to "7F2211\r"))
        val s = ObdSession(io).also { it.initialize() }
        assertEquals(listOf(oil), s.brandValues)
        assertEquals("7E0", io.header) // back on the engine
    }

    @Test fun pollReadsBrandValues() = runBlocking {
        val io = UnitElm(mapOf("7E0:2211BE" to "6211BE0E2F\r", "7E1:222104" to "62210441\r"))
        val s = ObdSession(io).also { it.initialize() }
        val d = s.poll().first()
        assertEquals(90.0, d.brand[oil])
        assertEquals(65.0, d.brand[gearbox])
        assertEquals("7E0", io.header)
    }

    @Test fun faultsGoToAllUnitsAfterBrandReads() = runBlocking {
        val io = UnitElm(mapOf("7E0:2211BE" to "6211BE0E2F\r"))
        val s = ObdSession(io).also { it.initialize() }
        s.poll().first()
        assertTrue(s.readFaults().codes.isEmpty())
        assertTrue("ATSH7DF" in io.sent.takeLast(8)) // codes were asked from every unit
        assertEquals("7E0", io.header)
    }

    @Test fun noProbeWithoutVin() = runBlocking {
        val io = UnitElm(mapOf("7E0:2211BE" to "6211BE0E2F\r"), vin = false)
        val s = ObdSession(io).also { it.initialize() }
        assertTrue(s.brandValues.isEmpty())
        assertTrue("2211BE" !in io.sent)
    }

    @Test fun demoCarAnswersBothValues() = runBlocking {
        val s = ObdSession(DemoElmIo()).also { it.initialize() }
        assertEquals(listOf(oil, gearbox), s.brandValues)
    }
}
