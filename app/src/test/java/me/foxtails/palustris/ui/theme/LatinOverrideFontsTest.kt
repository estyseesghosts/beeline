package me.foxtails.palustris.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.LoadedFontFamily
import androidx.compose.ui.text.intl.LocaleList
import me.foxtails.palustris.domain.AppFont
import me.foxtails.palustris.domain.AppLanguage
import me.foxtails.palustris.domain.AppPreferences
import me.foxtails.palustris.ui.PalustrisTheme
import me.foxtails.palustris.ui.settings.DisplaySettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Inter draws the Latin characters of a language that is not written in Latin script. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LatinOverrideFontsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(preferences: AppPreferences, content: @Composable () -> Unit) {
        compose.activity.runOnUiThread {
            compose.activity.setContent { PalustrisTheme(preferences) { content() } }
        }
        compose.waitForIdle()
    }

    @Test
    fun theBuilderMakesOnePlatformTypefacePerBaseFont() {
        val fonts = LatinOverrideFonts(compose.activity.resources)
        val japanese = fonts.family(AppFont.KiwiMaru)

        // A loaded family proves that the platform typeface was built, not the plain fallback.
        assertTrue(japanese.toString(), japanese is LoadedFontFamily)
        assertNotEquals(appFontFamily(AppFont.KiwiMaru), japanese)
        assertSame(japanese, fonts.family(AppFont.KiwiMaru))
        assertNotEquals(japanese, fonts.family(AppFont.ChironHeiHk))
        // The system font has no files. Inter still comes first.
        assertTrue(fonts.family(AppFont.Device) is LoadedFontFamily)
    }

    @Test
    fun theThemeUsesTheOverrideFamilyForAJapaneseUser() {
        var family: FontFamily? = null
        var fonts: LatinOverrideFonts? = null
        show(AppPreferences(language = AppLanguage.Japanese)) {
            family = MaterialTheme.typography.bodyLarge.fontFamily
            fonts = LocalLatinOverrideFonts.current
        }

        assertNotNull(fonts)
        assertSame(fonts!!.family(AppFont.KiwiMaru), family)
        assertNotEquals(appFontFamily(AppFont.KiwiMaru), family)
    }

    @Test
    fun theThemeKeepsThePlainFontWhenTheOverrideDoesNotApply() {
        var family: FontFamily? = null
        var fonts: LatinOverrideFonts? = LatinOverrideFonts(compose.activity.resources)

        show(AppPreferences(language = AppLanguage.Japanese, latinFontOverride = false)) {
            family = MaterialTheme.typography.bodyLarge.fontFamily
            fonts = LocalLatinOverrideFonts.current
        }
        assertEquals(appFontFamily(AppFont.KiwiMaru), family)
        assertNull(fonts)

        // A Latin-script language needs no override.
        show(AppPreferences(language = AppLanguage.English)) {
            family = MaterialTheme.typography.bodyLarge.fontFamily
            fonts = LocalLatinOverrideFonts.current
        }
        assertEquals(appFontFamily(AppFont.Inter), family)
        assertNull(fonts)

        // A font selected for its Latin design keeps its Latin characters.
        show(AppPreferences(language = AppLanguage.Japanese, font = AppFont.OpenDyslexic)) {
            family = MaterialTheme.typography.bodyLarge.fontFamily
            fonts = LocalLatinOverrideFonts.current
        }
        assertEquals(appFontFamily(AppFont.OpenDyslexic), family)
        assertNull(fonts)
    }

    @Test
    fun aPostKeepsInterForItsLatinCharactersUnderTheOverride() {
        var japanese: TextStyle? = null
        var chinese: TextStyle? = null
        var fonts: LatinOverrideFonts? = null
        show(AppPreferences(language = AppLanguage.Japanese)) {
            fonts = LocalLatinOverrideFonts.current
            japanese = TextStyle().forPostText("今日は good 天気")
            chinese = TextStyle().forPostText("這是我們的時間")
        }

        // Inter first, then Kiwi Maru, for a Japanese post of a Kiwi Maru user.
        assertSame(fonts!!.family(AppFont.KiwiMaru), japanese!!.fontFamily)
        assertEquals(LocaleList("ja-JP"), japanese!!.localeList)
        // Inter first, then the system font, for a Chinese post of the same user.
        assertSame(fonts!!.family(AppFont.Device), chinese!!.fontFamily)
        assertEquals(LocaleList("zh-Hant-TW"), chinese!!.localeList)
    }

    @Test
    fun theSwitchShowsOnlyForASupportedLanguageAndReportsAChange() {
        var reported: Boolean? = null
        fun page(preferences: AppPreferences) = show(preferences) {
            DisplaySettingsScreen(
                preferences = preferences,
                onColorScheme = {},
                onColorPalette = {},
                onBackground = {},
                onTextSize = {},
                onFont = {},
                onRequest60Hz = {},
                onLatinFontOverride = { reported = it },
            )
        }

        page(AppPreferences(language = AppLanguage.English))
        compose.onAllNodesWithTag("latin_font_override").assertCountEquals(0)
        page(AppPreferences(language = AppLanguage.Russian))
        compose.onAllNodesWithTag("latin_font_override").assertCountEquals(0)

        page(AppPreferences(language = AppLanguage.Japanese))
        compose.onNodeWithTag("latin_font_override").performScrollTo().assertIsOn()
        compose.onNodeWithTag("latin_font_override").performClick()
        compose.runOnIdle { assertEquals(false, reported) }

        page(AppPreferences(language = AppLanguage.Arabic, latinFontOverride = false))
        compose.onNodeWithTag("latin_font_override").performScrollTo().assertIsOff()
    }
}
