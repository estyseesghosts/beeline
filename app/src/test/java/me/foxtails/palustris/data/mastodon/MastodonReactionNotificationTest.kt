package me.foxtails.palustris.data.mastodon

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationLabel
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationTarget
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.PushSubscription
import me.foxtails.palustris.domain.ReactionRoute
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.ValidatedUrl
import me.foxtails.palustris.domain.categories
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Slice 5 reaction notifications and push: `pleroma:emoji_reaction` decodes with its actor
 * and status, the Social filter and push alert key appear only when the server advertises
 * reactions, and the unfiltered view still sends no `types[]`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonReactionNotificationTest {
    private lateinit var server: MockWebServer

    @Before
    fun startServer() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun unicodeReactionKeepsActorStatusAndHasNoImage() {
        val origin = server.url("/").toString().removeSuffix("/")
        val receiver = AccountId(Connection(origin, Protocol.MASTODON), "receiver")
        val actor = reactionAccount("actor", "actor")
        val json = JSONObject()
            .put("id", "reaction-unicode")
            .put("type", "pleroma:emoji_reaction")
            .put("created_at", "2026-09-07T10:00:00Z")
            .put("emoji", "☕")
            .put("emoji_url", JSONObject.NULL)
            .put("account", actor)
            .put("status", reactionStatus("status-unicode", actor))

        val notification = MastodonNotificationMapper.notification(json, origin, receiver)

        assertEquals("pleroma:emoji_reaction", notification.rawType)
        val activity = notification.activity as NotificationActivity.EmojiReaction
        assertEquals("☕", activity.reaction.identity)
        assertEquals(NotificationLabel.Plain("☕"), activity.reaction.fallbackText)
        assertNull(activity.reaction.emoji)
        assertEquals("actor", notification.actors.single().id.localId)
        assertNotNull(notification.post)
        assertEquals(NotificationTarget.Post(notification.post!!.id), notification.target)
        assertTrue(NotificationCategory.Social in activity.categories())
    }

    @Test
    fun customReactionExposesImageAccountAndStatus() {
        val origin = server.url("/").toString().removeSuffix("/")
        val receiver = AccountId(Connection(origin, Protocol.MASTODON), "receiver")
        val actor = reactionAccount("actor", "actor")
        val json = JSONObject()
            .put("id", "reaction-custom")
            .put("type", "pleroma:emoji_reaction")
            .put("created_at", "2026-09-07T10:00:00Z")
            .put("emoji", ":dinosaur:")
            .put("emoji_url", "https://example.social/emoji/dinosaur.png")
            .put("account", actor)
            .put("status", reactionStatus("status-custom", actor))

        val notification = MastodonNotificationMapper.notification(json, origin, receiver)

        val activity = notification.activity as NotificationActivity.EmojiReaction
        assertEquals(":dinosaur:", activity.reaction.identity)
        assertEquals(NotificationLabel.Plain("dinosaur"), activity.reaction.fallbackText)
        val emoji = checkNotNull(activity.reaction.emoji)
        assertEquals("dinosaur", emoji.shortcode)
        assertEquals("https://example.social/emoji/dinosaur.png", emoji.animatedUrl?.value)
        assertEquals("actor", notification.actors.single().id.localId)
        assertNotNull(notification.post)
        assertEquals(NotificationTarget.Post(notification.post!!.id), notification.target)
    }

    @Test
    fun reactionFilterNeedsAdvertisedSupportOrThePleromaRoute() {
        assertTrue(ServerCapabilities(supportsReactions = CapabilityStatus.Supported).supportsReactionNotifications())
        assertTrue(ServerCapabilities(reactionRoute = ReactionRoute.Pleroma).supportsReactionNotifications())
        assertFalse(ServerCapabilities().supportsReactionNotifications())
        assertFalse(
            ServerCapabilities(
                supportsReactions = CapabilityStatus.Unsupported,
                reactionRoute = ReactionRoute.None,
            ).supportsReactionNotifications(),
        )
        assertFalse(
            ServerCapabilities(
                supportsReactions = CapabilityStatus.Denied,
                reactionRoute = ReactionRoute.Unknown,
            ).supportsReactionNotifications(),
        )
    }

    @Test
    fun socialTypesIncludeReactionKeyOnlyWhenSupportedAndAllStaysUnfiltered() {
        val social = NotificationQuery(setOf(NotificationCategory.Social))

        assertTrue(social.mastodonTypes(includeReactions = true).contains("pleroma:emoji_reaction"))
        assertFalse(social.mastodonTypes(includeReactions = false).contains("pleroma:emoji_reaction"))
        assertTrue(NotificationQuery().mastodonTypes(includeReactions = true).isEmpty())
    }

    @Test
    fun socialRequestSendsReactionKeyOnlyOnReactionCapableServers() = runBlocking {
        val origin = server.url("/").toString().removeSuffix("/")
        val account = AccountId(Connection(origin, Protocol.MASTODON), "receiver")
        val social = NotificationQuery(setOf(NotificationCategory.Social))
        repeat(3) { server.enqueue(MockResponse().setBody(JSONArray().toString())) }

        val capable = MastodonNotificationService(
            origin,
            "token",
            mastodonTestClient(),
            account,
            System::currentTimeMillis,
        ) { ServerCapabilities(supportsReactions = CapabilityStatus.Supported, reactionRoute = ReactionRoute.Pleroma) }
        capable.notifications(social, null)
        assertTrue(server.takeRequest().path!!.contains("types%5B%5D=pleroma%3Aemoji_reaction"))

        val routeOnly = MastodonNotificationService(
            origin,
            "token",
            mastodonTestClient(),
            account,
            System::currentTimeMillis,
        ) { ServerCapabilities(reactionRoute = ReactionRoute.Pleroma) }
        routeOnly.notifications(social, null)
        assertTrue(server.takeRequest().path!!.contains("types%5B%5D=pleroma%3Aemoji_reaction"))

        MastodonNotificationService(origin, "token", mastodonTestClient(), account, System::currentTimeMillis)
            .notifications(social, null)
        assertFalse(server.takeRequest().path!!.contains("pleroma"))
    }

    @Test
    fun unfilteredRequestSendsNoTypesEvenWhenReactionsAreSupported() = runBlocking {
        val origin = server.url("/").toString().removeSuffix("/")
        val account = AccountId(Connection(origin, Protocol.MASTODON), "receiver")
        server.enqueue(MockResponse().setBody(JSONArray().toString()))

        val capable = MastodonNotificationService(
            origin,
            "token",
            mastodonTestClient(),
            account,
            System::currentTimeMillis,
        ) { ServerCapabilities(supportsReactions = CapabilityStatus.Supported, reactionRoute = ReactionRoute.Pleroma) }
        capable.notifications(NotificationQuery(), null)

        assertFalse(server.takeRequest().path!!.contains("types%5B%5D"))
    }

    @Test
    fun pushAlertKeyFollowsSocialPolicyOnlyWhenSupported() = runBlocking {
        val origin = server.url("/").toString().removeSuffix("/")
        val account = AccountId(Connection(origin, Protocol.MASTODON), "receiver")
        val endpoint = ValidatedUrl.https("https://push.example/endpoint")!!
        val subscription = PushSubscription(account, endpoint, "remote-1")
        fun confirmation() = MockResponse().setBody(
            JSONObject().put("id", "remote-1").put("endpoint", endpoint.value).toString(),
        )
        repeat(4) { server.enqueue(confirmation()) }

        val capable = MastodonPushService(origin, "token", mastodonTestClient(), account) {
            ServerCapabilities(supportsReactions = CapabilityStatus.Supported, reactionRoute = ReactionRoute.Pleroma)
        }
        capable.updatePolicy(subscription, setOf(NotificationCategory.Social))
        assertTrue(server.takeRequest().body.readUtf8().contains("data%5Balerts%5D%5Bpleroma%3Aemoji_reaction%5D=true"))

        capable.updatePolicy(subscription, setOf(NotificationCategory.Mentions))
        assertTrue(server.takeRequest().body.readUtf8().contains("data%5Balerts%5D%5Bpleroma%3Aemoji_reaction%5D=false"))

        MastodonPushService(origin, "token", mastodonTestClient(), account)
            .updatePolicy(subscription, setOf(NotificationCategory.Social))
        assertFalse(server.takeRequest().body.readUtf8().contains("pleroma"))

        MastodonPushService(origin, "token", mastodonTestClient(), account) {
            ServerCapabilities(reactionRoute = ReactionRoute.Pleroma)
        }.updatePolicy(subscription, setOf(NotificationCategory.Social))
        assertTrue(server.takeRequest().body.readUtf8().contains("data%5Balerts%5D%5Bpleroma%3Aemoji_reaction%5D=true"))
    }

    private fun reactionAccount(id: String, username: String) = JSONObject()
        .put("id", id)
        .put("username", username)
        .put("acct", username)
        .put("display_name", username.replaceFirstChar(Char::uppercase))

    private fun reactionStatus(id: String, actor: JSONObject) = JSONObject()
        .put("id", id)
        .put("created_at", "2026-09-07T09:00:00Z")
        .put("account", actor)
        .put("content", "<p>Hello</p>")
        .put("visibility", "public")
}
