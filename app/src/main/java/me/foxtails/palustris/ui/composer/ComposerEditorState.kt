package me.foxtails.palustris.ui.composer

import androidx.compose.runtime.saveable.Saver
import java.util.UUID
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.DraftMedia
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.PollRequest
import me.foxtails.palustris.domain.PostDraftQuotePreview
import me.foxtails.palustris.domain.PostOptions

/**
 * One post of the composer thread. Images are draft media records; the bytes live in the draft
 * media store.
 */
data class ComposerEntryState(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val warning: String = "",
    val warningEnabled: Boolean = false,
    val media: List<DraftMedia> = emptyList(),
    /** The poll being written, or null. A poll and images exclude each other. */
    val poll: PollRequest? = null,
) {
    /** The warning that is saved and published. A hidden warning field contributes nothing. */
    val effectiveWarning: String get() = if (warningEnabled) warning else ""

    /** What the dirty check compares. The warning toggle alone changes nothing the user can lose. */
    val content: ComposerEntryContent get() = ComposerEntryContent(id, text, effectiveWarning, media, poll)
}

/** The saved-or-not content of one entry. */
data class ComposerEntryContent(
    val id: String,
    val text: String,
    val warning: String,
    val media: List<DraftMedia>,
    val poll: PollRequest? = null,
)

/** An image that stays on a post being edited. The bytes live on the server; only its address and alt text are known here. */
data class EditAttachmentState(val id: String, val previewUrl: String?, val description: String?)

/**
 * The post an editor is changing. The editor's first entry holds the text, warning, and poll as they
 * are now. [sourceText], [sourceWarning], [originalAttachments], and [originalPoll] hold them as the
 * server gave them, so the request can send only what changed. [loading] is true until the source
 * text arrives. An edit editor never becomes a draft and is not restored after the process dies.
 */
data class ComposerEdit(
    val target: EntityId,
    val sourceText: String = "",
    val sourceWarning: String = "",
    val originalAttachments: List<EditAttachmentState> = emptyList(),
    val attachments: List<EditAttachmentState> = emptyList(),
    val originalPoll: PollRequest? = null,
    val loading: Boolean = true,
) {
    val attachmentsChanged: Boolean get() = attachments != originalAttachments
}

/**
 * Saveable composer editor fields for one connected account.
 *
 * The composer owner holds these values. The navigation shell reads them but does not own them. The
 * saved snapshot drives the dirty check. Reply and quote target identities are stored with the
 * account and session revision they were chosen under ([boundAccount], [boundRevision]), so a
 * restored editor can verify them before it is used. The draft list is not stored here. Custom
 * emoji maps in [targetPreview] are not restored; the preview shows plain text.
 *
 * [entries] holds the posts of the thread in publish order and is never empty. The first entry
 * also answers the flat [text] and [warning] accessors, which keep single-post callers simple.
 * The audience, reply target, and quote target belong to the thread and apply to the first post.
 */
data class ComposerEditorState(
    val draftId: String? = null,
    val entries: List<ComposerEntryState> = listOf(ComposerEntryState()),
    val savedEntries: List<ComposerEntryContent> = entries.map(ComposerEntryState::content),
    val audience: Audience = Audience.Public,
    val savedAudience: Audience = Audience.Public,
    val error: String? = null,
    val replyTo: EntityId? = null,
    val quoteOf: EntityId? = null,
    val savedReplyTo: String? = null,
    val savedQuoteOf: String? = null,
    val targetPreview: PostDraftQuotePreview? = null,
    val boundAccount: AccountId? = null,
    val boundRevision: Long = 0L,
    /** Names the draft media files of an editor that has no saved draft yet. See [effectiveDraftId]. */
    val mediaDraftId: String? = null,
    /** Set while the editor changes a published post instead of writing a new one. */
    val editing: ComposerEdit? = null,
    /** Thread-level choices: local-only, who may quote, and the scheduled time. */
    val options: PostOptions = PostOptions(),
    val savedOptions: PostOptions = PostOptions(),
) {
    init {
        require(entries.isNotEmpty()) { "The composer always holds at least one entry." }
    }

    /** The saved draft id, or the id that already names this editor's picked image files. */
    val effectiveDraftId: String? get() = draftId ?: mediaDraftId

    val first: ComposerEntryState get() = entries.first()
    val text: String get() = first.text
    val warning: String get() = first.warning
    val warningEnabled: Boolean get() = first.warningEnabled
    val savedText: String get() = savedEntries.firstOrNull()?.text.orEmpty()
    val savedWarning: String get() = savedEntries.firstOrNull()?.warning.orEmpty()
    val hasTargets: Boolean get() = replyTo != null || quoteOf != null

    /** True when any entry differs from the saved snapshot, in text, warning, media, or order. */
    val entriesChanged: Boolean get() = entries.map(ComposerEntryState::content) != savedEntries

    /** True when any entry carries text, a warning, or an image. */
    val hasContent: Boolean
        get() = entries.any { it.text.isNotBlank() || it.effectiveWarning.isNotBlank() || it.media.isNotEmpty() || it.poll != null }

    fun entry(id: String): ComposerEntryState? = entries.firstOrNull { it.id == id }

    /** Replaces one entry. An unknown id changes nothing. */
    fun withEntry(id: String, transform: (ComposerEntryState) -> ComposerEntryState): ComposerEditorState =
        copy(entries = entries.map { if (it.id == id) transform(it) else it })

    companion object {
        /** Restores shell-surviving fields across process recreation. */
        val Saver: Saver<ComposerEditorState, Any> get() = ComposerEditorStateSaver
    }
}
