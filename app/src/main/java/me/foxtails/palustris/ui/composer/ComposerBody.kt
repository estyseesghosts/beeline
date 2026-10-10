package me.foxtails.palustris.ui.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostLimits
import me.foxtails.palustris.domain.QuoteApproval
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.emoji.ComposerField
import me.foxtails.palustris.ui.emoji.InlineEmojiText
import me.foxtails.palustris.ui.emoji.PostText
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.shell.ComposerContract

/**
 * The composer body: the thread entries, the audience row, and the toolbar. The toolbar and the
 * counter act on the focused entry. The audience row shows only while the first entry has focus,
 * because the first entry decides the audience of every entry.
 */
@Composable
internal fun ComposerBody(
    owner: ComposerOwner,
    contract: ComposerContract,
    account: Account?,
    onRequestEmoji: (ComposerField) -> Unit,
    pendingEmojiInsertion: Pair<EmojiChoice, ComposerField>?,
    onEmojiInsertionApplied: () -> Unit,
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
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            owner.quoteTarget?.let { target ->
                ComposerTargetCard(target, owner.isReply, owner::removeTargets)
                if (!owner.isReply && !contract.quoteConfirmed) {
                    Text(
                        stringResource(R.string.quote_unconfirmed_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp).testTag("composer_quote_unconfirmed"),
                    )
                }
                if (!owner.isReply && target.post.quoteApproval == QuoteApproval.Manual) {
                    Text(
                        stringResource(R.string.quote_manual_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp).testTag("composer_quote_manual"),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            editor.entries.forEachIndexed { index, entry ->
                key(entry.id) {
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
        if (editing == null && focusedEntry.id == editor.first.id && audienceOptions.isNotEmpty()) {
            HorizontalDivider()
            ComposerAudienceRow(editor.audience, audienceOptions, owner::setAudience)
        }
        if (editing == null) {
            ComposerOptionsRow(
                options = editor.options,
                audience = editor.audience,
                posting = contract.limits.posting,
                entryCount = editor.entries.size,
                onLocalOnly = owner::setLocalOnly,
                onQuotePolicy = owner::setQuotePolicy,
                onScheduledAt = owner::setScheduledAt,
            )
        }
        HorizontalDivider()
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
            photo = media.photoState(focusedEntry),
            onPhoto = { media.onPhoto(focusedEntry) },
            pollEnabled = contract.limits.posting.poll != null && focusedEntry.media.isEmpty() && !contract.publishing && !owner.submitting,
            onPoll = {
                val limits = contract.limits.posting.poll
                if (focusedEntry.poll != null) owner.setPoll(focusedEntry.id, null) else if (limits != null) owner.setPoll(focusedEntry.id, newPoll(limits))
            },
            onEmoji = {
                val kind = if (warningFocused) ComposerField.Kind.Warning else ComposerField.Kind.Text
                onRequestEmoji(ComposerField(kind, focusedEntry.id))
            },
            addEnabled = focusedEntry.hasPostableContent() && !contract.publishing && !owner.submitting,
            onAddEntry = {
                if (focusedEntry.effectiveWarning.isNotBlank()) warningPrompt = focusedEntry.id else addAfter(focusedEntry.id, copyWarning = false)
            },
            onToggleWarning = {
                owner.setEntryWarningEnabled(focusedEntry.id, !focusedEntry.warningEnabled)
                if (focusedEntry.warningEnabled) focused = ComposerField(ComposerField.Kind.Text, focusedEntry.id)
            },
        )
    }
}

/** The target of a reply or quote with a remove button. It stays on the first entry. */
@Composable
private fun ComposerTargetCard(target: OwnedPost, isReply: Boolean, onRemove: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                InlineEmojiText(
                    text = if (isReply) {
                        stringResource(R.string.composer_replying_to, target.post.author.displayName)
                    } else {
                        stringResource(R.string.composer_quoting, target.post.author.displayName)
                    },
                    emoji = target.post.author.emoji,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRemove) { Text(stringResource(R.string.composer_remove_quote)) }
            }
            if (target.post.text.isBlank()) {
                Text(stringResource(R.string.composer_no_post_text), maxLines = 4, overflow = TextOverflow.Ellipsis)
            } else {
                PostText(target.post, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Photo, emoji, and content warning on the left. The characters remaining for the focused entry
 * on the right. The photo button follows [PhotoButtonState].
 */
@Composable
internal fun ComposerToolbar(
    entry: ComposerEntryState,
    limits: PostLimits,
    photo: PhotoButtonState,
    editing: Boolean = false,
    onPhoto: () -> Unit,
    pollEnabled: Boolean,
    onPoll: () -> Unit,
    onEmoji: () -> Unit,
    onToggleWarning: () -> Unit,
    addEnabled: Boolean,
    onAddEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emojiDescription = stringResource(R.string.emoji_open_picker)
    val warningDescription = stringResource(R.string.composer_content_warning)
    val pollDescription = stringResource(R.string.composer_poll)
    val remaining = entry.remaining(limits)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!editing) {
            IconButton(onClick = onPhoto, enabled = photo != PhotoButtonState.Disabled) {
                Icon(AppIcons.Image, stringResource(R.string.composer_add_photo))
            }
        }
        if (limits.posting.poll != null && !editing) {
            TextButton(
                onClick = onPoll,
                enabled = pollEnabled || entry.poll != null,
                modifier = Modifier.semantics { contentDescription = pollDescription }.testTag("composer_poll_toggle"),
                colors = if (entry.poll != null) {
                    ButtonDefaults.textButtonColors()
                } else {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                },
            ) { Text(stringResource(R.string.composer_poll_short)) }
        }
        TextButton(onClick = onEmoji, modifier = Modifier.semantics { contentDescription = emojiDescription }) {
            Text(stringResource(R.string.composer_emoji))
        }
        TextButton(
            onClick = onToggleWarning,
            modifier = Modifier.semantics { contentDescription = warningDescription },
            colors = if (entry.warningEnabled) {
                ButtonDefaults.textButtonColors()
            } else {
                ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            },
        ) { Text(stringResource(R.string.composer_content_warning_short)) }
        Spacer(Modifier.weight(1f))
        if (remaining != null) {
            val description = stringResource(R.string.composer_characters_remaining, remaining)
            Text(
                remaining.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp).semantics { contentDescription = description },
            )
        }
        if (!editing) {
            IconButton(onClick = onAddEntry, enabled = addEnabled) { Icon(ComposerIcons.Add, stringResource(R.string.composer_add_post)) }
        }
    }
}
