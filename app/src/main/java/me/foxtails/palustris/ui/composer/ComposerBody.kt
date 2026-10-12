package me.foxtails.palustris.ui.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.PostLimits
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.components.chromeBackdropSource
import me.foxtails.palustris.ui.components.floatingChromeBlur
import me.foxtails.palustris.ui.emoji.ComposerField
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.shell.ComposerContract

/**
 * The composer body in two layers. The scroll layer holds the cards. It reserves [topReserve] at its top and the
 * height of the bottom bubbles at its bottom, so a long post scrolls under the bubbles and every card can still
 * be reached. The bubble layer floats over it: row 1 acts on the focused card, and row 2 acts on the thread and
 * shows only while the first card has focus, because the first card decides the audience and options of all.
 */
@Composable
internal fun ComposerBody(
    owner: ComposerOwner,
    contract: ComposerContract,
    account: Account?,
    onRequestEmoji: (ComposerField) -> Unit,
    pendingEmojiInsertion: Pair<EmojiChoice, ComposerField>?,
    onEmojiInsertionApplied: () -> Unit,
    topReserve: Dp = 64.dp,
) {
    val editor = owner.editor
    val editing = editor.editing
    var focused by remember { mutableStateOf(ComposerField.Text) }
    val hashtags = remember { ComposerHashtags() }
    // A removed entry cannot keep focus; the first entry takes it.
    val focusedEntry = focused.entryId?.let(editor::entry) ?: editor.first
    val warningFocused = focused.kind == ComposerField.Kind.Warning && focusedEntry.warningEnabled
    LaunchedEffect(pendingEmojiInsertion, editor.entries.size) {
        val target = pendingEmojiInsertion?.second?.entryId
        if (target != null && editor.entry(target) == null) onEmojiInsertionApplied()
    }
    val audienceOptions = contract.availableAudiences.sortedBy { it.ordinal }
    val media = rememberComposerMedia(owner, contract)
    media.AltTextDialog()
    var focusRequest by remember { mutableStateOf<String?>(null) }
    // The entry whose warning the user may copy into a new entry. Null when no question is open.
    var warningPrompt by remember { mutableStateOf<String?>(null) }
    fun addAfter(entryId: String, copyWarning: Boolean) {
        val source = editor.entry(entryId) ?: return
        val added = owner.addEntryAfter(entryId) ?: return
        if (copyWarning) {
            owner.setEntryWarning(added, source.warning)
            owner.setEntryWarningEnabled(added, true)
        }
        focusRequest = added
    }
    warningPrompt?.let { entryId ->
        AlertDialog(
            onDismissRequest = { warningPrompt = null },
            title = { Text(stringResource(R.string.composer_reuse_warning_title)) },
            confirmButton = {
                TextButton(onClick = { warningPrompt = null; addAfter(entryId, copyWarning = true) }) {
                    Text(stringResource(R.string.composer_reuse_warning_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { warningPrompt = null; addAfter(entryId, copyWarning = false) }) {
                    Text(stringResource(R.string.composer_reuse_warning_no))
                }
            },
        )
    }
    var bubbleStackPx by remember { mutableIntStateOf(0) }
    val bottomReserve = with(LocalDensity.current) { bubbleStackPx.toDp() } + 8.dp
    val target = owner.quoteTarget
    val isReply = owner.isReply
    Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        val backdrop = LocalComposerBackdrop.current
        Column(
            Modifier.fillMaxSize().chromeBackdropSource(backdrop).verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = topReserve, bottom = bottomReserve),
        ) {
            if (isReply && target != null) ComposerReplyTarget(target)
            editor.entries.forEachIndexed { index, entry ->
                key(entry.id) {
                    val quote = target?.takeIf { index == 0 && !isReply }
                    ComposerEntryRow(
                        entry = entry,
                        layout = ComposerEntryLayout(index, last = index == editor.entries.lastIndex, focusRequested = focusRequest == entry.id),
                        account = account,
                        limits = contract.limits,
                        pendingEmojiInsertion = pendingEmojiInsertion,
                        actions = ComposerEntryActions(
                            onTextChange = { owner.setEntryText(entry.id, it) },
                            onWarningChange = { owner.setEntryWarning(entry.id, it) },
                            onFocus = { kind -> focused = ComposerField(kind, entry.id) },
                            onEmojiInserted = onEmojiInsertionApplied,
                            onFocusRequestHandled = { focusRequest = null },
                            onRemove = { owner.removeEntry(entry.id) },
                            loadThumbnail = media.loadThumbnail,
                            onRemoveMedia = { owner.removeMedia(entry.id, it) },
                            onEditAlt = { media.editAlt(entry.id, it) },
                            onPollChange = { owner.setPoll(entry.id, it) },
                            pollRemovable = editing == null,
                            hashtags = hashtags,
                        ),
                        // The first card of a reply has its avatar on the right, under the post it answers.
                        decor = ComposerEntryDecor(
                            avatarOnRight = index > 0 || isReply,
                            quoted = quote?.let { { ComposerQuotedCard(it) } },
                            belowCard = quote?.let { { ComposerQuoteNotices(it, contract.quoteConfirmed) } },
                        ),
                    )
                }
            }
            if (editing != null) {
                if (editing.loading && editor.error == null) {
                    Text(
                        stringResource(R.string.composer_edit_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (editing.attachments.isNotEmpty()) {
                    ComposerEditAttachments(
                        attachments = editing.attachments,
                        maxAltLength = contract.limits.posting.maxAltTextLength,
                        onAlt = owner::setEditAlt,
                        onRemove = owner::removeEditAttachment,
                    )
                }
                if (editor.first.poll != null && editor.first.poll != editing.originalPoll) {
                    Text(
                        stringResource(R.string.composer_edit_poll_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("composer_edit_poll_warning"),
                    )
                }
            }
            if (!owner.canPublish) {
                Text(
                    stringResource(R.string.composer_publish_disabled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val error = contract.error ?: editor.error
            AnimatedStatePane(stateKey = error != null, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().floatingChromeBlur(backdrop = backdrop)
                .onSizeChanged { bubbleStackPx = it.height },
        ) {
            // The chips follow the focused text field. The warning field never shows them.
            ComposerHashtagChips(
                fragment = hashtags.active?.takeIf { it.entryId == focusedEntry.id && !warningFocused }?.token?.fragment,
                suggestions = contract.hashtagSuggestions,
                onInsert = hashtags::insert,
            )
            ComposerToolbar(
                entry = focusedEntry,
                limits = contract.limits,
                editing = editing != null,
                attachRows = composerAttachRows(
                    entry = focusedEntry,
                    editing = editing != null,
                    photo = media.photoState(focusedEntry),
                    pollSupported = contract.limits.posting.poll != null,
                    pollEnabled = focusedEntry.media.isEmpty() && !contract.publishing && !owner.submitting,
                    onPhoto = { media.onPhoto(focusedEntry) },
                    onPoll = {
                        val limits = contract.limits.posting.poll
                        if (focusedEntry.poll != null) owner.setPoll(focusedEntry.id, null) else if (limits != null) owner.setPoll(focusedEntry.id, newPoll(limits))
                    },
                    onToggleWarning = {
                        owner.setEntryWarningEnabled(focusedEntry.id, !focusedEntry.warningEnabled)
                        if (focusedEntry.warningEnabled) focused = ComposerField(ComposerField.Kind.Text, focusedEntry.id)
                    },
                ),
                onEmoji = {
                    val kind = if (warningFocused) ComposerField.Kind.Warning else ComposerField.Kind.Text
                    onRequestEmoji(ComposerField(kind, focusedEntry.id))
                },
                addEnabled = focusedEntry.hasPostableContent() && !contract.publishing && !owner.submitting,
                onAddEntry = {
                    if (focusedEntry.effectiveWarning.isNotBlank()) warningPrompt = focusedEntry.id else addAfter(focusedEntry.id, copyWarning = false)
                },
            )
            if (editing == null && focusedEntry.id == editor.first.id) {
                ComposerOptionsRow(
                    options = editor.options,
                    audience = editor.audience,
                    posting = contract.limits.posting,
                    entryCount = editor.entries.size,
                    onLocalOnly = owner::setLocalOnly,
                    onQuotePolicy = owner::setQuotePolicy,
                    onScheduledAt = owner::setScheduledAt,
                    audienceOptions = audienceOptions,
                    onAudience = owner::setAudience,
                )
            }
        }
    }
}

/**
 * Row 1 of the bubbles: the attachment circle and the emoji circle on the left, the characters remaining for the focused
 * card and the add circle on the right. The attachment circle opens [ComposerAttachMenu] with [attachRows] (photo,
 * content warning, poll) and shows the selected tone while the card has a warning or a poll. The add circle is absent
 * while editing a post.
 */
@Composable
internal fun ComposerToolbar(
    entry: ComposerEntryState,
    limits: PostLimits,
    attachRows: List<ComposerAttachRow>,
    onEmoji: () -> Unit,
    addEnabled: Boolean,
    onAddEntry: () -> Unit,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
) {
    val remaining = entry.remaining(limits)
    var menuOpen by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    val attachActive = entry.poll != null || entry.warningEnabled
    val attachDescription = stringResource(R.string.composer_attach)
    val activeDescription = stringResource(R.string.composer_attach_active)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ComposerCircleBubble(
            AppIcons.Paperclip,
            attachDescription,
            onClick = { menuOpen = true },
            modifier = Modifier.testTag("composer_attach").onGloballyPositioned { anchor = it.boundsInWindow() }
                .semantics { if (attachActive) stateDescription = activeDescription },
            tone = if (attachActive) ComposerBubbleTone.Selected else ComposerBubbleTone.Plain,
        )
        ComposerCircleBubble(ComposerIcons.Emoji, stringResource(R.string.emoji_open_picker), onEmoji)
        Box(Modifier.weight(1f))
        if (remaining != null) {
            ComposerPillBubble(
                label = remaining.toString(),
                description = stringResource(R.string.composer_characters_remaining, remaining),
                onClick = null,
                labelColor = if (remaining < 0) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        }
        if (!editing) {
            ComposerCircleBubble(ComposerIcons.Add, stringResource(R.string.composer_add_post), onAddEntry, enabled = addEnabled)
        }
    }
    if (menuOpen) ComposerAttachMenu(anchor, attachRows, onDismiss = { menuOpen = false })
}
