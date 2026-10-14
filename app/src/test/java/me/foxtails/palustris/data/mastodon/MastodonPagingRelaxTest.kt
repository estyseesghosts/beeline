package me.foxtails.palustris.data.mastodon

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Slice 8 paging relax: a `Link` URL may carry extra query params beside the cursor.
 * Pleroma and Pixelfed echo unrelated timeline params (see the representative
 * `link.txt` fixtures), so the validator ignores unknown extras instead of rejecting
 * the page with `Unsupported("pagination.link")`. Origin, credentials, fragment, path,
 * cursor singleness, page size, and locality checks stay strict.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonPagingRelaxTest {
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

    private fun source() = MastodonSource(
        origin = origin,
        token = "token",
        api = mastodonTestClient(),
        accountId = AccountId(Connection(origin, Protocol.MASTODON), "local-user"),
    )

    @Test
    fun representativePleromaLinkParsesAndReplaysExtrasInOrder() = runBlocking {
        server.enqueue(MockResponse().setBody(load("fixtures/pleroma/home.json"))
            .addHeader("Link", fixtureLink("fixtures/pleroma/link.txt", "https://pleroma.example")))
        server.enqueue(MockResponse().setBody(JSONArray().toString()))
        val source = source()

        val first = source.timeline(Timeline.Home)

        assertEquals(listOf("pleroma-home-newest", "pleroma-home-older"), first.items.map { it.id.value })
        assertNotNull(first.nextCursor)
        source.timeline(Timeline.Home, first.nextCursor)
        // The extras ride along on the continuation in transport order.
        server.takeRequest()
        assertEquals(
            "/api/v1/timelines/home?max_id=pleroma-home-older&limit=20&only_media=false",
            server.takeRequest().path,
        )
    }

    @Test
    fun representativePixelfedLinkParsesAndReplaysExtrasInOrder() = runBlocking {
        server.enqueue(MockResponse().setBody(load("fixtures/pixelfed/home.json"))
            .addHeader("Link", fixtureLink("fixtures/pixelfed/link.txt", "https://pixelfed.example")))
        server.enqueue(MockResponse().setBody(JSONArray().toString()))
        val source = source()

        val first = source.timeline(Timeline.Home)

        assertEquals(listOf("pixelfed-home-newest", "pixelfed-home-older"), first.items.map { it.id.value })
        assertNotNull(first.nextCursor)
        source.timeline(Timeline.Home, first.nextCursor)
        server.takeRequest()
        assertEquals(
            "/api/v1/timelines/home?max_id=pixelfed-home-older&limit=10&only_media=false",
            server.takeRequest().path,
        )
    }

    @Test
    fun unknownExtraParamParsesWhileStrictChecksStay() = runBlocking {
        server.enqueue(MockResponse().setBody(JSONArray().toString()).addHeader(
            "Link", "<$origin/api/v1/timelines/home?max_id=x&unknown=y>; rel=\"next\"",
        ))
        val page = source().timeline(Timeline.Home)

        assertNotNull(page.nextCursor)

        val badLinks = listOf(
            "<https://other.example/api/v1/timelines/home?max_id=x>; rel=\"next\"",
            "<${origin.replace("http://", "http://user:pass@")}/api/v1/timelines/home?max_id=x>; rel=\"next\"",
            "<$origin/api/v1/timelines/home?max_id=x#fragment>; rel=\"next\"",
            "<$origin/api/v1/elsewhere?max_id=x>; rel=\"next\"",
            "<$origin/api/v1/timelines/home?max_id=x&max_id=y>; rel=\"next\"",
            "<$origin/api/v1/timelines/home>; rel=\"next\"",
        )
        badLinks.forEach { link ->
            server.enqueue(MockResponse().setBody(JSONArray().toString()).addHeader("Link", link))
            val error = assertThrows(SourceError.Unsupported::class.java) {
                runBlocking { source().timeline(Timeline.Home) }
            }
            assertEquals("pagination.link", error.feature)
        }
        assertEquals(badLinks.size + 1, server.requestCount)
    }
}
