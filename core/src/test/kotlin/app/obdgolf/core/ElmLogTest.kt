package app.obdgolf.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

class ElmLogSetupTest {
    @Test fun setupSurvivesALongDrive() {
        var t = 0L
        val log = ElmLog(capacity = 5, clock = { t++ })
        log.beginAttempt()
        log.add(ElmLog.Kind.SENT, "ATZ")
        log.add(ElmLog.Kind.RECEIVED, "ELM327 v2.3")
        log.connected()
        repeat(10) { log.add(ElmLog.Kind.SENT, "010C") }
        val text = log.export()
        assertTrue("--- Last successful connection ---" in text)
        assertTrue("> ATZ" in text)
        assertEquals(5, text.lines().count { it.endsWith("> 010C") })
    }

    @Test fun failedAttemptKeepsTheGoodSetup() {
        val log = ElmLog(capacity = 3)
        log.beginAttempt()
        log.add(ElmLog.Kind.SENT, "GOOD")
        log.connected()
        log.beginAttempt()
        log.add(ElmLog.Kind.SENT, "BAD")
        repeat(5) { log.add(ElmLog.Kind.SENT, "x") }
        assertEquals(listOf("GOOD"), log.setupSnapshot().map { it.text })
    }

    @Test fun noSectionWhileSetupIsStillInTheRing() {
        val log = ElmLog()
        log.beginAttempt()
        log.add(ElmLog.Kind.SENT, "ATZ")
        log.connected()
        assertFalse("---" in log.export())
    }
}

class ElmLogVinTest {
    @Test fun maskVin() {
        kotlin.test.assertEquals("WVWZZZ1KZB*******", ElmLog.maskVin("WVWZZZ1KZBP123456"))
    }

    @Test fun vinReplyIsNeverLogged(): Unit = kotlinx.coroutines.runBlocking {
        val log = ElmLog()
        LoggingElmIo(DemoElmIo(), log).exchange("0902")
        kotlin.test.assertEquals("(VIN hidden)", log.snapshot().last().text)
    }
}
