package me.foxtails.palustris.data.mastodon

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.PollRequest
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ScheduledPost
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SourceError
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Slice 7 gates: the Pixelfed probe reports list timelines Unsupported with `scheduling` and
 * `poll` null and edit Unsupported (see MastodonCapabilityProbeFlavorTest). These tests prove the
 * source honors those capabilities before any network, so the missing Pixelfed routes (X4-X6, X8)
 * can never answer 404 at runtime, and `create()` never sends `scheduled_at` or `poll[...]`
 * the server did not ask for.
 *
 * Runtime demotion of unexpected 404s belongs to Slice 8: a Trends section whose routes all
 * answer 404 hides for that load without changing the stored capabilities.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonPixelfedComposerGatesTest {
    private lateinit var server: MockWebServer
    private lateinit var origin: String

    @Before
    fun startServer() {
        server = MockWebServer().also { it.start() }
        origin = server.url("/").toString().removeSuffix("/")
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    private fun pixelfedCapabilities(): ServerCapabilities = MastodonCapabilityProbe.parseCapabilities(
        pixelfedInstance(),
    )

    /**
     * Representative Pixelfed instance shape: the compatibility version plus a Mastodon-style
     * `configuration` block. The live Pixelfed limit values are unverified (X12); the values
     * below exercise the parser only.
     */
    private fun pixelfedInstance(): JSONObject = JSONObject()
        .put("version", "3.5.3 (compatible; Pixelfed 0.12.5)")
        .put(
            "configuration",
            JSONObject()
                .put(
                    "statuses",
                    JSONObject()
                        .put("max_characters", 500)
                        .put("max_media_attachments", 10)
                        .put("characters_reserved_per_url", 23),
                )
                .put(
                    "media_attachments",
                    JSONObject()
                        .put("image_size_limit", 15_728_640)
                        .put("video_size_limit", 103_809_024)
                        .put("description_limit", 1500),
                ),
        )

    private fun pixelfedSource() = MastodonSource(
        origin = origin,
        token = "token",
        api = mastodonTestClient(),
        accountId = AccountId(Connection(origin, Protocol.MASTODON), "viewer"),
        initialCapabilities = pixelfedCapabilities(),
    )

    private fun id(value: String) = EntityId(origin, value)

    private fun statusJson() = JSONObject()
        .put("id", "s1")
        .put("created_at", "2026-10-10T00:00:00.000Z")
        .put("visibility", "public")
        .put("content", "<p>hi</p>")
        .put(
            "account",
            JSONObject().put("id", "a1").put("acct", "a").put("username", "a")
                .put("display_name", "A").put("url", "https://x/a"),
        )
        .toString()

    @Test
    fun scheduleThrowsUnsupportedWithoutNetworkOnPixelfed() {
        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { pixelfedSource().schedule(CreatePostRequest("later", scheduledAtEpochMillis = 1_900_000_000_000L)) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun scheduledReadsWritesAndMovesThrowWithoutNetworkOnPixelfed() {
        val source = pixelfedSource()
        val item = ScheduledPost("sch1", 1_900_000_000_000L, "later")

        assertThrows(SourceError.Unsupported::class.java) { runBlocking { source.scheduledPosts() } }
        assertThrows(SourceError.Unsupported::class.java) { runBlocking { source.cancelScheduled("sch1") } }
        assertThrows(SourceError.Unsupported::class.java) { runBlocking { source.reschedule(item, 1_900_000_100_000L) } }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun createWithPollThrowsUnsupportedWithoutNetworkOnPixelfed() {
        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { pixelfedSource().create(CreatePostRequest("Which?", poll = PollRequest(listOf("Cats", "Dogs")))) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun createWithoutPollNeverSendsPollOrScheduledFields() = runBlocking {
        server.enqueue(MockResponse().setBody(statusJson()))

        pixelfedSource().create(CreatePostRequest("hi"))

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("status=hi"))
        assertFalse(body.contains("poll"))
        assertFalse(body.contains("scheduled_at"))
    }

    @Test
    fun votePollThrowsUnsupportedWithoutNetworkOnPixelfed() {
        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { pixelfedSource().votePoll(id("s1"), "poll-1", listOf(0)) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun editSourceThrowsUnsupportedWithoutNetworkOnPixelfed() {
        assertThrows(SourceError.Unsupported::class.java) { runBlocking { pixelfedSource().editSource(id("s1")) } }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun listTimelineThrowsUnsupportedWithoutNetworkWhileTheListItselfStillReads() {
        val source = pixelfedSource()

        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.feed(FeedSource.UserList("7", "Friends")) }
        }
        assertEquals(0, server.requestCount)

        server.enqueue(MockResponse().setBody("[{\"id\":\"7\",\"title\":\"Friends\"}]"))
        val lists = runBlocking { source.userLists() }

        assertEquals(listOf(FeedSource.UserList("7", "Friends")), lists)
        assertEquals("/api/v1/lists", server.takeRequest().path)
    }

    @Test
    fun unprobedUnknownCapabilitiesStillAttemptTheRoutes() = runBlocking {
        val unknown = MastodonSource(
            origin = origin,
            token = "token",
            api = mastodonTestClient(),
            accountId = AccountId(Connection(origin, Protocol.MASTODON), "viewer"),
            initialCapabilities = MastodonCapabilityProbe.parseCapabilities(JSONObject()),
        )
        server.enqueue(
            MockResponse().setBody(
                JSONObject().put("id", "sch1").put("scheduled_at", "2030-03-17T17:46:40.000Z")
                    .put("params", JSONObject().put("text", "later")).toString(),
            ),
        )
        server.enqueue(MockResponse().setBody(statusJson()))
        server.enqueue(MockResponse().setBody("[" + statusJson() + "]"))

        unknown.schedule(CreatePostRequest("later", scheduledAtEpochMillis = 1_900_000_000_000L))
        unknown.create(CreatePostRequest("Which?", poll = PollRequest(listOf("Cats", "Dogs"))))
        unknown.feed(FeedSource.UserList("7", "Friends"))

        assertEquals(3, server.requestCount)
    }

    @Test
    fun trendsWithEverySectionMissingReturnsEmptyWithoutChangingCapabilities() = runBlocking {
        val source = pixelfedSource()
        val before = source.capabilities
        repeat(3) {
            server.enqueue(MockResponse().setResponseCode(404).setBody("{\"error\":\"Record not found\"}"))
        }

        val trends = source.trends()

        assertTrue(trends.tags.isEmpty())
        assertTrue(trends.posts.items.isEmpty())
        assertTrue(trends.links.isEmpty())
        assertEquals(3, server.requestCount)
        assertEquals(before, source.capabilities)
    }

    @Test
    fun pixelfedConfigurationParsesMediaLimitsAndKeepsTheDefaultUploadTypes() {
        val posting = pixelfedCapabilities().posting

        assertEquals(500, pixelfedCapabilities().maxPostLength)
        assertEquals(10, posting.maxAttachments)
        assertEquals(15_728_640L, posting.maxImageBytes)
        assertEquals(103_809_024L, posting.maxVideoBytes)
        assertEquals(1500, posting.maxAltTextLength)
        // Pixelfed lists no accepted MIME types, so the image default holds until proven live.
        assertEquals(setOf("image/jpeg", "image/png", "image/gif", "image/webp"), posting.uploadTypes)
    }
}
