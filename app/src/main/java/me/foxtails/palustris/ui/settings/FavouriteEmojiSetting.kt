package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.isValidFavouriteEmoji

/** The offered favourite reactions. The first one is the default of a new account. */
internal val FavouriteEmojiPresets = listOf("❤️", "👍", "⭐", "🎉", "😆", "🥰", "🙏", "🔥")

private const val MAX_FAVOURITE_EMOJI_INPUT_LENGTH = 16

/**
 * Whether typed text can become the favourite reaction. The field accepts one Unicode emoji only: a server
 * custom emoji shortcode is rejected, because a server can return it in another form than the stored one.
 * Letters, colons and spaces cannot be part of a Unicode emoji, so they mark other text.
 */
internal fun isFavouriteEmojiInput(value: String): Boolean {
    val text = value.trim()
    return isValidFavouriteEmoji(text) &&
        text.length <= MAX_FAVOURITE_EMOJI_INPUT_LENGTH &&
        text.none { it.isLetter() || it == ':' || it.isWhitespace() }
}

/**
 * Selects the emoji that the favourite button sends on a server where a favourite is a reaction.
 * The stored value shows as the selected chip, also when it is not one of the presets.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FavouriteEmojiSetting(current: String, onSelected: (String) -> Unit) {
    val choices = if (current in FavouriteEmojiPresets) FavouriteEmojiPresets else FavouriteEmojiPresets + current
    SettingsNote(stringResource(R.string.settings_favourite_emoji_summary))
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { emoji ->
            val description = stringResource(R.string.settings_favourite_emoji_choice, emoji)
            FilterChip(
                selected = emoji == current,
                onClick = { onSelected(emoji) },
                label = { Text(emoji) },
                modifier = Modifier.testTag("favourite_emoji_$emoji").semantics { contentDescription = description },
            )
        }
    }
    FavouriteEmojiField(onSelected)
}

@Composable
private fun FavouriteEmojiField(onSelected: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val valid = isFavouriteEmojiInput(input)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(stringResource(R.string.settings_favourite_emoji_other)) },
            supportingText = { Text(stringResource(R.string.settings_favourite_emoji_other_hint)) },
            isError = input.isNotBlank() && !valid,
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("favourite_emoji_input"),
        )
        Button(
            onClick = {
                onSelected(input.trim())
                input = ""
            },
            enabled = valid,
            modifier = Modifier.padding(start = 8.dp).testTag("favourite_emoji_use"),
        ) { Text(stringResource(R.string.settings_favourite_emoji_use)) }
    }
}
