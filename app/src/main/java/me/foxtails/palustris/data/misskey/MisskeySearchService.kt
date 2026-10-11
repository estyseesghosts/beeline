package me.foxtails.palustris.data.misskey

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.search.SearchQuery
import me.foxtails.palustris.domain.search.SearchSuggestions
import me.foxtails.palustris.domain.search.Trends
import org.json.JSONArray
import org.json.JSONObject

/**
 * Search and Trends reads for one Misskey-family session. Stateless. Post paging uses `untilId`, so the
 * cursor is the ID of the last post on the page. The caller checks the search policy first.
 */
internal class MisskeySearchService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val discovery: MisskeyDiscoveryService,
) {
    /**
     * Accounts come from `users/search`, or from `users/search-by-username-and-host` for a handle.
     * Hashtags come from `hashtags/search`. One call may fail while the other works.
     */
    suspend fun suggestions(query: SearchQuery): SearchSuggestions {
        val text = query.text.takeIf { it.isNotBlank() } ?: return SearchSuggestions()
        val (accounts, hashtags) = coroutineScope {
            val accountCall = async {
                if (query is SearchQuery.Hashtag) return@async Result.success(emptyList())
                runCatching { accounts(query, text) }
            }
            val tagCall = async {
                if (query is SearchQuery.Handle) return@async Result.success(emptyList())
                runCatching {
                    val body = JSONObject().put("query", text).put("limit", SearchSuggestions.MAX_HASHTAGS)
                    val names = call("hashtags/search", body)
                    List(names.length()) { names.optString(it) }.mapNotNull(MisskeyMapper::hashtagSuggestion)
                }
            }
            accountCall.await() to tagCall.await()
        }
        listOf(accounts, hashtags).forEach { result -> result.exceptionOrNull()?.takeIf { it is CancellationException }?.let { throw it } }
        if (accounts.isFailure && hashtags.isFailure) throw accounts.exceptionOrNull()!!
        return SearchSuggestions(accounts.getOrNull().orEmpty(), hashtags.getOrNull().orEmpty()).limited()
    }

    suspend fun searchPosts(query: String, cursor: String?): Page<Post> {
        val body = JSONObject().put("query", query).put("limit", POST_PAGE)
        cursor?.let { body.put("untilId", it) }
        return postPage(call("notes/search", body))
    }

    suspend fun trends(): Trends = coroutineScope {
        val tags = async { section { discovery.trendingHashtags(TAG_LIMIT) } }
        val posts = async { section { trendingPosts(null) } }
        Trends(tags.await().orEmpty(), posts.await() ?: Page(emptyList()))
    }

    suspend fun trendingPosts(cursor: String?): Page<Post> {
        val body = JSONObject().put("limit", POST_PAGE)
        cursor?.let { body.put("untilId", it) }
        return postPage(call("notes/featured", body))
    }

    /** The account behind a profile address. A local profile needs no federation lookup. */
    suspend fun resolveAccount(url: String): Account? {
        LOCAL_PROFILE.find(url)?.takeIf { url.startsWith("$origin/@") }?.let { match ->
            val body = JSONObject().put("username", match.groupValues[1])
            match.groupValues[2].takeIf { it.isNotBlank() }?.let { body.put("host", it) }
            return MisskeyMapper.account(JSONObject(raw("users/show", body)), origin)
        }
        val json = JSONObject(raw("ap/show", JSONObject().put("uri", url)))
        if (json.optString("type") != "User") return null
        return json.optJSONObject("object")?.let { MisskeyMapper.account(it, origin) }
    }

    private suspend fun accounts(query: SearchQuery, text: String): List<Account> {
        val body = JSONObject().put("limit", SearchSuggestions.MAX_ACCOUNTS)
        val endpoint = if (query is SearchQuery.Handle) {
            val parts = text.split('@')
            body.put("username", parts[0])
            parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { body.put("host", it) }
            "users/search-by-username-and-host"
        } else {
            body.put("query", text)
            "users/search"
        }
        return call(endpoint, body).objects().mapNotNull { runCatching { MisskeyMapper.account(it, origin) }.getOrNull() }
    }

    private fun postPage(notes: JSONArray): Page<Post> {
        val posts = notes.objects().mapNotNull { runCatching { MisskeyMapper.post(it, origin) }.getOrNull() }
        val lastId = notes.objects().lastOrNull()?.optString("id")?.takeIf { it.isNotBlank() }
        // A page can come back short when notes are filtered, so only an empty page ends the list.
        return Page(posts, lastId)
    }

    private suspend fun <T> section(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun raw(endpoint: String, body: JSONObject): String {
        if (token.isNotBlank()) body.put("i", token)
        return api.post(origin, endpoint, body, MISSKEY_MAX_RESPONSE_BYTES).body
    }

    private suspend fun call(endpoint: String, body: JSONObject): JSONArray = JSONArray(raw(endpoint, body))

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private companion object {
        const val POST_PAGE = 20
        const val TAG_LIMIT = 10

        /** `/@name` or `/@name@host`. */
        val LOCAL_PROFILE = Regex("^https?://[^/]+/@([A-Za-z0-9_.-]+)(?:@([A-Za-z0-9.-]+))?/?$")
    }
}
