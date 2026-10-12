package me.foxtails.palustris.ui.profile

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.components.PillAction
import me.foxtails.palustris.ui.motion.HapticEvent
import me.foxtails.palustris.ui.motion.LocalPalustrisHaptics
import me.foxtails.palustris.ui.posts.AnchoredChoiceBubble
import me.foxtails.palustris.ui.posts.BubblePlacement

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
@Composable
internal fun FollowChoice(
    anchor: Rect,
    following: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AnchoredChoiceBubble(anchor, BubblePlacement.Below, onDismiss) { padding ->
        FollowChoiceContent(following, onConfirm, padding)
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
