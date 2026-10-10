package me.foxtails.palustris.data.mastodon

import kotlin.math.ln
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.hashtags.HashtagSuggestion
import me.foxtails.palustris.domain.hashtags.TrendingHashtag
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonDiscoveryTest {
    private lateinit var server: MockWebServer
    private val origin get() = server.url("/").toString().removeSuffix("/")

    @Before
    fun start() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun source() = MastodonSource(
        origin = origin,
        token = "token",
        api = mastodonTestClient(),
        accountId = AccountId(Connection(origin, Protocol.MASTODON), "me"),
    )

    private fun account(id: String) =
        """{"id":"$id","username":"$id","acct":"$id","display_name":"$id"}"""

    private fun day(accounts: Int, uses: Int) = """{"day":"1","accounts":"$accounts","uses":"$uses"}"""

    @Test
    fun trendingHashtagsSumTheLatestTwoDays() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """[{"name":"cats","history":[${day(5, 9)},${day(3, 4)},${day(100, 100)}]},{"name":"nohistory"}]""",
            ),
        )

        val trending = source().trendingHashtags(50)

        assertEquals(listOf(TrendingHashtag("cats", 8, 13), TrendingHashtag("nohistory", null, null)), trending)
        val request = server.takeRequest()
        assertEquals("/api/v1/trends/tags?limit=20", request.path)
        assertEquals("Bearer token", request.getHeader("Authorization"))
    }

    @Test
    fun trendingHashtagsSkipMalformedItemsAndHandleAnEmptyList() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"name":"bad tag"},{"nothing":1},"text",{"name":"ok"}]"""))
        server.enqueue(MockResponse().setBody("[]"))

        assertEquals(listOf("ok"), source().trendingHashtags(20).map { it.name })
        assertEquals(emptyList<TrendingHashtag>(), source().trendingHashtags(20))
    }

    @Test
    fun trendingHashtagsFailOnAServerError() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        assertThrows(SourceError::class.java) { runBlocking { source().trendingHashtags(20) } }
    }

    @Test
    fun suggestionsWeighByTheSumOfAccountsInTheHistory() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"accounts":[],"statuses":[],"hashtags":[{"name":"photo","history":[${day(3, 3)},${day(4, 5)}]},{"name":"photos"},{"name":"bad tag"}]}""",
            ),
        )

        val suggestions = source().suggestHashtags("pho", 8)

        assertEquals(2, suggestions.size)
        assertEquals("photo", suggestions[0].name)
        assertEquals(ln(8.0) / ln(2.0), suggestions[0].weight!!, 1e-9)
        assertEquals(HashtagSuggestion("photos", null), suggestions[1])
        assertEquals("/api/v2/search?q=pho&type=hashtags&limit=8", server.takeRequest().path)
    }

    @Test
    fun suggestionsHandleEmptyAndFailedAnswers() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"hashtags":[]}"""))
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(422).setBody("{}"))

        assertEquals(emptyList<HashtagSuggestion>(), source().suggestHashtags("x", 8))
        assertEquals(emptyList<HashtagSuggestion>(), source().suggestHashtags("x", 8))
        assertThrows(SourceError::class.java) { runBlocking { source().suggestHashtags("x", 8) } }
        Unit
    }

    @Test
    fun popularAccountsMapTheSuggestionAccount() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """[{"source":"staff","account":${account("a")}},{"source":"x"},{"account":${account("a")}},{"account":${account("b")}}]""",
            ),
        )

        val accounts = source().popularAccounts(20)

        assertEquals(listOf("a", "b"), accounts.map { it.id.localId })
        assertEquals("/api/v2/suggestions?limit=20", server.takeRequest().path)
    }

    @Test
    fun popularAccountsFallBackToTheDirectory() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        server.enqueue(MockResponse().setBody("[${account("d1")},{\"broken\":true},${account("d2")}]"))

        val accounts = source().popularAccounts(20)

        assertEquals(listOf("d1", "d2"), accounts.map { it.id.localId })
        assertEquals("/api/v2/suggestions?limit=20", server.takeRequest().path)
        assertEquals("/api/v1/directory?order=active&local=true&limit=20", server.takeRequest().path)
    }

    @Test
    fun popularAccountsFailWhenTheFallbackFailsToo() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        assertThrows(SourceError::class.java) { runBlocking { source().popularAccounts(20) } }
    }
}
