package me.foxtails.palustris.data.misskey

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.MediaUploadRequest
import me.foxtails.palustris.domain.SourceError
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class MisskeyMediaTest {
    private lateinit var server: MockWebServer
    private lateinit var origin: String

    @Before
    fun start() {
        server = MockWebServer().also { it.start() }
        origin = server.url("/").toString().removeSuffix("/")
    }

    @After
    fun stop() {
        server.shutdown()
    }

    @Test
    fun uploadSendsTokenAltTextAndSensitivityAsFormFields() = runBlocking {
        server.enqueue(MockResponse().setBody(driveFile("file-1")))

        val attachment = source().uploadMedia(request(description = "A red kite", sensitive = true))

        val sent = server.takeRequest()
        assertEquals("/api/drive/files/create", sent.path)
        assertNull(sent.getHeader("Authorization"))
        val body = sent.body.readUtf8()
        assertTrue(body.contains("name=\"i\""))
        assertTrue(body.contains("test-token"))
        assertTrue(body.contains("name=\"comment\""))
        assertTrue(body.contains("A red kite"))
        assertEquals("true", fieldValue(body, "isSensitive"))
        assertEquals("true", fieldValue(body, "force"))
        assertTrue(body.contains("name=\"file\"; filename=\"photo.jpg\""))
        assertEquals("file-1", attachment.id)
        assertEquals("https://cdn.example/file-1.jpg", attachment.url)
    }

    @Test
    fun uploadWithoutAltTextOmitsTheCommentField() = runBlocking {
        server.enqueue(MockResponse().setBody(driveFile("file-2")))

        source().uploadMedia(request(description = null))

        val body = server.takeRequest().body.readUtf8()
        assertFalse(body.contains("name=\"comment\""))
        assertEquals("false", fieldValue(body, "isSensitive"))
    }

    @Test
    fun driveErrorCodesMapToDistinctSourceErrors() = runBlocking {
        val cases = listOf(
            "NO_FREE_SPACE" to SourceError.ResourceLimit("media.drive-full"),
            "MAX_FILE_SIZE_EXCEEDED" to SourceError.ResourceLimit("media.file-size"),
            "UNALLOWED_FILE_TYPE" to SourceError.Unsupported("media.file-type"),
            "INAPPROPRIATE" to SourceError.Unsupported("media.inappropriate"),
            "commentTooLong" to SourceError.Unsupported("media.alt-text-length"),
            "PERMISSION_DENIED" to SourceError.AccessDenied("media.upload"),
        )
        cases.forEach { (code, expected) ->
            server.enqueue(
                MockResponse().setResponseCode(400)
                    .setBody(JSONObject().put("error", JSONObject().put("code", code)).toString()),
            )
            val failure = assertThrows(SourceError::class.java) {
                runBlocking { source().uploadMedia(request()) }
            }
            assertEquals(code, expected, failure)
        }
    }

    @Test
    fun payloadTooLargeStatusIsAResourceLimit() {
        server.enqueue(MockResponse().setResponseCode(413).setBody("{}"))

        val failure = assertThrows(SourceError::class.java) {
            runBlocking { source().uploadMedia(request()) }
        }

        assertEquals(SourceError.ResourceLimit("media.file-size"), failure)
    }

    @Test
    fun createSendsFileIdsAndOmitsBlankText() = runBlocking {
        server.enqueue(MockResponse().setBody(note("created")))
        val attachments = listOf(Attachment(id = "file-1"), Attachment(id = "file-2"))

        source().create(CreatePostRequest("  ", attachments = attachments))

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(listOf("file-1", "file-2"), body.getJSONArray("fileIds").let { ids -> (0 until ids.length()).map(ids::getString) })
        assertFalse(body.has("text"))
    }

    @Test
    fun createKeepsTextBesideFiles() = runBlocking {
        server.enqueue(MockResponse().setBody(note("created")))

        source().create(CreatePostRequest("look", attachments = listOf(Attachment(id = "file-1"))))

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("look", body.getString("text"))
    }

    @Test
    fun createWithoutFilesStillSendsText() = runBlocking {
        server.enqueue(MockResponse().setBody(note("created")))

        source().create(CreatePostRequest("hello"))

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("hello", body.getString("text"))
        assertFalse(body.has("fileIds"))
    }

    /** Reads the text of one multipart form field. */
    private fun fieldValue(body: String, name: String): String? =
        Regex("name=\"$name\"\r\n(?:[A-Za-z-]+: [^\r\n]*\r\n)*\r\n([^\r\n]*)").find(body)?.groupValues?.get(1)

    private fun source() = MisskeySource(origin, "test-token", MisskeyApi(), capabilityCache = CapabilityCache())

    private fun request(description: String? = null, sensitive: Boolean = false) = MediaUploadRequest(
        open = { ByteArrayInputStream("image-bytes".toByteArray()) },
        mimeType = "image/jpeg",
        fileName = "photo.jpg",
        description = description,
        sensitive = sensitive,
    )

    private fun driveFile(id: String) = JSONObject()
        .put("id", id)
        .put("type", "image/jpeg")
        .put("url", "https://cdn.example/$id.jpg")
        .put("thumbnailUrl", "https://cdn.example/$id-thumb.jpg")
        .put("isSensitive", false)
        .toString()

    private fun note(id: String) = JSONObject()
        .put(
            "createdNote",
            JSONObject()
                .put("id", id)
                .put("createdAt", "2026-10-09T00:00:00.000Z")
                .put("text", "")
                .put("visibility", "public")
                .put("user", JSONObject().put("id", "u1").put("username", "me").put("name", "Me")),
        )
        .toString()
}
