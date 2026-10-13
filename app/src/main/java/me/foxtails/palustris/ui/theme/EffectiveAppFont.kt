package me.foxtails.palustris.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import me.foxtails.palustris.domain.AppFont
import me.foxtails.palustris.domain.AppLanguage
import me.foxtails.palustris.domain.AppPreferences
import me.foxtails.palustris.domain.effectiveFont
import me.foxtails.palustris.domain.latinFontOverrideActive
import me.foxtails.palustris.ui.localization.AppLocaleController

/**
 * The font in effect for the stored preferences and the current configuration locale. The theme
 * and the Appearance page both read it, so the selected option always names the rendered font.
 */
@Composable
internal fun effectiveAppFont(preferences: AppPreferences): AppFont =
    preferences.effectiveFont(deviceAppLanguage())

/**
 * The Material theme with the typography of the font in effect. It also publishes that font, so
 * a post can compare it with the font of its script.
 */
@Composable
internal fun AppFontTheme(preferences: AppPreferences, colorScheme: ColorScheme, content: @Composable () -> Unit) {
    val appFont = effectiveAppFont(preferences)
    val latinFonts = latinOverrideFonts(preferences)
    val typography = appTypography(appFont, preferences.textSize)
    CompositionLocalProvider(LocalAppFont provides appFont, LocalLatinOverrideFonts provides latinFonts) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = if (latinFonts == null) typography else typography.withFontFamily(latinFonts.family(appFont)),
            content = content,
        )
    }
}

/** The device language as a supported language, for a stored `SystemDefault` language. */
@Composable
internal fun deviceAppLanguage(): AppLanguage =
    AppLocaleController.languageForLocale(LocalConfiguration.current.locales.get(0))

/** The fonts of the Latin override, or null while the override does not apply. */
@Composable
private fun latinOverrideFonts(preferences: AppPreferences): LatinOverrideFonts? {
    val resources = LocalContext.current.resources
    val fonts = remember(resources) { LatinOverrideFonts(resources) }
    return fonts.takeIf { preferences.latinFontOverrideActive(deviceAppLanguage()) }
}
