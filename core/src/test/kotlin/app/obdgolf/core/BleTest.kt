package app.obdgolf.core

import app.obdgolf.core.GattChar.Companion.PROP_INDICATE
import app.obdgolf.core.GattChar.Companion.PROP_NOTIFY
import app.obdgolf.core.GattChar.Companion.PROP_WRITE
import app.obdgolf.core.GattChar.Companion.PROP_WRITE_NO_RESPONSE
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun u(short: String) = "0000$short-0000-1000-8000-00805f9b34fb"
private val gap = GattService(u("1800"), listOf(GattChar(u("2a00"), 0x02)))
private val battery = GattService(u("180f"), listOf(GattChar(u("2a19"), PROP_NOTIFY or 0x02)))

class BleTest {

    // --- picking the OBD channel ---

    @Test fun vgateFff0() {
        val s = GattService(u("fff0"), listOf(GattChar(u("fff1"), PROP_NOTIFY), GattChar(u("fff2"), PROP_WRITE_NO_RESPONSE)))
        assertEquals(BleChannel(u("fff0"), u("fff2"), u("fff1")), BleProfiles.pick(listOf(gap, battery, s)))
    }

    @Test fun hm10SingleCharacteristic() {
        val s = GattService(u("ffe0"), listOf(GattChar(u("ffe1"), PROP_NOTIFY or PROP_WRITE_NO_RESPONSE)))
        assertEquals(BleChannel(u("ffe0"), u("ffe1"), u("ffe1")), BleProfiles.pick(listOf(gap, s)))
    }

    @Test fun vlinker18f0() {
        val s = GattService(u("18f0"), listOf(GattChar(u("2af0"), PROP_NOTIFY), GattChar(u("2af1"), PROP_WRITE)))
        assertEquals(BleChannel(u("18f0"), u("2af1"), u("2af0")), BleProfiles.pick(listOf(s)))
    }

    @Test fun upperCaseUuidsAreFine() {
        val s = GattService(u("fff0").uppercase(), listOf(
            GattChar(u("fff1").uppercase(), PROP_NOTIFY), GattChar(u("fff2").uppercase(), PROP_WRITE)))
        assertEquals(u("fff2").uppercase(), BleProfiles.pick(listOf(s))?.writeUuid)
    }

    @Test fun unknownVendorServiceFallback() {
        val vendor = GattService("12345678-1234-1234-1234-123456789abc", listOf(
            GattChar("aaaa0001-1234-1234-1234-123456789abc", PROP_INDICATE),
            GattChar("aaaa0002-1234-1234-1234-123456789abc", PROP_WRITE),
        ))
        val ch = BleProfiles.pick(listOf(gap, battery, vendor))!!
        assertEquals("aaaa0001-1234-1234-1234-123456789abc", ch.notifyUuid)
        assertEquals("aaaa0002-1234-1234-1234-123456789abc", ch.writeUuid)
    }

    @Test fun batteryServiceIsNeverPicked() = assertNull(BleProfiles.pick(listOf(gap, battery)))

    @Test fun knownLayoutWinsOverVendorService() {
        val vendor = GattService("12345678-1234-1234-1234-123456789abc", listOf(GattChar("aaaa0001-1234-1234-1234-123456789abc", PROP_NOTIFY or PROP_WRITE)))
        val s = GattService(u("fff0"), listOf(GattChar(u("fff1"), PROP_NOTIFY), GattChar(u("fff2"), PROP_WRITE)))
        assertEquals(u("fff0"), BleProfiles.pick(listOf(vendor, s))?.serviceUuid)
    }

    @Test fun names() {
        assertTrue(BleProfiles.looksLikeObd("IOS-Vlink"))
        assertTrue(BleProfiles.looksLikeObd("OBDII"))
        assertTrue(BleProfiles.looksLikeObd("OBDLink CX"))
        assertTrue(!BleProfiles.looksLikeObd("Mi Band 7"))
        assertTrue(!BleProfiles.looksLikeObd(null))
    }

    // --- push transport ---

    @Test fun writesAreCutInto20BytePackets() = runBlocking {
        val sent = mutableListOf<ByteArray>()
        val t = PushTransport({ sent += it })
        t.write(ByteArray(45) { 'A'.code.toByte() })
        assertEquals(listOf(20, 20, 5), sent.map { it.size })
    }

    @Test fun biggerMtuMeansBiggerPackets() = runBlocking {
        val sent = mutableListOf<ByteArray>()
        val t = PushTransport({ sent += it }, chunkSize = 100)
        t.write(ByteArray(45))
        assertEquals(listOf(45), sent.map { it.size })
    }

    @Test fun readJoinsWaitingNotifications() = runBlocking {
        val t = PushTransport({})
        t.onBytes("41 0C".toByteArray()); t.onBytes(" 1A F8\r".toByteArray())
        assertEquals("41 0C 1A F8\r", String(t.read(100)))
        assertEquals(0, t.read(0).size)
    }

    @Test fun readTimesOutWithEmpty() = runBlocking { assertEquals(0, PushTransport({}).read(20).size) }

    @Test fun droppedLinkFailsReads() {
        val t = PushTransport({})
        t.onClosed("Adaptör bağlantısı koptu")
        val e = assertFailsWith<IOException> { runBlocking { t.read(100) } }
        assertEquals("Adaptör bağlantısı koptu", e.message)
        assertFailsWith<IOException> { runBlocking { t.write("ATZ\r".toByteArray()) } }
    }

    @Test fun elmFramingOverBleLikePackets() = runBlocking {
        // A BLE adapter answering in 20-byte notifications, driven by TransportElmIo.
        lateinit var t: PushTransport
        t = PushTransport({ cmd ->
            if (String(cmd).startsWith("0100")) "SEARCHING...\r4100BE3FA813\r\r>".toByteArray().toList().chunked(20).forEach { t.onBytes(it.toByteArray()) }
        })
        val reply = TransportElmIo(t).exchange("0100")
        assertEquals(setOf(0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x13, 0x15, 0x1C, 0x1F, 0x20),
            SupportedPids.parseBitmap(0, reply))
    }
}
