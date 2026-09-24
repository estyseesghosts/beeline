package me.foxtails.palustris.data.directmessages

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import me.foxtails.palustris.data.directmessages.DirectMessageRepository
import me.foxtails.palustris.data.directmessages.DirectMessageWriteAuthority
import me.foxtails.palustris.data.directmessages.InMemoryDirectMessageStore
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ConversationId
import me.foxtails.palustris.domain.ConversationIdentity
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.DirectMessageRequest
import me.foxtails.palustris.domain.DirectMessageSource
import me.foxtails.palustris.domain.DirectThreadRequest
import me.foxtails.palustris.domain.DirectThreadResult
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.SourceError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DirectMessageRepositoryTest {
    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val accountId = AccountId(connection, "owner")
    private val owner = Account(accountId, "Owner", "@owner@example.org")
    private val recipient = Account(AccountId(connection, "alice"), "Alice", "@alice@example.org")

    private fun post(id: String, author: Account = recipient) = Post(
        id = EntityId(connection.origin, id),
        author = author,
        text = id,
        publishedAtEpochMillis = 0L,
        audience = Audience.Direct,
    )

    private fun conversation(
        id: String,
        lastId: String,
        unread: Boolean = false,
        identity: ConversationIdentity = ConversationIdentity.Verified,
    ) = DirectConversation(
        id = ConversationId(connection.origin, id),
        participants = listOf(owner, recipient),
        lastPost = post(lastId),
        unread = unread,
        identity = identity,
    )

    private class GatedSource : DirectMessageSource {
        private val inboxPending = ArrayDeque<CompletableDeferred<Page<DirectConversation>>>()
        private val threadPending = ArrayDeque<CompletableDeferred<DirectThreadResult>>()
        private val sendPending = ArrayDeque<CompletableDeferred<Post>>()
        val threadRequests = mutableListOf<DirectThreadRequest>()
        val threadCursors = mutableListOf<String?>()
        var markReadCalls = 0

        override suspend fun conversations(cursor: String?): Page<DirectConversation> {
            val gate = CompletableDeferred<Page<DirectConversation>>()
            inboxPending += gate
            return withContext(NonCancellable) { gate.await() }
        }

        override suspend fun conversationThread(request: DirectThreadRequest, cursor: String?): DirectThreadResult {
            threadRequests += request
            threadCursors += cursor
            val gate = CompletableDeferred<DirectThreadResult>()
            threadPending += gate
            return withContext(NonCancellable) { gate.await() }
        }

        override suspend fun sendDirectMessage(request: DirectMessageRequest): Post {
            val gate = CompletableDeferred<Post>()
            sendPending += gate
            return withContext(NonCancellable) { gate.await() }
        }

        override suspend fun markConversationRead(id: ConversationId) {
            markReadCalls += 1
        }

        fun completeInbox(index: Int, page: Page<DirectConversation>) { inboxPending[index].complete(page) }
        fun completeThread(index: Int, thread: List<Post>) { threadPending[index].complete(DirectThreadResult(thread)) }
        fun completeThreadResult(index: Int, result: DirectThreadResult) { threadPending[index].complete(result) }
        fun completeSend(index: Int, post: Post) { sendPending[index].complete(post) }
    }

    private fun repository(
        authority: DirectMessageWriteAuthority,
        store: InMemoryDirectMessageStore,
        source: GatedSource,
        generation: Long,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) = DirectMessageRepository(accountId, source, store, generation, dispatcher, authority)

    @Test
    fun removedAccountsStayDeleted() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)

        val pending = async { repository.conversations() }
        advanceUntilIdle()
        // Account removal revokes writers before deleting rows.
        authority.invalidateAndDelete(accountId) { store.delete(accountId) }
        source.completeInbox(0, Page(listOf(conversation("late", "late-last"))))
        advanceUntilIdle()

        var cancelled = false
        try {
            pending.await()
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertTrue(store.conversations(accountId).isEmpty())
    }

    @Test
    fun oldSessionCannotWriteAfterReplacement() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val oldGeneration = authority.activate(accountId)
        val oldRepository = repository(authority, store, source, oldGeneration, dispatcher)

        val pending = async {
            oldRepository.send(DirectMessageRequest(listOf(recipient.id), "stale"))
        }
        advanceUntilIdle()
        // Session replacement activates a new writer and revokes the old one.
        val newGeneration = authority.activate(accountId)
        val newRepository = repository(authority, store, source, newGeneration, dispatcher)
        source.completeSend(0, post("stale-post", owner))
        advanceUntilIdle()

        var cancelled = false
        try {
            pending.await()
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertTrue(store.conversations(accountId).isEmpty())

        val accepted = async {
            newRepository.send(DirectMessageRequest(listOf(recipient.id), "fresh"))
        }
        advanceUntilIdle()
        source.completeSend(1, post("fresh-post", owner))
        advanceUntilIdle()
        accepted.await()
        assertEquals(1, store.conversations(accountId).size)
    }

    @Test
    fun twoRepositoriesInOneSessionShareWriterAuthority() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val generation = authority.activate(accountId)
        val first = repository(authority, store, source, generation, dispatcher)
        val second = repository(authority, store, source, generation, dispatcher)

        val pending = async { first.conversations() }
        advanceUntilIdle()
        source.completeInbox(0, Page(listOf(conversation("a", "last"))))
        assertEquals(1, pending.await().items.size)
        advanceUntilIdle()

        val sending = async {
            second.send(
                DirectMessageRequest(listOf(recipient.id), "hi"),
                conversationId = ConversationId(connection.origin, "a"),
                recipientAccounts = listOf(recipient),
            )
        }
        advanceUntilIdle()
        source.completeSend(0, post("sent", owner))
        sending.await()
        advanceUntilIdle()

        assertEquals(1, store.conversations(accountId).size)
        assertEquals("sent", store.conversations(accountId).single().lastPost.id.value)
    }

    @Test
    fun sendAcceptedDuringThreadLoadSurvivesOlderResponse() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)
        val id = ConversationId(connection.origin, "a")
        store.save(accountId, conversation("a", "seed"))

        val loading = async { repository.thread(id) }
        advanceUntilIdle()
        // The send is accepted while the thread request waits.
        val sending = async {
            repository.send(
                DirectMessageRequest(listOf(recipient.id), "hello"),
                conversationId = id,
                recipientAccounts = listOf(recipient),
            )
        }
        advanceUntilIdle()
        source.completeSend(0, post("sent", owner))
        sending.await()
        advanceUntilIdle()
        // The thread response predates the send. It must not drop the sent message or preview.
        source.completeThread(0, listOf(post("old", recipient)))
        val merged = loading.await().posts
        advanceUntilIdle()

        assertTrue(merged.map { it.id.value }.contains("sent"))
        assertEquals("sent", store.conversation(accountId, id)?.lastPost?.id?.value)
    }

    @Test
    fun olderInboxResponseDoesNotReplaceNewerSend() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)
        val id = ConversationId(connection.origin, "a")

        val inbox = async { repository.conversations() }
        advanceUntilIdle()
        val sending = async {
            repository.send(
                DirectMessageRequest(listOf(recipient.id), "hello"),
                conversationId = id,
                recipientAccounts = listOf(recipient),
            )
        }
        advanceUntilIdle()
        source.completeSend(0, post("sent", owner))
        sending.await()
        advanceUntilIdle()
        // The first page predates the send.
        source.completeInbox(0, Page(listOf(conversation("a", "old"))))
        inbox.await()
        advanceUntilIdle()

        assertEquals("sent", store.conversation(accountId, id)?.lastPost?.id?.value)
    }

    @Test
    fun refetchPreservesLocalReadStateForTheSameLastPost() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)

        val first = async { repository.conversations() }
        advanceUntilIdle()
        source.completeInbox(0, Page(listOf(conversation("a", "last", unread = true))))
        first.await()
        advanceUntilIdle()
        store.markRead(accountId, ConversationId(connection.origin, "a"))

        val second = async { repository.conversations() }
        advanceUntilIdle()
        source.completeInbox(1, Page(listOf(conversation("a", "last", unread = true))))
        second.await()
        advanceUntilIdle()

        assertEquals(false, store.conversations(accountId).single().unread)
    }

    @Test
    fun removalOfAnotherAccountKeepsWritesValid() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val other = AccountId(connection, "other")
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)

        val pending = async { repository.conversations() }
        advanceUntilIdle()
        authority.invalidateAndDelete(other) { store.delete(other) }
        source.completeInbox(0, Page(listOf(conversation("a", "last"))))
        val page = pending.await()
        advanceUntilIdle()

        assertEquals(1, page.items.size)
        assertEquals(1, store.conversations(accountId).size)
        assertTrue(store.conversations(other).isEmpty())
    }

    @Test
    fun staleWriterSkipsMarkRead() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)
        val id = ConversationId(connection.origin, "a")
        store.save(accountId, conversation("a", "last", unread = true))

        authority.invalidate(accountId)
        repository.markRead(id)
        advanceUntilIdle()

        assertEquals(1, source.markReadCalls)
        assertEquals(true, store.conversation(accountId, id)?.unread)
    }

    @Test
    fun provisionalConversationClearsLocalUnreadWithoutAServerRequest() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)
        val id = ConversationId(connection.origin, "sent-post")
        store.save(
            accountId,
            conversation("sent-post", "sent-post", unread = true, identity = ConversationIdentity.Provisional),
        )

        repository.markRead(id)
        advanceUntilIdle()

        // A provisional identifier is not a server conversation. Never guess one.
        assertEquals(0, source.markReadCalls)
        assertEquals(false, store.conversation(accountId, id)?.unread)
    }

    @Test
    fun verifiedConversationSendsTheServerMarkRead() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)
        val id = ConversationId(connection.origin, "conversation")
        store.save(accountId, conversation("conversation", "last", unread = true))

        repository.markRead(id)
        advanceUntilIdle()

        assertEquals(1, source.markReadCalls)
        assertEquals(false, store.conversation(accountId, id)?.unread)
    }

    @Test
    fun sendWithoutAConversationStoresAProvisionalIdentity() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)

        val sending = async { repository.send(DirectMessageRequest(listOf(recipient.id), "hello")) }
        advanceUntilIdle()
        source.completeSend(0, post("sent", owner))
        sending.await()
        advanceUntilIdle()

        assertEquals(ConversationIdentity.Provisional, store.conversations(accountId).single().identity)
    }

    @Test
    fun sendIntoAStoredConversationKeepsItsIdentity() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)
        val provisionalId = ConversationId(connection.origin, "sent-post")
        val verifiedId = ConversationId(connection.origin, "conversation")
        store.save(
            accountId,
            conversation("sent-post", "sent-post", identity = ConversationIdentity.Provisional),
        )
        store.save(accountId, conversation("conversation", "last"))

        val first = async {
            repository.send(
                DirectMessageRequest(listOf(recipient.id), "one"),
                conversationId = provisionalId,
                recipientAccounts = listOf(recipient),
            )
        }
        advanceUntilIdle()
        source.completeSend(0, post("next", owner))
        first.await()
        advanceUntilIdle()

        val second = async {
            repository.send(
                DirectMessageRequest(listOf(recipient.id), "two"),
                conversationId = verifiedId,
                recipientAccounts = listOf(recipient),
            )
        }
        advanceUntilIdle()
        source.completeSend(1, post("later", owner))
        second.await()
        advanceUntilIdle()

        assertEquals(
            ConversationIdentity.Provisional,
            store.conversation(accountId, provisionalId)?.identity,
        )
        assertEquals(
            ConversationIdentity.Verified,
            store.conversation(accountId, verifiedId)?.identity,
        )
    }

    @Test
    fun threadWithoutStoredConversationIsUnsupported() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)

        var unsupported = false
        try {
            repository.thread(ConversationId(connection.origin, "missing"))
        } catch (_: SourceError.Unsupported) {
            unsupported = true
        }

        assertTrue(unsupported)
        assertTrue(source.threadRequests.isEmpty())
    }

    @Test
    fun threadAnchorComesFromTheSelectedConversation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)
        val id = ConversationId(connection.origin, "a")
        store.save(accountId, conversation("a", "a-last"))

        val loading = async { repository.thread(id) }
        advanceUntilIdle()
        source.completeThread(0, listOf(post("a-last")))
        loading.await()
        advanceUntilIdle()

        val request = source.threadRequests.single()
        assertEquals(id, request.conversationId)
        assertEquals(EntityId(connection.origin, "a-last"), request.anchor)
    }

    @Test
    fun equalConversationAndPostValuesStaySeparateIdentities() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)
        val id = ConversationId(connection.origin, "same")
        store.save(accountId, conversation("same", "same"))

        val loading = async { repository.thread(id) }
        advanceUntilIdle()
        source.completeThread(0, listOf(post("same")))
        loading.await()
        advanceUntilIdle()

        val request = source.threadRequests.single()
        assertEquals(ConversationId(connection.origin, "same"), request.conversationId)
        assertEquals(EntityId(connection.origin, "same"), request.anchor)
    }

    @Test
    fun threadPassesCursorAndReturnsContinuationState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)
        val id = ConversationId(connection.origin, "a")
        store.save(accountId, conversation("a", "a-last"))
        val remote = DirectThreadResult(
            posts = listOf(post("fresh")),
            nextCursor = "next",
            limitations = emptyList(),
            acquisitionState = me.foxtails.palustris.domain.ThreadAcquisitionState.HasContinuation,
        )

        val loading = async { repository.thread(id, "cursor-in") }
        advanceUntilIdle()
        source.completeThreadResult(0, remote)
        val result = loading.await()
        advanceUntilIdle()

        assertEquals("cursor-in", source.threadCursors.single())
        assertEquals("next", result.nextCursor)
        assertEquals(me.foxtails.palustris.domain.ThreadAcquisitionState.HasContinuation, result.acquisitionState)
        assertTrue(result.limitations.isEmpty())
        assertTrue(result.posts.map { it.id.value }.contains("fresh"))
    }

    @Test
    fun threadMergesRemotePostsWithCachedThread() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)
        val id = ConversationId(connection.origin, "a")
        store.save(accountId, conversation("a", "a-last"))
        store.save(accountId, conversation("a", "a-last"), listOf(post("cached")))

        val loading = async { repository.thread(id) }
        advanceUntilIdle()
        source.completeThreadResult(
            0,
            DirectThreadResult(
                posts = listOf(post("fresh")),
                limitations = listOf(me.foxtails.palustris.domain.ThreadLimitation.UncertainServerTruncation),
                acquisitionState = me.foxtails.palustris.domain.ThreadAcquisitionState.Limited,
            ),
        )
        val result = loading.await()
        advanceUntilIdle()

        assertEquals(listOf("fresh", "cached"), result.posts.map { it.id.value })
        assertEquals(listOf("fresh", "cached"), store.thread(accountId, id).map { it.id.value })
        assertEquals(me.foxtails.palustris.domain.ThreadAcquisitionState.Limited, result.acquisitionState)
        assertEquals(
            listOf(me.foxtails.palustris.domain.ThreadLimitation.UncertainServerTruncation),
            result.limitations,
        )
    }

    @Test
    fun continuationBatchAppendsAfterCachedRowsAndSelectsNewestPreview() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)
        val id = ConversationId(connection.origin, "a")
        val cached = post("cached").copy(publishedAtEpochMillis = 100L)
        val fresh = post("fresh").copy(publishedAtEpochMillis = 200L)
        store.save(accountId, conversation("a", "cached").copy(lastPost = cached), listOf(cached))

        val loading = async { repository.thread(id, "cursor-in") }
        advanceUntilIdle()
        source.completeThreadResult(
            0,
            DirectThreadResult(
                // The continuation repeats the cached row and adds a newer post.
                posts = listOf(cached, fresh),
                nextCursor = null,
                limitations = emptyList(),
                acquisitionState = me.foxtails.palustris.domain.ThreadAcquisitionState.Finished,
            ),
        )
        val result = loading.await()
        advanceUntilIdle()

        // Continuation batches append after stored rows with cross-call dedup.
        assertEquals(listOf("cached", "fresh"), result.posts.map { it.id.value })
        assertEquals(listOf("cached", "fresh"), store.thread(accountId, id).map { it.id.value })
        // The preview follows the newest timestamp, not the last row position.
        assertEquals("fresh", store.conversation(accountId, id)?.lastPost?.id?.value)
    }

    @Test
    fun cachedNewerPreviewSurvivesOlderThreadRows() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)
        val id = ConversationId(connection.origin, "a")
        val cached = post("cached").copy(publishedAtEpochMillis = 300L)
        val older = post("older").copy(publishedAtEpochMillis = 100L)
        store.save(accountId, conversation("a", "cached").copy(lastPost = cached), listOf(cached))

        val loading = async { repository.thread(id) }
        advanceUntilIdle()
        source.completeThreadResult(
            0,
            DirectThreadResult(
                posts = listOf(older),
                nextCursor = null,
                limitations = emptyList(),
                acquisitionState = me.foxtails.palustris.domain.ThreadAcquisitionState.Finished,
            ),
        )
        val result = loading.await()
        advanceUntilIdle()

        // The inbox lastPost is newer than every returned row and may not exist
        // in the bounded thread rows. It stays the persisted preview.
        assertTrue(result.posts.map { it.id.value }.contains("cached"))
        assertEquals("cached", store.conversation(accountId, id)?.lastPost?.id?.value)
    }
}
