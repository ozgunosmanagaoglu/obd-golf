package app.obdgolf.core

import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TcpTransportTest {

    @Test fun parseFull() = assertEquals(WifiAddress("192.168.0.10", 35000), WifiAddress.parse(" 192.168.0.10:35000 "))
    @Test fun parseDefaultPort() = assertEquals(WifiAddress("192.168.1.10", 35000), WifiAddress.parse("192.168.1.10"))
    @Test fun parseTelnetPort() = assertEquals(23, WifiAddress.parse("192.168.0.10:23")?.port)
    @Test fun parseBadPort() = assertNull(WifiAddress.parse("192.168.0.10:abc"))
    @Test fun parsePortOutOfRange() = assertNull(WifiAddress.parse("192.168.0.10:70000"))
    @Test fun parseEmpty() = assertNull(WifiAddress.parse("  "))

    /** A Wi-Fi ELM327 on localhost that answers like the demo car. */
    private fun fakeWifiAdapter(): ServerSocket {
        val server = ServerSocket(0)
        thread(isDaemon = true) {
            server.accept().use { client ->
                val demo = DemoElmIo()
                val input = client.getInputStream()
                val output = client.getOutputStream()
                val cmd = StringBuilder()
                while (true) {
                    val b = input.read()
                    if (b < 0) break
                    if (b.toChar() == '\r') {
                        val reply = runBlocking { demo.exchange(cmd.toString()) }
                        output.write("$reply\r>".toByteArray(Charsets.US_ASCII))
                        output.flush()
                        cmd.clear()
                    } else {
                        cmd.append(b.toChar())
                    }
                }
            }
        }
        return server
    }

    @Test
    fun sessionOverTcp() = runBlocking {
        fakeWifiAdapter().use { server ->
            val transport = TcpTransport.open(WifiAddress("127.0.0.1", server.localPort))
            val session = ObdSession(TransportElmIo(transport))
            session.initialize()
            assertEquals("ELM327 v2.3 · CAN 11 bit 500k", session.adapter.label)
            session.close()
        }
    }

    @Test
    fun refusedConnectionExplainsWhat() {
        val port = ServerSocket(0).use { it.localPort } // nothing listens there any more
        val e = assertFailsWith<IOException> {
            runBlocking { TcpTransport.open(WifiAddress("127.0.0.1", port), timeoutMs = 1_000) }
        }
        assertTrue(e.message!!.contains("Wi-Fi"))
    }
}
