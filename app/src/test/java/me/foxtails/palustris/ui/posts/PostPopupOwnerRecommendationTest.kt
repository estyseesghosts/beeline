@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package me.foxtails.palustris.ui.posts

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.RecommendationExplanation
import me.foxtails.palustris.domain.RecommendationFeedbackSource
import me.foxtails.palustris.domain.RecommendationFlavor
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.UiStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice 4: not-interested removes the row locally immediately and reports silently, and the
 * popup serves the retained ranking rationale. Sources without feedback stay silent no-ops.
 */
class PostPopupOwnerRecommendationTest {
    private val connection = Connection("https://dvd.chat", Protocol.MISSKEY)
    private val me = AccountId(connection, "me")
    private val other = AccountId(connection, "other")

    private fun owned(author: AccountId, id: String = "r1") = OwnedPost(
        me,
        Post(
            id = EntityId(connection.origin, id),
            author = Account(author, "A", "@a@dvd.chat"),
            text = "recommended",
            publishedAtEpochMillis = 0,
            audience = Audience.Public,
        ),
        sessionRevision = 1L,
    )

    private class FakeFeedbackSource : SocialSource, RecommendationFeedbackSource {
        override val capabilities = ServerCapabilities()
        val notInterested = mutableListOf<EntityId>()
        val explanations = mutableMapOf<EntityId, RecommendationExplanation>()
        var failure: Exception? = null
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
        override suspend fun reportRecommendationDwell(dwellMillis: Map<EntityId, Long>) = Unit
        override suspend fun markRecommendationNotInterested(noteId: EntityId) {
            failure?.let { throw it }
            notInterested += noteId
        }
        override fun recommendationExplanation(noteId: EntityId): RecommendationExplanation? = explanations[noteId]
    }

    private class PlainSource : SocialSource {
        override val capabilities = ServerCapabilities()
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
    }

    @Test
    fun notInterestedRemovesImmediatelyThenReports() = runTest {
        val source = FakeFeedbackSource()
        val forwarded = mutableListOf<OwnedPost>()
        val popup = PostPopupOwner(me, 1L, source, this, onPostDeleted = { forwarded += it }, uiStrings = UiStrings.Default)
        val target = owned(other)

        popup.open(target, Rect.Zero)
        popup.markRecommendationNotInterested()

        // Immediate local removal: the sheet dismisses and the row forwards before the network.
        assertEquals(listOf(target), forwarded)
        assertNull(popup.target)
        assertTrue(source.notInterested.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf(EntityId(connection.origin, "r1")), source.notInterested)
    }

    @Test
    fun deniedNotInterestedKeepsTheRemovalAndStaysSilent() = runTest {
        val source = FakeFeedbackSource().apply { failure = SourceError.AccessDenied("recommendations.not-interested") }
        val forwarded = mutableListOf<OwnedPost>()
        val popup = PostPopupOwner(me, 1L, source, this, onPostDeleted = { forwarded += it }, uiStrings = UiStrings.Default)

        popup.open(owned(other), Rect.Zero)
        popup.markRecommendationNotInterested()
        advanceUntilIdle()

        assertEquals(1, forwarded.size)
        assertNull(popup.target)
    }

    @Test
    fun notInterestedNoOpsWithoutFeedbackContract() = runTest {
        val popup = PostPopupOwner(me, 1L, PlainSource(), this, uiStrings = UiStrings.Default)
        val target = owned(other)

        popup.open(target, Rect.Zero)
        popup.markRecommendationNotInterested()
        advanceUntilIdle()

        // Without a feedback contract the row stays: nothing was removed, nothing was sent.
        assertEquals(target, popup.target?.ownedPost)
    }

    @Test
    fun retiredOwnerIgnoresNotInterested() = runTest {
        val source = FakeFeedbackSource()
        val forwarded = mutableListOf<OwnedPost>()
        val popup = PostPopupOwner(me, 1L, source, this, onPostDeleted = { forwarded += it }, uiStrings = UiStrings.Default)

        popup.open(owned(other), Rect.Zero)
        popup.retire()
        popup.markRecommendationNotInterested()
        advanceUntilIdle()

        assertTrue(forwarded.isEmpty())
        assertTrue(source.notInterested.isEmpty())
    }

    @Test
    fun explanationServesTheRetainedRationale() = runTest {
        val source = FakeFeedbackSource().apply {
            explanations[EntityId(connection.origin, "r1")] =
                RecommendationExplanation(flavor = RecommendationFlavor.DVD_SHARKEY, score = 2.0, topic = "art", mode = "score")
        }
        val popup = PostPopupOwner(me, 1L, source, this, uiStrings = UiStrings.Default)

        assertNull(popup.recommendationExplanation())
        popup.open(owned(other), Rect.Zero)
        assertEquals("art", popup.recommendationExplanation()?.topic)
        popup.dismiss()
        assertNull(popup.recommendationExplanation())
    }
}
