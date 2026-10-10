package me.foxtails.palustris.ui.composer

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.DraftMediaImportError
import me.foxtails.palustris.ui.shell.ComposerContract

/**
 * What the composer body needs to attach images: the photo picker, the alt text editor, and
 * thumbnails. State owned: the entry that receives the next pick and the image whose alt text is
 * open. Both belong to the composer body; the editor and the draft media live in
 * [ComposerOwner] and the draft storage.
 */
@Stable
internal class ComposerMediaControls(private val owner: ComposerOwner) {
    /** The latest contract. The body sets it on each composition, so limits and access stay current. */
    var contract: ComposerContract = ComposerContract.Empty

    /** Opens the system picker for one image or for several. The body installs the launchers. */
    var launchPicker: (single: Boolean) -> Unit = {}

    private var altTarget by mutableStateOf<Pair<String, String>?>(null)

    /** The entry that receives the pick in flight. The picker answers after the user returns. */
    private var pickEntry: String? = null

    val loadThumbnail: ThumbnailLoader = { media, maxEdge, onResult ->
        val draftId = owner.editor.effectiveDraftId
        if (draftId == null) onResult(null) else owner.draftsContract.actions.thumbnail(draftId, media.id, maxEdge, onResult)
    }

    /** How the photo button looks for [entry]. */
    fun photoState(entry: ComposerEntryState): PhotoButtonState =
        owner.editor.photoButtonState(entry, contract.limits, contract.mediaUpload, contract.publishing || owner.submitting)

    /** Opens the picker, or the sign-in-again flow when the token cannot upload. */
    fun onPhoto(entry: ComposerEntryState) {
        when (photoState(entry)) {
            PhotoButtonState.Enabled -> {
                pickEntry = entry.id
                launchPicker(entry.imageSlots(contract.limits) == 1)
            }
            PhotoButtonState.SignInAgain -> owner.save { contract.actions.signInAgain() }
            PhotoButtonState.Disabled -> Unit
        }
    }

    fun editAlt(entryId: String, mediaId: String) {
        altTarget = entryId to mediaId
    }

    /** Imports the images the picker returned into the entry that asked for them. */
    fun picked(uris: List<Uri>, context: Context) {
        val entryId = pickEntry ?: return
        pickEntry = null
        val slots = owner.editor.entry(entryId)?.imageSlots(contract.limits) ?: Int.MAX_VALUE
        owner.importImages(entryId, uris, slots) { report -> report.announce(context, contract.limits.posting.maxAttachments) }
    }

    /** The alt text editor while an image is open. Nothing shows when the image has gone. */
    @Composable
    fun AltTextDialog() {
        val (entryId, mediaId) = altTarget ?: return
        val media = owner.editor.entry(entryId)?.media?.firstOrNull { it.id == mediaId }
        if (media == null) {
            altTarget = null
            return
        }
        ComposerAltTextDialog(
            media = media,
            maxLength = contract.limits.posting.maxAltTextLength,
            loadThumbnail = loadThumbnail,
            onSave = { text ->
                owner.setAltText(entryId, mediaId, text)
                altTarget = null
            },
            onDismiss = { altTarget = null },
        )
    }
}

/** Creates the media controls and installs the picker launchers for the composer body. */
@Composable
internal fun rememberComposerMedia(owner: ComposerOwner, contract: ComposerContract): ComposerMediaControls {
    val context = LocalContext.current
    val controls = remember(owner) { ComposerMediaControls(owner) }
    controls.contract = contract
    val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        controls.picked(listOfNotNull(uri), context)
    }
    val multiple = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        controls.picked(uris, context)
    }
    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    controls.launchPicker = { onlyOne -> if (onlyOne) single.launch(request) else multiple.launch(request) }
    return controls
}

/** Tells the user what a pick dropped or rejected. A clean import stays quiet. */
private fun MediaImportReport.announce(context: Context, maxAttachments: Int?) {
    val message = when {
        dropped > 0 && maxAttachments != null -> context.getString(R.string.composer_media_limit, maxAttachments)
        error == DraftMediaImportError.NotAnImage -> context.getString(R.string.composer_media_not_image)
        error == DraftMediaImportError.TooLarge -> context.getString(R.string.composer_media_too_large)
        error == DraftMediaImportError.Unreadable -> context.getString(R.string.composer_media_unreadable)
        else -> null
    }
    message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
}
