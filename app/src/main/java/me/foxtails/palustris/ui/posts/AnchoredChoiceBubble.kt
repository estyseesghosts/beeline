package me.foxtails.palustris.ui.posts

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import me.foxtails.palustris.ui.components.BeelineBubbleShape
import me.foxtails.palustris.ui.components.FloatingControlElevation

/** At or above this font scale a choice opens as a bottom sheet because a bubble no longer fits beside its anchor. */
private const val LARGE_FONT_SHEET_SCALE = 1.5f

/**
 * Presents one anchored choice: a popup bubble beside [anchor], or a bottom sheet at a large font scale. It holds no
 * state and no rules. The caller owns the rows and the choice; [content] receives the padding that fits the
 * presentation it landed in. The popup takes the focus and closes on Back and on a tap outside.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnchoredChoiceBubble(
    anchor: Rect,
    placement: BubblePlacement,
    onDismiss: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    if (LocalDensity.current.fontScale >= LARGE_FONT_SHEET_SCALE) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            content(Modifier.navigationBarsPadding().padding(16.dp))
        }
    } else {
        Popup(
            popupPositionProvider = WindowAnchorPositionProvider(anchor, placement),
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
        ) {
            Surface(
                shape = BeelineBubbleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = FloatingControlElevation,
            ) {
                content(Modifier.padding(8.dp))
            }
        }
    }
}
