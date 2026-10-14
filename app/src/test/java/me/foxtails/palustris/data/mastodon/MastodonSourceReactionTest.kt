package me.foxtails.palustris.data.mastodon

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ReactionSelectionMode
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
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

/**
 * Slice 4 mutation path: unicode is percent-encoded, custom goes out as `:shortcode:`,
 * both return the updated status for state replacement, and remote identities fail before
 * any network call. A custom `PUT` rejection demotes only the custom allowance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonSourceReactionTest {
    private val origin get() = server.url("/").toString().removeSuffix("/")
    private lateinit var server: MockWebServer

    private val localAccount = JSONObject()
        .put("id", "local-user")
        .put("username", "alice")
        .put("acct", "alice")
        .put("display_name", "Alice")

    @Before
    fun startServer() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun unicodePutUsesAPercentEncodedPathAndReturnsTheUpdatedStatus() = runBlocking {
        server.enqueue(MockResponse().setBody(status("status-1").put(
            "pleroma",
            JSONObject().put("emoji_reactions", JSONArray().put(
                JSONObject().put("name", "☕").put("count", 1).put("me", true),
            )),
        ).toString()))
        val source = reactionSource()

        val result = source.setReaction(EntityId(origin, "status-1"), EmojiChoice("☕", "☕"), true)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/pleroma/statuses/status-1/reactions/%E2%98%95", request.path)
        assertEquals(true, result.selected)
        val post = result.post ?: error("Mutation must return the updated status")
        assertEquals("status-1", post.id.value)
        assertEquals(listOf("☕"), post.reactions.map { it.emoji })
        assertEquals(listOf("☕"), post.selectedReactions.map { it.submissionValue })
    }

    @Test
    fun bareShortcodePutGainsColons() = runBlocking {
        server.enqueue(MockResponse().setBody(status("status-1").toString()))
        val source = reactionSource()

        source.react(EntityId(origin, "status-1"), EmojiChoice("dinosaur", "dinosaur"))

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/pleroma/statuses/status-1/reactions/:dinosaur:", request.path)
    }

    @Test
    fun colonWrappedCustomPassesThroughUntouched() = runBlocking {
        server.enqueue(MockResponse().setBody(status("status-1").toString()))
        val source = reactionSource()

        source.react(EntityId(origin, "status-1"), EmojiChoice(":dinosaur:", ":dinosaur:"))

        assertEquals(
            "/api/v1/pleroma/statuses/status-1/reactions/:dinosaur:",
            server.takeRequest().path,
        )
    }

    @Test
    fun deleteUsesTheSamePaths() = runBlocking {
        server.enqueue(MockResponse().setBody(status("status-1").toString()))
        server.enqueue(MockResponse().setBody(status("status-1").toString()))
        val source = reactionSource()
        val postId = EntityId(origin, "status-1")

        source.removeReaction(postId, EmojiChoice("☕", "☕"))
        source.removeReaction(postId, EmojiChoice("dinosaur", "dinosaur"))

        val unicodeDelete = server.takeRequest()
        assertEquals("DELETE", unicodeDelete.method)
        assertEquals("/api/v1/pleroma/statuses/status-1/reactions/%E2%98%95", unicodeDelete.path)
        val customDelete = server.takeRequest()
        assertEquals("DELETE", customDelete.method)
        assertEquals("/api/v1/pleroma/statuses/status-1/reactions/:dinosaur:", customDelete.path)
    }

    @Test
    fun remoteIdentityFailsBeforeNetwork() = runBlocking {
        val source = reactionSource()
        val postId = EntityId(origin, "status-1")

        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.react(postId, EmojiChoice("dinosaur@other.example", "dinosaur@other.example")) }
        }
        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.reactionAccounts(postId, EmojiChoice("dinosaur@other.example", "dinosaur@other.example")) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun blankSubmissionFailsBeforeNetwork() = runBlocking {
        val source = reactionSource()

        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.react(EntityId(origin, "status-1"), EmojiChoice(" ", " ")) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun customPutRejectionDemotesOnlyCustomSupport() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"error":"Could not add reaction emoji"}"""))
        val source = reactionSource()

        assertThrows(SourceError.ServerError::class.java) {
            runBlocking { source.react(EntityId(origin, "status-1"), EmojiChoice(":dinosaur:", ":dinosaur:")) }
        }

        assertEquals(CapabilityStatus.Unsupported, source.capabilities.supportsCustomReactions)
        assertEquals(CapabilityStatus.Supported, source.capabilities.supportsReactions)
        assertEquals(CapabilityStatus.Supported, source.capabilities.emoji.reactionMutation)
        assertTrue(PostAction.React in source.capabilities.actions)
    }

    @Test
    fun unicodeFailureKeepsTheCustomAllowance() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"Record not found"}"""))
        val source = reactionSource()

        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.react(EntityId(origin, "status-1"), EmojiChoice("☕", "☕")) }
        }

        assertEquals(CapabilityStatus.Unknown, source.capabilities.supportsCustomReactions)
        assertEquals(CapabilityStatus.Supported, source.capabilities.emoji.reactionMutation)
    }

    @Test
    fun reactionAccountsListsEntryAccounts() = runBlocking {
        val reactor = JSONObject(localAccount.toString()).put("id", "reactor").put("username", "bob").put("acct", "bob").put("display_name", "Bob")
        server.enqueue(MockResponse().setBody(JSONObject()
            .put("name", "dinosaur")
            .put("count", 1)
            .put("me", false)
            .put("accounts", JSONArray().put(reactor))
            .toString()))
        val source = reactionSource()

        val accounts = source.reactionAccounts(EntityId(origin, "status-1"), EmojiChoice("dinosaur", "dinosaur"))

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/pleroma/statuses/status-1/reactions/:dinosaur:", request.path)
        assertEquals(listOf("Bob"), accounts.map { it.displayName })
        assertEquals(listOf("reactor"), accounts.map { it.id.localId })
    }

    @Test
    fun reactionAccountsWithoutAccountsYieldsAnEmptyList() = runBlocking {
        server.enqueue(MockResponse().setBody(JSONObject().put("name", "☕").put("count", 1).toString()))
        val source = reactionSource()

        assertTrue(source.reactionAccounts(EntityId(origin, "status-1"), EmojiChoice("☕", "☕")).isEmpty())
    }

    private fun reactionSource() = MastodonSource(
        origin = origin,
        token = "token",
        api = mastodonTestClient(),
        accountId = AccountId(Connection(origin, Protocol.MASTODON), "local-user"),
        initialCapabilities = ServerCapabilities(
            timelines = setOf(Timeline.Home),
            actions = setOf(PostAction.Reply, PostAction.Favorite, PostAction.React),
            canPublish = true,
            emoji = EmojiCapabilities(
                catalog = CapabilityStatus.Supported,
                reactionListing = CapabilityStatus.Supported,
                reactionMutation = CapabilityStatus.Supported,
                selectionMode = ReactionSelectionMode.Independent,
            ),
            supportsReactions = CapabilityStatus.Supported,
            capabilitySchemaVersion = ServerCapabilities.CURRENT_CAPABILITY_SCHEMA_VERSION,
        ),
    )

    private fun status(id: String) = JSONObject()
        .put("id", id)
        .put("created_at", "2026-09-06T10:00:00Z")
        .put("account", localAccount)
        .put("content", "<p>Original</p>")
        .put("visibility", "public")
}
