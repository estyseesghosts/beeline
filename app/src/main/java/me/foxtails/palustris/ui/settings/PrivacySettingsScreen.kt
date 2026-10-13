package me.foxtails.palustris.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R

@Composable
fun PrivacySettingsScreen(
    cleanTrackingParameters: Boolean,
    onTrackingCleanup: (Boolean) -> Unit,
    onSelectAccount: () -> Unit,
) {
    SettingsSwitchRow(
        title = stringResource(R.string.settings_tracking_cleanup),
        summary = stringResource(R.string.settings_tracking_cleanup_summary),
        checked = cleanTrackingParameters,
        onCheckedChange = onTrackingCleanup,
        testTag = "tracking_cleanup",
    )
    SettingsRow(
        stringResource(R.string.settings_account_privacy),
        stringResource(R.string.settings_account_privacy_summary),
        onSelectAccount,
    )
}
