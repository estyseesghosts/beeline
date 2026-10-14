package me.foxtails.palustris.data.misskey

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.ContentFilterDraft
import me.foxtails.palustris.domain.ContentFilterInput
import me.foxtails.palustris.domain.ContentFilterKind
import me.foxtails.palustris.domain.FilterPattern
import me.foxtails.palustris.domain.PostFilterAction
import me.foxtails.palustris.domain.SourceError
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `mutedWords` collapse, `hardMutedWords` remove, and every write sends both lists with unknown entries kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MisskeyWordMuteServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var service: MisskeyWordMuteService

    private val account = """{"id":"me","mutedWords":[["finale"],["spoiler","season"],"/fin\\.ale/i",42],"hardMutedWords":["/sp(oi)+ler/"]}"""

    @Before
    fun start() {
        server = MockWebServer().also { it.start() }
        service = MisskeyWordMuteService(server.url("/").toString().removeSuffix("/"), "token", MisskeyApi())
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    @Test
    fun listsMapToWordMutes() = runBlocking {
        server.enqueue(json(account))

        val set = service.filters()

        assertEquals(ContentFilterKind.WordMute, set.kind)
        assertEquals(listOf("finale", "spoiler season", "fin.ale", "/sp(oi)+ler/"), set.filters.map { it.title })
        assertEquals(
            listOf(PostFilterAction.Collapse, PostFilterAction.Collapse, PostFilterAction.Collapse, PostFilterAction.Remove),
            set.filters.map { it.action },
        )
        assertEquals(FilterPattern.Words(listOf("finale"), ignoreCase = false), set.filters[0].patterns.single())
        assertEquals(FilterPattern.Expression("fin\\.ale", "i"), set.filters[2].patterns.single())
        val request = server.takeRequest()
        assertEquals("/api/i", request.path)
        assertEquals("token", JSONObject(request.body.readUtf8()).getString("i"))
    }

    @Test
    fun caseInsensitiveWordIsStoredAsAnEscapedExpression() = runBlocking {
        server.enqueue(json(account))
        server.enqueue(json(account))

        service.save(null, ContentFilterDraft("a.b", listOf(FilterPattern.Words(listOf("a.b/c"), ignoreCase = true)), PostFilterAction.Remove))

        server.takeRequest()
        val update = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(JSONArray("""["/sp(oi)+ler/","/a\\.b\\/c/i"]""").toString(), update.getJSONArray("hardMutedWords").toString())
        // Entries Beeline cannot read stay in the list.
        assertEquals(4, update.getJSONArray("mutedWords").length())
    }

    @Test
    fun editKeepsThePositionAndMovingToTheOtherListAppends() = runBlocking {
        server.enqueue(json(account))
        val first = service.filters().filters.first()
        server.takeRequest()

        server.enqueue(json(account))
        server.enqueue(json(account))
        service.save(first.id, ContentFilterDraft("final", listOf(FilterPattern.Words(listOf("final"), ignoreCase = false)), PostFilterAction.Collapse))
        server.takeRequest()
        val inPlace = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("""["final"]""", inPlace.getJSONArray("mutedWords").get(0).toString())

        server.enqueue(json(account))
        server.enqueue(json(account))
        service.save(first.id, ContentFilterDraft("finale", listOf(FilterPattern.Words(listOf("finale"), ignoreCase = false)), PostFilterAction.Remove))
        server.takeRequest()
        val moved = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(3, moved.getJSONArray("mutedWords").length())
        assertEquals("""["finale"]""", moved.getJSONArray("hardMutedWords").get(1).toString())
    }

    @Test
    fun deleteRemovesOnlyThatEntry() = runBlocking {
        server.enqueue(json(account))
        server.enqueue(json("""{"id":"me","mutedWords":[],"hardMutedWords":[]}"""))
        val id = MisskeyWordMuteService.entryId(PostFilterAction.Remove, "/sp(oi)+ler/")!!

        val set = service.delete(id)

        server.takeRequest()
        val update = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(0, update.getJSONArray("hardMutedWords").length())
        assertEquals(4, update.getJSONArray("mutedWords").length())
        assertEquals(0, set.filters.size)
    }

    @Test
    fun serverRefusalsBecomeInvalidInput() {
        server.enqueue(json(account))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"code":"INVALID_REGEXP","message":"Invalid Regular Expression."}}"""))
        server.enqueue(json(account))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"code":"TOO_MANY_MUTED_WORDS"}}"""))
        val draft = ContentFilterDraft("x", listOf(FilterPattern.Expression("(?=x)")), PostFilterAction.Collapse)

        val rejected = assertThrows(SourceError.InvalidInput::class.java) { runBlocking { service.save(null, draft) } }
        val full = assertThrows(SourceError.InvalidInput::class.java) { runBlocking { service.save(null, draft) } }

        assertEquals(ContentFilterInput.EXPRESSION, rejected.feature)
        assertEquals(ContentFilterInput.LIMIT, full.feature)
    }
}
