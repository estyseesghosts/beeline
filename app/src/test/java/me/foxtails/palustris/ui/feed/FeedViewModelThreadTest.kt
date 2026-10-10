package me.foxtails.palustris.ui.feed

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.foxtails.palustris.data.auth.InMemoryDraftStore
import me.foxtails.palustris.data.notifications.NotificationSyncOrchestrator
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.PostDraftEntry
import me.foxtails.palustris.domain.PreparedThreadImage
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.ThreadImageLimits
import me.foxtails.palustris.domain.ThreadImagePreparer
import me.foxtails.palustris.domain.ThreadPublication
import me.foxtails.palustris.domain.ThreadPublicationEntry
import me.foxtails.palustris.domain.ThreadPublishFailure
import me.foxtails.palustris.domain.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelThreadTest {
    private val connection = Connection("https://example.org", Protocol.MISSKEY)
    private val accountId = AccountId(connection, "owner")
    private val author = Account(AccountId(connection, "author"), "Author", "@author@example.org")

    private fun post(id: String) = Post(
        id = EntityId(connection.origin, id),
        author = author,
        text = id,
        publishedAtEpochMillis = 0,
        audience = Audience.Public,
    )

    private inner class FakeSource(var failCreateAt: Int = Int.MAX_VALUE) : SocialSource {
        override val capabilities = ServerCapabilities(timelines = setOf(Timeline.Home))
        val creates = mutableListOf<CreatePostRequest>()
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
        override suspend fun create(request: CreatePostRequest): Post {
            creates += request
            if (creates.size >= failCreateAt) throw IOException("create failed")
            return this@FeedViewModelThreadTest.post("created-${creates.size}")
        }
        override suspend fun searchHashtag(tag: String, cursor: String?): Page<Post> = Page(emptyList())
    }

    private object EmptyPreparer : ThreadImagePreparer {
        override suspend fun prepare(
            accountId: AccountId?,
            draftId: String,
            media: me.foxtails.palustris.domain.ThreadPublicationMedia,
            compress: Boolean,
            limits: ThreadImageLimits,
            onProgress: (Float) -> Unit,
        ): PreparedThreadImage = object : PreparedThreadImage {
            override val fileName = media.fileName
            override val mimeType = media.mimeType
            override fun open(): InputStream = ByteArrayInputStream(ByteArray(0))
            override fun release() = Unit
        }
    }

    private fun entry(id: String, text: String) = ThreadPublicationEntry(id = id, text = text)

    private fun publication(entries: List<ThreadPublicationEntry>) = ThreadPublication(
        draftId = "draft-1",
        entries = entries,
        audience = Audience.Public,
        accountId = accountId,
    )

    private fun model(source: FakeSource, drafts: InMemoryDraftStore, coordinator: NotificationSyncOrchestrator): FeedViewModel = FeedViewModel(
        accountId,
        source,
        coordinator,
        me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository(),
        0L,
        me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority(),
        drafts,
        EmptyPreparer,
    )

    @Test
    fun successReportsProgressDeletesTheDraftAndReturnsEveryPost() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = FakeSource()
            val drafts = InMemoryDraftStore()
            drafts.save(PostDraft(id = "draft-1", accountId = accountId, text = "one"))
            val model = FeedViewModel(
                accountId,
                source,
                coordinator,
                me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository(),
                0L,
                me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority(),
                drafts,
                EmptyPreparer,
            )
            advanceUntilIdle()
            val progress = mutableListOf<Pair<Int, Int>>()
            var accepted: List<OwnedPost> = emptyList()
            model.create(
                publication(listOf(entry("e1", "one"), entry("e2", "two"), entry("e3", "three"))),
                object : me.foxtails.palustris.domain.ThreadPublishListener {
                    override fun onProgress(posted: Int, total: Int) {
                        progress += posted to total
                    }
                    override fun onAccepted(created: List<OwnedPost>, requests: List<CreatePostRequest>) {
                        accepted = created
                    }
                    override fun onError(failure: ThreadPublishFailure) = error("Must not fail.")
                },
            )
            advanceUntilIdle()

            assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progress)
            assertEquals(listOf("created-1", "created-2", "created-3"), accepted.map { it.post.id.value })
            assertTrue(drafts.list(accountId).isEmpty())
            assertFalse(model.feed.value.publishing)
            assertEquals(0, model.feed.value.publishPosted)
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failureWritesTheRemainingDraftAndNamesTheFailedEntry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = FakeSource(failCreateAt = 2)
            val drafts = InMemoryDraftStore()
            val model = model(source, drafts, coordinator)
            advanceUntilIdle()
            var captured: ThreadPublishFailure? = null
            model.create(
                publication(listOf(entry("e1", "one"), entry("e2", "two"), entry("e3", "three"))),
                object : me.foxtails.palustris.domain.ThreadPublishListener {
                    override fun onError(failure: ThreadPublishFailure) {
                        captured = failure
                    }
                },
            )
            advanceUntilIdle()

            val remaining = drafts.list(accountId).single()
            assertEquals("two", remaining.text)
            assertEquals(listOf("e3"), remaining.followUps.map(PostDraftEntry::id))
            assertEquals("created-1", remaining.replyTo?.value)
            val error = model.feed.value.error.orEmpty()
            assertTrue(error.contains("2") && error.contains("3"))
            assertEquals(1, captured?.failedIndex)
            assertFalse(model.feed.value.publishing)
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun stopDuringPublishPostsNothingMore() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = FakeSource()
            val drafts = InMemoryDraftStore()
            val model = model(source, drafts, coordinator)
            advanceUntilIdle()
            model.stop()
            var called = false
            model.create(
                publication(listOf(entry("e1", "one"))),
                object : me.foxtails.palustris.domain.ThreadPublishListener {
                    override fun onAccepted(created: List<OwnedPost>, requests: List<CreatePostRequest>) {
                        called = true
                    }
                },
            )
            advanceUntilIdle()

            assertTrue(source.creates.isEmpty())
            assertFalse(called)
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }
}
