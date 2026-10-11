package me.foxtails.palustris.ui.photogrid

import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Timeline

sealed interface PhotoGridFeed {
    data class TimelineFeed(val timeline: Timeline) : PhotoGridFeed
    data class Hashtag(val tag: String) : PhotoGridFeed

    /** The search results carried from Search. It is never saved and the grid never fetches it. */
    data class Search(val query: String) : PhotoGridFeed
}

data class PhotoGridFeedState(
    val selectedFeed: PhotoGridFeed = PhotoGridFeed.TimelineFeed(Timeline.Home),
    val availableTimelines: List<Timeline> = listOf(Timeline.Home),
    val savedHashtags: List<String> = emptyList(),
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
    val preferenceLoading: Boolean = true,
    val preferenceSaving: Boolean = false,
    val preferenceError: String? = null,
)
