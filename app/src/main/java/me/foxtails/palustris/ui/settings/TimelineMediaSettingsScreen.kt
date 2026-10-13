package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.AppPreferences

/** The Timeline and media page: settings that change what feeds and search results show and play. */
@Composable
fun TimelineMediaSettingsScreen(
    preferences: AppPreferences,
    onAutoplayVideos: (Boolean) -> Unit,
    onCombineRelatedHashtags: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_autoplay_videos),
            summary = stringResource(R.string.settings_autoplay_videos_summary),
            checked = preferences.autoplayVideos,
            onCheckedChange = onAutoplayVideos,
            testTag = "autoplay_videos",
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_combine_related_hashtags),
            summary = stringResource(R.string.settings_combine_related_hashtags_summary),
            checked = preferences.combineRelatedHashtags,
            onCheckedChange = onCombineRelatedHashtags,
            testTag = "combine_related_hashtags",
        )
    }
}
