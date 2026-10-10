package me.foxtails.palustris.domain.hashtags

import me.foxtails.palustris.domain.AppLanguage
import me.foxtails.palustris.domain.hashtagBody
import me.foxtails.palustris.domain.hashtagIdentity
import me.foxtails.palustris.domain.isExactHashtag

/**
 * A hashtag search: the [primary] hashtag plus hashtags that match as well. [alsoMatching] is
 * ordered best first, so an adapter that accepts fewer hashtags keeps the front of the list.
 */
data class HashtagQuery(val primary: String, val alsoMatching: List<String> = emptyList()) {
    /**
     * The extra hashtag bodies an adapter sends: valid, distinct by identity, never the primary,
     * and at most [limit]. Order is kept.
     */
    fun extras(limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        val primaryIdentity = hashtagIdentity(primary)
        return alsoMatching
            .filter(::isExactHashtag)
            .map(::hashtagBody)
            .distinctBy(::hashtagIdentity)
            .filter { hashtagIdentity(it) != primaryIdentity }
            .take(limit)
    }
}

/**
 * Everything a search needs to expand a hashtag: the rules, the setting at the moment the search
 * starts, and the display language for related chips. Screens read the latest value when a
 * search begins, so a setting change never rewrites an open result.
 */
class HashtagExpansionInput(
    private val expander: HashtagExpander,
    val enabled: Boolean,
    val policy: HashtagLanguagePolicy,
) {
    /** The immutable catalog behind the rules. Suggestions read it. */
    val catalog: HashtagCatalog get() = expander.catalog

    /** Expands [tag] for a source that accepts [limit] extras. [combine] overrides the setting. */
    fun expand(tag: String, limit: Int, combine: Boolean = enabled): HashtagExpansion =
        expander.expand(tag, combine, policy, limit)

    companion object {
        /** No catalog and the setting off. Used where no composition root supplies the rules. */
        val Disabled = HashtagExpansionInput(
            HashtagExpander(HashtagCatalog.Empty),
            enabled = false,
            policy = HashtagLanguagePolicy.forLanguage(AppLanguage.English),
        )
    }
}
