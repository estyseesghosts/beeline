package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.UploadCompression
import me.foxtails.palustris.ui.shell.EmojiPresentation

/** What the server of the active account supports on the Posting page. An unsupported setting is not shown. */
data class PostingSettingsSupport(
    val uploadCompression: Boolean = false,
    val favouriteEmoji: Boolean = false,
)

/** Commands of the Posting page. The owner binds each one to the active account. */
class PostingSettingsActions(
    val onDefaultAudience: (Audience) -> Unit = {},
    val onRepliesUnlisted: (Boolean) -> Unit = {},
    val onUploadCompression: (UploadCompression) -> Unit = {},
    val onFavouriteEmoji: (String) -> Unit = {},
)

/**
 * The Posting page: defaults for new posts of one account. [accountLabel] names that account, because the
 * values are stored for each account and the page does not select one. [emoji] is the emoji catalog of that
 * account for the favourite reaction picker.
 */
@Composable
fun PostingSettingsScreen(
    preferences: PostPreferences,
    actions: PostingSettingsActions,
    support: PostingSettingsSupport = PostingSettingsSupport(),
    accountLabel: String? = null,
    emoji: EmojiPresentation = EmojiPresentation.Empty,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        accountLabel?.let { SettingsAccountScope(it) }
        SettingsSection(stringResource(R.string.settings_posting_default_audience)) {
            Audience.entries.filter { it != Audience.Direct }.forEach { audience ->
                SettingsChoiceRow(audience.label(), preferences.defaultAudience == audience, { actions.onDefaultAudience(audience) })
            }
            SettingsSwitchRow(
                title = stringResource(R.string.settings_posting_replies_unlisted),
                summary = stringResource(R.string.settings_posting_replies_unlisted_summary),
                checked = preferences.repliesUnlisted,
                onCheckedChange = actions.onRepliesUnlisted,
                testTag = "posting_replies_unlisted",
            )
        }
        if (support.uploadCompression) {
            HorizontalDivider()
            UploadCompressionSection(preferences.uploadCompression, actions.onUploadCompression)
        }
        if (support.favouriteEmoji) {
            HorizontalDivider()
            SettingsSection(stringResource(R.string.settings_favourite_emoji)) {
                FavouriteEmojiSetting(preferences.favouriteEmoji, emoji, actions.onFavouriteEmoji)
            }
        }
    }
}

@Composable
private fun UploadCompressionSection(selected: UploadCompression, onSelected: (UploadCompression) -> Unit) {
    SettingsSection(stringResource(R.string.settings_posting_compression)) {
        SettingsNote(stringResource(R.string.settings_posting_compression_summary))
        UploadCompression.entries.forEach { option ->
            SettingsChoiceRow(option.label(), selected == option, { onSelected(option) })
        }
    }
}

@Composable
private fun UploadCompression.label(): String = when (this) {
    UploadCompression.Always -> stringResource(R.string.settings_posting_compression_always)
    UploadCompression.Never -> stringResource(R.string.settings_posting_compression_never)
    UploadCompression.Ask -> stringResource(R.string.settings_posting_compression_ask)
}

@Composable
private fun Audience.label(): String = when (this) {
    Audience.Public -> stringResource(R.string.audience_everyone)
    Audience.Unlisted -> stringResource(R.string.audience_unlisted)
    Audience.Followers -> stringResource(R.string.audience_followers)
    Audience.Direct -> stringResource(R.string.audience_direct)
}
