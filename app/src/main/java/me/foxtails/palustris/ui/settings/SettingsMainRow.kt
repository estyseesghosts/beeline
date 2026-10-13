package me.foxtails.palustris.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.AppColorScheme
import me.foxtails.palustris.domain.AppPreferences

/** One row of the main page: the page it opens, its labels, and its stable test tag. */
internal class SettingsMainRow(val route: SettingsRoute, val testTag: String, val title: String, val summary: String)

/** The main page rows in display order. A summary shows the current value where one value describes the page. */
@Composable
internal fun settingsMainRows(preferences: AppPreferences): List<SettingsMainRow> {
    val colorSchemeLabel = stringResource(
        when (preferences.colorScheme) {
            AppColorScheme.System -> R.string.settings_colour_style_system
            AppColorScheme.SystemMonochrome -> R.string.settings_colour_style_system_monochrome
            AppColorScheme.Palette -> R.string.settings_colour_style_palette
        },
    )
    return listOf(
        SettingsMainRow(
            SettingsRoute.Display,
            "settings_display",
            stringResource(R.string.settings_display),
            "$colorSchemeLabel, ${appTextSizeLabel(preferences.textSize)}",
        ),
        SettingsMainRow(
            SettingsRoute.Language,
            "settings_language",
            stringResource(R.string.settings_language),
            appLanguageLabel(preferences.language),
        ),
        SettingsMainRow(
            SettingsRoute.TimelineMedia,
            "settings_timeline_media",
            stringResource(R.string.settings_timeline_media),
            stringResource(R.string.settings_timeline_media_summary),
        ),
        SettingsMainRow(
            SettingsRoute.Posting,
            "settings_posting",
            stringResource(R.string.settings_posting),
            stringResource(R.string.settings_posting_summary),
        ),
        SettingsMainRow(
            SettingsRoute.ContentWarnings,
            "settings_content_warnings",
            stringResource(R.string.settings_content_warnings),
            stringResource(R.string.settings_content_warnings_summary),
        ),
        SettingsMainRow(
            SettingsRoute.PrivacyAccounts,
            "settings_blocks_mutes",
            stringResource(R.string.settings_blocks_mutes),
            stringResource(R.string.settings_blocks_mutes_summary),
        ),
        SettingsMainRow(
            SettingsRoute.Privacy,
            "settings_privacy",
            stringResource(R.string.settings_privacy),
            stringResource(R.string.settings_privacy_summary),
        ),
        SettingsMainRow(
            SettingsRoute.Notifications,
            "settings_notifications",
            stringResource(R.string.settings_notifications),
            stringResource(R.string.settings_notifications_summary),
        ),
    )
}
