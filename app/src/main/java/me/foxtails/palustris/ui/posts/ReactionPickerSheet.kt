@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.foxtails.palustris.ui.posts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.ui.emoji.EmojiCatalogState
import me.foxtails.palustris.ui.emoji.EmojiChoiceGrid
import me.foxtails.palustris.ui.emoji.EmojiPreferenceActions
import me.foxtails.palustris.ui.emoji.EmojiRecents
import me.foxtails.palustris.ui.emoji.pinState

/** The surface that shows the reaction picker for one anchor. */
internal enum class ReactionPickerSurface { Anchored, Sheet }

private const val FONT_SCALE_SHEET_THRESHOLD = 1.5f
internal const val EXPANDED_PICKER_MIN_HEIGHT_DP = 360
internal const val EXPANDED_PICKER_MAX_HEIGHT_DP = 520
private const val ANCHOR_MARGIN_DP = 16

/**
 * Chooses the surface for the reaction picker. A missing anchor needs the sheet, because an anchored
 * surface has nowhere to attach. The compact pop-out stays anchored otherwise. The expanded picker
 * moves to the sheet when the font scale is large or when neither side of the anchor has room for
 * [EXPANDED_PICKER_MIN_HEIGHT_DP].
 */
internal fun reactionPickerSurface(
    anchor: Rect,
    windowWidthPx: Int,
    windowHeightPx: Int,
    density: Float,
    fontScale: Float,
    expanded: Boolean,
): ReactionPickerSurface {
    val anchorMissing = anchor.width <= 0f || anchor.height <= 0f ||
        anchor.right <= 0f || anchor.left >= windowWidthPx ||
        anchor.bottom <= 0f || anchor.top >= windowHeightPx
    return when {
        anchorMissing -> ReactionPickerSurface.Sheet
        !expanded -> ReactionPickerSurface.Anchored
        fontScale >= FONT_SCALE_SHEET_THRESHOLD -> ReactionPickerSurface.Sheet
        availableHeightDp(anchor, windowHeightPx, density) < EXPANDED_PICKER_MIN_HEIGHT_DP -> ReactionPickerSurface.Sheet
        else -> ReactionPickerSurface.Anchored
    }
}

/** The height in dp that the expanded picker can use on the roomier side of the anchor. */
internal fun availableHeightDp(anchor: Rect, windowHeightPx: Int, density: Float): Int {
    val above = anchor.top
    val below = windowHeightPx - anchor.bottom
    return (maxOf(above, below) / density).toInt() - ANCHOR_MARGIN_DP
}

internal fun Post.selectedReactionIdentities(): Set<String> = buildSet {
    selectedReactions.forEach { add(it.submissionValue) }
    reactions.filter { it.selected }.forEach { add(it.emoji) }
    myReaction?.let(::add)
}

internal fun Post.reactionChoices(): List<EmojiChoice> = reactions.map { reaction ->
    EmojiChoice(
        submissionValue = reaction.emoji,
        displayText = reaction.emoji,
        emoji = reaction.emojiMetadata,
    )
}

/**
 * The full picker in a bottom sheet for one reaction target. It uses the same catalog, recents,
 * pins, and choice callback as the anchored picker. It owns no target of its own.
 */
@Composable
internal fun ReactionPickerSheet(
    post: Post,
    catalog: EmojiCatalogState,
    recents: EmojiRecents,
    actions: EmojiPreferenceActions,
    onSelected: (EmojiChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.testTag("reaction_picker_sheet"),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 320.dp).padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.emoji_add_reaction),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            EmojiChoiceGrid(
                catalogItems = catalog.items,
                additionalChoices = post.reactionChoices(),
                selectedIdentities = post.selectedReactionIdentities(),
                preferences = catalog.preferences,
                pins = catalog.pinState(),
                recents = recents,
                testTag = "reaction_sheet_grid",
                actions = actions,
                onCloseRequest = onDismiss,
                onEmojiSelected = onSelected,
            )
        }
    }
}
