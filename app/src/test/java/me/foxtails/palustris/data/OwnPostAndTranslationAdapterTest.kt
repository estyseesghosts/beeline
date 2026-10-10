package me.foxtails.palustris.data

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.mastodon.MastodonCapabilityProbe
import me.foxtails.palustris.data.mastodon.MastodonSource
import me.foxtails.palustris.data.mastodon.mastodonTestClient
import me.foxtails.palustris.data.misskey.CapabilityCache
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.misskey.MisskeyMapper
import me.foxtails.palustris.data.misskey.MisskeySource
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OwnPostAndTranslationAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var origin: String

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        origin = server.url("/").toString().removeSuffix("/")
    }

    @After
    fun stop() = server.shutdown()

    private fun mastodon() = MastodonSource(
        origin = origin,
        token = "t",
        api = mastodonTestClient(),
        accountId = AccountId(Connection(origin, Protocol.MASTODON), "me"),
        initialCapabilities = ServerCapabilities(timelines = setOf(Timeline.Home)),
    )

    private fun misskey() = MisskeySource(
        origin = origin,
        token = "t",
        api = MisskeyApi(),
        accountId = AccountId(Connection(origin, Protocol.MISSKEY), "me"),
        capabilityCache = CapabilityCache(),
    )

    private fun id(value: String) = EntityId(origin, value)

    @Test
    fun mastodonDeleteSendsDeleteToTheStatus() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))

        mastodon().delete(id("42"))

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/statuses/42", request.path)
    }

    @Test
    fun misskeyDeleteSendsNotesDelete() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))

        misskey().delete(id("abc"))

        val request = server.takeRequest()
        assertEquals("/api/notes/delete", request.path)
        assertEquals("abc", JSONObject(request.body.readUtf8()).getString("noteId"))
    }

    @Test
    fun mastodonTranslateSendsLangAndMapsTheResponse() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                JSONObject()
                    .put("content", "<p>Hello <b>world</b></p>")
                    .put("spoiler_text", "")
                    .put("detected_source_language", "es")
                    .put("language", "en")
                    .put("provider", "DeepL.com")
                    .put("poll", JSONObject().put("id", "9").put("options", JSONArray().put(JSONObject().put("title", "Yes"))))
                    .put("media_attachments", JSONArray().put(JSONObject().put("id", "m1").put("description", "A cat")))
                    .toString(),
            ),
        )

        val result = mastodon().translate(id("42"), "en")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/statuses/42/translate", request.path)
        assertTrue(request.body.readUtf8().contains("lang=en"))
        assertTrue(result.text.contains("Hello"))
        assertEquals("es", result.sourceLanguage)
        assertEquals("DeepL.com", result.provider)
        assertEquals(listOf("Yes"), result.pollOptions)
        assertEquals(mapOf("m1" to "A cat"), result.attachmentDescriptions)
        assertNull(result.contentWarning)
    }

    @Test
    fun mastodonTranslateMapsNotConfiguredAndBusy() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("{\"error\":\"Record not found\"}"))
        server.enqueue(MockResponse().setResponseCode(503).setBody("{\"error\":\"busy\"}"))
        val source = mastodon()

        try {
            source.translate(id("1"), "en")
            fail("expected unsupported")
        } catch (e: SourceError.Unsupported) {
            assertEquals("translate", e.feature)
        }
        try {
            source.translate(id("1"), "en")
            fail("expected busy")
        } catch (e: SourceError.ResourceLimit) {
            assertEquals("translate.busy", e.feature)
        }
    }

    @Test
    fun misskeyTranslateSendsTargetLangAndMapsTheResponse() = runBlocking {
        server.enqueue(MockResponse().setBody(JSONObject().put("sourceLang", "ja").put("text", "Hello").toString()))

        val result = misskey().translate(id("n1"), "en")

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("n1", body.getString("noteId"))
        assertEquals("en", body.getString("targetLang"))
        assertEquals("Hello", result.text)
        assertEquals("ja", result.sourceLanguage)
        assertNull(result.provider)
    }

    @Test
    fun misskeyTranslateMapsUnavailable() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                JSONObject().put("error", JSONObject().put("code", "UNAVAILABLE").put("message", "x")).toString(),
            ),
        )

        try {
            misskey().translate(id("n1"), "en")
            fail("expected unsupported")
        } catch (e: SourceError.Unsupported) {
            assertEquals("translate", e.feature)
        }
    }

    @Test
    fun misskeyMapperReadsBothTranslationShapes() {
        val result = MisskeyMapper.translation(JSONObject().put("text", "Hi"))
        assertEquals("Hi", result.text)
        assertNull(result.sourceLanguage)
    }

    @Test
    fun mastodonProbeReadsTranslationFromConfigurationOrAkkomaFeature() {
        fun cap(instance: JSONObject) = MastodonCapabilityProbe.translationCapability(instance)

        val enabled = JSONObject().put("configuration", JSONObject().put("translation", JSONObject().put("enabled", true)))
        assertEquals(CapabilityStatus.Supported, cap(enabled).status)
        assertTrue(cap(enabled).publicOnly)

        val disabled = JSONObject().put("configuration", JSONObject().put("translation", JSONObject().put("enabled", false)))
        assertEquals(CapabilityStatus.Unsupported, cap(disabled).status)

        val akkoma = JSONObject().put(
            "pleroma",
            JSONObject().put("metadata", JSONObject().put("features", JSONArray().put("akkoma:machine_translation"))),
        )
        assertEquals(CapabilityStatus.Supported, cap(akkoma).status)

        assertEquals(CapabilityStatus.Unsupported, cap(JSONObject()).status)
    }

    @Test
    fun mastodonProbeMarksDeleteSupported() {
        val capabilities = MastodonCapabilityProbe.parseCapabilities(JSONObject().put("version", "4.5.0"))

        assertEquals(CapabilityStatus.Supported, capabilities.ownPosts.delete)
        assertEquals(CapabilityStatus.Unknown, capabilities.ownPosts.edit)
    }
}
