package me.foxtails.palustris.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import me.foxtails.palustris.domain.AppFont
import me.foxtails.palustris.domain.AppPreferences
import me.foxtails.palustris.domain.effectiveFont
import me.foxtails.palustris.ui.localization.AppLocaleController

/**
 * The font in effect for the stored preferences and the current configuration locale. The theme
 * and the Appearance page both read it, so the selected option always names the rendered font.
 */
@Composable
internal fun effectiveAppFont(preferences: AppPreferences): AppFont =
    preferences.effectiveFont(AppLocaleController.languageForLocale(LocalConfiguration.current.locales.get(0)))

/**
 * The Material theme with the typography of the font in effect. It also publishes that font, so
 * a post can compare it with the font of its script.
 */
@Composable
internal fun AppFontTheme(preferences: AppPreferences, colorScheme: ColorScheme, content: @Composable () -> Unit) {
    val appFont = effectiveAppFont(preferences)
    CompositionLocalProvider(LocalAppFont provides appFont) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = appTypography(appFont, preferences.textSize),
            content = content,
        )
    }
}
