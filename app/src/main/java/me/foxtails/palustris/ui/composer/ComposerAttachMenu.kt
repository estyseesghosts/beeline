package me.foxtails.palustris.ui.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.components.PillAction
import me.foxtails.palustris.ui.motion.HapticEvent
import me.foxtails.palustris.ui.motion.LocalPalustrisHaptics
import me.foxtails.palustris.ui.posts.AnchoredChoiceBubble
import me.foxtails.palustris.ui.posts.BubblePlacement

/** One row of the attachment menu. The rule that decides [enabled] stays with its owner; the row only reports it. */
internal data class ComposerAttachRow(
    val tag: String,
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val onChoose: () -> Unit,
)

/**
 * The attachment menu that the paperclip circle opens, in the pop-out style of Follow and Repost. It is stateless: the
 * toolbar owns whether it is open and where its anchor is. A tap on a row closes the menu first and then runs the
 * row's action once. The first enabled row takes the focus.
 */
@Composable
internal fun ComposerAttachMenu(anchor: Rect, rows: List<ComposerAttachRow>, onDismiss: () -> Unit) {
    AnchoredChoiceBubble(anchor, BubblePlacement.Above, onDismiss) { padding ->
        val haptics = LocalPalustrisHaptics.current
        val firstChoice = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { firstChoice.requestFocus() } }
        val firstEnabled = rows.indexOfFirst { it.enabled }
        Column(padding.testTag("composer_attach_menu"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEachIndexed { index, row ->
                PillAction(
                    label = row.label,
                    onClick = {
                        haptics.perform(HapticEvent.Selection)
                        onDismiss()
                        row.onChoose()
                    },
                    modifier = (if (index == firstEnabled) Modifier.focusRequester(firstChoice) else Modifier).testTag(row.tag),
                    enabled = row.enabled,
                    leadingIcon = row.icon,
                )
            }
        }
    }
}

/**
 * The rows of the attachment menu for [entry]. Each rule lives where it did before the menu: [photo] follows
 * `PhotoButtonState`, [pollEnabled] is the poll rule of the composer body, and the content warning is always
 * available. While [editing] only the content warning row exists, and the server decides whether a poll row exists
 * ([pollSupported]).
 */
@Composable
internal fun composerAttachRows(
    entry: ComposerEntryState,
    editing: Boolean,
    photo: PhotoButtonState,
    pollSupported: Boolean,
    pollEnabled: Boolean,
    onPhoto: () -> Unit,
    onPoll: () -> Unit,
    onToggleWarning: () -> Unit,
): List<ComposerAttachRow> = buildList {
    if (!editing) {
        add(
            ComposerAttachRow(
                tag = "composer_attach_photo",
                label = stringResource(R.string.composer_add_photo),
                icon = AppIcons.Image,
                enabled = photo != PhotoButtonState.Disabled,
                onChoose = onPhoto,
            ),
        )
    }
    add(
        ComposerAttachRow(
            tag = "composer_attach_warning",
            label = stringResource(if (entry.warningEnabled) R.string.composer_warning_remove else R.string.composer_warning_add),
            icon = ComposerIcons.Warning,
            enabled = true,
            onChoose = onToggleWarning,
        ),
    )
    if (!editing && pollSupported) {
        add(
            ComposerAttachRow(
                tag = "composer_poll_toggle",
                label = stringResource(if (entry.poll != null) R.string.composer_poll_remove else R.string.composer_poll_add),
                icon = ComposerIcons.Poll,
                enabled = pollEnabled || entry.poll != null,
                onChoose = onPoll,
            ),
        )
    }
}
