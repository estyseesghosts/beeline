package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import org.json.JSONArray

internal class MastodonTimelineService(
    private val pageClient: MastodonPageClient,
    private val origin: String,
) {
    suspend fun timeline(
        timeline: Timeline,
        cursor: String?,
        capabilities: ServerCapabilities,
    ): Page<Post> {
        if (timeline !in capabilities.timelines) throw SourceError.Unsupported("timeline:$timeline")
        val route = route(timeline)
        val response = pageClient.getPage(route, cursor)
        val statuses = JSONArray(response.body)
        return Page(
            items = (0 until statuses.length()).map { MastodonMapper.post(statuses.getJSONObject(it), origin) },
            nextCursor = pageClient.nextCursor(response, route, pageClient.currentUrl(route, cursor)),
        )
    }

    fun validateCursor(timeline: Timeline, cursor: String?) {
        pageClient.validateCursor(route(timeline), cursor)
    }

    private fun route(timeline: Timeline) = when (timeline) {
            Timeline.Home -> MastodonPageRoute("timeline:Home", "v1/timelines/home", "/api/v1/timelines/home", timeline.name, "timeline")
            Timeline.Local -> MastodonPageRoute("timeline:Local", "v1/timelines/public?local=true", "/api/v1/timelines/public", timeline.name, "timeline")
            Timeline.Federated -> MastodonPageRoute("timeline:Federated", "v1/timelines/public", "/api/v1/timelines/public", timeline.name, "timeline")
            Timeline.Social, Timeline.Bubble -> throw SourceError.Unsupported("timeline:$timeline")
        }
}
