package me.foxtails.palustris.ui.composer

import androidx.compose.runtime.saveable.Saver
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.PostDraftQuotePreview

/**
 * Saveable composer editor fields for one connected account.
 *
 * The composer owner holds these values. The navigation shell reads them but does not own them. The
 * saved snapshot drives the dirty check. Reply and quote target identities are stored with the
 * account and session revision they were chosen under ([boundAccount], [boundRevision]), so a
 * restored editor can verify them before it is used. The draft list is not stored here. Custom
 * emoji maps in [targetPreview] are not restored; the preview shows plain text.
 */
data class ComposerEditorState(
    val draftId: String? = null,
    val text: String = "",
    val savedText: String = "",
    val warning: String = "",
    val savedWarning: String = "",
    val warningEnabled: Boolean = false,
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
) {
    val hasTargets: Boolean get() = replyTo != null || quoteOf != null

    companion object {
        /** Restores shell-surviving fields across process recreation. */
        val Saver: Saver<ComposerEditorState, Any> get() = ComposerEditorStateSaver
    }
}
