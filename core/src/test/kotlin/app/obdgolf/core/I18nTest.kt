package app.obdgolf.core

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class I18nTest {
    @Test fun everyLanguageTranslatesEveryText() {
        for (lang in Lang.entries.filter { it != Lang.EN }) {
            val missing = Str.entries.filter { it !in lang.table }
            assertTrue(missing.isEmpty(), "${lang.code} is missing $missing")
        }
    }

    @Test fun translationsKeepPlaceholders() {
        for (lang in Lang.entries) for ((key, text) in lang.table) {
            val found = Regex("\\{\\d}").findAll(text).map { it.value }.toSet()
            assertEquals(key.placeholders, found, "${lang.code} $key")
        }
    }

    @Test fun everyPidAndDtcKindHasAText() {
        Pid.entries.forEach { it.labelKey }   // throws if a PID_ key is missing
        assertEquals(Pid.entries.size, Str.entries.count { it.name.startsWith("PID_") })
        assertEquals("Devir", Strings(Lang.TR).pid(Pid.RPM))
        assertEquals("Pending", Strings(Lang.EN).dtcKind(DtcKind.PENDING))
    }

    @Test fun dtcTablesCoverTheSameCodes() {
        for (lang in Lang.entries) {
            val codes = DtcInfo.codes(lang)
            if (codes.isNotEmpty()) assertEquals(DtcInfo.codes(Lang.EN), codes, lang.code)
        }
    }

    @Test fun picksLanguageFromPhoneLocale() {
        assertEquals(Lang.TR, Lang.forLocale(Locale("tr", "TR")))
        assertEquals(Lang.EN, Lang.forLocale(Locale.US))
        assertEquals(Lang.EN, Lang.forLocale(Locale("xx")))
        assertEquals(Lang.TR, Lang.fromCode("tr-TR"))
        assertEquals(Lang.PT, Lang.forLocale(Locale("pt", "BR")))
        assertEquals(Lang.UK, Lang.forLocale(Locale("uk", "UA")))
    }

    @Test fun noTranslationIsBlankOrUntrimmed() {
        for (lang in Lang.entries) for ((key, text) in lang.table) {
            assertTrue(text.isNotBlank() && text == text.trim(), "${lang.code} $key")
        }
    }

    @Test fun languageCodesAreUnique() {
        assertEquals(Lang.entries.size, Lang.entries.map { it.code }.toSet().size)
    }

    @Test fun formatsArgumentsAndNestedMessages() {
        val tr = Strings(Lang.TR)
        assertEquals("Okunamadı: Bağlantı hatası", tr.of(Msg.of(Str.READ_FAILED, Msg.of(Str.CONNECTION_ERROR))))
        assertEquals("Okunamadı: socket closed", tr.of(Msg.of(Str.READ_FAILED, Msg.Raw("socket closed"))))
        assertEquals("1 h 5 min", Strings(Lang.EN).format(Str.HOURS_MINUTES, 1, 5))
    }

    @Test fun localizedErrorsKeepEnglishMessageAndKey() {
        val e = ElmTimeoutException("0100")
        assertEquals("The adapter didn't answer \"0100\" in time", e.message)
        assertEquals("Adaptör \"0100\" komutuna zamanında cevap vermedi", Strings(Lang.TR).of(e.toMsg()))
        assertEquals(Msg.Raw("boom"), RuntimeException("boom").toMsg())
        assertEquals(Msg.Key(Str.CONNECTION_ERROR), RuntimeException().toMsg())
    }
}
