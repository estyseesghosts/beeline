package me.foxtails.palustris.domain

/**
 * One pattern of a [ContentFilter]. A filter matches a post when any one of its patterns matches.
 */
sealed interface FilterPattern {
    /**
     * Every word in [words] must appear in the post. [wholeWord] requires word boundaries at the ends of each word.
     * [ignoreCase] is true for Mastodon keywords. Misskey word mutes match capitalization.
     */
    data class Words(val words: List<String>, val wholeWord: Boolean = false, val ignoreCase: Boolean = true) : FilterPattern

    /** A regular expression in the JavaScript form that Misskey stores: [source] without slashes, and [flags]. */
    data class Expression(val source: String, val flags: String = "") : FilterPattern
}

/** How the account's server stores filters. The settings editor follows this shape. */
enum class ContentFilterKind {
    /** Titled filters with several keywords, an action, contexts, and expiry. The server matches them. Mastodon 4.0 and later. */
    Titled,

    /** One phrase per filter, with contexts, expiry, and "irreversible". The client matches them. Mastodon v1 filters: Pleroma, Akkoma, and older servers. */
    Phrase,

    /** Two word lists: one collapses and one removes. Each entry is words or an expression. The client matches them. Misskey family. */
    WordMute,
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

/** The features that [SourceError.InvalidInput] names when a server refuses a filter. */
object ContentFilterInput {
    /** The server cannot use the expression, such as Misskey's RE2 rejecting a lookahead. */
    const val EXPRESSION = "moderation.filters.expression"

    /** The server holds no more filters or words for this account. */
    const val LIMIT = "moderation.filters.limit"
}

/** All filters of one account, as one snapshot. [kind] shapes the editor and says who matches them. */
data class ContentFilterSet(
    val kind: ContentFilterKind,
    val filters: List<ContentFilter> = emptyList(),
) {
    /** True when Beeline must match the filters itself. The server already matches [ContentFilterKind.Titled] filters. */
    val clientMatched: Boolean get() = kind != ContentFilterKind.Titled
}
