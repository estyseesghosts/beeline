package me.foxtails.palustris.data.misskey

import java.time.Instant
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.PollRequest
import me.foxtails.palustris.domain.ScheduleRoute
import me.foxtails.palustris.domain.ScheduledPost
import me.foxtails.palustris.domain.SourceError
import org.json.JSONArray
import org.json.JSONObject

/**
 * Holds, lists, moves and cancels scheduled notes on the Misskey family.
 *
 * Responsibility: the two server routes. Sharkey uses the `notes/schedule` routes, which has no update route, so a
 * move cancels the note and creates it again. Misskey uses the `notes/drafts` routes with `isActuallyScheduled`;
 * those are server-side drafts, kept apart from Beeline's own encrypted drafts, and only the scheduled ones
 * are listed. Lifetime: the source of one session creates it and keeps it as long as the source lives.
 */
internal class MisskeyScheduleService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
) {
    suspend fun create(route: ScheduleRoute, note: JSONObject, request: CreatePostRequest): ScheduledPost {
        val at = request.scheduledAtEpochMillis ?: throw SourceError.Unsupported("schedule.time")
        val body = JSONObject(note.toString())
        val response = when (route) {
            ScheduleRoute.Sharkey -> {
                body.put("scheduleNote", JSONObject().put("scheduledAt", at))
                api.post(origin, "notes/schedule/create", body, MISSKEY_MAX_RESPONSE_BYTES)
            }
            ScheduleRoute.MisskeyDrafts -> {
                body.put("scheduledAt", at).put("isActuallyScheduled", true)
                api.post(origin, "notes/drafts/create", body, MISSKEY_MAX_RESPONSE_BYTES)
            }
            ScheduleRoute.Mastodon -> throw SourceError.Unsupported("schedule")
        }
        val json = response.body.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
        // The create answer may hold the new item under one of several names. The ID is only needed to cancel it later.
        val id = json?.let { it.optString("id").ifBlank { null } ?: it.optJSONObject("createdDraft")?.optString("id") ?: it.optJSONObject("draft")?.optString("id") }.orEmpty()
        return ScheduledPost(
            id = id,
            scheduledAtEpochMillis = at,
            text = request.text,
            audience = request.audience,
            contentWarning = request.contentWarning,
            attachmentCount = request.attachments.size,
            hasPoll = request.poll != null,
            request = request,
        )
    }

    suspend fun list(route: ScheduleRoute): List<ScheduledPost> {
        val items = when (route) {
            ScheduleRoute.Sharkey -> JSONArray(api.post(origin, "notes/schedule/list", JSONObject().put("i", token).put("limit", LIST_LIMIT), MISSKEY_MAX_RESPONSE_BYTES).body)
            ScheduleRoute.MisskeyDrafts -> JSONArray(
                api.post(origin, "notes/drafts/list", JSONObject().put("i", token).put("limit", LIST_LIMIT).put("scheduled", true), MISSKEY_MAX_RESPONSE_BYTES).body,
            )
            ScheduleRoute.Mastodon -> throw SourceError.Unsupported("schedule")
        }
        return (0 until items.length()).mapNotNull { index -> items.optJSONObject(index)?.let(::scheduledPost) }
            .filter { route != ScheduleRoute.MisskeyDrafts || it.scheduledAtEpochMillis > 0 }
            .sortedBy(ScheduledPost::scheduledAtEpochMillis)
    }

    suspend fun cancel(route: ScheduleRoute, id: String) {
        when (route) {
            ScheduleRoute.Sharkey -> api.post(origin, "notes/schedule/delete", JSONObject().put("i", token).put("noteId", id), MISSKEY_MAX_RESPONSE_BYTES)
            ScheduleRoute.MisskeyDrafts -> api.post(origin, "notes/drafts/delete", JSONObject().put("i", token).put("draftId", id), MISSKEY_MAX_RESPONSE_BYTES)
            ScheduleRoute.Mastodon -> throw SourceError.Unsupported("schedule")
        }
    }

    /**
     * Moves an item. Misskey updates the draft in place. Sharkey has no update route: it cancels the note and
     * schedules it again from the request the list rebuilt, and a failed second step reports the error with the
     * original already gone, so the caller reloads the list.
     */
    suspend fun reschedule(route: ScheduleRoute, item: ScheduledPost, atEpochMillis: Long, note: (CreatePostRequest) -> JSONObject): ScheduledPost {
        val request = item.request ?: throw SourceError.Unsupported("schedule.update")
        val moved = request.copy(scheduledAtEpochMillis = atEpochMillis)
        return when (route) {
            ScheduleRoute.MisskeyDrafts -> {
                val body = JSONObject(note(moved).toString()).put("draftId", item.id).put("scheduledAt", atEpochMillis).put("isActuallyScheduled", true)
                api.post(origin, "notes/drafts/update", body, MISSKEY_MAX_RESPONSE_BYTES)
                item.copy(scheduledAtEpochMillis = atEpochMillis, request = moved)
            }
            ScheduleRoute.Sharkey -> {
                cancel(route, item.id)
                create(route, note(moved), moved)
            }
            ScheduleRoute.Mastodon -> throw SourceError.Unsupported("schedule")
        }
    }

    /** Reads one scheduled item. Sharkey wraps the note in `note`; Misskey's draft is the note itself. */
    private fun scheduledPost(json: JSONObject): ScheduledPost? {
        val note = json.optJSONObject("note") ?: json
        val id = json.optString("id").ifBlank { note.optString("id") }.ifBlank { return null }
        val at = timeOf(json.opt("scheduledAt")) ?: timeOf(note.opt("scheduledAt")) ?: 0L
        val files = note.optJSONArray("files")
        val attachments = (0 until (files?.length() ?: 0)).mapNotNull { files?.optJSONObject(it) }.map { file ->
            Attachment(url = file.optString("url").ifBlank { null }, mimeType = file.optString("type", "application/octet-stream"), id = file.optString("id").ifBlank { null })
        }
        val poll = note.optJSONObject("poll")?.let { pollJson ->
            val choices = pollJson.optJSONArray("choices")
            PollRequest(
                choices = (0 until (choices?.length() ?: 0)).map { index ->
                    choices!!.opt(index).let { choice -> (choice as? JSONObject)?.optString("text") ?: choice.toString() }
                },
                multiple = pollJson.optBoolean("multiple"),
            )
        }
        val audience = when (note.optString("visibility")) {
            "home" -> Audience.Unlisted
            "followers" -> Audience.Followers
            "specified" -> Audience.Direct
            else -> Audience.Public
        }
        val text = note.optString("text").takeUnless { note.isNull("text") }.orEmpty()
        val cw = note.optString("cw").takeUnless { note.isNull("cw") }?.ifBlank { null }
        val request = CreatePostRequest(
            text = text,
            audience = audience,
            contentWarning = cw,
            replyTo = note.optString("replyId").takeUnless { note.isNull("replyId") || it.isBlank() }?.let { EntityId(origin, it) },
            attachments = attachments,
            poll = poll,
            quoteOf = note.optString("renoteId").takeUnless { note.isNull("renoteId") || it.isBlank() }?.let { EntityId(origin, it) },
            localOnly = note.optBoolean("localOnly"),
            scheduledAtEpochMillis = at,
        )
        return ScheduledPost(
            id = id,
            scheduledAtEpochMillis = at,
            text = text,
            audience = audience,
            contentWarning = cw,
            attachmentCount = attachments.size,
            hasPoll = poll != null,
            request = request,
        )
    }

    /** A schedule time as the server sends it: epoch milliseconds, or an ISO string. */
    private fun timeOf(value: Any?): Long? = when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
        else -> null
    }

    private companion object {
        const val LIST_LIMIT = 30
    }
}
