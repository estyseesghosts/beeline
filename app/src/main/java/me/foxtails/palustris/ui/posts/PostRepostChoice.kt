package me.foxtails.palustris.ui.posts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.components.BeelineBubbleShape
import me.foxtails.palustris.ui.components.PillAction
import me.foxtails.palustris.ui.motion.HapticEvent
import me.foxtails.palustris.ui.motion.LocalPalustrisHaptics

/** At or above this font scale the choice opens as a bottom sheet because a bubble no longer fits beside its anchor. */
private const val LARGE_FONT_SHEET_SCALE = 1.5f

/**
 * The Repost / Quote choice that a tap on the repost action opens. It owns presentation only: a tap on the action
 * never sends. Repost and Quote report through callbacks; mutation, rollback, and the composer stay with their owners.
 * [quoteEnabled] is true only when the current source supports quoting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RepostChoice(
    pending: PendingRepostConfirmation,
    quoteEnabled: Boolean,
    onDismiss: () -> Unit,
    onRepost: () -> Unit,
    onQuote: () -> Unit,
) {
    val density = LocalDensity.current
    if (density.fontScale >= LARGE_FONT_SHEET_SCALE) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            RepostChoiceContent(pending.selected, quoteEnabled, onRepost, onQuote, Modifier.navigationBarsPadding().padding(16.dp))
        }
    } else {
        Popup(
            popupPositionProvider = WindowAnchorPositionProvider(pending.anchorBounds, BubblePlacement.Below),
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
        ) {
            Surface(
                shape = BeelineBubbleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
            ) {
                RepostChoiceContent(pending.selected, quoteEnabled, onRepost, onQuote, Modifier.padding(8.dp))
            }
        }
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
