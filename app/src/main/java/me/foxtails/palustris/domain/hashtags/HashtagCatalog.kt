package me.foxtails.palustris.domain.hashtags

import java.util.Locale

/** How a catalog member relates to the head of its group. */
enum class HashtagRelation(val code: String) {
    /** The canonical hashtag of the group. */
    Head("h"),

    /** The same topic in another form or language. An entry point for a merge. */
    Synonym("s"),

    /** A narrower topic. It joins a merge but does not start one. */
    Narrower("n"),

    /** A neighbouring topic. It is only suggested. */
    Related("r"),
    ;

    companion object {
        fun fromCode(code: String): HashtagRelation? = entries.firstOrNull { it.code == code }
    }
}

/**
 * One hashtag of a group. [name] is already an identity (lowercase, `Locale.ROOT`), [languages]
 * are catalog language codes and [weight] is `log2(1 + accounts)` for seven days.
 */
data class CatalogMember(
    val name: String,
    val languages: List<String>,
    val relation: HashtagRelation,
    val weight: Double,
) {
    /** The language that ranks this member first. Null only for malformed data. */
    val primaryLanguage: String? get() = languages.firstOrNull()
}

/**
 * A topic with its members. [excludedGroups] never merge into this group, [childGroups] only
 * contribute their heads as suggestions, and [ambiguous] lists hashtags that must not expand.
 */
data class HashtagGroup(
    val id: String,
    val members: List<CatalogMember>,
    val excludedGroups: Set<String> = emptySet(),
    val childGroups: List<String> = emptyList(),
    val ambiguous: Set<String> = emptySet(),
) {
    val head: CatalogMember? get() = members.firstOrNull { it.relation == HashtagRelation.Head }
}

/** A member together with the group that holds it. */
data class CatalogEntry(val group: HashtagGroup, val member: CatalogMember)

/**
 * Immutable catalog of related hashtag groups with an identity index and prefix lookup.
 * Responsibility: lookup only. Process lifetime: the repository builds one instance.
 */
class HashtagCatalog(groups: List<HashtagGroup>) {
    val groups: List<HashtagGroup> = groups.toList()

    private val byId: Map<String, HashtagGroup> = this.groups.associateBy { it.id }
    private val index: Map<String, List<CatalogEntry>> = buildMap<String, MutableList<CatalogEntry>> {
        this@HashtagCatalog.groups.forEach { group ->
            group.members.forEach { member ->
                getOrPut(member.name) { mutableListOf() } += CatalogEntry(group, member)
            }
        }
    }
    private val sortedNames: List<String> = index.keys.sorted()

    val memberCount: Int get() = this.groups.sumOf { it.members.size }

    fun group(id: String): HashtagGroup? = byId[id]

    /** Every `(group, member)` pair for an identity. Empty when the catalog does not know it. */
    fun entries(identity: String): List<CatalogEntry> = index[identity].orEmpty()

    /** Entries whose identity starts with [prefix], which must be an identity already. */
    fun prefixEntries(prefix: String): List<CatalogEntry> {
        if (prefix.isEmpty()) return emptyList()
        var position = sortedNames.binarySearch(prefix)
        if (position < 0) position = -position - 1
        val matches = mutableListOf<CatalogEntry>()
        while (position < sortedNames.size && sortedNames[position].startsWith(prefix)) {
            matches += index.getValue(sortedNames[position])
            position++
        }
        return matches
    }

    companion object {
        val Empty = HashtagCatalog(emptyList())

        /** The identity of a catalog name: the same lowercase rule as `hashtagIdentity`. */
        fun identityOf(name: String): String = name.removePrefix("#").lowercase(Locale.ROOT)
    }
}
