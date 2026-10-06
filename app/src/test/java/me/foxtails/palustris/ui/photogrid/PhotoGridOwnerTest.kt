package me.foxtails.palustris.ui.photogrid

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import me.foxtails.palustris.data.preferences.InMemoryPhotoGridPreferencesRepository
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.timelineDisplayOrder
import me.foxtails.palustris.ui.session.ConnectedEntryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoGridOwnerTest {
    private val connection = Connection("https://grid.example", me.foxtails.palustris.domain.Protocol.MISSKEY)
    private val account = AccountId(connection, "owner")
    private val author = Account(account, "Owner", "@owner@grid.example")

    @Test
    fun gridDoesNotLoadUntilRequestedAndUsesIndependentSelection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = GridSource()
        val model = model(source)
        try {
            advanceUntilIdle()
            assertTrue(source.timelineCalls.none { it == Timeline.Bubble })

            model.selectFeed(PhotoGridFeed.TimelineFeed(Timeline.Bubble))
            advanceUntilIdle()

            assertEquals(listOf("bubble-post"), model.state.value.posts.map { it.post.id.value })
            assertEquals(account, model.state.value.posts.single().fetchedBy)
            assertEquals(7L, model.state.value.posts.single().sessionRevision)
        } finally {
            model.release()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun pagingContinuesThroughTextOnlyPagesAndStopsRepeatedCursors() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = GridSource().apply { bubblePages = listOf(
            Page(listOf(post("text-only")), "cursor-1"),
            Page(listOf(post("photo")), "cursor-1"),
        ) }
        val model = model(source)
        try {
            advanceUntilIdle()
            model.selectFeed(PhotoGridFeed.TimelineFeed(Timeline.Bubble))
            advanceUntilIdle()
            model.loadMore()
            advanceUntilIdle()
            assertEquals(listOf("text-only", "photo"), model.state.value.posts.map { it.post.id.value })
            assertEquals(null, model.state.value.nextCursor)
            assertEquals(2, source.bubbleCallCount)
        } finally {
            model.release()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun lateCanceledGridResultCannotReplaceTheSelectedFeed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = GridSource()
        val delayed = CompletableDeferred<Unit>()
        source.bubbleGate = delayed
        val model = model(source)
        try {
            advanceUntilIdle()
            model.selectFeed(PhotoGridFeed.TimelineFeed(Timeline.Bubble))
            model.selectFeed(PhotoGridFeed.TimelineFeed(Timeline.Home))
            advanceUntilIdle()
            delayed.complete(Unit)
            advanceUntilIdle()
            assertEquals(PhotoGridFeed.TimelineFeed(Timeline.Home), model.state.value.selectedFeed)
            assertTrue(model.state.value.posts.none { it.post.id.value == "bubble-post" })
        } finally {
            model.release()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun ownerRejectsForeignPostsAndReleaseIsIdempotent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = model(GridSource())
        try {
            owner.applyExternalPost(OwnedPost(AccountId(connection, "other"), post("foreign"), 7L))
            owner.applyExternalPost(OwnedPost(account, post("stale"), 6L))
            assertFalse(owner.state.value.posts.any { it.post.id.value == "foreign" })
            assertFalse(owner.state.value.posts.any { it.post.id.value == "stale" })
            owner.release()
            owner.release()
            owner.selectFeed(PhotoGridFeed.TimelineFeed(Timeline.Bubble))
            advanceUntilIdle()
            assertTrue(owner.state.value.posts.isEmpty())
        } finally {
            owner.release()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun refreshRejectsLateSuccessAndFailureFromThePreviousRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val successSource = GridSource()
        val oldSuccess = CompletableDeferred<Result<Page<Post>>>()
        val refreshedSuccess = CompletableDeferred<Result<Page<Post>>>()
        successSource.bubbleResults += oldSuccess
        successSource.bubbleResults += refreshedSuccess
        val successOwner = model(successSource)
        try {
            successOwner.selectFeed(PhotoGridFeed.TimelineFeed(Timeline.Bubble))
            runCurrent()
            successOwner.refresh()
            runCurrent()
            oldSuccess.complete(Result.success(Page(listOf(post("old-success")))))
            advanceUntilIdle()
            assertTrue(successOwner.state.value.posts.isEmpty())
            assertTrue(successOwner.state.value.loading)
            refreshedSuccess.complete(Result.success(Page(listOf(post("refreshed")))))
            advanceUntilIdle()
            assertEquals(listOf("refreshed"), successOwner.state.value.posts.map { it.post.id.value })
            assertEquals(2, successSource.cleanedRequests)
        } finally {
            successOwner.release()
        }

        val failureSource = GridSource()
        val oldFailure = CompletableDeferred<Result<Page<Post>>>()
        val refreshedFailure = CompletableDeferred<Result<Page<Post>>>()
        failureSource.bubbleResults += oldFailure
        failureSource.bubbleResults += refreshedFailure
        val failureOwner = model(failureSource)
        try {
            failureOwner.selectFeed(PhotoGridFeed.TimelineFeed(Timeline.Bubble))
            runCurrent()
            failureOwner.refresh()
            runCurrent()
            oldFailure.complete(Result.failure(IllegalStateException("old failure")))
            advanceUntilIdle()
            assertTrue(failureOwner.state.value.error.isNullOrEmpty())
            assertTrue(failureOwner.state.value.loading)
            refreshedFailure.complete(Result.success(Page(listOf(post("fresh-after-failure")))))
            advanceUntilIdle()
            assertEquals(listOf("fresh-after-failure"), failureOwner.state.value.posts.map { it.post.id.value })
            assertEquals(2, failureSource.cleanedRequests)
        } finally {
            failureOwner.release()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun preferencesAreAccountScopedAndHashtagRemovalFollowsRetirement() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = InMemoryPhotoGridPreferencesRepository()
        val other = AccountId(connection, "other")
        val first = PhotoGridOwner(account, GridSource(), 7L, kotlinx.coroutines.CoroutineScope(Dispatchers.Main), repository) { it }
        val second = PhotoGridOwner(other, GridSource(), 8L, kotlinx.coroutines.CoroutineScope(Dispatchers.Main), repository) { it }
        try {
            first.addHashtag("one")
            second.addHashtag("two")
            advanceUntilIdle()
            assertEquals(listOf("one"), first.state.value.savedHashtags)
            assertEquals(listOf("two"), second.state.value.savedHashtags)
            first.release()
            second.release()
            repository.remove(account)
            assertEquals(emptyList<String>(), repositorySnapshot(repository, account).hashtags)
            assertEquals(listOf("two"), repositorySnapshot(repository, other).hashtags)
        } finally {
            first.release()
            second.release()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun optimisticMutationThenExternalChangePreservesPhotoGridRows() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = GridSource()
        val owner = model(source)
        try {
            owner.selectFeed(PhotoGridFeed.TimelineFeed(Timeline.Bubble))
            advanceUntilIdle()
            val original = owner.state.value.posts.single().post
            owner.updateFavouritePreference { it.copy(favourited = true) }
            assertTrue(owner.state.value.posts.single().post.favourited)
            owner.applyExternalPost(OwnedPost(account, original.copy(favourited = true), 7L))
            assertTrue(owner.state.value.posts.single().post.favourited)
            assertEquals("bubble-post", owner.state.value.posts.single().post.text)
        } finally {
            owner.release()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun unsupportedAndDeniedTimelinesDoNotFallbackToHome() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = GridSource().apply {
            capabilities = ServerCapabilities(
                timelineStatuses = mapOf(
                    Timeline.Home to CapabilityStatus.Unsupported,
                    Timeline.Bubble to CapabilityStatus.Denied,
                ),
            )
        }
        val owner = model(source)
        try {
            owner.ensureLoaded()
            advanceUntilIdle()
            assertTrue(owner.state.value.availableTimelines.isEmpty())
            assertTrue(source.timelineCalls.isEmpty())
            assertTrue(owner.state.value.posts.isEmpty())
        } finally {
            owner.release()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun temporaryFailureAndSignInRequiredRemainDistinct() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val temporary = GridSource().apply { failure = IllegalStateException("temporary") }
        val unauthorized = GridSource().apply { failure = SourceError.Unauthorized }
        val first = model(temporary)
        val second = model(unauthorized)
        try {
            first.selectFeed(PhotoGridFeed.TimelineFeed(Timeline.Bubble))
            second.selectFeed(PhotoGridFeed.TimelineFeed(Timeline.Bubble))
            advanceUntilIdle()
            assertEquals("temporary", first.state.value.error)
            assertFalse(first.state.value.needsSignIn)
            assertTrue(second.state.value.needsSignIn)
            assertEquals("", second.state.value.error)
        } finally {
            first.release()
            second.release()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aStoppedOwnerIsNotReusedByTheNextSessionGeneration() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = GridSource()
        val store = ConnectedEntryStore()
        store.beginEntry(1L)
        val old = model(source, revision = 7L)
        store.register(1L, "photo-grid") { old.release() }
        store.beginEntry(2L)
        val replacement = model(source, revision = 8L)
        store.register(2L, "photo-grid") { replacement.release() }
        try {
            old.applyExternalPost(OwnedPost(account, post("late-old"), 7L))
            assertTrue(old.state.value.posts.isEmpty())
            replacement.selectFeed(PhotoGridFeed.TimelineFeed(Timeline.Bubble))
            advanceUntilIdle()
            assertEquals(listOf("bubble-post"), replacement.state.value.posts.map { it.post.id.value })
        } finally {
            store.retireAll()
            old.release()
            replacement.release()
            Dispatchers.resetMain()
        }
    }

    private suspend fun repositorySnapshot(
        repository: InMemoryPhotoGridPreferencesRepository,
        id: AccountId,
    ): me.foxtails.palustris.domain.PhotoGridPreferences = repository.observe(id).first()

    private fun model(source: GridSource, revision: Long = 7L) = PhotoGridOwner(
        accountId = account,
        source = source,
        sessionRevision = revision,
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main),
        preferencesRepository = InMemoryPhotoGridPreferencesRepository(),
        applyFavouritePreference = { it },
    )

    private fun post(id: String) = Post(
        EntityId(connection.origin, id), author, id, 0L, Audience.Public,
    )

    private inner class GridSource : SocialSource {
        override var capabilities = ServerCapabilities(timelines = timelineDisplayOrder.toSet())
        val timelineCalls = mutableListOf<Timeline>()
        var bubblePages = listOf(Page(listOf(post("bubble-post")), "bubble-cursor"))
        var bubbleCallCount = 0
        var bubbleGate: CompletableDeferred<Unit>? = null
        val bubbleResults = ArrayDeque<CompletableDeferred<Result<Page<Post>>>>()
        var cleanedRequests = 0
        var failure: Exception? = null

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> {
            timelineCalls += timeline
            failure?.let { throw it }
            if (timeline == Timeline.Bubble) {
                val call = bubbleCallCount++
                bubbleResults.removeFirstOrNull()?.let { result ->
                    try {
                        return withContext(NonCancellable) { result.await().getOrThrow() }
                    } finally {
                        cleanedRequests += 1
                    }
                }
                if (call == 0) bubbleGate?.await()
                val page = bubblePages.getOrNull(call) ?: Page(emptyList())
                return page
            }
            return Page(listOf(post("home-post")))
        }
    }
}
