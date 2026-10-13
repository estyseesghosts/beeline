package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.ui.emoji.CustomEmojiImage
import me.foxtails.palustris.ui.emoji.EmojiPickerHost
import me.foxtails.palustris.ui.emoji.EmojiPickerTarget
import me.foxtails.palustris.ui.shell.EmojiPresentation

/**
 * Selects the emoji that the favourite button sends on a server where a favourite is a reaction.
 *
 * The row shows the stored emoji and opens the emoji picker of the account, so a Unicode emoji and a custom
 * server emoji are both possible. The stored value is the submission identity of the chosen emoji. [emoji] is
 * the catalog of the account that the page edits; the picker loads it on demand.
 */
@Composable
internal fun FavouriteEmojiSetting(current: String, emoji: EmojiPresentation, onSelected: (String) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    // A stored custom emoji needs the catalog for its image.
    LaunchedEffect(emoji.actions) { emoji.actions.loadCatalog() }
    val custom = emoji.catalog.items.firstOrNull { it.submissionValue == current }
    ListItem(
        modifier = Modifier.fillMaxWidth().testTag("favourite_emoji_row").clickable { picking = true },
        headlineContent = { Text(stringResource(R.string.settings_favourite_emoji_choose)) },
        supportingContent = { Text(stringResource(R.string.settings_favourite_emoji_summary)) },
        trailingContent = { FavouriteEmojiGlyph(current, custom) },
    )
    EmojiPickerHost(
        target = EmojiPickerTarget.Favourite.takeIf { picking },
        catalog = emoji.catalog,
        selectionMode = emoji.capabilities.selectionMode,
        mutationSupported = true,
        onLoadCatalog = emoji.actions::loadCatalog,
        onRetryCatalog = emoji.actions::retryCatalog,
        onDismiss = { picking = false },
        onEmojiSelected = { choice -> onSelected(choice.submissionValue) },
        onToggleGroupCollapsed = emoji.actions::toggleGroupCollapsed,
        onToggleGroupPinned = emoji.actions::toggleGroupPinned,
        onTogglePinnedEmoji = emoji.actions::togglePinnedEmoji,
    )
}

/** The stored favourite reaction: the image of a custom emoji, or the text of a Unicode emoji. */
@Composable
private fun FavouriteEmojiGlyph(current: String, custom: CustomEmoji?) {
    if (custom != null) {
        CustomEmojiImage(emoji = custom, fallbackText = custom.token, modifier = Modifier.size(32.dp))
    } else {
        Text(current, Modifier.testTag("favourite_emoji_current"), fontSize = 24.sp, maxLines = 1)
    }
}
