package me.foxtails.palustris.ui.posts

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.effectiveTargetId

/** Reads the edits made in this session without holding a source or a scope. */
interface PostEditPresentation {
    /** [ownedPost] with the session's edit applied, or [ownedPost] itself when it was not edited here. */
    fun applied(ownedPost: OwnedPost): OwnedPost
}

/** Starts editing one of the account's own posts. Null where no composer is wired, such as a preview. */
internal val LocalPostEditRequest = staticCompositionLocalOf<((OwnedPost) -> Unit)?> { null }

internal val LocalPostEdits = staticCompositionLocalOf<PostEditPresentation?> { null }

/**
 * Holds the posts the account edited in this connected session and releases them with it. A list
 * that loaded the post before the edit still shows the old text, so every row reads through this
 * owner and shows the edit at once, in every surface. A copy of the post that was edited later
 * than the stored edit wins, so a refresh from the server is never hidden.
 */
class PostEditOwner(
    private val accountId: AccountId,
    private val sessionRevision: Long,
) : PostEditPresentation {
    private val edited = mutableStateMapOf<EntityId, Post>()
    private var retired = false

    /** Stores the server's answer to an edit of [target]. */
    fun record(target: EntityId, post: Post) {
        if (!retired) edited[target] = post
    }

    override fun applied(ownedPost: OwnedPost): OwnedPost {
        if (ownedPost.fetchedBy != accountId || ownedPost.sessionRevision != sessionRevision) return ownedPost
        val stored = edited[ownedPost.effectiveTargetId()] ?: return ownedPost
        return ownedPost.copy(post = mergedEdit(ownedPost.post, stored))
    }

    fun retire() {
        retired = true
        edited.clear()
    }
}

/**
 * The post with the edited parts of [stored] laid over it. The reaction, repost, and count fields stay as
 * [base] has them, because they change independently of the text.
 */
internal fun mergedEdit(base: Post, stored: Post): Post {
    val storedAt = stored.editedAtEpochMillis ?: return base
    if (storedAt <= (base.editedAtEpochMillis ?: 0L)) return base
    return base.copy(
        text = stored.text,
        mentions = stored.mentions,
        contentWarning = stored.contentWarning,
        attachments = stored.attachments,
        poll = stored.poll ?: base.poll,
        emoji = stored.emoji,
        editedAtEpochMillis = stored.editedAtEpochMillis,
    )
}
