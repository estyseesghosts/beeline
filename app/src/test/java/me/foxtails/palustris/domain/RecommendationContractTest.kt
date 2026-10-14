package me.foxtails.palustris.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationContractTest {
    private fun sourceWith(statuses: Map<Timeline, CapabilityStatus>): SocialSource {
        val capabilities = ServerCapabilities().withTimelineStatuses(statuses)
        return object : SocialSource {
            override val capabilities: ServerCapabilities = capabilities
            override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> =
                throw SourceError.Unsupported("timeline:$timeline")
        }
    }

    @Test
    fun displayOrderPlacesRecommendedAfterSocial() {
        assertEquals(
            listOf(Timeline.Home, Timeline.Local, Timeline.Social, Timeline.Recommended, Timeline.Bubble, Timeline.Federated),
            timelineDisplayOrder,
        )
    }

    @Test
    fun timelinesIncludesRecommendedOnlyWhenSupported() = runTest {
        val others = timelineDisplayOrder.filterNot { it == Timeline.Recommended }.associateWith { CapabilityStatus.Supported }
        assertTrue(sourceWith(others + (Timeline.Recommended to CapabilityStatus.Supported)).timelines().contains(Timeline.Recommended))
        for (status in listOf(CapabilityStatus.Unknown, CapabilityStatus.Unsupported, CapabilityStatus.Denied, CapabilityStatus.TemporarilyUnavailable)) {
            assertTrue(sourceWith(others + (Timeline.Recommended to status)).timelines().none { it == Timeline.Recommended })
        }
    }

    @Test
    fun timelinesKeepsRecommendedPositionWhenSupported() = runTest {
        val all = timelineDisplayOrder.associateWith { CapabilityStatus.Supported }
        assertEquals(timelineDisplayOrder, sourceWith(all).timelines())
    }

    @Test
    fun schemaBumpCarriesNullableRecommendations() {
        assertEquals(15, ServerCapabilities.CURRENT_CAPABILITY_SCHEMA_VERSION)
        assertEquals(15, ServerCapabilities().capabilitySchemaVersion)
        assertNull(ServerCapabilities().recommendations)
        assertEquals(
            CapabilityStatus.Unknown,
            RecommendationCapability(flavor = RecommendationFlavor.DVD_SHARKEY, origin = "https://dvd.chat").status,
        )
    }

    @Test
    fun everyFlavorFlowsThroughGenericPaths() {
        // No production `when` switches on flavor, so a second value compiles through these same paths untouched.
        assertTrue(RecommendationFlavor.entries.contains(RecommendationFlavor.DVD_SHARKEY))
        for (flavor in RecommendationFlavor.entries) {
            val capability = RecommendationCapability(flavor = flavor, status = CapabilityStatus.Supported, origin = "https://dvd.chat")
            assertEquals(flavor, capability.flavor)
            val explanation = RecommendationExplanation(
                flavor = flavor,
                score = 1.5,
                topic = "art",
                mode = "score",
                factors = listOf(RecommendationFactor("recency", 0.5, "x1.2")),
                learnedRows = listOf(RecommendationLearnedRow("ranker", 0.7, 0.9)),
            )
            assertEquals(flavor, explanation.flavor)
            assertEquals(listOf(RecommendationFactor("recency", 0.5, "x1.2")), explanation.factors)
        }
    }

    @Test
    fun recommendedTakesHomeFilterContext() {
        assertEquals(FilterContext.Home, FeedSource.Timeline(Timeline.Recommended).filterContext())
        assertEquals(FilterContext.Home, FeedSource.Timeline(Timeline.Home).filterContext())
        for (timeline in listOf(Timeline.Local, Timeline.Social, Timeline.Bubble, Timeline.Federated)) {
            assertEquals(FilterContext.Public, FeedSource.Timeline(timeline).filterContext())
        }
    }
}
