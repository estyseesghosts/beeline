package me.foxtails.palustris.ui.recommendations

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost

/** A recommended row counts as seen only after this long on screen. Shorter views are ignored. */
const val RECOMMENDATION_MIN_DWELL_MILLIS = 600L

/** How often accumulated dwell flushes while the feed stays visible. */
const val RECOMMENDATION_DWELL_FLUSH_INTERVAL_MILLIS = 15_000L

/** The tracker sends at most this many notes per dwell call. */
const val RECOMMENDATION_DWELL_MAX_CHUNK = 50

/** One hour in milliseconds: a single note never reports more than this per flush. */
const val RECOMMENDATION_DWELL_MAX_MILLIS = 3_600_000L

/** A recommended row counts as visible once this fraction of its height is on screen. */
const val RECOMMENDATION_ROW_VISIBILITY_FRACTION = 0.5f

/**
 * Splits accumulated dwell into chunks of at most [maxChunkSize] for the dwell endpoint.
 * Responsibility: pure chunking policy shared by the tracker and its tests. Lifetime: none.
 */
fun chunkDwellMillis(
    entries: Map<EntityId, Long>,
    maxChunkSize: Int = RECOMMENDATION_DWELL_MAX_CHUNK,
): List<Map<EntityId, Long>> {
    if (entries.isEmpty()) return emptyList()
    require(maxChunkSize > 0) { "dwell chunks must hold at least one note" }
    return entries.entries.chunked(maxChunkSize).map { chunk -> chunk.associate { it.key to it.value } }
}

/**
 * Visible fraction of one row, from its pixel offset and size against the viewport window.
 * Fully outside reads 0, fully inside reads 1. Lifetime: none.
 */
fun recommendationRowVisibleFraction(itemOffset: Int, itemSize: Int, viewportStart: Int, viewportEnd: Int): Float {
    if (itemSize <= 0 || viewportEnd <= viewportStart) return 0f
    val visible = (minOf(itemOffset + itemSize, viewportEnd) - maxOf(itemOffset, viewportStart)).coerceAtLeast(0)
    return (visible.toFloat() / itemSize).coerceIn(0f, 1f)
}

/**
 * Accumulates visible milliseconds per recommended note for one feed. Instance state only: the
 * host owns one beside its list state, so no global mutable state exists and dwell ties to feed
 * visibility, never to a screen lifetime.
 * Responsibility: dwell accumulation, threshold, cap, and flush timing. Lifetime: bound to one
 * feed composition; released with a final [dispose] flush by the host.
 */
class RecommendationDwellAccumulator(
    /** False drops every observation: anonymous and non-DVD feeds never accrue. */
    val enabled: Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val visibleSince = mutableMapOf<EntityId, Long>()
    private val totals = mutableMapOf<EntityId, Long>()

    /**
     * Reports the recommended rows now at least half visible. Newly hidden rows roll their
     * segment into the totals and flush when due; newly visible rows start a segment. Returns
     * the dwell due for sending, already thresholded and capped. Empty when disabled.
     */
    fun onVisibilityChanged(visible: Set<EntityId>, nowMillis: Long = clock()): Map<EntityId, Long> {
        if (!enabled) {
            visibleSince.clear()
            totals.clear()
            return emptyMap()
        }
        val hidden = visibleSince.keys - visible
        hidden.forEach { id -> rollVisible(id, nowMillis) }
        (visible - visibleSince.keys).forEach { id -> visibleSince[id] = nowMillis }
        return takeDue(hidden)
    }

    /**
     * Rolls still-visible segments into the totals and returns everything due after the flush
     * interval. The host calls this every [RECOMMENDATION_DWELL_FLUSH_INTERVAL_MILLIS].
     */
    fun poll(nowMillis: Long = clock()): Map<EntityId, Long> {
        if (!enabled) return emptyMap()
        visibleSince.keys.toList().forEach { id -> rollVisible(id, nowMillis) }
        return takeDue(totals.keys.toSet())
    }

    /**
     * Final flush for host disposal. The host sends the result only while still enabled, so a
     * signed-out dispose drops state instead of reporting.
     */
    fun dispose(nowMillis: Long = clock()): Map<EntityId, Long> {
        if (!enabled) {
            visibleSince.clear()
            totals.clear()
            return emptyMap()
        }
        visibleSince.keys.toList().forEach { id -> rollVisible(id, nowMillis) }
        return takeDue(totals.keys.toSet())
    }

    private fun rollVisible(id: EntityId, nowMillis: Long) {
        val start = visibleSince.remove(id) ?: return
        val segment = (nowMillis - start).coerceAtLeast(0L)
        if (segment > 0L) {
            totals[id] = ((totals[id] ?: 0L) + segment).coerceAtMost(RECOMMENDATION_DWELL_MAX_MILLIS)
        }
    }

    private fun takeDue(only: Set<EntityId>): Map<EntityId, Long> {
        if (only.isEmpty()) return emptyMap()
        val due = mutableMapOf<EntityId, Long>()
        only.forEach { id ->
            val total = totals.remove(id) ?: return@forEach
            if (total >= RECOMMENDATION_MIN_DWELL_MILLIS) {
                due[id] = total.coerceAtMost(RECOMMENDATION_DWELL_MAX_MILLIS)
            }
            // Under-threshold views are ignored: dropped, never carried forward.
        }
        return due
    }
}

/**
 * Binds a [RecommendationDwellAccumulator] to feed visibility. Observes the lazy list's visible
 * rows at 50 percent visibility, flushes every 15 seconds, on hide, and on dispose, and sends
 * chunks of at most 50 through [onFlushChunk]. Failures stay silent: a throwing flush is
 * dropped without retry. Runs only while [enabled] (signed-in DVD accounts); the caller derives
 * it from the selected feed and capabilities.
 * Responsibility: feed-visibility lifetime for dwell reporting. Lifetime: this composition.
 */
@Composable
fun RecommendationDwellHost(
    listState: LazyListState,
    posts: List<OwnedPost>,
    enabled: Boolean,
    onFlushChunk: suspend (Map<EntityId, Long>) -> Unit,
) {
    val accumulator = remember(enabled) { RecommendationDwellAccumulator(enabled) }
    val scope = rememberCoroutineScope()

    fun send(due: Map<EntityId, Long>) {
        if (due.isEmpty()) return
        scope.launch {
            chunkDwellMillis(due).forEach { chunk ->
                runCatching { onFlushChunk(chunk) }
            }
        }
    }

    LaunchedEffect(listState, accumulator, posts) {
        snapshotFlow { dwellVisibleIds(listState, posts) }
            .distinctUntilChanged()
            .collect { visible -> send(accumulator.onVisibilityChanged(visible)) }
    }
    // The interval loop owns the dispose flush: its finally still runs while the host leaves the
    // composition, and NonCancellable lets the final send finish instead of dying with it. A
    // DisposableEffect onDispose could not call the suspending flush at all.
    LaunchedEffect(accumulator) {
        try {
            while (true) {
                delay(RECOMMENDATION_DWELL_FLUSH_INTERVAL_MILLIS)
                send(accumulator.poll())
            }
        } finally {
            withContext(NonCancellable) {
                val due = accumulator.dispose()
                if (due.isNotEmpty() && accumulator.enabled) {
                    chunkDwellMillis(due).forEach { chunk ->
                        runCatching { onFlushChunk(chunk) }
                    }
                }
            }
        }
    }
}

/**
 * Recommended rows at least half visible right now, resolved by stable item key. HomeFeed keys
 * each post row `"${connection}/${value}"` (HomeFeed post items), so an error banner, empty
 * state, or footer ahead of the rows cannot shift attribution the way indices would: header and
 * footer keys never match a post key and are skipped, and every matched key maps to its own
 * note. No index-offset hack: the lookup, not the position, owns the mapping.
 */
private fun dwellVisibleIds(listState: LazyListState, posts: List<OwnedPost>): Set<EntityId> {
    val layout = listState.layoutInfo
    if (posts.isEmpty()) return emptySet()
    val byKey = recommendationPostKeys(posts)
    return layout.visibleItemsInfo.mapNotNull { item ->
        val fraction = recommendationRowVisibleFraction(
            itemOffset = item.offset,
            itemSize = item.size,
            viewportStart = layout.viewportStartOffset,
            viewportEnd = layout.viewportEndOffset,
        )
        dwellVisibleId(item.key, fraction, byKey)
    }.toSet()
}

/**
 * Lookup from a lazy-list item key to the served note it renders. Mirrors the HomeFeed row key
 * so resolution stays correct when non-post items shift layout indices.
 * Responsibility: pure key policy shared by the tracker and its tests. Lifetime: none.
 */
internal fun recommendationPostKeys(posts: List<OwnedPost>): Map<String, EntityId> =
    posts.associate { recommendationPostKey(it.post.id) to it.post.id }

/** The stable key HomeFeed assigns one post row. */
internal fun recommendationPostKey(id: EntityId): String = "${id.connection}/${id.value}"

/**
 * Resolves one visible row to its note: null for header/footer/empty keys and for rows under
 * half visibility, even with a known key.
 */
internal fun dwellVisibleId(itemKey: Any?, fraction: Float, byKey: Map<String, EntityId>): EntityId? {
    if (fraction < RECOMMENDATION_ROW_VISIBILITY_FRACTION) return null
    val key = itemKey as? String ?: return null
    return byKey[key]
}
