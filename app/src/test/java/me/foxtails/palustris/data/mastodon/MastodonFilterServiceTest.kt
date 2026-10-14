package me.foxtails.palustris.data.mastodon

import java.net.URLDecoder
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.transport.HttpStatusFailure
import me.foxtails.palustris.domain.ContentFilterDraft
import me.foxtails.palustris.domain.ContentFilterKind
import me.foxtails.palustris.domain.FilterContext
import me.foxtails.palustris.domain.FilterPattern
import me.foxtails.palustris.domain.PostFilterAction
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v2 filters on Mastodon 4.0 and later, v1 filters after a v2 404, and the form fields of each write. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonFilterServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var service: MastodonFilterService

    private val v2List = """
        [{"id":"f1","title":"Spoilers","context":["home","public"],"filter_action":"hide","expires_at":"2026-10-20T10:00:00.000Z",
          "keywords":[{"id":"k1","keyword":"finale","whole_word":true},{"id":"k2","keyword":"season","whole_word":false}]}]
    """.trimIndent()
    private val v1List = """[{"id":"7","phrase":"finale","context":["home","thread"],"whole_word":false,"irreversible":true,"expires_at":null}]"""

    @Before
    fun start() {
        server = MockWebServer().also { it.start() }
        service = MastodonFilterService(server.url("/").toString().removeSuffix("/"), "token", mastodonTestClient())
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    private fun RecordedRequest.form(): List<Pair<String, String>> = body.readUtf8().split('&').filter(String::isNotEmpty).map { field ->
        val (key, value) = field.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
    }

    @Test
    fun v2FiltersMapToTitledFilters() = runBlocking {
        server.enqueue(json(v2List))

        val set = service.filters()

        assertEquals(ContentFilterKind.Titled, set.kind)
        val filter = set.filters.single()
        assertEquals("Spoilers", filter.title)
        assertEquals(PostFilterAction.Remove, filter.action)
        assertEquals(setOf(FilterContext.Home, FilterContext.Public), filter.contexts)
        assertEquals(listOf(FilterPattern.Words(listOf("finale"), wholeWord = true), FilterPattern.Words(listOf("season"))), filter.patterns)
        assertEquals(listOf("k1", "k2"), filter.patternIds)
        assertEquals(1_792_490_400_000L, filter.expiresAtEpochMillis)
        assertEquals("/api/v2/filters", server.takeRequest().path)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun v2NotFoundSelectsV1FiltersForTheLifeOfTheService() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(json(v1List))
        server.enqueue(json(v1List))

        val first = service.filters()
        val second = service.filters()

        assertEquals(ContentFilterKind.Phrase, first.kind)
        assertEquals(PostFilterAction.Remove, first.filters.single().action)
        assertEquals("finale", first.filters.single().title)
        assertEquals(setOf(FilterContext.Home, FilterContext.Thread), first.filters.single().contexts)
        assertEquals(ContentFilterKind.Phrase, second.kind)
        assertEquals(listOf("/api/v2/filters", "/api/v1/filters", "/api/v1/filters"), List(3) { server.takeRequest().path })
    }

    @Test
    fun otherV2FailuresStayFailuresAndSelectNothing() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(json("[]"))

        val failure = assertThrows(HttpStatusFailure::class.java) { runBlocking { service.filters() } }
        val retry = runBlocking { service.filters() }

        assertEquals(500, failure.status)
        assertEquals(ContentFilterKind.Titled, retry.kind)
        assertEquals("/api/v2/filters", server.takeRequest().path)
        assertEquals("/api/v2/filters", server.takeRequest().path)
    }

    @Test
    fun v2UpdateKeepsKeywordIdsAndDestroysRemovedKeywords() = runBlocking {
        server.enqueue(json(v2List))
        server.enqueue(json(v2List))
        server.enqueue(json("{}"))
        server.enqueue(json(v2List))
        service.filters()
        server.takeRequest()

        service.save(
            "f1",
            ContentFilterDraft(
                title = "Spoilers",
                patterns = listOf(FilterPattern.Words(listOf("finale"), wholeWord = false), FilterPattern.Words(listOf("episode"))),
                action = PostFilterAction.Collapse,
                contexts = setOf(FilterContext.Thread),
                expiresInSeconds = null,
            ),
        )

        assertEquals("/api/v2/filters", server.takeRequest().path)
        val update = server.takeRequest()
        assertEquals("PUT", update.method)
        assertEquals("/api/v2/filters/f1", update.path)
        assertEquals(
            listOf(
                "title" to "Spoilers",
                "filter_action" to "warn",
                "context[]" to "thread",
                "expires_in" to "",
                "keywords_attributes[0][id]" to "k1",
                "keywords_attributes[0][keyword]" to "finale",
                "keywords_attributes[0][whole_word]" to "false",
                "keywords_attributes[1][keyword]" to "episode",
                "keywords_attributes[1][whole_word]" to "false",
                "keywords_attributes[2][id]" to "k2",
                "keywords_attributes[2][_destroy]" to "true",
            ),
            update.form(),
        )
        assertEquals("/api/v2/filters", server.takeRequest().path)
    }

    @Test
    fun v1CreateSendsOnePhraseAndNoExpiryWhenItNeverExpires() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(json("[]"))
        server.enqueue(json("{}"))
        server.enqueue(json(v1List))
        service.filters()

        val set = service.save(
            null,
            ContentFilterDraft("finale", listOf(FilterPattern.Words(listOf("finale"), wholeWord = true)), PostFilterAction.Remove, setOf(FilterContext.Home)),
        )

        server.takeRequest()
        server.takeRequest()
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/api/v1/filters", create.path)
        val fields = create.form()
        assertEquals(listOf("phrase" to "finale", "whole_word" to "true", "irreversible" to "true", "context[]" to "home"), fields)
        assertFalse(fields.any { it.first == "expires_in" })
        assertEquals(1, set.filters.size)
    }

    @Test
    fun deleteUsesTheSelectedVersionAndEncodesTheId() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(json(v1List))
        server.enqueue(json("{}"))
        server.enqueue(json("[]"))
        service.filters()

        val set = service.delete("a/b")

        server.takeRequest()
        server.takeRequest()
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/api/v1/filters/a%2Fb", delete.path)
        assertTrue(set.filters.isEmpty())
    }
}
