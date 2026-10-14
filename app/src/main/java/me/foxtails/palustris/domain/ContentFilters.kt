package me.foxtails.palustris.domain

/**
 * One pattern of a [ContentFilter]. A filter matches a post when any one of its patterns matches.
 */
sealed interface FilterPattern {
    /**
     * Every word in [words] must appear in the post. [wholeWord] requires word boundaries at the ends of each word.
     * [ignoreCase] is true for Mastodon keywords.
     */
    data class Words(val words: List<String>, val wholeWord: Boolean = false, val ignoreCase: Boolean = true) : FilterPattern
}

/** How the account's server stores filters. The settings editor follows this shape. */
enum class ContentFilterKind {
    /** Titled filters with several keywords, an action, contexts, and expiry. The server matches them. Mastodon 4.0 and later. */
    Titled,

    /** One phrase per filter, with contexts, expiry, and "irreversible". The client matches them. Mastodon v1 filters: Pleroma, Akkoma, and older servers. */
    Phrase,
}

/**
 * One filter of the account. [id] is opaque and only the adapter that made it reads it. [patternIds] holds the
 * server keyword IDs in the order of [patterns] where the server has them; an adapter uses them to edit keywords.
 */
data class ContentFilter(
    val id: String,
    val title: String,
    val patterns: List<FilterPattern>,
    val action: PostFilterAction,
    val contexts: Set<FilterContext> = FilterContext.entries.toSet(),
    val expiresAtEpochMillis: Long? = null,
    val patternIds: List<String?> = emptyList(),
)

/** What the user saves. [expiresInSeconds] null means the filter never expires. */
data class ContentFilterDraft(
    val title: String,
    val patterns: List<FilterPattern>,
    val action: PostFilterAction,
    val contexts: Set<FilterContext> = FilterContext.entries.toSet(),
    val expiresInSeconds: Long? = null,
)

/** All filters of one account, as one snapshot. [kind] shapes the editor and says who matches them. */
data class ContentFilterSet(
    val kind: ContentFilterKind,
    val filters: List<ContentFilter> = emptyList(),
) {
    /** True when Beeline must match the filters itself. The server already matches [ContentFilterKind.Titled] filters. */
    val clientMatched: Boolean get() = kind != ContentFilterKind.Titled
}
