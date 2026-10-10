package me.foxtails.palustris.ui.shell

import android.graphics.Bitmap
import android.net.Uri
import me.foxtails.palustris.domain.DraftMedia
import me.foxtails.palustris.domain.DraftMediaImportError
import me.foxtails.palustris.domain.PostDraft

/**
 * Draft listing and persistence for the active account.
 *
 * The owner hides storage selection, legacy migration, and account scoping. The shell keeps only
 * composer fields. A load or delete failure reports an explicit message. [Empty] is an inert
 * preview value.
 */
data class DraftsContract(
    val actions: Actions,
) {
    interface Actions {
        fun load(onResult: (List<PostDraft>) -> Unit, onError: (String) -> Unit)
        fun save(draft: PostDraft, onResult: (PostDraft) -> Unit, onError: () -> Unit)
        fun delete(draftId: String, onDone: () -> Unit, onError: (String) -> Unit)

        /** Copies a picked image into the draft's media. Previews and fakes without storage reject it. */
        fun importMedia(
            draftId: String,
            source: Uri,
            onResult: (DraftMedia) -> Unit,
            onError: (DraftMediaImportError) -> Unit,
        ) = onError(DraftMediaImportError.Unreadable)

        /** Decodes a thumbnail of a draft image, or reports null. */
        fun thumbnail(draftId: String, mediaId: String, maxEdge: Int, onResult: (Bitmap?) -> Unit) = onResult(null)
    }

    companion object {
        val Empty = DraftsContract(DraftsEmptyActions)
    }
}

private object DraftsEmptyActions : DraftsContract.Actions {
    override fun load(onResult: (List<PostDraft>) -> Unit, onError: (String) -> Unit) = onResult(emptyList())
    override fun save(draft: PostDraft, onResult: (PostDraft) -> Unit, onError: () -> Unit) = Unit
    override fun delete(draftId: String, onDone: () -> Unit, onError: (String) -> Unit) = onDone()
}
