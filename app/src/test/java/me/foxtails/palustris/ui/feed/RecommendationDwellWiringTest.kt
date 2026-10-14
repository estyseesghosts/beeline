@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package me.foxtails.palustris.ui.feed

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.foxtails.palustris.data.notifications.NotificationSyncOrchestrator
import me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.RecommendationFeedbackSource
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice 4 wiring: the feed ViewModel owns dwell flush collection in its own scope, and the Home
 * host runs only for recommended rows of a supported signed-in DVD session. The composition side
 * passes a fire-and-forget sink, so transport never runs in composition; these tests pin the
 * owner side plus the pure enabled/rows rules the shell wires through.
 */
class RecommendationDwellWiringTest {
    private val dvdConnection = Connection("https://dvd.chat", Protocol.MISSKEY)
    private val accountId = AccountId(dvdConnection, "owner")
    private val author = Account(AccountId(dvdConnection, "author"), "Author", "@author@dvd.chat")

    private fun row(id: String) = OwnedPost(
        accountId,
        Post(EntityId(dvdConnection.origin, id), author, "post $id", 0, Audience.Public),
        sessionRevision = 0L,
    )

    private class DwellSource(
        override val capabilities: ServerCapabilities = ServerCapabilities(timelines = setOf(Timeline.Home)),
    ) : SocialSource, RecommendationFeedbackSource {
        val dwellReports = mutableListOf<Map<EntityId, Long>>()
        var failure: Exception? = null
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
        override suspend fun reportRecommendationDwell(dwellMillis: Map<EntityId, Long>) {
            failure?.let { throw it }
            dwellReports += dwellMillis
        }
        override suspend fun markRecommendationNotInterested(noteId: EntityId) = Unit
        override fun recommendationExplanation(noteId: EntityId) = null
    }

    private class PlainSource : SocialSource {
        override val capabilities = ServerCapabilities(timelines = setOf(Timeline.Home))
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
    }

    private fun model(account: AccountId, source: SocialSource) = FeedViewModel(
        account,
        source,
        NotificationSyncOrchestrator(),
        InMemoryPostPreferencesRepository(),
        0L,
        PostInteractionExecutionAuthority(),
    )

    @Test
    fun dwellChunkDelegatesToTheFeedbackSourceInOwnerScope() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = DwellSource()
            val feedModel = model(accountId, source)
            val chunk = mapOf(EntityId(dvdConnection.origin, "r1") to 1_200L)

            feedModel.reportRecommendationDwell(chunk)
            // Fire-and-forget: nothing is reported synchronously from the caller.
            assertTrue(source.dwellReports.isEmpty())
            advanceUntilIdle()

            assertEquals(listOf(chunk), source.dwellReports)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun stoppedModelDropsDwellSilently() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = DwellSource()
            val feedModel = model(accountId, source)
            feedModel.stop()

            feedModel.reportRecommendationDwell(mapOf(EntityId(dvdConnection.origin, "r1") to 1_200L))
            advanceUntilIdle()

            assertTrue(source.dwellReports.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failingSourceNeverEscapesTheSink() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = DwellSource().apply { failure = IOException("unreachable") }
            val feedModel = model(accountId, source)

            feedModel.reportRecommendationDwell(mapOf(EntityId(dvdConnection.origin, "r1") to 1_200L))
            advanceUntilIdle()

            assertTrue(source.dwellReports.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun sourceWithoutFeedbackStaysSilent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val feedModel = model(accountId, PlainSource())

            feedModel.reportRecommendationDwell(mapOf(EntityId(dvdConnection.origin, "r1") to 1_200L))
            advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun dwellRunsOnlyOnRecommendedWithAGatedTab() {
        val gated = setOf(Timeline.Home, Timeline.Social, Timeline.Recommended)
        val ungated = setOf(Timeline.Home, Timeline.Social)

        // Recommended selected while the adapter gate offers it: the only enabled case. The gated
        // tab set already encodes Supported plus dvd.chat plus signed-in, so Compose checks
        // nothing beyond timeline and capability state.
        assertTrue(recommendationDwellEnabled(FeedSource.Timeline(Timeline.Recommended), gated))
        // Any other feed, even with the tab offered, never reports.
        assertFalse(recommendationDwellEnabled(FeedSource.Timeline(Timeline.Social), gated))
        assertFalse(recommendationDwellEnabled(FeedSource.Timeline(Timeline.Home), gated))
        // Recommended selected without the gated tab (anonymous, off-host, denied, unknown, or
        // temporarily unavailable) never reports.
        assertFalse(recommendationDwellEnabled(FeedSource.Timeline(Timeline.Recommended), ungated))
        assertFalse(recommendationDwellEnabled(FeedSource.Timeline(Timeline.Home), ungated))
    }

    @Test
    fun dwellRowsStayEmptyOffTheRecommendedFeed() {
        val rows = listOf(row("r1"), row("r2"))

        assertEquals(rows, recommendationDwellRows(FeedSource.Timeline(Timeline.Recommended), rows))
        assertTrue(recommendationDwellRows(FeedSource.Timeline(Timeline.Social), rows).isEmpty())
        assertTrue(recommendationDwellRows(FeedSource.Timeline(Timeline.Home), rows).isEmpty())
    }
}
