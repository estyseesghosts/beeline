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

    @Test
    fun noFontChoiceFollowsTheDeviceLanguageUnderSystemDefault() {
        val preferences = AppPreferences()
        assertEquals(AppFont.Inter, preferences.effectiveFont(AppLanguage.English))
        assertEquals(AppFont.Sarala, preferences.effectiveFont(AppLanguage.Hindi))
        assertEquals(AppFont.KiwiMaru, preferences.effectiveFont(AppLanguage.Japanese))
        assertEquals(AppFont.ZcoolKuaiLe, preferences.effectiveFont(AppLanguage.ChineseMainland))
        assertEquals(AppFont.ChocolateClassicalSans, preferences.effectiveFont(AppLanguage.ChineseTaiwan))
        assertEquals(AppFont.ChironHeiHk, preferences.effectiveFont(AppLanguage.CantoneseHongKong))
        // A device language without a bundled default keeps the system font.
        assertEquals(AppFont.Device, preferences.effectiveFont(AppLanguage.Korean))
        assertEquals(AppFont.Device, preferences.effectiveFont(AppLanguage.SystemDefault))
    }

    @Test
    fun noFontChoiceFollowsAStoredLanguageOverTheDeviceLanguage() {
        val japanese = AppPreferences(language = AppLanguage.Japanese)
        assertEquals(AppFont.KiwiMaru, japanese.effectiveFont(AppLanguage.English))

        // A stored language without a bundled default does not fall through to the device language.
        val korean = AppPreferences(language = AppLanguage.Korean)
        assertEquals(AppFont.Device, korean.effectiveFont(AppLanguage.English))
    }

    @Test
    fun anExplicitFontChoiceWinsOverEveryLanguageDefault() {
        val openDyslexic = AppPreferences(font = AppFont.OpenDyslexic, language = AppLanguage.Japanese)
        assertEquals(AppFont.OpenDyslexic, openDyslexic.effectiveFont(AppLanguage.English))

        // An explicit Device is a choice. It keeps the system font where the language has a default.
        val device = AppPreferences(font = AppFont.Device)
        assertEquals(AppFont.Device, device.effectiveFont(AppLanguage.English))
    }
}
