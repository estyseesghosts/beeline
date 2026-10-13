package me.foxtails.palustris.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageDirectionTest {
    @Test
    fun arabicIsTheOnlyRightToLeftLanguage() {
        assertTrue(AppLanguage.Arabic.isRightToLeft)
        AppLanguage.entries.filter { it != AppLanguage.Arabic }.forEach { language ->
            assertFalse(language.name, language.isRightToLeft)
        }
    }

    @Test
    fun arabicIsTheOnlyLanguageWithGrammaticalGenderForms() {
        assertTrue(AppLanguage.Arabic.hasGrammaticalGenderForms)
        AppLanguage.entries.filter { it != AppLanguage.Arabic }.forEach { language ->
            assertFalse(language.name, language.hasGrammaticalGenderForms)
        }
    }

    @Test
    fun aLanguageChangeKeepsTheGrammaticalGender() {
        val feminine = AppPreferences(language = AppLanguage.Arabic, grammaticalGender = AppGrammaticalGender.Feminine)
        assertEquals(AppGrammaticalGender.Feminine, feminine.withLanguage(AppLanguage.English).grammaticalGender)
    }

    @Test
    fun aChangeToOrFromArabicReturnsTheLayoutDirectionToSystem() {
        val forcedLtr = AppPreferences(language = AppLanguage.English, layoutDirection = AppLayoutDirection.ForceLtr)
        val arabic = forcedLtr.withLanguage(AppLanguage.Arabic)
        assertEquals(AppLanguage.Arabic, arabic.language)
        assertEquals(AppLayoutDirection.System, arabic.layoutDirection)

        val forcedRtl = AppPreferences(language = AppLanguage.Arabic, layoutDirection = AppLayoutDirection.ForceRtl)
        val english = forcedRtl.withLanguage(AppLanguage.English)
        assertEquals(AppLanguage.English, english.language)
        assertEquals(AppLayoutDirection.System, english.layoutDirection)

        val systemDefault = forcedRtl.withLanguage(AppLanguage.SystemDefault)
        assertEquals(AppLayoutDirection.System, systemDefault.layoutDirection)
    }

    @Test
    fun aChangeBetweenLanguagesOfOneDirectionKeepsTheOverride() {
        val forcedRtl = AppPreferences(language = AppLanguage.English, layoutDirection = AppLayoutDirection.ForceRtl)
        val japanese = forcedRtl.withLanguage(AppLanguage.Japanese)
        assertEquals(AppLanguage.Japanese, japanese.language)
        assertEquals(AppLayoutDirection.ForceRtl, japanese.layoutDirection)

        // Every other field stays.
        val custom = AppPreferences(font = AppFont.OpenDyslexic, request60Hz = true)
        assertEquals(custom.copy(language = AppLanguage.Arabic), custom.withLanguage(AppLanguage.Arabic))
    }
}
