package me.foxtails.palustris.ui.posts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost

internal data class PendingRepostConfirmation(
    val fetchedBy: AccountId,
    val postId: EntityId,
    val sessionRevision: Long,
    val anchorBounds: Rect,
    val selected: Boolean,
)

internal class PostRepostConfirmationState {
    var pending by mutableStateOf<PendingRepostConfirmation?>(null)
        private set

    fun request(post: OwnedPost, anchorBounds: Rect) {
        pending = PendingRepostConfirmation(
            fetchedBy = post.fetchedBy,
            postId = post.post.id,
            sessionRevision = post.sessionRevision,
            anchorBounds = anchorBounds,
            selected = post.post.reposted,
        )
    }

    fun dismiss() {
        pending = null
    }

    fun reconcile(post: OwnedPost) {
        val current = pending ?: return
        if (current.fetchedBy == post.fetchedBy && current.postId == post.post.id &&
            (current.sessionRevision != post.sessionRevision || current.selected != post.post.reposted)
        ) {
            pending = null
        }
    }

    fun confirm(post: OwnedPost, onReshare: (OwnedPost) -> Unit) {
        val current = pending ?: return
        if (current.isCurrentFor(post) && current.selected == post.post.reposted) onReshare(post)
        pending = null
    }

    /** Routes the Quote choice to the composer only while the choice still names this post and session. */
    fun quote(post: OwnedPost, onQuote: (OwnedPost) -> Unit) {
        val current = pending ?: return
        if (current.isCurrentFor(post)) onQuote(post)
        pending = null
    }

    private fun PendingRepostConfirmation.isCurrentFor(post: OwnedPost): Boolean =
        fetchedBy == post.fetchedBy && postId == post.post.id && sessionRevision == post.sessionRevision
}
