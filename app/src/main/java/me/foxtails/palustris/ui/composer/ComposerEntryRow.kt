package me.foxtails.palustris.ui.composer

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.PostLimits
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.components.BeelineCardShape
import me.foxtails.palustris.ui.components.BeelineNestedSurfaceShape
import me.foxtails.palustris.ui.emoji.ComposerField
import me.foxtails.palustris.ui.motion.ExpandableContent
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme

/**
 * The cursor of one text field. The editor stores plain text, so the cursor lives beside the field
 * and an emoji lands where the user left it.
 */
@Stable
internal class CursorField(length: Int) {
    var selection by mutableStateOf(TextRange(length))

    /** Moves a cursor that points past the end of a shortened text back to the end. */
    fun clamp(length: Int) {
        if (selection.max > length) selection = TextRange(length)
    }

    fun value(text: String): TextFieldValue =
        TextFieldValue(text, TextRange(selection.start.coerceIn(0, text.length), selection.end.coerceIn(0, text.length)))

    /** Replaces [start] until [end] of [text] with [replacement], moves the cursor after it, and returns the new text. */
    fun replace(text: String, start: Int, end: Int, replacement: String): String {
        selection = TextRange(start + replacement.length)
        return text.replaceRange(start, end, replacement)
    }

    /** Replaces the selection of [text] with [insertion], moves the cursor after it, and returns the new text. */
    fun insert(text: String, insertion: String): String {
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(start, text.length)
        selection = TextRange(start + insertion.length)
        return text.replaceRange(start, end, insertion)
    }
}

/** Where an entry sits in the thread. The body asks for focus on a new entry through [focusRequested]. */
internal class ComposerEntryLayout(val index: Int, val last: Boolean, val focusRequested: Boolean)

/**
 * What surrounds one card. The avatar hangs on the right for every card except the first of a new post or
 * quote. [quoted] draws inside the card, below its poll. [belowCard] draws on the page background under it.
 */
internal class ComposerEntryDecor(
    val avatarOnRight: Boolean = false,
    val quoted: (@Composable () -> Unit)? = null,
    val belowCard: (@Composable () -> Unit)? = null,
)

/** What one entry row reads and reports. The body owns focus and the editor. */
internal class ComposerEntryActions(
    val onTextChange: (String) -> Unit,
    val onWarningChange: (String) -> Unit,
    val onFocus: (ComposerField.Kind) -> Unit,
    val onEmojiInserted: () -> Unit,
    val onFocusRequestHandled: () -> Unit,
    val onRemove: () -> Unit,
    val loadThumbnail: ThumbnailLoader,
    val onRemoveMedia: (mediaId: String) -> Unit,
    val onEditAlt: (mediaId: String) -> Unit,
    val onPollChange: (me.foxtails.palustris.domain.PollRequest?) -> Unit = {},
    /** False while editing a post: an edit can change a poll but cannot take it away. */
    val pollRemovable: Boolean = true,
    val hashtags: ComposerHashtags = ComposerHashtags(),
)

/**
 * One post of the thread: avatar, optional content warning field, and text field. An emoji for
 * this entry is inserted at the cursor of the field it targets, then reported through
 * [ComposerEntryActions.onEmojiInserted].
 */
@Composable
internal fun ComposerEntryRow(
    entry: ComposerEntryState,
    layout: ComposerEntryLayout,
    account: Account?,
    limits: PostLimits,
    pendingEmojiInsertion: Pair<EmojiChoice, ComposerField>?,
    actions: ComposerEntryActions,
    decor: ComposerEntryDecor = ComposerEntryDecor(avatarOnRight = layout.index > 0),
) {
    val index = layout.index
    val first = index == 0
    val focusRequester = remember(entry.id) { FocusRequester() }
    LaunchedEffect(layout.focusRequested) {
        if (layout.focusRequested) {
            focusRequester.requestFocus()
            actions.onFocusRequestHandled()
        }
    }
    val text = remember(entry.id) { CursorField(entry.text.length) }
    val warning = remember(entry.id) { CursorField(entry.warning.length) }
    LaunchedEffect(entry.text) { text.clamp(entry.text.length) }
    LaunchedEffect(entry.warning) { warning.clamp(entry.warning.length) }
    LaunchedEffect(pendingEmojiInsertion) {
        val (choice, field) = pendingEmojiInsertion ?: return@LaunchedEffect
        // A null entry id addresses the first entry. Another entry's row leaves the insertion pending.
        if (field.entryId == null && !first || field.entryId != null && field.entryId != entry.id) return@LaunchedEffect
        when (field.kind) {
            ComposerField.Kind.Text -> actions.onTextChange(text.insert(entry.text, choice.submissionValue))
            ComposerField.Kind.Warning -> actions.onWarningChange(warning.insert(entry.warning, choice.submissionValue))
        }
        actions.onEmojiInserted()
    }
    var textFocused by remember { mutableStateOf(false) }
    LaunchedEffect(entry.text, text.selection, textFocused) {
        val token = hashtagTokenAt(entry.text, text.selection.start).takeIf { textFocused && text.selection.collapsed }
        actions.hashtags.report(entry.id, token)
    }
    LaunchedEffect(actions.hashtags.pending) {
        val insertion = actions.hashtags.pending?.takeIf { it.entryId == entry.id } ?: return@LaunchedEffect
        hashtagTokenAt(entry.text, text.selection.start)?.let { token ->
            actions.onTextChange(text.replace(entry.text, token.start, token.end, hashtagInsertionText(insertion.name)))
        }
        actions.hashtags.applied()
    }
    val textDescription = if (first) stringResource(R.string.composer_post_text) else stringResource(R.string.composer_entry_text, index + 1)
    val warningRemaining = entry.warningRemaining(limits)
    val scheme = LocalPalustrisMotionScheme.current
    val avatarOnRight = decor.avatarOnRight
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(layout.focusRequested) {
        // A new card scrolls into view above the bubbles that float over the bottom of the page.
        if (layout.focusRequested) bringIntoView.bringIntoView()
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        if (!avatarOnRight) {
            ComposerAvatarColumn(account, line = !layout.last)
            Spacer(Modifier.width(COMPOSER_AVATAR_GAP))
        }
        Column(Modifier.weight(1f).bringIntoViewRequester(bringIntoView)) {
            Box(Modifier.padding(bottom = 8.dp)) {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(BeelineCardShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .animateContentSize(animationSpec = scheme.gentleSize)
                        .padding(COMPOSER_CARD_INSET)
                        .testTag("composer_card_${index + 1}"),
                ) {
                    ExpandableContent(visible = entry.warningEnabled, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = warning.value(entry.warning),
                            onValueChange = { changed ->
                                actions.onWarningChange(changed.text)
                                warning.selection = changed.selection
                            },
                            label = { Text(stringResource(R.string.composer_content_warning)) },
                            isError = warningRemaining != null && warningRemaining < 0,
                            supportingText = warningRemaining?.let { left ->
                                { Text(stringResource(R.string.composer_warning_remaining, left)) }
                            },
                            shape = BeelineNestedSurfaceShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                                .onFocusChanged { if (it.isFocused) actions.onFocus(ComposerField.Kind.Warning) },
                        )
                    }
                    BasicTextField(
                        value = text.value(entry.text),
                        onValueChange = { changed ->
                            actions.onTextChange(changed.text)
                            text.selection = changed.selection
                        },
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged {
                                textFocused = it.isFocused
                                if (it.isFocused) actions.onFocus(ComposerField.Kind.Text)
                            }
                            .semantics { contentDescription = textDescription },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { field ->
                            Box {
                                if (entry.text.isEmpty()) {
                                    val hint = if (first) R.string.composer_placeholder else R.string.composer_thread_placeholder
                                    Text(stringResource(hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                field()
                            }
                        },
                    )
                    if (entry.media.isNotEmpty()) {
                        ComposerMediaStrip(entry.media, actions.loadThumbnail, actions.onRemoveMedia, actions.onEditAlt, Modifier.padding(top = 4.dp))
                    }
                    val pollLimits = limits.posting.poll
                    if (entry.poll != null && pollLimits != null) {
                        ComposerPollEditor(
                            poll = entry.poll,
                            limits = pollLimits,
                            onChange = { actions.onPollChange(it) },
                            onRemove = { actions.onPollChange(null) },
                            removable = actions.pollRemovable,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    decor.quoted?.let { quoted -> Box(Modifier.padding(top = 4.dp)) { quoted() } }
                }
                if (!first) {
                    ComposerRemoveEntry(
                        description = stringResource(R.string.composer_remove_entry, index + 1),
                        onClick = actions.onRemove,
                        modifier = Modifier.align(if (avatarOnRight) Alignment.TopStart else Alignment.TopEnd)
                            .offset(x = if (avatarOnRight) -REMOVE_OFFSET else REMOVE_OFFSET, y = -REMOVE_OFFSET),
                    )
                }
            }
            decor.belowCard?.invoke()
        }
        if (avatarOnRight) {
            Spacer(Modifier.width(COMPOSER_AVATAR_GAP))
            ComposerAvatarColumn(account, line = !layout.last)
        }
    }
}

/** The small circle that removes an entry after the first. It hangs on the corner away from the avatar. */
@Composable
private fun ComposerRemoveEntry(description: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(REMOVE_SIZE).semantics { contentDescription = description },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(AppIcons.Close, null, Modifier.size(16.dp)) }
    }
}

private val REMOVE_SIZE = 32.dp
private val REMOVE_OFFSET = 12.dp
