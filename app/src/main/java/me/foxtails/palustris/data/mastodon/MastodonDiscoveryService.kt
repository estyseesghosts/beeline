package me.foxtails.palustris.data.mastodon

import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import me.foxtails.palustris.data.transport.AuthenticatedHttpClient
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.hashtags.HashtagSuggestion
import me.foxtails.palustris.domain.hashtags.TrendingHashtag
import org.json.JSONArray
import org.json.JSONObject

/**
 * Discovery reads for one Mastodon session: trending hashtags, hashtag prefix suggestions and popular
 * accounts. Stateless. Each call works on demand, with no capability probe. A malformed item is
 * skipped, so one bad entry never hides the list.
 */
internal class MastodonDiscoveryService(
    private val origin: String,
    private val token: String,
    private val api: AuthenticatedHttpClient,
) {
    /** `trends/tags` accepts at most 20. */
    suspend fun trendingHashtags(limit: Int): List<TrendingHashtag> {
        val count = limit.coerceIn(1, MAX_TRENDING)
        val body = api.get(origin, "api/v1/trends/tags?limit=$count", token, MASTODON_MAX_RESPONSE_BYTES).body
        return JSONArray(body).objects().mapNotNull(MastodonMapper::trendingHashtag).take(limit)
    }

    suspend fun suggestHashtags(prefix: String, limit: Int): List<HashtagSuggestion> {
        val encoded = URLEncoder.encode(prefix, Charsets.UTF_8.name())
        val path = "api/v2/search?q=$encoded&type=hashtags&limit=${limit.coerceAtLeast(1)}"
        val hashtags = JSONObject(api.get(origin, path, token, MASTODON_MAX_RESPONSE_BYTES).body).optJSONArray("hashtags")
        return hashtags.objects().mapNotNull(MastodonMapper::hashtagSuggestion).take(limit)
    }

    /**
     * `v2/suggestions` needs the read scope and fails on older servers. Any failure falls back to the
     * local directory, which works without sign-in.
     */
    suspend fun popularAccounts(limit: Int): List<Account> {
        val count = limit.coerceIn(1, MAX_ACCOUNTS)
        return try {
            val body = api.get(origin, "api/v2/suggestions?limit=$count", token, MASTODON_MAX_RESPONSE_BYTES).body
            JSONArray(body).objects().mapNotNull { it.optJSONObject("account") }.mapNotNull(::account)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            val path = "api/v1/directory?order=active&local=true&limit=$count"
            JSONArray(api.get(origin, path, token, MASTODON_MAX_RESPONSE_BYTES).body).objects().mapNotNull(::account)
        }.distinctBy { it.id }.take(limit)
    }

    private fun account(json: JSONObject) = runCatching { MastodonMapper.account(json, origin) }.getOrNull()

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private companion object {
        const val MAX_TRENDING = 20
        const val MAX_ACCOUNTS = 40
    }
}
