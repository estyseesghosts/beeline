package me.foxtails.palustris.data.misskey

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.hashtags.HashtagSuggestion
import me.foxtails.palustris.domain.hashtags.TrendingHashtag
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
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
class MisskeyDiscoveryTest {
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

    private fun source() = MisskeySource(
        origin,
        "test-token",
        MisskeyApi(),
        accountId = AccountId(Connection(origin, Protocol.MISSKEY), "me"),
        capabilityCache = CapabilityCache(),
    )

    private fun user(id: String) = """{"id":"$id","username":"$id","name":"$id","host":null}"""

    @Test
    fun trendingHashtagsUseTheUsersCount() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""[{"tag":"cats","usersCount":7,"chart":[]},{"tag":"bad tag","usersCount":3},{"x":1}]"""),
        )

        val trending = source().trendingHashtags(20)

        assertEquals(listOf(TrendingHashtag("cats", 7, null)), trending)
        val request = server.takeRequest()
        assertEquals("/api/hashtags/trend", request.path)
        assertEquals("test-token", JSONObject(request.body.readUtf8()).getString("i"))
    }

    @Test
    fun anEmptyTrendFallsBackToTheHashtagList() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(MockResponse().setBody("""[{"tag":"dogs","mentionedUsersCount":4}]"""))

        val trending = source().trendingHashtags(20)

        assertEquals(listOf(TrendingHashtag("dogs", 4, null)), trending)
        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("/api/hashtags/list", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals(20, body.getInt("limit"))
        assertEquals("+mentionedUsers", body.getString("sort"))
    }

    @Test
    fun trendingFailsOnAServerError() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        assertThrows(SourceError::class.java) { runBlocking { source().trendingHashtags(20) } }
    }

    @Test
    fun suggestionsReadBareNamesWithoutWeights() = runBlocking {
        server.enqueue(MockResponse().setBody("""["photo","photos","bad tag"]"""))
        server.enqueue(MockResponse().setBody("[]"))

        assertEquals(
            listOf(HashtagSuggestion("photo", null), HashtagSuggestion("photos", null)),
            source().suggestHashtags("pho", 8),
        )
        assertEquals(emptyList<HashtagSuggestion>(), source().suggestHashtags("x", 8))
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("pho", body.getString("query"))
        assertEquals(8, body.getInt("limit"))
    }

    @Test
    fun suggestionsFailOnAClientError() {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody("""{"error":{"message":"bad","code":"INVALID_PARAM","id":"x"}}"""),
        )
        assertThrows(SourceError::class.java) { runBlocking { source().suggestHashtags("x", 8) } }
    }

    @Test
    fun popularAccountsJoinPinnedAndRankedUsersWithoutDuplicatesOrTheSignedInAccount() = runBlocking {
        server.enqueue(MockResponse().setBody("[${user("p1")},${user("me")}]"))
        server.enqueue(MockResponse().setBody("[${user("p1")},{\"broken\":1},${user("r1")}]"))

        val accounts = source().popularAccounts(20)

        assertEquals(listOf("p1", "r1"), accounts.map { it.id.localId })
        assertEquals("/api/pinned-users", server.takeRequest().path)
        val users = server.takeRequest()
        assertEquals("/api/users", users.path)
        val body = JSONObject(users.body.readUtf8())
        assertEquals("+follower", body.getString("sort"))
        assertEquals("alive", body.getString("state"))
        assertEquals("local", body.getString("origin"))
    }

    @Test
    fun popularAccountsKeepTheListThatWorks() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        server.enqueue(MockResponse().setBody("[${user("r1")}]"))

        assertEquals(listOf("r1"), source().popularAccounts(20).map { it.id.localId })
    }

    @Test
    fun popularAccountsFailWhenBothListsFail() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        assertThrows(SourceError::class.java) { runBlocking { source().popularAccounts(20) } }
    }
}
