package app.obdgolf.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ElmLogTest {
    @Test fun keepsOnlyTheLastEntries() {
        val log = ElmLog(capacity = 3)
        repeat(5) { log.note("n$it") }
        assertEquals(listOf("n2", "n3", "n4"), log.snapshot().map { it.text })
    }

    @Test fun visibleControlCharacters() = assertEquals("41 0C\\r\\r\\0", ElmLog.visible("41 0C\r\r\u0000"))

    @Test fun logsCommandsRepliesAndErrors() = runBlocking {
        val log = ElmLog(clock = { 0L })
        val io = LoggingElmIo(object : ElmIo {
            override suspend fun exchange(command: String, timeoutMs: Long): String =
                if (command == "0105") throw ElmTimeoutException(command) else "410C1AF8\r"
            override fun close() {}
        }, log)
        io.exchange("010C")
        assertFailsWith<ElmTimeoutException> { io.exchange("0105") }
        assertEquals(
            listOf(ElmLog.Kind.SENT to "010C", ElmLog.Kind.RECEIVED to "410C1AF8\\r", ElmLog.Kind.SENT to "0105"),
            log.snapshot().take(3).map { it.kind to it.text },
        )
        assertEquals(ElmLog.Kind.ERROR, log.snapshot().last().kind)
    }

    @Test fun exportHasHeaderAndLines() {
        val log = ElmLog(clock = { 0L })
        log.add(ElmLog.Kind.SENT, "ATZ")
        val text = log.export(listOf("Revora 0.1.30", "Android 14"))
        assertTrue(text.startsWith("Revora 0.1.30\nAndroid 14\n\n"))
        assertTrue(text.trimEnd().endsWith("> ATZ"))
    }
}

class ElmLogVinTest {
    @Test fun maskVin() = kotlin.test.assertEquals("WVWZZZ1KZB*******", ElmLog.maskVin("WVWZZZ1KZBP123456"))

    @Test fun vinReplyIsNeverLogged() = kotlinx.coroutines.runBlocking {
        val log = ElmLog()
        LoggingElmIo(DemoElmIo(), log).exchange("0902")
        kotlin.test.assertEquals("(VIN hidden)", log.snapshot().last().text)
    }
}
