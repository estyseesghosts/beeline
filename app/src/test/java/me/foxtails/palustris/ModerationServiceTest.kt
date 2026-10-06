package me.foxtails.palustris

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.mastodon.MastodonModerationService
import me.foxtails.palustris.data.mastodon.mastodonTestClient
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.misskey.MisskeyModerationService
import me.foxtails.palustris.data.transport.HttpStatusFailure
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.ModerationCursor
import me.foxtails.palustris.domain.ModerationListKind
import me.foxtails.palustris.domain.ModerationListQuery
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ReportRequest
import me.foxtails.palustris.domain.SourceError
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
            val service = MastodonModerationService(origin, "token", mastodonTestClient(), accountId, 1L, "source-1")
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
            val service = MastodonModerationService(origin, token, mastodonTestClient(), AccountId(Connection(origin, Protocol.MASTODON), "viewer"), 1L, "source-1")
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
            val service = MastodonModerationService(origin, "token", mastodonTestClient(), account, 1L, "source-1")
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
                    JSONObject(raw.toString()).put("version", 3),
                    JSONObject(raw.toString()).put("version", "2"),
                    JSONObject(raw.toString()).put("version", 1).apply {
                        remove("sessionRevision")
                        remove("sourceInstance")
                    },
                    JSONObject(raw.toString()).apply { remove("route") },
                    JSONObject(raw.toString()).apply { remove("sessionRevision") },
                    JSONObject(raw.toString()).apply { remove("sourceInstance") },
                    JSONObject(raw.toString()).put("extra", true),
                    JSONObject(raw.toString()).put("sessionRevision", "1"),
                    JSONObject(raw.toString()).put("sessionRevision", 2),
                    JSONObject(raw.toString()).put("sourceInstance", "source-2"),
                    JSONObject(raw.toString()).put("url", 42),
                ).map { payload ->
                    valid.copy(value = java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(payload.toString().toByteArray(Charsets.UTF_8)))
                } + listOf(valid.copy(value = "not-valid-base64!!!"))
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
            val other = MastodonModerationService(origin, "token", mastodonTestClient(), AccountId(Connection(origin, Protocol.MASTODON), "other"), 1L, "source-1")
            assertThrows(SourceError.Unsupported::class.java) { runBlocking { other.blocked(valid) } }
            assertEquals(count, server.requestCount)
        }
    }

    @Test
    fun mastodonModerationCursorsBindSessionRevisionAndSourceInstance() {
        runBlocking {
            val origin = server.url("/").toString().removeSuffix("/")
            val account = AccountId(Connection(origin, Protocol.MASTODON), "viewer")
            val service = MastodonModerationService(origin, "token", mastodonTestClient(), account, 1L, "source-1")
            server.enqueue(MockResponse().setBody("[${mastodonUser("blocked")}]")
                .addHeader("Link", "<$origin/api/v1/accounts/blocked?limit=40&max_id=x>; rel=\"next\""))
            val cursor = service.blocked().nextCursor!!
            val count = server.requestCount

            // Same account and revision, but another source instance.
            val otherInstance = MastodonModerationService(origin, "token", mastodonTestClient(), account, 1L, "source-2")
            val instanceError = assertThrows(SourceError.Unsupported::class.java) {
                runBlocking { otherInstance.blocked(cursor) }
            }
            assertEquals("moderation.cursor", instanceError.feature)

            // Same account and instance, but a newer session revision.
            val newerRevision = MastodonModerationService(origin, "token", mastodonTestClient(), account, 2L, "source-1")
            val revisionError = assertThrows(SourceError.Unsupported::class.java) {
                runBlocking { newerRevision.blocked(cursor) }
            }
            assertEquals("moderation.cursor", revisionError.feature)

            // Cross-account replay fails before requests.
            val crossAccount = MastodonModerationService(
                origin, "token", mastodonTestClient(),
                AccountId(Connection(origin, Protocol.MASTODON), "other"), 1L, "source-1",
            )
            val accountError = assertThrows(SourceError.Unsupported::class.java) {
                runBlocking { crossAccount.blocked(cursor) }
            }
            assertEquals("moderation.cursor", accountError.feature)

            // Cross-kind replay fails before requests.
            val kindError = assertThrows(SourceError.Unsupported::class.java) {
                runBlocking { service.muted(cursor) }
            }
            assertEquals("moderation.cursor", kindError.feature)

            assertEquals(count, server.requestCount)
        }
    }

    @Test
    fun mastodonModerationRejectsSelfLinkToFirstPage() {
        runBlocking {
            val origin = server.url("/").toString().removeSuffix("/")
            val account = AccountId(Connection(origin, Protocol.MASTODON), "viewer")
            val service = MastodonModerationService(origin, "token", mastodonTestClient(), account, 1L, "source-1")
            // A self-Link to the exact first-page route cannot become a continuation.
            server.enqueue(MockResponse().setBody("[${mastodonUser("blocked")}]")
                .addHeader("Link", "<$origin/api/v1/accounts/blocked?limit=40>; rel=\"next\""))
            val count = server.requestCount
            val error = assertThrows(SourceError.Unsupported::class.java) { runBlocking { service.blocked() } }
            assertEquals("pagination.link", error.feature)
            assertEquals(count + 1, server.requestCount)
        }
    }

    @Test
    fun mastodonModerationRejectsInvalidLinksAndCurrentUrlLoop() {
        runBlocking {
            val origin = server.url("/").toString().removeSuffix("/")
            val service = MastodonModerationService(origin, "token", mastodonTestClient(), AccountId(Connection(origin, Protocol.MASTODON), "viewer"), 1L, "source-1")
            server.enqueue(MockResponse().setBody("[]").addHeader("Link", "<$origin/api/v1/accounts/muted?max_id=x&local=true>; rel=\"next\""))
            val invalid = assertThrows(SourceError.Unsupported::class.java) { runBlocking { service.muted() } }
            assertEquals("pagination.link", invalid.feature)
            val count = server.requestCount
            server.enqueue(MockResponse().setBody("[]").addHeader("Link", "<$origin/api/v1/accounts/blocked?limit=40>; rel=\"next\""))
            val loopService = MastodonModerationService(origin, "token", mastodonTestClient(), AccountId(Connection(origin, Protocol.MASTODON), "viewer"), 1L, "source-1")
            val loop = assertThrows(SourceError.Unsupported::class.java) { runBlocking { loopService.blocked() } }
            assertEquals("pagination.link", loop.feature)
            assertEquals(count + 1, server.requestCount)
        }
    }

    @Test
    fun mastodonModerationRejectsValuelessLimitLinksAndAllowsMissingLimit() {
        runBlocking {
            val origin = server.url("/").toString().removeSuffix("/")
            val service = MastodonModerationService(origin, "token", mastodonTestClient(), AccountId(Connection(origin, Protocol.MASTODON), "viewer"), 1L, "source-1")
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
        val service = MastodonModerationService(origin, "token", mastodonTestClient(), AccountId(Connection(origin, Protocol.MASTODON), "viewer"), 1L, "source-1")
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
        val service = MastodonModerationService(origin, "token", mastodonTestClient(), AccountId(Connection(origin, Protocol.MASTODON), "viewer"), 1L, "source-1")
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
                mastodonTestClient(),
                AccountId(Connection(origin, Protocol.MASTODON), "viewer"),
                1L,
                "source-1",
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
                mastodonTestClient(),
                AccountId(Connection(origin, Protocol.MASTODON), "viewer"),
                1L,
                "source-1",
            )
            val target = AccountId(Connection(origin, Protocol.MASTODON), "target")
            server.enqueue(MockResponse().setBody("{}"))
            server.enqueue(MockResponse().setResponseCode(400).setBody("bad request"))

            val error = assertThrows(HttpStatusFailure::class.java) { runBlocking { service.setMuted(target, true) } }
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
                mastodonTestClient(),
                AccountId(Connection(origin, Protocol.MASTODON), "viewer"),
                1L,
                "source-1",
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
    fun mastodonRejectsInvalidReportTargetsBeforeRequest() {
        val origin = server.url("/").toString().removeSuffix("/")
        val service = MastodonModerationService(
            origin,
            "token",
            mastodonTestClient(),
            AccountId(Connection(origin, Protocol.MASTODON), "viewer"),
            1L,
            "source-1",
        )
        val count = server.requestCount

        // Same origin but Misskey protocol is not a Mastodon report target.
        val wrongProtocol = assertThrows(SourceError.Unsupported::class.java) {
            runBlocking {
                service.report(
                    ReportRequest(
                        AccountId(Connection(origin, Protocol.MISSKEY), "target"),
                        comment = "spam",
                    ),
                )
            }
        }
        assertEquals("moderation.report", wrongProtocol.feature)

        // Blank and whitespace-only target IDs produce no request.
        listOf("", "   ").forEach { localId ->
            val blank = assertThrows(SourceError.Unsupported::class.java) {
                runBlocking {
                    service.report(
                        ReportRequest(
                            AccountId(Connection(origin, Protocol.MASTODON), localId),
                            comment = "spam",
                        ),
                    )
                }
            }
            assertEquals("moderation.report", blank.feature)
        }

        // A foreign account origin keeps the foreign-origin error type.
        val foreign = assertThrows(SourceError.ForeignOrigin::class.java) {
            runBlocking {
                service.report(
                    ReportRequest(
                        AccountId(Connection("https://foreign.example", Protocol.MASTODON), "target"),
                        comment = "spam",
                    ),
                )
            }
        }
        assertEquals("moderation.report", foreign.feature)

        // A foreign status ID keeps the foreign-origin error type.
        val foreignStatus = assertThrows(SourceError.ForeignOrigin::class.java) {
            runBlocking {
                service.report(
                    ReportRequest(
                        AccountId(Connection(origin, Protocol.MASTODON), "target"),
                        EntityId("https://foreign.example", "status"),
                        "spam",
                    ),
                )
            }
        }
        assertEquals("moderation.report", foreignStatus.feature)

        // Blank and whitespace-only status IDs produce no request.
        listOf("", "   ").forEach { value ->
            val blankStatus = assertThrows(SourceError.Unsupported::class.java) {
                runBlocking {
                    service.report(
                        ReportRequest(
                            AccountId(Connection(origin, Protocol.MASTODON), "target"),
                            EntityId(origin, value),
                            "spam",
                        ),
                    )
                }
            }
            assertEquals("moderation.report", blankStatus.feature)
        }

        assertEquals(count, server.requestCount)
    }

    @Test
    fun mastodonSendsAccountOnlyReportsAndEncodesReservedFields() {
        runBlocking {
            val origin = server.url("/").toString().removeSuffix("/")
            val service = MastodonModerationService(
                origin,
                "token",
                mastodonTestClient(),
                AccountId(Connection(origin, Protocol.MASTODON), "viewer"),
                1L,
                "source-1",
            )
            server.enqueue(MockResponse())
            server.enqueue(MockResponse())

            // An account-only report sends no status IDs and no blank comment.
            service.report(
                ReportRequest(
                    AccountId(Connection(origin, Protocol.MASTODON), "target"),
                    comment = "   ",
                ),
            )
            // Reserved characters stay form-encoded, never raw path text.
            service.report(
                ReportRequest(
                    AccountId(Connection(origin, Protocol.MASTODON), "seg/ment"),
                    EntityId(origin, "status"),
                    "a&b=c",
                ),
            )

            val accountOnly = server.takeRequest()
            assertEquals("/api/v1/reports", accountOnly.path)
            val accountOnlyBody = accountOnly.body.readUtf8()
            assertTrue(accountOnlyBody.contains("account_id=target"))
            assertTrue(!accountOnlyBody.contains("status_ids"))
            assertTrue(!accountOnlyBody.contains("comment"))

            val encoded = server.takeRequest()
            assertEquals("/api/v1/reports", encoded.path)
            val encodedBody = encoded.body.readUtf8()
            assertTrue(encodedBody.contains("account_id=seg%2Fment"))
            assertTrue(encodedBody.contains("status_ids%5B%5D=status"))
            assertTrue(encodedBody.contains("comment=a%26b%3Dc"))
            assertEquals(2, server.requestCount)
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
