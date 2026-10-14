package me.foxtails.palustris.ui.feed

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.foxtails.palustris.data.misskey.CapabilityCache
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.misskey.MisskeySource
import me.foxtails.palustris.data.notifications.NotificationSyncOrchestrator
import me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Event
import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.RecommendationCapability
import me.foxtails.palustris.domain.RecommendationFlavor
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.timelineDisplayOrder
import me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice 3: Recommended behaves like Social in Home, without streaming. The tab row needs no
 * change: both compact and wide Home build it from the same `homeFeedChipEntries`, which
 * filters `timelineDisplayOrder` by the adapter's `timelines()`. These tests pin the gating
 * matrix, the refresh-versus-load-more cursors, and the no-streaming invariant at the feed
 * owner, where selection already persists like Social.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecommendedHomeEntryTest {
    private val dvdConnection = Connection("https://dvd.chat", Protocol.MISSKEY)
    private val accountId = AccountId(dvdConnection, "owner")
    private val otherAccountId = AccountId(dvdConnection, "other")
    private val author = Account(AccountId(dvdConnection, "author"), "Author", "@author@dvd.chat")

    private fun post(prefix: String, index: Int) = Post(
        id = EntityId(dvdConnection.origin, "$prefix-$index"),
        author = author,
        text = "$prefix-$index",
        publishedAtEpochMillis = 0,
        audience = Audience.Public,
    )

    private fun rows(model: FeedViewModel): List<String> =
        model.feed.value.posts.map { it.id.value }

    /**
     * Offset-paged fake in the DVD shape: cursors are decimal offsets, the first page starts at
     * 0, and a refresh replays the first page. It records which entry point each call used so
     * tests can tell an explicit refresh apart from cursor paging.
     * Responsibility: DVD-shaped paging plus call recording. Lifetime: one test.
     */
    private inner class OffsetSource(
        override val capabilities: ServerCapabilities = ServerCapabilities(timelines = setOf(Timeline.Home)),
        private val idPrefix: String = "r",
        private val pageSize: Int = 30,
    ) : SocialSource {
        val refreshCalls = mutableListOf<FeedSource>()
        val feedCalls = mutableListOf<Pair<FeedSource, String?>>()
        var streamSubscriptions = 0
        var failure: Exception? = null
        var totalAvailable = 60

        /** Mirrors the adapter gate: Recommended surfaces only while the probe reports Supported. */
        var recommendedStatus: CapabilityStatus? = null

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = page(cursor)

        override suspend fun timelines(): List<Timeline> = timelineDisplayOrder.filter {
            it in capabilities.timelines ||
                (it == Timeline.Recommended && recommendedStatus == CapabilityStatus.Supported)
        }

        override suspend fun feed(feed: FeedSource, cursor: String?): Page<Post> {
            feedCalls += feed to cursor
            failure?.let { throw it }
            return page(cursor)
        }

        override suspend fun refreshFeed(feed: FeedSource): Page<Post> {
            refreshCalls += feed
            return feed(feed)
        }

        override fun streamEvents(): Flow<Event> {
            streamSubscriptions += 1
            return emptyFlow()
        }

        private fun page(cursor: String?): Page<Post> {
            val offset = cursor?.toIntOrNull() ?: 0
            val items = (offset until minOf(offset + pageSize, totalAvailable)).map { post(idPrefix, it) }
            return Page(items, nextCursor = if (items.isEmpty()) null else (offset + items.size).toString())
        }
    }

    /** A source without a refresh-sensitive endpoint: the default replays a first-page load. */
    private inner class PlainSource : SocialSource {
        override val capabilities = ServerCapabilities(timelines = setOf(Timeline.Home, Timeline.Local))
        val calls = mutableListOf<Pair<Timeline, String?>>()

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> {
            calls += timeline to cursor
            return Page(listOf(post("plain", 0)), nextCursor = null)
        }
    }

    private fun model(account: AccountId, source: SocialSource, coordinator: NotificationSyncOrchestrator) = FeedViewModel(
        account,
        source,
        coordinator,
        InMemoryPostPreferencesRepository(),
        0L,
        PostInteractionExecutionAuthority(),
    )

    @Test
    fun recommendedSurfacesOnlyWhenProbeReportsSupported() = runTest {
        fun sourceWith(status: CapabilityStatus?): MisskeySource {
            val capabilities = ServerCapabilities(
                timelines = setOf(Timeline.Home, Timeline.Social),
                recommendations = status?.let {
                    RecommendationCapability(RecommendationFlavor.DVD_SHARKEY, it, dvdConnection.origin)
                },
            )
            return MisskeySource(
                dvdConnection.origin,
                "test-token",
                MisskeyApi(),
                initialCapabilities = capabilities,
                accountId = accountId,
                capabilityCache = CapabilityCache(),
            )
        }

        assertEquals(
            listOf(Timeline.Home, Timeline.Social, Timeline.Recommended),
            sourceWith(CapabilityStatus.Supported).timelines(),
        )
        for (status in listOf(
            CapabilityStatus.Denied,
            CapabilityStatus.Unknown,
            CapabilityStatus.Unsupported,
            CapabilityStatus.TemporarilyUnavailable,
        )) {
            assertFalse(
                "Recommended must stay hidden on $status",
                Timeline.Recommended in sourceWith(status).timelines(),
            )
        }
        assertFalse(Timeline.Recommended in sourceWith(null).timelines())
    }

    @Test
    fun anonymousAndOffHostSourcesHideRecommended() = runTest {
        // Both sources carry a Supported snapshot so hiding is proven by the account and host
        // bindings in MisskeySource.timelines(), not by a conveniently absent capability.
        val supported = RecommendationCapability(RecommendationFlavor.DVD_SHARKEY, CapabilityStatus.Supported, dvdConnection.origin)
        val anonymous = MisskeySource(
            dvdConnection.origin,
            "test-token",
            MisskeyApi(),
            initialCapabilities = ServerCapabilities(
                timelines = setOf(Timeline.Home, Timeline.Social),
                recommendations = supported,
            ),
            accountId = null,
            capabilityCache = CapabilityCache(),
        )
        assertFalse(Timeline.Recommended in anonymous.timelines())

        val elsewhereConnection = Connection("https://misskey.io", Protocol.MISSKEY)
        val elsewhere = MisskeySource(
            elsewhereConnection.origin,
            "test-token",
            MisskeyApi(),
            initialCapabilities = ServerCapabilities(
                timelines = setOf(Timeline.Home, Timeline.Social),
                recommendations = supported,
            ),
            accountId = AccountId(elsewhereConnection, "owner"),
            capabilityCache = CapabilityCache(),
        )
        assertFalse(Timeline.Recommended in elsewhere.timelines())
    }

    @Test
    fun homeTabsFollowTheAdapterGate() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val cases = listOf(
                CapabilityStatus.Supported,
                CapabilityStatus.Denied,
                CapabilityStatus.Unknown,
                CapabilityStatus.Unsupported,
                CapabilityStatus.TemporarilyUnavailable,
                null,
            )
            for (status in cases) {
                val source = OffsetSource()
                source.recommendedStatus = status
                val model = model(accountId, source, coordinator)
                advanceUntilIdle()

                assertEquals(
                    "Recommended tab must show iff the probe reports Supported, was $status",
                    status == CapabilityStatus.Supported,
                    Timeline.Recommended in model.feed.value.timelines,
                )
                model.stop()
            }
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun refreshReRanksWhileLoadMorePagesByVisibleCount() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = OffsetSource()
            val model = model(accountId, source, coordinator)
            advanceUntilIdle()

            model.refresh(Timeline.Recommended)
            advanceUntilIdle()

            val recommended = FeedSource.Timeline(Timeline.Recommended)
            assertEquals(listOf(FeedSource.Timeline(Timeline.Home), recommended), source.refreshCalls)
            assertEquals(Timeline.Recommended, model.feed.value.timeline)
            assertEquals(30, rows(model).size)
            assertEquals("30", model.feed.value.nextCursor)

            model.loadMore()
            advanceUntilIdle()

            assertEquals(recommended to "30", source.feedCalls.last())
            assertEquals(60, rows(model).size)
            assertEquals("60", model.feed.value.nextCursor)
            // Paging never reuses the explicit-refresh path.
            assertEquals(2, source.refreshCalls.size)
            assertEquals(Timeline.Recommended, model.feed.value.timeline)
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun selectionPersistsAcrossRefreshAndPaging() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = OffsetSource()
            val model = model(accountId, source, coordinator)
            advanceUntilIdle()

            model.refresh(Timeline.Recommended)
            advanceUntilIdle()
            model.refresh()
            advanceUntilIdle()

            assertEquals(Timeline.Recommended, model.feed.value.timeline)
            assertEquals(FeedSource.Timeline(Timeline.Recommended), model.feed.value.feedSource)

            model.loadMore()
            advanceUntilIdle()

            assertEquals(Timeline.Recommended, model.feed.value.timeline)
            assertEquals(60, rows(model).size)
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun defaultRefreshReplaysAFirstPageLoad() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = PlainSource()
            val model = model(accountId, source, coordinator)
            advanceUntilIdle()

            model.refresh(Timeline.Local)
            advanceUntilIdle()

            assertEquals(listOf(Timeline.Home to null, Timeline.Local to null), source.calls)
            assertEquals(Timeline.Local, model.feed.value.timeline)
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun emptyRecommendedFeedEndsPagingWithoutError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = OffsetSource()
            source.totalAvailable = 0
            val model = model(accountId, source, coordinator)
            advanceUntilIdle()

            model.refresh(Timeline.Recommended)
            advanceUntilIdle()

            assertTrue(rows(model).isEmpty())
            assertNull(model.feed.value.nextCursor)
            assertNull(model.feed.value.error)
            assertFalse(model.feed.value.loading)
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedRefreshKeepsTheRetryPath() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = OffsetSource()
            val model = model(accountId, source, coordinator)
            advanceUntilIdle()

            source.failure = IOException("recommendations unavailable")
            model.refresh(Timeline.Recommended)
            advanceUntilIdle()

            assertNotNull(model.feed.value.error)
            // A failed timeline change restores the previous timeline and its rows.
            assertEquals(Timeline.Home, model.feed.value.timeline)
            assertEquals(30, rows(model).size)

            source.failure = null
            model.refresh(Timeline.Recommended)
            advanceUntilIdle()

            assertNull(model.feed.value.error)
            assertEquals(Timeline.Recommended, model.feed.value.timeline)
            assertEquals(30, rows(model).size)
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun accountSwitchKeepsFeedsIsolated() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val sourceA = OffsetSource(idPrefix = "a")
            val modelA = model(accountId, sourceA, coordinator)
            advanceUntilIdle()
            modelA.refresh(Timeline.Recommended)
            advanceUntilIdle()
            val callsA = sourceA.feedCalls.size + sourceA.refreshCalls.size
            val rowsA = rows(modelA)

            val sourceB = OffsetSource(idPrefix = "b")
            sourceB.totalAvailable = 30
            val modelB = model(otherAccountId, sourceB, coordinator)
            advanceUntilIdle()
            modelB.refresh(Timeline.Recommended)
            advanceUntilIdle()

            assertTrue(rowsA.all { it.startsWith("a-") })
            assertEquals(rowsA, rows(modelA))
            assertEquals(callsA, sourceA.feedCalls.size + sourceA.refreshCalls.size)
            assertTrue(rows(modelB).all { it.startsWith("b-") })
            assertEquals(30, rows(modelB).size)
            assertEquals("30", modelB.feed.value.nextCursor)
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun noStreamingSubscriptionOrPrepend() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = OffsetSource()
            val model = model(accountId, source, coordinator)
            advanceUntilIdle()
            model.refresh(Timeline.Recommended)
            advanceUntilIdle()
            model.loadMore()
            advanceUntilIdle()

            // The feed owner never subscribes: freshness comes from refresh and paging only.
            assertEquals(0, source.streamSubscriptions)
            // External updates touch fields on loaded rows; an unknown post inserts nothing.
            val before = rows(model)
            model.applyExternalPost(OwnedPost(accountId, post("streamed", 999)))
            assertEquals(before, rows(model))
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }
}
