package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.ui.search.AccountSearchState

/**
 * Account-search presentation.
 *
 * Search keeps its own query and pagination. The connected [me.foxtails.palustris.ui.search.SearchOwner]
 * supplies this state and actions. It must not absorb Home timeline state. [Empty] is an inert preview
 * value.
 */
data class SearchContract(
    val state: AccountSearchState,
    val actions: Actions,
) {
    interface Actions {
        fun search(query: String)
        fun loadMore()
    }

    companion object {
        val Empty = SearchContract(AccountSearchState(), SearchEmptyActions)
    }
}

private object SearchEmptyActions : SearchContract.Actions {
    override fun search(query: String) = Unit
    override fun loadMore() = Unit
}
