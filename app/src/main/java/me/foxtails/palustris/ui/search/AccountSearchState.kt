package me.foxtails.palustris.ui.search

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.Post

data class AccountSearchState(
    val query: String = "",
    val accounts: List<Account> = emptyList(),
    val posts: List<Post> = emptyList(),
    val tagQuery: String? = null,
    /** The extra hashtags the search applied, best first. Load-more sends exactly these. */
    val combinedTags: List<String> = emptyList(),
    /** Suggested hashtags for display. Empty when the setting is off or the catalog has none. */
    val relatedTags: List<String> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null,
)
