package me.foxtails.palustris.domain

import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Server limits for one image. A null limit means the server did not report one. */
data class ThreadImageLimits(
    val maxBytes: Long? = null,
    val maxPixels: Long? = null,
    val acceptedTypes: Set<String>? = null,
    /** Largest accepted video file. */
    val maxVideoBytes: Long? = null,
)

/** One image ready to upload. The publisher releases it after the upload. */
interface PreparedThreadImage {
    val fileName: String
    val mimeType: String
    fun open(): InputStream
    fun release()
}

/** True for a draft attachment that is a video, which takes the video preparation path. */
fun ThreadPublicationMedia.isVideo(): Boolean = mimeType.startsWith("video/")

/**
 * Prepares a draft image for upload.
 *
 * Responsibility: decrypt the draft copy, compress it when asked, and fit it to the server
 * limits. A video is converted instead, and [onProgress] receives that conversion's progress from
 * 0 to 1. Lifetime: one call for each attachment. It keeps no state between calls.
 */
interface ThreadImagePreparer {
    suspend fun prepare(
        accountId: AccountId?,
        draftId: String,
        media: ThreadPublicationMedia,
        compress: Boolean,
        limits: ThreadImageLimits,
        onProgress: (Float) -> Unit = {},
    ): PreparedThreadImage

    /**
     * Called when the server refused [prepared]. Returns true when preparing the same attachment again
     * would produce a different file, so the publisher should retry once. A WebM that a server
     * refuses is recorded here and the next preparation uses MP4.
     */
    fun uploadRejected(accountId: AccountId?, prepared: PreparedThreadImage, error: Exception): Boolean = false
}

/** The outcome of one thread publish job. */
sealed interface ThreadPublishResult {
    data class Success(
        val created: List<Post>,
        val requests: List<CreatePostRequest>,
    ) : ThreadPublishResult

    data class Failed(
        val posted: List<Post>,
        val postedRequests: List<CreatePostRequest>,
        val failedEntryId: String,
        val failedIndex: Int,
        val totalEntries: Int,
        val error: Throwable,
        val remaining: ThreadPublication,
    ) : ThreadPublishResult
}

/**
 * Publishes a thread entry by entry.
 *
 * Responsibility: image preparation and upload, ordered post creation, idempotent retries, and
 * best-effort cleanup of uploads that no post uses. Lifetime: one publish job. It keeps the
 * created posts only until it returns.
 *
 * Entry 1 uses the publication reply target and quote. Each later entry replies to the post
 * created before it and carries no quote. Every entry uses the first entry's audience. Each
 * request carries `<draftId>-<entryId>` as its idempotency key, so a retry of the remaining
 * entries never duplicates a posted one. After each created post [onEntryPosted] runs with the
 * posts so far and the publication that remains, or null when nothing remains. An upload or
 * preparation failure stops before `create` for that entry. A failure returns the entries that
 * remain with their reply target moved to the last created post. Cancellation stops the job and
 * is rethrown, never reported as a failure.
 */
class ThreadPublisher(
    private val source: SocialSource,
    private val preparer: ThreadImagePreparer,
    private val posting: PostingCapabilities = PostingCapabilities(),
    private val accountId: AccountId? = null,
) {
    suspend fun publish(
        publication: ThreadPublication,
        onMediaProgress: (Float?) -> Unit = {},
        onEntryPosted: suspend (posted: List<Post>, remaining: ThreadPublication?) -> Unit = { _, _ -> },
    ): ThreadPublishResult {
        val total = publication.entries.size
        val effectiveCompress = publication.compress && posting.clientCompression
        val limits = ThreadImageLimits(
            maxBytes = posting.maxImageBytes,
            maxPixels = posting.maxImagePixels,
            acceptedTypes = posting.uploadTypes,
            maxVideoBytes = posting.maxVideoBytes,
        )
        val created = mutableListOf<Post>()
        val requests = mutableListOf<CreatePostRequest>()
        var previousReply = publication.replyTo
        for (index in publication.entries.indices) {
            val entry = publication.entries[index]
            currentCoroutineContext().ensureActive()
            val uploaded = mutableListOf<Attachment>()
            val attachments = mutableListOf<Attachment>()
            try {
                for (image in entry.media) {
                    currentCoroutineContext().ensureActive()
                    val attachment = uploadOne(publication, image, effectiveCompress, limits, onMediaProgress)
                    attachments += attachment
                    uploaded += attachment
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                cleanup(uploaded)
                return failed(publication, created, requests, index, error)
            }
            val request = CreatePostRequest(
                text = entry.text,
                audience = publication.audience,
                contentWarning = entry.contentWarning,
                replyTo = previousReply,
                attachments = attachments,
                quoteOf = if (index == 0) publication.quoteOf else null,
                idempotencyKey = "${publication.draftId}-${entry.id}",
            )
            val post = try {
                source.create(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                cleanup(uploaded)
                return failed(publication, created, requests, index, error)
            }
            created += post
            requests += request
            previousReply = post.id
            val remaining = if (index + 1 < total) {
                publication.copy(
                    entries = publication.entries.drop(index + 1),
                    replyTo = post.id,
                    quoteOf = null,
                )
            } else {
                null
            }
            onEntryPosted(created.toList(), remaining)
        }
        return ThreadPublishResult.Success(created = created.toList(), requests = requests.toList())
    }

    /**
     * Prepares and uploads one attachment. When the preparer says the server's refusal changes the
     * outcome (a WebM that the server does not accept), it prepares again and uploads once more.
     */
    private suspend fun uploadOne(
        publication: ThreadPublication,
        media: ThreadPublicationMedia,
        compress: Boolean,
        limits: ThreadImageLimits,
        onMediaProgress: (Float?) -> Unit,
    ): Attachment {
        val owner = publication.accountId ?: accountId
        var retried = false
        while (true) {
            val prepared = try {
                preparer.prepare(owner, publication.draftId, media, compress, limits) { onMediaProgress(it) }
            } finally {
                onMediaProgress(null)
            }
            try {
                return source.uploadMedia(
                    MediaUploadRequest(
                        open = prepared::open,
                        mimeType = prepared.mimeType,
                        fileName = prepared.fileName,
                        description = media.description,
                        sensitive = media.sensitive,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (retried || !preparer.uploadRejected(owner, prepared, error)) throw error
                retried = true
            } finally {
                prepared.release()
            }
        }
    }

    private fun failed(
        publication: ThreadPublication,
        created: List<Post>,
        requests: List<CreatePostRequest>,
        index: Int,
        error: Exception,
    ): ThreadPublishResult.Failed = ThreadPublishResult.Failed(
        posted = created.toList(),
        postedRequests = requests.toList(),
        failedEntryId = publication.entries[index].id,
        failedIndex = index,
        totalEntries = publication.entries.size,
        error = error,
        remaining = publication.copy(
            entries = publication.entries.drop(index),
            replyTo = created.lastOrNull()?.id ?: publication.replyTo,
            quoteOf = if (index == 0) publication.quoteOf else null,
        ),
    )

    /** Deletes uploads that no post uses. Every delete is best effort and never fails the job. */
    private suspend fun cleanup(uploaded: List<Attachment>) {
        uploaded.mapNotNull(Attachment::id).forEach { id ->
            runCatching { source.deleteUpload(id) }
        }
    }
}
