package me.foxtails.palustris.domain

/**
 * How a confirmed deletion changes a loaded collection. A row goes when it is the deleted post or
 * wraps it, as a repost does. The deleted post's parent loses one reply from its count.
 */
fun Post.isRemovedBy(deleted: Post): Boolean {
    val target = deleted.effectiveTargetId()
    return id == target || actionTargetId == target
}

fun Post.afterReplyDeleted(deleted: Post): Post {
    val parent = deleted.replyTo ?: return this
    if (id != parent && actionTargetId != parent) return this
    return copy(interactionCounts = interactionCounts.copy(replyCount = interactionCounts.replyCount.adjustedBy(-1)))
}

fun List<Post>.afterDeletion(deleted: Post): List<Post> =
    filterNot { it.isRemovedBy(deleted) }.map { it.afterReplyDeleted(deleted) }

fun List<OwnedPost>.afterOwnedDeletion(deleted: OwnedPost): List<OwnedPost> =
    filterNot { it.fetchedBy == deleted.fetchedBy && it.post.isRemovedBy(deleted.post) }
        .map { owned ->
            if (owned.fetchedBy == deleted.fetchedBy) owned.copy(post = owned.post.afterReplyDeleted(deleted.post)) else owned
        }
