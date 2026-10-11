package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.data.transport.AuthenticatedHttpClient
import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import org.json.JSONArray

/**
 * Lists and followed hashtags for one Mastodon session. Stateless. List timelines page through the `Link`
 * header like every other timeline, and the followed-tags read follows the same header for a few pages.
 */
internal class MastodonFeedService(
    private val origin: String,
    private val token: String,
    private val api: AuthenticatedHttpClient,
    private val pageClient: MastodonPageClient,
) {
    suspend fun lists(): List<FeedSource.UserList> {
        val array = JSONArray(api.get(origin, "api/v1/lists", token, MASTODON_MAX_RESPONSE_BYTES).body)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            FeedSource.UserList(id, item.optString("title").ifBlank { id })
        }
    }

    suspend fun listTimeline(id: String, cursor: String?): Page<Post> {
        val encoded = id.encodeMastodonPathSegment()
        val route = MastodonPageRoute("list", "v1/timelines/list/$encoded?limit=40", "/api/v1/timelines/list/$encoded", id, "list")
        val currentUrl = pageClient.currentUrl(route, cursor)
        val response = pageClient.getPage(route, cursor)
        val statuses = JSONArray(response.body)
        return Page(
            items = (0 until statuses.length()).map { MastodonMapper.post(statuses.getJSONObject(it), origin) },
            nextCursor = pageClient.nextCursor(response, route, currentUrl),
        )
    }

    /** Tag names without `#`, in server order. A page that fails after the first ends the list. */
    suspend fun followedTags(): List<String> {
        val route = MastodonPageRoute("followed_tags", "v1/followed_tags?limit=200", "/api/v1/followed_tags", "followed", "followed")
        val names = mutableListOf<String>()
        var cursor: String? = null
        repeat(MAX_TAG_PAGES) { page ->
            val currentUrl = pageClient.currentUrl(route, cursor)
            val response = try {
                pageClient.getPage(route, cursor)
            } catch (e: Exception) {
                if (page == 0) throw e
                return names
            }
            val array = JSONArray(response.body)
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.optString("name")?.takeIf { it.isNotBlank() }?.let(names::add)
            }
            cursor = runCatching { pageClient.nextCursor(response, route, currentUrl) }.getOrNull() ?: return names
        }
        return names
    }

    suspend fun followTag(tag: String) {
        api.post(origin, "api/v1/tags/${tag.encodeMastodonPathSegment()}/follow", "{}", token, MASTODON_MAX_RESPONSE_BYTES)
    }

    suspend fun unfollowTag(tag: String) {
        api.post(origin, "api/v1/tags/${tag.encodeMastodonPathSegment()}/unfollow", "{}", token, MASTODON_MAX_RESPONSE_BYTES)
    }

    private companion object {
        const val MAX_TAG_PAGES = 5
    }
}
