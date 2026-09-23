package me.foxtails.palustris

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.mastodon.MastodonModerationService
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.misskey.ApiFailure
import me.foxtails.palustris.data.misskey.MisskeyModerationService
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ModerationListKind
import me.foxtails.palustris.domain.ModerationListQuery
import me.foxtails.palustris.domain.ModerationCursor
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ReportRequest
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.EntityId
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModerationServiceTest {
    private lateinit var server: MockWebServer

    @Before
    fun startServer() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun misskeyUsesRelationshipIdsForPagingAndRemoval() {
        runBlocking {
            val origin = server.url("/").toString().removeSuffix("/")
            val accountId = AccountId(Connection(origin, Protocol.MISSKEY), "viewer")
            val service = MisskeyModerationService(origin, "token", MisskeyApi(), accountId)
            server.enqueue(MockResponse().setBody("[{\"id\":\"block-1\",\"blockee\":${misskeyUser("blocked")}}]"))
            server.enqueue(MockResponse())

            val page = service.blocked()
            service.removeBlocked(page.items.single())

            assertEquals("blocked", page.items.single().account.id.localId)
            assertEquals("block-1", page.items.single().relationshipId)
            val listRequest = server.takeRequest()
            val removeRequest = server.takeRequest()
            assertEquals("/api/blocking", listRequest.path)
            assertEquals("/api/blocking/delete", removeRequest.path)
            assertEquals("block-1", JSONObject(removeRequest.body.readUtf8()).getString("blockId"))
        }
    }

    @Test
    fun misskeyRejectsHashtagMappingRatherThanTreatingWordMutesAsHashtags() {
        val origin = server.url("/").toString().removeSuffix("/")
        val service = MisskeyModerationService(
            origin,
            "token",
            MisskeyApi(),
            AccountId(Connection(origin, Protocol.MISSKEY), "viewer"),
        )
        assertThrows(SourceError.Unsupported::class.java) { runBlocking { service.hashtags() } }
    }

    @Test
    fun mastodonUsesAccountRoutesAndRejectsForeignCursors() {
        runBlocking {
            val origin = server.url("/").toString().removeSuffix("/")
            val accountId = AccountId(Connection(origin, Protocol.MASTODON), "viewer")
            val service = MastodonModerationService(origin, "token", MisskeyApi(), accountId)
            server.enqueue(MockResponse().setBody("[${mastodonUser("blocked")}]"))
            server.enqueue(MockResponse())

            val page = service.blocked()
            service.removeBlocked(page.items.single())

            assertEquals("/api/v1/accounts/blocked?limit=40", server.takeRequest().path)
            assertEquals("/api/v1/accounts/blocked/block", server.takeRequest().path)

            val foreignCursor = ModerationCursor(
                AccountId(Connection("https://foreign.example", Protocol.MASTODON), "viewer"),
                ModerationListQuery(ModerationListKind.Blocked),
                "mastodon-blocked-v1",
                "https://foreign.example/api/v1/accounts/blocked?max_id=1",
            )
            assertThrows(SourceError.Unsupported::class.java) { runBlocking { service.blocked(foreignCursor) } }
        }
    }

    @Test
    fun mastodonRejectsForeignMutationBeforeRequest() {
        val origin = server.url("/").toString().removeSuffix("/")
        val service = MastodonModerationService(origin, "token", MisskeyApi(), AccountId(Connection(origin, Protocol.MASTODON), "viewer"))
        val foreign = AccountId(Connection("https://foreign.example", Protocol.MASTODON), "target")

        val error = assertThrows(SourceError.ForeignOrigin::class.java) {
            runBlocking { service.setBlocked(foreign, true) }
        }

        assertEquals("profile.block", error.feature)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun mastodonEncodesReservedTargetOnEveryAccountRoute() {
        runBlocking {
            val origin = server.url("/").toString().removeSuffix("/")
            val service = MastodonModerationService(origin, "token", MisskeyApi(), AccountId(Connection(origin, Protocol.MASTODON), "viewer"))
            val targetId = AccountId(Connection(origin, Protocol.MASTODON), "segment/with?query value")
            server.enqueue(MockResponse().setBody("[${mastodonUser(targetId.localId)}]"))
            server.enqueue(MockResponse().setBody("{\"blocking\":true}"))
            server.enqueue(MockResponse().setBody("{\"blocking\":false}"))
            server.enqueue(MockResponse().setBody("{\"muting\":true}"))
            server.enqueue(MockResponse().setBody("{\"muting\":false}"))
            server.enqueue(MockResponse())
            server.enqueue(MockResponse())

            val entry = service.blocked().items.single()
            service.setBlocked(targetId, true)
            service.setBlocked(targetId, false)
            service.setMuted(targetId, true)
            service.setMuted(targetId, false)
            service.removeBlocked(entry)
            service.removeMuted(entry)

            val expectedRequests = listOf(
                "GET" to "/api/v1/accounts/blocked?limit=40",
                "POST" to "/api/v1/accounts/segment%2Fwith%3Fquery%20value/block",
                "DELETE" to "/api/v1/accounts/segment%2Fwith%3Fquery%20value/unblock",
                "POST" to "/api/v1/accounts/segment%2Fwith%3Fquery%20value/mute",
                "DELETE" to "/api/v1/accounts/segment%2Fwith%3Fquery%20value/unmute",
                "DELETE" to "/api/v1/accounts/segment%2Fwith%3Fquery%20value/block",
                "DELETE" to "/api/v1/accounts/segment%2Fwith%3Fquery%20value/mute",
            )
            expectedRequests.forEach { (method, path) ->
                val request = server.takeRequest()
                assertEquals(path, request.path)
                assertEquals(method, request.method)
            }
        }
    }

    @Test
    fun mastodonRecoversMalformedMutationUsingEncodedRelationshipQuery() {
        runBlocking {
            val origin = server.url("/").toString().removeSuffix("/")
            val token = "secret-token"
            val service = MastodonModerationService(
                origin,
                token,
                MisskeyApi(),
                AccountId(Connection(origin, Protocol.MASTODON), "viewer"),
            )
            val target = AccountId(Connection(origin, Protocol.MASTODON), "segment/with?query value")
            server.enqueue(MockResponse().setBody("{}"))
            server.enqueue(MockResponse().setBody("[ {\"blocking\":true} ]"))

            val relationship = service.setBlocked(target, true)
            val mutation = server.takeRequest()
            val recovery = server.takeRequest()

            assertEquals("/api/v1/accounts/segment%2Fwith%3Fquery%20value/block", mutation.path)
            assertEquals("Bearer $token", mutation.getHeader("Authorization"))
            assertEquals("/api/v1/accounts/relationships?id%5B%5D=segment%2Fwith%3Fquery%20value", recovery.path)
            assertEquals("Bearer $token", recovery.getHeader("Authorization"))
            assertTrue(relationship.blocking)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun mastodonRecoveryFailureDoesNotRetryOrIncludeTokenInError() {
        runBlocking {
            val origin = server.url("/").toString().removeSuffix("/")
            val token = "secret-token"
            val service = MastodonModerationService(
                origin,
                token,
                MisskeyApi(),
                AccountId(Connection(origin, Protocol.MASTODON), "viewer"),
            )
            val target = AccountId(Connection(origin, Protocol.MASTODON), "target")
            server.enqueue(MockResponse().setBody("{}"))
            server.enqueue(MockResponse().setResponseCode(400).setBody("bad request"))

            val error = assertThrows(ApiFailure::class.java) { runBlocking { service.setMuted(target, true) } }
            val mutation = server.takeRequest()
            val recovery = server.takeRequest()
            assertEquals("/api/v1/accounts/target/mute", mutation.path)
            assertEquals("/api/v1/accounts/relationships?id%5B%5D=target", recovery.path)
            assertEquals("Bearer $token", recovery.getHeader("Authorization"))
            assertEquals(400, error.status)
            val messages = generateSequence(error as Throwable?) { it.cause }.mapNotNull { it.message }.toList()
            assertTrue(messages.isNotEmpty())
            assertTrue(messages.none { it.contains(token) })
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun mastodonMapsReportsToAccountAndStatusFields() {
        runBlocking {
            val origin = server.url("/").toString().removeSuffix("/")
            val service = MastodonModerationService(
                origin,
                "token",
                MisskeyApi(),
                AccountId(Connection(origin, Protocol.MASTODON), "viewer"),
            )
            server.enqueue(MockResponse())

            service.report(
                ReportRequest(
                    targetAccountId = AccountId(Connection(origin, Protocol.MASTODON), "target"),
                    postId = EntityId(origin, "status"),
                    comment = "spam",
                ),
            )

            val request = server.takeRequest()
            assertEquals("/api/v1/reports", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("account_id=target"))
            assertTrue(body.contains("status_ids%5B%5D=status"))
            assertTrue(body.contains("comment=spam"))
        }
    }

    @Test
    fun misskeyReportsAccountsAndRejectsPostReports() {
        runBlocking {
            val origin = server.url("/").toString().removeSuffix("/")
            val service = MisskeyModerationService(
                origin,
                "token",
                MisskeyApi(),
                AccountId(Connection(origin, Protocol.MISSKEY), "viewer"),
            )
            server.enqueue(MockResponse())

            service.report(
                ReportRequest(AccountId(Connection(origin, Protocol.MISSKEY), "target"), comment = "abuse"),
            )
            val request = server.takeRequest()
            assertEquals("/api/users/report", request.path)
            val body = JSONObject(request.body.readUtf8())
            assertEquals("target", body.getString("userId"))
            assertEquals("abuse", body.getString("comment"))

            assertThrows(SourceError.Unsupported::class.java) {
                runBlocking {
                    service.report(
                        ReportRequest(
                            AccountId(Connection(origin, Protocol.MISSKEY), "target"),
                            EntityId(origin, "status"),
                            "abuse",
                        ),
                    )
                }
            }
        }
    }

    private fun misskeyUser(id: String) = JSONObject()
        .put("id", id)
        .put("username", "user")
        .put("name", "User")
        .put("host", JSONObject.NULL)

    private fun mastodonUser(id: String) = JSONObject()
        .put("id", id)
        .put("username", "user")
        .put("acct", "user")
        .put("display_name", "User")
        .put("avatar", JSONObject.NULL)
        .put("note", "")
        .put("header", JSONObject.NULL)
        .put("followers_count", 0)
        .put("following_count", 0)
        .put("statuses_count", 0)
}
