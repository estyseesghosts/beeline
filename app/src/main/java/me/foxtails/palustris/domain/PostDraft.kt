package me.foxtails.palustris.domain

import java.time.Instant
import java.util.UUID

data class PostDraftQuotePreview(
    val authorDisplayName: String,
    val authorHandle: String,
    val text: String,
    val url: String? = null,
    val authorEmoji: Map<String, CustomEmoji> = emptyMap(),
    val postEmoji: Map<String, CustomEmoji> = emptyMap(),
)

/**
 * One image kept with a draft. The bytes live in the draft media store under [id]; the draft
 * records only what the editor and the publisher need to know without opening the file.
 */
data class DraftMedia(
    val id: String = UUID.randomUUID().toString(),
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null,
    val byteSize: Long = 0L,
    val altText: String? = null,
)

/** A post of a thread after the first one. The first post lives in the [PostDraft] fields. */
data class PostDraftEntry(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val contentWarning: String? = null,
    val media: List<DraftMedia> = emptyList(),
)

/**
 * A saved composer state. The top-level text, warning, and [media] belong to the first post of a
 * thread. [followUps] hold posts 2 and later in publish order. A draft saved before threads
 * existed has no follow-ups and no media, and loads as one post.
 */
data class PostDraft(
    val id: String = UUID.randomUUID().toString(),
    val accountId: AccountId?,
    val text: String,
    val audience: Audience = Audience.Public,
    val contentWarning: String? = null,
    val replyTo: EntityId? = null,
    val quoteOf: EntityId? = null,
    val attachments: List<Attachment> = emptyList(),
    val poll: PollRequest? = null,
    val updatedAt: Long = Instant.now().toEpochMilli(),
    val quotePreview: PostDraftQuotePreview? = null,
    val media: List<DraftMedia> = emptyList(),
    val followUps: List<PostDraftEntry> = emptyList(),
) {
    /** Every image the draft keeps, first post first. */
    val allMedia: List<DraftMedia> get() = media + followUps.flatMap(PostDraftEntry::media)
}
