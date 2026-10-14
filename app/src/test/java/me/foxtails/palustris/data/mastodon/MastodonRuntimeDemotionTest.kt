package me.foxtails.palustris.data.mastodon

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.PushSubscriptionSpec
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.ValidatedUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Slice 8 runtime demotion: an optional route that answers 404 or 405 is Unsupported
 * for the session, and later calls throw before the network through their capability
 * gates. 500s, timeouts, and rate limits never demote: the next call retries. Trends
 * sections have no session capability field, so a refused section hides as an empty
 * result instead. The 404 envelope below is the representative Mastodon-API error
 * shape; no live Pixelfed body was available.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonRuntimeDemotionTest {
    private lateinit var server: MockWebServer
    private val origin get() = server.url("/").toString().removeSuffix("/")

    @Before
    fun startServer() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    private fun accountId() = AccountId(Connection(origin, Protocol.MASTODON), "viewer")

    private fun source() = MastodonSource(
        origin = origin,
        token = "token",
        api = mastodonTestClient(),
        accountId = accountId(),
    )

    private fun pushSpec(): PushSubscriptionSpec {
        val endpoint = ValidatedUrl.https("https://push.example/endpoint") ?: error("Test endpoint was rejected")
        return PushSubscriptionSpec(accountId(), endpoint, "public-key", "auth-secret")
    }

    @Test
    fun listTimeline404DemotesListsForTheSession() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody(NOT_FOUND_BODY))
        val source = source()

        val error = assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.feed(FeedSource.UserList("7", "Friends")) }
        }

        assertEquals("list", error.feature)
        assertEquals(CapabilityStatus.Unsupported, source.capabilities.feedChips.lists)
        // The second call throws before the network through the capability gate.
        val gated = assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.feed(FeedSource.UserList("7", "Friends")) }
        }
        assertEquals("list", gated.feature)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun listTimeline405DemotesListsForTheSession() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(405).setBody(NOT_FOUND_BODY))
        val source = source()

        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.feed(FeedSource.UserList("7", "Friends")) }
        }

        assertEquals(CapabilityStatus.Unsupported, source.capabilities.feedChips.lists)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun unreadCount404DemotesUnreadForTheSession() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody(NOT_FOUND_BODY))
        val source = source()

        val error = assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.notificationUnreadState() }
        }

        assertEquals("notifications.unread", error.feature)
        assertEquals(CapabilityStatus.Unsupported, source.capabilities.notifications.unread)
        // Without an unread route the state reads Unknown without the network.
        assertEquals(NotificationUnreadState.Unknown, source.notificationUnreadState())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun unreadCount400DemotesUnreadForTheSession() = runBlocking {
        // Pleroma answers 400 (not 404): the v1 path collides with notifications/:id.
        server.enqueue(MockResponse().setResponseCode(400).setBody(NOT_FOUND_BODY))
        val source = source()

        val error = assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.notificationUnreadState() }
        }

        assertEquals("notifications.unread", error.feature)
        assertEquals(CapabilityStatus.Unsupported, source.capabilities.notifications.unread)
        // Without an unread route the state reads Unknown without the network.
        assertEquals(NotificationUnreadState.Unknown, source.notificationUnreadState())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun trends404OnEverySectionHidesTheSections() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(404).setBody(NOT_FOUND_BODY)
        }

        val trends = source().trends()

        assertTrue(trends.isEmpty)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun pushCreate404DemotesPushForTheSession() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody(NOT_FOUND_BODY))
        val source = source()

        val error = assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.createOrReplacePushSubscription(pushSpec(), null) }
        }

        assertEquals("notifications.push.create", error.feature)
        assertEquals(CapabilityStatus.Unsupported, source.capabilities.notifications.webPush)
        assertEquals(CapabilityStatus.Unsupported, source.pushProviderInfo().status)
        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.createOrReplacePushSubscription(pushSpec(), null) }
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun serverErrorOnListTimelineDoesNotDemote() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))
        server.enqueue(MockResponse().setBody("[]"))
        val source = source()

        assertThrows(SourceError.ServerError::class.java) {
            runBlocking { source.feed(FeedSource.UserList("7", "Friends")) }
        }

        // A 500 stays temporarily unavailable: the capability is untouched and the retry works.
        assertEquals(CapabilityStatus.Supported, source.capabilities.feedChips.lists)
        assertTrue(source.feed(FeedSource.UserList("7", "Friends")).items.isEmpty())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun timeoutOnUnreadDoesNotDemote() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setBody("""{"count":2}"""))
        val source = source()

        assertThrows(SourceError.NetworkUnavailable::class.java) {
            runBlocking { source.notificationUnreadState() }
        }

        assertEquals(CapabilityStatus.Unknown, source.capabilities.notifications.unread)
        assertEquals(NotificationUnreadState.AtLeast(2), source.notificationUnreadState())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun rateLimitOnPushDoesNotDemote() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"throttled"}"""))
        val source = source()

        assertThrows(SourceError.RateLimited::class.java) {
            runBlocking { source.createOrReplacePushSubscription(pushSpec(), null) }
        }

        assertEquals(CapabilityStatus.Unknown, source.capabilities.notifications.webPush)
        assertEquals(CapabilityStatus.Supported, source.pushProviderInfo().status)
        assertEquals(1, server.requestCount)
    }

    private companion object {
        /** Representative Mastodon-API 404 envelope for a missing optional route. */
        const val NOT_FOUND_BODY = """{"error":"Record not found"}"""
    }
}
