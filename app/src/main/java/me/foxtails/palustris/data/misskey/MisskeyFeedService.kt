package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lists and antennas for one Misskey-family session. Stateless. Both timelines page with `untilId`, so the
 * cursor is the ID of the last post on the page, and only an empty page ends the list.
 */
internal class MisskeyFeedService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
) {
    suspend fun lists(): List<FeedSource.UserList> = call("users/lists/list", JSONObject()).objects().mapNotNull { item ->
        val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        FeedSource.UserList(id, item.optString("name").ifBlank { id })
    }

    suspend fun antennas(): List<FeedSource.Antenna> = call("antennas/list", JSONObject()).objects().mapNotNull { item ->
        val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        FeedSource.Antenna(id, item.optString("name").ifBlank { id })
    }

    suspend fun listTimeline(id: String, cursor: String?): Page<Post> =
        postPage(call("notes/user-list-timeline", pageBody("listId", id, cursor)))

    suspend fun antennaTimeline(id: String, cursor: String?): Page<Post> =
        postPage(call("antennas/notes", pageBody("antennaId", id, cursor)))

    private fun pageBody(idField: String, id: String, cursor: String?): JSONObject {
        val body = JSONObject().put(idField, id).put("limit", PAGE)
        cursor?.let { body.put("untilId", it) }
        return body
    }

    private fun postPage(notes: JSONArray): Page<Post> {
        val posts = notes.objects().mapNotNull { runCatching { MisskeyMapper.post(it, origin) }.getOrNull() }
        val lastId = notes.objects().lastOrNull()?.optString("id")?.takeIf { it.isNotBlank() }
        return Page(posts, lastId)
    }

    private suspend fun call(endpoint: String, body: JSONObject): JSONArray {
        if (token.isNotBlank()) body.put("i", token)
        return JSONArray(api.post(origin, endpoint, body, MISSKEY_MAX_RESPONSE_BYTES).body)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    private companion object {
        const val PAGE = 30
    }
}
