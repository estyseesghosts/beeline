package me.foxtails.palustris.ui.profile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ProfileCapability
import me.foxtails.palustris.domain.ProfileTimelineQuery
import me.foxtails.palustris.domain.ProfileTimelineTab
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTimelinePagerTest {
    private val target = AccountId(Connection("https://example.org", Protocol.MASTODON), "target")

    @Test
    fun refreshClearsCursorHistory() = runTest {
        val source = PagingSource(listOf(
            Page(emptyList(), "cursor"),
            Page(emptyList(), null),
            Page(emptyList(), "cursor"),
            Page(emptyList(), null),
        ))
        val states = mutableListOf<Map<ProfileTimelineTab, ProfilePageState>>()
        val pager = pager(source, states, this)

        pager.setTarget(target, 1)
        pager.refresh(target, 1, ProfileTimelineTab.Posts)
        runCurrent()
        pager.loadMore(target, 1, ProfileTimelineTab.Posts)
        runCurrent()
        pager.refresh(target, 1, ProfileTimelineTab.Posts)
        runCurrent()
        pager.loadMore(target, 1, ProfileTimelineTab.Posts)
        runCurrent()

        assertEquals(listOf(null, "cursor", null, "cursor"), source.calls.map { it.second })
    }

    @Test
    fun repeatedCursorTerminatesPaging() = runTest {
        val source = PagingSource(listOf(Page(emptyList(), "cursor"), Page(emptyList(), "cursor")))
        val states = mutableListOf<Map<ProfileTimelineTab, ProfilePageState>>()
        val pager = pager(source, states, this)

        pager.setTarget(target, 1)
        pager.refresh(target, 1, ProfileTimelineTab.Posts)
        runCurrent()
        pager.loadMore(target, 1, ProfileTimelineTab.Posts)
        runCurrent()

        val page = states.last().getValue(ProfileTimelineTab.Posts)
        assertTrue(page.terminal)
        assertEquals(2, source.calls.size)
    }

    @Test
    fun inFlightCursorCannotBeRequestedAgain() = runTest {
        val gate = CompletableDeferred<Unit>()
        val source = PagingSource(listOf(Page(emptyList(), "cursor"), Page(emptyList(), null)))
        val states = mutableListOf<Map<ProfileTimelineTab, ProfilePageState>>()
        val pager = pager(source, states, this)

        pager.setTarget(target, 1)
        pager.refresh(target, 1, ProfileTimelineTab.Posts)
        runCurrent()
        source.gate = gate
        pager.loadMore(target, 1, ProfileTimelineTab.Posts)
        runCurrent()
        assertTrue(states.last().getValue(ProfileTimelineTab.Posts).loadingMore)
        pager.requestPageForTest(target, 1, ProfileTimelineTab.Posts, "cursor")
        runCurrent()

        assertEquals(2, source.calls.size)
        assertTrue(states.last().getValue(ProfileTimelineTab.Posts).terminal)
        gate.complete(Unit)
    }

    @Test
    fun targetChangeClearsCursorHistory() = runTest {
        val other = target.copy(localId = "other")
        val source = PagingSource(listOf(
            Page(emptyList(), "cursor"),
            Page(emptyList(), "cursor"),
            Page(emptyList(), null),
        ))
        val pager = pager(source, mutableListOf(), this)

        pager.setTarget(target, 1)
        pager.refresh(target, 1, ProfileTimelineTab.Posts)
        runCurrent()
        pager.setTarget(other, 2)
        pager.refresh(other, 2, ProfileTimelineTab.Posts)
        runCurrent()
        pager.loadMore(other, 2, ProfileTimelineTab.Posts)
        runCurrent()

        assertEquals(listOf(null, null, "cursor"), source.calls.map { it.second })
    }

    @Test
    fun cursorHistoryStaysBoundedAndEvictsOldestCompletedCursor() = runTest {
        val source = CursorChainSource()
        val pager = pager(source, mutableListOf(), this)

        pager.setTarget(target, 1)
        pager.refresh(target, 1, ProfileTimelineTab.Posts)
        runCurrent()
        repeat(65) {
            pager.loadMore(target, 1, ProfileTimelineTab.Posts)
            runCurrent()
        }
        pager.loadMore(target, 1, ProfileTimelineTab.Posts)
        runCurrent()

        assertEquals(67, source.calls.size)
        assertEquals("c1", source.calls.last())
    }

    @Test
    fun mergedRowsPreserveTransportOrder() = runTest {
        val author = Account(target, "Target", "@target@example.org")
        fun post(id: String) = Post(
            id = EntityId(target.connection.origin, id),
            author = author,
            text = id,
            publishedAtEpochMillis = 0,
            audience = Audience.Public,
        )
        val source = PagingSource(listOf(
            Page(listOf(post("one")), "cursor-a"),
            Page(listOf(post("one"), post("two")), null),
        ))
        val states = mutableListOf<Map<ProfileTimelineTab, ProfilePageState>>()
        val pager = pager(source, states, this)

        pager.setTarget(target, 1)
        pager.refresh(target, 1, ProfileTimelineTab.Posts)
        runCurrent()
        pager.loadMore(target, 1, ProfileTimelineTab.Posts)
        runCurrent()

        // Transport order stays intact while repeated rows collapse into one entry.
        val page = states.last().getValue(ProfileTimelineTab.Posts)
        assertEquals(listOf("one", "two"), page.posts.map { it.post.id.value })
        assertEquals(listOf(null, "cursor-a"), source.calls.map { it.second })
    }

    @Test
    fun oldTargetAndGenerationCannotTriggerAcquisition() = runTest {
        val source = PagingSource(listOf(Page(emptyList(), "cursor"), Page(emptyList(), null)))
        val states = mutableListOf<Map<ProfileTimelineTab, ProfilePageState>>()
        val pager = pager(source, states, this)

        pager.setTarget(target, 1)
        pager.refresh(target, 1, ProfileTimelineTab.Posts)
        runCurrent()
        assertEquals(1, source.calls.size)

        val other = target.copy(localId = "other")
        pager.setTarget(other, 2)
        pager.refresh(target, 1, ProfileTimelineTab.Posts)
        runCurrent()
        pager.loadMore(target, 1, ProfileTimelineTab.Posts)
        runCurrent()
        pager.refresh(other, 1, ProfileTimelineTab.Posts)
        runCurrent()

        // A stale target or a stale generation must not acquire another page.
        assertEquals(1, source.calls.size)
        assertFalse(states.last().containsKey(ProfileTimelineTab.Posts))
    }

    @Test
    fun refreshFailureRetainsRowsAndCursor() = runTest {
        val author = Account(target, "Target", "@target@example.org")
        val first = Post(
            id = EntityId(target.connection.origin, "first"),
            author = author,
            text = "first",
            publishedAtEpochMillis = 0,
            audience = Audience.Public,
        )
        val source = FailingProfileSource(listOf(Page(listOf(first), "cursor-a")))
        val states = mutableListOf<Map<ProfileTimelineTab, ProfilePageState>>()
        val pager = pager(source, states, this)

        pager.setTarget(target, 1)
        pager.refresh(target, 1, ProfileTimelineTab.Posts)
        runCurrent()
        source.error = SourceError.Unauthorized
        pager.refresh(target, 1, ProfileTimelineTab.Posts)
        runCurrent()

        // A failed refresh keeps the rows and the cursor for sign-in recovery.
        val page = states.last().getValue(ProfileTimelineTab.Posts)
        assertEquals(listOf("first"), page.posts.map { it.post.id.value })
        assertEquals("cursor-a", page.nextCursor)
        assertNotNull(page.error)
        assertTrue(page.needsSignIn)
        assertFalse(page.refreshing)
        assertFalse(page.initialLoading)
        assertFalse(page.loadingMore)
    }

    @Test
    fun likedPagerRequestsOnlyWhenCapabilityIsSupported() = runTest {
        CapabilityStatus.entries.forEach { status ->
            val source = LikedCapabilitySource(status)
            val states = mutableListOf<Map<ProfileTimelineTab, ProfilePageState>>()
            val pager = pager(source, states, this)

            pager.setTarget(target, 1)
            pager.refresh(target, 1, ProfileTimelineTab.Liked)
            runCurrent()

            assertEquals(status == CapabilityStatus.Supported, source.calls == 1)
            val page = states.last().getValue(ProfileTimelineTab.Liked)
            if (status == CapabilityStatus.Unknown) assertNull(page.error)
            if (status == CapabilityStatus.Denied) assertNotNull(page.error)
        }
    }

    @Test
    fun defaultProfileCapabilityIsConservative() = runTest {
        val source = PagingSource(listOf(Page(emptyList(), null)))
        val result = source.profileCapability(
            me.foxtails.palustris.domain.ProfileCapabilityQuery(target, ProfileCapability.LikedPosts),
        )

        assertEquals(CapabilityStatus.Unsupported, result.status)
    }

    private fun pager(
        source: SocialSource,
        states: MutableList<Map<ProfileTimelineTab, ProfilePageState>>,
        scope: CoroutineScope,
    ) = ProfileTimelinePager(target, source, scope, 0L, { states += it })

    private class PagingSource(
        private val pages: List<Page<Post>>,
    ) : SocialSource {
        override val capabilities = ServerCapabilities()
        val calls = mutableListOf<Pair<ProfileTimelineQuery, String?>>()
        var gate: CompletableDeferred<Unit>? = null
        private var index = 0

        override suspend fun timeline(timeline: me.foxtails.palustris.domain.Timeline, cursor: String?) = Page<Post>(emptyList())

        override suspend fun profileTimeline(query: ProfileTimelineQuery, cursor: String?): Page<Post> {
            calls += query to cursor
            gate?.await()
            return pages[index++]
        }

        override suspend fun profile(id: AccountId): Account = Account(id, "Target", "@target@example.org")
    }

    private class CursorChainSource : SocialSource {
        override val capabilities = ServerCapabilities()
        val calls = mutableListOf<String?>()

        override suspend fun timeline(timeline: me.foxtails.palustris.domain.Timeline, cursor: String?) = Page<Post>(emptyList())

        override suspend fun profileTimeline(query: ProfileTimelineQuery, cursor: String?): Page<Post> {
            calls += cursor
            val next = when {
                cursor == null -> "c1"
                cursor == "c65" -> "c1"
                else -> "c${cursor.removePrefix("c").toInt() + 1}"
            }
            return Page(emptyList(), next)
        }
    }

    private class FailingProfileSource(
        private val pages: List<Page<Post>>,
    ) : SocialSource {
        override val capabilities = ServerCapabilities()
        var error: Exception? = null
        private var index = 0

        override suspend fun timeline(timeline: me.foxtails.palustris.domain.Timeline, cursor: String?) = Page<Post>(emptyList())

        override suspend fun profileTimeline(query: ProfileTimelineQuery, cursor: String?): Page<Post> {
            error?.let { throw it }
            return pages[index++]
        }

        override suspend fun profile(id: AccountId): Account = Account(id, "Target", "@target@example.org")
    }

    private class LikedCapabilitySource(
        private val status: CapabilityStatus,
    ) : SocialSource {
        override val capabilities = ServerCapabilities()
        var calls = 0

        override suspend fun timeline(timeline: me.foxtails.palustris.domain.Timeline, cursor: String?) = Page<Post>(emptyList())

        override suspend fun profileCapability(query: me.foxtails.palustris.domain.ProfileCapabilityQuery) =
            me.foxtails.palustris.domain.ProfileCapabilityResult(status)

        override suspend fun profileTimeline(query: ProfileTimelineQuery, cursor: String?): Page<Post> {
            calls++
            return Page(emptyList(), null)
        }
    }
}
