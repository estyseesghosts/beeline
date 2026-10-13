package me.foxtails.palustris.ui.theme

import androidx.compose.runtime.Composable
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
