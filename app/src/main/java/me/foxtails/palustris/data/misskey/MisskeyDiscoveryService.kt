package me.foxtails.palustris.data.misskey

import kotlinx.coroutines.CancellationException
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.hashtags.HashtagSuggestion
import me.foxtails.palustris.domain.hashtags.TrendingHashtag
import org.json.JSONArray
import org.json.JSONObject

/**
 * Discovery reads for one Misskey-family session. Stateless. Every endpoint works without sign-in
 * on the servers checked, so the token only adds the session. [accountId] is the signed-in account,
 * which never appears in the popular accounts. A malformed item is skipped.
 */
internal class MisskeyDiscoveryService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val accountId: AccountId?,
) {
    /** `hashtags/trend` returns the top 10. An empty answer falls back to `hashtags/list`. */
    suspend fun trendingHashtags(limit: Int): List<TrendingHashtag> {
        val trend = call("hashtags/trend", JSONObject()).objects().mapNotNull(MisskeyMapper::trendingHashtag)
        if (trend.isNotEmpty()) return trend.take(limit)
        val body = JSONObject().put("limit", limit.coerceIn(1, MAX_LIST)).put("sort", "+mentionedUsers")
        return call("hashtags/list", body).objects().mapNotNull(MisskeyMapper::trendingHashtag).take(limit)
    }

    suspend fun suggestHashtags(prefix: String, limit: Int): List<HashtagSuggestion> {
        val body = JSONObject().put("query", prefix).put("limit", limit.coerceAtLeast(1))
        val names = call("hashtags/search", body)
        return List(names.length()) { names.optString(it) }.mapNotNull(MisskeyMapper::hashtagSuggestion).take(limit)
    }

    /**
     * Pinned users first, then the most followed local users. One list may fail while the other
     * works, so the answer keeps what it has and rethrows only when both fail.
     */
    suspend fun popularAccounts(limit: Int): List<Account> {
        val pinned = runCatching { call("pinned-users", JSONObject()) }
        val ranked = runCatching {
            val body = JSONObject().put("limit", limit.coerceIn(1, MAX_LIST)).put("sort", "+follower")
                .put("state", "alive").put("origin", "local")
            call("users", body)
        }
        pinned.exceptionOrNull()?.takeIf { it is CancellationException }?.let { throw it }
        ranked.exceptionOrNull()?.takeIf { it is CancellationException }?.let { throw it }
        if (pinned.isFailure && ranked.isFailure) throw pinned.exceptionOrNull()!!
        return (pinned.getOrNull().objects() + ranked.getOrNull().objects())
            .mapNotNull { runCatching { MisskeyMapper.account(it, origin) }.getOrNull() }
            .distinctBy { it.id }
            .filter { it.id != accountId }
            .take(limit)
    }

    private suspend fun call(endpoint: String, body: JSONObject): JSONArray {
        if (token.isNotBlank()) body.put("i", token)
        return JSONArray(api.post(origin, endpoint, body, MISSKEY_MAX_RESPONSE_BYTES).body)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private companion object {
        const val MAX_LIST = 100
    }
}
