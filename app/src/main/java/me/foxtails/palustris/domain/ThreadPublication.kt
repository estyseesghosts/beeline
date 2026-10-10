package me.foxtails.palustris.domain

/**
 * One image of a thread entry to publish.
 *
 * The bytes stay in the draft media store until the publisher prepares them. [description] carries
 * the alt text. [sensitive] is true when the entry has a content warning, so a Misskey entry with
 * a warning uploads its files as sensitive. Width, height, and byte size round-trip back into the
 * draft when a publish stops early.
 */
data class ThreadPublicationMedia(
    val id: String,
    val mimeType: String,
    val fileName: String,
    val description: String? = null,
    val sensitive: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    val byteSize: Long = 0L,
)

/** One post of a thread in publish order. */
data class ThreadPublicationEntry(
    val id: String,
    val text: String = "",
    val contentWarning: String? = null,
    val media: List<ThreadPublicationMedia> = emptyList(),
)

/**
 * A thread to publish.
 *
 * [entries] holds the posts in publish order and is never empty. The [audience] belongs to the
 * thread and applies to every entry. [replyTo] and [quoteOf] belong to the first entry only.
 * [compress] is the resolved choice: true compresses images before upload when the server leaves
 * compression to the client. [accountId] owns the draft media files the publisher prepares.
 */
data class ThreadPublication(
    val draftId: String,
    val entries: List<ThreadPublicationEntry>,
    val audience: Audience = Audience.Public,
    val replyTo: EntityId? = null,
    val quoteOf: EntityId? = null,
    val compress: Boolean = true,
    val accountId: AccountId? = null,
) {
    init {
        require(entries.isNotEmpty()) { "A thread publication holds at least one entry." }
    }
}

/** What a failed publish keeps for the retry. */
data class ThreadPublishFailure(
    val failedEntryId: String,
    val failedIndex: Int,
    val totalEntries: Int,
    val error: Throwable,
    val remaining: ThreadPublication,
    val posted: List<Post> = emptyList(),
)

/** The draft media record for a publication image. Size and dimensions round-trip unchanged. */
fun ThreadPublicationMedia.toDraftMedia(): DraftMedia = DraftMedia(
    id = id,
    mimeType = mimeType,
    width = width,
    height = height,
    byteSize = byteSize,
    altText = description,
)

/**
 * The draft that keeps the entries a publish has not posted yet. The first remaining entry maps
 * to the draft top level and later ones to follow-ups, so the existing draft storage writes it
 * without a migration.
 */
fun ThreadPublication.toPostDraft(accountId: AccountId?): PostDraft {
    val first = entries.first()
    return PostDraft(
        id = draftId,
        accountId = accountId,
        text = first.text,
        audience = audience,
        contentWarning = first.contentWarning,
        replyTo = replyTo,
        quoteOf = quoteOf,
        media = first.media.map(ThreadPublicationMedia::toDraftMedia),
        followUps = entries.drop(1).map {
            PostDraftEntry(
                id = it.id,
                text = it.text,
                contentWarning = it.contentWarning,
                media = it.media.map(ThreadPublicationMedia::toDraftMedia),
            )
        },
    )
}
