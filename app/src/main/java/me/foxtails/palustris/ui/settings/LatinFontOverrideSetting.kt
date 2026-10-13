package me.foxtails.palustris.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.AppLanguage
import me.foxtails.palustris.domain.AppPreferences
import me.foxtails.palustris.domain.supportsLatinFontOverride
import me.foxtails.palustris.ui.theme.deviceAppLanguage

/**
 * The switch for the Latin font override. It shows only for a language that supports the
 * override, because it has no effect for every other language. A `SystemDefault` language uses
 * the device language.
 */
@Composable
internal fun LatinFontOverrideRow(preferences: AppPreferences, onLatinFontOverride: (Boolean) -> Unit) {
    val device = deviceAppLanguage()
    val shown = if (preferences.language == AppLanguage.SystemDefault) device else preferences.language
    if (!shown.supportsLatinFontOverride) return
    SettingsSwitchRow(
        title = stringResource(R.string.settings_latin_font_override),
        summary = stringResource(R.string.settings_latin_font_override_summary),
        checked = preferences.latinFontOverride,
        onCheckedChange = onLatinFontOverride,
        testTag = "latin_font_override",
    )
}
