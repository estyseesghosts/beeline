package me.foxtails.palustris.data.mastodon

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.NotificationCapabilities
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.PushSubscription
import me.foxtails.palustris.domain.PushSubscriptionSpec
import me.foxtails.palustris.domain.ReportRequest
import me.foxtails.palustris.domain.ReportRoute
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.ServerFlavor
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.ValidatedUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Slice 6 gates: a Pixelfed probe reports notification dismissal, unread count, push, and
 * streaming Unsupported with report route V11Report (see MastodonCapabilityProbeFlavorTest).
 * These tests prove the sources honor those capabilities before any network, so the missing
 * Pixelfed routes below can never answer 404 at runtime.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonPixelfedGatesTest {
    private lateinit var server: MockWebServer

    @Before
    fun startServer() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    /**
     * Pixelfed X2/X3/X9/X10 missing routes from routes/api.php dev commit 42827e2. Each answers
     * 404 with a Mastodon-API error envelope (representative shape; the live Pixelfed body is
     * unverified). The gate tests below prove the client never issues these requests on Pixelfed,
     * so these 404s can never surface. Runtime demotion of unexpected 404s belongs to Slice 8.
     */
    private object PixelfedMissingRoutes {
        const val UNREAD_COUNT = "api/v1/notifications/unread_count"
        const val DISMISS = "api/v1/notifications/1/dismiss"
        const val REPORTS = "api/v1/reports"
        const val NOT_FOUND_BODY = "{\"error\":\"Record not found\"}"
    }

    private fun pixelfedCapabilities() = ServerCapabilities(
        notifications = NotificationCapabilities(
            dismissal = CapabilityStatus.Unsupported,
            streaming = CapabilityStatus.Unsupported,
            webPush = CapabilityStatus.Unsupported,
            unread = CapabilityStatus.Unsupported,
        ),
        reportRoute = ReportRoute.V11Report,
        flavor = ServerFlavor.Pixelfed,
    )

    private fun accountId(origin: String) = AccountId(Connection(origin, Protocol.MASTODON), "viewer")

    @Test
    fun unreadStateReturnsUnknownWithoutNetworkOnPixelfed() {
        val origin = server.url("/").toString().removeSuffix("/")
        val service = MastodonNotificationService(origin, "token", mastodonTestClient(), accountId(origin), { 0L }) {
            pixelfedCapabilities()
        }

        val state = runBlocking { service.unreadState() }

        assertEquals(NotificationUnreadState.Unknown, state)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun unreadStateStillReadsTheRouteWhenUnprobed() {
        val origin = server.url("/").toString().removeSuffix("/")
        val service = MastodonNotificationService(origin, "token", mastodonTestClient(), accountId(origin), { 0L }) {
            ServerCapabilities()
        }
        server.enqueue(MockResponse().setBody(PixelfedMissingRoutes.NOT_FOUND_BODY).setResponseCode(404))

        runCatching { runBlocking { service.unreadState() } }

        assertEquals(1, server.requestCount)
        assertEquals("/${PixelfedMissingRoutes.UNREAD_COUNT}", server.takeRequest().path)
    }

    @Test
    fun dismissThrowsUnsupportedBeforeNetworkOnPixelfed() {
        val origin = server.url("/").toString().removeSuffix("/")
        val service = MastodonNotificationService(origin, "token", mastodonTestClient(), accountId(origin), { 0L }) {
            pixelfedCapabilities()
        }

        assertThrows(SourceError.Unsupported::class.java) { runBlocking { service.dismiss(EntityId(origin, "1")) } }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun dismissStillPostsWhenUnprobed() {
        val origin = server.url("/").toString().removeSuffix("/")
        val service = MastodonNotificationService(origin, "token", mastodonTestClient(), accountId(origin), { 0L }) {
            ServerCapabilities()
        }
        server.enqueue(MockResponse().setBody("{}"))

        runBlocking { service.dismiss(EntityId(origin, "1")) }

        assertEquals("/${PixelfedMissingRoutes.DISMISS}", server.takeRequest().path)
    }

    @Test
    fun pushProviderInfoReportsUnsupportedOnPixelfed() {
        val origin = server.url("/").toString().removeSuffix("/")
        val service = MastodonPushService(origin, "token", mastodonTestClient(), accountId(origin)) {
            pixelfedCapabilities()
        }

        val info = runBlocking { service.providerInfo() }

        assertEquals(CapabilityStatus.Unsupported, info.status)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun pushProviderInfoKeepsOptimisticSupportWhenUnprobed() {
        val origin = server.url("/").toString().removeSuffix("/")
        val service = MastodonPushService(origin, "token", mastodonTestClient(), accountId(origin)) {
            ServerCapabilities()
        }

        val info = runBlocking { service.providerInfo() }

        assertEquals(CapabilityStatus.Supported, info.status)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun pushReadsAndWritesStayOffTheNetworkOnPixelfed() {
        val origin = server.url("/").toString().removeSuffix("/")
        val viewer = accountId(origin)
        val service = MastodonPushService(origin, "token", mastodonTestClient(), viewer) {
            pixelfedCapabilities()
        }
        val endpoint = ValidatedUrl.https("https://push.example/endpoint") ?: error("Test endpoint was rejected")
        val spec = PushSubscriptionSpec(viewer, endpoint, "public-key", "auth-secret")
        val subscription = PushSubscription(viewer, endpoint, "remote-1")

        assertNull(runBlocking { service.query(null) })
        assertThrows(SourceError.Unsupported::class.java) { runBlocking { service.createOrReplace(spec, null) } }
        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { service.updatePolicy(subscription, setOf(NotificationCategory.All)) }
        }
        assertThrows(SourceError.Unsupported::class.java) { runBlocking { service.remove(subscription) } }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun streamingFailsTerminalWithoutOpeningASocketOnPixelfed() {
        val origin = server.url("/").toString().removeSuffix("/")
        val service = MastodonStreamService(origin, "token", mastodonTestClient(), accountId(origin)) {
            pixelfedCapabilities()
        }

        val error = assertThrows(SourceError.Unsupported::class.java) { runBlocking { service.events().first() } }

        assertEquals("notifications.streaming", error.feature)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun reportPostsToV1ReportsOnMastodonRoute() {
        val origin = server.url("/").toString().removeSuffix("/")
        val viewer = accountId(origin)
        val service = MastodonModerationService(origin, "token", mastodonTestClient(), viewer, 1L, "source-1") {
            ServerCapabilities()
        }
        server.enqueue(MockResponse().setBody("{}"))
        val target = AccountId(Connection(origin, Protocol.MASTODON), "target")

        runBlocking { service.report(ReportRequest(target, comment = "spam")) }

        assertEquals("/${PixelfedMissingRoutes.REPORTS}", server.takeRequest().path)
    }

    @Test
    fun reportThrowsUnsupportedBeforeNetworkOnV11ReportRoute() {
        val origin = server.url("/").toString().removeSuffix("/")
        val viewer = accountId(origin)
        val service = MastodonModerationService(origin, "token", mastodonTestClient(), viewer, 1L, "source-1") {
            pixelfedCapabilities()
        }
        val target = AccountId(Connection(origin, Protocol.MASTODON), "target")

        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { service.report(ReportRequest(target, comment = "spam")) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun reportStillValidatesForeignOriginsBeforeTheRouteGate() {
        val origin = server.url("/").toString().removeSuffix("/")
        val viewer = accountId(origin)
        val service = MastodonModerationService(origin, "token", mastodonTestClient(), viewer, 1L, "source-1") {
            pixelfedCapabilities()
        }
        val foreign = AccountId(Connection("https://other.example", Protocol.MASTODON), "target")

        assertThrows(SourceError.ForeignOrigin::class.java) {
            runBlocking { service.report(ReportRequest(foreign, comment = "spam")) }
        }
        assertEquals(0, server.requestCount)
    }
}
