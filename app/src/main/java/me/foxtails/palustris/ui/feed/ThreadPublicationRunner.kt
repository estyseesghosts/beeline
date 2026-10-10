package me.foxtails.palustris.ui.feed

import kotlinx.coroutines.CancellationException
import me.foxtails.palustris.data.auth.DraftStore
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PreparedThreadImage
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.ThreadImageLimits
import me.foxtails.palustris.domain.ThreadImagePreparer
import me.foxtails.palustris.domain.ThreadPublication
import me.foxtails.palustris.domain.ThreadPublicationMedia
import me.foxtails.palustris.domain.ThreadPublishFailure
import me.foxtails.palustris.domain.ThreadPublishListener
import me.foxtails.palustris.domain.ThreadPublishResult
import me.foxtails.palustris.domain.ThreadPublisher
import me.foxtails.palustris.domain.toPostDraft
import me.foxtails.palustris.ui.UiStrings

/**
 * Runs one thread publication for the feed.
 *
 * Responsibility: ordered posting with its draft progress writes, unused-upload cleanup through
 * the publisher, and listener callbacks. Lifetime: one publish job. State writes go through
 * [Events] so the owning ViewModel keeps its own state authority.
 */
class ThreadPublicationRunner(
    private val source: SocialSource,
    private val preparer: ThreadImagePreparer,
    private val draftStore: DraftStore,
    private val uiStrings: UiStrings = UiStrings.Default,
) {
    /** Feed state writes for one publish job. */
    interface Events {
        val isStopped: Boolean
        fun onProgress(posted: Int, total: Int)
        fun onSuccess(owned: List<OwnedPost>, requests: List<CreatePostRequest>)
        fun onFailure(failure: ThreadPublishFailure, message: String)
        fun onUnexpected(error: Exception)
        fun onRefresh()
    }

    suspend fun run(
        publication: ThreadPublication,
        accountId: AccountId,
        sessionRevision: Long,
        listener: ThreadPublishListener,
        events: Events,
    ) {
        val total = publication.entries.size
        try {
            val publisher = ThreadPublisher(source, preparer, source.capabilities.posting, accountId)
            when (val result = publisher.publish(publication) { posted, remaining ->
                if (remaining != null) saveRemaining(remaining, accountId)
                events.onProgress(posted.size, total)
                listener.onProgress(posted.size, total)
            }) {
                is ThreadPublishResult.Success -> {
                    if (events.isStopped) return
                    runCatching { draftStore.delete(accountId, publication.draftId) }
                    val owned = result.created.map { OwnedPost(accountId, it, sessionRevision) }
                    events.onSuccess(owned, result.requests)
                    listener.onAccepted(owned, result.requests)
                    events.onRefresh()
                }
                is ThreadPublishResult.Failed -> {
                    if (events.isStopped) return
                    saveRemaining(result.remaining, accountId)
                    val detail = (result.error as? Exception)?.let(uiStrings::sourceError)
                        ?: result.error.message.orEmpty()
                    val failure = ThreadPublishFailure(
                        failedEntryId = result.failedEntryId,
                        failedIndex = result.failedIndex,
                        totalEntries = result.totalEntries,
                        error = result.error,
                        remaining = result.remaining,
                        posted = result.posted,
                    )
                    events.onFailure(failure, uiStrings.composerEntryPublishFailed(result.failedIndex + 1, result.totalEntries, detail))
                    listener.onError(failure)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (events.isStopped) return
            events.onUnexpected(e)
        }
    }

    /** Writes the entries that remain after each posted entry. Best effort, never fails the job. */
    private suspend fun saveRemaining(remaining: ThreadPublication, accountId: AccountId) {
        runCatching { draftStore.save(remaining.toPostDraft(accountId)) }
    }
}

/** Fails image preparation. Runs only when no preparer was injected and a publish carries media. */
internal object EmptyThreadPreparer : ThreadImagePreparer {
    override suspend fun prepare(
        accountId: AccountId?,
        draftId: String,
        media: ThreadPublicationMedia,
        compress: Boolean,
        limits: ThreadImageLimits,
    ): PreparedThreadImage = throw UnsupportedOperationException("No thread image preparer")
}
