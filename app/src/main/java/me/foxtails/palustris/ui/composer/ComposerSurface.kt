package me.foxtails.palustris.ui.composer

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.emoji.ComposerField
import me.foxtails.palustris.ui.motion.TriggerSurface
import me.foxtails.palustris.ui.motion.TriggerSurfaceSource
import me.foxtails.palustris.ui.motion.rememberTriggerSurfaceState
import me.foxtails.palustris.ui.shell.ComposerContract

/** Test tag of the composer surface container. */
internal const val COMPOSER_SURFACE_TAG = "composer_surface"

/** Test tag of the card scrim that runs the guarded close. */
internal const val COMPOSER_SCRIM_TAG = "composer_scrim"

/** Test tag of the top-bar Post button. */
internal const val COMPOSER_POST_TAG = "composer_post"

/** Width cap of the floating composer card on expanded windows. */
private val COMPOSER_CARD_MAX_WIDTH = 600.dp

/** Fraction of the window height the floating card may use at most. */
private const val COMPOSER_CARD_MAX_HEIGHT_FRACTION = 0.85f

/** Dim behind the floating card. */
private val COMPOSER_CARD_SCRIM = Color.Black.copy(alpha = 0.32f)

/**
 * Assembles the composer surface for the connected session. The shell chooses placement
 * and owns the guarded close. This host owns the drafts browser state, the top bar, and
 * the editor bindings. Opening a draft saves the current editor first.
 */
@Composable
internal fun ComposerSurfaceHost(
    owner: ComposerOwner,
    contract: ComposerContract,
    account: Account?,
    card: Boolean,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    onRequestEmoji: (ComposerField) -> Unit,
    pendingEmojiInsertion: Pair<EmojiChoice, ComposerField>?,
    onEmojiInsertionApplied: () -> Unit,
    triggerSource: TriggerSurfaceSource? = null,
) {
    val context = LocalContext.current
    val replySentMessage = stringResource(R.string.reply_sent)
    val quoteSentMessage = stringResource(R.string.quote_sent)
    var draftsOpen by rememberSaveable { mutableStateOf(false) }
    val publishing = contract.publishing || owner.submitting
    val busy = publishing || owner.closing
    fun publish() {
        owner.publish { replySent, quoteSent ->
            val message = when {
                replySent -> replySentMessage
                quoteSent -> quoteSentMessage
                else -> null
            }
            message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            onClose()
        }
    }
    ComposerSurface(
        card = card,
        onDismiss = onDismiss,
        triggerSource = triggerSource,
        topBar = { requestClose ->
            if (draftsOpen) {
                ComposerDraftsTopBar(onBack = { draftsOpen = false })
            } else {
                ComposerTopBar(
                    onClose = requestClose,
                    onOpenDrafts = { draftsOpen = true },
                    draftsEnabled = !busy,
                    onPublish = ::publish,
                    postEnabled = owner.canPublish && !busy && owner.editor.postable(contract.limits),
                    publishing = publishing,
                    posted = contract.publishPosted,
                    total = contract.publishTotal,
                )
            }
        },
    ) {
        if (draftsOpen) {
            DraftsScreen(
                drafts = owner.drafts,
                onEdit = { draft -> owner.save { owner.requestDraft(draft); draftsOpen = false } },
                onDelete = owner::deleteDraft,
            )
        } else {
            ComposerBody(
                owner = owner,
                contract = contract,
                account = account,
                onRequestEmoji = onRequestEmoji,
                pendingEmojiInsertion = pendingEmojiInsertion,
                onEmojiInsertionApplied = onEmojiInsertionApplied,
            )
        }
    }
}

/**
 * Matches the dialog window to the surface. The dialog content stays below the status bar,
 * so the window background shows through that strip. Painting it the surface color keeps
 * the strip seamless instead of the default window gray. Icon appearance follows the
 * surface luminance, like the shell navigation bars.
 */
@Composable
private fun DialogSystemBars() {
    val view = LocalView.current
    val surface = MaterialTheme.colorScheme.surface
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        window.isStatusBarContrastEnforced = false
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(surface.toArgb()))
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
            surface.luminance() > 0.5f
    }
}

/**
 * Composer placement. Compact windows (compact-wide included) get a full-screen surface.
 * Expanded windows get a floating card at most 600 dp wide and 85% of the window height.
 * The dialog reports back presses and outside taps to [onDismiss], which is the shell
 * guarded close. The trigger motion and focus return stay with [TriggerSurface].
 */
@Composable
internal fun ComposerSurface(
    card: Boolean,
    onDismiss: () -> Unit,
    triggerSource: TriggerSurfaceSource? = null,
    topBar: @Composable (requestClose: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    val surface = rememberTriggerSurfaceState()
    val dismissLabel = stringResource(R.string.composer_close)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DialogSystemBars()
        if (card) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize()
                        .background(COMPOSER_CARD_SCRIM)
                        .testTag(COMPOSER_SCRIM_TAG)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = dismissLabel,
                            onClick = onDismiss,
                        ),
                )
                BoxWithConstraints(Modifier.align(Alignment.Center)) {
                    Surface(
                        modifier = Modifier.widthIn(max = COMPOSER_CARD_MAX_WIDTH)
                            .heightIn(max = maxHeight * COMPOSER_CARD_MAX_HEIGHT_FRACTION)
                            .testTag(COMPOSER_SURFACE_TAG),
                        shape = MaterialTheme.shapes.extraLarge,
                        tonalElevation = 6.dp,
                    ) {
                        TriggerSurface(
                            state = surface,
                            trigger = { triggerSource?.bounds },
                            dismissLabel = dismissLabel,
                            onClosed = onDismiss,
                            returnFocus = { triggerSource?.restoreFocus() },
                        ) { requestClose ->
                            Column(Modifier.fillMaxWidth()) {
                                topBar(requestClose)
                                content()
                            }
                        }
                    }
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxSize().statusBarsPadding().testTag(COMPOSER_SURFACE_TAG),
                color = MaterialTheme.colorScheme.surface,
            ) {
                TriggerSurface(
                    state = surface,
                    trigger = { triggerSource?.bounds },
                    dismissLabel = dismissLabel,
                    onClosed = onDismiss,
                    returnFocus = { triggerSource?.restoreFocus() },
                ) { requestClose ->
                    Column(Modifier.fillMaxSize()) {
                        topBar(requestClose)
                        content()
                    }
                }
            }
        }
    }
}

/**
 * Composer top bar. Close sits on the left. Drafts and the filled Post button sit on the
 * right. While publishing the Post button shows a spinner with the posted entry count.
 */
@Composable
internal fun ComposerTopBar(
    onClose: () -> Unit,
    onOpenDrafts: () -> Unit,
    draftsEnabled: Boolean,
    onPublish: () -> Unit,
    postEnabled: Boolean,
    publishing: Boolean,
    posted: Int,
    total: Int,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(AppIcons.Close, stringResource(R.string.composer_close)) }
        Spacer(Modifier.weight(1f))
        TextButton(enabled = draftsEnabled, onClick = onOpenDrafts) {
            Text(stringResource(R.string.drafts_page_title))
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onPublish, enabled = postEnabled, modifier = Modifier.testTag(COMPOSER_POST_TAG)) {
            if (publishing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                if (total > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.composer_publish_progress, posted, total))
                }
            } else {
                Text(stringResource(R.string.composer_publish))
            }
        }
    }
}

/** Top bar of the drafts browser inside the composer. Back returns to the editor. */
@Composable
internal fun ComposerDraftsTopBar(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(AppIcons.Back, stringResource(R.string.app_back)) }
        Text(stringResource(R.string.drafts_page_title), style = MaterialTheme.typography.titleLarge)
    }
}
