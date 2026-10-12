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
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.ui.components.BeelineBubbleShape
import me.foxtails.palustris.ui.components.FloatingControlElevation
import me.foxtails.palustris.ui.components.PillAction
import me.foxtails.palustris.ui.motion.HapticEvent
import me.foxtails.palustris.ui.motion.LocalPalustrisHaptics
import me.foxtails.palustris.ui.posts.BubblePlacement
import me.foxtails.palustris.ui.posts.WindowAnchorPositionProvider
import me.foxtails.palustris.ui.shell.FeedChipsContract

/** At or above this font scale the menu opens as a bottom sheet because a bubble no longer fits beside its chip. */
private const val LARGE_FONT_SHEET_SCALE = 1.5f

/** Removes the hashtag or group chip [feed]. Other chip kinds have no menu. */
internal fun FeedChipsContract.Actions.removeChip(feed: FeedSource) {
    when (feed) {
        is FeedSource.Hashtag -> removeHashtag(feed.tag) {}
        is FeedSource.HashtagGroup -> removeHashtagGroup(feed) {}
        else -> Unit
    }
}

/** The hashtag chip whose menu is open, and the window bounds of that chip. */
internal data class HashtagChipMenu(val feed: FeedSource, val anchorBounds: Rect)

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
            HashtagChipBubbleContent(menu.feed, onConfirm, Modifier.navigationBarsPadding().padding(16.dp))
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
                shadowElevation = FloatingControlElevation,
            ) {
                HashtagChipBubbleContent(menu.feed, onConfirm, Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun HashtagChipBubbleContent(feed: FeedSource, onConfirm: () -> Unit, modifier: Modifier) {
    val haptics = LocalPalustrisHaptics.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val label = when (feed) {
        is FeedSource.Hashtag -> stringResource(hashtagMenuLabel(feed.origin), hashtagChipLabel(feed.tag))
        is FeedSource.HashtagGroup -> stringResource(R.string.feed_chip_remove_tag, feed.name)
        else -> return
    }
    PillAction(
        label = label,
        onClick = {
            haptics.perform(HapticEvent.Commit)
            onConfirm()
        },
        modifier = modifier.focusRequester(focus).testTag("feed_chip_hashtag_action"),
    )
}
