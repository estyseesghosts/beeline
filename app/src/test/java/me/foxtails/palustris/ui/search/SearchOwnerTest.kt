package me.foxtails.palustris.ui.search

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.ui.session.ConnectedEntryStore
import me.foxtails.palustris.ui.shell.PostProjectionCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchOwnerTest {
    private val connection = Connection("https://search.example", Protocol.MISSKEY)
    private val account = AccountId(connection, "owner")
    private val foreign = AccountId(connection, "other")
    private val author = Account(account, "Owner", "@owner@search.example")

    private fun post(id: String) = Post(
        id = EntityId(connection.origin, id),
        author = author,
        text = id,
        publishedAtEpochMillis = 0L,
        audience = Audience.Public,
    )

    private class Source : SocialSource {
        override val capabilities = ServerCapabilities()
        val pending = CompletableDeferred<Page<Post>>()
        var cancellationCount = 0
        var cleanupCount = 0

        override suspend fun timeline(timeline: me.foxtails.palustris.domain.Timeline, cursor: String?) = Page<Post>(emptyList())
        override suspend fun searchHashtag(tag: String, cursor: String?): Page<Post> = try {
            pending.await()
        } catch (error: CancellationException) {
            cancellationCount++
            throw error
        } finally {
            cleanupCount++
        }
    }

    @Test
    fun accountRemovalReleasesSearchAndRejectsLateResult() = runTest {
        val source = Source()
        val owner = SearchOwner(account, source, 4L, this, ::identity)
        val store = ConnectedEntryStore()
        store.beginEntry(9L)
        store.register(9L, "search") { owner.release() }

        owner.search("#cats")
        advanceUntilIdle()
        store.retireAll()
        source.pending.complete(Page(listOf(post("late"))))
        advanceUntilIdle()

        assertEquals(1, source.cancellationCount)
        assertEquals(1, source.cleanupCount)
        assertTrue(owner.state.value.posts.isEmpty())
    }

    @Test
    fun sessionReplacementCancelsOldRequestAndRejectsItsResult() = runTest {
        val oldSource = Source()
        val oldOwner = SearchOwner(account, oldSource, 4L, this, ::identity)
        oldOwner.search("#old")
        advanceUntilIdle()

        oldOwner.release()
        val newSource = Source()
        val newOwner = SearchOwner(account, newSource, 5L, this, ::identity)
        newOwner.search("#new")
        advanceUntilIdle()
        oldSource.pending.complete(Page(listOf(post("old-late"))))
        newSource.pending.complete(Page(listOf(post("new-result"))))
        advanceUntilIdle()

        assertEquals(1, oldSource.cancellationCount)
        assertEquals(1, oldSource.cleanupCount)
        assertTrue(oldOwner.state.value.posts.isEmpty())
        assertEquals(listOf(post("new-result")), newOwner.state.value.posts)
    }

    @Test
    fun switchingAccountRejectsOldResultsAndKeepsStateIsolated() = runTest {
        val oldSource = Source()
        val oldOwner = SearchOwner(account, oldSource, 4L, this, ::identity)
        oldOwner.search("#old")
        advanceUntilIdle()

        oldOwner.release()
        val newSource = Source()
        val newOwner = SearchOwner(foreign, newSource, 4L, this, ::identity)
        newOwner.search("#new")
        advanceUntilIdle()
        oldSource.pending.complete(Page(listOf(post("old-late"))))
        newSource.pending.complete(Page(listOf(post("new-result"))))
        advanceUntilIdle()

        assertEquals(1, oldSource.cancellationCount)
        assertTrue(oldOwner.state.value.posts.isEmpty())
        assertEquals(listOf(post("new-result")), newOwner.state.value.posts)
    }

    @Test
    fun foreignAndOldRevisionUpdatesDoNotReachSearch() = runTest {
        val owner = SearchOwner(account, Source(), 4L, this, ::identity)
        owner.applyExternalPost(OwnedPost(foreign, post("foreign"), 4L))
        owner.applyExternalPost(OwnedPost(account, post("old"), 3L))
        owner.applyExternalPost(OwnedPost(account, post("accepted"), 4L))

        assertEquals(emptyList<Post>(), owner.state.value.posts)
    }

    @Test
    fun coordinatorDeliversExternalAndAcceptedPublicationUpdatesWithoutHome() = runTest {
        val source = Source()
        val owner = SearchOwner(account, source, 4L, this, ::identity)
        owner.search("#cats")
        advanceUntilIdle()
        source.pending.complete(Page(listOf(post("result"))))
        advanceUntilIdle()
        val coordinator = PostProjectionCoordinator(account, 4L)
        val searchSink = object : PostProjectionCoordinator.Sink {
            override fun applyExternalPost(updated: OwnedPost) = owner.applyExternalPost(updated)
            override fun applyPublishedPost(request: CreatePostRequest) = owner.applyPublishedPost(request)
        }
        val origin = object : PostProjectionCoordinator.Sink {
            override fun applyExternalPost(updated: OwnedPost) = Unit
        }
        coordinator.register(searchSink)
        coordinator.register(origin)

        coordinator.forwardExternalPost(origin, OwnedPost(account, post("result").copy(
            myReaction = "👍",
            interactionCounts = post("result").interactionCounts.copy(reactionCount = 0, quoteRepostCount = 0),
        ), 4L))
        coordinator.forwardPublishedPost(origin, CreatePostRequest("reply", quoteOf = post("result").id), OwnedPost(account, post("new"), 4L))

        assertEquals("👍", owner.state.value.posts.single().myReaction)
        assertEquals(1, owner.state.value.posts.single().interactionCounts.quoteRepostCount)
    }

    @Test
    fun recreationUnregistersOldSearchBeforeRegisteringOneNewSink() = runTest {
        val source = Source()
        val owner = SearchOwner(account, source, 4L, this, ::identity)
        val recreated = SearchOwner(account, source, 4L, this, ::identity)
        val coordinator = PostProjectionCoordinator(account, 4L)
        var oldDeliveries = 0
        var newDeliveries = 0
        val oldSink = sink(owner) { oldDeliveries++ }
        val newSink = sink(recreated) { newDeliveries++ }
        val origin = object : PostProjectionCoordinator.Sink {
            override fun applyExternalPost(updated: OwnedPost) = Unit
        }
        val store = ConnectedEntryStore()
        store.beginEntry(9L)
        coordinator.register(oldSink)
        store.register(9L, "search-$account-9") {
            owner.release()
            coordinator.unregister(oldSink)
        }
        coordinator.unregister(oldSink)
        coordinator.register(newSink)

        coordinator.forwardExternalPost(origin, OwnedPost(account, post("missing"), 4L))

        assertEquals(0, oldDeliveries)
        assertEquals(1, newDeliveries)
        assertEquals(emptyList<Post>(), owner.state.value.posts)
        assertEquals(emptyList<Post>(), recreated.state.value.posts)
        store.retireAll()
    }

    @Test
    fun releaseAndDisposableTeardownRunCleanupOnce() = runTest {
        val source = Source()
        val owner = SearchOwner(account, source, 4L, this, ::identity)
        owner.search("#cats")
        advanceUntilIdle()
        val coordinator = PostProjectionCoordinator(account, 4L)
        val sink = sink(owner)
        coordinator.register(sink)
        val store = ConnectedEntryStore()
        store.beginEntry(9L)
        store.register(9L, "search") {
            owner.release()
            coordinator.unregister(sink)
        }

        store.retireAll()
        coordinator.unregister(sink)
        owner.release()
        advanceUntilIdle()

        assertEquals(1, source.cancellationCount)
        assertEquals(1, source.cleanupCount)
    }

    @Test
    fun optimisticPublicationAndExternalUpdateReachSearch() = runTest {
        val source = Source()
        val owner = SearchOwner(account, source, 4L, this, ::identity)
        owner.search("#cats")
        advanceUntilIdle()
        source.pending.complete(Page(listOf(post("result").copy(
            interactionCounts = post("result").interactionCounts.copy(replyCount = 1),
        ))))
        advanceUntilIdle()
        val coordinator = PostProjectionCoordinator(account, 4L)
        val searchSink = sink(owner)
        val origin = object : PostProjectionCoordinator.Sink {
            override fun applyExternalPost(updated: OwnedPost) = Unit
        }
        coordinator.register(searchSink)
        coordinator.register(origin)

        coordinator.forwardPublishedPost(origin, CreatePostRequest("reply", replyTo = post("result").id), OwnedPost(account, post("created"), 4L))
        coordinator.forwardExternalPost(origin, OwnedPost(account, post("result").copy(
            myReaction = "👍",
            interactionCounts = post("result").interactionCounts.copy(reactionCount = 0, replyCount = 2),
        ), 4L))

        assertEquals("👍", owner.state.value.posts.single().myReaction)
        assertEquals(2, owner.state.value.posts.single().interactionCounts.replyCount)
    }

    private fun sink(owner: SearchOwner, onExternal: () -> Unit = {}) = object : PostProjectionCoordinator.Sink {
        override fun applyExternalPost(updated: OwnedPost) {
            onExternal()
            owner.applyExternalPost(updated)
        }
        override fun applyPublishedPost(request: CreatePostRequest) = owner.applyPublishedPost(request)
    }

    private fun identity(post: Post): Post = post
}
