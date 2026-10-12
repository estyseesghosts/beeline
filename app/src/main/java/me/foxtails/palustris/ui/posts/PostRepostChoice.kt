package me.foxtails.palustris.ui.posts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.components.PillAction
import me.foxtails.palustris.ui.motion.HapticEvent
import me.foxtails.palustris.ui.motion.LocalPalustrisHaptics

/**
 * The Repost / Quote choice that a tap on the repost action opens. It owns presentation only: a tap on the action
 * never sends. Repost and Quote report through callbacks; mutation, rollback, and the composer stay with their owners.
 * [quoteEnabled] is true only when the current source supports quoting.
 */
@Composable
internal fun RepostChoice(
    pending: PendingRepostConfirmation,
    quoteEnabled: Boolean,
    onDismiss: () -> Unit,
    onRepost: () -> Unit,
    onQuote: () -> Unit,
) {
    AnchoredChoiceBubble(pending.anchorBounds, BubblePlacement.Below, onDismiss) { padding ->
        RepostChoiceContent(pending.selected, quoteEnabled, onRepost, onQuote, padding)
    }
}

@Composable
private fun RepostChoiceContent(
    reposted: Boolean,
    quoteEnabled: Boolean,
    onRepost: () -> Unit,
    onQuote: () -> Unit,
    modifier: Modifier,
) {
    val haptics = LocalPalustrisHaptics.current
    val firstChoice = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstChoice.requestFocus() } }
    Column(modifier.testTag("repost_choice"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PillAction(
            label = stringResource(if (reposted) R.string.post_action_undo_repost else R.string.post_action_repost),
            onClick = {
                haptics.perform(HapticEvent.Commit)
                onRepost()
            },
            modifier = Modifier.focusRequester(firstChoice).testTag("repost_confirmation"),
        )
        if (quoteEnabled) {
            PillAction(
                label = stringResource(R.string.post_action_quote),
                onClick = {
                    haptics.perform(HapticEvent.Selection)
                    onQuote()
                },
                modifier = Modifier.testTag("repost_choice_quote"),
            )
        }
    }
}
