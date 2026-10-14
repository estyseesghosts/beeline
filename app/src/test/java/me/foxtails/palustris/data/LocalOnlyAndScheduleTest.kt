package me.foxtails.palustris.data

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.auth.toDraft
import me.foxtails.palustris.data.auth.toJson
import me.foxtails.palustris.data.mastodon.MastodonCapabilityProbe
import me.foxtails.palustris.data.mastodon.MastodonMapper
import me.foxtails.palustris.data.mastodon.MastodonSource
import me.foxtails.palustris.data.mastodon.mastodonTestClient
import me.foxtails.palustris.data.misskey.CapabilityCache
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.misskey.MisskeyCapabilityProbe
import me.foxtails.palustris.data.misskey.MisskeySource
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.LocalOnlyRoute
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.PostOptions
import me.foxtails.palustris.domain.PostingCapabilities
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.QuoteApprovalPolicy
import me.foxtails.palustris.domain.ScheduleRoute
import me.foxtails.palustris.domain.ScheduledPost
import me.foxtails.palustris.domain.SchedulingCapability
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

/** Local-only and scheduled posts: probes, requests, list and cancel routes for each family, and draft storage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalOnlyAndScheduleTest {
    private lateinit var server: MockWebServer
    private lateinit var origin: String
    private val at = 1_900_000_000_000L

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        origin = server.url("/").toString().removeSuffix("/")
    }

    @After
    fun stop() = server.shutdown()

    private fun mastodon(posting: PostingCapabilities) = MastodonSource(
        origin = origin,
        token = "t",
        api = mastodonTestClient(),
        accountId = AccountId(Connection(origin, Protocol.MASTODON), "me"),
        initialCapabilities = ServerCapabilities(timelines = setOf(Timeline.Home), canPublish = true, posting = posting),
    )

    private fun misskey(posting: PostingCapabilities) = MisskeySource(
        origin = origin,
        token = "t",
        api = MisskeyApi(),
        accountId = AccountId(Connection(origin, Protocol.MISSKEY), "me"),
        capabilityCache = CapabilityCache(),
        initialCapabilities = ServerCapabilities(timelines = setOf(Timeline.Home), canPublish = true, posting = posting),
    )

    private fun statusJson() = JSONObject()
        .put("id", "s1").put("created_at", "2026-10-10T00:00:00.000Z").put("visibility", "public").put("content", "<p>x</p>")
        .put("account", JSONObject().put("id", "a").put("acct", "a").put("username", "a").put("display_name", "A").put("url", "https://x/a"))

    @Test
    fun localOnlyEvidencePerMastodonFamily() {
        fun route(instance: JSONObject) = MastodonCapabilityProbe.parseCapabilities(instance).posting.localOnly

        assertEquals(LocalOnlyRoute.MastodonFlag, route(JSONObject().put("version", "4.3.0").put("api_versions", JSONObject().put("mastodon", 4).put("glitch", 1))))
        assertEquals(LocalOnlyRoute.MastodonFlag, route(JSONObject().put("version", "4.2.0+hometown-1.1.0")))
        assertEquals(LocalOnlyRoute.PleromaVisibility, route(JSONObject().put("version", "2.7.2 (compatible; Akkoma 3.10)").put("pleroma", JSONObject())))
        assertEquals(LocalOnlyRoute.None, route(JSONObject().put("version", "4.5.0")))
    }

    @Test
    fun mastodonForkSendsLocalOnlyAndPleromaSendsLocalVisibility() = runBlocking {
        server.enqueue(MockResponse().setBody(statusJson().toString()))
        mastodon(PostingCapabilities(localOnly = LocalOnlyRoute.MastodonFlag)).create(CreatePostRequest("hi", localOnly = true))
        val flag = server.takeRequest().body.readUtf8()
        assertTrue(flag.contains("local_only=true"))
        assertTrue(flag.contains("visibility=public"))

        server.enqueue(MockResponse().setBody(statusJson().toString()))
        mastodon(PostingCapabilities(localOnly = LocalOnlyRoute.PleromaVisibility)).create(CreatePostRequest("hi", localOnly = true))
        val local = server.takeRequest().body.readUtf8()
        assertTrue(local.contains("visibility=local"))
        assertFalse(local.contains("local_only"))

        server.enqueue(MockResponse().setBody(statusJson().toString()))
        mastodon(PostingCapabilities(localOnly = LocalOnlyRoute.PleromaVisibility)).create(CreatePostRequest("hi", audience = Audience.Followers, localOnly = true))
        assertTrue(server.takeRequest().body.readUtf8().contains("visibility=private"))

        server.enqueue(MockResponse().setBody(statusJson().toString()))
        mastodon(PostingCapabilities()).create(CreatePostRequest("hi", localOnly = true))
        val vanilla = server.takeRequest().body.readUtf8()
        assertFalse(vanilla.contains("local_only"))
        assertTrue(vanilla.contains("visibility=public"))
    }

    @Test
    fun mastodonLocalPostsAreMarkedLocal() {
        assertTrue(MastodonMapper.post(statusJson().put("local_only", true), origin).localOnly)
        assertTrue(MastodonMapper.post(statusJson().put("visibility", "local"), origin).localOnly)
        assertFalse(MastodonMapper.post(statusJson(), origin).localOnly)
    }

    @Test
    fun misskeySendsLocalOnlyAndReadsItBack() = runBlocking {
        server.enqueue(MockResponse().setBody(JSONObject().put("createdNote", noteJson("n1", localOnly = true)).toString()))

        val created = misskey(PostingCapabilities(localOnly = LocalOnlyRoute.MisskeyFlag)).create(CreatePostRequest("hi", localOnly = true))

        assertTrue(JSONObject(server.takeRequest().body.readUtf8()).getBoolean("localOnly"))
        assertTrue(created.localOnly)
    }

    private fun noteJson(id: String, localOnly: Boolean = false) = JSONObject()
        .put("id", id).put("createdAt", "2026-10-10T00:00:00.000Z").put("text", "x").put("visibility", "public")
        .put("localOnly", localOnly)
        .put("user", JSONObject().put("id", "u1").put("username", "u").put("host", JSONObject.NULL))

    @Test
    fun mastodonScheduleSendsTheTimeAndReadsTheScheduledStatus() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                JSONObject().put("id", "sch1").put("scheduled_at", "2030-03-17T17:46:40.000Z")
                    .put("params", JSONObject().put("text", "later").put("visibility", "private").put("spoiler_text", "cw"))
                    .toString(),
            ),
        )

        val item = mastodon(PostingCapabilities()).schedule(CreatePostRequest("later", audience = Audience.Followers, scheduledAtEpochMillis = at))

        val request = server.takeRequest()
        assertEquals("/api/v1/statuses", request.path)
        assertTrue(request.body.readUtf8().contains("scheduled_at=2030-03-17T17%3A46%3A40Z"))
        assertEquals("sch1", item.id)
        assertEquals("later", item.text)
        assertEquals(Audience.Followers, item.audience)
        assertEquals(at, item.scheduledAtEpochMillis)
    }

    @Test
    fun mastodonListCancelAndReschedule() = runBlocking {
        val items = JSONArray()
            .put(JSONObject().put("id", "b").put("scheduled_at", "2030-03-18T00:00:00.000Z").put("params", JSONObject().put("text", "second")))
            .put(JSONObject().put("id", "a").put("scheduled_at", "2030-03-17T00:00:00.000Z").put("params", JSONObject().put("text", "first")))
        server.enqueue(MockResponse().setBody(items.toString()))
        val source = mastodon(PostingCapabilities())

        val listed = source.scheduledPosts()
        assertEquals("/api/v1/scheduled_statuses?limit=40", server.takeRequest().path)
        assertEquals(listOf("a", "b"), listed.map(ScheduledPost::id))

        server.enqueue(MockResponse().setBody("{}"))
        source.cancelScheduled("a")
        val cancel = server.takeRequest()
        assertEquals("DELETE", cancel.method)
        assertEquals("/api/v1/scheduled_statuses/a", cancel.path)

        server.enqueue(MockResponse().setBody(JSONObject().put("id", "b").put("scheduled_at", "2030-03-19T00:00:00.000Z").put("params", JSONObject().put("text", "second")).toString()))
        val moved = source.reschedule(listed[1], at)
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/api/v1/scheduled_statuses/b", put.path)
        assertTrue(put.body.readUtf8().contains("scheduled_at="))
        assertEquals("b", moved.id)
    }

    @Test
    fun sharkeyScheduleListCancelAndRescheduleByRecreating() = runBlocking {
        val source = misskey(PostingCapabilities(scheduling = SchedulingCapability(ScheduleRoute.Sharkey, 60L, 5)))
        server.enqueue(MockResponse().setBody("{}"))
        val created = source.schedule(CreatePostRequest("later", audience = Audience.Followers, localOnly = true, scheduledAtEpochMillis = at))
        val create = server.takeRequest()
        assertEquals("/api/notes/schedule/create", create.path)
        val createBody = JSONObject(create.body.readUtf8())
        assertEquals(at, createBody.getJSONObject("scheduleNote").getLong("scheduledAt"))
        assertEquals("followers", createBody.getString("visibility"))
        assertTrue(createBody.getBoolean("localOnly"))
        assertEquals(at, created.scheduledAtEpochMillis)

        val listBody = JSONArray().put(
            JSONObject().put("id", "sn1").put("scheduledAt", at).put(
                "note",
                JSONObject().put("text", "later").put("visibility", "followers").put("localOnly", true).put("cw", JSONObject.NULL)
                    .put("files", JSONArray().put(JSONObject().put("id", "f1").put("type", "image/png").put("url", "https://x/f.png"))),
            ),
        )
        server.enqueue(MockResponse().setBody(listBody.toString()))
        val listed = source.scheduledPosts()
        assertEquals("/api/notes/schedule/list", server.takeRequest().path)
        val item = listed.single()
        assertEquals("sn1", item.id)
        assertEquals(1, item.attachmentCount)
        assertEquals(Audience.Followers, item.audience)
        assertTrue(item.request?.localOnly == true)

        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("{}"))
        source.reschedule(item, at + 600_000L)
        val delete = server.takeRequest()
        assertEquals("/api/notes/schedule/delete", delete.path)
        assertEquals("sn1", JSONObject(delete.body.readUtf8()).getString("noteId"))
        val recreate = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(at + 600_000L, recreate.getJSONObject("scheduleNote").getLong("scheduledAt"))
        assertEquals(listOf("f1"), (0 until recreate.getJSONArray("fileIds").length()).map { recreate.getJSONArray("fileIds").getString(it) })
    }

    @Test
    fun misskeyDraftRoutesScheduleListMoveAndCancel() = runBlocking {
        val source = misskey(PostingCapabilities(scheduling = SchedulingCapability(ScheduleRoute.MisskeyDrafts, 60L, 1)))
        server.enqueue(MockResponse().setBody("{}"))
        source.schedule(CreatePostRequest("later", scheduledAtEpochMillis = at))
        val create = server.takeRequest()
        assertEquals("/api/notes/drafts/create", create.path)
        val createBody = JSONObject(create.body.readUtf8())
        assertEquals(at, createBody.getLong("scheduledAt"))
        assertTrue(createBody.getBoolean("isActuallyScheduled"))

        val drafts = JSONArray()
            .put(JSONObject().put("id", "d1").put("text", "scheduled").put("visibility", "public").put("scheduledAt", at).put("isActuallyScheduled", true))
            .put(JSONObject().put("id", "d2").put("text", "plain draft").put("visibility", "public").put("scheduledAt", JSONObject.NULL).put("isActuallyScheduled", false))
        server.enqueue(MockResponse().setBody(drafts.toString()))
        val listed = source.scheduledPosts()
        val listRequest = server.takeRequest()
        assertEquals("/api/notes/drafts/list", listRequest.path)
        assertTrue(JSONObject(listRequest.body.readUtf8()).getBoolean("scheduled"))
        assertEquals(listOf("d1"), listed.map(ScheduledPost::id))

        server.enqueue(MockResponse().setBody("{}"))
        source.reschedule(listed.single(), at + 1_000L)
        val update = server.takeRequest()
        assertEquals("/api/notes/drafts/update", update.path)
        val updateBody = JSONObject(update.body.readUtf8())
        assertEquals("d1", updateBody.getString("draftId"))
        assertEquals(at + 1_000L, updateBody.getLong("scheduledAt"))

        server.enqueue(MockResponse().setBody("{}"))
        source.cancelScheduled("d1")
        val delete = server.takeRequest()
        assertEquals("/api/notes/drafts/delete", delete.path)
        assertEquals("d1", JSONObject(delete.body.readUtf8()).getString("draftId"))
    }

    @Test
    fun aServerWithoutSchedulingRefusesBeforeTheNetwork() {
        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { misskey(PostingCapabilities()).schedule(CreatePostRequest("x", scheduledAtEpochMillis = at)) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun misskeyProbeReadsSchedulingFromEndpointsAndPolicies() = runBlocking {
        suspend fun posting(endpoints: JSONArray, policies: JSONObject): PostingCapabilities {
            MockWebServer().use { probeServer ->
                probeServer.enqueue(MockResponse().setBody(JSONObject().put("version", "2026.1.0").toString()))
                probeServer.enqueue(MockResponse().setBody(JSONObject().put("policies", policies).toString()))
                probeServer.enqueue(MockResponse().setBody("[]"))
                probeServer.enqueue(MockResponse().setBody(endpoints.toString()))
                val connection = Connection(probeServer.url("/").toString().removeSuffix("/"), Protocol.MISSKEY)
                return MisskeyCapabilityProbe(MisskeyApi(), "token").probeCapabilities(connection).posting
            }
        }

        val sharkey = posting(JSONArray().put("notes/edit").put("notes/schedule/create"), JSONObject().put("scheduleNoteMax", 3))
        assertEquals(SchedulingCapability(ScheduleRoute.Sharkey, 60L, 3), sharkey.scheduling)
        assertEquals(LocalOnlyRoute.MisskeyFlag, sharkey.localOnly)

        val misskey = posting(JSONArray().put("notes/drafts/create"), JSONObject())
        assertEquals(SchedulingCapability(ScheduleRoute.MisskeyDrafts, 60L, 1), misskey.scheduling)

        assertNull(posting(JSONArray().put("notes/drafts/create"), JSONObject().put("scheduledNoteLimit", 0)).scheduling)
        assertNull(posting(JSONArray().put("notes/create"), JSONObject()).scheduling)
    }

    @Test
    fun mastodonProbeAlwaysOffersSchedulingFiveMinutesAhead() {
        val posting = MastodonCapabilityProbe.parseCapabilities(JSONObject().put("version", "4.5.0")).posting
        assertEquals(SchedulingCapability(ScheduleRoute.Mastodon, 300L), posting.scheduling)
    }

    @Test
    fun draftsKeepLocalOnlyThePolicyAndTheScheduledTime() {
        val draft = PostDraft(
            id = "d",
            accountId = AccountId(Connection(origin, Protocol.MASTODON), "me"),
            text = "x",
            options = PostOptions(localOnly = true, quotePolicy = QuoteApprovalPolicy.Nobody, scheduledAtEpochMillis = at),
        )

        val restored = draft.toJson().toDraft()

        assertEquals(draft.options, restored.options)
        assertNotNull(restored.options.scheduledAtEpochMillis)
        assertTrue(PostDraft(id = "e", accountId = null, text = "").toJson().toDraft().options.isDefault)
    }

    @Test
    fun draftsKeepContentType() {
        val markdown = PostDraft(
            id = "m",
            accountId = AccountId(Connection(origin, Protocol.MISSKEY), "me"),
            text = "x",
            options = PostOptions(contentType = "text/markdown"),
        )
        assertEquals("text/markdown", markdown.toJson().toDraft().options.contentType)

        val omit = PostDraft(id = "o", accountId = null, text = "x")
        assertNull(omit.toJson().toDraft().options.contentType)

        val legacy = PostDraft(id = "l", accountId = null, text = "x").toJson().apply { remove("contentType") }
        assertNull(legacy.toDraft().options.contentType)
    }
}
