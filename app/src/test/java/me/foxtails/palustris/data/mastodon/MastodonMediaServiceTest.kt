package me.foxtails.palustris.data.mastodon

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.MediaUploadRequest
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.SourceError
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
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
class MastodonMediaServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var origin: String
    private val pauses = mutableListOf<Long>()
    private var opened = 0

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
    fun v2UploadSendsFileAndDescriptionAndReturnsReadyMedia() = runBlocking {
        server.enqueue(MockResponse().setBody(media("m1", url = "https://cdn.example/m1.jpg")))

        val attachment = service().upload(request(description = "A red kite"))

        val sent = server.takeRequest()
        assertEquals("/api/v2/media", sent.path)
        assertEquals("Bearer token", sent.getHeader("Authorization"))
        val body = sent.body.readUtf8()
        assertTrue(body.contains("name=\"description\""))
        assertTrue(body.contains("A red kite"))
        assertTrue(body.contains("name=\"file\"; filename=\"photo.jpg\""))
        assertEquals("m1", attachment.id)
        assertEquals("https://cdn.example/m1.jpg", attachment.url)
        assertEquals(1, server.requestCount)
        assertTrue(pauses.isEmpty())
    }

    @Test
    fun blankDescriptionIsNotSent() = runBlocking {
        server.enqueue(MockResponse().setBody(media("m1", url = "https://cdn.example/m1.jpg")))

        service().upload(request(description = "  "))

        assertTrue(!server.takeRequest().body.readUtf8().contains("name=\"description\""))
    }

    @Test
    fun acceptedUploadPollsUntilProcessingEnds() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202).setBody(media("m2", url = null)))
        server.enqueue(MockResponse().setResponseCode(206).setBody(media("m2", url = null)))
        server.enqueue(MockResponse().setResponseCode(200).setBody(media("m2", url = "https://cdn.example/m2.mp4")))

        val attachment = service().upload(request())

        assertEquals("/api/v2/media", server.takeRequest().path)
        assertEquals("/api/v1/media/m2", server.takeRequest().path)
        assertEquals("/api/v1/media/m2", server.takeRequest().path)
        assertEquals("https://cdn.example/m2.mp4", attachment.url)
        assertEquals(listOf(1_000L, 2_000L), pauses)
    }

    @Test
    fun failedProcessingStopsPollingWithTheServerError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202).setBody(media("m3", url = null)))
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"error":"cannot process"}"""))

        val failure = assertThrows(me.foxtails.palustris.data.transport.HttpStatusFailure::class.java) {
            runBlocking { service().upload(request()) }
        }

        assertEquals(422, failure.status)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun pollingStopsAfterTheWaitBudget() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202).setBody(media("m4", url = null)))
        repeat(40) { server.enqueue(MockResponse().setResponseCode(206).setBody(media("m4", url = null))) }

        assertThrows(SourceError.ServerError::class.java) {
            runBlocking { service().upload(request()) }
        }

        assertTrue(pauses.sum() >= 60_000L)
        assertTrue(pauses.max() <= 5_000L)
    }

    @Test
    fun missingV2RouteFallsBackToV1WithAFreshStream() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"not found"}"""))
        server.enqueue(MockResponse().setBody(media("m5", url = "https://cdn.example/m5.jpg")))

        val attachment = service().upload(request())

        assertEquals("/api/v2/media", server.takeRequest().path)
        assertEquals("/api/v1/media", server.takeRequest().path)
        assertEquals("m5", attachment.id)
        assertEquals(2, opened)
    }

    @Test
    fun oversizedUploadIsAResourceLimit() {
        server.enqueue(MockResponse().setResponseCode(413).setBody("{}"))

        assertThrows(SourceError.ResourceLimit::class.java) {
            runBlocking { source().uploadMedia(request()) }
        }
    }

    @Test
    fun createSendsMediaIdsAndAllowsEmptyStatus() = runBlocking {
        server.enqueue(MockResponse().setBody(status("created")))
        val attachments = listOf(Attachment(id = "m1"), Attachment(id = "m2"))

        source().create(CreatePostRequest("", attachments = attachments, idempotencyKey = "draft-1-entry-1"))

        val sent = server.takeRequest()
        assertEquals("/api/v1/statuses", sent.path)
        assertEquals("draft-1-entry-1", sent.getHeader("Idempotency-Key"))
        val body = sent.body.readUtf8()
        assertTrue(body.contains("media_ids%5B%5D=m1"))
        assertTrue(body.contains("media_ids%5B%5D=m2"))
        assertTrue(body.contains("status="))
    }

    @Test
    fun createWithoutAKeyOmitsTheIdempotencyHeader() = runBlocking {
        server.enqueue(MockResponse().setBody(status("created")))

        source().create(CreatePostRequest("hello"))

        assertNull(server.takeRequest().getHeader("Idempotency-Key"))
    }

    @Test
    fun quoteWithMediaIsStillRejectedBeforeAnyRequest() {
        val quote = me.foxtails.palustris.domain.EntityId(origin, "quoted")

        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking {
                source().create(CreatePostRequest("hi", quoteOf = quote, attachments = listOf(Attachment(id = "m1"))))
            }
        }
        assertEquals(0, server.requestCount)
    }

    private fun service() = MastodonMediaService(origin, "token", mastodonTestClient()) { pauses += it }

    private fun source() = MastodonSource(
        origin = origin,
        token = "token",
        api = mastodonTestClient(),
        accountId = AccountId(Connection(origin, Protocol.MASTODON), "local-user"),
    )

    private fun request(description: String? = null) = MediaUploadRequest(
        open = {
            opened++
            ByteArrayInputStream("image-bytes".toByteArray())
        },
        mimeType = "image/jpeg",
        fileName = "photo.jpg",
        description = description,
        sensitive = false,
    )

    private fun media(id: String, url: String?) = JSONObject()
        .put("id", id)
        .put("type", "image")
        .put("url", url ?: JSONObject.NULL)
        .toString()

    private fun status(id: String) = JSONObject()
        .put("id", id)
        .put("content", "")
        .put("created_at", "2026-10-09T00:00:00.000Z")
        .put("visibility", "public")
        .put(
            "account",
            JSONObject().put("id", "local-user").put("username", "me").put("acct", "me").put("display_name", "Me")
                .put("url", "$origin/@me").put("avatar", "$origin/a.png"),
        )
        .toString()
}
