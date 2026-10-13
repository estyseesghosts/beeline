package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
