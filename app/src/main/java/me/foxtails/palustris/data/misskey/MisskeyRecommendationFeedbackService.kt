package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.SourceError
import org.json.JSONArray
import org.json.JSONObject

/**
 * DVD Sharkey recommendations feedback for one signed-in session. Stateless: every call carries
 * its own payload, and no page state or retry queue sits beside the source cache.
 * Responsibility: best-effort `notes/recommendations/dwell` and `notes/recommendations/not-interested`
 * transport with DVD gating. Lifetime: bound to its [MisskeySource] session; holds nothing between calls.
 */
internal class MisskeyRecommendationFeedbackService(
    private val origin: String,
    private val token: String?,
    private val api: MisskeyApi,
    private val accountId: AccountId?,
    private val maxResponseBytes: Long = MISSKEY_MAX_RESPONSE_BYTES,
) {
    /**
     * Reports accumulated visible milliseconds per served note. Entries with no positive dwell
     * are dropped, each value is capped, and the payload goes out in chunks of at most 50 with
     * exactly one attempt per chunk: other failures are silent and never retried. Only a
     * permission failure escapes, as [SourceError.AccessDenied], so a denied call can never read
     * as an unsupported feature. Logs carry no secrets or bodies; the token travels only in the
     * request body the transport owns.
     */
    suspend fun reportDwell(entries: Map<EntityId, Long>) {
        val credential = credential("recommendations.dwell")
        val sanitized = entries.mapNotNull { (id, dwellMillis) ->
            if (id.connection != origin || id.value.isBlank()) return@mapNotNull null
            val capped = dwellMillis.coerceIn(0L, MAX_DWELL_MILLIS)
            if (capped <= 0L) return@mapNotNull null
            id.value to capped
        }
        if (sanitized.isEmpty()) return
        sanitized.chunked(MAX_DWELL_CHUNK).forEach { chunk ->
            val body = JSONObject()
                .put("i", credential)
                .put("notes", JSONArray(chunk.map { (noteId, dwellMillis) ->
                    JSONObject().put("id", noteId).put("dwell", dwellMillis)
                }))
            // Best-effort dwell shape: Sharkey takes per-note dwell milliseconds beside the note
            // id. Live dvd.chat verification still needs explicit user approval, so a rejected
            // shape stays silent here instead of retrying.
            postFeedback(DWELL_ENDPOINT, body, "recommendations.dwell")
        }
    }

    /**
     * Tells the ranker the served note holds no interest. The caller removes the row locally
     * before this call, so the network result never gates the UI. Same error policy as dwell:
     * permission failures throw [SourceError.AccessDenied]; everything else is silent.
     */
    suspend fun markNotInterested(noteId: EntityId) {
        val credential = credential("recommendations.not-interested")
        if (noteId.connection != origin || noteId.value.isBlank()) {
            throw SourceError.ForeignOrigin("recommendations.not-interested")
        }
        val body = JSONObject()
            .put("i", credential)
            .put("noteId", noteId.value)
        postFeedback(NOT_INTERESTED_ENDPOINT, body, "recommendations.not-interested")
    }

    /**
     * DVD-gated credential: the `dvd.chat` host plus a signed-in token plus the owning account,
     * checked before any network use. Anonymous and foreign sessions fail here, never at the server.
     */
    private fun credential(feature: String): String {
        if (!isDvdSharkeyHost(origin)) throw SourceError.Unsupported(feature)
        if (accountId != null && accountId.connection.origin != origin) throw SourceError.ForeignOrigin(feature)
        return token?.takeIf(String::isNotBlank) ?: throw SourceError.AccessDenied(feature)
    }

    /**
     * One feedback POST with exactly one attempt. A permission failure maps to
     * [SourceError.AccessDenied] without touching capabilities, so the feature never reads as
     * unsupported after one denied call. Any other failure is silent: feedback never retries and
     * never invalidates the capability snapshot.
     */
    private suspend fun postFeedback(endpoint: String, body: JSONObject, feature: String) {
        try {
            api.post(origin, endpoint, body, maxResponseBytes)
        } catch (error: ApiFailure) {
            val code = error.code?.uppercase()
            if (error.status == 401 || error.status == 403 || code in PERMISSION_CODES) {
                throw SourceError.AccessDenied(feature)
            }
            // Silent: no retry storms, no capability churn, no bodies in logs.
        }
    }

    private companion object {
        const val DWELL_ENDPOINT = "notes/recommendations/dwell"
        const val NOT_INTERESTED_ENDPOINT = "notes/recommendations/not-interested"

        /** The tracker reports at most this many notes per dwell call. */
        const val MAX_DWELL_CHUNK = 50

        /** One hour in milliseconds: a single note can never accrue more than this per report. */
        const val MAX_DWELL_MILLIS = 3_600_000L

        /** Server permission failures that deny feedback without demoting the feature. */
        val PERMISSION_CODES = setOf("PERMISSION_DENIED", "FORBIDDEN", "ACCESS_DENIED")
    }
}
