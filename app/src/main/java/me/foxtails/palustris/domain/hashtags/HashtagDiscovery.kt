package me.foxtails.palustris.domain.hashtags

/** A hashtag that the server reports as trending. Counts are null when the server omits them. */
data class TrendingHashtag(val name: String, val accounts: Int?, val uses: Int?)

/** A suggested hashtag. [weight] approximates `log2(1 + accounts)` and is null when unknown. */
data class HashtagSuggestion(val name: String, val weight: Double?)
