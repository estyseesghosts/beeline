package me.foxtails.palustris.data.notifications

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Event
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialEvent
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NotificationSyncOrchestratorTest {
    private val account = AccountId(Connection("https://example.org", Protocol.MISSKEY), "receiver")
    private val otherAccount = account.copy(localId = "other")
    private val source = EmptySource()

    @Test
    fun noOpUsesOneAllocatorAcrossAccountsAndRemoval() = runBlocking {
        val controller = NoOpNotificationSyncController()

        assertEquals(1L, controller.register(account, source).generation)
        assertEquals(2L, controller.register(otherAccount, source).generation)
        controller.unregister(account)
        assertEquals(3L, controller.register(account, source).generation)
        controller.removeAccount(otherAccount)
        assertEquals(4L, controller.register(otherAccount, source).generation)
    }

    @Test
    fun productionUsesTheSameLifetimeAllocation() = runBlocking {
        val controller = NotificationSyncOrchestrator()
        try {
            assertEquals(1L, controller.register(account, source).generation)
            controller.removeAccount(account)
            assertEquals(2L, controller.register(account, source).generation)
        } finally {
            controller.close()
        }
    }

    @Test
    fun unregisterRejectsLateStreamEventsAndRemovesActiveEntry() = runBlocking {
        val controller = NotificationSyncOrchestrator()
        try {
            controller.register(account, source)
            controller.unregister(account)

            assertFalse(controller.accept(Event(account, SocialEvent.Other("late"))))
            assertFalse(controller.observeAccount(account).value.isActive)
        } finally {
            controller.close()
        }
    }

    @Test
    fun noOpRemovalAlsoLeavesNoActiveEntry() = runBlocking {
        val controller = NoOpNotificationSyncController()
        controller.register(account, source)
        controller.removeAccount(account)

        assertFalse(controller.observeAccount(account).value.isActive)
    }

    private class EmptySource : SocialSource {
        override val capabilities = ServerCapabilities()

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())

        override suspend fun searchHashtag(tag: String, cursor: String?): Page<Post> = Page(emptyList())

        override suspend fun notifications(
            query: NotificationQuery,
            cursor: me.foxtails.palustris.domain.NotificationCursor?,
        ): NotificationPage = NotificationPage(emptyList())

        override suspend fun fetchNewerNotifications(
            query: NotificationQuery,
            checkpoint: me.foxtails.palustris.domain.NotificationCheckpoint,
        ): NotificationPage = NotificationPage(emptyList())

        override suspend fun fetchOlderNotifications(
            query: NotificationQuery,
            checkpoint: me.foxtails.palustris.domain.NotificationCheckpoint,
        ): NotificationPage = NotificationPage(emptyList())

        override suspend fun notificationUnreadState(): NotificationUnreadState =
            NotificationUnreadState.Unknown
    }
}
