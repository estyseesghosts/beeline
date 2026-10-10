package me.foxtails.palustris.ui.composer

import androidx.compose.runtime.Composable
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.ui.emoji.ComposerField
import me.foxtails.palustris.ui.motion.TriggerSurfaceSource
import me.foxtails.palustris.ui.shell.ComposerContract

/**
 * Narrow placement slot for the composer overlay. The shell chooses placement (a floating
 * card on expanded windows, full-screen otherwise) and owns the open, guarded close, and
 * emoji-picker target. The feature owns the surface assembly in [ComposerSurfaceHost].
 * A composer presentation change stays in `ui/composer/`.
 */
@Composable
fun ComposerOverlayHost(
    owner: ComposerOwner,
    contract: ComposerContract,
    account: Account?,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    onRequestEmoji: (ComposerField) -> Unit,
    pendingEmojiInsertion: Pair<EmojiChoice, ComposerField>?,
    onEmojiInsertionApplied: () -> Unit,
    card: Boolean = false,
    triggerSource: TriggerSurfaceSource? = null
) {
    ComposerSurfaceHost(
        owner = owner,
        contract = contract,
        account = account,
        card = card,
        onDismiss = onDismiss,
        onClose = onClose,
        onRequestEmoji = onRequestEmoji,
        pendingEmojiInsertion = pendingEmojiInsertion,
        onEmojiInsertionApplied = onEmojiInsertionApplied,
        triggerSource = triggerSource,
    )
}
