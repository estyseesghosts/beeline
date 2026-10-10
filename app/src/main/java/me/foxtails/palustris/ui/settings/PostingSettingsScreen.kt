package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import androidx.compose.ui.Modifier
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.UploadCompression

@Composable
fun PostingSettingsScreen(
    preferences: PostPreferences,
    onDefaultAudience: (Audience) -> Unit,
    onRepliesUnlisted: (Boolean) -> Unit,
    showUploadCompression: Boolean = false,
    onUploadCompression: (UploadCompression) -> Unit = {},
) {
    val options = Audience.entries.filter { it != Audience.Direct }
    Column(Modifier.fillMaxWidth()) {
        options.forEach { audience ->
            ListItem(
                headlineContent = { Text(audience.label()) },
                supportingContent = { Text(stringResource(R.string.settings_posting_audience_summary)) },
                trailingContent = {
                    androidx.compose.material3.RadioButton(
                        selected = preferences.defaultAudience == audience,
                        onClick = { onDefaultAudience(audience) },
                    )
                },
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_posting_replies_unlisted)) },
            supportingContent = { Text(stringResource(R.string.settings_posting_replies_unlisted_summary)) },
            trailingContent = {
                Switch(
                    checked = preferences.repliesUnlisted,
                    onCheckedChange = onRepliesUnlisted,
                )
            },
        )
        if (showUploadCompression) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_posting_compression)) },
                supportingContent = { Text(stringResource(R.string.settings_posting_compression_summary)) },
            )
            UploadCompression.entries.forEach { option ->
                ListItem(
                    modifier = Modifier.selectable(
                        selected = preferences.uploadCompression == option,
                        role = androidx.compose.ui.semantics.Role.RadioButton,
                        onClick = { onUploadCompression(option) },
                    ),
                    headlineContent = { Text(option.label()) },
                    trailingContent = {
                        androidx.compose.material3.RadioButton(
                            selected = preferences.uploadCompression == option,
                            onClick = null,
                        )
                    },
                )
            }
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
