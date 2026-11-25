package app.obdgolf.core

import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Replies with scripted chunks, the way a real adapter splits its answer. */
private class FakeTransport(private val replies: Map<String, List<String>>) : Transport {
    val written = mutableListOf<String>()
    val queue = ArrayDeque<ByteArray>()
    var closed = false

    override suspend fun write(data: ByteArray) {
        val cmd = String(data, Charsets.US_ASCII)
        written += cmd
        replies[cmd.trimEnd('\r')]?.forEach { queue.addLast(it.toByteArray(Charsets.ISO_8859_1)) }
    }

    override suspend fun read(timeoutMs: Long): ByteArray {
        if (closed) throw IOException("closed")
        return queue.removeFirstOrNull() ?: ByteArray(0)
    }

    override fun close() { closed = true }
}

class TransportElmIoTest {

    @Test
    fun `sends the command with a carriage return and returns the text before the prompt`() = runBlocking {
        val t = FakeTransport(mapOf("010C" to listOf("410C1AF8\r\r>")))
        val reply = TransportElmIo(t).exchange("010C")
        assertEquals(listOf("010C\r"), t.written)
        assertEquals("410C1AF8\r\r", reply)
    }

    @Test
    fun `joins a reply that arrives in small pieces like BLE notifications`() = runBlocking {
        val t = FakeTransport(mapOf("0100" to listOf("41 00 BE", " 3F A8 13", "\r", "\r>")))
        assertEquals("41 00 BE 3F A8 13\r\r", TransportElmIo(t).exchange("0100"))
    }

    @Test
    fun `drops bytes left over from an earlier reply before sending`() = runBlocking {
        val t = FakeTransport(mapOf("010D" to listOf("410D32\r>")))
        t.queue.addLast("STOPPED\r>".toByteArray())
        assertEquals("410D32\r", TransportElmIo(t).exchange("010D"))
    }

    @Test
    fun `ignores NUL padding some clones send`() = runBlocking {
        val t = FakeTransport(mapOf("ATRV" to listOf("\u000014.1V\r\u0000>")))
        assertEquals("14.1V\r", TransportElmIo(t).exchange("ATRV"))
    }

    @Test
    fun `times out when the prompt never comes`() = runBlocking {
        var now = 0L
        val t = FakeTransport(mapOf("0105" to listOf("4105")))
        val io = TransportElmIo(object : Transport by t {
            override suspend fun read(timeoutMs: Long): ByteArray { now += 500; return t.read(timeoutMs) }
        }) { now }
        val e = assertFailsWith<ElmTimeoutException> { io.exchange("0105", timeoutMs = 2_000) }
        assertEquals("0105", e.command)
    }

    @Test
    fun `close closes the transport`() {
        val t = FakeTransport(emptyMap())
        TransportElmIo(t).close()
        assertTrue(t.closed)
    }

    @Test
    fun `stream transport works end to end with ObdSession parsing`() = runBlocking {
        // Like a real adapter: the reply only shows up after the command was written.
        val output = ByteArrayOutputStream()
        val reply = "410C1AF8\r\r>".toByteArray()
        var pos = 0
        val input = object : InputStream() {
            override fun available() = if (output.size() > 0) reply.size - pos else 0
            override fun read(): Int = if (pos < reply.size) reply[pos++].toInt() else -1
        }
        val io = TransportElmIo(StreamTransport(input, output))
        assertEquals("410C1AF8\r\r", io.exchange("010C", timeoutMs = 500))
        assertEquals("010C\r", output.toString(Charsets.US_ASCII))
    }

    @Test
    fun `stream transport returns empty on timeout instead of blocking`() = runBlocking {
        val t = StreamTransport(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream())
        assertEquals(0, t.read(20).size)
    }
}
