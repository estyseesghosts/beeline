package me.foxtails.palustris.data.mastodon

import java.util.Base64
import me.foxtails.palustris.domain.SourceError
import org.json.JSONObject

internal data class MastodonPageRoute(
    val name: String,
    val endpoint: String,
    val encodedPath: String,
    val query: String,
    val queryIdentityKey: String,
)

internal object MastodonPageCursor {
    private const val VERSION = 1
    private const val VARIANT = "mastodon-page-v1"

    data class Identity(
        val origin: String,
        val account: String,
        val sessionRevision: Long,
        val sourceInstance: String,
        val route: String,
        val query: String,
    )

    fun encode(identity: Identity, url: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(JSONObject().put("version", VERSION).put("variant", VARIANT)
            .put("origin", identity.origin).put("account", identity.account)
            .put("sessionRevision", identity.sessionRevision).put("sourceInstance", identity.sourceInstance)
            .put("route", identity.route).put("query", identity.query).put("url", url)
            .toString().toByteArray(Charsets.UTF_8))

    fun decode(cursor: String, expected: Identity): String {
        try {
            // Raw URL cursors are rejected because a server-supplied URL must not choose an authenticated route.
            if (cursor.startsWith("http://") || cursor.startsWith("https://") || cursor.contains("://")) reject()
            val json = JSONObject(String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8))
            val keys = setOf("version", "variant", "origin", "account", "sessionRevision", "sourceInstance", "route", "query", "url")
            if (json.opt("version") !is Int || json.opt("variant") !is String ||
                json.opt("origin") !is String || json.opt("account") !is String ||
                json.opt("sessionRevision") !is Int && json.opt("sessionRevision") !is Long ||
                json.opt("sourceInstance") !is String || json.opt("route") !is String ||
                json.opt("query") !is String || json.opt("url") !is String
            ) reject()
            if (json.keys().asSequence().toSet() != keys || json.getInt("version") != VERSION || json.getString("variant") != VARIANT ||
                json.getString("origin") != expected.origin || json.getString("account") != expected.account ||
                json.getLong("sessionRevision") != expected.sessionRevision || json.getString("sourceInstance") != expected.sourceInstance ||
                json.getString("route") != expected.route || json.getString("query") != expected.query) reject()
            return json.getString("url").takeIf { it.isNotBlank() && !it.startsWith("/") }
                ?: throw SourceError.Unsupported("pagination.cursor")
        } catch (error: SourceError) {
            throw error
        } catch (_: Exception) {
            reject()
        }
    }

    private fun reject(): Nothing = throw SourceError.Unsupported("pagination.cursor")
}
