package me.foxtails.palustris.ui.search

import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.adjustedBy
import me.foxtails.palustris.domain.mergeExternalActionFields

/** Applies [transform] to the post [id] names, or whose action target [id] names. */
internal fun List<Post>.withUpdated(id: EntityId, transform: (Post) -> Post): List<Post> =
    map { post -> if (post.id == id || post.actionTargetId == id) transform(post) else post }

/** Merges the action fields of [incoming] into the post that [target] names. */
internal fun List<Post>.withExternal(target: EntityId, incoming: Post): List<Post> =
    withUpdated(target) { it.mergeExternalActionFields(incoming) }

/**
 * The count changes that a published reply or quote causes: the parent gains a reply, and the quoted
 * post gains a quote. Each pair is a post ID and the change to apply to that post.
 */
internal fun CreatePostRequest.countChanges(): List<Pair<EntityId, (Post) -> Post>> = buildList {
    replyTo?.let { target ->
        add(
            target to { post: Post ->
                post.copy(interactionCounts = post.interactionCounts.copy(replyCount = post.interactionCounts.replyCount.adjustedBy(1)))
            },
        )
    }
    quoteOf?.let { target ->
        add(
            target to { post: Post ->
                post.copy(
                    interactionCounts = post.interactionCounts.copy(
                        quoteRepostCount = post.interactionCounts.quoteRepostCount.adjustedBy(1),
                    ),
                )
            },
        )
    }
}
