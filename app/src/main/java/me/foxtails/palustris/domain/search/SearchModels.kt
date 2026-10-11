package me.foxtails.palustris.domain.search

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.hashtags.HashtagSuggestion
import me.foxtails.palustris.domain.hashtags.TrendingHashtag

/** What the suggestion panel shows while a query is typed: at most [MAX_ACCOUNTS] and [MAX_HASHTAGS]. */
data class SearchSuggestions(
    val accounts: List<Account> = emptyList(),
    val hashtags: List<HashtagSuggestion> = emptyList(),
) {
    val isEmpty: Boolean get() = accounts.isEmpty() && hashtags.isEmpty()

    /** Cuts both lists to their limits and drops repeats, whatever the server returned. */
    fun limited(): SearchSuggestions = SearchSuggestions(
        accounts = accounts.distinctBy { it.id }.take(MAX_ACCOUNTS),
        hashtags = hashtags.distinctBy { it.name.lowercase() }.take(MAX_HASHTAGS),
    )

    companion object {
        const val MAX_ACCOUNTS = 3
        const val MAX_HASHTAGS = 6
    }
}

/** A link that the server reports as trending. Mastodon only. */
data class TrendingLink(
    val url: String,
    val title: String,
    val description: String?,
    val provider: String?,
    val imageUrl: String?,
)

/** The three Trends sections. A section the server cannot or will not fill is empty. */
data class Trends(
    val tags: List<TrendingHashtag> = emptyList(),
    val posts: Page<Post> = Page(emptyList()),
    val links: List<TrendingLink> = emptyList(),
) {
    val isEmpty: Boolean get() = tags.isEmpty() && posts.items.isEmpty() && links.isEmpty()
}

/** Whether the account may search post text. Mastodon cannot tell ahead of time, so it stays Unknown. */
data class SearchCapabilities(
    val posts: CapabilityStatus = CapabilityStatus.Unknown,
)
