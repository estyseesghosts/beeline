package me.foxtails.palustris.data.mastodon

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostingCapabilities
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Timeline
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `content_type` gating for `POST /api/v1/statuses`. The field goes out only when the server
 * lists `postFormats` and the request carries a format from that list; every other combination
 * omits it entirely.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonStatusContentTypeTest {
    @Test
    fun pleromaFormatsSendTheChosenContentType(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(statusBody()))
            val source = source(server, PostingCapabilities(postFormats = listOf("text/plain", "text/markdown")))

            source.create(CreatePostRequest(text = "hi", contentType = "text/markdown"))

            assertTrue(server.takeRequest().body.readUtf8().contains("content_type=text%2Fmarkdown"))
        }
    }

    @Test
    fun mastodonWithoutFormatsOmitsContentType(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(statusBody()))
            val source = source(server, PostingCapabilities(postFormats = null))

            source.create(CreatePostRequest(text = "hi", contentType = "text/markdown"))

            assertFalse(server.takeRequest().body.readUtf8().contains("content_type"))
        }
    }

    @Test
    fun aFormatTheServerDidNotListIsOmitted(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(statusBody()))
            val source = source(server, PostingCapabilities(postFormats = listOf("text/plain")))

            source.create(CreatePostRequest(text = "hi", contentType = "text/markdown"))

            assertFalse(server.takeRequest().body.readUtf8().contains("content_type"))
        }
    }

    @Test
    fun noChoiceOmitsContentTypeEvenWhereFormatsExist(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(statusBody()))
            val source = source(server, PostingCapabilities(postFormats = listOf("text/plain", "text/markdown")))

            source.create(CreatePostRequest(text = "hi"))

            assertFalse(server.takeRequest().body.readUtf8().contains("content_type"))
        }
    }

    private fun source(server: MockWebServer, posting: PostingCapabilities): MastodonSource {
        val origin = server.url("/").toString().removeSuffix("/")
        return MastodonSource(
            origin = origin,
            token = "content-type-token",
            api = mastodonTestClient(),
            accountId = AccountId(Connection(origin, Protocol.MASTODON), "content-type-user"),
            initialCapabilities = ServerCapabilities(
                timelines = setOf(Timeline.Home),
                audiences = setOf(Audience.Public),
                actions = setOf(PostAction.Reply),
                posting = posting,
            ),
        )
    }

    private fun statusBody(): String = JSONObject()
        .put("id", "s1")
        .put("created_at", "2026-10-01T00:00:00Z")
        .put("visibility", "public")
        .put("content", "<p>hi</p>")
        .put("account", JSONObject().put("id", "u1").put("username", "u").put("acct", "u"))
        .toString()
}
