package me.foxtails.palustris.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AppFontDefaultsTest {
    @Test
    fun latinScriptLanguagesDefaultToInter() {
        listOf(
            AppLanguage.English,
            AppLanguage.German,
            AppLanguage.Spanish,
            AppLanguage.SpanishSpain,
            AppLanguage.SpanishLatinAmerica,
            AppLanguage.French,
            AppLanguage.PortugueseBrazil,
            AppLanguage.PortuguesePortugal,
            AppLanguage.Indonesian,
        ).forEach { language ->
            assertEquals(AppFont.Inter, defaultFontForLanguage(language))
        }
    }

    @Test
    fun cjkAndHindiLanguagesDefaultToTheirBundledFont() {
        assertEquals(AppFont.ZcoolKuaiLe, defaultFontForLanguage(AppLanguage.ChineseMainland))
        assertEquals(AppFont.ChocolateClassicalSans, defaultFontForLanguage(AppLanguage.ChineseTaiwan))
        assertEquals(AppFont.ChironHeiHk, defaultFontForLanguage(AppLanguage.CantoneseHongKong))
        assertEquals(AppFont.KiwiMaru, defaultFontForLanguage(AppLanguage.Japanese))
        assertEquals(AppFont.Sarala, defaultFontForLanguage(AppLanguage.Hindi))
    }

    @Test
    fun systemCoveredLanguagesKeepTheSystemFont() {
        listOf(
            AppLanguage.SystemDefault,
            AppLanguage.Chinese,
            AppLanguage.Korean,
            AppLanguage.Russian,
        ).forEach { language ->
            assertEquals(AppFont.Device, defaultFontForLanguage(language))
        }
    }
}
