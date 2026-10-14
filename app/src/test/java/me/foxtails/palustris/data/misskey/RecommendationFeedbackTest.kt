package me.foxtails.palustris.data.misskey

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.RecommendationCapability
import me.foxtails.palustris.domain.RecommendationFlavor
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SourceError
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Slice 4 contract: DVD dwell and not-interested feedback. The fake server is a canned OkHttp
 * client (no network). Dwell and not-interested are best-effort: permission failures map to
 * Denied without demoting the feature, and every other failure is silent with one attempt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecommendationFeedbackTest {
    private val dvdOrigin = "https://dvd.chat"
    private val connection = Connection(dvdOrigin, Protocol.MISSKEY)
    private val account = AccountId(connection, "user-a")

    private class FakeFeedbackServer {
        val calls = mutableListOf<Pair<String, JSONObject>>()
        var dwellStatus = 200
        var dwellBody = "{}"
        var notInterestedStatus = 200
        var notInterestedBody = "{}"

        fun api(): MisskeyApi {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                val raw = request.body?.let { body -> okio.Buffer().also { body.writeTo(it) }.readUtf8() } ?: "{}"
                val payload = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
                calls.add(request.url.encodedPath to payload)
                when (request.url.encodedPath) {
                    "/api/notes/recommendations/dwell" -> json(chain, dwellStatus, dwellBody)
                    "/api/notes/recommendations/not-interested" -> json(chain, notInterestedStatus, notInterestedBody)
                    else -> json(chain, 404, """{"error":{"code":"NOT_SUPPORTED"}}""")
                }
            }.build()
            return MisskeyApi(client)
        }

        private fun json(chain: Interceptor.Chain, code: Int, body: String): Response =
            Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(code).message("Status").body(body.toResponseBody("application/json".toMediaType())).build()

        fun dwellBodies(): List<JSONObject> =
            calls.filter { it.first == "/api/notes/recommendations/dwell" }.map { it.second }

        fun notInterestedBodies(): List<JSONObject> =
            calls.filter { it.first == "/api/notes/recommendations/not-interested" }.map { it.second }
    }

    private fun feedback(
        server: FakeFeedbackServer,
        origin: String = dvdOrigin,
        token: String? = "test-token",
        accountId: AccountId? = account,
    ): MisskeyRecommendationFeedbackService =
        MisskeyRecommendationFeedbackService(origin, token, server.api(), accountId, MISSKEY_MAX_RESPONSE_BYTES)

    private fun id(value: String, origin: String = dvdOrigin) = EntityId(origin, value)

    @Test fun dwellSendsIdsWithMillisInChunksOfFifty() = runBlocking {
        val server = FakeFeedbackServer()
        val entries = (0 until 55).associate { id("n$it") to (1_000L + it) }

        feedback(server).reportDwell(entries)

        val bodies = server.dwellBodies()
        assertEquals(2, bodies.size)
        val notes = bodies.flatMap { body ->
            assertEquals("test-token", body.getString("i"))
            (0 until body.getJSONArray("notes").length()).map { body.getJSONArray("notes").getJSONObject(it) }
        }
        assertEquals(55, notes.size)
        assertEquals(50, bodies[0].getJSONArray("notes").length())
        assertEquals(5, bodies[1].getJSONArray("notes").length())
        val first = bodies[0].getJSONArray("notes").getJSONObject(0)
        assertEquals("n0", first.getString("id"))
        assertEquals(1_000L, first.getLong("dwell"))
    }

    @Test fun dwellCapsEachValueAtOneHour() = runBlocking {
        val server = FakeFeedbackServer()

        feedback(server).reportDwell(mapOf(id("n1") to 5_000_000L))

        assertEquals(3_600_000L, server.dwellBodies().single().getJSONArray("notes").getJSONObject(0).getLong("dwell"))
    }

    @Test fun dwellSkipsEmptyAndNonPositiveEntriesWithoutNetwork() = runBlocking {
        val server = FakeFeedbackServer()

        feedback(server).reportDwell(emptyMap())
        feedback(server).reportDwell(mapOf(id("n1") to 0L, id("n2") to -5L))

        assertTrue(server.calls.isEmpty())
    }

    @Test fun dwellDeniesAnonymousBeforeNetwork() {
        val server = FakeFeedbackServer()
        assertThrows(SourceError.AccessDenied::class.java) {
            runBlocking { feedback(server, token = null, accountId = null).reportDwell(mapOf(id("n1") to 1_000L)) }
        }
        assertTrue(server.calls.isEmpty())
    }

    @Test fun dwellRejectsOffDvdHostBeforeNetwork() {
        val server = FakeFeedbackServer()
        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { feedback(server, origin = "https://misskey.io").reportDwell(mapOf(id("n1") to 1_000L)) }
        }
        assertTrue(server.calls.isEmpty())
    }

    @Test fun dwellStaysSilentOnServerFailureWithOneAttempt() = runBlocking {
        val server = FakeFeedbackServer().apply {
            dwellStatus = 500
            dwellBody = """{"error":{"code":"INTERNAL_ERROR"}}"""
        }

        feedback(server).reportDwell(mapOf(id("n1") to 1_000L))

        assertEquals(1, server.dwellBodies().size)
    }

    @Test fun notInterestedSendsNoteId() = runBlocking {
        val server = FakeFeedbackServer()

        feedback(server).markNotInterested(id("n1"))

        val body = server.notInterestedBodies().single()
        assertEquals("test-token", body.getString("i"))
        assertEquals("n1", body.getString("noteId"))
    }

    @Test fun notInterestedMapsPermissionFailureToDenied() {
        val server = FakeFeedbackServer().apply {
            notInterestedStatus = 403
            notInterestedBody = """{"error":{"code":"PERMISSION_DENIED"}}"""
        }
        assertThrows(SourceError.AccessDenied::class.java) {
            runBlocking { feedback(server).markNotInterested(id("n1")) }
        }
        assertEquals(1, server.notInterestedBodies().size)
    }

    @Test fun notInterestedStaysSilentOnServerFailure() = runBlocking {
        val server = FakeFeedbackServer().apply {
            notInterestedStatus = 500
            notInterestedBody = """{"error":{"code":"INTERNAL_ERROR"}}"""
        }

        feedback(server).markNotInterested(id("n1"))

        assertEquals(1, server.notInterestedBodies().size)
    }

    @Test fun notInterestedDeniesAnonymousBeforeNetwork() {
        val server = FakeFeedbackServer()
        assertThrows(SourceError.AccessDenied::class.java) {
            runBlocking { feedback(server, token = null, accountId = null).markNotInterested(id("n1")) }
        }
        assertTrue(server.calls.isEmpty())
    }

    @Test fun notInterestedRejectsForeignNoteBeforeNetwork() {
        val server = FakeFeedbackServer()
        assertThrows(SourceError.ForeignOrigin::class.java) {
            runBlocking { feedback(server).markNotInterested(id("n1", origin = "https://misskey.io")) }
        }
        assertTrue(server.calls.isEmpty())
    }

    @Test fun misskeySourceDeniedFeedbackKeepsTheFeatureSupported() = runBlocking {
        val server = FakeFeedbackServer().apply {
            notInterestedStatus = 403
            notInterestedBody = """{"error":{"code":"PERMISSION_DENIED"}}"""
            dwellStatus = 500
            dwellBody = """{"error":{"code":"INTERNAL_ERROR"}}"""
        }
        val capabilities = ServerCapabilities(
            recommendations = RecommendationCapability(RecommendationFlavor.DVD_SHARKEY, CapabilityStatus.Supported, dvdOrigin),
        )
        val source = MisskeySource(dvdOrigin, "test-token", server.api(), initialCapabilities = capabilities, accountId = account, capabilityCache = CapabilityCache())

        assertThrows(SourceError.AccessDenied::class.java) {
            runBlocking { source.markRecommendationNotInterested(id("n1")) }
        }
        // A denied feedback call never demotes the feed: no Unsupported, no snapshot churn.
        assertEquals(CapabilityStatus.Supported, source.capabilities.recommendations?.status)
        // Other failures stay silent with one attempt.
        source.reportRecommendationDwell(mapOf(id("n1") to 1_000L))
        assertEquals(1, server.dwellBodies().size)
        assertEquals(CapabilityStatus.Supported, source.capabilities.recommendations?.status)
    }

    @Test fun feedServiceRetainsExplanationsForTheReasonDialog() = runBlocking {
        val user = """{"id":"user-a","username":"alice","name":"Alice"}"""
        val factors = """{"score":1.5,"topic":"art","mode":"score","factors":[{"label":"recency","contribution":0.5}]}"""
        val note = """{"id":"r1","createdAt":"2026-09-06T10:00:00Z","user":$user,"text":"Hello","visibility":"home","_recommendationFactors_":$factors}"""
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("Status").body("[$note]".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val service = MisskeyDvdSharkeyRecommendationService(dvdOrigin, "test-token", MisskeyApi(client), account, MISSKEY_MAX_RESPONSE_BYTES) { "en" }

        assertEquals(null, service.explanationFor(id("r1")))
        val page = service.page(null)
        assertEquals(listOf("r1"), page.posts.items.map { it.id.value })

        val explanation = service.explanationFor(id("r1"))
        assertEquals(1.5, explanation?.score ?: Double.NaN, 0.0)
        assertEquals("art", explanation?.topic)
        assertEquals("score", explanation?.mode)
        assertEquals("recency", explanation?.factors?.single()?.label)
        assertEquals(null, service.explanationFor(id("missing")))
    }
}
