package me.foxtails.palustris

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.directmessages.DirectMessageRepository
import me.foxtails.palustris.data.directmessages.DirectMessageWriteAuthority
import me.foxtails.palustris.data.directmessages.InMemoryDirectMessageStore
import me.foxtails.palustris.data.mastodon.MastodonSource
import me.foxtails.palustris.data.misskey.CapabilityCache
import me.foxtails.palustris.data.misskey.MISSKEY_MAX_RESPONSE_BYTES
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.misskey.MisskeySource
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ConversationId
import me.foxtails.palustris.domain.ConversationIdentity
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.DirectMessageRequest
import me.foxtails.palustris.domain.DirectThreadRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.ThreadAcquisitionState
import me.foxtails.palustris.domain.ThreadLimitation
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DirectMessageSourceTest {
    private companion object {
        const val FAKE_TOKEN = "secret-token"
    }

    private fun recordedRequest(server: MockWebServer): RecordedRequest =
        server.takeRequest(5, TimeUnit.SECONDS)
            ?: throw AssertionError("Expected a direct-message HTTP request within 5 seconds")

    @Test
    fun misskeySendsSpecifiedRecipientsAndReply() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            server.enqueue(
                MockResponse().setBody(
                    JSONObject().put("createdNote", JSONObject(createdMisskeyNote("sent"))).toString(),
                ),
            )
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            val source = MisskeySource(origin, "token", MisskeyApi(), accountId = owner, capabilityCache = CapabilityCache())

            source.sendDirectMessage(
                DirectMessageRequest(
                    recipients = listOf(AccountId(Connection(origin, Protocol.MISSKEY), "recipient")),
                    text = "Hello",
                    replyTo = EntityId(origin, "parent"),
                ),
            )

            val body = JSONObject(recordedRequest(server).body.readUtf8())
            assertEquals("specified", body.getString("visibility"))
            assertEquals(listOf("recipient"), body.getJSONArray("visibleUserIds").let { values ->
                (0 until values.length()).map(values::getString)
            })
            assertEquals("parent", body.getString("replyId"))
            assertFalse(body.has("chatId"))
        }
    }

    @Test
    fun mastodonSendsDirectStatusWithResolvedRecipientMention() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            server.enqueue(MockResponse().setBody(mastodonAccount("remote", "alice", "alice@example.org").toString()))
            server.enqueue(MockResponse().setBody(mastodonStatus("sent", "direct", "Hello").toString()))
            val owner = AccountId(Connection(origin, Protocol.MASTODON), "owner")
            val recipient = AccountId(Connection(origin, Protocol.MASTODON), "remote")
            val source = MastodonSource(
                origin = origin,
                token = "token",
                api = MisskeyApi(),
                accountId = owner,
                initialCapabilities = ServerCapabilities(),
            )

            source.sendDirectMessage(DirectMessageRequest(listOf(recipient), "Hello"))

            assertEquals("/api/v1/accounts/remote", recordedRequest(server).path)
            val fields = URLDecoder.decode(recordedRequest(server).body.readUtf8(), StandardCharsets.UTF_8.name())
            assertTrue(fields.contains("status=@alice@example.org Hello"))
            assertTrue(fields.contains("visibility=direct"))
        }
    }

    @Test
    fun mastodonConversationListFiltersNonDirectConversations() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val conversation = JSONObject()
                .put("id", "conversation")
                .put("unread", true)
                .put("accounts", org.json.JSONArray().put(mastodonAccount("remote", "alice", "alice@example.org")))
                .put("last_status", mastodonStatus("last", "direct", "Latest"))
            val publicConversation = JSONObject()
                .put("id", "public-conversation")
                .put("accounts", org.json.JSONArray().put(mastodonAccount("public-user", "public", "public@example.org")))
                .put("last_status", mastodonStatus("public-last", "public", "Do not expose"))
            server.enqueue(MockResponse().setBody(org.json.JSONArray().put(conversation).put(publicConversation).toString()))
            val source = mastodonSource(origin)

            val page = source.conversations()

            assertEquals(1, page.items.size)
            assertEquals(true, page.items.single().unread)
            assertEquals("/api/v1/conversations?limit=40", recordedRequest(server).path)
        }
    }

    @Test
    fun mastodonThreadLoadsTheAnchorStatusWithoutListingFirst() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val context = JSONObject()
                .put("ancestors", org.json.JSONArray().put(mastodonStatus("first", "direct", "First")))
                .put("descendants", org.json.JSONArray()
                    .put(mastodonStatus("reply", "direct", "Reply"))
                    .put(mastodonStatus("public-reply", "public", "Do not expose")))
            server.enqueue(MockResponse().setBody(mastodonStatus("last", "direct", "Latest").toString()))
            server.enqueue(MockResponse().setBody(context.toString()))
            val source = mastodonSource(origin)

            val result = source.conversationThread(
                DirectThreadRequest(ConversationId(origin, "conversation"), EntityId(origin, "last")),
            )

            assertEquals(listOf("first", "last", "reply"), result.posts.map { it.id.value })
            assertEquals(null, result.nextCursor)
            assertEquals(me.foxtails.palustris.domain.ThreadAcquisitionState.Finished, result.acquisitionState)
            assertTrue(result.limitations.isEmpty())
            assertEquals("/api/v1/statuses/last", recordedRequest(server).path)
            assertEquals("/api/v1/statuses/last/context", recordedRequest(server).path)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun mastodonThreadRejectsAnInaccessibleAnchor() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            server.enqueue(MockResponse().setResponseCode(404))
            val source = mastodonSource(origin)

            var unsupported = false
            try {
                source.conversationThread(
                    DirectThreadRequest(ConversationId(origin, "conversation"), EntityId(origin, "deleted")),
                )
            } catch (_: me.foxtails.palustris.domain.SourceError.Unsupported) {
                unsupported = true
            }

            assertTrue(unsupported)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun mastodonThreadRejectsAPublicAnchor() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            server.enqueue(MockResponse().setBody(mastodonStatus("last", "public", "Not direct").toString()))
            val source = mastodonSource(origin)

            var unsupported = false
            try {
                source.conversationThread(
                    DirectThreadRequest(ConversationId(origin, "conversation"), EntityId(origin, "last")),
                )
            } catch (_: me.foxtails.palustris.domain.SourceError.Unsupported) {
                unsupported = true
            }

            assertTrue(unsupported)
        }
    }

    @Test
    fun mastodonThreadRejectsAForeignAnchorWithoutARequest() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val source = mastodonSource(origin)

            var unsupported = false
            try {
                source.conversationThread(
                    DirectThreadRequest(
                        ConversationId(origin, "conversation"),
                        EntityId("https://other.example.org", "last"),
                    ),
                )
            } catch (_: me.foxtails.palustris.domain.SourceError.Unsupported) {
                unsupported = true
            }

            assertTrue(unsupported)
            assertEquals(0, server.requestCount)
        }
    }

    private fun mastodonSource(origin: String): MastodonSource = MastodonSource(
        origin,
        "token",
        MisskeyApi(),
        AccountId(Connection(origin, Protocol.MASTODON), "owner"),
        initialCapabilities = ServerCapabilities(),
    )

    private fun misskeySource(
        origin: String,
        owner: AccountId,
        sessionRevision: Long = 0L,
    ): MisskeySource = MisskeySource(
        origin, "token", MisskeyApi(), accountId = owner,
        capabilityCache = CapabilityCache(), sessionRevision = sessionRevision,
    )

    private fun childrenBody(vararg ids: String): String =
        "[${ids.joinToString(",") { createdMisskeyNote(it) }}]"

    private fun childrenOf(parent: String, vararg ids: String): String =
        "[${ids.joinToString(",") { createdMisskeyNote(it, parent) }}]"

    private fun threadCursorJson(cursor: String): JSONObject =
        JSONObject(String(java.util.Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8))

    private fun threadPending(cursor: String): org.json.JSONArray =
        threadCursorJson(cursor).getJSONArray("pending")

    @Test
    fun misskeyThreadLoadsRootAndOneChildrenPageWithExactRequests() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            server.enqueue(MockResponse().setBody(childrenOf("root", "c1", "c2")))
            // Bounded BFS expands each direct child once. Grandchildren are empty.
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody("[]"))
            val source = misskeySource(origin, owner)

            val result = source.conversationThread(
                DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor")),
            )

            assertEquals(listOf("root", "c1", "c2"), result.posts.map { it.id.value })
            assertEquals(null, result.nextCursor)
            assertEquals(ThreadAcquisitionState.Finished, result.acquisitionState)
            assertTrue(result.limitations.isEmpty())
            val show = recordedRequest(server)
            assertEquals("/api/notes/show", show.path)
            assertEquals("root", JSONObject(show.body.readUtf8()).getString("noteId"))
            val children = recordedRequest(server)
            assertEquals("/api/notes/children", children.path)
            val childrenBodyJson = JSONObject(children.body.readUtf8())
            assertEquals("root", childrenBodyJson.getString("noteId"))
            assertEquals(30, childrenBodyJson.getInt("limit"))
            assertFalse(childrenBodyJson.has("untilId"))
            // Grandchild expansions for c1 and c2 follow in transport order.
            assertEquals("c1", JSONObject(recordedRequest(server).body.readUtf8()).getString("noteId"))
            assertEquals("c2", JSONObject(recordedRequest(server).body.readUtf8()).getString("noteId"))
            assertEquals(4, server.requestCount)
        }
    }

    @Test
    fun misskeyThreadPagesChildrenBeyondThirtyWithRawLastId() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            val firstIds = (1..30).map { "c$it" }.toTypedArray()
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            server.enqueue(MockResponse().setBody(childrenOf("root", *firstIds)))
            server.enqueue(MockResponse().setBody(childrenOf("root", "c31", "c32")))
            // The third descendant request expands the first child. It is empty.
            server.enqueue(MockResponse().setBody("[]"))
            val source = misskeySource(origin, owner)

            val result = source.conversationThread(
                DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor")),
            )

            // Bounded BFS uses 3 descendant requests: two root pages plus one
            // child expansion. Remaining grandchildren stay in the continuation.
            assertEquals(33, result.posts.size)
            assertEquals("root", result.posts.first().id.value)
            assertEquals(ThreadAcquisitionState.HasContinuation, result.acquisitionState)
            assertTrue(result.nextCursor != null)
            recordedRequest(server)
            recordedRequest(server)
            val second = recordedRequest(server)
            assertEquals("/api/notes/children", second.path)
            assertEquals("c30", JSONObject(second.body.readUtf8()).getString("untilId"))
            assertEquals(4, server.requestCount)
        }
    }

    @Test
    fun misskeyThreadContinuationLoadsOnlyTheNextBatch() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(1..30).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(31..60).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(61..90).map { "c$it" }.toTypedArray())))
            val source = misskeySource(origin, owner)

            val first = source.conversationThread(
                DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor")),
            )

            assertEquals(ThreadAcquisitionState.HasContinuation, first.acquisitionState)
            assertTrue(first.nextCursor != null)
            // The pending work carries the root pagination with the raw last ID.
            val pending = threadPending(first.nextCursor!!)
            assertTrue((0 until pending.length()).any { index ->
                val item = pending.getJSONObject(index)
                item.getString("parentId") == "root" && item.getString("untilId") == "c90"
            })
            assertEquals(4, server.requestCount)
            repeat(4) { recordedRequest(server) }
            server.enqueue(MockResponse().setBody(childrenOf("root", "c91", "c92")))
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody("[]"))
            val second = source.conversationThread(
                DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor")),
                first.nextCursor,
            )
            assertEquals(listOf("c91", "c92"), second.posts.map { it.id.value })
            // Child expansions for the next grandchildren keep pending work.
            assertEquals(ThreadAcquisitionState.HasContinuation, second.acquisitionState)
            assertTrue(second.nextCursor != null)
            // A continuation never reloads the root or its ancestors.
            val continued = recordedRequest(server)
            assertEquals("/api/notes/children", continued.path)
            assertEquals("c90", JSONObject(continued.body.readUtf8()).getString("untilId"))
            assertEquals(7, server.requestCount)
        }
    }

    @Test
    fun misskeyThreadContinuationRejectsEachIdentityDimensionBeforeRequests() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(1..30).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(31..60).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(61..90).map { "c$it" }.toTypedArray())))
            val original = misskeySource(origin, owner, sessionRevision = 1)
            val cursor = original.conversationThread(
                DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor")),
            ).nextCursor!!
            assertEquals(4, server.requestCount)
            val valid = threadCursorJson(cursor)
            val request = DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor"))
            fun tampered(mutator: (JSONObject) -> Unit): String {
                val copy = JSONObject(valid.toString())
                mutator(copy)
                return java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(copy.toString().toByteArray(Charsets.UTF_8))
            }
            fun tamperedPending(mutator: (JSONObject) -> Unit): String {
                val copy = JSONObject(valid.toString())
                val pending = copy.getJSONArray("pending")
                mutator(pending.getJSONObject(0))
                return java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(copy.toString().toByteArray(Charsets.UTF_8))
            }
            fun tamperedLimitations(mutator: (org.json.JSONArray) -> Unit): String {
                val copy = JSONObject(valid.toString())
                mutator(copy.getJSONArray("limitations"))
                return java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(copy.toString().toByteArray(Charsets.UTF_8))
            }
            fun tamperedAccepted(mutator: (org.json.JSONArray) -> Unit): String {
                val copy = JSONObject(valid.toString())
                mutator(copy.getJSONArray("accepted"))
                return java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(copy.toString().toByteArray(Charsets.UTF_8))
            }
            val invalid = listOf(
                "not-base64!",
                tampered { it.put("account", "other") },
                tampered { it.put("sessionRevision", 2L) },
                tampered { it.put("origin", "https://other.example") },
                tampered { it.put("sourceInstance", "other-instance") },
                tampered { it.put("conversationId", "other-root") },
                tampered { it.put("variant", "other-variant") },
                tampered { it.put("version", 3) },
                tampered { it.put("version", "4") },
                tampered { it.put("extra", "field") },
                tampered { it.remove("pending") },
                tampered { it.remove("loaded") },
                tampered { it.put("loaded", "many") },
                tampered { it.put("loaded", -1) },
                tampered { it.put("loaded", 201) },
                tampered { it.put("loaded", 1.5) },
                tampered { it.put("loaded", 0) },
                tampered { it.remove("accepted") },
                tampered { it.put("accepted", "ids") },
                tampered { it.remove("limitations") },
                tampered { it.put("limitations", "limits") },
                tampered {
                    val overflow = org.json.JSONArray()
                    repeat(65) { overflow.put(JSONObject().put("type", "uncertain")) }
                    it.put("limitations", overflow)
                },
                tampered { it.remove("requestsUsed") },
                tampered { it.put("requestsUsed", "many") },
                tampered { it.put("requestsUsed", -1) },
                tampered { it.put("requestsUsed", 41) },
                tampered { it.put("requestsUsed", 1.5) },
                tampered { it.put("pending", org.json.JSONArray()) },
                tampered { it.put("pending", "work") },
                tampered {
                    val overflow = org.json.JSONArray()
                    repeat(201) { index ->
                        overflow.put(JSONObject().put("parentId", "p$index").put("depth", 1).put("untilId", JSONObject.NULL))
                    }
                    it.put("pending", overflow)
                },
                tamperedPending { it.put("parentId", "") },
                tamperedPending { it.put("parentId", 1) },
                tamperedPending { it.put("depth", 0) },
                tamperedPending { it.put("depth", 11) },
                tamperedPending { it.put("depth", "1") },
                tamperedPending { it.put("untilId", "") },
                tamperedPending { it.put("untilId", 1) },
                tamperedPending { it.put("extra", "field") },
                tamperedAccepted { it.put("duplicate-of-c1") },
                tamperedAccepted { it.put(42) },
                tampered { it.put("accepted", run {
                    val overflow = org.json.JSONArray()
                    repeat(201) { index -> overflow.put("a$index") }
                    overflow
                }) },
                tampered {
                    val accepted = it.getJSONArray("accepted")
                    accepted.put(accepted.getString(0))
                    it.put("loaded", accepted.length())
                },
                tamperedLimitations { it.put(JSONObject().put("type", "batchTime")) },
                tamperedLimitations { it.put(JSONObject().put("type", "branch").put("parentId", "p")) },
                tamperedLimitations { it.put(JSONObject().put("type", "other")) },
                tamperedLimitations { it.put(JSONObject().put("type", "uncertain").put("extra", 1)) },
                tamperedLimitations { it.put(JSONObject().put("type", "node").put("loaded", "many").put("maximum", 200)) },
                tamperedLimitations { it.put(JSONObject().put("type", "node").put("loaded", 1).put("maximum", 199)) },
                tamperedLimitations { it.put(JSONObject().put("type", "pending").put("pending", "many").put("maximum", 200)) },
                tamperedLimitations { it.put(JSONObject().put("type", "pending").put("pending", -1).put("maximum", 200)) },
                tamperedLimitations { it.put(JSONObject().put("type", "pending").put("pending", 201).put("maximum", 200)) },
                tamperedLimitations { it.put(JSONObject().put("type", "pending").put("pending", 1).put("maximum", 199)) },
                tamperedLimitations { it.put(JSONObject().put("type", "pending").put("pending", 1)) },
                tamperedLimitations { it.put(JSONObject().put("type", "pending").put("pending", 1).put("maximum", 200).put("extra", 1)) },
                tamperedLimitations { it.put(JSONObject().put("type", "depth").put("depth", 11).put("maximum", 9)) },
                tamperedLimitations { it.put(JSONObject().put("type", "ancestor").put("loaded", 1).put("maximum", 19)) },
                tamperedLimitations { it.put(JSONObject().put("type", "unavailableParent").put("parentId", "")) },
                tamperedLimitations { it.put(JSONObject().put("type", "unavailableParent")) },
                tamperedLimitations { it.put(JSONObject().put("type", "request").put("used", 41).put("maximum", 40)) },
                tamperedLimitations { it.put(JSONObject().put("type", "request").put("used", 1).put("maximum", 39)) },
                tamperedLimitations { it.put("not-an-object") },
            )
            invalid.forEach { bad ->
                var failure: SourceError? = null
                try {
                    original.conversationThread(request, bad)
                } catch (error: SourceError) {
                    failure = error
                }
                assertEquals(SourceError.Unsupported("direct.thread.continuation"), failure)
                assertEquals(4, server.requestCount)
            }
            // A replaced source owns a new instance identity, so the old cursor is stale.
            val replacement = misskeySource(origin, owner, sessionRevision = 1)
            var failure: SourceError? = null
            try {
                replacement.conversationThread(request, cursor)
            } catch (error: SourceError) {
                failure = error
            }
            assertEquals(SourceError.Unsupported("direct.thread.continuation"), failure)
            assertEquals(4, server.requestCount)
        }
    }

    @Test
    fun misskeyThreadIdlessFullPageKeepsUncertainLimitationWithContinuation() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            val ids = (1..29).map { "c$it" }.toTypedArray()
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            server.enqueue(MockResponse().setBody("[${childrenOf("root", *ids).removeSurrounding("[", "]")},{}]"))
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody("[]"))
            val source = misskeySource(origin, owner)

            val result = source.conversationThread(
                DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor")),
            )

            // The idless full page cannot paginate the root branch, but the
            // enqueued grandchildren keep pending work for a continuation.
            assertTrue(result.nextCursor != null)
            assertEquals(ThreadAcquisitionState.HasContinuation, result.acquisitionState)
            assertTrue(result.limitations.contains(ThreadLimitation.UncertainServerTruncation))
            assertEquals(4, server.requestCount)
        }
    }

    @Test
    fun misskeyThreadNonAdvancingPageKeepsUncertainLimitationWithContinuation() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(1..30).map { "c$it" }.toTypedArray())))
            // The server repeats the same last ID instead of advancing.
            server.enqueue(MockResponse().setBody(childrenOf("root", *(1..30).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody("[]"))
            val source = misskeySource(origin, owner)

            val result = source.conversationThread(
                DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor")),
            )

            assertTrue(result.nextCursor != null)
            assertEquals(ThreadAcquisitionState.HasContinuation, result.acquisitionState)
            assertTrue(result.limitations.contains(ThreadLimitation.UncertainServerTruncation))
            assertEquals(4, server.requestCount)
        }
    }

    @Test
    fun misskeyThreadBoundsAncestorsAtTwenty() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            // A 25-deep reply chain needs only 21 reads: the root plus 20 parents.
            for (index in 0..20) {
                server.enqueue(MockResponse().setBody(createdMisskeyNote("n$index", "n${index + 1}")))
            }
            server.enqueue(MockResponse().setBody("[]"))
            val source = misskeySource(origin, owner)

            val result = source.conversationThread(
                DirectThreadRequest(ConversationId(origin, "n0"), EntityId(origin, "anchor")),
            )

            assertEquals(21, result.posts.size)
            assertEquals("n20", result.posts.first().id.value)
            assertEquals("n0", result.posts[20].id.value)
            assertTrue(result.limitations.any { it is ThreadLimitation.AncestorLimit })
            assertEquals(ThreadAcquisitionState.Limited, result.acquisitionState)
            assertEquals(22, server.requestCount)
        }
    }

    @Test
    fun misskeyThreadUnavailableParentContinuesToChildren() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root", "missing-parent")))
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setBody(childrenOf("root", "c1")))
            server.enqueue(MockResponse().setBody("[]"))
            val source = misskeySource(origin, owner)

            val result = source.conversationThread(
                DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor")),
            )

            assertEquals(listOf("root", "c1"), result.posts.map { it.id.value })
            assertEquals(1, result.limitations.size)
            assertTrue(result.limitations.single() is ThreadLimitation.UnavailableParent)
            assertEquals(ThreadAcquisitionState.Limited, result.acquisitionState)
            assertEquals(null, result.nextCursor)
            assertEquals(4, server.requestCount)
        }
    }

    @Test
    fun misskeyThreadAncestorNetworkFailureRethrowsWithoutLimitation() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root", "missing-parent")))
            server.enqueue(MockResponse().setResponseCode(500).setBody("{\"error\":{\"code\":\"INTERNAL\"}}"))
            val source = misskeySource(origin, owner)

            var failure: SourceError? = null
            try {
                source.conversationThread(
                    DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor")),
                )
            } catch (error: SourceError) {
                failure = error
            }

            // Only structural absence becomes UnavailableParent. A server
            // failure rethrows and never becomes a limitation.
            assertTrue(failure is SourceError.ServerError)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun misskeyThreadDuplicateAcrossPagesDeduplicatesWithinTheBatch() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            val firstIds = (1..30).map { "c$it" }.toTypedArray()
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            server.enqueue(MockResponse().setBody(childrenOf("root", *firstIds)))
            // The next page repeats c30 with a new raw last ID. Identity differs
            // from the pagination cursor, so the widened no-progress guard does
            // not fire, but the repeated post stays deduplicated in the batch.
            server.enqueue(MockResponse().setBody(childrenOf("root", "c30", "c31")))
            server.enqueue(MockResponse().setBody("[]"))
            val source = misskeySource(origin, owner)

            val result = source.conversationThread(
                DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor")),
            )

            val ids = result.posts.map { it.id.value }
            assertEquals(ids.size, ids.distinct().size)
            assertTrue(ids.contains("c30"))
            assertTrue(ids.contains("c31"))
            assertEquals(ThreadAcquisitionState.HasContinuation, result.acquisitionState)
            assertEquals(4, server.requestCount)
        }
    }

    @Test
    fun misskeyThreadDepthBeyondTenRecordsDepthLimit() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(1..30).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(31..60).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(61..90).map { "c$it" }.toTypedArray())))
            val source = misskeySource(origin, owner)
            val request = DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor"))
            val cursor = source.conversationThread(request).nextCursor!!
            assertEquals(4, server.requestCount)
            repeat(4) { recordedRequest(server) }
            // A crafted continuation queues a depth-10 parent. Its child would
            // exceed the fixed depth limit of 10 and must record DepthLimit.
            val decoded = threadCursorJson(cursor)
            val deep = JSONObject(decoded.toString())
            val pending = org.json.JSONArray()
            pending.put(JSONObject().put("parentId", "deep-parent").put("depth", 10).put("untilId", JSONObject.NULL))
            deep.put("pending", pending)
            val deepCursor = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(deep.toString().toByteArray(Charsets.UTF_8))
            server.enqueue(MockResponse().setBody(childrenOf("deep-parent", "grandchild")))

            val result = source.conversationThread(request, deepCursor)

            assertEquals(listOf("grandchild"), result.posts.map { it.id.value })
            assertTrue(result.limitations.any { it is ThreadLimitation.DepthLimit })
            assertEquals(ThreadAcquisitionState.Limited, result.acquisitionState)
            assertEquals(null, result.nextCursor)
        }
    }

    @Test
    fun misskeyThreadIgnoresUnrelatedDirectRow() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            val good = createdMisskeyNote("good", "root")
            val forked = createdMisskeyNote("forked", "other-parent")
            server.enqueue(MockResponse().setBody("[$good,$forked]"))
            server.enqueue(MockResponse().setBody("[]"))
            val source = misskeySource(origin, owner)

            val result = source.conversationThread(
                DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor")),
            )

            assertEquals(listOf("root", "good"), result.posts.map { it.id.value })
            assertEquals(ThreadAcquisitionState.Finished, result.acquisitionState)
            assertTrue(result.limitations.isEmpty())
            assertEquals(3, server.requestCount)
        }
    }

    @Test
    fun misskeyThreadFullNonDirectPageStillAdvancesToDirectChild() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            val publicPage = (1..30).joinToString(",") { index ->
                createdMisskeyNote("n$index").replace("specified", "public")
            }
            server.enqueue(MockResponse().setBody("[$publicPage]"))
            server.enqueue(MockResponse().setBody(childrenOf("root", "good")))
            server.enqueue(MockResponse().setBody("[]"))
            val source = misskeySource(origin, owner)

            val result = source.conversationThread(
                DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor")),
            )

            assertTrue(result.posts.map { it.id.value }.contains("good"))
            assertTrue(result.limitations.contains(ThreadLimitation.UncertainServerTruncation))
            assertEquals(4, server.requestCount)
            recordedRequest(server)
            recordedRequest(server)
            val second = recordedRequest(server)
            assertEquals("n30", JSONObject(second.body.readUtf8()).optString("untilId"))
        }
    }

    @Test
    fun misskeyThreadNodeLimitClearsPendingWork() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(1..100).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(101..200).map { "c$it" }.toTypedArray())))
            val source = misskeySource(origin, owner)

            val result = source.conversationThread(
                DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor")),
            )

            assertEquals(201, result.posts.size)
            assertTrue(result.limitations.any { it is ThreadLimitation.NodeLimit })
            assertTrue(result.limitations.none { it is ThreadLimitation.PendingLimit })
            assertEquals(ThreadAcquisitionState.Limited, result.acquisitionState)
            assertEquals(null, result.nextCursor)
            assertEquals(3, server.requestCount)
        }
    }

    @Test
    fun misskeyThreadPendingFrontierLimitStopsBatchWithoutContinuation() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(1..30).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(31..60).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(61..90).map { "c$it" }.toTypedArray())))
            val source = misskeySource(origin, owner)
            val request = DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor"))

            val first = source.conversationThread(request)

            assertEquals(ThreadAcquisitionState.HasContinuation, first.acquisitionState)
            assertEquals(4, server.requestCount)
            repeat(4) { recordedRequest(server) }
            // Fill the 200-item frontier around one served parent. The two new
            // children stay far below the 200-descendant cap, so only the
            // frontier path can trigger.
            val decoded = JSONObject(threadCursorJson(first.nextCursor!!).toString())
            val fullPending = org.json.JSONArray()
            fullPending.put(JSONObject().put("parentId", "c1").put("depth", 2).put("untilId", JSONObject.NULL))
            repeat(199) { index ->
                fullPending.put(JSONObject().put("parentId", "fill$index").put("depth", 2).put("untilId", JSONObject.NULL))
            }
            decoded.put("pending", fullPending)
            val fullCursor = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(decoded.toString().toByteArray(Charsets.UTF_8))
            server.enqueue(MockResponse().setBody(childrenOf("c1", "n1", "n2", "n3")))

            val second = source.conversationThread(request, fullCursor)

            // The frontier records its actual size, clears pending work, ends
            // the batch with no continuation, and never reports NodeLimit.
            assertTrue(second.limitations.contains(ThreadLimitation.PendingLimit(200, 200)))
            assertTrue(second.limitations.none { it is ThreadLimitation.NodeLimit })
            assertEquals(ThreadAcquisitionState.Limited, second.acquisitionState)
            assertEquals(null, second.nextCursor)
            // One root read, three first-batch children requests, and one
            // frontier request. The batch stops instead of draining the rest.
            assertEquals(5, server.requestCount)
        }
    }

    @Test
    fun misskeyThreadContinuationDoesNotReacceptConversationRoot() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(1..30).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(31..60).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(61..90).map { "c$it" }.toTypedArray())))
            val source = misskeySource(origin, owner)
            val request = DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor"))

            val first = source.conversationThread(request)

            assertEquals(4, server.requestCount)
            repeat(4) { recordedRequest(server) }
            // A hostile page returns the conversation root as a child of c1.
            // The continuation seeds dedup with the root ID, so the row is
            // skipped instead of counted a second time.
            val decoded = JSONObject(threadCursorJson(first.nextCursor!!).toString())
            val singlePending = org.json.JSONArray()
            singlePending.put(JSONObject().put("parentId", "c1").put("depth", 2).put("untilId", JSONObject.NULL))
            decoded.put("pending", singlePending)
            val hostileCursor = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(decoded.toString().toByteArray(Charsets.UTF_8))
            server.enqueue(MockResponse().setBody(childrenOf("c1", "root")))

            val second = source.conversationThread(request, hostileCursor)

            assertTrue(second.posts.isEmpty())
            assertTrue(second.limitations.none { it is ThreadLimitation.NodeLimit })
            assertEquals(null, second.nextCursor)
            assertEquals(5, server.requestCount)
        }
    }

    @Test
    fun misskeyThreadPendingLimitationSurvivesAnEmptyContinuation() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(1..30).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(31..60).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(61..90).map { "c$it" }.toTypedArray())))
            val source = misskeySource(origin, owner)
            val request = DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor"))

            val first = source.conversationThread(request)

            assertEquals(4, server.requestCount)
            repeat(4) { recordedRequest(server) }
            // A carried pending-frontier limitation decodes strictly and keeps
            // the drained terminal call Limited with no continuation.
            val decoded = JSONObject(threadCursorJson(first.nextCursor!!).toString())
            val carried = decoded.getJSONArray("limitations")
            carried.put(JSONObject().put("type", "pending").put("pending", 200).put("maximum", 200))
            val tail = JSONObject(decoded.toString())
            val singlePending = org.json.JSONArray()
            singlePending.put(tail.getJSONArray("pending").getJSONObject(0))
            tail.put("pending", singlePending)
            val carriedCursor = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tail.toString().toByteArray(Charsets.UTF_8))
            server.enqueue(MockResponse().setBody("[]"))

            val second = source.conversationThread(request, carriedCursor)

            assertTrue(second.posts.isEmpty())
            assertTrue(second.limitations.contains(ThreadLimitation.PendingLimit(200, 200)))
            assertEquals(ThreadAcquisitionState.Limited, second.acquisitionState)
            assertEquals(null, second.nextCursor)
            assertEquals(5, server.requestCount)
        }
    }

    @Test
    fun misskeyThreadContinuationRejectsBadLoadedCountBeforeRequests() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(1..30).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(31..60).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(61..90).map { "c$it" }.toTypedArray())))
            val source = misskeySource(origin, owner, sessionRevision = 1)
            val request = DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor"))
            val cursor = source.conversationThread(request).nextCursor!!
            assertEquals(4, server.requestCount)
            val valid = threadCursorJson(cursor)
            fun badLoaded(mutator: (JSONObject) -> Unit): String {
                val copy = JSONObject(valid.toString())
                mutator(copy)
                return java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(copy.toString().toByteArray(Charsets.UTF_8))
            }
            listOf(
                badLoaded { it.remove("loaded") },
                badLoaded { it.put("loaded", "many") },
                badLoaded { it.put("loaded", -1) },
                badLoaded { it.put("loaded", 201) },
                badLoaded { it.put("loaded", 1.5) },
                // Loaded must equal the accepted ID count carried in the cursor.
                badLoaded { it.put("loaded", 0) },
                badLoaded { it.remove("accepted") },
                badLoaded { it.put("accepted", "ids") },
                badLoaded {
                    val accepted = it.getJSONArray("accepted")
                    accepted.put(accepted.getString(0))
                    it.put("loaded", accepted.length())
                },
                badLoaded { it.remove("limitations") },
                badLoaded {
                    val limitations = it.getJSONArray("limitations")
                    limitations.put(JSONObject().put("type", "other"))
                },
                badLoaded { it.remove("requestsUsed") },
                badLoaded { it.put("requestsUsed", 41) },
                badLoaded { it.put("version", 3) },
            ).forEach { bad ->
                var failure: SourceError? = null
                try {
                    source.conversationThread(request, bad)
                } catch (error: SourceError) {
                    failure = error
                }
                assertEquals(SourceError.Unsupported("direct.thread.continuation"), failure)
                assertEquals(4, server.requestCount)
            }
        }
    }

    @Test
    fun misskeyThreadCursorCarriesChainStateWithVersionFour() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(1..30).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(31..60).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(61..90).map { "c$it" }.toTypedArray())))
            val source = misskeySource(origin, owner)

            val result = source.conversationThread(
                DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor")),
            )

            val cursor = threadCursorJson(result.nextCursor!!)
            // The chain cursor binds the aggregate budget and the accepted
            // descendant set. Loaded always equals the accepted ID count.
            assertEquals(4, cursor.getInt("version"))
            assertEquals(
                setOf("version", "variant", "origin", "account", "sessionRevision", "sourceInstance",
                    "conversationId", "loaded", "pending", "accepted", "limitations", "requestsUsed"),
                cursor.keys().asSequence().toSet(),
            )
            assertEquals(90, cursor.getInt("loaded"))
            assertEquals(90, cursor.getJSONArray("accepted").length())
            // One root read plus three children requests.
            assertEquals(4, cursor.getInt("requestsUsed"))
        }
    }

    @Test
    fun misskeyThreadLimitedBatchFollowedByEmptyContinuationStaysLimited() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(1..30).map { "c$it" }.toTypedArray())))
            // The server repeats the same page instead of advancing.
            server.enqueue(MockResponse().setBody(childrenOf("root", *(1..30).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody("[]"))
            val source = misskeySource(origin, owner)
            val request = DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor"))

            val first = source.conversationThread(request)

            assertEquals(ThreadAcquisitionState.HasContinuation, first.acquisitionState)
            assertTrue(first.limitations.contains(ThreadLimitation.UncertainServerTruncation))
            assertEquals(4, server.requestCount)
            // The empty continuation drains one queued grandchild per request.
            // A single-item tail keeps the follow-up to one empty page.
            val decoded = threadCursorJson(first.nextCursor!!)
            val tail = JSONObject(decoded.toString())
            val singlePending = org.json.JSONArray()
            singlePending.put(tail.getJSONArray("pending").getJSONObject(0))
            tail.put("pending", singlePending)
            val tailCursor = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tail.toString().toByteArray(Charsets.UTF_8))
            server.enqueue(MockResponse().setBody("[]"))

            val second = source.conversationThread(request, tailCursor)

            // The terminal call carries the earlier truncation and never
            // reports Finished after prior truncation.
            assertTrue(second.posts.isEmpty())
            assertEquals(null, second.nextCursor)
            assertTrue(second.limitations.contains(ThreadLimitation.UncertainServerTruncation))
            assertEquals(ThreadAcquisitionState.Limited, second.acquisitionState)
            assertEquals(5, server.requestCount)
        }
    }

    @Test
    fun misskeyThreadOverlappingContinuationPagesDoNotInflateLoadedCount() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(1..30).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(31..60).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(61..90).map { "c$it" }.toTypedArray())))
            val source = misskeySource(origin, owner)
            val request = DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor"))

            val first = source.conversationThread(request)

            assertEquals(90, threadCursorJson(first.nextCursor!!).getInt("loaded"))
            assertEquals(4, server.requestCount)
            repeat(4) { recordedRequest(server) }
            // The next root page repeats thirty accepted IDs and adds one new
            // direct child. The repeated rows must not count or re-enqueue.
            val overlapping = "[${(1..30).map { createdMisskeyNote("c$it", "root") }.joinToString(",")}," +
                "${createdMisskeyNote("c91", "root")}]"
            server.enqueue(MockResponse().setBody(overlapping))
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody("[]"))

            val second = source.conversationThread(request, first.nextCursor)

            assertEquals(listOf("c91"), second.posts.map { it.id.value })
            val continued = threadCursorJson(second.nextCursor!!)
            // Loaded stays equal to the distinct accepted descendant count.
            assertEquals(91, continued.getInt("loaded"))
            assertEquals(91, continued.getJSONArray("accepted").length())
            val accepted = (0 until continued.getJSONArray("accepted").length())
                .map { continued.getJSONArray("accepted").getString(it) }
            assertEquals(accepted.size, accepted.distinct().size)
            assertTrue(accepted.contains("c91"))
            // The repeated rows never enqueue a second grandchild expansion.
            val pendingParents = (0 until continued.getJSONArray("pending").length())
                .map { continued.getJSONArray("pending").getJSONObject(it).getString("parentId") }
            assertEquals(pendingParents.size, pendingParents.distinct().size)
            assertEquals(7, server.requestCount)
        }
    }

    @Test
    fun misskeyThreadAdvancingNonDirectPagesReachAnOlderDirectChildAcrossCalls() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            fun publicPage(prefix: String): String =
                "[${(1..30).joinToString(",") { createdMisskeyNote("$prefix$it").replace("specified", "public") }}]"
            server.enqueue(MockResponse().setBody(publicPage("a")))
            server.enqueue(MockResponse().setBody(publicPage("b")))
            server.enqueue(MockResponse().setBody(publicPage("c")))
            val source = misskeySource(origin, owner)
            val request = DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor"))

            val first = source.conversationThread(request)

            // Three full public pages advance the root pagination across the
            // per-call budget without one accepted child.
            assertTrue(first.posts.none { it.id.value == "good" })
            assertEquals(ThreadAcquisitionState.HasContinuation, first.acquisitionState)
            assertTrue(first.limitations.contains(ThreadLimitation.UncertainServerTruncation))
            assertEquals(4, server.requestCount)
            repeat(4) { recordedRequest(server) }
            server.enqueue(MockResponse().setBody(childrenOf("root", "good")))
            server.enqueue(MockResponse().setBody("[]"))

            val second = source.conversationThread(request, first.nextCursor)

            assertTrue(second.posts.map { it.id.value }.contains("good"))
            // The carried truncation survives the successful follow-up page.
            assertTrue(second.limitations.contains(ThreadLimitation.UncertainServerTruncation))
            assertEquals(6, server.requestCount)
        }
    }

    @Test
    fun misskeyThreadChildrenServerFailureThrowsWithoutAFabricatedLimitation() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            server.enqueue(MockResponse().setResponseCode(500).setBody("{\"error\":{\"code\":\"INTERNAL\"}}"))
            val source = misskeySource(origin, owner)

            var failure: SourceError? = null
            try {
                source.conversationThread(
                    DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor")),
                )
            } catch (error: SourceError) {
                failure = error
            }

            // A failed children request normalizes to ServerError. It never
            // becomes an UncertainServerTruncation limitation.
            assertTrue(failure is SourceError.ServerError)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun misskeyThreadTotalRequestLimitStopsTheChainWithoutContinuation() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(1..30).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(31..60).map { "c$it" }.toTypedArray())))
            server.enqueue(MockResponse().setBody(childrenOf("root", *(61..90).map { "c$it" }.toTypedArray())))
            val source = misskeySource(origin, owner)
            val request = DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor"))

            val first = source.conversationThread(request)

            assertEquals(ThreadAcquisitionState.HasContinuation, first.acquisitionState)
            assertEquals(4, server.requestCount)
            repeat(4) { recordedRequest(server) }
            // Move the chain budget near the 40-request total. The carried
            // accepted set and loaded count stay consistent.
            val decoded = threadCursorJson(first.nextCursor!!)
            val nearCap = JSONObject(decoded.toString())
            nearCap.put("requestsUsed", 38)
            val nearCapCursor = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(nearCap.toString().toByteArray(Charsets.UTF_8))
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody("[]"))

            val capped = source.conversationThread(request, nearCapCursor)

            // The 40th successful request stops the chain with RequestLimit
            // and empty pending work, even though pages keep succeeding.
            assertEquals(null, capped.nextCursor)
            assertTrue(capped.limitations.any { it == ThreadLimitation.RequestLimit(40, 40) })
            assertEquals(ThreadAcquisitionState.Limited, capped.acquisitionState)
            assertEquals(6, server.requestCount)
        }
    }

    @Test
    fun mastodonThreadRejectsNonNullCursorBeforeAnyRequest() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val source = mastodonSource(origin)

            var failure: SourceError? = null
            try {
                source.conversationThread(
                    DirectThreadRequest(ConversationId(origin, "conversation"), EntityId(origin, "last")),
                    "opaque-cursor",
                )
            } catch (error: SourceError) {
                failure = error
            }

            assertEquals(SourceError.Unsupported("direct.thread.continuation"), failure)
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun misskeyThreadOversizedChildrenResponseKeepsDirectThreadLabel() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody(createdMisskeyNote("root")))
            server.enqueue(MockResponse().setBody(" ".repeat((MISSKEY_MAX_RESPONSE_BYTES + 1).toInt())))
            val source = misskeySource(origin, owner)

            var failure: SourceError? = null
            try {
                source.conversationThread(
                    DirectThreadRequest(ConversationId(origin, "root"), EntityId(origin, "anchor")),
                )
            } catch (error: SourceError) {
                failure = error
            }

            assertEquals(SourceError.ResourceLimit("direct.thread"), failure)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun mastodonRepliesMentionAllSuppliedParticipantsAndMarksServerConversationRead() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            server.enqueue(MockResponse().setBody(mastodonAccount("one", "alice", "alice@example.org").toString()))
            server.enqueue(MockResponse().setBody(mastodonAccount("two", "bob", "bob@example.org").toString()))
            server.enqueue(MockResponse().setBody(mastodonStatus("sent", "direct", "Reply").toString()))
            server.enqueue(MockResponse().setBody("{}"))
            val source = MastodonSource(
                origin,
                "token",
                MisskeyApi(),
                AccountId(Connection(origin, Protocol.MASTODON), "owner"),
                initialCapabilities = ServerCapabilities(),
            )

            source.sendDirectMessage(
                DirectMessageRequest(
                    recipients = listOf(
                        AccountId(Connection(origin, Protocol.MASTODON), "one"),
                        AccountId(Connection(origin, Protocol.MASTODON), "two"),
                    ),
                    text = "Reply",
                    replyTo = EntityId(origin, "parent"),
                ),
            )
            recordedRequest(server)
            recordedRequest(server)
            val fields = URLDecoder.decode(recordedRequest(server).body.readUtf8(), StandardCharsets.UTF_8.name())
            source.markConversationRead(ConversationId(origin, "conversation"))

            assertTrue(fields.contains("status=@alice@example.org @bob@example.org Reply"))
            assertTrue(fields.contains("in_reply_to_id=parent"))
            assertEquals("/api/v1/conversations/conversation/read", recordedRequest(server).path)
        }
    }

    @Test
    fun misskeySpecifiedNotesBecomeReplyRootedConversations() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("root")},${createdMisskeyNote("public").replace("specified", "public")}]"))
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("reply", "root")}]"))
            val source = MisskeySource(origin, "token", MisskeyApi(), accountId = owner, capabilityCache = CapabilityCache())

            val conversation = source.conversations().items.single()

            assertEquals("root", conversation.id.value)
            assertEquals("root", conversation.rootPostId?.value)
            assertEquals("reply", conversation.lastPost.id.value)
            assertEquals(ConversationIdentity.Verified, conversation.identity)
            assertEquals("/api/notes/mentions", recordedRequest(server).path)
            assertEquals("/api/users/notes", recordedRequest(server).path)
        }
    }

    @Test
    fun misskeyInboxStreamsHaveIndependentCursorValuesAndPreserveMappedTransportItems() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("mentioned")}]"))
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("sent")}]"))
            val source = MisskeySource(origin, FAKE_TOKEN, MisskeyApi(), accountId = owner, capabilityCache = CapabilityCache())

            val page = source.conversations()

            assertEquals(listOf("mentioned", "sent"), page.items.map { it.lastPost.id.value })
            assertEquals(listOf("mentioned", "sent"), page.items.map { it.lastPost.text })
            assertEquals(ConversationIdentity.Verified, page.items.first().identity)
            val mentions = recordedRequest(server)
            val sent = recordedRequest(server)
            assertEquals("POST", mentions.method)
            assertEquals("/api/notes/mentions", mentions.path)
            assertEquals("{\"i\":\"$FAKE_TOKEN\",\"limit\":30,\"markAsRead\":false}", mentions.body.readUtf8())
            assertEquals("POST", sent.method)
            assertEquals("/api/users/notes", sent.path)
            assertEquals("{\"i\":\"$FAKE_TOKEN\",\"limit\":30,\"markAsRead\":false,\"userId\":\"owner\",\"includeReplies\":true}", sent.body.readUtf8())
            assertTrue(page.nextCursor != null)
            val cursorId = page.nextCursor!!.let { java.util.Base64.getUrlDecoder().decode(it).toString(Charsets.UTF_8) }
                .let { JSONObject(it) }
            assertEquals("mentioned", cursorId.getString("mentionsUntilId"))
            assertEquals("sent", cursorId.getString("sentUntilId"))
        }
    }

    @Test
    fun misskeyInboxCursorKeepsPerStreamProgressAndUsesEndpointOrderInsteadOfTimestamp() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("m-new").replace("10:00", "12:00")},${createdMisskeyNote("m-old").replace("10:00", "09:00")}]"))
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("s-new").replace("10:00", "11:00")}]"))
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody("[]"))
            val source = MisskeySource(origin, FAKE_TOKEN, MisskeyApi(), accountId = owner, capabilityCache = CapabilityCache())
            val first = source.conversations()
            assertEquals(listOf("m-new", "m-old", "s-new"), first.items.map { it.lastPost.id.value })
            source.conversations(first.nextCursor)

            val mentionsFirst = recordedRequest(server)
            val sentFirst = recordedRequest(server)
            assertEquals("POST", mentionsFirst.method)
            assertEquals("POST", sentFirst.method)
            val mentionsNext = recordedRequest(server)
            val sentNext = recordedRequest(server)
            val cursor = first.nextCursor!!.let { java.util.Base64.getUrlDecoder().decode(it).toString(Charsets.UTF_8) }
                .let(::JSONObject)
            assertEquals("m-old", cursor.getString("mentionsUntilId"))
            assertEquals("s-new", cursor.getString("sentUntilId"))
            assertEquals("POST", mentionsNext.method)
            assertEquals("POST", sentNext.method)
            assertEquals("{\"i\":\"$FAKE_TOKEN\",\"limit\":30,\"markAsRead\":false,\"untilId\":\"m-old\"}", mentionsNext.body.readUtf8())
            assertEquals("{\"i\":\"$FAKE_TOKEN\",\"limit\":30,\"markAsRead\":false,\"untilId\":\"s-new\",\"userId\":\"owner\",\"includeReplies\":true}", sentNext.body.readUtf8())
            assertEquals("/api/notes/mentions", mentionsNext.path)
            assertEquals("/api/users/notes", sentNext.path)
        }
    }

    @Test
    fun misskeyInboxAdvancesFromRawLastIdWhenEveryItemIsFiltered() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val raw = JSONObject(createdMisskeyNote("raw-public")).put("visibility", "public")
            server.enqueue(MockResponse().setBody("[$raw]"))
            server.enqueue(MockResponse().setBody("[]"))
            val source = MisskeySource(origin, FAKE_TOKEN, MisskeyApi(), accountId = AccountId(Connection(origin, Protocol.MISSKEY), "owner"), capabilityCache = CapabilityCache())
            val first = source.conversations()
            assertTrue(first.items.isEmpty())
            val cursor = JSONObject(String(java.util.Base64.getUrlDecoder().decode(first.nextCursor!!)))
            assertEquals("raw-public", cursor.getString("mentionsUntilId"))
            assertFalse(cursor.getBoolean("mentionsExhausted"))
            assertTrue(cursor.getBoolean("sentExhausted"))
            server.enqueue(MockResponse().setBody("[]"))
            val second = source.conversations(first.nextCursor)
            assertEquals(null, second.nextCursor)
            assertEquals("/api/notes/mentions", recordedRequest(server).path)
            assertEquals("/api/users/notes", recordedRequest(server).path)
            val mentionsNext = recordedRequest(server)
            assertEquals("/api/notes/mentions", mentionsNext.path)
            assertEquals("raw-public", JSONObject(mentionsNext.body.readUtf8()).getString("untilId"))
            assertEquals(3, server.requestCount)
        }
    }

    @Test
    fun misskeyInboxIdlessLastItemExhaustsOnlyItsStreamWithoutReplay() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("mention")},{}]"))
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("sent-1")}]"))
            val source = MisskeySource(origin, FAKE_TOKEN, MisskeyApi(), accountId = AccountId(Connection(origin, Protocol.MISSKEY), "owner"), capabilityCache = CapabilityCache())
            val first = source.conversations()
            assertEquals(listOf("mention", "sent-1"), first.items.map { it.lastPost.id.value })
            val cursor = JSONObject(String(java.util.Base64.getUrlDecoder().decode(first.nextCursor!!)))
            assertTrue(cursor.getBoolean("mentionsExhausted"))
            assertFalse(cursor.getBoolean("sentExhausted"))
            assertEquals("sent-1", cursor.getString("sentUntilId"))
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("sent-2")}]"))
            val second = source.conversations(first.nextCursor)
            assertEquals(listOf("sent-2"), second.items.map { it.lastPost.id.value })
            assertEquals("/api/notes/mentions", recordedRequest(server).path)
            assertEquals("/api/users/notes", recordedRequest(server).path)
            val sentNext = recordedRequest(server)
            assertEquals("/api/users/notes", sentNext.path)
            assertEquals("sent-1", JSONObject(sentNext.body.readUtf8()).getString("untilId"))
            assertEquals(3, server.requestCount)
        }
    }

    @Test
    fun misskeyInboxIdlessLastItemsStopBothStreams() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("mention")},{}]"))
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("sent")},{}]"))
            val source = MisskeySource(origin, FAKE_TOKEN, MisskeyApi(), accountId = AccountId(Connection(origin, Protocol.MISSKEY), "owner"), capabilityCache = CapabilityCache())

            val page = source.conversations()

            assertEquals(listOf("mention", "sent"), page.items.map { it.lastPost.id.value })
            assertEquals(null, page.nextCursor)
            assertEquals("/api/notes/mentions", recordedRequest(server).path)
            assertEquals("/api/users/notes", recordedRequest(server).path)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun misskeyInboxDeduplicatesAfterConcatenatingMentionStreamFirst() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val note = createdMisskeyNote("same")
            val sentCopy = JSONObject(note).put("text", "sent copy").put("createdAt", "2026-09-06T12:00:00Z")
            server.enqueue(MockResponse().setBody("[$note]"))
            server.enqueue(MockResponse().setBody("[$sentCopy,${createdMisskeyNote("sent-only")}]"))
            val source = MisskeySource(origin, FAKE_TOKEN, MisskeyApi(), accountId = AccountId(Connection(origin, Protocol.MISSKEY), "owner"), capabilityCache = CapabilityCache())

            val page = source.conversations()

            assertEquals(listOf("same", "sent-only"), page.items.map { it.lastPost.id.value })
            assertEquals("same", page.items.first().lastPost.text)
            assertEquals(10 * 60 * 60 * 1000L, page.items.first().lastPost.publishedAtEpochMillis % (24 * 60 * 60 * 1000L))
            assertEquals("/api/notes/mentions", recordedRequest(server).path)
            assertEquals("/api/users/notes", recordedRequest(server).path)
        }
    }

    @Test
    fun misskeyInboxMalformedAndLegacyCursorsFailBeforeRequests() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            val source = MisskeySource(origin, FAKE_TOKEN, MisskeyApi(), accountId = owner, capabilityCache = CapabilityCache())
            val legacy = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                JSONObject().put("origin", origin).put("account", "owner").put("untilId", "old").toString().toByteArray(),
            )

            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("mention")}]"))
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("sent")}]"))
            val valid = JSONObject(String(java.util.Base64.getUrlDecoder().decode(source.conversations().nextCursor!!)))
            val stringVersion = JSONObject(valid.toString()).put("version", "2")
            val fractionalRevision = JSONObject(valid.toString()).put("sessionRevision", 0.5)
            val stringRevision = JSONObject(valid.toString()).put("sessionRevision", "0")
            val longString = JSONObject(valid.toString()).put("origin", 1L)
            val wrongId = JSONObject(valid.toString()).put("mentionsUntilId", true)
            val wrongExhaustion = JSONObject(valid.toString()).put("mentionsExhausted", "false")
            val invalid = listOf("not-base64!", legacy, encodeCursor(stringVersion), encodeCursor(fractionalRevision), encodeCursor(stringRevision), encodeCursor(longString), encodeCursor(wrongId), encodeCursor(wrongExhaustion))
            invalid.forEach { cursor ->
                var failure: SourceError? = null
                try { source.conversations(cursor) } catch (error: SourceError) { failure = error }
                assertEquals(SourceError.Unsupported("direct.pagination"), failure)
                assertEquals(2, server.requestCount)
            }
        }
    }

    @Test
    fun misskeyInboxReplyWithoutReturnedRootIsProvisional() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("reply", "missing-root")}]"))
            server.enqueue(MockResponse().setBody("[]"))
            val source = MisskeySource(origin, FAKE_TOKEN, MisskeyApi(), accountId = owner, capabilityCache = CapabilityCache())

            val conversation = source.conversations().items.single()

            assertEquals(ConversationId(origin, "reply"), conversation.id)
            assertEquals(EntityId(origin, "reply"), conversation.rootPostId)
            assertEquals("reply", conversation.lastPost.id.value)
            assertEquals(ConversationIdentity.Provisional, conversation.identity)
            assertEquals("/api/notes/mentions", recordedRequest(server).path)
            assertEquals("/api/users/notes", recordedRequest(server).path)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun misskeyInboxCursorRejectsEachIdentityDimensionIndependentlyBeforeRequests() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("mention")}]"))
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("sent")}]"))
            val original = MisskeySource(origin, FAKE_TOKEN, MisskeyApi(), accountId = owner, capabilityCache = CapabilityCache(), sessionRevision = 1)
            val cursor = original.conversations().nextCursor!!
            val valid = JSONObject(String(java.util.Base64.getUrlDecoder().decode(cursor)))
            val otherAccount = JSONObject(valid.toString()).put("account", "other")
            var failure: SourceError? = null
            try { original.conversations(encodeCursor(otherAccount)) } catch (error: SourceError) { failure = error }
            assertEquals(SourceError.Unsupported("direct.pagination"), failure)
            assertEquals(2, server.requestCount)

            val otherRevision = JSONObject(valid.toString()).put("sessionRevision", 2L)
            failure = null
            try { original.conversations(encodeCursor(otherRevision)) } catch (error: SourceError) { failure = error }
            assertEquals(SourceError.Unsupported("direct.pagination"), failure)
            assertEquals(2, server.requestCount)

            val replacement = MisskeySource(origin, FAKE_TOKEN, MisskeyApi(), accountId = owner, capabilityCache = CapabilityCache(), sessionRevision = 1)
            failure = null
            try { replacement.conversations(cursor) } catch (error: SourceError) { failure = error }
            assertEquals(SourceError.Unsupported("direct.pagination"), failure)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun misskeyInboxFallsBackToNotificationsWhenMentionsIsNotFound() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setBody("[${notification("notification-1", createdMisskeyNote("note-1"))}]"))
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody("[${notification("notification-2", createdMisskeyNote("note-2"))}]"))
            val source = MisskeySource(origin, FAKE_TOKEN, MisskeyApi(), accountId = owner, capabilityCache = CapabilityCache())

            val first = source.conversations()
            assertEquals(listOf("note-1"), first.items.map { it.lastPost.id.value })

            assertEquals("/api/notes/mentions", recordedRequest(server).path)
            val fallback = recordedRequest(server)
            assertEquals("/api/i/notifications", fallback.path)
            assertTrue(fallback.body.readUtf8().contains("\"includeTypes\":[\"mention\",\"reply\"]"))
            assertEquals("/api/users/notes", recordedRequest(server).path)
            assertEquals(3, server.requestCount)
            val cursor = JSONObject(String(java.util.Base64.getUrlDecoder().decode(first.nextCursor!!)))
            assertEquals("notifications", cursor.getString("mentionsSource"))
            assertEquals(JSONObject.NULL, cursor.get("mentionsUntilId"))
            assertEquals("notification-1", cursor.getString("mentionsFallbackUntilId"))
            assertTrue(cursor.getBoolean("sentExhausted"))
            val second = source.conversations(first.nextCursor)
            assertEquals(listOf("note-2"), second.items.map { it.lastPost.id.value })
            val fallbackNext = recordedRequest(server)
            assertEquals("/api/i/notifications", fallbackNext.path)
            assertEquals("notification-1", JSONObject(fallbackNext.body.readUtf8()).getString("untilId"))
            assertEquals(4, server.requestCount)
            val secondCursor = JSONObject(String(java.util.Base64.getUrlDecoder().decode(second.nextCursor!!)))
            assertEquals("notification-2", secondCursor.getString("mentionsFallbackUntilId"))
            assertTrue(secondCursor.getBoolean("sentExhausted"))
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody("[]"))
            val fresh = source.conversations()
            assertEquals(null, fresh.nextCursor)
            assertEquals("/api/notes/mentions", recordedRequest(server).path)
            assertEquals("/api/users/notes", recordedRequest(server).path)
            assertEquals(6, server.requestCount)
        }
    }

    @Test
    fun misskeyInboxSkipsExhaustedStreamsAndStopsWithNullCursor() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val source = MisskeySource(origin, FAKE_TOKEN, MisskeyApi(), accountId = AccountId(Connection(origin, Protocol.MISSKEY), "owner"), capabilityCache = CapabilityCache())
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("sent-1")}]"))
            val first = source.conversations()
            assertEquals("/api/notes/mentions", recordedRequest(server).path)
            assertEquals("/api/users/notes", recordedRequest(server).path)
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("sent-2")}]"))
            val second = source.conversations(first.nextCursor)
            val sentRequest = recordedRequest(server)
            assertEquals("/api/users/notes", sentRequest.path)
            assertEquals("{\"i\":\"$FAKE_TOKEN\",\"limit\":30,\"markAsRead\":false,\"untilId\":\"sent-1\",\"userId\":\"owner\",\"includeReplies\":true}", sentRequest.body.readUtf8())
            server.enqueue(MockResponse().setBody("[]"))
            val third = source.conversations(second.nextCursor)
            assertEquals("/api/users/notes", recordedRequest(server).path)
            assertEquals(null, third.nextCursor)
            assertEquals(4, server.requestCount)
        }
    }

    @Test
    fun misskeyReadValidationNoOpMakesNoRequestEvenWhenRepeated() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val source = MisskeySource(
                origin, FAKE_TOKEN, MisskeyApi(),
                accountId = AccountId(Connection(origin, Protocol.MISSKEY), "owner"),
                capabilityCache = CapabilityCache(),
            )

            repeat(2) { source.markConversationRead(ConversationId(origin, "root")) }
            listOf(
                ConversationId(origin, " "),
                ConversationId("https://foreign.example", "root"),
            ).forEach { invalid ->
                var failure: SourceError? = null
                try {
                    source.markConversationRead(invalid)
                } catch (error: SourceError) {
                    failure = error
                }
                assertEquals(SourceError.Unsupported("direct.read"), failure)
            }

            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun misskeyInboxServerFailureMapsWithoutLeakingToken() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            server.enqueue(MockResponse().setResponseCode(503).setBody("{\"error\":{\"code\":\"TEMPORARY\"},\"token\":\"$FAKE_TOKEN\"}"))
            val source = MisskeySource(
                origin, FAKE_TOKEN, MisskeyApi(),
                accountId = AccountId(Connection(origin, Protocol.MISSKEY), "owner"),
                capabilityCache = CapabilityCache(),
            )

            var failure: Exception? = null
            try {
                source.conversations()
            } catch (error: Exception) {
                failure = error
            }

            assertTrue(failure is SourceError.ServerError)
            var cause: Throwable? = failure
            while (cause != null) {
                assertFalse(cause.message.orEmpty().contains(FAKE_TOKEN))
                cause = cause.cause
            }
            val request = recordedRequest(server)
            assertEquals("POST", request.method)
            assertEquals("/api/notes/mentions", request.path)
        }
    }

    @Test
    fun repositoryKeepsConversationStateSeparateByAccount() = runBlocking {
        val origin = "https://example.org"
        val first = AccountId(Connection(origin, Protocol.MISSKEY), "first")
        val second = AccountId(Connection(origin, Protocol.MISSKEY), "second")
        val store = InMemoryDirectMessageStore()
        val message = Account(first, "First", "@first@example.org")
        val source = object : me.foxtails.palustris.domain.DirectMessageSource {
            override suspend fun conversations(cursor: String?) = Page<DirectConversation>(emptyList())
            override suspend fun conversationThread(request: DirectThreadRequest, cursor: String?) =
                me.foxtails.palustris.domain.DirectThreadResult(emptyList())
            override suspend fun sendDirectMessage(request: DirectMessageRequest) =
                post(EntityId(origin, "post"), message)
            override suspend fun markConversationRead(id: ConversationId) = Unit
        }
        val authority = DirectMessageWriteAuthority()
        val generation = authority.activate(first)
        val repository = DirectMessageRepository(first, source, store, generation, authority = authority)

        repository.send(DirectMessageRequest(listOf(second), "Private"))

        assertEquals(1, store.conversations(first).size)
        assertTrue(store.conversations(second).isEmpty())
    }

    @Test
    fun repositoryPreservesLocalReadStateWhenTheSameRemoteConversationIsRefetched() = runBlocking {
        val origin = "https://example.org"
        val account = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
        val recipient = Account(account.copy(localId = "recipient"), "Recipient", "@recipient@example.org")
        val lastPost = post(EntityId(origin, "last"), recipient)
        val conversation = DirectConversation(
            id = ConversationId(origin, "root"),
            participants = listOf(recipient),
            lastPost = lastPost,
            unread = true,
            rootPostId = lastPost.id,
            identity = ConversationIdentity.Verified,
        )
        val source = object : me.foxtails.palustris.domain.DirectMessageSource {
            override suspend fun conversations(cursor: String?) = Page(listOf(conversation))
            override suspend fun conversationThread(request: DirectThreadRequest, cursor: String?) =
                me.foxtails.palustris.domain.DirectThreadResult(listOf(lastPost))
            override suspend fun sendDirectMessage(request: DirectMessageRequest) = lastPost
            override suspend fun markConversationRead(id: ConversationId) = Unit
        }
        val store = InMemoryDirectMessageStore()
        val authority = DirectMessageWriteAuthority()
        val generation = authority.activate(account)
        val repository = DirectMessageRepository(account, source, store, generation, authority = authority)

        repository.conversations()
        store.markRead(account, conversation.id)
        repository.conversations()

        assertFalse(store.conversations(account).single().unread)
    }

    private fun createdMisskeyNote(id: String, replyId: String? = null): String = JSONObject()
        .put("id", id)
        .put("createdAt", if (id == "reply") "2026-09-06T11:00:00Z" else "2026-09-06T10:00:00Z")
        .put("user", JSONObject().put("id", "remote").put("username", "alice").put("host", JSONObject.NULL))
        .put("text", id)
        .put("visibility", "specified")
        .apply { replyId?.let { put("replyId", it) } }
        .toString()

    private fun notification(id: String, note: String): String = JSONObject()
        .put("id", id).put("type", "mention").put("note", JSONObject(note)).toString()

    private fun encodeCursor(value: JSONObject): String = java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toString().toByteArray(Charsets.UTF_8))

    private fun mastodonAccount(id: String, username: String, acct: String): JSONObject = JSONObject()
        .put("id", id)
        .put("username", username)
        .put("acct", acct)
        .put("display_name", username)
        .put("note", "")

    private fun mastodonStatus(id: String, visibility: String, text: String): JSONObject = JSONObject()
        .put("id", id)
        .put("created_at", "2026-09-06T10:00:00Z")
        .put("account", mastodonAccount("remote", "alice", "alice@example.org"))
        .put("content", "<p>$text</p>")
        .put("visibility", visibility)

    private fun post(id: EntityId, author: Account): me.foxtails.palustris.domain.Post =
        me.foxtails.palustris.domain.Post(id, author, "Private", 0L, me.foxtails.palustris.domain.Audience.Direct)
}
