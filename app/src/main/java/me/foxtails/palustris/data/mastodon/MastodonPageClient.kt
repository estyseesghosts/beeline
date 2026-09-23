package me.foxtails.palustris.data.mastodon

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.domain.SourceError

internal class MastodonPageClient(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val account: String,
    private val sessionRevision: Long,
    private val sourceInstance: String,
) {
    suspend fun getPage(route: MastodonPageRoute, cursor: String?) = if (cursor == null) {
        api.get(origin, route.endpoint, token)
    } else {
        val url = decodeAndValidate(route, cursor)
        api.getUrl(url.toString(), token)
    }

    fun validateCursor(route: MastodonPageRoute, cursor: String?): HttpUrl? =
        cursor?.let { decodeAndValidate(route, it) }

    // Temporary: raw Link replay retained until slice 1B2 binds these routes; remove there.
    suspend fun getPageLegacyRawReplay(endpoint: String, cursor: String?) = if (cursor == null) {
        api.get(origin, endpoint, token)
    } else if (cursor.startsWith("http://") || cursor.startsWith("https://")) {
        api.getUrl(validatePaginationUrl(cursor).toString(), token)
    } else {
        api.get(origin, cursor.removePrefix("/api/"), token)
    }

    fun nextCursor(response: me.foxtails.palustris.data.misskey.HttpResponse, route: MastodonPageRoute, currentUrl: String): String? {
        val link = response.linkHeaderCursor() ?: return null
        val url = try {
            validateUrl(route, link, currentUrl)
        } catch (_: Exception) {
            throw SourceError.Unsupported("pagination.link")
        }
        return MastodonPageCursor.encode(identity(route), url.toString())
    }

    fun currentUrl(route: MastodonPageRoute, cursor: String?): String = if (cursor == null) {
        origin.toHttpUrl().resolve("/api/${route.endpoint}")?.toString()
            ?: throw SourceError.Unsupported("pagination.cursor")
    } else {
        decodeAndValidate(route, cursor).toString()
    }

    private fun decodeAndValidate(route: MastodonPageRoute, cursor: String): HttpUrl {
        val url = MastodonPageCursor.decode(cursor, identity(route))
        return try {
            validateUrl(route, url, origin.toHttpUrl().resolve("/api/${route.endpoint}")?.toString())
        } catch (_: Exception) {
            throw SourceError.Unsupported("pagination.cursor")
        }
    }

    private fun validateUrl(route: MastodonPageRoute, cursor: String, currentUrl: String?): HttpUrl {
        val page = cursor.toHttpUrlOrNull() ?: throw SourceError.Unsupported("pagination")
        val authenticatedOrigin = origin.toHttpUrl()
        if (page.scheme != authenticatedOrigin.scheme || page.host != authenticatedOrigin.host ||
            page.port != authenticatedOrigin.port || page.username.isNotEmpty() || page.password.isNotEmpty() ||
            page.fragment != null || page.encodedPath != route.encodedPath || currentUrl == page.toString()
        ) {
            throw SourceError.Unsupported("pagination.cursor")
        }
        val names = page.queryParameterNames
        val pagination = listOf("max_id", "since_id", "min_id").filter { page.queryParameterValues(it).size == 1 && !page.queryParameter(it).isNullOrBlank() }
        if (names.any { it !in setOf("max_id", "since_id", "min_id", "local") } || pagination.size != 1 ||
            page.queryParameterNames.any { page.queryParameterValues(it).size != 1 } ||
            when (route.name) {
                "timeline:Local" -> page.queryParameter("local") != "true"
                "timeline:Federated", "timeline:Home" -> page.queryParameter("local") != null
                else -> false
            }
        ) throw SourceError.Unsupported("pagination.cursor")
        return page
    }

    private fun identity(route: MastodonPageRoute) = MastodonPageCursor.Identity(
        origin, account, sessionRevision, sourceInstance, route.name, route.query,
    )

    private fun validatePaginationUrl(cursor: String): HttpUrl {
        val page = cursor.toHttpUrlOrNull() ?: throw SourceError.Unsupported("pagination")
        val authenticatedOrigin = origin.toHttpUrl()
        if (page.scheme != authenticatedOrigin.scheme || page.host != authenticatedOrigin.host ||
            page.port != authenticatedOrigin.port || page.username.isNotEmpty() || page.password.isNotEmpty() ||
            page.fragment != null
        ) throw SourceError.Unsupported("pagination")
        return page
    }
}
