package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.ui.photogrid.PhotoGridFeed
import me.foxtails.palustris.ui.photogrid.PhotoGridFeedState

/**
 * Photo Grid presentation.
 *
 * Photo Grid has independent feed state and timeline selection. It must not reuse Home feed
 * state or Home scroll state. [Empty] is an inert preview value.
 */
data class PhotoGridContract(
    val state: PhotoGridFeedState,
    val actions: Actions,
    /** The shared chip catalog: lists, hashtags and antennas. The grid and Home read the same one. */
    val chips: FeedChipsContract = FeedChipsContract.Empty,
) {
    interface Actions {
        fun ensureLoaded()
        fun selectFeed(feed: PhotoGridFeed)
        fun refresh()
        fun loadMore()
    }

    companion object {
        val Empty = PhotoGridContract(PhotoGridFeedState(), PhotoGridEmptyActions)
    }
}

private object PhotoGridEmptyActions : PhotoGridContract.Actions {
    override fun ensureLoaded() = Unit
    override fun selectFeed(feed: PhotoGridFeed) = Unit
    override fun refresh() = Unit
    override fun loadMore() = Unit
}
