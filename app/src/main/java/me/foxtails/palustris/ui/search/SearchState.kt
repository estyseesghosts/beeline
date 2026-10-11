package me.foxtails.palustris.ui.search

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.search.SearchSuggestions

/** What the last submitted query asked for. [Lookup] covers a handle and a link, which open one target. */
enum class SearchMode { Idle, Posts, Hashtag, Lookup }

/** The one thing a handle or link search found. The screen opens it once and then consumes it. */
sealed interface SearchTarget {
    data class Profile(val account: Account) : SearchTarget

    data class SinglePost(val post: Post) : SearchTarget
}

/**
 * Search state for one connected session.
 *
 * [query] and everything below it belong to the last submitted query. [suggestions] belong to the text
 * that [suggestionsFor] holds, which can be newer than [query] while the user types.
 */
data class SearchState(
    val query: String = "",
    val mode: SearchMode = SearchMode.Idle,
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
    /** True when the account's role blocks post search. It differs from an empty answer. */
    val postSearchDenied: Boolean = false,
    val suggestions: SearchSuggestions = SearchSuggestions(),
    val suggestionsFor: String = "",
    val target: SearchTarget? = null,
) {
    /** True when this state holds a result set that the Photo Grid can show. */
    val hasPostResults: Boolean
        get() = (mode == SearchMode.Posts || mode == SearchMode.Hashtag) && query.isNotBlank() && error == null && !postSearchDenied
}

/** What the Search screen shows for the typed text and the state. */
internal enum class SearchPresentation { Trends, Suggestions, Posts, Hashtag, Lookup }

/** Chooses the pane. Results show only while the field still holds the query that produced them. */
internal fun searchPresentation(typed: String, state: SearchState): SearchPresentation = when {
    typed.isBlank() -> SearchPresentation.Trends
    typed.trim() != state.query -> SearchPresentation.Suggestions
    else -> when (state.mode) {
        SearchMode.Posts -> SearchPresentation.Posts
        SearchMode.Hashtag -> SearchPresentation.Hashtag
        // A lookup that opened its target and found no error shows the suggestions again when the user returns.
        SearchMode.Lookup -> if (state.loading || state.error != null || state.target != null) SearchPresentation.Lookup else SearchPresentation.Suggestions
        SearchMode.Idle -> SearchPresentation.Suggestions
    }
}
