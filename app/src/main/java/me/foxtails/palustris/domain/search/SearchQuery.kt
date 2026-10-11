package me.foxtails.palustris.domain.search

import me.foxtails.palustris.domain.isExactHashtag

/** What a typed search string asks for. [text] is the value the search routes send. */
sealed interface SearchQuery {
    val text: String

    data object Blank : SearchQuery {
        override val text: String = ""
    }

    /** `@user` or `@user@host`. [text] has no leading at sign. [full] is true when it names a host. */
    data class Handle(override val text: String, val full: Boolean) : SearchQuery

    /** `#tag`. [text] is the tag without the hash. */
    data class Hashtag(override val text: String) : SearchQuery

    data class Url(override val text: String) : SearchQuery

    data class FreeText(override val text: String) : SearchQuery
}

private val handleBody = Regex("""^[A-Za-z0-9_][A-Za-z0-9_.-]*(@[A-Za-z0-9.-]+\.[A-Za-z0-9-]+)?$""")
private val urlShape = Regex("""^https?://[^\s/]+\S*$""", RegexOption.IGNORE_CASE)

/** Sorts a typed string into a handle, a hashtag, a URL or free text. Surrounding space never counts. */
fun classifySearchQuery(raw: String): SearchQuery {
    val trimmed = raw.trim()
    return when {
        trimmed.isEmpty() -> SearchQuery.Blank
        urlShape.matches(trimmed) -> SearchQuery.Url(trimmed)
        trimmed.startsWith("#") && isExactHashtag(trimmed) -> SearchQuery.Hashtag(trimmed.removePrefix("#"))
        trimmed.startsWith("@") && handleBody.matches(trimmed.removePrefix("@")) -> {
            val body = trimmed.removePrefix("@")
            SearchQuery.Handle(body, full = '@' in body)
        }
        else -> SearchQuery.FreeText(trimmed)
    }
}
