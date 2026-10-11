package me.foxtails.palustris.ui.chips

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.ui.components.BeelineBubbleShape
import me.foxtails.palustris.ui.components.PillAction
import me.foxtails.palustris.ui.motion.HapticEvent
import me.foxtails.palustris.ui.motion.LocalPalustrisHaptics
import me.foxtails.palustris.ui.posts.BubblePlacement
import me.foxtails.palustris.ui.posts.WindowAnchorPositionProvider

/** At or above this font scale the menu opens as a bottom sheet because a bubble no longer fits beside its chip. */
private const val LARGE_FONT_SHEET_SCALE = 1.5f

/** The hashtag chip whose menu is open, and the window bounds of that chip. */
internal data class HashtagChipMenu(val hashtag: FeedSource.Hashtag, val anchorBounds: Rect)

/**
 * The menu that a long press on a hashtag chip opens. It has one button: Unfollow for a tag followed on
 * the server, Remove for a tag saved on the device. It uses the same bubble style and placement as the
 * repost choice. A long press never changes anything by itself: [onConfirm] runs only on the button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HashtagChipBubble(
    menu: HashtagChipMenu,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val density = LocalDensity.current
    if (density.fontScale >= LARGE_FONT_SHEET_SCALE) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            HashtagChipBubbleContent(menu.hashtag, onConfirm, Modifier.navigationBarsPadding().padding(16.dp))
        }
    } else {
        Popup(
            popupPositionProvider = WindowAnchorPositionProvider(menu.anchorBounds, BubblePlacement.Above),
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
        ) {
            Surface(
                shape = BeelineBubbleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
            ) {
                HashtagChipBubbleContent(menu.hashtag, onConfirm, Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun HashtagChipBubbleContent(hashtag: FeedSource.Hashtag, onConfirm: () -> Unit, modifier: Modifier) {
    val haptics = LocalPalustrisHaptics.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val label = stringResource(hashtagMenuLabel(hashtag.origin), hashtagChipLabel(hashtag.tag))
    PillAction(
        label = label,
        onClick = {
            haptics.perform(HapticEvent.Commit)
            onConfirm()
        },
        modifier = modifier.focusRequester(focus).testTag("feed_chip_hashtag_action"),
    )
}
