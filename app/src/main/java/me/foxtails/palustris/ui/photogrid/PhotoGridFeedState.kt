package me.foxtails.palustris.ui.photogrid

import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Timeline

sealed interface PhotoGridFeed {
    /** A built-in timeline, list, hashtag or antenna from the shared chip catalog. */
    data class Source(val source: FeedSource) : PhotoGridFeed

    /** The search results carried from Search. It is never saved and the grid never fetches it. */
    data class Search(val query: String) : PhotoGridFeed
}

data class PhotoGridFeedState(
    val selectedFeed: PhotoGridFeed = PhotoGridFeed.Source(FeedSource.Timeline(Timeline.Home)),
    val availableTimelines: List<Timeline> = listOf(Timeline.Home),
    /** The query of the search results carried from Search, or null when none are open. */
    val carriedSearch: String? = null,
    val posts: List<OwnedPost> = emptyList(),
    /** Extra hashtags the selected hashtag feed applied, best first. Paging reuses them. Never saved. */
    val combinedTags: List<String> = emptyList(),
    val initialLoadComplete: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null,
    val needsSignIn: Boolean = false,
)
