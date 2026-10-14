package me.foxtails.palustris.data

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.mastodon.MastodonCapabilityProbe
import me.foxtails.palustris.data.mastodon.MastodonMapper
import me.foxtails.palustris.data.mastodon.MastodonSource
import me.foxtails.palustris.data.mastodon.mastodonTestClient
import me.foxtails.palustris.data.misskey.CapabilityCache
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.misskey.MisskeyMapper
import me.foxtails.palustris.data.misskey.MisskeySource
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.PollLimits
import me.foxtails.palustris.domain.PollRequest
import me.foxtails.palustris.domain.PostingCapabilities
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PollAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var origin: String

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        origin = server.url("/").toString().removeSuffix("/")
    }

    @After
    fun stop() = server.shutdown()

    private fun mastodon() = MastodonSource(
        origin = origin,
        token = "t",
        api = mastodonTestClient(),
        accountId = AccountId(Connection(origin, Protocol.MASTODON), "me"),
        initialCapabilities = ServerCapabilities(
            timelines = setOf(Timeline.Home),
            canPublish = true,
            // The poll routes under test need the server's poll limits; a server without them
            // throws before the network (see MastodonPixelfedComposerGatesTest).
            posting = PostingCapabilities(poll = PollLimits(4, 50, 300L, 2_629_746L)),
        ),
    )

    private fun misskey() = MisskeySource(
        origin = origin,
        token = "t",
        api = MisskeyApi(),
        accountId = AccountId(Connection(origin, Protocol.MISSKEY), "me"),
        capabilityCache = CapabilityCache(),
    )

    private fun id(value: String) = EntityId(origin, value)

    private fun mastodonPoll(
        voted: Boolean = false,
        expired: Boolean = false,
        multiple: Boolean = false,
        ownVotes: List<Int> = emptyList(),
        expiresAt: String? = "2026-10-11T00:00:00.000Z",
    ) = JSONObject()
        .put("id", "poll-1")
        .put("expires_at", expiresAt ?: JSONObject.NULL)
        .put("expired", expired)
        .put("multiple", multiple)
        .put("votes_count", 5)
        .put("voters_count", if (multiple) 4 else JSONObject.NULL)
        .put("voted", voted)
        .put("own_votes", JSONArray(ownVotes))
        .put(
            "options",
            JSONArray()
                .put(JSONObject().put("title", "Cats").put("votes_count", 3))
                .put(JSONObject().put("title", "Dogs").put("votes_count", 2)),
        )

    @Test
    fun mastodonMapperReadsNotVotedVotedExpiredAndMultiplePolls() {
        val open = MastodonMapper.poll(mastodonPoll())
        assertEquals("poll-1", open.id)
        assertEquals(listOf("Cats", "Dogs"), open.options.map { it.text })
        assertTrue(open.open)
        assertFalse(open.showsResults)
        assertTrue(open.ownVotes.isEmpty())
        assertNull(open.votersCount)

        val voted = MastodonMapper.poll(mastodonPoll(voted = true, ownVotes = listOf(1)))
        assertTrue(voted.voted)
        assertEquals(listOf(1), voted.ownVotes)
        assertTrue(voted.options[1].picked)
        assertTrue(voted.showsResults)
        assertFalse(voted.open)

        val expired = MastodonMapper.poll(mastodonPoll(expired = true))
        assertTrue(expired.expired)
        assertTrue(expired.showsResults)

        val multiple = MastodonMapper.poll(mastodonPoll(multiple = true, voted = true, ownVotes = listOf(0, 1), expiresAt = null))
        assertTrue(multiple.multiple)
        assertEquals(listOf(0, 1), multiple.ownVotes)
        assertEquals(4, multiple.votersCount)
        assertNull(multiple.expiresAtEpochMillis)
    }

    @Test
    fun mastodonStatusCarriesItsPoll() {
        val status = JSONObject(statusJson()).put("poll", mastodonPoll())

        assertEquals("poll-1", MastodonMapper.post(status, origin).poll?.id)
    }

    @Test
    fun misskeyMapperReadsChoicesVotedAndExpiry() {
        val note = JSONObject()
            .put(
                "choices",
                JSONArray()
                    .put(JSONObject().put("text", "Yes").put("votes", 4).put("isVoted", true))
                    .put(JSONObject().put("text", "No").put("votes", 1).put("isVoted", false)),
            )
            .put("multiple", false)
            .put("expiresAt", "2026-10-09T00:00:00.000Z")

        val poll = MisskeyMapper.poll("note-1", note, nowMillis = java.time.Instant.parse("2026-10-10T00:00:00Z").toEpochMilli())

        assertEquals("note-1", poll.id)
        assertTrue(poll.expired)
        assertTrue(poll.voted)
        assertEquals(listOf(0), poll.ownVotes)
        assertEquals(5, poll.totalVotes)

        val open = MisskeyMapper.poll("note-1", note.put("expiresAt", JSONObject.NULL))
        assertFalse(open.expired)
        assertNull(open.expiresAtEpochMillis)
    }

    @Test
    fun mastodonCreateSendsPollFieldsAndDefaultsTheExpiry() = runBlocking {
        server.enqueue(MockResponse().setBody(statusJson()))

        mastodon().create(
            CreatePostRequest("Which?", poll = PollRequest(listOf("Cats", "Dogs"), multiple = true, expiresInSeconds = 3600L, hideTotals = true)),
        )

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("poll%5Boptions%5D%5B%5D=Cats"))
        assertTrue(body.contains("poll%5Boptions%5D%5B%5D=Dogs"))
        assertTrue(body.contains("poll%5Bexpires_in%5D=3600"))
        assertTrue(body.contains("poll%5Bmultiple%5D=true"))
        assertTrue(body.contains("poll%5Bhide_totals%5D=true"))

        server.enqueue(MockResponse().setBody(statusJson()))
        mastodon().create(CreatePostRequest("Which?", poll = PollRequest(listOf("A", "B"))))
        val defaulted = server.takeRequest().body.readUtf8()
        assertTrue(defaulted.contains("poll%5Bexpires_in%5D=86400"))
        assertFalse(defaulted.contains("multiple"))
    }

    @Test
    fun mastodonRefusesAPollTogetherWithMedia() {
        val request = CreatePostRequest(
            "x",
            attachments = listOf(Attachment(url = "https://example.org/a.jpg", mimeType = "image/jpeg", id = "m1")),
            poll = PollRequest(listOf("a", "b")),
        )

        assertThrows(SourceError.Unsupported::class.java) { runBlocking { mastodon().create(request) } }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun mastodonVoteSendsChoicesAndReturnsTheUpdatedPoll() = runBlocking {
        server.enqueue(MockResponse().setBody(mastodonPoll(voted = true, ownVotes = listOf(0, 1), multiple = true).toString()))

        val poll = mastodon().votePoll(id("s1"), "poll-1", listOf(0, 1))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/polls/poll-1/votes", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("choices%5B%5D=0"))
        assertTrue(body.contains("choices%5B%5D=1"))
        assertTrue(poll.voted)
        assertEquals(listOf(0, 1), poll.ownVotes)
    }

    @Test
    fun misskeyCreateSendsExpiredAfterInMilliseconds() = runBlocking {
        server.enqueue(MockResponse().setBody(JSONObject().put("createdNote", noteJson("n1")).toString()))

        misskey().create(CreatePostRequest("Which?", poll = PollRequest(listOf("a", "b"), multiple = true, expiresInSeconds = 600L)))

        val poll = JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("poll")
        assertEquals(600_000L, poll.getLong("expiredAfter"))
        assertTrue(poll.getBoolean("multiple"))
        assertFalse(poll.has("expiresAt"))
    }

    @Test
    fun misskeyVoteCallsOncePerChoiceThenRefetchesTheNote() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("{}"))
        val voted = JSONObject()
            .put("multiple", true)
            .put(
                "choices",
                JSONArray()
                    .put(JSONObject().put("text", "a").put("votes", 2).put("isVoted", true))
                    .put(JSONObject().put("text", "b").put("votes", 1).put("isVoted", true)),
            )
        server.enqueue(MockResponse().setBody(noteJson("n1").put("poll", voted).toString()))

        val poll = misskey().votePoll(id("n1"), "n1", listOf(0, 1))

        val first = server.takeRequest()
        val second = server.takeRequest()
        val refetch = server.takeRequest()
        assertEquals("/api/notes/polls/vote", first.path)
        assertEquals(0, JSONObject(first.body.readUtf8()).getInt("choice"))
        assertEquals(1, JSONObject(second.body.readUtf8()).getInt("choice"))
        assertEquals("/api/notes/show", refetch.path)
        assertEquals(listOf(0, 1), poll.ownVotes)
        assertEquals(3, poll.totalVotes)
    }

    @Test
    fun mastodonProbeReadsPollLimitsFromConfigurationOrPleromaOrDefaults() {
        val configured = JSONObject().put("version", "4.5.0").put(
            "configuration",
            JSONObject().put(
                "polls",
                JSONObject().put("max_options", 6).put("max_characters_per_option", 80).put("min_expiration", 600).put("max_expiration", 86400),
            ),
        )
        val mastodon = requireNotNull(MastodonCapabilityProbe.parseCapabilities(configured).posting.poll)
        assertEquals(6, mastodon.maxOptions)
        assertEquals(80, mastodon.maxOptionLength)
        assertEquals(600L, mastodon.minExpirySeconds)
        assertEquals(86400L, mastodon.maxExpirySeconds)

        val pleromaInstance = JSONObject().put("version", "2.7.2 (compatible; Pleroma 2.6.0)").put(
            "poll_limits",
            JSONObject().put("max_options", 20).put("max_option_chars", 200).put("min_expiration", 10).put("max_expiration", 31536000),
        )
        val pleroma = requireNotNull(MastodonCapabilityProbe.parseCapabilities(pleromaInstance).posting.poll)
        assertEquals(20, pleroma.maxOptions)
        assertEquals(200, pleroma.maxOptionLength)
        assertEquals(31536000L, pleroma.maxExpirySeconds)

        val defaults = requireNotNull(MastodonCapabilityProbe.parseCapabilities(JSONObject().put("version", "4.2.0")).posting.poll)
        assertEquals(4, defaults.maxOptions)
        assertEquals(50, defaults.maxOptionLength)
        assertNotNull(defaults.maxExpirySeconds)
    }

    @Test
    fun mastodonProbeReadsEditSupportFromVersionOrPleromaFeatures() {
        fun edit(instance: JSONObject) = MastodonCapabilityProbe.parseCapabilities(instance).ownPosts.edit

        assertEquals(CapabilityStatus.Supported, edit(JSONObject().put("version", "3.5.0")))
        assertEquals(CapabilityStatus.Unsupported, edit(JSONObject().put("version", "3.4.9")))
        val akkoma = JSONObject().put("version", "2.7.2 (compatible; Akkoma 3.10)").put(
            "pleroma",
            JSONObject().put("metadata", JSONObject().put("features", JSONArray().put("editing"))),
        )
        assertEquals(CapabilityStatus.Supported, edit(akkoma))
        val oldPleroma = JSONObject().put("version", "2.7.2 (compatible; Pleroma 2.5.0)").put(
            "pleroma",
            JSONObject().put("metadata", JSONObject().put("features", JSONArray().put("shareable_emoji_packs"))),
        )
        assertEquals(CapabilityStatus.Unsupported, edit(oldPleroma))
    }

    private fun noteJson(id: String) = JSONObject()
        .put("id", id)
        .put("createdAt", "2026-10-10T00:00:00.000Z")
        .put("text", "x")
        .put("visibility", "public")
        .put("user", JSONObject().put("id", "u1").put("username", "u").put("host", JSONObject.NULL))

    private fun statusJson() = JSONObject()
        .put("id", "s1")
        .put("created_at", "2026-10-10T00:00:00.000Z")
        .put("visibility", "public")
        .put("content", "<p>x</p>")
        .put("account", JSONObject().put("id", "a1").put("acct", "a").put("username", "a").put("display_name", "A").put("url", "https://x/a"))
        .toString()
}
