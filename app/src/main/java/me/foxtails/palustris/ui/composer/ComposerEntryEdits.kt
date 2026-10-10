package me.foxtails.palustris.ui.composer

import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.PostDraftEntry

/**
 * Pure edits of the composer thread. [ComposerOwner] applies them to the editor state it owns, so
 * entry rules live here and the owner keeps only the sequencing, revision, and draft I/O.
 */
internal fun ComposerEditorState.withEntryAfter(entryId: String, added: ComposerEntryState): ComposerEditorState {
    val index = entries.indexOfFirst { it.id == entryId }
    if (index < 0) return this
    return copy(entries = entries.toMutableList().apply { add(index + 1, added) })
}

/** Drops an entry. The first entry and unknown ids stay, because a thread always has a first post. */
internal fun ComposerEditorState.withoutEntry(entryId: String): ComposerEditorState =
    if (entryId == first.id) this else copy(entries = entries.filterNot { it.id == entryId })

internal fun ComposerEntryState.withoutMedia(mediaId: String): ComposerEntryState =
    copy(media = media.filterNot { it.id == mediaId })

/** Sets the alt text of one image. Blank text clears it. */
internal fun ComposerEntryState.withAltText(mediaId: String, altText: String?): ComposerEntryState =
    copy(media = media.map { if (it.id == mediaId) it.copy(altText = altText?.takeIf(String::isNotBlank)) else it })

/** The editor entries of a saved draft. The first entry takes a new id; follow-ups keep theirs. */
internal fun PostDraft.toEditorEntries(): List<ComposerEntryState> = listOf(
    ComposerEntryState(
        text = text,
        warning = contentWarning.orEmpty(),
        warningEnabled = !contentWarning.isNullOrBlank(),
        media = media,
    ),
) + followUps.map {
    ComposerEntryState(
        id = it.id,
        text = it.text,
        warning = it.contentWarning.orEmpty(),
        warningEnabled = !it.contentWarning.isNullOrBlank(),
        media = it.media,
    )
}

/** Entries 2 and later as draft follow-ups. A hidden warning field is not saved. */
internal fun ComposerEditorState.toFollowUps(): List<PostDraftEntry> = entries.drop(1).map {
    PostDraftEntry(
        id = it.id,
        text = it.text,
        contentWarning = it.effectiveWarning.takeIf(String::isNotBlank),
        media = it.media,
    )
}
