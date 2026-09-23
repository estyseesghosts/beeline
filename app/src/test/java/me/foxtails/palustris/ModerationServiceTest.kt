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
    fun mastodonBlockedAndMutedPagesUseOpaqueRouteBoundCursors() {
        runBlocking {
            val origin = server.url("/").toString().removeSuffix("/")
            val token = "page-token"
            val service = MastodonModerationService(origin, token, MisskeyApi(), AccountId(Connection(origin, Protocol.MASTODON), "viewer"))
            for (route in listOf("blocked", "muted")) {
                val firstId = "$route-first"
                val secondId = "$route-second"
                server.enqueue(MockResponse().setBody("[${mastodonUser(firstId)}]").addHeader("Link", "<$origin/api/v1/accounts/$route?limit=40&max_id=opaque%2Fid>; rel=\"next\""))
                server.enqueue(MockResponse().setBody("[${mastodonUser(secondId)}]"))
                val first = if (route == "blocked") service.blocked() else service.muted()
                assertEquals(listOf(firstId), first.items.map { it.account.id.localId })
                assertTrue(first.nextCursor!!.value.none { it == '/' })
                val secondPage = if (route == "blocked") service.blocked(first.nextCursor) else service.muted(first.nextCursor)
                assertEquals(listOf(secondId), secondPage.items.map { it.account.id.localId })
                assertEquals(listOf(firstId, secondId), first.items.map { it.account.id.localId } + secondPage.items.map { it.account.id.localId })
                val firstRequest = server.takeRequest()
                assertEquals("GET", firstRequest.method)
                assertEquals("/api/v1/accounts/$route?limit=40", firstRequest.path)
                assertEquals("Bearer $token", firstRequest.getHeader("Authorization"))
                val second = server.takeRequest()
                assertEquals("GET", second.method)
                assertEquals("/api/v1/accounts/$route?limit=40&max_id=opaque%2Fid", second.path)
                assertEquals("Bearer $token", second.getHeader("Authorization"))
            }
        }
    }

    @Test
    fun mastodonModerationRejectsTamperedAndLegacyCursorsBeforeRequest() {
        runBlocking {
            val origin = server.url("/").toString().removeSuffix("/")
            val account = AccountId(Connection(origin, Protocol.MASTODON), "viewer")
            val service = MastodonModerationService(origin, "token", MisskeyApi(), account)
            server.enqueue(MockResponse().setBody("[]").addHeader("Link", "<$origin/api/v1/accounts/blocked?limit=40&max_id=x>; rel=\"next\""))
            val valid = service.blocked().nextCursor!!
            val raw = JSONObject(String(java.util.Base64.getUrlDecoder().decode(valid.value), Charsets.UTF_8))
            val count = server.requestCount
            val urls = listOf(
                "$origin/api/v1/accounts/notifications?max_id=x", "$origin/api/v1/accounts/blocked?limit=41&max_id=x",
                "$origin/api/v1/accounts/blocked?limit=40&max_id=x&unknown=y", "$origin/api/v1/accounts/blocked?limit=40&max_id=x&max_id=y",
                "$origin/api/v1/accounts/blocked?limit=40&max_id=", "$origin/api/v1/accounts/blocked?limit=40&max_id=x#frag",
                "$origin/api/v1/accounts/blocked?max_id=x&limit", "$origin/api/v1/accounts/blocked?max_id=x&limit=",
                "$origin/api/v1/accounts/blocked?max_id=x&limit=40&limit=40",
                "$origin/api/v1/accounts/blocked?max_id=x&since_id=y",
                "${origin.replace("http://", "https://")}/api/v1/accounts/blocked?limit=40&max_id=x",
                server.url("/").newBuilder()
                    .port(if (server.port == 65535) 65534 else 65535)
                    .encodedPath("/api/v1/accounts/blocked")
                    .encodedQuery("limit=40&max_id=x")
                    .build().toString(),
                "${origin.replace("http://", "http://u:p@")}/api/v1/accounts/blocked?limit=40&max_id=x",
            )
            val bad = urls.map { url ->
                val payload = JSONObject(raw.toString()).put("url", url).toString().toByteArray(Charsets.UTF_8)
                valid.copy(value = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload))
            } + listOf(valid.copy(value = "$origin/api/v1/accounts/blocked?max_id=x"), valid.copy(value = "v1/accounts/blocked?max_id=x")) +
                listOf(
                    JSONObject(raw.toString()).put("version", 2),
                    JSONObject(raw.toString()).put("version", "1"),
                    JSONObject(raw.toString()).apply { remove("route") },
                    JSONObject(raw.toString()).put("url", 42),
                ).map { payload ->
                    valid.copy(value = java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(payload.toString().toByteArray(Charsets.UTF_8)))
                }
            bad.forEach { cursor ->
                val error = assertThrows(SourceError.Unsupported::class.java) { runBlocking { service.blocked(cursor) } }
                assertEquals("moderation.cursor", error.feature)
                assertEquals(count, server.requestCount)
            }
            val wrongKind = assertThrows(SourceError.Unsupported::class.java) { runBlocking { service.muted(valid) } }
            assertEquals("moderation.cursor", wrongKind.feature)
            assertEquals(count, server.requestCount)
            val wrongVariant = assertThrows(SourceError.Unsupported::class.java) {
                runBlocking { service.blocked(valid.copy(protocolVariant = "mastodon-blocked-v2")) }
            }
            assertEquals("moderation.cursor", wrongVariant.feature)
            assertEquals(count, server.requestCount)
            listOf("variant" to "other", "route" to "muted").forEach { (key, value) ->
                val payload = JSONObject(raw.toString()).put(key, value).toString().toByteArray(Charsets.UTF_8)
                val tampered = valid.copy(value = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload))
                val error = assertThrows(SourceError.Unsupported::class.java) { runBlocking { service.blocked(tampered) } }
                assertEquals("moderation.cursor", error.feature)
                assertEquals(count, server.requestCount)
            }
            val other = MastodonModerationService(origin, "token", MisskeyApi(), AccountId(Connection(origin, Protocol.MASTODON), "other"))
            assertThrows(SourceError.Unsupported::class.java) { runBlocking { other.blocked(valid) } }
            assertEquals(count, server.requestCount)
        }
    }

    @Test
    fun mastodonModerationRejectsInvalidLinksAndCurrentUrlLoop() {
        runBlocking {
            val origin = server.url("/").toString().removeSuffix("/")
            val service = MastodonModerationService(origin, "token", MisskeyApi(), AccountId(Connection(origin, Protocol.MASTODON), "viewer"))
            server.enqueue(MockResponse().setBody("[]").addHeader("Link", "<$origin/api/v1/accounts/muted?max_id=x&local=true>; rel=\"next\""))
            val invalid = assertThrows(SourceError.Unsupported::class.java) { runBlocking { service.muted() } }
            assertEquals("pagination.link", invalid.feature)
            val count = server.requestCount
            server.enqueue(MockResponse().setBody("[]").addHeader("Link", "<$origin/api/v1/accounts/blocked?limit=40>; rel=\"next\""))
            val loopService = MastodonModerationService(origin, "token", MisskeyApi(), AccountId(Connection(origin, Protocol.MASTODON), "viewer"))
            val loop = assertThrows(SourceError.Unsupported::class.java) { runBlocking { loopService.blocked() } }
            assertEquals("pagination.link", loop.feature)
            assertEquals(count + 1, server.requestCount)
        }
    }

    @Test
    fun mastodonModerationRejectsValuelessLimitLinksAndAllowsMissingLimit() {
        runBlocking {
            val origin = server.url("/").toString().removeSuffix("/")
            val service = MastodonModerationService(origin, "token", MisskeyApi(), AccountId(Connection(origin, Protocol.MASTODON), "viewer"))
            for (query in listOf("max_id=x&limit", "max_id=x&limit=", "max_id=x&limit=40&limit=40")) {
                val count = server.requestCount
                server.enqueue(MockResponse().setBody("[]").addHeader(
                    "Link", "<$origin/api/v1/accounts/blocked?$query>; rel=\"next\"",
                ))
                val error = assertThrows(SourceError.Unsupported::class.java) { runBlocking { service.blocked() } }
                assertEquals("pagination.link", error.feature)
                assertEquals(count + 1, server.requestCount)
            }
            val count = server.requestCount
            server.enqueue(MockResponse().setBody("[]").addHeader(
                "Link", "<$origin/api/v1/accounts/blocked?max_id=x>; rel=\"next\"",
            ))
            assertTrue(service.blocked().nextCursor != null)
            assertEquals(count + 1, server.requestCount)
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
