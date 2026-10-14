package me.foxtails.palustris.ui.recommendations

import me.foxtails.palustris.domain.RecommendationExplanation
import me.foxtails.palustris.domain.RecommendationFactor
import me.foxtails.palustris.domain.RecommendationFlavor
import me.foxtails.palustris.domain.RecommendationLearnedRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice 4: reason-dialog content for score versus following modes. Pure mapping tests over the
 * generic domain explanation; Compose rendering is pinned by [RecommendationReasonDialogTest].
 */
class RecommendationReasonContentTest {
    private fun scoreExplanation() = RecommendationExplanation(
        flavor = RecommendationFlavor.DVD_SHARKEY,
        score = 1.5,
        topic = "art",
        mode = "score",
        factors = listOf(
            RecommendationFactor("recency", 0.5, "x1.2"),
            RecommendationFactor("subtotal", 1.0),
        ),
        learnedRows = listOf(RecommendationLearnedRow("ranker", 0.7, 0.9)),
    )

    @Test fun scoreModeShowsTheNumericBreakdown() {
        val content = recommendationReasonContent(scoreExplanation())

        assertEquals(RecommendationReasonMode.Score, content.mode)
        assertEquals(1.5, content.score ?: Double.NaN, 0.0)
        assertEquals("art", content.topic)
        assertEquals("score", content.typeLabel)
        assertTrue(content.showBreakdown)
        assertTrue(content.showLearned)
        assertEquals(listOf("recency", "subtotal"), content.factors.map { it.label })
        assertEquals(0.5, content.factors[0].contribution ?: Double.NaN, 0.0)
        assertEquals("x1.2", content.factors[0].detail)
        assertEquals("ranker", content.learnedRows.single().label)
        assertEquals(0.7, content.learnedRows.single().weight ?: Double.NaN, 0.0)
        assertEquals(0.9, content.learnedRows.single().value ?: Double.NaN, 0.0)
    }

    @Test fun followingModeHidesTheBreakdown() {
        val content = recommendationReasonContent(
            RecommendationExplanation(
                flavor = RecommendationFlavor.DVD_SHARKEY,
                topic = "art",
                mode = "following",
            ),
        )

        assertEquals(RecommendationReasonMode.Following, content.mode)
        assertNull(content.score)
        assertEquals("art", content.topic)
        assertEquals("following", content.typeLabel)
        assertFalse(content.showBreakdown)
        assertFalse(content.showLearned)
        assertTrue(content.factors.isEmpty())
        assertTrue(content.learnedRows.isEmpty())
    }

    @Test fun unknownModeRendersRowsWithoutAModeClaim() {
        val content = recommendationReasonContent(scoreExplanation().copy(mode = "hybrid"))

        assertEquals(RecommendationReasonMode.Other, content.mode)
        assertFalse(content.showBreakdown)
        assertEquals(2, content.factors.size)
        assertTrue(content.showLearned)
    }

    @Test fun emptyExplanationHasNothingToShow() {
        val content = recommendationReasonContent(
            RecommendationExplanation(flavor = RecommendationFlavor.DVD_SHARKEY),
        )

        assertEquals(RecommendationReasonMode.Other, content.mode)
        assertNull(content.score)
        assertNull(content.topic)
        assertNull(content.typeLabel)
        assertFalse(content.showBreakdown)
        assertFalse(content.showLearned)
    }
}
