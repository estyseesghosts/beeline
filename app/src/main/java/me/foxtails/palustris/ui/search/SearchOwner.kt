package me.foxtails.palustris.ui.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.domain.hashtags.HashtagExpansionInput
import me.foxtails.palustris.ui.links.PostLinkResolver
import me.foxtails.palustris.ui.shell.SearchContract

/** Owns Search and Trends state and requests for one connected account and session revision. */
internal class SearchOwner(
    val accountId: AccountId,
    private val source: SocialSource,
    val sessionRevision: Long,
    scope: CoroutineScope,
    applyFavouritePreference: (Post) -> Post,
    hashtagInput: () -> HashtagExpansionInput = { HashtagExpansionInput.Disabled },
    linkResolver: () -> PostLinkResolver? = { null },
) : SearchContract.Actions {
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

    override fun search(query: String) = controller.search(query)

    override fun searchWithoutRelated(query: String) = controller.searchWithoutRelated(query)

    override fun suggest(text: String) = controller.suggest(text)

    override fun loadMore() = controller.loadMore()

    override fun consumeTarget() = controller.consumeTarget()

    override fun loadTrends() = trends.load()

    override fun loadMoreTrends() = trends.loadMore()

    override fun refreshTrends() = trends.refresh()

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
        request.countChanges().forEach { (id, change) -> trends.updatePost(id, change) }
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
