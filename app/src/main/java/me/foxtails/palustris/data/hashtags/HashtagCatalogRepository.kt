package me.foxtails.palustris.data.hashtags

import java.io.InputStream
import me.foxtails.palustris.domain.hashtags.CatalogMember
import me.foxtails.palustris.domain.hashtags.HashtagCatalog
import me.foxtails.palustris.domain.hashtags.HashtagGroup
import me.foxtails.palustris.domain.hashtags.HashtagRelation
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads the bundled hashtag catalog once and keeps the immutable result for the process.
 * A missing, unreadable or invalid asset gives an empty catalog, so related hashtags
 * quietly turn off instead of crashing. [openAsset] must return a fresh stream or throw.
 */
class HashtagCatalogRepository(private val openAsset: () -> InputStream) {
    val catalog: HashtagCatalog by lazy {
        runCatching {
            openAsset().use { stream -> HashtagCatalogParser.parse(stream.readBytes().toString(Charsets.UTF_8)) }
        }.getOrDefault(HashtagCatalog.Empty)
    }
}

/** Parses the version 1 asset format. Throws on a structural error. */
internal object HashtagCatalogParser {
    private const val SUPPORTED_VERSION = 1

    fun parse(text: String): HashtagCatalog {
        val root = JSONObject(text)
        require(root.getInt("v") == SUPPORTED_VERSION) { "Unsupported hashtag catalog version" }
        val groups = root.getJSONArray("groups")
        return HashtagCatalog(List(groups.length()) { parseGroup(groups.getJSONObject(it)) })
    }

    private fun parseGroup(json: JSONObject): HashtagGroup {
        val members = json.getJSONArray("m")
        return HashtagGroup(
            id = json.getString("id"),
            members = List(members.length()) { members.getJSONArray(it) }.mapNotNull(::parseMember),
            excludedGroups = json.strings("x").toSet(),
            childGroups = json.strings("c"),
            ambiguous = json.strings("amb").map(HashtagCatalog::identityOf).toSet(),
        )
    }

    /** `[name, [languages], relation, weight]`. A member with an unknown relation is dropped. */
    private fun parseMember(json: JSONArray): CatalogMember? {
        val relation = HashtagRelation.fromCode(json.getString(2)) ?: return null
        val name = HashtagCatalog.identityOf(json.getString(0))
        if (name.isBlank()) return null
        val languages = json.getJSONArray(1)
        return CatalogMember(
            name = name,
            languages = List(languages.length()) { languages.getString(it) },
            relation = relation,
            weight = json.getDouble(3),
        )
    }

    private fun JSONObject.strings(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return List(array.length()) { array.getString(it) }
    }
}
