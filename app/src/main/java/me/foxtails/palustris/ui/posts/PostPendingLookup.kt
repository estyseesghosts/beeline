package me.foxtails.palustris.ui.posts

import androidx.compose.runtime.staticCompositionLocalOf
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.effectiveTargetId

/** Families a visible control stands for. Favorite and reaction share one control, so both count as pending for it. */
internal object PostControlFamilies {
    val Repost = setOf(PostActionFamily.Reshare)
    val Favourite = setOf(PostActionFamily.Favorite, PostActionFamily.FavoriteReaction, PostActionFamily.Reaction)
    val Reaction = setOf(PostActionFamily.Reaction, PostActionFamily.FavoriteReaction)
    val Bookmark = setOf(PostActionFamily.Bookmark)
}

/**
 * Tells presentation whether an action is still in flight for a post, so an optimistic state is not shown as confirmed.
 * The session host supplies the answer from its [PostInteractionExecutionAuthority]; the default reports nothing pending.
 */
internal fun interface PostPendingLookup {
    fun isPending(ownedPost: OwnedPost, families: Set<PostActionFamily>): Boolean
}

internal val LocalPostPendingLookup = staticCompositionLocalOf { PostPendingLookup { _, _ -> false } }

internal fun PostInteractionExecutionAuthority.pendingLookup(): PostPendingLookup = PostPendingLookup { ownedPost, families ->
    isPending(ownedPost.fetchedBy, ownedPost.sessionRevision, ownedPost.effectiveTargetId(), families)
}
