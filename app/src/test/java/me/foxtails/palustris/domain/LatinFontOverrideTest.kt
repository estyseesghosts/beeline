package me.foxtails.palustris.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatinFontOverrideTest {
    @Test
    fun theOverrideSupportsOnlyLanguagesWhoseScriptInterDoesNotHold() {
        val supported = setOf(
            AppLanguage.Hindi,
            AppLanguage.Japanese,
            AppLanguage.Korean,
            AppLanguage.CantoneseHongKong,
            AppLanguage.Chinese,
            AppLanguage.ChineseMainland,
            AppLanguage.ChineseTaiwan,
            AppLanguage.Arabic,
        )
        AppLanguage.entries.forEach { language ->
            assertEquals(language.name, language in supported, language.supportsLatinFontOverride)
        }
        // Inter holds Cyrillic, so it would replace the font of the whole language.
        assertFalse(AppLanguage.Russian.supportsLatinFontOverride)
        assertFalse(AppLanguage.English.supportsLatinFontOverride)
        assertFalse(AppLanguage.SystemDefault.supportsLatinFontOverride)
    }

    @Test
    fun theOverrideIsOnByDefaultForASupportedLanguage() {
        assertTrue(AppPreferences().latinFontOverride)
        assertTrue(AppPreferences(language = AppLanguage.Japanese).latinFontOverrideActive(AppLanguage.English))
        assertTrue(AppPreferences(language = AppLanguage.Hindi).latinFontOverrideActive(AppLanguage.English))
        // Arabic and Korean render in the system font. Inter still draws their Latin characters.
        assertTrue(AppPreferences(language = AppLanguage.Arabic).latinFontOverrideActive(AppLanguage.English))
        assertTrue(AppPreferences(language = AppLanguage.Korean).latinFontOverrideActive(AppLanguage.English))
    }

    @Test
    fun aSystemDefaultLanguageUsesTheDeviceLanguage() {
        assertTrue(AppPreferences().latinFontOverrideActive(AppLanguage.Japanese))
        assertFalse(AppPreferences().latinFontOverrideActive(AppLanguage.English))
        assertFalse(AppPreferences().latinFontOverrideActive(AppLanguage.SystemDefault))
        // A stored language wins over the device language.
        assertFalse(AppPreferences(language = AppLanguage.English).latinFontOverrideActive(AppLanguage.Japanese))
    }

    @Test
    fun theSwitchTurnsTheOverrideOff() {
        val off = AppPreferences(language = AppLanguage.Japanese, latinFontOverride = false)
        assertFalse(off.latinFontOverrideActive(AppLanguage.English))
    }

    @Test
    fun aFontSelectedForItsLatinDesignKeepsItsLatinCharacters() {
        listOf(AppFont.Serif, AppFont.Sans, AppFont.OpenDyslexic, AppFont.Inter).forEach { font ->
            val preferences = AppPreferences(language = AppLanguage.Japanese, font = font)
            assertFalse(font.name, preferences.latinFontOverrideActive(AppLanguage.English))
        }
        listOf(
            AppFont.Device,
            AppFont.KiwiMaru,
            AppFont.ZcoolKuaiLe,
            AppFont.ChocolateClassicalSans,
            AppFont.ChironHeiHk,
            AppFont.Sarala,
        ).forEach { font ->
            val preferences = AppPreferences(language = AppLanguage.Japanese, font = font)
            assertTrue(font.name, preferences.latinFontOverrideActive(AppLanguage.English))
        }
    }
}
