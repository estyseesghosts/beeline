@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package me.foxtails.palustris.ui.emoji

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.EmojiChoice

private val TileMinimumSize = 48.dp

/**
 * One emoji tile for the compact pop-out and the full picker.
 *
 * The tile owns press, selected, pending, and pin presentation plus its accessibility state.
 * A tap selects the emoji. A long press or the pin accessibility action asks the caller to
 * confirm a pin change with the tile bounds. The tile never writes preferences. It shows
 * [pending] while the caller saves a pin change and ignores input during that time.
 */
@Composable
internal fun EmojiPickerTile(
    choice: EmojiChoice,
    selected: Boolean,
    pinned: Boolean,
    pending: Boolean,
    onClick: () -> Unit,
    onLongClick: (Rect) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val background = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        pressed && !pending -> MaterialTheme.colorScheme.surfaceContainer
        else -> Color.Transparent
    }
    val custom = choice.emoji
    val description = custom?.let { stringResource(R.string.custom_emoji_image_description, it.shortcode) }
        ?: choice.displayText
    val pinStateDescription = stringResource(
        when {
            pending -> R.string.emoji_pin_state_saving
            pinned -> R.string.emoji_pin_state_pinned
            else -> R.string.emoji_pin_state_unpinned
        },
    )
    val pinActionLabel = stringResource(if (pinned) R.string.emoji_unpin_action else R.string.emoji_pin_action)
    val openPinConfirmation = { onLongClick(bounds) }
    Box(
        Modifier
            .defaultMinSize(minWidth = TileMinimumSize, minHeight = TileMinimumSize)
            .alpha(if (pending) PENDING_ALPHA else 1f)
            .background(background, RoundedCornerShape(12.dp))
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !pending,
                onLongClickLabel = pinActionLabel,
                onClick = onClick,
                onLongClick = openPinConfirmation,
            )
            .semantics {
                contentDescription = description
                role = Role.Button
                this.selected = selected
                stateDescription = pinStateDescription
                if (!pending) {
                    customActions = listOf(
                        CustomAccessibilityAction(pinActionLabel) {
                            openPinConfirmation()
                            true
                        },
                    )
                }
            }
            .testTag("emoji_picker_cell_${choice.submissionValue}"),
        contentAlignment = Alignment.Center,
    ) {
        if (custom != null) {
            CustomEmojiImage(
                emoji = custom,
                fallbackText = custom.token,
                modifier = Modifier.size(32.dp),
            )
        } else {
            // Combined sequences stay on one line, centered, and are never clipped by the tile.
            Text(
                choice.displayText,
                fontSize = 24.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val PENDING_ALPHA = 0.5f
