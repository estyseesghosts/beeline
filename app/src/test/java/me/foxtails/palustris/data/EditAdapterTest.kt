package me.foxtails.palustris.data

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.mastodon.MastodonMapper
import me.foxtails.palustris.data.mastodon.MastodonSource
import me.foxtails.palustris.data.mastodon.mastodonTestClient
import me.foxtails.palustris.data.misskey.CapabilityCache
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.misskey.MisskeyMapper
import me.foxtails.palustris.data.misskey.MisskeySource
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EditAttachment
import me.foxtails.palustris.domain.EditPostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnPostCapabilities
import me.foxtails.palustris.domain.PollRequest
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

/** Contract tests for the edit route of both adapters, including the case where edit is not supported. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var origin: String

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        origin = server.url("/").toString().removeSuffix("/")
    }

    @After
    fun stop() = server.shutdown()

    private fun capabilities(edit: CapabilityStatus) = ServerCapabilities(
        timelines = setOf(Timeline.Home),
        canPublish = true,
        ownPosts = OwnPostCapabilities(delete = CapabilityStatus.Supported, edit = edit),
    )

    private fun mastodon(edit: CapabilityStatus = CapabilityStatus.Supported) = MastodonSource(
        origin = origin,
        token = "t",
        api = mastodonTestClient(),
        accountId = AccountId(Connection(origin, Protocol.MASTODON), "me"),
        initialCapabilities = capabilities(edit),
    )

    private fun misskey(edit: CapabilityStatus = CapabilityStatus.Supported) = MisskeySource(
        origin = origin,
        token = "t",
        api = MisskeyApi(),
        accountId = AccountId(Connection(origin, Protocol.MISSKEY), "me"),
        capabilityCache = CapabilityCache(),
        initialCapabilities = capabilities(edit),
    )

    private fun id(value: String) = EntityId(origin, value)

    private fun statusJson(editedAt: String? = "2026-10-10T12:00:00.000Z") = JSONObject()
        .put("id", "s1")
        .put("created_at", "2026-10-10T00:00:00.000Z")
        .put("edited_at", editedAt ?: JSONObject.NULL)
        .put("visibility", "public")
        .put("content", "<p>new</p>")
        .put("account", JSONObject().put("id", "a1").put("acct", "a").put("username", "a").put("display_name", "A").put("url", "https://x/a"))

    @Test
    fun mastodonEditSourceReadsRawTextAndWarning() = runBlocking {
        server.enqueue(MockResponse().setBody(JSONObject().put("id", "s1").put("text", "raw *text*").put("spoiler_text", "cw").toString()))

        val source = mastodon().editSource(id("s1"))

        assertEquals("/api/v1/statuses/s1/source", server.takeRequest().path)
        assertEquals("raw *text*", source.text)
        assertEquals("cw", source.contentWarning)
    }

    @Test
    fun mastodonEditSendsOnlyTheChangedFieldsPlusTheText() = runBlocking {
        server.enqueue(MockResponse().setBody(statusJson().toString()))

        val edited = mastodon().edit(id("s1"), EditPostRequest(text = "new"))

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/statuses/s1", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("status=new"))
        assertFalse(body.contains("spoiler_text"))
        assertFalse(body.contains("media_ids"))
        assertFalse(body.contains("poll"))
        assertNotNull(edited.editedAtEpochMillis)
    }

    @Test
    fun mastodonEditSendsWarningAltTextAndPoll() = runBlocking {
        server.enqueue(MockResponse().setBody(statusJson().toString()))

        mastodon().edit(
            id("s1"),
            EditPostRequest(
                text = "new",
                contentWarning = "careful",
                attachments = listOf(EditAttachment("m1", "A cat"), EditAttachment("m2", null)),
                poll = PollRequest(listOf("a", "b"), multiple = true, expiresInSeconds = 600),
            ),
        )

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("spoiler_text=careful"))
        assertTrue(body.contains("media_ids%5B%5D=m1"))
        assertTrue(body.contains("media_ids%5B%5D=m2"))
        assertTrue(body.contains("media_attributes%5B0%5D%5Bid%5D=m1"))
        assertTrue(body.contains("media_attributes%5B0%5D%5Bdescription%5D=A+cat") || body.contains("media_attributes%5B0%5D%5Bdescription%5D=A%20cat"))
        assertTrue(body.contains("media_attributes%5B1%5D%5Bdescription%5D="))
        assertTrue(body.contains("poll%5Boptions%5D%5B%5D=a"))
        assertTrue(body.contains("poll%5Bexpires_in%5D=600"))
        assertTrue(body.contains("poll%5Bmultiple%5D=true"))
    }

    @Test
    fun mastodonEditIsRefusedBeforeTheNetworkWhenUnsupported() {
        listOf(CapabilityStatus.Unsupported, CapabilityStatus.Unknown).forEach { status ->
            assertThrows(SourceError.Unsupported::class.java) { runBlocking { mastodon(status).edit(id("s1"), EditPostRequest("x")) } }
            assertThrows(SourceError.Unsupported::class.java) { runBlocking { mastodon(status).editSource(id("s1")) } }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun mastodonMapperReadsEditedAt() {
        assertNotNull(MastodonMapper.post(statusJson(), origin).editedAtEpochMillis)
        assertNull(MastodonMapper.post(statusJson(editedAt = null), origin).editedAtEpochMillis)
    }

    private fun noteJson(
        text: String = "hello",
        visibility: String = "followers",
        localOnly: Boolean = true,
        updatedAt: String? = null,
    ) = JSONObject()
        .put("id", "n1")
        .put("createdAt", "2026-10-10T00:00:00.000Z")
        .put("updatedAt", updatedAt ?: JSONObject.NULL)
        .put("text", text)
        .put("cw", "old cw")
        .put("visibility", visibility)
        .put("localOnly", localOnly)
        .put("reactionAcceptance", "likeOnly")
        .put("replyId", "parent")
        .put("files", JSONArray().put(JSONObject().put("id", "f1").put("type", "image/png").put("url", "https://x/f.png").put("comment", "old alt")))
        .put("user", JSONObject().put("id", "u1").put("username", "u").put("host", JSONObject.NULL))

    @Test
    fun sharkeyEditKeepsVisibilityAndLocalOnlyOfTheStoredNote() = runBlocking {
        server.enqueue(MockResponse().setBody(noteJson().toString()))
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody(noteJson(text = "edited", updatedAt = "2026-10-10T12:00:00.000Z").toString()))

        val edited = misskey().edit(id("n1"), EditPostRequest(text = "edited"))

        assertEquals("/api/notes/show", server.takeRequest().path)
        val editRequest = server.takeRequest()
        assertEquals("/api/notes/edit", editRequest.path)
        val body = JSONObject(editRequest.body.readUtf8())
        assertEquals("n1", body.getString("editId"))
        assertEquals("edited", body.getString("text"))
        assertEquals("followers", body.getString("visibility"))
        assertTrue(body.getBoolean("localOnly"))
        assertEquals("old cw", body.getString("cw"))
        assertEquals("likeOnly", body.getString("reactionAcceptance"))
        assertEquals("parent", body.getString("replyId"))
        assertEquals(listOf("f1"), (0 until body.getJSONArray("fileIds").length()).map { body.getJSONArray("fileIds").getString(it) })
        assertNotNull(edited.editedAtEpochMillis)
    }

    @Test
    fun sharkeyEditChangesWarningAndAltTextThroughTheDriveFile() = runBlocking {
        server.enqueue(MockResponse().setBody(noteJson().toString()))
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody(noteJson(updatedAt = "2026-10-10T12:00:00.000Z").toString()))

        misskey().edit(
            id("n1"),
            EditPostRequest(text = "hello", contentWarning = "new cw", attachments = listOf(EditAttachment("f1", "new alt"))),
        )

        server.takeRequest()
        val update = server.takeRequest()
        assertEquals("/api/drive/files/update", update.path)
        val updateBody = JSONObject(update.body.readUtf8())
        assertEquals("f1", updateBody.getString("fileId"))
        assertEquals("new alt", updateBody.getString("comment"))
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("new cw", body.getString("cw"))
    }

    @Test
    fun sharkeyEditIsRefusedBeforeTheNetworkWhenUnsupported() {
        assertThrows(SourceError.Unsupported::class.java) { runBlocking { misskey(CapabilityStatus.Unsupported).edit(id("n1"), EditPostRequest("x")) } }
        assertThrows(SourceError.Unsupported::class.java) { runBlocking { misskey(CapabilityStatus.Unknown).editSource(id("n1")) } }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun misskeyMapperReadsUpdatedAtAsEdited() {
        assertNotNull(MisskeyMapper.post(noteJson(updatedAt = "2026-10-10T12:00:00.000Z"), origin).editedAtEpochMillis)
        assertNull(MisskeyMapper.post(noteJson(), origin).editedAtEpochMillis)
    }
}
