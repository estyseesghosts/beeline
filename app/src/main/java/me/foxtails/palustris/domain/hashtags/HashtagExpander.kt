package me.foxtails.palustris.domain.hashtags

import me.foxtails.palustris.domain.hashtagIdentity

/**
 * The result of expanding one searched hashtag. [applied] lists the extra hashtags to search, best
 * first and already trimmed to the source limit. [related] lists suggestions for display only.
 */
data class HashtagExpansion(
    val applied: List<String> = emptyList(),
    val related: List<String> = emptyList(),
) {
    companion object {
        val None = HashtagExpansion()
    }
}

/**
 * Pure expansion, ranking and related-hashtag rules over an immutable [HashtagCatalog]. The
 * expander owns no mutable state, so one instance serves every account and screen.
 */
class HashtagExpander(val catalog: HashtagCatalog) {
    /**
     * Expands [tag] when [enabled]. [limit] is how many extras the source accepts. [policy] filters
     * the related hashtags. It never filters [HashtagExpansion.applied].
     */
    fun expand(
        tag: String,
        enabled: Boolean,
        policy: HashtagLanguagePolicy,
        limit: Int,
    ): HashtagExpansion {
        if (!enabled) return HashtagExpansion.None
        val identity = runCatching { hashtagIdentity(tag) }.getOrNull() ?: return HashtagExpansion.None
        val entries = catalog.entries(identity)
        if (entries.isEmpty()) return HashtagExpansion.None
        if (entries.any { identity in it.group.ambiguous }) return HashtagExpansion.None

        val related = relatedFor(identity, entries, policy)
        val entryPoints = entries.filter {
            it.member.relation == HashtagRelation.Head || it.member.relation == HashtagRelation.Synonym
        }
        if (entryPoints.isEmpty() || limit <= 0) return HashtagExpansion(related = related)

        val groups = entryPoints.map { it.group }.distinctBy { it.id }
        val blocked = buildSet {
            add(identity)
            groups.forEach { group ->
                addAll(group.ambiguous)
                group.excludedGroups.forEach { excluded ->
                    catalog.group(excluded)?.members?.forEach { add(it.name) }
                }
            }
        }
        val candidates = groups
            .flatMap { it.members }
            .filter { it.relation != HashtagRelation.Related && it.name !in blocked }
            .groupBy { it.name }
            .map { (_, duplicates) -> duplicates.minWith(compareBy({ it.relation.ordinal }, { -it.weight })) }
        return HashtagExpansion(applied = rank(candidates).take(limit), related = related)
    }

    /**
     * Ranking: the head first, then the best synonym of each first language that the head does not
     * cover (ordered by weight), then the remaining synonyms by weight, then the narrower members.
     * The caller trims from the end, so a small limit keeps one member per language.
     */
    private fun rank(candidates: List<CatalogMember>): List<String> {
        val byWeight = compareByDescending<CatalogMember> { it.weight }.thenBy { it.name }
        val heads = candidates.filter { it.relation == HashtagRelation.Head }.sortedWith(byWeight)
        val synonyms = candidates.filter { it.relation == HashtagRelation.Synonym }.sortedWith(byWeight)
        val narrower = candidates.filter { it.relation == HashtagRelation.Narrower }.sortedWith(byWeight)

        val covered = heads.mapNotNull { it.primaryLanguage }.toMutableSet()
        val perLanguage = mutableListOf<CatalogMember>()
        synonyms.forEach { member ->
            val language = member.primaryLanguage
            if (language != null && covered.add(language)) perLanguage += member
        }
        val chosen = perLanguage.toSet()
        return (heads + perLanguage + synonyms.filter { it !in chosen } + narrower).map { it.name }
    }

    private fun relatedFor(
        identity: String,
        entries: List<CatalogEntry>,
        policy: HashtagLanguagePolicy,
    ): List<String> {
        val suggestions = mutableListOf<CatalogMember>()
        entries.forEach { (group, member) ->
            suggestions += group.members.filter { it.relation == HashtagRelation.Related }
            if (member.relation == HashtagRelation.Narrower) group.head?.let(suggestions::add)
            group.childGroups.forEach { child -> catalog.group(child)?.head?.let(suggestions::add) }
        }
        val ambiguous = entries.flatMapTo(mutableSetOf()) { it.group.ambiguous }
        return suggestions
            .filter { it.name != identity && it.name !in ambiguous && policy.allows(it) }
            .sortedWith(compareByDescending<CatalogMember> { it.weight }.thenBy { it.name })
            .map { it.name }
            .distinct()
    }
}
