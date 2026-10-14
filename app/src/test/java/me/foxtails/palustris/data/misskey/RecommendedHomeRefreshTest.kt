package me.foxtails.palustris.data.misskey

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.RecommendationCapability
import me.foxtails.palustris.domain.RecommendationFlavor
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Timeline
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Slice 3 refresh plumbing to the DVD endpoint. An explicit Home refresh re-ranks from offset 0
 * with `refresh=true`; cursor paging continues the offset and never re-ranks. The fake server is
 * a canned OkHttp client (no network); fresh seeded capabilities skip the probe, so the only
 * network call in each test is the feed itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecommendedHomeRefreshTest {
    private val dvdOrigin = "https://dvd.chat"
    private val user = """{"id":"user-a","username":"alice","name":"Alice"}"""

    private class RefreshCaptureServer {
        val recommendationBodies = mutableListOf<JSONObject>()
        var recommendationsBody = "[]"

        fun api(): MisskeyApi {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                if (request.url.encodedPath != "/api/notes/recommendations") {
                    return@addInterceptor json(chain, 404, """{"error":{"code":"NOT_SUPPORTED"}}""")
                }
                val raw = request.body?.let { body ->
                    okio.Buffer().also { body.writeTo(it) }.readUtf8()
                } ?: "{}"
                recommendationBodies += JSONObject(raw)
                json(chain, 200, recommendationsBody)
            }.build()
            return MisskeyApi(client)
        }

        private fun json(chain: Interceptor.Chain, code: Int, body: String): Response =
            Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(code).message("Status").body(body.toResponseBody("application/json".toMediaType())).build()
    }

    private fun note(id: String): JSONObject =
        JSONObject("""{"id":"$id","createdAt":"2026-09-06T10:00:00Z","user":$user,"text":"Hello $id","visibility":"home"}""")

    private fun dvdSource(server: RefreshCaptureServer): MisskeySource {
        val capabilities = ServerCapabilities(
            timelines = setOf(Timeline.Home, Timeline.Social),
            recommendations = RecommendationCapability(RecommendationFlavor.DVD_SHARKEY, CapabilityStatus.Supported, dvdOrigin),
            capabilitiesLastUpdated = System.currentTimeMillis(),
        )
        val account = AccountId(Connection(dvdOrigin, Protocol.MISSKEY), "user-a")
        return MisskeySource(
            dvdOrigin,
            "test-token",
            server.api(),
            initialCapabilities = capabilities,
            accountId = account,
            capabilityCache = CapabilityCache(),
        )
    }

    @Test fun refreshFeedReRanksRecommendedFromZero() = runBlocking {
        val server = RefreshCaptureServer()
        server.recommendationsBody = JSONArray(listOf(note("r1"), note("r2"))).toString()

        val page = dvdSource(server).refreshFeed(FeedSource.Timeline(Timeline.Recommended))

        assertEquals(listOf("r1", "r2"), page.items.map { it.id.value })
        assertEquals("2", page.nextCursor)
        val body = server.recommendationBodies.single()
        assertEquals(0, body.getInt("offset"))
        assertEquals(true, body.getBoolean("refresh"))
        assertFalse(body.has("untilId"))
    }

    @Test fun timelinePagingNeverSendsTheRefreshFlag() = runBlocking {
        val server = RefreshCaptureServer()
        server.recommendationsBody = JSONArray((30 until 60).map { note("n$it") }).toString()

        val page = dvdSource(server).timeline(Timeline.Recommended, "30")

        assertEquals(30, page.items.size)
        assertEquals("60", page.nextCursor)
        val body = server.recommendationBodies.single()
        assertEquals(30, body.getInt("offset"))
        assertFalse(body.has("refresh"))
        assertFalse(body.has("untilId"))
    }
}
