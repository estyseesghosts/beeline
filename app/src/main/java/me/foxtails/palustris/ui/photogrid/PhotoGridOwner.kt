package me.foxtails.palustris.ui.photogrid

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.adjustedBy
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.domain.hashtags.HashtagExpansionInput

/** Owns Photo Grid state and requests for one connected account and session revision. */
internal class PhotoGridOwner(
    val accountId: AccountId,
    private val source: SocialSource,
    val sessionRevision: Long,
    scope: CoroutineScope,
    controller: PhotoGridController,
) {
    constructor(
        accountId: AccountId,
        source: SocialSource,
        sessionRevision: Long,
        scope: CoroutineScope,
        preferencesRepository: me.foxtails.palustris.domain.PhotoGridPreferencesRepository,
        hashtagInput: () -> HashtagExpansionInput = { HashtagExpansionInput.Disabled },
        applyFavouritePreference: (Post) -> Post,
    ) : this(
        accountId,
        source,
        sessionRevision,
        scope,
        PhotoGridController(
            accountId = accountId,
            source = source,
            sessionRevision = sessionRevision,
            scope = scope,
            preferencesRepository = preferencesRepository,
            applyFavouritePreference = applyFavouritePreference,
            hashtagInput = hashtagInput,
        ),
    )

    private var released = false

    val state: StateFlow<PhotoGridFeedState> = controller.state

    private val delegate = controller

    fun ensureLoaded() = delegate.ensureLoaded()
    fun selectFeed(feed: PhotoGridFeed) = delegate.selectFeed(feed)
    fun refresh() = delegate.refresh()
    fun loadMore() = delegate.loadMore()
    fun addHashtag(value: String, onSuccess: () -> Unit = {}) = delegate.addHashtag(value, onSuccess)
    fun clearPreferenceError() = delegate.clearPreferenceError()

    fun updateFavouritePreference(transform: (Post) -> Post) {
        if (!released) delegate.updatePosts(transform)
    }

    fun applyExternalPost(updated: OwnedPost) {
        if (!accepts(updated)) return
        delegate.updateExternalPost(updated.effectiveTargetId(), updated.post)
    }

    fun applyDeletedPost(deleted: OwnedPost) {
        if (accepts(deleted)) delegate.removeDeletedPost(deleted)
    }

    fun applyPublishedPost(request: CreatePostRequest) {
        if (released) return
        request.replyTo?.let { target ->
            delegate.updatePost(target) { post ->
                post.copy(interactionCounts = post.interactionCounts.copy(
                    replyCount = post.interactionCounts.replyCount.adjustedBy(1),
                ))
            }
        }
        request.quoteOf?.let { target ->
            delegate.updatePost(target) { post ->
                post.copy(interactionCounts = post.interactionCounts.copy(
                    quoteRepostCount = post.interactionCounts.quoteRepostCount.adjustedBy(1),
                ))
            }
        }
    }

    fun release() {
        if (released) return
        released = true
        delegate.stop()
    }

    private fun accepts(updated: OwnedPost): Boolean =
        !released && updated.fetchedBy == accountId && updated.sessionRevision == sessionRevision
}
