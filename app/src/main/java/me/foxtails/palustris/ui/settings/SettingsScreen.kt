package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.AppColorScheme
import me.foxtails.palustris.domain.AppPreferences

/**
 * The Settings main page: one row for each settings page, in a fixed order. Each row opens its page
 * through [onRoute]. The page scrolls, so every row stays reachable on a short screen or with a large font.
 */
@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    onRoute: (SettingsRoute) -> Unit,
) {
    val rows = settingsMainRows(preferences)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SettingsMainRows(rows, onRoute)
    }
}

@Composable
private fun SettingsMainRows(rows: List<SettingsMainRow>, onRoute: (SettingsRoute) -> Unit) {
    rows.forEach { row ->
        SettingsRow(row.title, row.summary, { onRoute(row.route) }, row.testTag)
    }
}

/** One row of the main page: the page it opens, its labels, and its stable test tag. */
private class SettingsMainRow(val route: SettingsRoute, val testTag: String, val title: String, val summary: String)

/** The main page rows in display order. A summary shows the current value where one value describes the page. */
@Composable
private fun settingsMainRows(preferences: AppPreferences): List<SettingsMainRow> {
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
