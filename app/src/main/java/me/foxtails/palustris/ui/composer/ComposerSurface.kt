package me.foxtails.palustris.ui.composer

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.components.BeelineBubbleMinHeight
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

/** Test tag of the back bubble that runs the guarded close. */
internal const val COMPOSER_BACK_TAG = "composer_back"

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
    val scheduledMessage = stringResource(R.string.composer_scheduled_notice)
    val quoteDroppedMessage = stringResource(R.string.quote_dropped_notice)
    var draftsOpen by rememberSaveable { mutableStateOf(false) }
    val publishing = contract.publishing || owner.submitting
    val busy = publishing || owner.closing
    var askCompression by rememberSaveable { mutableStateOf(false) }
    fun startPublish(compress: Boolean? = null) {
        // The answer reaches the publication that this call builds, then the owner follows the setting again.
        owner.compressChoice = compress
        owner.publish(onNotice = { notice ->
            val message = when (notice) {
                PublishNotice.Scheduled -> scheduledMessage
                PublishNotice.QuoteDropped -> quoteDroppedMessage
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }) { replySent, quoteSent ->
            val message = when {
                replySent -> replySentMessage
                quoteSent -> quoteSentMessage
                else -> null
            }
            message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            onClose()
        }
        owner.compressChoice = null
    }
    fun publish() {
        if (owner.mustAskCompression()) askCompression = true else startPublish()
    }
    if (askCompression) {
        AlertDialog(
            onDismissRequest = { askCompression = false },
            title = { Text(stringResource(R.string.composer_compress_title)) },
            text = { Text(stringResource(R.string.composer_compress_message)) },
            confirmButton = {
                TextButton(onClick = { askCompression = false; startPublish(compress = true) }) {
                    Text(stringResource(R.string.composer_compress_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { askCompression = false; startPublish(compress = false) }) {
                    Text(stringResource(R.string.composer_compress_no))
                }
            },
        )
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
                    editing = owner.editor.editing != null,
                    scheduling = owner.editor.options.scheduledAtEpochMillis != null,
                    onPublish = ::publish,
                    postEnabled = owner.canPublish && !busy && owner.editor.postable(contract.limits) &&
                        (owner.editor.editing?.let { !it.loading && owner.hasChanges } ?: true),
                    publishing = publishing,
                    posted = contract.publishPosted,
                    total = contract.publishTotal,
                    mediaProgress = contract.publishMediaProgress,
                )
            }
        },
    ) { topReserve ->
        if (draftsOpen) {
            Box(Modifier.padding(top = topReserve)) {
                DraftsScreen(
                    drafts = owner.drafts,
                    onEdit = { draft -> owner.save { owner.requestDraft(draft); draftsOpen = false } },
                    onDelete = owner::deleteDraft,
                    scheduled = contract.limits.posting.scheduling?.let { scheduling ->
                        ScheduledTab(
                            items = owner.scheduled,
                            loading = owner.scheduledLoading,
                            error = owner.scheduledError,
                            minLeadSeconds = scheduling.minLeadSeconds,
                            onRefresh = owner::refreshScheduled,
                            onCancel = owner::cancelScheduled,
                            onReschedule = owner::reschedule,
                        )
                    },
                )
            }
        } else {
            ComposerBody(
                owner = owner,
                contract = contract,
                account = account,
                onRequestEmoji = onRequestEmoji,
                pendingEmojiInsertion = pendingEmojiInsertion,
                onEmojiInsertionApplied = onEmojiInsertionApplied,
                topReserve = topReserve,
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
    content: @Composable (topReserve: Dp) -> Unit,
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
                            ComposerLayers(topBar = { topBar(requestClose) }, statusBarClearance = false, content = content)
                        }
                    }
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxSize().testTag(COMPOSER_SURFACE_TAG),
                color = MaterialTheme.colorScheme.surface,
            ) {
                TriggerSurface(
                    state = surface,
                    trigger = { triggerSource?.bounds },
                    dismissLabel = dismissLabel,
                    onClosed = onDismiss,
                    returnFocus = { triggerSource?.restoreFocus() },
                ) { requestClose ->
                    ComposerLayers(topBar = { topBar(requestClose) }, statusBarClearance = true, content = content)
                }
            }
        }
    }
}

/** Height of the row of top bubbles: one bubble and its margins. */
private val TOP_BUBBLE_ROW_HEIGHT = BeelineBubbleMinHeight + 16.dp

/**
 * The two layers of the composer. The content layer scrolls and reserves the top bubble row, below
 * the status bar when [statusBarClearance] is set. The bubble layer floats over it and never
 * scrolls. The content lambda receives the clearance it must keep at its top.
 */
@Composable
private fun ComposerLayers(
    topBar: @Composable () -> Unit,
    statusBarClearance: Boolean,
    content: @Composable (topReserve: Dp) -> Unit,
) {
    val statusBar = if (statusBarClearance) WindowInsets.statusBars.asPaddingValues().calculateTopPadding() else 0.dp
    Box(Modifier.fillMaxSize()) {
        content(statusBar + TOP_BUBBLE_ROW_HEIGHT)
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .then(if (statusBarClearance) Modifier.statusBarsPadding() else Modifier),
        ) { topBar() }
    }
}

/**
 * Composer top bubbles. A back circle runs the guarded close on the left. Drafts and the
 * emphasized Post pill sit on the right. While publishing the Post pill shows a spinner with the
 * posted entry count.
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
    mediaProgress: Float? = null,
    editing: Boolean = false,
    scheduling: Boolean = false,
) {
    val closeDescription = stringResource(R.string.composer_close)
    val label = when {
        publishing && mediaProgress != null -> stringResource(R.string.composer_video_converting, (mediaProgress * 100).toInt())
        publishing && total > 0 -> stringResource(R.string.composer_publish_progress, posted, total)
        publishing -> ""
        editing -> stringResource(R.string.composer_save)
        scheduling -> stringResource(R.string.composer_schedule_action)
        else -> stringResource(R.string.composer_publish)
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ComposerCircleBubble(AppIcons.Back, closeDescription, onClose, Modifier.testTag(COMPOSER_BACK_TAG))
        Spacer(Modifier.weight(1f))
        if (!editing) {
            ComposerPillBubble(stringResource(R.string.drafts_page_title), onOpenDrafts, enabled = draftsEnabled)
        }
        ComposerPillBubble(
            label = label,
            onClick = onPublish,
            enabled = postEnabled,
            tone = ComposerBubbleTone.Emphasized,
            modifier = Modifier.testTag(COMPOSER_POST_TAG),
            leading = if (publishing) {
                { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
            } else {
                null
            },
        )
    }
}

/** Top bubble of the drafts browser inside the composer. Back returns to the editor. */
@Composable
internal fun ComposerDraftsTopBar(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ComposerCircleBubble(AppIcons.Back, stringResource(R.string.app_back), onBack)
        Text(stringResource(R.string.drafts_page_title), style = MaterialTheme.typography.titleLarge)
    }
}
