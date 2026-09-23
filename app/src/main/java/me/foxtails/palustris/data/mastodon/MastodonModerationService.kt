package me.foxtails.palustris.data.mastodon

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Base64
import org.json.JSONArray
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.ModerationAccount
import me.foxtails.palustris.domain.ModerationCursor
import me.foxtails.palustris.domain.ModerationListKind
import me.foxtails.palustris.domain.ModerationPage
import me.foxtails.palustris.domain.MutedHashtag
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ReportRequest
import me.foxtails.palustris.domain.SourceError
import org.json.JSONObject

class MastodonModerationService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val accountId: AccountId,
) {
    suspend fun blocked(cursor: ModerationCursor? = null): ModerationPage<ModerationAccount> = list(ModerationListKind.Blocked, cursor)
    suspend fun muted(cursor: ModerationCursor? = null): ModerationPage<ModerationAccount> = list(ModerationListKind.Muted, cursor)
    suspend fun hashtags(cursor: ModerationCursor? = null): ModerationPage<MutedHashtag> =
        throw SourceError.Unsupported("moderation.hashtags")

    suspend fun removeBlocked(entry: ModerationAccount) = remove("block", entry.account.id)
    suspend fun removeMuted(entry: ModerationAccount) = remove("mute", entry.account.id)

    suspend fun setBlocked(target: AccountId, blocked: Boolean): ProfileRelationship {
        validateTarget(target, "profile.block")
        return relationship(target, if (blocked) "block" else "unblock")
    }

    suspend fun setMuted(target: AccountId, muted: Boolean): ProfileRelationship {
        validateTarget(target, "profile.mute")
        return relationship(target, if (muted) "mute" else "unmute")
    }

    suspend fun report(request: ReportRequest) {
        validateOrigin(request.targetAccountId.connection.origin, "moderation.report")
        request.postId?.let { validateOrigin(it.connection, "moderation.report") }
        val fields = buildList {
            add("account_id" to request.targetAccountId.localId)
            request.postId?.let { add("status_ids[]" to it.value) }
            if (request.comment.isNotBlank()) add("comment" to request.comment)
        }
        api.postForm(origin, "api/v1/reports", fields, token)
    }

    private suspend fun list(kind: ModerationListKind, cursor: ModerationCursor?): ModerationPage<ModerationAccount> {
        val variant = "mastodon-${kind.name.lowercase()}-v1"
        val route = if (kind == ModerationListKind.Blocked) "blocked" else "muted"
        val path = "/api/v1/accounts/$route"
        val endpoint = "v1/accounts/$route?limit=40"
        val url = cursor?.let { decodeCursor(it, kind, variant, route, path) }
        val current = url ?: origin.toHttpUrl().resolve("/api/$endpoint")!!
        val response = if (url == null) api.get(origin, endpoint, token) else api.getUrl(url.toString(), token)
        val values = JSONArray(response.body)
        val items = (0 until values.length()).mapNotNull { index ->
            val account = runCatching { MastodonMapper.account(values.getJSONObject(index), origin) }.getOrNull() ?: return@mapNotNull null
            ModerationAccount(account = account, relationshipId = account.id.localId)
        }
        val next = response.linkHeaderCursor()?.let {
            val link = try {
                validateUrl(it, path, current.toString())
            } catch (_: Exception) {
                throw SourceError.Unsupported("pagination.link")
            }
            encodeCursor(kind, variant, route, link.toString())
        }
        return ModerationPage(items, next)
    }

    private suspend fun remove(action: String, target: AccountId) {
        validateOrigin(target.connection.origin, "moderation.remove")
        api.delete(origin, accountPath(target, action), token)
    }

    private suspend fun relationship(target: AccountId, action: String): ProfileRelationship {
        validateTarget(target, "profile.$action")
        val response = if (action == "block" || action == "mute") {
            api.postForm(origin, accountPath(target, action), emptyList(), token)
        } else {
            api.delete(origin, accountPath(target, action), token)
        }
        return runCatching { parseRelationship(response.body, target) }
            .getOrElse { profileRelationship(target) }
    }

    private suspend fun profileRelationship(target: AccountId): ProfileRelationship {
        // Build a complete HttpUrl with an encoded path and query, then use getUrl to bypass the /api/ prefix that MisskeyApi.get adds.
        val url = origin.toHttpUrl().newBuilder()
            .addPathSegments("api/v1/accounts/relationships")
            .addQueryParameter("id[]", target.localId)
            .build()
        val response = api.getUrl(url.toString(), token)
        return parseRelationship(response.body, target)
    }

    private fun accountPath(target: AccountId, action: String): String = origin.toHttpUrl().newBuilder()
        .addPathSegments("api/v1/accounts")
        .addPathSegment(target.localId)
        .addPathSegment(action)
        .build()
        .encodedPath
        .removePrefix("/")

    private fun parseRelationship(body: String, profileId: AccountId): ProfileRelationship {
        val root = body.trimStart()
        val json = if (root.startsWith("[")) JSONArray(body).optJSONObject(0) else JSONObject(body)
        if (json == null || (!json.has("following") && !json.has("requested") && !json.has("followed_by") &&
                !json.has("muting") && !json.has("blocking"))) {
            throw SourceError.Unsupported("profile.relationship")
        }
        return MastodonMapper.relationship(json, profileId)
    }

    private fun validateTarget(target: AccountId, feature: String) {
        if (target.connection.origin != origin) throw SourceError.ForeignOrigin(feature)
        if (target.connection.protocol != Protocol.MASTODON || target.localId.isBlank()) {
            throw SourceError.Unsupported(feature)
        }
    }

    private fun validateOrigin(targetOrigin: String, feature: String) {
        if (targetOrigin != origin) throw SourceError.ForeignOrigin(feature)
    }

    private fun decodeCursor(cursor: ModerationCursor, kind: ModerationListKind, variant: String, route: String, path: String): HttpUrl {
        if (cursor.accountId != accountId || cursor.query.kind != kind || cursor.protocolVariant != variant) {
            throw SourceError.Unsupported("moderation.cursor")
        }
        val payload = try {
            if (cursor.value.contains("://")) throw IllegalArgumentException()
            JSONObject(String(Base64.getUrlDecoder().decode(cursor.value), Charsets.UTF_8))
        } catch (_: Exception) {
            throw SourceError.Unsupported("moderation.cursor")
        }
        val keys = setOf("version", "variant", "route", "kind", "account", "url")
        if (payload.keys().asSequence().toSet() != keys || payload.opt("version") !is Int ||
            payload.opt("variant") !is String || payload.opt("route") !is String ||
            payload.opt("kind") !is String || payload.opt("account") !is String || payload.opt("url") !is String ||
            payload.getInt("version") != 1 || payload.getString("variant") != variant ||
            payload.getString("route") != route || payload.getString("kind") != kind.name ||
            payload.getString("account") != accountId.localId
        ) throw SourceError.Unsupported("moderation.cursor")
        val page = payload.getString("url").toHttpUrlOrNull() ?: throw SourceError.Unsupported("moderation.cursor")
        return try {
            validateUrl(page.toString(), path, null)
        } catch (_: Exception) {
            throw SourceError.Unsupported("moderation.cursor")
        }
    }

    private fun validateUrl(value: String, path: String, current: String?): HttpUrl {
        val page = value.toHttpUrlOrNull() ?: throw IllegalArgumentException()
        val authenticated = origin.toHttpUrl()
        if (page.scheme != authenticated.scheme || page.host != authenticated.host || page.port != authenticated.port ||
            page.username.isNotEmpty() || page.password.isNotEmpty() || page.fragment != null ||
            page.encodedPath != path || page.toString() == current
        ) throw IllegalArgumentException()
        val names = page.queryParameterNames
        val pagination = listOf("max_id", "since_id", "min_id").filter {
            page.queryParameterValues(it).size == 1 && !page.queryParameter(it).isNullOrBlank()
        }
        if (names.any { it !in setOf("max_id", "since_id", "min_id", "limit") } || pagination.size != 1 ||
            names.any { page.queryParameterValues(it).size != 1 } ||
            ("limit" in names && page.queryParameterValues("limit") != listOf("40"))
        ) throw IllegalArgumentException()
        return page
    }

    private fun encodeCursor(kind: ModerationListKind, variant: String, route: String, value: String) =
        ModerationCursor(accountId, me.foxtails.palustris.domain.ModerationListQuery(kind), variant,
            Base64.getUrlEncoder().withoutPadding().encodeToString(JSONObject().put("version", 1)
                .put("variant", variant).put("route", route).put("kind", kind.name)
                .put("account", accountId.localId).put("url", value).toString().toByteArray(Charsets.UTF_8)))
}
