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

            val thread = source.conversationThread(
                DirectThreadRequest(ConversationId(origin, "conversation"), EntityId(origin, "last")),
            )

            assertEquals(listOf("first", "last", "reply"), thread.map { it.id.value })
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
