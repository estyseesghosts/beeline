package me.foxtails.palustris.ui.composer

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostDraftQuotePreview

/** The plain-text preview that a draft keeps of a reply or quote target. */
internal fun OwnedPost.toQuotePreview() = PostDraftQuotePreview(
    authorDisplayName = post.author.displayName,
    authorHandle = post.author.handle,
    text = post.text,
    url = post.url,
)

/**
 * Rebuilds a plain-text target post from a saved preview for [owner]. Returns null when the
 * target belongs to another server, so a restored preview never links to the wrong account.
 */
internal fun previewTarget(
    owner: Account?,
    targetId: EntityId?,
    preview: PostDraftQuotePreview?,
    fallbackLabel: String,
): OwnedPost? {
    if (owner == null || targetId == null || preview == null || targetId.connection != owner.id.connection.origin) return null
    val author = Account(
        id = AccountId(Connection(targetId.connection, owner.id.connection.protocol), "draft-quote-author"),
        displayName = preview.authorDisplayName.ifBlank { preview.authorHandle.ifBlank { fallbackLabel } },
        handle = preview.authorHandle.ifBlank { fallbackLabel },
    )
    return OwnedPost(
        owner.id,
        Post(
            id = targetId,
            author = author,
            text = preview.text,
            publishedAtEpochMillis = 0L,
            audience = Audience.Public,
            url = preview.url,
        ),
    )
}
