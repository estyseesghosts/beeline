package me.foxtails.palustris

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.directmessages.DirectMessageRepository
import me.foxtails.palustris.data.directmessages.DirectMessageWriteAuthority
import me.foxtails.palustris.data.directmessages.InMemoryDirectMessageStore
import me.foxtails.palustris.data.mastodon.MastodonSource
import me.foxtails.palustris.data.misskey.CapabilityCache
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
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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

            val body = JSONObject(server.takeRequest().body.readUtf8())
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

            assertEquals("/api/v1/accounts/remote", server.takeRequest().path)
            val fields = URLDecoder.decode(server.takeRequest().body.readUtf8(), StandardCharsets.UTF_8.name())
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
            assertEquals("/api/v1/conversations?limit=40", server.takeRequest().path)
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

            val thread = source.conversationThread(
                DirectThreadRequest(ConversationId(origin, "conversation"), EntityId(origin, "last")),
            )

            assertEquals(listOf("first", "last", "reply"), thread.map { it.id.value })
            assertEquals("/api/v1/statuses/last", server.takeRequest().path)
            assertEquals("/api/v1/statuses/last/context", server.takeRequest().path)
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
            server.takeRequest()
            server.takeRequest()
            val fields = URLDecoder.decode(server.takeRequest().body.readUtf8(), StandardCharsets.UTF_8.name())
            source.markConversationRead(ConversationId(origin, "conversation"))

            assertTrue(fields.contains("status=@alice@example.org @bob@example.org Reply"))
            assertTrue(fields.contains("in_reply_to_id=parent"))
            assertEquals("/api/v1/conversations/conversation/read", server.takeRequest().path)
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
            assertEquals("/api/notes/mentions", server.takeRequest().path)
            assertEquals("/api/users/notes", server.takeRequest().path)
        }
    }

    @Test
    fun misskeyInboxStreamsShareUntilIdAndPreserveMappedTransportItems() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("mentioned")}]"))
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("sent")}]"))
            val source = MisskeySource(origin, FAKE_TOKEN, MisskeyApi(), accountId = owner, capabilityCache = CapabilityCache())

            val page = source.conversations()

            assertEquals(listOf("mentioned", "sent"), page.items.map { it.lastPost.id.value })
            assertEquals(listOf("mentioned", "sent"), page.items.map { it.lastPost.text })
            val mentions = server.takeRequest()
            val sent = server.takeRequest()
            assertEquals("POST", mentions.method)
            assertEquals("/api/notes/mentions", mentions.path)
            assertEquals("{\"i\":\"$FAKE_TOKEN\",\"limit\":30,\"markAsRead\":false}", mentions.body.readUtf8())
            assertEquals("POST", sent.method)
            assertEquals("/api/users/notes", sent.path)
            assertEquals("{\"i\":\"$FAKE_TOKEN\",\"limit\":30,\"markAsRead\":false,\"userId\":\"owner\",\"includeReplies\":true}", sent.body.readUtf8())
            assertTrue(page.nextCursor != null)
            val cursorId = page.nextCursor!!.let { java.util.Base64.getUrlDecoder().decode(it).toString(Charsets.UTF_8) }
                .let { JSONObject(it).getString("untilId") }
            assertEquals("mentioned", cursorId)
        }
    }

    @Test
    fun misskeyInboxCursorFansOutSameUntilIdAndMergesByTimestamp() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("m-new").replace("10:00", "12:00")},${createdMisskeyNote("m-old").replace("10:00", "09:00")}]"))
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("s-new").replace("10:00", "11:00")}]"))
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody("[]"))
            val source = MisskeySource(origin, FAKE_TOKEN, MisskeyApi(), accountId = owner, capabilityCache = CapabilityCache())
            val first = source.conversations()
            assertEquals(listOf("m-new", "s-new", "m-old"), first.items.map { it.lastPost.id.value })
            source.conversations(first.nextCursor)

            val mentionsFirst = server.takeRequest()
            val sentFirst = server.takeRequest()
            assertEquals("POST", mentionsFirst.method)
            assertEquals("POST", sentFirst.method)
            val mentionsNext = server.takeRequest()
            val sentNext = server.takeRequest()
            val cursorId = first.nextCursor!!.let { java.util.Base64.getUrlDecoder().decode(it).toString(Charsets.UTF_8) }
                .let { JSONObject(it).getString("untilId") }
            assertEquals("m-old", cursorId)
            assertEquals("POST", mentionsNext.method)
            assertEquals("POST", sentNext.method)
            assertEquals("{\"i\":\"$FAKE_TOKEN\",\"limit\":30,\"markAsRead\":false,\"untilId\":\"m-old\"}", mentionsNext.body.readUtf8())
            assertEquals("{\"i\":\"$FAKE_TOKEN\",\"limit\":30,\"markAsRead\":false,\"untilId\":\"m-old\",\"userId\":\"owner\",\"includeReplies\":true}", sentNext.body.readUtf8())
            assertEquals("/api/notes/mentions", mentionsNext.path)
            assertEquals("/api/users/notes", sentNext.path)
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
            val request = server.takeRequest()
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
            override suspend fun conversationThread(request: DirectThreadRequest) = emptyList<Post>()
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
            override suspend fun conversationThread(request: DirectThreadRequest) = listOf(lastPost)
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
