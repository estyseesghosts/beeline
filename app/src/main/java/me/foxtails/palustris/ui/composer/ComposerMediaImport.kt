package me.foxtails.palustris.ui.composer

import android.net.Uri
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.DraftMediaImportError
import me.foxtails.palustris.domain.PostLimits

/** What one pick added and what it dropped. [error] is the first rejection reason, if any. */
internal data class MediaImportReport(val added: Int, val dropped: Int, val error: DraftMediaImportError?)

/**
 * Copies picked images into entry [entryId], one after another, then reports once.
 *
 * The photo picker may ignore its item limit (the document-picker fallback does), so the count
 * limit is enforced here: images past [slots] are dropped and counted in the report. A rejected
 * file does not stop the others. The draft media directory is named by [ComposerOwner.mediaDraftId],
 * which gives an unsaved editor an id before its first save.
 */
internal fun ComposerOwner.importImages(
    entryId: String,
    picked: List<Uri>,
    slots: Int,
    onDone: (MediaImportReport) -> Unit,
) {
    val accepted = picked.take(slots.coerceAtLeast(0))
    val dropped = picked.size - accepted.size
    if (accepted.isEmpty()) {
        onDone(MediaImportReport(added = 0, dropped = dropped, error = null))
        return
    }
    val draftId = mediaDraftId()
    var added = 0
    var error: DraftMediaImportError? = null
    fun next(index: Int) {
        if (index >= accepted.size) {
            onDone(MediaImportReport(added, dropped, error))
            return
        }
        draftsContract.actions.importMedia(
            draftId = draftId,
            source = accepted[index],
            onResult = { media ->
                addMedia(entryId, media)
                added += 1
                next(index + 1)
            },
            onError = { reason ->
                error = error ?: reason
                next(index + 1)
            },
        )
    }
    next(0)
}

/** How the photo button behaves for the focused entry. */
internal enum class PhotoButtonState { Enabled, Disabled, SignInAgain }

/** Free image slots of [entry]. Null means the server reported no limit. */
internal fun ComposerEntryState.imageSlots(limits: PostLimits): Int? =
    limits.posting.maxAttachments?.let { (it - media.size).coerceAtLeast(0) }

/**
 * The photo button state. It is disabled while a publish runs, when the entry is full, when the
 * server does not support uploads, and on the first entry while it quotes a post on a server that
 * rejects a quote with images. A denied token opens the sign-in-again flow. An unknown or
 * temporarily unavailable upload state keeps the button enabled, because one failed probe is not
 * proof that the server lacks uploads.
 */
internal fun ComposerEditorState.photoButtonState(
    entry: ComposerEntryState,
    limits: PostLimits,
    mediaUpload: CapabilityStatus,
    busy: Boolean,
): PhotoButtonState = when {
    busy || mediaUpload == CapabilityStatus.Unsupported -> PhotoButtonState.Disabled
    mediaUpload == CapabilityStatus.Denied -> PhotoButtonState.SignInAgain
    entry.imageSlots(limits) == 0 -> PhotoButtonState.Disabled
    entry.id == first.id && quoteOf != null && !limits.posting.quoteWithMedia -> PhotoButtonState.Disabled
    else -> PhotoButtonState.Enabled
}
