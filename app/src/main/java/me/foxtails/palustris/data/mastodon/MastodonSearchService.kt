package me.foxtails.palustris.data.mastodon

import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import me.foxtails.palustris.data.transport.AuthenticatedHttpClient
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.search.SearchQuery
import me.foxtails.palustris.domain.search.SearchSuggestions
import me.foxtails.palustris.domain.search.TrendingLink
import me.foxtails.palustris.domain.search.Trends
import org.json.JSONArray
import org.json.JSONObject

/**
 * Search and Trends reads for one Mastodon session. Stateless. Post paging uses the `offset` of
 * `v2/search` and `v1/trends/statuses`, and the cursor is that offset as text. A malformed item is
 * skipped, and a Trends section the server refuses is empty.
 */
internal class MastodonSearchService(
    private val origin: String,
    private val token: String,
    private val api: AuthenticatedHttpClient,
) {
    /**
     * Two calls: accounts, then hashtags. `resolve` is sent only for a full handle, so a remote account
     * is fetched only when the user typed all of it. One call may fail while the other works.
     */
    suspend fun suggestions(query: SearchQuery): SearchSuggestions {
        val text = query.text.takeIf { it.isNotBlank() } ?: return SearchSuggestions()
        val encoded = enc(text)
        val resolve = (query as? SearchQuery.Handle)?.full == true
        val (accounts, hashtags) = coroutineScope {
            val accountCall = async {
                if (query is SearchQuery.Hashtag) return@async Result.success(emptyList())
                runCatching {
                    val path = "api/v2/search?q=$encoded&type=accounts&limit=${SearchSuggestions.MAX_ACCOUNTS}" +
                        if (resolve) "&resolve=true" else ""
                    get(path).optJSONArray("accounts").objects().mapNotNull(::account)
                }
            }
            val tagCall = async {
                if (query is SearchQuery.Handle) return@async Result.success(emptyList())
                runCatching {
                    val path = "api/v2/search?q=$encoded&type=hashtags&limit=${SearchSuggestions.MAX_HASHTAGS}"
                    get(path).optJSONArray("hashtags").objects().mapNotNull(MastodonMapper::hashtagSuggestion)
                }
            }
            accountCall.await() to tagCall.await()
        }
        listOf(accounts, hashtags).forEach { result -> result.exceptionOrNull()?.takeIf { it is CancellationException }?.let { throw it } }
        if (accounts.isFailure && hashtags.isFailure) throw accounts.exceptionOrNull()!!
        return SearchSuggestions(accounts.getOrNull().orEmpty(), hashtags.getOrNull().orEmpty()).limited()
    }

    suspend fun searchPosts(query: String, cursor: String?): Page<Post> {
        val offset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val path = "api/v2/search?q=${enc(query)}&type=statuses&limit=$POST_PAGE&offset=$offset"
        val posts = get(path).optJSONArray("statuses").objects().mapNotNull(::post)
        // A page can come back short when posts are filtered, so only an empty page ends the list.
        return Page(posts, if (posts.isNotEmpty()) (offset + posts.size).toString() else null)
    }

    suspend fun trends(): Trends = coroutineScope {
        val tags = async { section { JSONArray(raw("api/v1/trends/tags?limit=$TAG_LIMIT")).objects().mapNotNull(MastodonMapper::trendingHashtag) } }
        val posts = async { section { trendingPosts(null) } }
        val links = async { section { JSONArray(raw("api/v1/trends/links?limit=$LINK_LIMIT")).objects().mapNotNull(::link) } }
        Trends(tags.await().orEmpty(), posts.await() ?: Page(emptyList()), links.await().orEmpty())
    }

    suspend fun trendingPosts(cursor: String?): Page<Post> {
        val offset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val body = raw("api/v1/trends/statuses?limit=$TRENDING_POST_PAGE&offset=$offset")
        val posts = JSONArray(body).objects().mapNotNull(::post)
        return Page(posts, if (posts.isNotEmpty()) (offset + posts.size).toString() else null)
    }

    /** The account behind a profile address. `resolve` makes the server fetch a remote one. */
    suspend fun resolveAccount(url: String): Account? =
        get("api/v2/search?q=${enc(url)}&resolve=true&type=accounts&limit=1").optJSONArray("accounts").objects()
            .firstNotNullOfOrNull(::account)

    private suspend fun <T> section(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun raw(path: String): String = api.get(origin, path, token, MASTODON_MAX_RESPONSE_BYTES).body

    private suspend fun get(path: String): JSONObject = JSONObject(raw(path))

    private fun account(json: JSONObject): Account? = runCatching { MastodonMapper.account(json, origin) }.getOrNull()

    private fun post(json: JSONObject): Post? = runCatching { MastodonMapper.post(json, origin) }.getOrNull()

    private fun link(json: JSONObject): TrendingLink? {
        val url = json.optString("url").takeIf { it.startsWith("http") } ?: return null
        val title = json.optString("title").takeIf { it.isNotBlank() } ?: return null
        fun text(key: String) = if (json.isNull(key)) null else json.optString(key).takeIf { it.isNotBlank() }
        return TrendingLink(url, title, text("description"), text("provider_name"), text("image"))
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val POST_PAGE = 20
        const val TRENDING_POST_PAGE = 20
        const val TAG_LIMIT = 10
        const val LINK_LIMIT = 10
    }
}
