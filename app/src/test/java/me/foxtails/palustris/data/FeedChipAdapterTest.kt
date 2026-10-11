package me.foxtails.palustris.data

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.mastodon.MastodonCapabilityProbe
import me.foxtails.palustris.data.mastodon.MastodonSource
import me.foxtails.palustris.data.mastodon.mastodonTestClient
import me.foxtails.palustris.data.misskey.CapabilityCache
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.misskey.MisskeySource
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** M7 routes: lists, followed hashtags and antennas on both server families. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedChipAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var origin: String
    private val seen = CopyOnWriteArrayList<RecordedRequest>()
    private var answers: (RecordedRequest) -> MockResponse = { MockResponse().setResponseCode(404) }

    @Before
    fun start() {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    seen += request
                    return answers(request)
                }
            }
            start()
        }
        origin = server.url("/").toString().removeSuffix("/")
    }

    @After
    fun stop() = server.shutdown()

    private fun json(body: Any) = MockResponse().setHeader("Content-Type", "application/json").setBody(body.toString())

    private fun mastodonAccount() = JSONObject().put("id", "a1").put("acct", "u1").put("username", "u1")
        .put("display_name", "U1").put("url", "https://x/u1")

    private fun status(id: String) = JSONObject().put("id", id).put("created_at", "2026-10-10T00:00:00.000Z")
        .put("visibility", "public").put("content", "<p>hi</p>").put("account", mastodonAccount())

    private fun mastodon() = MastodonSource(
        origin = origin,
        token = "t",
        api = mastodonTestClient(),
        accountId = AccountId(Connection(origin, Protocol.MASTODON), "me"),
        initialCapabilities = ServerCapabilities(timelines = setOf(Timeline.Home), canPublish = true),
    )

    private fun misskey() = MisskeySource(
        origin = origin,
        token = "t",
        api = MisskeyApi(),
        accountId = AccountId(Connection(origin, Protocol.MISSKEY), "me"),
        capabilityCache = CapabilityCache(),
        initialCapabilities = ServerCapabilities(timelines = setOf(Timeline.Home)),
    )

    private fun note(id: String) = JSONObject().put("id", id).put("createdAt", "2026-10-10T00:00:00.000Z")
        .put("text", "hi").put("visibility", "public")
        .put("user", JSONObject().put("id", "m1").put("username", "m1").put("host", JSONObject.NULL).put("name", "M1"))

    private fun body(request: RecordedRequest) = JSONObject(request.body.readUtf8())

    @Test
    fun mastodonListsReadIdAndTitle() = runBlocking {
        answers = {
            json(JSONArray().put(JSONObject().put("id", "7").put("title", "Friends")).put(JSONObject().put("id", "9").put("title", "")))
        }

        val lists = mastodon().userLists()

        assertEquals(listOf(FeedSource.UserList("7", "Friends"), FeedSource.UserList("9", "9")), lists)
        assertEquals("/api/v1/lists", seen.single().path)
    }

    @Test
    fun mastodonListTimelineUsesTheListRouteAndPagesByLink() = runBlocking {
        answers = { request ->
            if (request.path.orEmpty().contains("max_id=")) {
                json(JSONArray().put(status("3")))
            } else {
                json(JSONArray().put(status("5")).put(status("4"))).setHeader(
                    "Link",
                    "<$origin/api/v1/timelines/list/7?limit=40&max_id=4>; rel=\"next\"",
                )
            }
        }
        val source = mastodon()

        val first = source.feed(FeedSource.UserList("7", "Friends"))
        val second = source.feed(FeedSource.UserList("7", "Friends"), first.nextCursor)

        assertEquals(listOf("5", "4"), first.items.map { it.id.value })
        assertEquals(listOf("3"), second.items.map { it.id.value })
        assertEquals("/api/v1/timelines/list/7?limit=40", seen.first().path)
        assertTrue(seen.last().path.orEmpty().contains("max_id=4"))
    }

    @Test
    fun mastodonFollowedTagsFollowTheLinkHeader() = runBlocking {
        answers = { request ->
            if (request.path.orEmpty().contains("max_id=")) {
                json(JSONArray().put(JSONObject().put("name", "dogs")))
            } else {
                json(JSONArray().put(JSONObject().put("name", "cats")).put(JSONObject().put("name", "art"))).setHeader(
                    "Link",
                    "<$origin/api/v1/followed_tags?limit=200&max_id=2>; rel=\"next\"",
                )
            }
        }

        val tags = mastodon().followedTags()

        assertEquals(listOf("cats", "art", "dogs"), tags)
    }

    @Test
    fun mastodonFollowAndUnfollowPostToTheTagRoutesWithoutTheHash() = runBlocking {
        answers = { json(JSONObject().put("name", "cats").put("following", true)) }
        val source = mastodon()

        source.followTag("#Cats")
        source.unfollowTag("cats")

        assertEquals(listOf("/api/v1/tags/Cats/follow", "/api/v1/tags/cats/unfollow"), seen.map { it.path })
        assertTrue(seen.all { it.method == "POST" })
    }

    @Test
    fun mastodonHasNoAntennas() {
        assertThrows(SourceError.Unsupported::class.java) { runBlocking { mastodon().feed(FeedSource.Antenna("1", "a")) } }
    }

    @Test
    fun mastodonHashtagFeedUsesTheTagTimeline() = runBlocking {
        answers = { json(JSONArray().put(status("1"))) }

        mastodon().feed(FeedSource.Hashtag("cats", me.foxtails.palustris.domain.HashtagOrigin.Server))

        assertTrue(seen.single().path.orEmpty().startsWith("/api/v1/timelines/tag/cats"))
    }

    @Test
    fun misskeyListsAndAntennasReadIdAndName() = runBlocking {
        answers = { request ->
            when (request.path) {
                "/api/users/lists/list" -> json(JSONArray().put(JSONObject().put("id", "l1").put("name", "Artists")))
                "/api/antennas/list" -> json(JSONArray().put(JSONObject().put("id", "n1").put("name", "Cats")))
                else -> MockResponse().setResponseCode(404)
            }
        }
        val source = misskey()

        assertEquals(listOf(FeedSource.UserList("l1", "Artists")), source.userLists())
        assertEquals(listOf(FeedSource.Antenna("n1", "Cats")), source.antennas())
    }

    @Test
    fun misskeyListAndAntennaTimelinesPageByUntilId() = runBlocking {
        answers = { json(JSONArray().put(note("n9")).put(note("n8"))) }
        val source = misskey()

        val list = source.feed(FeedSource.UserList("l1", "Artists"))
        val antenna = source.feed(FeedSource.Antenna("n1", "Cats"), "n8")

        assertEquals("n8", list.nextCursor)
        assertEquals(listOf("n9", "n8"), antenna.items.map { it.id.value })
        assertEquals("/api/notes/user-list-timeline", seen[0].path)
        assertEquals("l1", body(seen[0]).getString("listId"))
        assertEquals("/api/antennas/notes", seen[1].path)
        val antennaBody = body(seen[1])
        assertEquals("n1", antennaBody.getString("antennaId"))
        assertEquals("n8", antennaBody.getString("untilId"))
    }

    @Test
    fun misskeyKeepsSavedTagsOnTheDevice() {
        assertThrows(SourceError.Unsupported::class.java) { runBlocking { misskey().followTag("cats") } }
        assertTrue(runBlocking { misskey().followedTags() }.isEmpty())
    }

    @Test
    fun mastodonProbeOffersFollowedTagsFromFourZeroAndNeverAntennas() {
        fun probe(version: String) = MastodonCapabilityProbe.parseCapabilities(JSONObject().put("version", version)).feedChips
        val modern = probe("4.3.1")
        assertEquals(CapabilityStatus.Supported, modern.lists)
        assertEquals(CapabilityStatus.Supported, modern.followedTags)
        assertEquals(CapabilityStatus.Unsupported, modern.antennas)
        assertEquals(CapabilityStatus.Unsupported, probe("3.5.0").followedTags)
        assertEquals(CapabilityStatus.Unknown, probe("garbage").followedTags)
    }
}
