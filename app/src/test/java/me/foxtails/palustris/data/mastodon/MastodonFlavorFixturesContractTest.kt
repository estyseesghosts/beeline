package me.foxtails.palustris.data.mastodon

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.MediaUploadRequest
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Timeline
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Slice 8 flavor contract: the representative pleroma/pixelfed fixtures page, notify,
 * and upload through the Mastodon adapter, and the existing mastodon/akkoma fixtures
 * still page after the Link relax. Mastodon itself is covered by
 * [MastodonSourceContractTest]; Akkoma reactions and probe shapes stay in their
 * existing tests and are not duplicated here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonFlavorFixturesContractTest {
    private lateinit var server: MockWebServer
    private val origin get() = server.url("/").toString().removeSuffix("/")

    @Before
    fun startServer() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    private fun load(path: String): String = checkNotNull(
        javaClass.classLoader.getResource(path),
    ) { "missing fixture $path" }.readText()

    /** Rewrites a representative fixture Link header to the test server. Only the path and query shape are significant. */
    private fun fixtureLink(path: String, fixtureOrigin: String): String =
        load(path).lines().first { it.trimStart().startsWith("<") }.replace(fixtureOrigin, origin)

    private fun viewer() = AccountId(Connection(origin, Protocol.MASTODON), "viewer")

    private fun source() = MastodonSource(
        origin = origin,
        token = "token",
        api = mastodonTestClient(),
        accountId = viewer(),
    )

    @Test
    fun pleromaHomeTimelinePagesThroughRepresentativeLink() = runBlocking {
        server.enqueue(MockResponse().setBody(load("fixtures/pleroma/home.json"))
            .addHeader("Link", fixtureLink("fixtures/pleroma/link.txt", "https://pleroma.example")))
        server.enqueue(MockResponse().setBody(JSONArray().toString()))
        // One source owns the page flow: the cursor binds to its instance, so a fresh
        // source would reject the continuation as foreign.
        val source = source()

        val first = source.timeline(Timeline.Home)

        assertEquals(listOf("pleroma-home-newest", "pleroma-home-older"), first.items.map { it.id.value })
        assertNotNull(first.nextCursor)
        source.timeline(Timeline.Home, first.nextCursor)
        server.takeRequest()
        val continuation = server.takeRequest()
        assertTrue(continuation.path.orEmpty().contains("max_id=pleroma-home-older"))
        assertTrue(continuation.path.orEmpty().contains("limit=20"))
        assertTrue(continuation.path.orEmpty().contains("only_media=false"))
    }

    @Test
    fun pixelfedHomeTimelinePagesThroughRepresentativeLink() = runBlocking {
        server.enqueue(MockResponse().setBody(load("fixtures/pixelfed/home.json"))
            .addHeader("Link", fixtureLink("fixtures/pixelfed/link.txt", "https://pixelfed.example")))
        server.enqueue(MockResponse().setBody(JSONArray().toString()))
        // One source owns the page flow: the cursor binds to its instance, so a fresh
        // source would reject the continuation as foreign.
        val source = source()

        val first = source.timeline(Timeline.Home)

        assertEquals(listOf("pixelfed-home-newest", "pixelfed-home-older"), first.items.map { it.id.value })
        // The newest photo post carries its representative attachment.
        assertEquals("https://pixelfed.example/storage/attachments/photo.jpg", first.items.first().attachments.single().url)
        assertNotNull(first.nextCursor)
        source.timeline(Timeline.Home, first.nextCursor)
        server.takeRequest()
        val continuation = server.takeRequest()
        assertTrue(continuation.path.orEmpty().contains("max_id=pixelfed-home-older"))
        assertTrue(continuation.path.orEmpty().contains("limit=10"))
        assertTrue(continuation.path.orEmpty().contains("only_media=false"))
    }

    @Test
    fun mastodonAndAkkomaFixturesStillPageAfterTheRelax() = runBlocking {
        val mastodonBody = "[${load("fixtures/mastodon/standard-status-custom-emojis.json")}]"
        server.enqueue(MockResponse().setBody(mastodonBody)
            .addHeader("Link", "<$origin/api/v1/timelines/home?max_id=111222333444555777>; rel=\"next\""))
        val mastodonPage = source().timeline(Timeline.Home)
        assertEquals(listOf("111222333444555777"), mastodonPage.items.map { it.id.value })
        assertNotNull(mastodonPage.nextCursor)

        val akkomaBody = "[${load("fixtures/akkoma/status-emoji-reactions.json")}]"
        server.enqueue(MockResponse().setBody(akkomaBody)
            .addHeader("Link", "<$origin/api/v1/timelines/home?max_id=AkkomaStatus001>; rel=\"next\""))
        val akkomaPage = source().timeline(Timeline.Home)
        assertEquals(listOf("AkkomaStatus001"), akkomaPage.items.map { it.id.value })
        assertNotNull(akkomaPage.nextCursor)
    }

    @Test
    fun pleromaAndPixelfedInstancesIdentifyTheirFlavor() {
        val pleroma = JSONObject(load("fixtures/pleroma/instance.json"))
        assertTrue(pleroma.getString("version").contains("(compatible; Pleroma"))
        assertTrue(pleroma.getJSONObject("pleroma").getJSONObject("metadata")
            .getJSONArray("features").let { features ->
                (0 until features.length()).any { features.getString(it) == "pleroma_emoji_reactions" }
            })

        val pixelfed = JSONObject(load("fixtures/pixelfed/instance.json"))
        assertTrue(pixelfed.getString("version").contains("(compatible; Pixelfed"))
    }

    @Test
    fun pleromaAndPixelfedNotificationsParse() {
        val pleromaItems = JSONArray(load("fixtures/pleroma/notifications.json"))
        val pleroma = (0 until pleromaItems.length()).map {
            MastodonNotificationMapper.notification(pleromaItems.getJSONObject(it), origin, viewer())
        }
        assertEquals(listOf("pleroma-notification-1", "pleroma-notification-2"), pleroma.map { it.id.value })
        assertEquals(listOf("mention", "follow"), pleroma.map { it.rawType })

        val pixelfedItems = JSONArray(load("fixtures/pixelfed/notifications.json"))
        val pixelfed = (0 until pixelfedItems.length()).map {
            MastodonNotificationMapper.notification(pixelfedItems.getJSONObject(it), origin, viewer())
        }
        assertEquals(listOf("pixelfed-notification-1", "pixelfed-notification-2"), pixelfed.map { it.id.value })
        assertEquals(listOf("mention", "follow"), pixelfed.map { it.rawType })
    }

    @Test
    fun pixelfedMediaUploadParsesRepresentativeAttachment() = runBlocking {
        server.enqueue(MockResponse().setBody(load("fixtures/pixelfed/media.json")))
        val request = MediaUploadRequest({ ByteArrayInputStream("bytes".toByteArray()) }, "image/jpeg", "upload", null, false)

        val attachment = source().uploadMedia(request)

        assertEquals("pixelfed-media-7", attachment.id)
        assertEquals("https://pixelfed.example/storage/attachments/photo.jpg", attachment.url)
        assertEquals("/api/v2/media", server.takeRequest().path)
    }
}
