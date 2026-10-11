package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.domain.FavouriteArtworkStyle
import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Timeline

/**
 * Home timeline rows and paging state.
 *
 * Search results and Photo Grid state are deliberately not part of this contract. [Empty] is an
 * inert preview value.
 */
data class HomeFeedUiState(
    val favouriteArtworkStyle: FavouriteArtworkStyle = FavouriteArtworkStyle.Heart,
    val ownedPosts: List<OwnedPost> = emptyList(),
    val posts: List<Post> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null,
    val needsSignIn: Boolean = false,
    val selectedTimeline: Timeline = Timeline.Home,
    /** The feed Home shows. It differs from [selectedTimeline] for a list, hashtag or antenna. */
    val selectedFeed: FeedSource = FeedSource.Timeline(Timeline.Home),
    val availableTimelines: Set<Timeline> = setOf(Timeline.Home),
    /** Home request epoch from the feed owner. Demand resets when it changes. */
    val requestEpoch: Long = 0L,
)

/** Home timeline presentation. The Home owner drives timeline selection and paging. */
data class HomeContract(
    val state: HomeFeedUiState,
    val actions: Actions,
    /** The shared chip catalog: lists, hashtags and antennas. The Photo Grid reads the same one. */
    val chips: FeedChipsContract = FeedChipsContract.Empty,
) {
    interface Actions {
        fun refresh(feed: FeedSource)
        fun loadMore(feed: FeedSource)
    }

    companion object {
        val Empty = HomeContract(HomeFeedUiState(), HomeEmptyActions)
    }
}

private object HomeEmptyActions : HomeContract.Actions {
    override fun refresh(feed: FeedSource) = Unit
    override fun loadMore(feed: FeedSource) = Unit
}
