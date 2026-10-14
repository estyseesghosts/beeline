package me.foxtails.palustris.data.misskey

import java.io.IOException
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.RecommendationCapability
import me.foxtails.palustris.domain.RecommendationFlavor
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SourceError
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Slice 2 contract: the DVD probe gate and the offset-paged recommendations feed. The fake server
 * is a canned OkHttp client (no network), so the gate can run against the real `dvd.chat` origin.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DvdRecommendationsTest {
    private val dvdOrigin = "https://dvd.chat"
    private val user = """{"id":"user-a","username":"alice","name":"Alice"}"""
    private val requiredEndpoints = listOf(
        "notes/recommendations",
        "notes/recommendations/dwell",
        "notes/recommendations/not-interested",
        "i/recommendation-interests",
    )

    private class FakeDvdServer {
        val calls = mutableListOf<Pair<String, JSONObject>>()
        var iBody: String = """{"policies":{},"recommendationSettings":{"enabled":true}}"""
        var endpointsBody: String = """["notes/recommendations","notes/recommendations/dwell","notes/recommendations/not-interested","i/recommendation-interests"]"""
        var endpointsStatus: Int = 200
        var endpointsThrow: Boolean = false
        var recommendationsBody: String = "[]"

        fun api(): MisskeyApi {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                val raw = request.body?.let { body -> okio.Buffer().also { body.writeTo(it) }.readUtf8() } ?: "{}"
                val payload = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
                calls.add(request.url.encodedPath to payload)
                when (request.url.encodedPath) {
                    "/api/meta" -> json(chain, 200, """{"version":"2026.1.0"}""")
                    "/api/i" -> json(chain, 200, iBody)
                    "/api/endpoints" -> {
                        if (endpointsThrow) throw IOException("unreachable")
                        json(chain, endpointsStatus, endpointsBody)
                    }
                    "/api/notes/recommendations" -> json(chain, 200, recommendationsBody)
                    else -> json(chain, 404, """{"error":{"code":"NOT_SUPPORTED"}}""")
                }
            }.build()
            return MisskeyApi(client)
        }

        private fun json(chain: Interceptor.Chain, code: Int, body: String): Response =
            Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(code).message("Status").body(body.toResponseBody("application/json".toMediaType())).build()

        fun recommendationBodies(): List<JSONObject> =
            calls.filter { it.first == "/api/notes/recommendations" }.map { it.second }
    }

    private fun serverWithEndpoints(endpoints: List<String> = requiredEndpoints): FakeDvdServer =
        FakeDvdServer().apply { endpointsBody = JSONArray(endpoints).toString() }

    private fun probeRecommendations(server: FakeDvdServer, origin: String = dvdOrigin, token: String? = "test-token") = runBlocking {
        MisskeyCapabilityProbe(server.api(), token).probeCapabilities(Connection(origin, Protocol.MISSKEY)).recommendations
    }

    private fun dvdService(
        server: FakeDvdServer,
        origin: String = dvdOrigin,
        token: String? = "test-token",
        accountId: AccountId? = AccountId(Connection(origin, Protocol.MISSKEY), "user-a"),
        lang: String = "en-US",
    ): MisskeyDvdSharkeyRecommendationService =
        MisskeyDvdSharkeyRecommendationService(origin, token, server.api(), accountId, MISSKEY_MAX_RESPONSE_BYTES) { lang }

    private fun note(id: String, factors: JSONObject? = null): JSONObject =
        JSONObject("""{"id":"$id","createdAt":"2026-09-06T10:00:00Z","user":$user,"text":"Hello $id","visibility":"home"}""")
            .apply { if (factors != null) put("_recommendationFactors_", factors) }

    private fun factors(): JSONObject = JSONObject(
        """{"score":1.5,"topic":"art","mode":"score","factors":[{"label":"recency","contribution":0.5,"detail":"x1.2"}],""" +
            """"learned":[{"label":"ranker","weight":0.7,"value":0.9}]}""",
    )

    private fun noteArray(ids: List<String>): String = JSONArray(ids.map { note(it) }).toString()

    @Test fun probeReportsSupportedWithAllEndpointsAndEnabled() = runBlocking {
        val server = serverWithEndpoints()
        val capabilities = MisskeyCapabilityProbe(server.api(), "test-token")
            .probeCapabilities(Connection(dvdOrigin, Protocol.MISSKEY))

        assertEquals(CapabilityStatus.Supported, capabilities.recommendations?.status)
        assertEquals(RecommendationFlavor.DVD_SHARKEY, capabilities.recommendations?.flavor)
        assertEquals(dvdOrigin, capabilities.recommendations?.origin)
        assertFalse(Timeline.Recommended in capabilities.timelines)
    }

    @Test fun probeTreatsAbsentRecommendationSettingsAsEnabled() {
        val server = serverWithEndpoints().apply { iBody = """{"policies":{}}""" }
        assertEquals(CapabilityStatus.Supported, probeRecommendations(server)?.status)
    }

    @Test fun probeDeniesWhenUserDisabledRecommendations() {
        val server = serverWithEndpoints().apply { iBody = """{"policies":{},"recommendationSettings":{"enabled":false}}""" }
        assertEquals(CapabilityStatus.Denied, probeRecommendations(server)?.status)
    }

    @Test fun probeReportsUnsupportedWhenEndpointMissing() {
        val server = serverWithEndpoints(requiredEndpoints.dropLast(1))
        assertEquals(CapabilityStatus.Unsupported, probeRecommendations(server)?.status)
    }

    @Test fun probeReportsUnsupportedOffDvdHost() {
        val server = serverWithEndpoints()
        assertEquals(CapabilityStatus.Unsupported, probeRecommendations(server, origin = "https://misskey.io")?.status)
    }

    @Test fun probeDeniesAnonymousOnDvdHostWithoutFurtherCalls() = runBlocking {
        val server = serverWithEndpoints()
        val recommendations = MisskeyCapabilityProbe(server.api(), null)
            .probeCapabilities(Connection(dvdOrigin, Protocol.MISSKEY)).recommendations

        assertEquals(CapabilityStatus.Denied, recommendations?.status)
        assertEquals(listOf("/api/meta"), server.calls.map { it.first })
    }

    @Test fun probeReportsUnknownWhenEndpointListUnreadable() {
        val server = serverWithEndpoints().apply { endpointsBody = "{}" }
        assertEquals(CapabilityStatus.Unknown, probeRecommendations(server)?.status)
    }

    @Test fun probeReportsTemporarilyUnavailableOnRateLimit() {
        val server = serverWithEndpoints().apply {
            endpointsStatus = 429
            endpointsBody = """{"error":{"code":"RATE_LIMIT_EXCEEDED"}}"""
        }
        assertEquals(CapabilityStatus.TemporarilyUnavailable, probeRecommendations(server)?.status)
    }

    @Test fun probeReportsTemporarilyUnavailableOnNetworkFailure() {
        val server = serverWithEndpoints().apply { endpointsThrow = true }
        assertEquals(CapabilityStatus.TemporarilyUnavailable, probeRecommendations(server)?.status)
    }

    @Test fun hostGateMatchesCaseInsensitivelyAndRejectsGarbage() {
        assertTrue(isDvdSharkeyHost("https://dvd.chat"))
        assertTrue(isDvdSharkeyHost("https://DVD.CHAT"))
        assertFalse(isDvdSharkeyHost("https://misskey.io"))
        assertFalse(isDvdSharkeyHost("not a url"))
    }

    @Test fun firstPageRequestsOffsetZeroWithoutRefreshOrUntilId() = runBlocking {
        val server = FakeDvdServer()
        server.recommendationsBody = noteArray((0 until 30).map { "n$it" })

        val page = dvdService(server).page(null)

        assertEquals(30, page.posts.items.size)
        assertEquals("30", page.posts.nextCursor)
        val body = server.recommendationBodies().single()
        assertEquals(30, body.getInt("limit"))
        assertEquals(0, body.getInt("offset"))
        assertFalse(body.has("refresh"))
        assertFalse(body.has("untilId"))
        assertEquals("en", body.getString("lang"))
        assertEquals(false, body.getBoolean("withSensitive"))
        assertEquals(true, body.getBoolean("withRenotes"))
    }

    @Test fun secondPageSendsDecodedOffset() = runBlocking {
        val server = FakeDvdServer()
        server.recommendationsBody = noteArray((30 until 60).map { "n$it" })

        val page = dvdService(server).page("30")

        assertEquals(30, page.posts.items.size)
        assertEquals("60", page.posts.nextCursor)
        assertEquals(30, server.recommendationBodies().single().getInt("offset"))
    }

    @Test fun refreshReRanksFromZeroWithRefreshFlag() = runBlocking {
        val server = FakeDvdServer()
        server.recommendationsBody = noteArray(listOf("r1"))

        val page = dvdService(server).page(null, refresh = true)

        assertEquals("1", page.posts.nextCursor)
        val body = server.recommendationBodies().single()
        assertEquals(0, body.getInt("offset"))
        assertEquals(true, body.getBoolean("refresh"))
    }

    @Test fun emptyPageEndsPagination() = runBlocking {
        val page = dvdService(FakeDvdServer()).page("60")
        assertTrue(page.posts.items.isEmpty())
        assertNull(page.posts.nextCursor)
    }

    @Test fun anonymousServiceDeniesBeforeNetwork() {
        val server = FakeDvdServer()
        assertThrows(SourceError.AccessDenied::class.java) {
            runBlocking { dvdService(server, token = null, accountId = null).page(null) }
        }
        assertTrue(server.calls.isEmpty())
    }

    @Test fun foreignOriginServiceThrowsUnsupportedBeforeNetwork() {
        val server = FakeDvdServer()
        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { dvdService(server, origin = "https://misskey.io").page(null) }
        }
        assertTrue(server.calls.isEmpty())
    }

    @Test fun foreignCursorRejectedBeforeNetwork() {
        val server = FakeDvdServer()
        val service = dvdService(server)
        for (cursor in listOf("9abcdef123", "-5", "30x")) {
            assertThrows(SourceError.ForeignOrigin::class.java) { runBlocking { service.page(cursor) } }
        }
        assertTrue(server.calls.isEmpty())
    }

    @Test fun malformedNotesSkippedWithExplanationsMapped() = runBlocking {
        val server = FakeDvdServer()
        server.recommendationsBody = JSONArray(listOf(note("good", factors()), JSONObject("""{"text":"broken"}"""), note("plain"))).toString()

        val page = dvdService(server).page(null)

        assertEquals(listOf("good", "plain"), page.posts.items.map { it.id.value })
        assertEquals("2", page.posts.nextCursor)
        assertEquals(2, page.explanations.size)
        val good = page.explanations.entries.first { it.key.value == "good" }.value
        assertEquals(RecommendationFlavor.DVD_SHARKEY, good.flavor)
        assertEquals(1.5, good.score!!, 0.0)
        assertEquals("art", good.topic)
        assertEquals("score", good.mode)
        assertEquals("recency", good.factors.single().label)
        assertEquals(0.5, good.factors.single().contribution!!, 0.0)
        assertEquals("x1.2", good.factors.single().detail)
        assertEquals("ranker", good.learnedRows.single().label)
        val plain = page.explanations.entries.first { it.key.value == "plain" }.value
        assertEquals(RecommendationFlavor.DVD_SHARKEY, plain.flavor)
        assertNull(plain.score)
        assertTrue(plain.factors.isEmpty())
        assertTrue(plain.learnedRows.isEmpty())
    }

    @Test fun langNormalizesDeviceLanguage() {
        assertEquals("zh", normalizeRecommendationLang("zh-CN"))
        assertEquals("en", normalizeRecommendationLang("en-US"))
        assertEquals("fr", normalizeRecommendationLang("fr"))
        assertEquals("en", normalizeRecommendationLang(""))
    }

    @Test fun serviceSendsNormalizedDeviceLanguage() = runBlocking {
        val server = FakeDvdServer()
        server.recommendationsBody = noteArray(listOf("r1"))

        dvdService(server, lang = "zh-CN").page(null)

        assertEquals("zh", server.recommendationBodies().single().getString("lang"))
    }

    @Test fun misskeySourceRoutesRecommendedThroughDvdService() = runBlocking {
        val server = FakeDvdServer()
        server.recommendationsBody = noteArray(listOf("r1", "r2"))
        val account = AccountId(Connection(dvdOrigin, Protocol.MISSKEY), "user-a")
        val source = MisskeySource(dvdOrigin, "test-token", server.api(), accountId = account, capabilityCache = CapabilityCache())

        val page = source.timeline(Timeline.Recommended)

        assertEquals(listOf("r1", "r2"), page.items.map { it.id.value })
        assertEquals("2", page.nextCursor)
        val body = server.recommendationBodies().single()
        assertEquals(0, body.getInt("offset"))
        assertFalse(body.has("untilId"))
    }

    @Test fun misskeySourceTimelinesSurfacesRecommendedOnlyWhenSupported() = runBlocking {
        fun sourceWith(status: CapabilityStatus): MisskeySource {
            val capabilities = ServerCapabilities(
                timelines = setOf(Timeline.Home, Timeline.Social),
                recommendations = RecommendationCapability(RecommendationFlavor.DVD_SHARKEY, status, dvdOrigin),
            )
            val account = AccountId(Connection(dvdOrigin, Protocol.MISSKEY), "user-a")
            return MisskeySource(dvdOrigin, "test-token", MisskeyApi(), initialCapabilities = capabilities, accountId = account, capabilityCache = CapabilityCache())
        }

        val supported = sourceWith(CapabilityStatus.Supported)
        assertEquals(listOf(Timeline.Home, Timeline.Social, Timeline.Recommended), supported.timelines())
        assertFalse(Timeline.Recommended in supported.capabilities.timelines)
        for (status in listOf(CapabilityStatus.Denied, CapabilityStatus.Unsupported, CapabilityStatus.Unknown, CapabilityStatus.TemporarilyUnavailable)) {
            assertFalse("Recommended must stay hidden on $status", Timeline.Recommended in sourceWith(status).timelines())
        }
    }
}
