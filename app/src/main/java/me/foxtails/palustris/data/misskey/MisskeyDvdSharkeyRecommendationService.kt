package me.foxtails.palustris.data.misskey

import java.util.Locale
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.RecommendationExplanation
import me.foxtails.palustris.domain.RecommendationFactor
import me.foxtails.palustris.domain.RecommendationFlavor
import me.foxtails.palustris.domain.RecommendationLearnedRow
import me.foxtails.palustris.domain.SourceError
import org.json.JSONArray
import org.json.JSONObject

/**
 * DVD Sharkey recommendations feed for one signed-in session. Stateless: each page carries its own
 * posts and explanations, and no second cache or singleton sits beside the source cache.
 * Responsibility: offset-paged `notes/recommendations` transport plus factor mapping.
 * Lifetime: bound to its [MisskeySource] session; holds no page state between calls.
 */
internal class MisskeyDvdSharkeyRecommendationService(
    private val origin: String,
    private val token: String?,
    private val api: MisskeyApi,
    private val accountId: AccountId?,
    private val maxResponseBytes: Long = MISSKEY_MAX_RESPONSE_BYTES,
    private val deviceLanguage: () -> String = { Locale.getDefault().toLanguageTag() },
) {
    /** One served page: the posts for the timeline plus the ranking rationale behind each, keyed by post ID. */
    data class RecommendationFeedPage(
        val posts: Page<Post>,
        val explanations: Map<EntityId, RecommendationExplanation>,
    )

    /**
     * Ranking rationales from served pages, keyed by post ID. Bounded to the most recent pages
     * so the reason dialog can render without protocol JSON in Compose; evicted entries read as
     * null. Guarded because pages arrive on background dispatchers.
     */
    private val explanationRegistry = LinkedHashMap<EntityId, RecommendationExplanation>()

    /** The rationale behind one served note, or null when no served page carried it. */
    @Synchronized
    fun explanationFor(id: EntityId): RecommendationExplanation? = explanationRegistry[id]

    @Synchronized
    private fun retainExplanations(explanations: Map<EntityId, RecommendationExplanation>) {
        explanations.forEach { (id, explanation) ->
            explanationRegistry.remove(id)
            explanationRegistry[id] = explanation
        }
        while (explanationRegistry.size > EXPLANATION_REGISTRY_MAX) {
            explanationRegistry.entries.iterator().apply { next(); remove() }
        }
    }

    /**
     * Serves one page by decimal offset. A null cursor starts at 0; a returned cursor continues
     * where the previous page ended; [refresh] re-ranks from 0. Malformed notes are skipped so one
     * bad note never fails the page. Logs carry no secrets or bodies; the token travels only in
     * the request body the transport owns.
     */
    suspend fun page(cursor: String?, refresh: Boolean = false): RecommendationFeedPage {
        if (!isDvdSharkeyHost(origin)) throw SourceError.Unsupported("timeline:Recommended")
        val credential = token?.takeIf(String::isNotBlank) ?: throw SourceError.AccessDenied("timeline:Recommended")
        if (accountId != null && accountId.connection.origin != origin) throw SourceError.ForeignOrigin("timeline:Recommended")
        val offset = decodeOffset(cursor)
        val body = JSONObject()
            .put("i", credential)
            .put("limit", PAGE_SIZE)
            .put("offset", offset)
            .put("lang", normalizeRecommendationLang(deviceLanguage()))
            // TODO(dvd-slice-2): resolve withSensitive from the verified Home filter owner; no Home
            //  filter owner exists yet, so the feed excludes sensitive notes until one is verified.
            .put("withSensitive", false)
            // TODO(dvd-slice-2): resolve withRenotes from the verified Home boosts filter owner; no
            //  boosts filter owner exists yet, so the feed keeps renotes until one is verified.
            .put("withRenotes", true)
        if (refresh) body.put("refresh", true)
        // Never send untilId to this endpoint: pagination is offset-based, and a foreign ID cursor
        // would re-rank or repeat notes instead of continuing the offset.
        val raw = api.post(origin, RECOMMENDATIONS_ENDPOINT, body, maxResponseBytes).body
        val items = mutableListOf<Post>()
        val explanations = mutableMapOf<EntityId, RecommendationExplanation>()
        for (note in responseNotes(raw)) {
            val post = runCatching { MisskeyMapper.post(note, origin) }.getOrNull() ?: continue
            items.add(post)
            explanations[post.id] = mapExplanation(note)
        }
        val nextCursor = if (items.isEmpty()) null else (offset + items.size).toString()
        retainExplanations(explanations)
        return RecommendationFeedPage(Page(items, nextCursor), explanations)
    }

    /**
     * Decodes the opaque cursor to a decimal offset. The cursor carries only the offset because
     * the account and origin binding is structural: each source owns its service, so a cursor from
     * another account, origin, timeline, or protocol never parses and reads as foreign.
     */
    private fun decodeOffset(cursor: String?): Int {
        if (cursor.isNullOrBlank()) return 0
        val offset = cursor.trim().toIntOrNull()
        if (offset == null || offset < 0) throw SourceError.ForeignOrigin("timeline:Recommended")
        return offset
    }

    /** The endpoint answers a bare note array; a `{"notes": [...]}` envelope reads the same way. */
    private fun responseNotes(raw: String): List<JSONObject> {
        val array = when {
            raw.trimStart().startsWith("[") -> runCatching { JSONArray(raw) }.getOrNull()
            else -> runCatching { JSONObject(raw).optJSONArray("notes") }.getOrNull()
        } ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    /**
     * Maps `_recommendationFactors_` onto the generic explanation. The factor payload is
     * best-effort and version-tolerant: unknown keys are ignored and a missing or malformed
     * payload yields an empty explanation that keeps the flavor, never a failure.
     */
    private fun mapExplanation(note: JSONObject): RecommendationExplanation {
        val factors = runCatching {
            val payload = note.optJSONObject(FACTORS_KEY) ?: return@runCatching emptyExplanation()
            val rows = firstArray(payload, "factors", "rows", "breakdown").mapNotNull { item ->
                val label = item.nullableString("label") ?: item.nullableString("name") ?: return@mapNotNull null
                RecommendationFactor(
                    label = label,
                    contribution = item.optFiniteDouble("contribution")
                        ?: item.optFiniteDouble("score")
                        ?: item.optFiniteDouble("value"),
                    detail = item.nullableString("detail"),
                )
            }
            val learned = firstArray(payload, "learned", "learnedRows", "ranker").mapNotNull { item ->
                val label = item.nullableString("label") ?: item.nullableString("name") ?: return@mapNotNull null
                RecommendationLearnedRow(
                    label = label,
                    weight = item.optFiniteDouble("weight"),
                    value = item.optFiniteDouble("value"),
                )
            }
            RecommendationExplanation(
                flavor = RecommendationFlavor.DVD_SHARKEY,
                score = payload.optFiniteDouble("score"),
                topic = payload.nullableString("topic"),
                mode = payload.nullableString("mode") ?: payload.nullableString("type"),
                factors = rows,
                learnedRows = learned,
            )
        }.getOrNull()
        return factors ?: emptyExplanation()
    }

    private fun emptyExplanation(): RecommendationExplanation =
        RecommendationExplanation(flavor = RecommendationFlavor.DVD_SHARKEY)

    private fun firstArray(payload: JSONObject, vararg keys: String): List<JSONObject> {
        for (key in keys) {
            val array = payload.optJSONArray(key) ?: continue
            return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        }
        return emptyList()
    }

    private fun JSONObject.optFiniteDouble(key: String): Double? {
        if (isNull(key)) return null
        return runCatching { optDouble(key) }.getOrNull()?.takeIf { it.isFinite() }
    }

    private companion object {
        const val RECOMMENDATIONS_ENDPOINT = "notes/recommendations"
        const val FACTORS_KEY = "_recommendationFactors_"
        const val PAGE_SIZE = 30

        /** How many served rationales the registry keeps before evicting the oldest. */
        const val EXPLANATION_REGISTRY_MAX = 300
    }
}

/**
 * Normalizes a BCP-47 tag to the primary language subtag the endpoint takes (`zh-CN` to `zh`).
 * Slice-2 decision: the device primary locale supplies `lang`, not the app locale, because the
 * ranker personalizes by device language; the mapping is injectable so tests never depend on the
 * host locale.
 */
internal fun normalizeRecommendationLang(languageTag: String): String {
    val primary = languageTag.substringBefore('-').substringBefore('_').trim().lowercase()
    return primary.ifBlank { FALLBACK_LANG }
}

private const val FALLBACK_LANG = "en"
