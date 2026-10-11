package me.foxtails.palustris.data

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.mastodon.MastodonSource
import me.foxtails.palustris.data.mastodon.mastodonTestClient
import me.foxtails.palustris.data.misskey.CapabilityCache
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.misskey.MisskeyCapabilityProbe
import me.foxtails.palustris.data.misskey.MisskeySource
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.search.SearchCapabilities
import me.foxtails.palustris.domain.search.SearchQuery
import me.foxtails.palustris.domain.search.classifySearchQuery
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** M6 routes: suggestions, post search, trends and profile addresses on both server families. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var origin: String
    private val seen = CopyOnWriteArrayList<RecordedRequest>()
    private var answers: (RecordedRequest) -> MockResponse = { MockResponse().setResponseCode(404) }

    @Before
    fun start() {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    seen += request
                    return answers(request)
                }
            }
            start()
        }
        origin = server.url("/").toString().removeSuffix("/")
    }

    @After
    fun stop() = server.shutdown()

    private fun json(body: Any) = MockResponse().setHeader("Content-Type", "application/json").setBody(body.toString())

    private fun mastodonAccount(n: Int) = JSONObject().put("id", "a$n").put("acct", "u$n").put("username", "u$n")
        .put("display_name", "U$n").put("url", "https://x/u$n")

    private fun status(id: String) = JSONObject().put("id", id).put("created_at", "2026-10-10T00:00:00.000Z")
        .put("visibility", "public").put("content", "<p>hi</p>").put("account", mastodonAccount(1))

    private fun tag(name: String) = JSONObject().put("name", name).put(
        "history",
        JSONArray().put(JSONObject().put("day", "1").put("accounts", "4").put("uses", "9")),
    )

    private fun mastodon() = MastodonSource(
        origin = origin,
        token = "t",
        api = mastodonTestClient(),
        accountId = AccountId(Connection(origin, Protocol.MASTODON), "me"),
        initialCapabilities = ServerCapabilities(timelines = setOf(Timeline.Home), canPublish = true),
    )

    private fun misskey(capabilities: ServerCapabilities = ServerCapabilities(timelines = setOf(Timeline.Home))) = MisskeySource(
        origin = origin,
        token = "t",
        api = MisskeyApi(),
        accountId = AccountId(Connection(origin, Protocol.MISSKEY), "me"),
        capabilityCache = CapabilityCache(),
        initialCapabilities = capabilities,
    )

    private fun misskeyUser(n: Int) = JSONObject().put("id", "m$n").put("username", "m$n").put("host", JSONObject.NULL)
        .put("name", "M$n")

    private fun note(id: String) = JSONObject().put("id", id).put("createdAt", "2026-10-10T00:00:00.000Z")
        .put("text", "hi").put("visibility", "public").put("user", misskeyUser(1))

    @Test
    fun mastodonSuggestionsAskForThreeAccountsAndSixTagsAndNeverResolveAPartialHandle() = runBlocking {
        answers = { request ->
            val path = request.path.orEmpty()
            when {
                "type=accounts" in path -> json(JSONObject().put("accounts", JSONArray((1..5).map { mastodonAccount(it) })))
                else -> json(JSONObject().put("hashtags", JSONArray((1..9).map { tag("bee$it") })))
            }
        }

        val result = mastodon().searchSuggestions(classifySearchQuery("bee"))

        assertEquals(3, result.accounts.size)
        assertEquals(6, result.hashtags.size)
        val paths = seen.map { it.path.orEmpty() }
        assertTrue(paths.any { "type=accounts" in it && "limit=3" in it && "resolve" !in it })
        assertTrue(paths.any { "type=hashtags" in it && "limit=6" in it })
    }

    @Test
    fun mastodonResolvesOnlyAFullHandleAndAsksNoTagsForIt() = runBlocking {
        answers = { json(JSONObject().put("accounts", JSONArray().put(mastodonAccount(1)))) }

        val result = mastodon().searchSuggestions(classifySearchQuery("@bee@remote.example"))

        assertEquals(1, result.accounts.size)
        assertTrue(seen.single().path.orEmpty().contains("resolve=true"))
        assertTrue(result.hashtags.isEmpty())
    }

    @Test
    fun mastodonKeepsTheAccountsWhenTheTagCallFails() = runBlocking {
        answers = { request ->
            if ("type=accounts" in request.path.orEmpty()) {
                json(JSONObject().put("accounts", JSONArray().put(mastodonAccount(1))))
            } else {
                MockResponse().setResponseCode(500)
            }
        }

        val result = mastodon().searchSuggestions(classifySearchQuery("bee"))

        assertEquals(1, result.accounts.size)
        assertTrue(result.hashtags.isEmpty())
    }

    @Test
    fun mastodonPostSearchPagesByOffsetAndStopsOnAnEmptyPage() = runBlocking {
        var calls = 0
        answers = {
            calls++
            json(JSONObject().put("statuses", JSONArray().apply { if (calls == 1) put(status("1")).put(status("2")) }))
        }

        val first = mastodon().searchPosts("bee")
        val second = mastodon().searchPosts("bee", first.nextCursor)

        assertEquals(2, first.items.size)
        assertEquals("2", first.nextCursor)
        assertTrue(seen[1].path.orEmpty().contains("offset=2"))
        assertNull(second.nextCursor)
    }

    @Test
    fun mastodonTrendsFillEverySectionAndHideARefusedOne() = runBlocking {
        answers = { request ->
            val path = request.path.orEmpty()
            when {
                "trends/tags" in path -> json(JSONArray().put(tag("cats")))
                "trends/statuses" in path -> json(JSONArray().put(status("9")))
                else -> MockResponse().setResponseCode(404)
            }
        }

        val trends = mastodon().trends()

        assertEquals("cats", trends.tags.single().name)
        assertEquals("9", trends.posts.items.single().id.value)
        assertTrue(trends.links.isEmpty())
    }

    @Test
    fun mastodonTrendsParseLinks() = runBlocking {
        answers = { request ->
            if ("trends/links" in request.path.orEmpty()) {
                json(
                    JSONArray().put(
                        JSONObject().put("url", "https://news.example/a").put("title", "A story")
                            .put("description", "").put("provider_name", "News").put("image", JSONObject.NULL),
                    ),
                )
            } else {
                json(JSONArray())
            }
        }

        val link = mastodon().trends().links.single()

        assertEquals("A story", link.title)
        assertEquals("News", link.provider)
        assertNull(link.description)
        assertNull(link.imageUrl)
    }

    @Test
    fun mastodonResolvesAProfileAddress() = runBlocking {
        answers = { json(JSONObject().put("accounts", JSONArray().put(mastodonAccount(4)))) }

        val account = mastodon().resolveAccountUrl("https://remote.example/@bob")

        assertNotNull(account)
        assertTrue(seen.single().path.orEmpty().contains("type=accounts"))
        assertTrue(seen.single().path.orEmpty().contains("resolve=true"))
    }

    @Test
    fun misskeySuggestionsUseTheUserAndHashtagRoutes() = runBlocking {
        answers = { request ->
            when (request.path) {
                "/api/users/search" -> json(JSONArray((1..5).map { misskeyUser(it) }))
                "/api/hashtags/search" -> json(JSONArray((1..9).map { "bee$it" }))
                else -> MockResponse().setResponseCode(404)
            }
        }

        val result = misskey().searchSuggestions(classifySearchQuery("bee"))

        assertEquals(3, result.accounts.size)
        assertEquals(6, result.hashtags.size)
        val bodies = seen.associate { it.path to JSONObject(it.body.readUtf8()) }
        assertEquals(3, bodies.getValue("/api/users/search").getInt("limit"))
        assertEquals(6, bodies.getValue("/api/hashtags/search").getInt("limit"))
    }

    @Test
    fun misskeyHandleSuggestionsUseTheUsernameAndHostRoute() = runBlocking {
        answers = { json(JSONArray().put(misskeyUser(1))) }

        val result = misskey().searchSuggestions(classifySearchQuery("@bee@remote.example"))

        assertEquals(1, result.accounts.size)
        val request = seen.single()
        assertEquals("/api/users/search-by-username-and-host", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("bee", body.getString("username"))
        assertEquals("remote.example", body.getString("host"))
    }

    @Test
    fun misskeyPostSearchPagesByLastId() = runBlocking {
        var calls = 0
        answers = {
            calls++
            json(JSONArray().apply { if (calls == 1) put(note("n2")).put(note("n1")) })
        }

        val first = misskey().searchPosts("bee")
        val second = misskey().searchPosts("bee", first.nextCursor)

        assertEquals("n1", first.nextCursor)
        assertEquals("n1", JSONObject(seen[1].body.readUtf8()).getString("untilId"))
        assertNull(second.nextCursor)
    }

    @Test
    fun misskeyRefusesPostSearchWhenThePolicyDeniesIt() {
        val source = misskey(ServerCapabilities(timelines = setOf(Timeline.Home), search = SearchCapabilities(CapabilityStatus.Denied)))

        assertThrows(SourceError.AccessDenied::class.java) { runBlocking { source.searchPosts("bee") } }
        assertTrue(seen.isEmpty())
    }

    @Test
    fun misskeyTrendsCombineTrendingTagsAndFeaturedNotes() = runBlocking {
        answers = { request ->
            when (request.path) {
                "/api/hashtags/trend" -> json(JSONArray().put(JSONObject().put("tag", "cats").put("usersCount", 7)))
                "/api/notes/featured" -> json(JSONArray().put(note("f1")))
                else -> MockResponse().setResponseCode(404)
            }
        }

        val trends = misskey().trends()

        assertEquals("cats", trends.tags.single().name)
        assertEquals("f1", trends.posts.items.single().id.value)
        assertTrue(trends.links.isEmpty())
    }

    @Test
    fun misskeyProbeReadsTheSearchPolicy() {
        fun status(policy: JSONObject): CapabilityStatus {
            answers = { request ->
                when (request.path) {
                    "/api/meta" -> json(JSONObject().put("version", "2026.10.0"))
                    "/api/i" -> json(JSONObject().put("policies", policy))
                    else -> MockResponse().setResponseCode(404)
                }
            }
            return runBlocking {
                MisskeyCapabilityProbe(MisskeyApi(), "t").probeCapabilities(Connection(origin, Protocol.MISSKEY))
            }.search.posts
        }

        assertEquals(CapabilityStatus.Supported, status(JSONObject().put("canSearchNotes", true)))
        assertEquals(CapabilityStatus.Denied, status(JSONObject().put("canSearchNotes", false)))
        assertEquals(CapabilityStatus.Unknown, status(JSONObject()))
    }

    @Test
    fun misskeyResolvesALocalProfileWithoutFederationAndARemoteOneThroughApShow() = runBlocking {
        answers = { request ->
            when (request.path) {
                "/api/users/show" -> json(misskeyUser(2))
                "/api/ap/show" -> json(JSONObject().put("type", "User").put("object", misskeyUser(3)))
                else -> MockResponse().setResponseCode(404)
            }
        }
        val source = misskey()

        assertEquals("m2", source.resolveAccountUrl("$origin/@m2")?.id?.localId)
        assertEquals("m3", source.resolveAccountUrl("https://remote.example/@m3")?.id?.localId)
        assertEquals(listOf("/api/users/show", "/api/ap/show"), seen.map { it.path })
    }

    @Test
    fun classifierSortsEveryQueryShape() {
        assertEquals(SearchQuery.Blank, classifySearchQuery("   "))
        assertEquals(SearchQuery.Handle("bee", full = false), classifySearchQuery(" @bee "))
        assertEquals(SearchQuery.Handle("bee@remote.example", full = true), classifySearchQuery("@bee@remote.example"))
        assertEquals(SearchQuery.Hashtag("cats"), classifySearchQuery("#cats"))
        assertEquals(SearchQuery.Url("https://x.example/@a/123"), classifySearchQuery("https://x.example/@a/123"))
        assertEquals(SearchQuery.FreeText("hello world"), classifySearchQuery("hello world"))
        assertEquals(SearchQuery.FreeText("#two words"), classifySearchQuery("#two words"))
        assertEquals(SearchQuery.FreeText("bee"), classifySearchQuery("bee"))
        assertEquals(SearchQuery.FreeText("@"), classifySearchQuery("@"))
    }
}
