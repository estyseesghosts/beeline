package me.foxtails.palustris.data.mastodon

import java.time.Instant
import me.foxtails.palustris.data.transport.AuthenticatedHttpClient
import me.foxtails.palustris.data.transport.HttpStatusFailure
import me.foxtails.palustris.domain.ContentFilter
import me.foxtails.palustris.domain.ContentFilterDraft
import me.foxtails.palustris.domain.ContentFilterInput
import me.foxtails.palustris.domain.ContentFilterKind
import me.foxtails.palustris.domain.ContentFilterSet
import me.foxtails.palustris.domain.FilterPattern
import me.foxtails.palustris.domain.PostFilterAction
import me.foxtails.palustris.domain.SourceError
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads and edits the filters of one Mastodon-compatible account. It lives as long as its source.
 *
 * Mastodon 4.0 and later has titled v2 filters, matched by the server. Pleroma, Akkoma, and older servers have
 * only v1 filters: one phrase each, matched by the client. The service asks for v2 first. A 404 there selects
 * v1 for the life of this service; any other failure stays a failure and selects nothing.
 */
class MastodonFilterService(
    private val origin: String,
    private val token: String,
    private val api: AuthenticatedHttpClient,
) {
    @Volatile private var kind: ContentFilterKind? = null

    suspend fun filters(): ContentFilterSet = when (kind) {
        ContentFilterKind.Titled -> ContentFilterSet(ContentFilterKind.Titled, v2Filters(fetchV2()))
        ContentFilterKind.Phrase -> ContentFilterSet(ContentFilterKind.Phrase, v1Filters())
        // The service only ever selects Titled or Phrase.
        null, ContentFilterKind.WordMute -> try {
            val body = fetchV2()
            kind = ContentFilterKind.Titled
            ContentFilterSet(ContentFilterKind.Titled, v2Filters(body))
        } catch (failure: HttpStatusFailure) {
            if (failure.status != 404) throw failure
            kind = ContentFilterKind.Phrase
            ContentFilterSet(ContentFilterKind.Phrase, v1Filters())
        }
    }

    /** Creates a filter when [id] is null, or replaces the filter [id]. Returns the fresh set. */
    suspend fun save(id: String?, draft: ContentFilterDraft): ContentFilterSet {
        require(draft.patterns.isNotEmpty() && draft.contexts.isNotEmpty())
        when (resolveKind()) {
            ContentFilterKind.Phrase -> {
                val fields = v1Fields(draft)
                if (id == null) api.postForm(origin, "api/v1/filters", fields, token, MASTODON_MAX_RESPONSE_BYTES)
                else api.putForm(origin, path("api/v1/filters", id), fields, token, MASTODON_MAX_RESPONSE_BYTES)
            }
            ContentFilterKind.WordMute -> throw SourceError.Unsupported("moderation.filters.save")
            ContentFilterKind.Titled -> if (id == null) {
                api.postForm(origin, "api/v2/filters", v2Fields(draft, current = null), token, MASTODON_MAX_RESPONSE_BYTES)
            } else {
                // Read the filter again, so the keyword IDs are the server's current ones. A filter deleted
                // elsewhere is missing here, and the server answers the update with 404.
                val current = v2Filters(fetchV2()).firstOrNull { it.id == id }
                api.putForm(origin, path("api/v2/filters", id), v2Fields(draft, current), token, MASTODON_MAX_RESPONSE_BYTES)
            }
        }
        return filters()
    }

    suspend fun delete(id: String): ContentFilterSet {
        val route = if (resolveKind() == ContentFilterKind.Phrase) "api/v1/filters" else "api/v2/filters"
        api.delete(origin, path(route, id), token, MASTODON_MAX_RESPONSE_BYTES)
        return filters()
    }

    private suspend fun resolveKind(): ContentFilterKind = kind ?: filters().kind

    private suspend fun fetchV2(): String = api.get(origin, "api/v2/filters", token, MASTODON_MAX_RESPONSE_BYTES).body

    private suspend fun v1Filters(): List<ContentFilter> {
        val values = JSONArray(api.get(origin, "api/v1/filters", token, MASTODON_MAX_RESPONSE_BYTES).body)
        return (0 until values.length()).mapNotNull { index -> values.optJSONObject(index)?.let(::v1Filter) }
    }

    private fun path(route: String, id: String): String = origin.toHttpUrl().newBuilder()
        .addPathSegments(route)
        .addPathSegment(id)
        .build()
        .encodedPath
        .removePrefix("/")

    internal companion object {
        fun v2Filters(body: String): List<ContentFilter> {
            val values = JSONArray(body)
            return (0 until values.length()).mapNotNull { index -> values.optJSONObject(index)?.let(::v2Filter) }
        }

        /** Mastodon matches keywords without regard to case. */
        fun v2Filter(json: JSONObject): ContentFilter? {
            val id = json.optString("id").takeIf(String::isNotBlank) ?: return null
            val keywords = json.optJSONArray("keywords") ?: JSONArray()
            val entries = (0 until keywords.length()).mapNotNull { keywords.optJSONObject(it) }
                .filter { it.optString("keyword").isNotEmpty() }
            return ContentFilter(
                id = id,
                title = json.optString("title"),
                patterns = entries.map { FilterPattern.Words(listOf(it.optString("keyword")), wholeWord = it.optBoolean("whole_word")) },
                patternIds = entries.map { it.optString("id").takeIf(String::isNotBlank) },
                action = MastodonFilterMapper.action(json.optString("filter_action")),
                contexts = MastodonFilterMapper.contexts(json.optJSONArray("context")),
                expiresAtEpochMillis = expiry(json),
            )
        }

        /** An irreversible v1 filter removes; the server already drops its matches from Home and notifications. */
        fun v1Filter(json: JSONObject): ContentFilter? {
            val id = json.optString("id").takeIf(String::isNotBlank) ?: return null
            val phrase = json.optString("phrase").takeIf(String::isNotEmpty) ?: return null
            return ContentFilter(
                id = id,
                title = phrase,
                patterns = listOf(FilterPattern.Words(listOf(phrase), wholeWord = json.optBoolean("whole_word"))),
                patternIds = listOf(id),
                action = if (json.optBoolean("irreversible")) PostFilterAction.Remove else PostFilterAction.Collapse,
                contexts = MastodonFilterMapper.contexts(json.optJSONArray("context")),
                expiresAtEpochMillis = expiry(json),
            )
        }

        private fun expiry(json: JSONObject): Long? = json.optString("expires_at").takeIf { it.isNotBlank() && it != "null" }
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

        /** A filter without expiry sends no `expires_in`: Pleroma's schema rejects an empty value. */
        fun v1Fields(draft: ContentFilterDraft): List<Pair<String, String>> {
            val pattern = draft.patterns.single() as? FilterPattern.Words ?: throw SourceError.InvalidInput(ContentFilterInput.EXPRESSION)
            return buildList {
                add("phrase" to pattern.words.single())
                add("whole_word" to pattern.wholeWord.toString())
                add("irreversible" to (draft.action == PostFilterAction.Remove).toString())
                draft.contexts.forEach { add("context[]" to MastodonFilterMapper.contextName(it)) }
                draft.expiresInSeconds?.let { add("expires_in" to it.toString()) }
            }
        }

        /**
         * Kept keywords keep their IDs. Removed keywords get `_destroy`. New keywords carry no ID. The arrays use
         * indexed keys, because Rails pairs `keywords_attributes[][...]` fields by order and a missing field shifts them.
         * An empty `expires_in` clears the expiry; Mastodon reads it as no expiry.
         */
        fun v2Fields(draft: ContentFilterDraft, current: ContentFilter?): List<Pair<String, String>> = buildList {
            add("title" to draft.title)
            add("filter_action" to if (draft.action == PostFilterAction.Remove) "hide" else "warn")
            draft.contexts.forEach { add("context[]" to MastodonFilterMapper.contextName(it)) }
            add("expires_in" to (draft.expiresInSeconds?.toString() ?: ""))
            val existing = current?.patterns.orEmpty().zip(current?.patternIds.orEmpty())
            val kept = mutableSetOf<String>()
            var index = 0
            draft.patterns.filterIsInstance<FilterPattern.Words>().forEach { pattern ->
                val keyword = pattern.words.single()
                val match = existing.firstOrNull { (old, id) -> id != null && id !in kept && (old as? FilterPattern.Words)?.words?.single() == keyword }
                match?.second?.let { id ->
                    kept += id
                    add("keywords_attributes[$index][id]" to id)
                }
                add("keywords_attributes[$index][keyword]" to keyword)
                add("keywords_attributes[$index][whole_word]" to pattern.wholeWord.toString())
                index += 1
            }
            existing.mapNotNull { it.second }.filter { it !in kept }.forEach { id ->
                add("keywords_attributes[$index][id]" to id)
                add("keywords_attributes[$index][_destroy]" to "true")
                index += 1
            }
        }
    }
}
