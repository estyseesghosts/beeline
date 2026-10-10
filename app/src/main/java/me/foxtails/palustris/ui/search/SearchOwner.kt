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
import me.foxtails.palustris.domain.hashtags.HashtagSuggestionService

/** Owns Search state and requests for one connected account and session revision. */
internal class SearchOwner(
    val accountId: AccountId,
    private val source: SocialSource,
    val sessionRevision: Long,
    scope: CoroutineScope,
    applyFavouritePreference: (Post) -> Post,
    hashtagInput: () -> HashtagExpansionInput = { HashtagExpansionInput.Disabled },
) {
    private val controller = SearchController(
        source = source,
        scope = scope,
        applyFavouritePreference = applyFavouritePreference,
        onStateChanged = {},
        hashtagInput = hashtagInput,
    )

    private val explore = SearchExploreController(
        source = source,
        scope = scope,
        suggestionService = HashtagSuggestionService(
            catalog = hashtagInput().catalog,
            accountKey = "${accountId.connection.origin}/${accountId.localId}",
            policy = { hashtagInput().policy },
            fetchServer = source::suggestHashtags,
        ),
    )

    val state: StateFlow<AccountSearchState> = controller.state

    val exploreState: StateFlow<SearchExploreState> = explore.state

    fun search(query: String) = controller.search(query)

    fun searchWithoutRelated(query: String) = controller.searchWithoutRelated(query)

    fun loadMore() = controller.loadMore()

    fun loadTrending() = explore.loadTrending()

    fun suggestHashtags(text: String) = explore.suggest(text)

    fun applyExternalPost(updated: OwnedPost) {
        if (updated.fetchedBy != accountId || updated.sessionRevision != sessionRevision) return
        controller.updateExternalPost(updated.effectiveTargetId(), updated.post)
    }

    fun applyPublishedPost(request: CreatePostRequest) = controller.applyPublishedPost(request)

    fun updateFavouritePreference(transform: (Post) -> Post) = controller.updatePosts(transform)

    fun release() {
        controller.stop()
        explore.stop()
    }
}
