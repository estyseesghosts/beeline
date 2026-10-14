package me.foxtails.palustris.data.mastodon

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
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
 * Timeline item tolerance: one malformed Pleroma status must not fail the page.
 * Pagination validation stays strict; only item mapping skips bad entries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonTimelineToleranceTest {
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

    private fun source() = MastodonSource(
        origin = origin,
        token = "token",
        api = mastodonTestClient(),
        accountId = AccountId(Connection(origin, Protocol.MASTODON), "local-user"),
    )

    @Test
    fun homePageWithPoisonEntryReturnsGoodItemsInOrder() = runBlocking {
        val good = JSONArray(load("fixtures/pleroma/home.json"))
        val body = JSONArray()
            .put(good.getJSONObject(0))
            .put(JSONObject().put("id", "poison-no-account").put("content", "<p>poison</p>"))
            .put(good.getJSONObject(1))
            .toString()
        server.enqueue(
            MockResponse().setBody(body).addHeader(
                "Link",
                "<$origin/api/v1/timelines/home?max_id=pleroma-home-older>; rel=\"next\"",
            ),
        )

        val page = source().timeline(Timeline.Home)

        assertEquals(listOf("pleroma-home-newest", "pleroma-home-older"), page.items.map { it.id.value })
        assertNotNull(page.nextCursor)
    }

    @Test
    fun homePageWithNullEntrySkipsNullAndKeepsGoodItems() = runBlocking {
        val good = JSONArray(load("fixtures/pleroma/home.json"))
        val body = JSONArray()
            .put(good.getJSONObject(0))
            .put(JSONObject.NULL)
            .put(good.getJSONObject(1))
            .toString()
        server.enqueue(MockResponse().setBody(body))

        val page = source().timeline(Timeline.Home)

        assertEquals(listOf("pleroma-home-newest", "pleroma-home-older"), page.items.map { it.id.value })
    }

    @Test
    fun homePageWithAllPoisonEntriesReturnsEmptyWithoutThrowing() = runBlocking {
        val body = JSONArray()
            .put(JSONObject().put("id", "poison-no-account").put("content", "<p>poison</p>"))
            .put(JSONObject.NULL)
            .toString()
        server.enqueue(MockResponse().setBody(body))

        val page = source().timeline(Timeline.Home)

        assertTrue(page.items.isEmpty())
    }
}
