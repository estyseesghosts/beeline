package me.foxtails.palustris.ui.profile

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.components.BeelineBubbleShape
import me.foxtails.palustris.ui.components.PillAction
import me.foxtails.palustris.ui.motion.HapticEvent
import me.foxtails.palustris.ui.motion.LocalPalustrisHaptics
import me.foxtails.palustris.ui.posts.BubblePlacement
import me.foxtails.palustris.ui.posts.WindowAnchorPositionProvider

/** At or above this font scale the choice opens as a bottom sheet because a bubble no longer fits beside its anchor. */
private const val LARGE_FONT_SHEET_SCALE = 1.5f

/**
 * Holds the anchor of the one open Follow / Unfollow confirmation for one follow control.
 *
 * Each follow control remembers its own state keyed on the relationship it was opened for, so a
 * relationship change, an account change, or a mutation in flight discards the bubble instead of
 * confirming against stale state. A tap on the control never sends; only the bubble does.
 */
internal class FollowChoiceState {
    var anchor by mutableStateOf<Rect?>(null)
        private set

    fun request(bounds: Rect) {
        anchor = bounds
    }

    fun dismiss() {
        anchor = null
    }
}

/**
 * The Follow / Unfollow confirmation that a tap on a follow control opens. It owns presentation only:
 * [onConfirm] reports the choice and the relationship mutation stays with its owner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FollowChoice(
    anchor: Rect,
    following: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (LocalDensity.current.fontScale >= LARGE_FONT_SHEET_SCALE) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            FollowChoiceContent(following, onConfirm, Modifier.navigationBarsPadding().padding(16.dp))
        }
    } else {
        Popup(
            popupPositionProvider = WindowAnchorPositionProvider(anchor, BubblePlacement.Below),
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
        ) {
            Surface(
                shape = BeelineBubbleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
            ) {
                FollowChoiceContent(following, onConfirm, Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun FollowChoiceContent(following: Boolean, onConfirm: () -> Unit, modifier: Modifier) {
    val haptics = LocalPalustrisHaptics.current
    val choice = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { choice.requestFocus() } }
    val label = stringResource(if (following) R.string.profile_unfollow_confirm else R.string.profile_follow_confirm)
    PillAction(
        label = label,
        onClick = {
            haptics.perform(HapticEvent.Commit)
            onConfirm()
        },
        modifier = modifier.focusRequester(choice).testTag("follow_confirmation"),
        leadingIcon = if (following) AppIcons.Unfollow else AppIcons.Follow,
    )
}
