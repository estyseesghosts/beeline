package me.foxtails.palustris.ui.recommendations

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice 4: dwell accumulation, threshold, cap, chunking, and flush triggers. The accumulator is
 * pure apart from its clock, so a fake millisecond clock drives every timing case.
 */
class RecommendationDwellTrackerTest {
    private val origin = "https://dvd.chat"
    private fun id(value: String) = EntityId(origin, value)
    private val connection = Connection(origin, Protocol.MISSKEY)
    private val owner = AccountId(connection, "owner")
    private val author = Account(AccountId(connection, "author"), "Author", "@author@dvd.chat")

    private fun postRow(value: String) = OwnedPost(
        owner,
        Post(EntityId(origin, value), author, "post $value", 0, Audience.Public),
        sessionRevision = 0L,
    )

    private var now = 0L
    private fun tracker(enabled: Boolean = true) = RecommendationDwellAccumulator(enabled) { now }

    @Test fun viewUnderThresholdIsIgnored() {
        val accumulator = tracker()

        val hidden = accumulator.onVisibilityChanged(setOf(id("a")), now)
        assertTrue(hidden.isEmpty())
        now = 500
        val due = accumulator.onVisibilityChanged(emptySet(), now)

        assertTrue(due.isEmpty())
        assertTrue(accumulator.poll(now).isEmpty())
    }

    @Test fun visibleMillisAccumulateAcrossObservationsUntilDue() {
        val accumulator = tracker()

        accumulator.onVisibilityChanged(setOf(id("a")), 0)
        now = 300
        assertTrue(accumulator.onVisibilityChanged(setOf(id("a")), now).isEmpty())
        now = 700
        val due = accumulator.onVisibilityChanged(emptySet(), now)

        assertEquals(mapOf(id("a") to 700L), due)
    }

    @Test fun hideFlushesOnlyHiddenRows() {
        val accumulator = tracker()
        val a = id("a")
        val b = id("b")

        accumulator.onVisibilityChanged(setOf(a, b), 0)
        now = 1_000
        assertEquals(mapOf(a to 1_000L), accumulator.onVisibilityChanged(setOf(b), now))
        now = 2_000
        assertEquals(mapOf(b to 2_000L), accumulator.onVisibilityChanged(emptySet(), now))
        assertTrue(accumulator.poll(now).isEmpty())
    }

    @Test fun intervalPollFlushesDueAndResets() {
        val accumulator = tracker()

        accumulator.onVisibilityChanged(setOf(id("a")), 0)
        now = RECOMMENDATION_DWELL_FLUSH_INTERVAL_MILLIS
        assertEquals(mapOf(id("a") to RECOMMENDATION_DWELL_FLUSH_INTERVAL_MILLIS), accumulator.poll(now))
        assertTrue(accumulator.poll(now).isEmpty())
    }

    @Test fun disposeFlushesVisibleAndAccumulated() {
        val accumulator = tracker()

        accumulator.onVisibilityChanged(setOf(id("a")), 0)
        now = 1_000
        assertEquals(mapOf(id("a") to 1_000L), accumulator.dispose(now))
        assertTrue(accumulator.dispose(now).isEmpty())
    }

    @Test fun eachValueCapsAtOneHour() {
        val accumulator = tracker()

        accumulator.onVisibilityChanged(setOf(id("a")), 0)
        val due = accumulator.dispose(5_000_000L)

        assertEquals(mapOf(id("a") to RECOMMENDATION_DWELL_MAX_MILLIS), due)
    }

    @Test fun disabledTrackerAccruesNothing() {
        val accumulator = tracker(enabled = false)

        accumulator.onVisibilityChanged(setOf(id("a")), 0)
        assertTrue(accumulator.onVisibilityChanged(emptySet(), 10_000L).isEmpty())
        assertTrue(accumulator.poll(20_000L).isEmpty())
        assertTrue(accumulator.dispose(30_000L).isEmpty())
    }

    @Test fun chunkingSplitsAtFiftyAndKeepsOrder() {
        val entries = (0 until 55).associate { id("n$it") to 1_000L }

        val chunks = chunkDwellMillis(entries)

        assertEquals(2, chunks.size)
        assertEquals(50, chunks[0].size)
        assertEquals(5, chunks[1].size)
        assertEquals(entries, chunks[0] + chunks[1])
        assertTrue(chunkDwellMillis(emptyMap()).isEmpty())
    }

    @Test fun visibilityFractionNeedsHalfTheRow() {
        assertEquals(1f, recommendationRowVisibleFraction(0, 100, 0, 100))
        assertEquals(0.5f, recommendationRowVisibleFraction(50, 100, 0, 100))
        assertEquals(0.25f, recommendationRowVisibleFraction(75, 100, 0, 100))
        assertEquals(0f, recommendationRowVisibleFraction(100, 100, 0, 100))
        assertEquals(0f, recommendationRowVisibleFraction(0, 0, 0, 100))
        assertEquals(0f, recommendationRowVisibleFraction(-100, 100, 0, 100))
    }

    @Test fun keyBasedResolutionSurvivesAHeaderRow() {
        val posts = listOf(postRow("r0"), postRow("r1"), postRow("r2"))
        val byKey = recommendationPostKeys(posts)

        // The error banner occupies layout index 0 with an auto key, pushing every post row down
        // one. Resolution ignores indices entirely: each post key maps to its own note, so the
        // shift can neither misattribute nor drop the last row.
        assertEquals(id("r0"), dwellVisibleId(recommendationPostKey(id("r0")), 1f, byKey))
        assertEquals(id("r1"), dwellVisibleId(recommendationPostKey(id("r1")), 0.75f, byKey))
        assertEquals(id("r2"), dwellVisibleId(recommendationPostKey(id("r2")), 0.5f, byKey))
        // Header, footer, and empty rows carry auto keys that never match a post key.
        assertNull(dwellVisibleId(0, 1f, byKey))
        assertNull(dwellVisibleId(null, 1f, byKey))
        assertNull(dwellVisibleId("home_feed_error", 1f, byKey))
        // Rows under half visibility never resolve, even with a known key.
        assertNull(dwellVisibleId(recommendationPostKey(id("r0")), 0.25f, byKey))
    }
}
