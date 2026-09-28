package me.foxtails.palustris.ui.search

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.Post

data class AccountSearchState(
    val query: String = "",
    val accounts: List<Account> = emptyList(),
    val posts: List<Post> = emptyList(),
    val tagQuery: String? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null,
)
