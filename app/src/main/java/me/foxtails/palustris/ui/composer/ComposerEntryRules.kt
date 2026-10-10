package me.foxtails.palustris.ui.composer

import me.foxtails.palustris.domain.PostLimits

/**
 * Pure posting rules for the composer thread. [ComposerOwner] holds the editor; these rules decide
 * what the body shows and whether Post is enabled, so the owner does not grow.
 */

/** An entry can be posted with text or with at least one image. */
internal fun ComposerEntryState.hasPostableContent(): Boolean = text.isNotBlank() || media.isNotEmpty()

/** Characters left for this entry, or null when the server reported no limit. */
internal fun ComposerEntryState.remaining(limits: PostLimits): Int? = limits.remaining(text, effectiveWarning)

/** Characters left for this entry's content warning when the server limits it separately. */
internal fun ComposerEntryState.warningRemaining(limits: PostLimits): Int? =
    limits.warningRemaining(effectiveWarning)

internal fun ComposerEntryState.exceedsLimit(limits: PostLimits): Boolean = limits.exceeds(text, effectiveWarning)

/** True when every entry has content and none is over a limit. Empty entries block Post. */
internal fun ComposerEditorState.postable(limits: PostLimits): Boolean =
    entries.all { it.hasPostableContent() && !it.exceedsLimit(limits) }

/** The editor with [prepare] applied to the text of every entry, for example to clean links. */
internal fun ComposerEditorState.withPreparedText(prepare: (String) -> String): ComposerEditorState =
    copy(entries = entries.map { it.copy(text = prepare(it.text)) })
