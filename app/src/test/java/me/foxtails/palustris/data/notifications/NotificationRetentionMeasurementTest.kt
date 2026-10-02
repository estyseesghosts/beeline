package me.foxtails.palustris.data.notifications

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.foxtails.palustris.data.notifications.db.NotificationDatabase
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Event
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationDeliveryState
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationPageDirection
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.SocialEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Synthetic retention measurement for notification correctness state.
 *
 * Each test records collection sizes, table counts, and workload parameters.
 * No test asserts heap or disk bytes: those numbers need a device run and
 * Robolectric byte assertions cannot supply them. No test evicts state.
 * Account removal is the release path measured here. Storage reset is not exercised.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationRetentionMeasurementTest {
    private val account = AccountId(Connection("https://example.org", Protocol.MISSKEY), "receiver")
    private val sibling = AccountId(Connection("https://example.org", Protocol.MISSKEY), "sibling")

    @Test
    fun visibleItemsStayBoundedAcrossFiftyThousandIngestedEvents() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)

        val pages = 100
        val pageSize = 500
        repeat(pages) { page ->
            val items = (0 until pageSize).map { index ->
                notification(account, "evt-${page * pageSize + index}", page * pageSize + index)
            }
            val current = NotificationPage(
                items = items,
                checkpoint = checkpoint(),
                unreadState = NotificationUnreadState.Unknown,
            )
            assertTrue(repository.establishBaseline(token, baselineRequest(), current))
        }

        val state = repository.observe(account).value
        assertEquals(500, state.items.size)
        assertEquals(1, state.checkpoints.size)
        assertTrue(state.deliveries.isEmpty())
    }

    @Test
    fun dismissalTombstonesGrowExactlyWithOneThousandDismissals() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)

        val total = 1_000
        ingestIds(repository, token, total)
        (0 until total).forEach { index ->
            assertTrue(repository.dismiss(token, EntityId(account.connection.origin, "dismiss-$index")))
        }

        val state = repository.observe(account).value
        assertEquals(total, state.dismissedIds.size)
        assertTrue(state.items.isEmpty())
        assertTrue(state.deliveries.isEmpty())
    }

    @Test
    fun dismissalTombstonesGrowExactlyWithTenThousandDismissals() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)

        val total = 10_000
        ingestIds(repository, token, total)
        (0 until total).forEach { index ->
            assertTrue(repository.dismiss(token, EntityId(account.connection.origin, "dismiss-$index")))
        }

        val state = repository.observe(account).value
        assertEquals(total, state.dismissedIds.size)
        assertTrue(state.items.isEmpty())
        assertTrue(state.deliveries.isEmpty())
    }

    @Test
    fun refreshReplayNeverRestoresDismissedOrPresentedEvents() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        val baseline = NotificationPage(
            items = listOf(
                notification(account, "replay-dismissed", 1),
                notification(account, "replay-presented", 2),
            ),
            checkpoint = checkpoint(),
            unreadState = NotificationUnreadState.Unknown,
        )
        assertTrue(repository.establishBaseline(token, baselineRequest(), baseline))
        assertTrue(
            repository.applyStreamEvent(
                token,
                Event(account, SocialEvent.NotificationReceived(notification(account, "replay-dismissed", 1))),
            ),
        )
        assertTrue(
            repository.applyStreamEvent(
                token,
                Event(account, SocialEvent.NotificationReceived(notification(account, "replay-presented", 2))),
            ),
        )
        val before = repository.observe(account).value
        assertEquals(2, before.deliveries.size)

        val dismissedId = EntityId(account.connection.origin, "replay-dismissed")
        val presentedId = EntityId(account.connection.origin, "replay-presented")
        assertTrue(repository.dismiss(token, dismissedId))
        assertTrue(repository.markPresented(token, presentedId))

        val replay = NotificationPage(
            items = listOf(
                notification(account, "replay-dismissed", 1),
                notification(account, "replay-presented", 2),
                notification(account, "replay-new", 3),
            ),
            checkpoint = checkpoint(),
            unreadState = NotificationUnreadState.Unknown,
            direction = NotificationPageDirection.Newer,
        )
        assertTrue(repository.ingestNewerPage(token, newerRequest(), replay))

        val state = repository.observe(account).value
        assertFalse(state.items.any { it.id == dismissedId })
        assertFalse(state.deliveries.containsKey(dismissedId))
        val presented = state.items.single { it.id == presentedId }
        assertTrue(presented.readState.androidPresented)
        assertTrue(state.items.any { it.id.value == "replay-new" })
        assertEquals(1, state.dismissedIds.size)
    }

    @Test
    fun streamDeliveryRecordsGrowPerEventAndReleaseOnDismissal() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        val baseline = NotificationPage(
            items = emptyList(),
            checkpoint = checkpoint(),
            unreadState = NotificationUnreadState.Unknown,
        )
        assertTrue(repository.establishBaseline(token, baselineRequest(), baseline))

        val total = 200
        (0 until total).forEach { index ->
            assertTrue(
                repository.applyStreamEvent(
                    token,
                    Event(account, SocialEvent.NotificationReceived(notification(account, "delivery-$index", index))),
                ),
            )
        }
        assertEquals(total, repository.observe(account).value.deliveries.size)
        assertEquals(total, repository.pendingDeliveries(account, nowEpochMillis = 1_000).size)

        val claimed = (0 until 50).map { index ->
            repository.claimDelivery(
                token,
                EntityId(account.connection.origin, "delivery-$index"),
                nowEpochMillis = 1_000,
            )
        }
        assertEquals(50, claimed.filterNotNull().size)
        assertEquals(total - 50, repository.pendingDeliveries(account, nowEpochMillis = 1_000).size)

        claimed.filterNotNull().forEach { record ->
            assertTrue(
                repository.finishDelivery(
                    token,
                    record.notificationId,
                    NotificationDeliveryState.Presented,
                    claimId = record.claimId,
                ),
            )
        }
        assertEquals(total, repository.observe(account).value.deliveries.size)

        (0 until 100).forEach { index ->
            assertTrue(repository.dismiss(token, EntityId(account.connection.origin, "delivery-$index")))
        }
        val state = repository.observe(account).value
        assertEquals(total - 100, state.deliveries.size)
        assertEquals(100, state.dismissedIds.size)
    }

    @Test
    fun checkpointsStayBoundedByStableQueryKey() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)

        val queries = listOf(
            NotificationQuery(),
            NotificationQuery(categories = setOf(NotificationCategory.Mentions)),
            NotificationQuery(categories = setOf(NotificationCategory.Replies)),
            NotificationQuery(categories = setOf(NotificationCategory.Quotes)),
            NotificationQuery(categories = setOf(NotificationCategory.Social)),
        )
        queries.forEach { query ->
            val page = NotificationPage(
                items = listOf(notification(account, "checkpoint-${query.stableKey}", 1)),
                checkpoint = NotificationCheckpoint(account, query, capturedAtEpochMillis = 10),
                unreadState = NotificationUnreadState.Unknown,
            )
            assertTrue(
                repository.establishBaseline(
                    token,
                    NotificationIngestRequest(query, NotificationPageDirection.Initial),
                    page,
                ),
            )
        }
        assertEquals(queries.size, repository.observe(account).value.checkpoints.size)

        queries.forEach { query ->
            val page = NotificationPage(
                items = listOf(notification(account, "checkpoint-repeat-${query.stableKey}", 2)),
                checkpoint = NotificationCheckpoint(account, query, capturedAtEpochMillis = 20),
                unreadState = NotificationUnreadState.Unknown,
            )
            assertTrue(
                repository.establishBaseline(
                    token,
                    NotificationIngestRequest(query, NotificationPageDirection.Initial),
                    page,
                ),
            )
        }
        assertEquals(queries.size, repository.observe(account).value.checkpoints.size)
    }

    @Test
    fun removalCleansOneAccountWithoutTouchingAnother() = runBlocking {
        val store = InMemoryNotificationStore()
        val repository = NotificationRepository(store)
        val first = NotificationSyncToken(account, 1)
        val second = NotificationSyncToken(sibling, 1)
        repository.activate(first)
        repository.activate(second)

        listOf(account to first, sibling to second).forEach { (owner, token) ->
            val baseline = NotificationPage(
                items = (0 until 10).map { index -> notification(owner, "removal-$index", index) },
                checkpoint = NotificationCheckpoint(owner, NotificationQuery(), capturedAtEpochMillis = 10),
                unreadState = NotificationUnreadState.Unknown,
            )
            assertTrue(repository.establishBaseline(token, baselineRequest(), baseline))
            (0 until 5).forEach { index ->
                assertTrue(
                    repository.applyStreamEvent(
                        token,
                        Event(owner, SocialEvent.NotificationReceived(notification(owner, "stream-$index", index))),
                    ),
                )
            }
            (0 until 3).forEach { index ->
                assertTrue(repository.dismiss(token, EntityId(owner.connection.origin, "removal-$index")))
            }
        }
        val siblingBefore = repository.observe(sibling).value
        assertEquals(12, siblingBefore.items.size)
        assertEquals(3, siblingBefore.dismissedIds.size)
        assertEquals(5, siblingBefore.deliveries.size)

        repository.remove(account)

        assertTrue(store.read(account) is NotificationStoreRead.Absent)
        assertTrue(repository.observe(account).value.items.isEmpty())
        assertTrue(repository.isRetired(account))
        val siblingAfter = repository.observe(sibling).value
        assertEquals(siblingBefore.items, siblingAfter.items)
        assertEquals(siblingBefore.dismissedIds, siblingAfter.dismissedIds)
        assertEquals(siblingBefore.deliveries, siblingAfter.deliveries)

        val replacement = NotificationSyncToken(account, 2)
        repository.activate(replacement)
        val readded = NotificationPage(
            items = (0 until 10).map { index -> notification(account, "removal-$index", index) },
            checkpoint = NotificationCheckpoint(account, NotificationQuery(), capturedAtEpochMillis = 30),
            unreadState = NotificationUnreadState.Unknown,
        )
        assertTrue(repository.establishBaseline(replacement, baselineRequest(), readded))
        val restored = repository.observe(account).value
        assertEquals(10, restored.items.size)
        assertTrue(restored.dismissedIds.isEmpty())
    }

    @Test
    fun retiredGenerationsStayMonotonicAcrossRepeatedRemoval() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        assertTrue(repository.updateUnreadState(token, NotificationUnreadState.Exact(1)))

        repository.remove(account)
        repository.remove(account)

        assertTrue(repository.isRetired(account))
        assertFalse(repository.updateUnreadState(token, NotificationUnreadState.Exact(2)))
        repository.activate(token)
        assertEquals(null, repository.currentToken(account))

        val replacement = NotificationSyncToken(account, 3)
        repository.activate(replacement)
        assertTrue(repository.updateUnreadState(replacement, NotificationUnreadState.Exact(3)))
        assertEquals(replacement, repository.currentToken(account))
    }

    @Test
    fun roomStoreKeepsOneStateRowPerAccountWithEmptySiblingTables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NotificationDatabase::class.java).build()
        try {
            val store = RoomNotificationStore(database, LegacyNotificationFileImporter(context, FileNotificationStore(context)))
            val repository = NotificationRepository(store)
            listOf(account to 1L, sibling to 1L).forEach { (owner, generation) ->
                val token = NotificationSyncToken(owner, generation)
                repository.activate(token)
                val baseline = NotificationPage(
                    items = (0 until 10).map { index -> notification(owner, "room-$index", index) },
                    checkpoint = NotificationCheckpoint(owner, NotificationQuery(), capturedAtEpochMillis = 10),
                    unreadState = NotificationUnreadState.Unknown,
                )
                runBlocking {
                    repository.establishBaseline(token, baselineRequest(), baseline)
                    (0 until 4).forEach { index ->
                        repository.dismiss(token, EntityId(owner.connection.origin, "room-$index"))
                    }
                }
            }

            runBlocking(Dispatchers.IO) {
                assertEquals(2, countRows(database, "notification_state"))
                assertEquals(0, countRows(database, "notification_events"))
                assertEquals(0, countRows(database, "notification_actors"))
                assertEquals(0, countRows(database, "notification_groups"))
                assertEquals(0, countRows(database, "notification_query_state"))
                assertEquals(0, countRows(database, "notification_dismissals"))
                assertEquals(0, countRows(database, "notification_delivery"))
                assertEquals(0, countRows(database, "notification_acknowledgement"))
                assertEquals(0, countRows(database, "push_registration"))
                assertEquals(0, countRows(database, "notification_settings"))
            }
        } finally {
            runBlocking(Dispatchers.IO) { database.close() }
        }
    }

    private suspend fun countRows(database: NotificationDatabase, table: String): Int =
        withContext(Dispatchers.IO) {
            val cursor = database.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM $table"))
            cursor.use {
                it.moveToFirst()
                it.getInt(0)
            }
        }

    private suspend fun ingestIds(
        repository: NotificationRepository,
        token: NotificationSyncToken,
        total: Int,
    ) {
        var ingested = 0
        while (ingested < total) {
            val batch = (ingested until minOf(ingested + 500, total)).map { index ->
                notification(account, "dismiss-$index", index)
            }
            val page = NotificationPage(
                items = batch,
                checkpoint = checkpoint(),
                unreadState = NotificationUnreadState.Unknown,
            )
            assertTrue(repository.establishBaseline(token, baselineRequest(), page))
            ingested += batch.size
        }
    }

    private fun checkpoint() = NotificationCheckpoint(account, NotificationQuery(), capturedAtEpochMillis = 10)

    private fun baselineRequest() = NotificationIngestRequest(NotificationQuery(), NotificationPageDirection.Initial)

    private fun newerRequest() = NotificationIngestRequest(NotificationQuery(), NotificationPageDirection.Newer)

    private fun notification(owner: AccountId, id: String, createdAtEpochMillis: Int) = Notification(
        id = EntityId(owner.connection.origin, id),
        accountId = owner,
        createdAtEpochMillis = createdAtEpochMillis.toLong(),
        activity = NotificationActivity.Follow,
        actors = listOf(Account(owner, "Receiver", "@receiver@example.org")),
        post = null,
        rawType = id,
    )
}
