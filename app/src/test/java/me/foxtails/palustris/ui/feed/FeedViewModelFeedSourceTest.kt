package me.foxtails.palustris.ui.feed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import me.foxtails.palustris.domain.HashtagOrigin
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Home shows one feed at a time and keeps the paging cursor of that feed. */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelFeedSourceTest {
    private val connection = Connection("https://example.org", Protocol.MISSKEY)
    private val accountId = AccountId(connection, "owner")
    private val author = Account(AccountId(connection, "author"), "Author", "@author@example.org")

    private fun post(id: String) = Post(EntityId(connection.origin, id), author, id, 0L, Audience.Public)

    private class RecordingSource(val failList: Boolean = false) : SocialSource {
        override val capabilities = ServerCapabilities(timelines = setOf(Timeline.Home, Timeline.Local))
        val requests = mutableListOf<Pair<String, String?>>()

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> =
            record("timeline:${timeline.name}", cursor)

        override suspend fun feed(feed: FeedSource, cursor: String?): Page<Post> {
            if (feed is FeedSource.UserList && failList) throw SourceError.Unsupported("lists")
            return when (feed) {
                is FeedSource.Timeline -> timeline(feed.timeline, cursor)
                else -> record(feed.key, cursor)
            }
        }

        private fun record(key: String, cursor: String?): Page<Post> {
            requests += key to cursor
            val author = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "author"), "Author", "@a@example.org")
            val id = key + (cursor ?: "")
            val post = Post(EntityId("https://example.org", id), author, id, 0L, Audience.Public)
            return Page(listOf(post), if (cursor == null) "next" else null)
        }
    }

    private fun model(source: SocialSource, coordinator: NotificationSyncOrchestrator) = FeedViewModel(
        accountId,
        source,
        coordinator,
        InMemoryPostPreferencesRepository(),
        0L,
        PostInteractionExecutionAuthority(),
    )

    @Test
    fun refreshingAListLoadsItsPostsAndPagesWithItsOwnCursor() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = RecordingSource()
            val model = model(source, coordinator)
            advanceUntilIdle()
            val list = FeedSource.UserList("l1", "Artists")

            model.refresh(list)
            advanceUntilIdle()
            assertEquals(listOf("list:l1"), model.feed.value.posts.map { it.id.value })
            assertEquals(list, model.feed.value.feedSource)
            // The built-in timeline stays what it was while a list is shown.
            assertEquals(Timeline.Home, model.feed.value.timeline)

            model.loadMore(list)
            advanceUntilIdle()
            assertEquals(listOf("list:l1", "list:l1next"), model.feed.value.posts.map { it.id.value })
            assertEquals("list:l1" to "next", source.requests.last())
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun switchingFeedClearsTheRowsAndAStaleLoadMoreIsIgnored() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = RecordingSource()
            val model = model(source, coordinator)
            advanceUntilIdle()
            val hashtag = FeedSource.Hashtag("cats", HashtagOrigin.Server)

            model.refresh(hashtag)
            advanceUntilIdle()
            val before = source.requests.size
            // A load-more for a feed that is no longer shown must not reach the server.
            model.loadMore(FeedSource.Timeline(Timeline.Local))
            advanceUntilIdle()

            assertEquals(before, source.requests.size)
            assertEquals(listOf("hashtag:cats"), model.feed.value.posts.map { it.id.value })
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aFailedFeedSwitchRestoresThePreviousFeedAndItsRows() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = RecordingSource(failList = true)
            val model = model(source, coordinator)
            advanceUntilIdle()

            model.refresh(FeedSource.UserList("l1", "Artists"))
            advanceUntilIdle()

            assertEquals(FeedSource.Timeline(Timeline.Home), model.feed.value.feedSource)
            assertEquals(listOf("timeline:Home"), model.feed.value.posts.map { it.id.value })
            assertNotNull(model.feed.value.error)
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aBuiltInTimelineStillGoesThroughTheTimelineRoute() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = RecordingSource()
            val model = model(source, coordinator)
            advanceUntilIdle()

            model.refresh(Timeline.Local)
            advanceUntilIdle()

            assertEquals(Timeline.Local, model.feed.value.timeline)
            assertEquals("timeline:Local" to null, source.requests.last())
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }
}
