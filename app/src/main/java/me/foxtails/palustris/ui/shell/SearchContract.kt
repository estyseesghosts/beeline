package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.ui.search.SearchState
import me.foxtails.palustris.ui.search.SearchTrendsActions
import me.foxtails.palustris.ui.search.SearchTrendsState

/**
 * Search presentation.
 *
 * Search keeps its own query and pagination. The connected [me.foxtails.palustris.ui.search.SearchOwner]
 * supplies this state and actions. It must not absorb Home timeline state. [Empty] is an inert preview
 * value.
 */
data class SearchContract(
    val state: SearchState,
    val actions: Actions,
    val trends: SearchTrendsState = SearchTrendsState(),
) {
    interface Actions : SearchTrendsActions {
        fun search(query: String)

        /** Searches the hashtag alone, even when the Combine related hashtags setting is on. */
        fun searchWithoutRelated(query: String)
        fun loadMore()

        /** Reports the typed text so the owner can fetch suggestions after a quiet period. */
        fun suggest(text: String) = Unit

        /** Clears the target that a handle or link search opened, once the screen has opened it. */
        fun consumeTarget() = Unit
    }

    companion object {
        val Empty = SearchContract(SearchState(), SearchEmptyActions)
    }
}

private object SearchEmptyActions : SearchContract.Actions {
    override fun search(query: String) = Unit
    override fun searchWithoutRelated(query: String) = Unit
    override fun loadMore() = Unit
}
