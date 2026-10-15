package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.timelineStatus
import org.json.JSONArray
import org.json.JSONObject

internal class MisskeyTimelineService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val maxResponseBytes: Long = MISSKEY_MAX_RESPONSE_BYTES,
) {
    suspend fun timeline(timeline: Timeline, cursor: String?, capabilities: ServerCapabilities): Page<Post> {
        when (capabilities.timelineStatus(timeline)) {
            CapabilityStatus.Supported -> Unit
            CapabilityStatus.Denied -> throw SourceError.AccessDenied("timeline:$timeline")
            CapabilityStatus.Unsupported -> throw SourceError.Unsupported("timeline:$timeline")
            CapabilityStatus.TemporarilyUnavailable -> throw SourceError.ServerError("timeline:$timeline")
            CapabilityStatus.Unknown -> throw SourceError.Unsupported("timeline:$timeline")
        }
        val params = JSONObject().put("i", token).put("limit", 30)
        if (cursor != null) params.put("untilId", cursor)
        val endpoint = when (timeline) {
            Timeline.Home -> "notes/timeline"
            Timeline.Local -> "notes/local-timeline"
            Timeline.Social -> "notes/hybrid-timeline"
            // DVD recommendations have a dedicated service; this generic path never serves them.
            Timeline.Recommended -> throw SourceError.Unsupported("timeline:$timeline")
            Timeline.Bubble -> "notes/bubble-timeline"
            Timeline.Federated -> "notes/global-timeline"
        }
        // Generic timelines stay unfiltered. Upstream Misskey and Catodon both default
        // withFiles to false, and Catodon's own client only sends it for an explicit
        // media filter, so false keeps the wire shape stable across servers.
        params.put("withFiles", false)
        val notes = JSONArray(api.post(origin, endpoint, params, maxResponseBytes).body)
        val raw = (0 until notes.length()).mapNotNull { notes.optJSONObject(it) }
        return Page(
            // One malformed note must not fail the whole page. The cursor still reads
            // the last wire item so paging advances past poison notes instead of refetching them.
            raw.mapNotNull {
                try {
                    MisskeyMapper.post(it, origin)
                } catch (_: Exception) {
                    null
                }
            },
            // Misskey paginates by the outer renote ID, not the displayed original note.
            raw.lastOrNull()?.optString("id")?.takeIf { it.isNotBlank() },
        )
    }
}
