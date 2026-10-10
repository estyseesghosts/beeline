package me.foxtails.palustris.ui.composer

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.DraftMedia
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.PostingVisibilityPolicy
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.ThreadPublication
import me.foxtails.palustris.domain.ThreadPublicationEntry
import me.foxtails.palustris.domain.ThreadPublicationMedia
import me.foxtails.palustris.domain.ThreadPublishFailure
import me.foxtails.palustris.ui.UiStrings

/**
 * Pure mapping between the editor thread and its publication. [ComposerOwner] applies these to
 * the editor state it owns, so entry rules live here and the owner keeps only the sequencing,
 * revision, and draft I/O.
 */
internal fun ComposerEditorState.toThreadPublication(
    draftId: String,
    audience: Audience,
    replyTo: EntityId?,
    quoteOf: EntityId?,
    compress: Boolean,
    accountId: AccountId?,
): ThreadPublication = ThreadPublication(
    draftId = draftId,
    entries = entries.map { entry ->
        ThreadPublicationEntry(
            id = entry.id,
            text = entry.text,
            contentWarning = entry.effectiveWarning.takeIf(String::isNotBlank),
            media = entry.media.map { media ->
                ThreadPublicationMedia(
                    id = media.id,
                    mimeType = media.mimeType,
                    fileName = threadFileName(media),
                    description = media.altText,
                    sensitive = entry.effectiveWarning.isNotBlank(),
                    width = media.width,
                    height = media.height,
                    byteSize = media.byteSize,
                )
            },
        )
    },
    audience = audience,
    replyTo = replyTo,
    quoteOf = quoteOf,
    compress = compress,
    accountId = accountId,
)

private fun threadFileName(media: DraftMedia): String {
    val extension = when (media.mimeType.lowercase()) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> media.mimeType.substringAfter('/').substringBefore(';').takeIf(String::isNotBlank) ?: "img"
    }
    return "${media.id}.$extension"
}

/** The thread audience when the server offers it, else null. An unknown set keeps the editor choice. */
internal fun ComposerEditorState.validatedAudience(knownAudiences: Set<Audience>): Audience? =
    if (knownAudiences.isEmpty()) {
        audience
    } else {
        runCatching {
            PostingVisibilityPolicy.validateExplicit(audience, ServerCapabilities(audiences = knownAudiences))
        }.getOrNull()
    }

/** Restores an entry of a remaining publication. Ids stay so a retry keeps its idempotency key. */
internal fun ThreadPublicationEntry.toEntryState(): ComposerEntryState = ComposerEntryState(
    id = id,
    text = text,
    warning = contentWarning.orEmpty(),
    warningEnabled = !contentWarning.isNullOrBlank(),
    media = media.map {
        DraftMedia(
            id = it.id,
            mimeType = it.mimeType,
            width = it.width,
            height = it.height,
            byteSize = it.byteSize,
            altText = it.description,
        )
    },
)

/** Human detail for a publish failure. */
internal fun ThreadPublishFailure.detail(uiStrings: UiStrings): String =
    (error as? Exception)?.let(uiStrings::sourceError) ?: error.message.orEmpty()

/**
 * The editor that keeps the entries a failed publish did not post. Reply and quote targets
 * follow the remaining publication, and the error names the failed entry.
 */
internal fun ComposerOwner.retainStates(failure: ThreadPublishFailure): ComposerEditorState {
    val states = failure.remaining.entries.map { it.toEntryState() }
    return editor.copy(
        entries = states,
        savedEntries = states.map(ComposerEntryState::content),
        replyTo = failure.remaining.replyTo,
        savedReplyTo = failure.remaining.replyTo?.value,
        quoteOf = failure.remaining.quoteOf,
        savedQuoteOf = failure.remaining.quoteOf?.value,
        error = uiStrings.composerEntryPublishFailed(failure.failedIndex + 1, failure.totalEntries, failure.detail(uiStrings)),
    )
}
