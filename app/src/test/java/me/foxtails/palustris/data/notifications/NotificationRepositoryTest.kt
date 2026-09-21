package me.foxtails.palustris.data.notifications

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import me.foxtails.palustris.data.notifications.InMemoryNotificationStore
import me.foxtails.palustris.data.notifications.NotificationIngestRequest
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.data.notifications.NotificationRepositoryState
import me.foxtails.palustris.data.notifications.NotificationStoreRead
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.NotificationCursor
import me.foxtails.palustris.domain.Event
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationAcknowledgement
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationLabel
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationDeliveryState
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationPageDirection
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationReadStatus
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.NotificationSyncCompleteness
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.NotificationDestination
import me.foxtails.palustris.domain.ValidatedUrl
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostInteractionCounts
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.SocialEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRepositoryTest {
    private val account = AccountId(Connection("https://example.org", Protocol.MISSKEY), "receiver")

    @Test
    fun duplicatePagesMergeWithoutLosingLocalSeenOrUnreadPrecision() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        val notification = notification("one", NotificationActivity.Reply)
        val page = NotificationPage(
            items = listOf(notification),
            checkpoint = NotificationCheckpoint(account, NotificationQuery(), capturedAtEpochMillis = 10),
            unreadState = NotificationUnreadState.Exact(1),
        )

        assertTrue(repository.establishBaseline(token, ingestRequest(page), page))
        assertTrue(repository.markSeen(token, notification.id))
        val unreadPage = page.copy(
            items = listOf(notification.copy(
                readState = notification.readState.copy(status = NotificationReadStatus.Unread),
            )),
        )
        assertTrue(repository.establishBaseline(token, ingestRequest(unreadPage), unreadPage))

        val state = repository.observe(account).value
        assertEquals(1, state.items.size)
        assertEquals(NotificationReadStatus.Unread, state.items.single().readState.status)
        assertTrue(state.items.single().readState.locallySeen)
        assertEquals(NotificationUnreadState.Exact(1), state.unreadState)
    }

    @Test
    fun invalidatedGenerationCannotRecreateRemovedAccountRows() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 4)
        repository.activate(token)
        repository.invalidate(account, token.generation)

        val late = NotificationPage(listOf(notification("late", NotificationActivity.Follow)))
        assertFalse(repository.establishBaseline(token, ingestRequest(late), late))
        assertTrue(repository.observe(account).value.items.isEmpty())
    }

    @Test
    fun generationZeroWriterCannotRecreateAfterRemoval() = runBlocking {
        val store = InMemoryNotificationStore()
        val repository = NotificationRepository(store)
        val initial = NotificationSyncToken(account, 0)
        val page = NotificationPage(listOf(notification("before-remove", NotificationActivity.Follow)))

        assertTrue(repository.establishBaseline(initial, ingestRequest(page), page))
        repository.remove(account)

        val late = NotificationPage(listOf(notification("after-remove", NotificationActivity.Follow)))
        assertFalse(repository.establishBaseline(initial, ingestRequest(late), late))
        assertTrue(store.read(account) is NotificationStoreRead.Absent)
    }

    @Test
    fun generationZeroWriterIsValidBeforeFirstRemovalAndRejectedAfterward() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 0)

        assertTrue(repository.updateUnreadState(token, NotificationUnreadState.Exact(1)))
        repository.remove(account)

        assertFalse(repository.updateUnreadState(token, NotificationUnreadState.Exact(2)))
        assertTrue(repository.observe(account).value.items.isEmpty())
    }

    @Test
    fun removalWaitsForInFlightNotificationWriteAndDeletesCommittedState() = runBlocking {
        val store = GatedNotificationStore()
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)

        val writer = async {
            repository.establishBaseline(
                token,
                baselineRequest(),
                NotificationPage(listOf(notification("in-flight", NotificationActivity.Follow))),
            )
        }
        store.writeEntered.await()

        val removal = async { repository.remove(account) }
        yield()
        assertFalse(removal.isCompleted)

        store.releaseWrite.complete(Unit)
        assertTrue(writer.await())
        removal.await()

        assertTrue(store.read(account) is NotificationStoreRead.Absent)
        assertFalse(repository.establishBaseline(
            token,
            baselineRequest(),
            NotificationPage(listOf(notification("late", NotificationActivity.Follow))),
        ))
        assertTrue(repository.observe(account).value.items.isEmpty())
        val writeLocks = NotificationRepository::class.java.getDeclaredField("writeLocks").apply {
            isAccessible = true
        }.get(repository) as Map<*, *>
        assertFalse(writeLocks.containsKey(account))
    }

    @Test
    fun cancelledNotificationLockWaiterDoesNotResurrectAfterRemoval() = runBlocking {
        val store = GatedNotificationStore()
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)

        val writer = async {
            repository.updateUnreadState(token, NotificationUnreadState.Exact(1))
        }
        store.writeEntered.await()

        val waiter = launch {
            repository.updateUnreadState(token, NotificationUnreadState.Exact(2))
        }
        yield()
        waiter.cancelAndJoin()
        store.releaseWrite.complete(Unit)
        assertTrue(writer.await())

        repository.remove(account)
        assertFalse(repository.updateUnreadState(token, NotificationUnreadState.Exact(3)))
        assertTrue(store.read(account) is NotificationStoreRead.Absent)
    }

    @Test
    fun notificationRemovalDoesNotBlockWritesForAnotherAccount() = runBlocking {
        val other = account.copy(localId = "other")
        val store = GatedNotificationStore()
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        val otherToken = NotificationSyncToken(other, 1)
        repository.activate(token)
        repository.activate(otherToken)

        val writer = async {
            repository.updateUnreadState(token, NotificationUnreadState.Exact(1))
        }
        store.writeEntered.await()
        val removal = async { repository.remove(account) }
        yield()

        assertTrue(repository.updateUnreadState(otherToken, NotificationUnreadState.Exact(1)))
        store.releaseWrite.complete(Unit)
        assertTrue(writer.await())
        removal.await()
        assertEquals(NotificationUnreadState.Exact(1), repository.observe(other).value.unreadState)
    }

    @Test
    fun cancellationDuringRemovalPropagatesAndLeavesTheAccountInvalidated() = runBlocking {
        val store = object : NotificationStore {
            override fun read(accountId: AccountId): NotificationStoreRead = NotificationStoreRead.Absent
            override fun write(accountId: AccountId, state: NotificationRepositoryState) {}
            override fun delete(accountId: AccountId) = throw CancellationException("cancel delete")
        }
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)

        var cancelled = false
        try {
            repository.remove(account)
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertFalse(repository.establishBaseline(token, ingestRequest(NotificationPage(
            listOf(notification("late", NotificationActivity.Follow)),
        )), NotificationPage(listOf(notification("late", NotificationActivity.Follow)))))
    }

    @Test
    fun positiveActivationClearsRetiredAccountTombstone() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val old = NotificationSyncToken(account, 1)
        repository.activate(old)
        repository.remove(account)

        val replacement = NotificationSyncToken(account, 2)
        repository.activate(replacement)

        assertTrue(repository.establishBaseline(
            replacement,
            ingestRequest(NotificationPage(listOf(notification("replacement", NotificationActivity.Follow)))),
            NotificationPage(listOf(notification("replacement", NotificationActivity.Follow))),
        ))
    }

    @Test
    fun reactivationAfterRemovalUsesTheNewGeneration() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val old = NotificationSyncToken(account, 1)
        repository.activate(old)
        repository.remove(account)

        val replacement = NotificationSyncToken(account, 2)
        repository.activate(replacement)

        assertTrue(repository.updateUnreadState(replacement, NotificationUnreadState.Exact(1)))
        assertEquals(NotificationUnreadState.Exact(1), repository.observe(account).value.unreadState)
    }

    @Test
    fun stalePositiveActivationCannotClearRetiredAccountTombstone() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val old = NotificationSyncToken(account, 1)
        repository.activate(old)
        repository.remove(account)

        repository.activate(old)

        assertFalse(repository.updateUnreadState(old, NotificationUnreadState.Exact(1)))
        assertEquals(null, repository.currentToken(account))
    }

    @Test
    fun tombstonedObserveSkipsStoreReadAndCreatesNoEntry() = runBlocking {
        val counting = CountingNotificationStore()
        val repository = NotificationRepository(counting)
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        repository.remove(account)

        counting.reads = 0
        val state = repository.observe(account).value

        // A retired account returns a transient empty value. No store read runs.
        // No map entry appears.
        assertTrue(state.items.isEmpty())
        assertEquals(NotificationUnreadState.Unknown, state.unreadState)
        assertEquals(0, counting.reads)
        assertFalse(stateKeys(repository).contains(account))
        repository.observe(account)
        assertEquals(0, counting.reads)
        assertFalse(stateKeys(repository).contains(account))
    }

    @Test
    fun acknowledgementSeparatesServerReadAndAndroidPresentationState() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        val item = notification("one", NotificationActivity.Mention)
        val first = NotificationPage(listOf(item), unreadState = NotificationUnreadState.Exact(1))
        repository.establishBaseline(token, ingestRequest(first), first)

        assertTrue(repository.markPresented(token, item.id))
        assertTrue(repository.acknowledge(
            token,
            NotificationAcknowledgement(account, NotificationUnreadState.None, 20),
        ))

        val readState = repository.observe(account).value.items.single().readState
        assertEquals(NotificationReadStatus.Read, readState.status)
        assertTrue(readState.serverAcknowledged)
        assertTrue(readState.androidPresented)
    }

    @Test
    fun androidDismissalIsPersistedWithoutMarkingNotificationRead() = runBlocking {
        val store = InMemoryNotificationStore()
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        val item = notification("swiped", NotificationActivity.Mention)
        val swiped = NotificationPage(listOf(item), unreadState = NotificationUnreadState.Exact(1))
        repository.establishBaseline(token, ingestRequest(swiped), swiped)

        assertTrue(repository.markAndroidDismissed(account, item.id))

        val readState = repository.observe(account).value.items.single().readState
        assertEquals(NotificationReadStatus.Unknown, readState.status)
        assertFalse(readState.serverAcknowledged)
        assertFalse(readState.androidPresented)
        assertTrue(readState.androidDismissed)
        assertTrue(NotificationRepository(store).observe(account).value.items.single().readState.androidDismissed)
    }

    @Test
    fun localDismissalIsAStableTombstoneAcrossRepositoryRecreation() = runBlocking {
        val store = InMemoryNotificationStore()
        val first = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        first.activate(token)
        val item = notification("dismissed", NotificationActivity.Follow)
        first.establishBaseline(token, baselineRequest(), NotificationPage(
            items = listOf(item),
            checkpoint = NotificationCheckpoint(account, NotificationQuery()),
        ))
        first.dismissFromInbox(token, item.id, remoteApplied = false)

        val restarted = NotificationRepository(store)
        restarted.activate(token)
        restarted.establishBaseline(token, baselineRequest(), NotificationPage(
            items = listOf(item),
            checkpoint = NotificationCheckpoint(account, NotificationQuery()),
        ))

        assertTrue(restarted.observe(account).value.items.isEmpty())
        assertTrue(item.id in restarted.observe(account).value.dismissedIds)
    }

    @Test
    fun olderAndNewerWritesAdvanceOnlyTheirOwnBoundary() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val query = NotificationQuery()
        repository.activate(token)
        repository.establishBaseline(token, baselineRequest(query), NotificationPage(
            items = listOf(notification("initial", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(
                account,
                query,
                newest = me.foxtails.palustris.domain.NotificationCursor("newest-1"),
                oldest = me.foxtails.palustris.domain.NotificationCursor("oldest-1"),
                capturedAtEpochMillis = 1,
            ),
            direction = NotificationPageDirection.Initial,
        ))
        repository.ingestOlderPage(token, olderRequest(query), NotificationPage(
            items = listOf(notification("older", NotificationActivity.Mention)),
            olderCursor = me.foxtails.palustris.domain.NotificationCursor("oldest-2"),
            checkpoint = NotificationCheckpoint(
                account,
                query,
                newest = me.foxtails.palustris.domain.NotificationCursor("should-not-win"),
                oldest = me.foxtails.palustris.domain.NotificationCursor("oldest-2"),
                capturedAtEpochMillis = 2,
            ),
            direction = NotificationPageDirection.Older,
        ))
        assertEquals(
            "newest-1",
            repository.checkpoint(account, query)?.newest?.value,
        )
        assertEquals("oldest-2", repository.checkpoint(account, query)?.oldest?.value)

        repository.ingestNewerPage(token, newerRequest(query), NotificationPage(
            items = listOf(notification("newer", NotificationActivity.Mention)),
            newerCursor = me.foxtails.palustris.domain.NotificationCursor("newest-2"),
            checkpoint = NotificationCheckpoint(
                account,
                query,
                newest = me.foxtails.palustris.domain.NotificationCursor("newest-2"),
                oldest = me.foxtails.palustris.domain.NotificationCursor("should-not-win"),
                capturedAtEpochMillis = 3,
            ),
            direction = NotificationPageDirection.Newer,
        ))
        assertEquals("newest-2", repository.checkpoint(account, query)?.newest?.value)
        assertEquals("oldest-2", repository.checkpoint(account, query)?.oldest?.value)
    }

    @Test
    fun streamArrivalPreservesEachCachedRowsReadState() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val alreadyRead = notification("read", NotificationActivity.Follow).copy(
            readState = me.foxtails.palustris.domain.NotificationReadState(NotificationReadStatus.Read),
        )
        repository.activate(token)
        repository.establishBaseline(token, baselineRequest(), NotificationPage(listOf(alreadyRead)))

        assertTrue(repository.applyStreamEvent(token, Event(
            account,
            SocialEvent.NotificationReceived(notification("new", NotificationActivity.Mention)),
        )))

        assertEquals(
            NotificationReadStatus.Read,
            repository.observe(account).value.items.single { it.id == alreadyRead.id }.readState.status,
        )
    }

    @Test
    fun olderHistoryAndOverlappingPagesDoNotCreateAudibleDeliveries() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val query = NotificationQuery()
        val baseline = notification("baseline", NotificationActivity.Mention)
        repository.activate(token)
        repository.establishBaseline(token, baselineRequest(query), NotificationPage(
            items = listOf(baseline),
            checkpoint = NotificationCheckpoint(account, query),
        ))

        repository.ingestOlderPage(token, olderRequest(query), NotificationPage(
            items = listOf(notification("older", NotificationActivity.Follow)),
            olderCursor = me.foxtails.palustris.domain.NotificationCursor("older-next"),
            checkpoint = NotificationCheckpoint(
                account,
                query,
                oldest = me.foxtails.palustris.domain.NotificationCursor("older-next"),
            ),
            direction = NotificationPageDirection.Older,
        ))
        repository.ingestNewerPage(token, newerRequest(query), NotificationPage(
            items = listOf(baseline, notification("newer", NotificationActivity.Reply)),
            newerCursor = me.foxtails.palustris.domain.NotificationCursor("newer-next"),
            checkpoint = NotificationCheckpoint(
                account,
                query,
                newest = me.foxtails.palustris.domain.NotificationCursor("newer-next"),
            ),
            direction = NotificationPageDirection.Newer,
        ))

        assertEquals(listOf("newer"), repository.pendingDeliveries(account).map { it.notificationId.value })
    }

    @Test
    fun filteredQueriesKeepIndependentCheckpointsAndCompleteness() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val mentions = NotificationQuery(setOf(me.foxtails.palustris.domain.NotificationCategory.Mentions))
        val social = NotificationQuery(setOf(me.foxtails.palustris.domain.NotificationCategory.Social))
        repository.activate(token)
        repository.establishBaseline(token, baselineRequest(mentions), NotificationPage(
            items = listOf(notification("mention", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, mentions, oldest = me.foxtails.palustris.domain.NotificationCursor("mentions-old")),
        ))
        repository.ingestOlderPage(token, olderRequest(social), NotificationPage(
            items = listOf(notification("social", NotificationActivity.Follow)),
            olderCursor = me.foxtails.palustris.domain.NotificationCursor("social-old"),
            checkpoint = NotificationCheckpoint(account, social, oldest = me.foxtails.palustris.domain.NotificationCursor("social-old")),
            direction = NotificationPageDirection.Older,
        ))

        assertEquals("mentions-old", repository.checkpoint(account, mentions)?.oldest?.value)
        assertEquals("social-old", repository.checkpoint(account, social)?.oldest?.value)
        assertEquals(1, repository.observeInbox(account, mentions).first().items.size)
        assertEquals(1, repository.observeInbox(account, social).first().items.size)
    }

    @Test
    fun olderContinuationMovesFromIncompleteToTerminalWithoutRestoringTheCursor() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val query = NotificationQuery()
        repository.activate(token)
        repository.establishBaseline(token, baselineRequest(query), NotificationPage(
            items = listOf(notification("baseline", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, query, oldest = me.foxtails.palustris.domain.NotificationCursor("old-1")),
        ))
        repository.ingestOlderPage(token, olderRequest(query), NotificationPage(
            items = listOf(notification("older", NotificationActivity.Mention)),
            olderCursor = me.foxtails.palustris.domain.NotificationCursor("old-2"),
            checkpoint = NotificationCheckpoint(account, query, oldest = me.foxtails.palustris.domain.NotificationCursor("old-2")),
            direction = NotificationPageDirection.Older,
        ))
        assertEquals(NotificationSyncCompleteness.Incomplete, repository.checkpoint(account, query)?.completeness)

        repository.ingestOlderPage(token, olderRequest(query), NotificationPage(
            items = emptyList(),
            checkpoint = NotificationCheckpoint(account, query),
            direction = NotificationPageDirection.Older,
            reachedBoundary = true,
        ))
        assertEquals(NotificationSyncCompleteness.Complete, repository.checkpoint(account, query)?.completeness)
        assertEquals(null, repository.checkpoint(account, query)?.oldest)
    }

    @Test
    fun streamDuplicatesCreateOneDeliveryAndDismissalSurvivesRedelivery() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val query = NotificationQuery()
        val baseline = notification("baseline", NotificationActivity.Mention)
        val incoming = notification("stream", NotificationActivity.Reply)
        repository.activate(token)
        repository.establishBaseline(token, baselineRequest(query), NotificationPage(
            items = listOf(baseline),
            checkpoint = NotificationCheckpoint(account, query),
        ))

        val event = Event(account, SocialEvent.NotificationReceived(incoming))
        assertTrue(repository.applyStreamEvent(token, event))
        assertTrue(repository.applyStreamEvent(token, event))
        assertEquals(listOf("stream"), repository.pendingDeliveries(account).map { it.notificationId.value })

        assertTrue(repository.dismissFromInbox(token, incoming.id, remoteApplied = false))
        assertTrue(repository.applyStreamEvent(token, event))
        assertTrue(repository.observe(account).value.items.none { it.id == incoming.id })
        assertTrue(repository.pendingDeliveries(account).isEmpty())
    }

    @Test
    fun expiredDeliveryClaimCanBeRecoveredAndOldClaimCannotFinishIt() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val query = NotificationQuery()
        val incoming = notification("incoming", NotificationActivity.Mention)
        repository.activate(token)
        repository.establishBaseline(token, baselineRequest(query), NotificationPage(
            items = listOf(notification("baseline", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, query),
        ))
        repository.ingestNewerPage(token, newerRequest(query), NotificationPage(
            items = listOf(incoming),
            checkpoint = NotificationCheckpoint(account, query),
            direction = NotificationPageDirection.Newer,
        ))

        val first = repository.claimDelivery(token, incoming.id, nowEpochMillis = 100L)
        assertNotNull(first)
        assertTrue(repository.pendingDeliveries(account, nowEpochMillis = first!!.claimExpiresAtEpochMillis - 1).isEmpty())
        assertFalse(repository.finishDelivery(
            token,
            incoming.id,
            NotificationDeliveryState.Presented,
            claimId = "stale-claim",
        ))

        val recovered = repository.claimDelivery(
            token,
            incoming.id,
            nowEpochMillis = first.claimExpiresAtEpochMillis,
        )
        assertNotNull(recovered)
        assertNotEquals(first.claimId, recovered!!.claimId)
        assertTrue(repository.finishDelivery(
            token,
            incoming.id,
            NotificationDeliveryState.Presented,
            claimId = recovered.claimId,
        ))
        assertTrue(repository.pendingDeliveries(account).isEmpty())
    }

    @Test
    fun notificationRepositoryIsApplicationSingleton() {
        assertNotNull(NotificationRepository::class.java.getAnnotation(javax.inject.Singleton::class.java))
    }

    @Test
    fun notificationPostPersistencePreservesAvailableInteractionCounts() = runBlocking {
        val store = InMemoryNotificationStore()
        val first = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        val post = Post(
            id = EntityId(account.connection.origin, "post"),
            author = Account(account, "Author", "@author@example.org"),
            text = "Post",
            publishedAtEpochMillis = 1,
            audience = me.foxtails.palustris.domain.Audience.Public,
            interactionCounts = PostInteractionCounts(
                favouriteCount = 0,
                reactionCount = 2,
                repostCount = 3,
                quoteRepostCount = 0,
                replyCount = 4,
            ),
        )
        first.activate(token)
        first.establishBaseline(token, baselineRequest(), NotificationPage(listOf(notification("post", NotificationActivity.Mention, post))))

        val restored = NotificationRepository(store).observe(account).value.items.single().post

        assertEquals(post.interactionCounts, restored?.interactionCounts)
    }

    @Test
    fun unknownActivityPreservesValidatedNestedDestination() = runBlocking {
        val store = InMemoryNotificationStore()
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        val destination = NotificationDestination.Server(
            ValidatedUrl.https("https://example.org/activity/1")!!,
        )
        repository.establishBaseline(token, baselineRequest(), NotificationPage(
            listOf(notification("unknown", NotificationActivity.Unknown(NotificationLabel.Plain("Unknown"), destination))),
        ))

        val restored = NotificationRepository(store).observe(account).value.items.single()
        val activity = restored.activity as NotificationActivity.Unknown
        assertEquals(destination, activity.validatedDestination)
    }

    @Test
    fun foreignCheckpointAndEntityOriginAreRejectedWithoutPublication() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        val foreignAccount = AccountId(Connection("https://foreign.example", Protocol.MISSKEY), "receiver")

        assertFalse(repository.establishBaseline(token, baselineRequest(), NotificationPage(
            items = emptyList(),
            checkpoint = NotificationCheckpoint(foreignAccount, NotificationQuery()),
        )))
        assertFalse(repository.establishBaseline(token, baselineRequest(), NotificationPage(
            items = listOf(notification("foreign", NotificationActivity.Mention).copy(
                id = EntityId("https://foreign.example", "foreign"),
            )),
        )))
        assertTrue(repository.observe(account).value.items.isEmpty())
    }

    @Test
    fun mismatchedCheckpointQueryIsRejectedBothDirections() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val all = NotificationQuery()
        val mentions = NotificationQuery(setOf(me.foxtails.palustris.domain.NotificationCategory.Mentions))
        repository.activate(token)
        assertTrue(repository.establishBaseline(token, baselineRequest(all), NotificationPage(
            items = listOf(notification("base", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, all),
        )))

        assertFalse(repository.ingestNewerPage(token, newerRequest(all), NotificationPage(
            items = listOf(notification("foreign-query", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, mentions),
            direction = NotificationPageDirection.Newer,
        )))
        assertFalse(repository.ingestOlderPage(token, olderRequest(mentions), NotificationPage(
            items = listOf(notification("foreign-query", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, all),
            direction = NotificationPageDirection.Older,
        )))
        assertEquals(listOf("base"), repository.observe(account).value.items.map { it.id.value })
        assertEquals(null, repository.checkpoint(account, mentions))
    }

    @Test
    fun queryFieldVariationsAreRejectedIndependently() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val requested = NotificationQuery()
        repository.activate(token)
        assertTrue(repository.establishBaseline(token, baselineRequest(requested), NotificationPage(
            items = listOf(notification("base", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, requested),
        )))

        val limitVariant = NotificationQuery(limit = 10)
        val groupedVariant = NotificationQuery(grouped = true)
        assertFalse(repository.ingestNewerPage(token, newerRequest(requested), NotificationPage(
            items = listOf(notification("limit", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, limitVariant),
            direction = NotificationPageDirection.Newer,
        )))
        assertFalse(repository.ingestNewerPage(token, newerRequest(requested), NotificationPage(
            items = listOf(notification("grouped", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, groupedVariant),
            direction = NotificationPageDirection.Newer,
        )))
        assertEquals(listOf("base"), repository.observe(account).value.items.map { it.id.value })
    }

    @Test
    fun emptyPageWithForeignQueryIsRejected() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        assertFalse(repository.establishBaseline(token, baselineRequest(), NotificationPage(
            items = emptyList(),
            checkpoint = NotificationCheckpoint(
                account,
                NotificationQuery(setOf(me.foxtails.palustris.domain.NotificationCategory.Mentions)),
            ),
        )))
        assertTrue(repository.observe(account).value.items.isEmpty())
        assertEquals(null, repository.checkpoint(account, NotificationQuery()))
    }

    @Test
    fun checkpointFreePageIsAcceptedOnlyUnderItsExplicitQuery() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val mentions = NotificationQuery(setOf(me.foxtails.palustris.domain.NotificationCategory.Mentions))
        repository.activate(token)
        val page = NotificationPage(
            items = listOf(notification("free", NotificationActivity.Mention)),
            unreadState = NotificationUnreadState.Exact(2),
        )
        assertTrue(repository.establishBaseline(token, baselineRequest(mentions), page))
        assertEquals(listOf("free"), repository.observe(account).value.items.map { it.id.value })
        assertEquals(NotificationUnreadState.Exact(2), repository.observe(account).value.unreadState)
        assertNotNull(repository.checkpoint(account, mentions))
        assertEquals(null, repository.checkpoint(account, NotificationQuery()))
    }

    @Test
    fun staleSameQueryBoundaryIsRejectedWithoutMovingCursors() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val query = NotificationQuery()
        repository.activate(token)
        assertTrue(repository.establishBaseline(token, baselineRequest(query), NotificationPage(
            items = listOf(notification("base", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(
                account,
                query,
                oldest = NotificationCursor("old-0"),
            ),
        )))
        assertTrue(repository.ingestOlderPage(token, olderRequest(query, NotificationCursor("old-0")), NotificationPage(
            items = listOf(notification("older-1", NotificationActivity.Mention)),
            olderCursor = NotificationCursor("old-1"),
            checkpoint = NotificationCheckpoint(account, query),
            direction = NotificationPageDirection.Older,
        )))
        assertFalse(repository.ingestOlderPage(token, olderRequest(query, NotificationCursor("old-0")), NotificationPage(
            items = listOf(notification("stale", NotificationActivity.Mention)),
            olderCursor = NotificationCursor("old-0"),
            checkpoint = NotificationCheckpoint(account, query),
            direction = NotificationPageDirection.Older,
        )))
        assertFalse(repository.ingestNewerPage(token, newerRequest(query, NotificationCursor("bogus")), NotificationPage(
            items = listOf(notification("stale-new", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, query),
            direction = NotificationPageDirection.Newer,
        )))
        assertEquals("old-1", repository.checkpoint(account, query)?.olderContinuation?.value)
        assertTrue(repository.observe(account).value.items.none { it.id.value == "stale" })
    }

    @Test
    fun restoredMismatchedCheckpointEntryCannotSupplyACursor() = runBlocking {
        val store = InMemoryNotificationStore()
        val mentions = NotificationQuery(setOf(me.foxtails.palustris.domain.NotificationCategory.Mentions))
        store.write(
            account,
            NotificationRepositoryState(checkpoints = mapOf(
                NotificationQuery().stableKey to NotificationCheckpoint(account, mentions),
            )),
        )
        val repository = NotificationRepository(store)
        assertEquals(null, repository.checkpoint(account, NotificationQuery()))
        assertEquals(null, repository.checkpoint(account, mentions))
    }

    @Test
    fun rejectedIngestionChangesNeitherMemoryNorDurableState() = runBlocking {
        val store = InMemoryNotificationStore()
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        val query = NotificationQuery()
        repository.activate(token)
        assertTrue(repository.establishBaseline(token, baselineRequest(query), NotificationPage(
            items = listOf(notification("base", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, query, capturedAtEpochMillis = 10),
            unreadState = NotificationUnreadState.Exact(1),
        )))
        val memoryBefore = repository.observe(account).value
        val durableBefore = (store.read(account) as? NotificationStoreRead.Readable)?.state

        assertFalse(repository.ingestNewerPage(token, newerRequest(query), NotificationPage(
            items = listOf(notification("rejected", NotificationActivity.Mention)),
            newerCursor = NotificationCursor("rejected-next"),
            checkpoint = NotificationCheckpoint(
                account,
                NotificationQuery(setOf(me.foxtails.palustris.domain.NotificationCategory.Mentions)),
                capturedAtEpochMillis = 99,
            ),
            unreadState = NotificationUnreadState.Exact(5),
            direction = NotificationPageDirection.Newer,
        )))

        val memoryAfter = repository.observe(account).value
        val durableAfter = (store.read(account) as? NotificationStoreRead.Readable)?.state
        assertEquals(memoryBefore.items.map { it.id }, memoryAfter.items.map { it.id })
        assertEquals(memoryBefore.unreadState, memoryAfter.unreadState)
        assertEquals(memoryBefore.checkpoints, memoryAfter.checkpoints)
        assertEquals(memoryBefore.deliveries, memoryAfter.deliveries)
        assertEquals(memoryBefore.lastSyncedAtEpochMillis, memoryAfter.lastSyncedAtEpochMillis)
        assertEquals(durableBefore?.items?.map { it.id }, durableAfter?.items?.map { it.id })
        assertEquals(durableBefore?.unreadState, durableAfter?.unreadState)
        assertEquals(durableBefore?.checkpoints, durableAfter?.checkpoints)
        assertEquals(durableBefore?.deliveries, durableAfter?.deliveries)
        assertEquals(durableBefore?.lastSyncedAtEpochMillis, durableAfter?.lastSyncedAtEpochMillis)
        assertTrue(repository.pendingDeliveries(account).isEmpty())
    }

    @Test
    fun removalWaiterCannotActivateStaleReplacement() = runBlocking {
        val store = GatedNotificationStore()
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)

        val writer = async {
            repository.updateUnreadState(token, NotificationUnreadState.Exact(1))
        }
        store.writeEntered.await()

        val removal = async { repository.remove(account) }
        yield()
        // A racing activation with the stale generation never applies.
        repository.activate(token)
        store.releaseWrite.complete(Unit)
        assertTrue(writer.await())
        removal.await()

        assertFalse(repository.updateUnreadState(token, NotificationUnreadState.Exact(2)))
        assertEquals(null, repository.currentToken(account))
        assertTrue(store.read(account) is NotificationStoreRead.Absent)

        // Only a strictly newer generation activates after removal.
        repository.activate(token)
        assertEquals(null, repository.currentToken(account))
        val replacement = NotificationSyncToken(account, 2)
        repository.activate(replacement)
        assertTrue(repository.updateUnreadState(replacement, NotificationUnreadState.Exact(3)))
    }

    @Test
    fun lateStreamEventCannotRecreateStateAfterRemoval() = runBlocking {
        val store = InMemoryNotificationStore()
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        repository.establishBaseline(
            token,
            baselineRequest(),
            NotificationPage(listOf(notification("before-remove", NotificationActivity.Follow))),
        )
        repository.remove(account)

        val late = Event(
            account,
            SocialEvent.NotificationReceived(notification("after-remove", NotificationActivity.Mention)),
        )
        assertFalse(repository.applyStreamEvent(token, late))
        assertTrue(repository.observe(account).value.items.isEmpty())
        assertTrue(store.read(account) is NotificationStoreRead.Absent)
    }

    private fun ingestRequest(page: NotificationPage) =
        NotificationIngestRequest(page.checkpoint?.query ?: NotificationQuery(), NotificationPageDirection.Initial)

    private fun baselineRequest(query: NotificationQuery = NotificationQuery()) =
        NotificationIngestRequest(query, NotificationPageDirection.Initial)

    private fun newerRequest(
        query: NotificationQuery = NotificationQuery(),
        expectedContinuation: NotificationCursor? = null,
    ) = NotificationIngestRequest(query, NotificationPageDirection.Newer, expectedContinuation)

    private fun olderRequest(
        query: NotificationQuery = NotificationQuery(),
        expectedContinuation: NotificationCursor? = null,
    ) = NotificationIngestRequest(query, NotificationPageDirection.Older, expectedContinuation)

    private fun notification(id: String, activity: NotificationActivity, post: Post? = null) = Notification(
        id = EntityId(account.connection.origin, id),
        accountId = account,
        createdAtEpochMillis = 100,
        activity = activity,
        actors = listOf(Account(account, "Receiver", "@receiver@example.org")),
        post = post,
        rawType = id,
    )

    @Suppress("UNCHECKED_CAST")
    private fun stateKeys(repository: NotificationRepository): Set<AccountId> {
        val field = NotificationRepository::class.java.getDeclaredField("states").apply {
            isAccessible = true
        }
        return ((field.get(repository) as Map<AccountId, *>).keys.toSet())
    }
}

/** Blocks one durable write so account-lock ordering can be tested without timing assumptions. */
private class GatedNotificationStore : NotificationStore {
    private val delegate = InMemoryNotificationStore()
    val writeEntered = CompletableDeferred<Unit>()
    val releaseWrite = CompletableDeferred<Unit>()
    private var gateNextWrite = true

    override fun read(accountId: AccountId): NotificationStoreRead = delegate.read(accountId)

    override fun write(accountId: AccountId, state: NotificationRepositoryState) {
        if (gateNextWrite) {
            gateNextWrite = false
            runBlocking {
                writeEntered.complete(Unit)
                releaseWrite.await()
            }
        }
        delegate.write(accountId, state)
    }

    override fun delete(accountId: AccountId) = delegate.delete(accountId)
}

/** Counts store reads so a tombstoned observe can prove it issues no read. */
private class CountingNotificationStore : NotificationStore {
    private val delegate = InMemoryNotificationStore()
    var reads = 0

    override fun read(accountId: AccountId): NotificationStoreRead {
        reads++
        return delegate.read(accountId)
    }

    override fun write(accountId: AccountId, state: NotificationRepositoryState) =
        delegate.write(accountId, state)

    override fun delete(accountId: AccountId) = delegate.delete(accountId)
}
