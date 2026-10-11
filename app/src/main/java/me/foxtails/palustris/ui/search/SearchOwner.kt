package me.foxtails.palustris.ui.search

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
import me.foxtails.palustris.ui.links.PostLinkResolver

/** Owns Search and Trends state and requests for one connected account and session revision. */
internal class SearchOwner(
    val accountId: AccountId,
    private val source: SocialSource,
    val sessionRevision: Long,
    scope: CoroutineScope,
    applyFavouritePreference: (Post) -> Post,
    hashtagInput: () -> HashtagExpansionInput = { HashtagExpansionInput.Disabled },
    linkResolver: () -> PostLinkResolver? = { null },
) {
    private val controller = SearchController(
        source = source,
        scope = scope,
        applyFavouritePreference = applyFavouritePreference,
        onStateChanged = {},
        hashtagInput = hashtagInput,
        linkResolver = linkResolver,
    )

    private val trends = SearchTrendsController(
        source = source,
        scope = scope,
        applyFavouritePreference = applyFavouritePreference,
    )

    val state: StateFlow<SearchState> = controller.state

    val trendsState: StateFlow<SearchTrendsState> = trends.state

    fun search(query: String) = controller.search(query)

    fun searchWithoutRelated(query: String) = controller.searchWithoutRelated(query)

    fun suggest(text: String) = controller.suggest(text)

    fun loadMore() = controller.loadMore()

    fun consumeTarget() = controller.consumeTarget()

    fun loadTrends() = trends.load()

    fun loadMoreTrends() = trends.loadMore()

    fun refreshTrends() = trends.refresh()

    fun applyExternalPost(updated: OwnedPost) {
        if (updated.fetchedBy != accountId || updated.sessionRevision != sessionRevision) return
        controller.updateExternalPost(updated.effectiveTargetId(), updated.post)
        trends.updateExternalPost(updated.effectiveTargetId(), updated.post)
    }

    fun applyDeletedPost(deleted: OwnedPost) {
        if (deleted.fetchedBy != accountId || deleted.sessionRevision != sessionRevision) return
        controller.removeDeletedPost(deleted.post)
        trends.removeDeletedPost(deleted.post)
    }

    fun applyPublishedPost(request: CreatePostRequest) {
        controller.applyPublishedPost(request)
        request.replyTo?.let { target ->
            trends.updatePost(target) { post ->
                post.copy(interactionCounts = post.interactionCounts.copy(replyCount = post.interactionCounts.replyCount.adjustedBy(1)))
            }
        }
        request.quoteOf?.let { target ->
            trends.updatePost(target) { post ->
                post.copy(interactionCounts = post.interactionCounts.copy(quoteRepostCount = post.interactionCounts.quoteRepostCount.adjustedBy(1)))
            }
        }
    }

    fun updateFavouritePreference(transform: (Post) -> Post) {
        controller.updatePosts(transform)
        trends.updatePosts(transform)
    }

    fun release() {
        controller.stop()
        trends.stop()
    }
}
