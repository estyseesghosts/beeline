package me.foxtails.palustris.data

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.mastodon.MastodonCapabilityProbe
import me.foxtails.palustris.data.mastodon.MastodonMapper
import me.foxtails.palustris.data.mastodon.MastodonSource
import me.foxtails.palustris.data.mastodon.mastodonTestClient
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.PostingCapabilities
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.QuoteApproval
import me.foxtails.palustris.domain.QuoteApprovalPolicy
import me.foxtails.palustris.domain.QuoteUnavailable
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.carriesQuote
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Quote shapes of each server family, quote creation on a Mastodon-API server, and the check that a quote arrived. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuoteInteropTest {
    private lateinit var server: MockWebServer
    private lateinit var origin: String

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        origin = server.url("/").toString().removeSuffix("/")
    }

    @After
    fun stop() = server.shutdown()

    private fun account(id: String) = JSONObject()
        .put("id", id).put("acct", "u$id").put("username", "u$id").put("display_name", "U$id").put("url", "https://x/u$id")

    private fun status(id: String, content: String = "<p>hi</p>") = JSONObject()
        .put("id", id)
        .put("created_at", "2026-10-10T00:00:00.000Z")
        .put("visibility", "public")
        .put("content", content)
        .put("account", account("a$id"))

    private fun post(json: JSONObject) = MastodonMapper.post(json, origin)

    @Test
    fun mastodonQuoteWithAcceptedStateAndStatus() {
        val parsed = post(status("1").put("quote", JSONObject().put("state", "accepted").put("quoted_status", status("2"))))

        assertEquals("2", parsed.quote?.id?.value)
        assertNull(parsed.quoteUnavailable)
    }

    @Test
    fun mastodonShallowQuoteKeepsOnlyTheId() {
        val parsed = post(status("1").put("quote", JSONObject().put("state", "accepted").put("quoted_status_id", "77")))

        assertNull(parsed.quote)
        assertEquals(EntityId(origin, "77"), parsed.quoteId)
        assertTrue(parsed.carriesQuote())
    }

    @Test
    fun everyNonAcceptedStateBecomesAPlaceholderReason() {
        val expected = mapOf(
            "pending" to QuoteUnavailable.Pending,
            "rejected" to QuoteUnavailable.Rejected,
            "revoked" to QuoteUnavailable.Revoked,
            "deleted" to QuoteUnavailable.Deleted,
            "unauthorized" to QuoteUnavailable.Unauthorized,
            "blocked_account" to QuoteUnavailable.BlockedAccount,
            "blocked_domain" to QuoteUnavailable.BlockedDomain,
            "muted_account" to QuoteUnavailable.MutedAccount,
        )
        expected.forEach { (state, reason) ->
            val parsed = post(status("1").put("quote", JSONObject().put("state", state)))
            assertNull(state, parsed.quote)
            assertEquals(state, reason, parsed.quoteUnavailable)
        }
    }

    @Test
    fun fedibirdSendsAPlainStatusWithNoState() {
        val parsed = post(status("1").put("quote", status("2")).put("quote_id", "2"))

        assertEquals("2", parsed.quote?.id?.value)
    }

    @Test
    fun fedibirdMutedQuoteIsAPlaceholder() {
        val parsed = post(status("1").put("quote", status("2")).put("quote_muted", true))

        assertNull(parsed.quote)
        assertEquals(QuoteUnavailable.Hidden, parsed.quoteUnavailable)
    }

    @Test
    fun akkomaSendsTheQuoteWithStateAndACopyOfItself() {
        val quoted = status("2")
        val parsed = post(status("1").put("quote", JSONObject(quoted.toString()).put("state", "accepted").put("quoted_status", quoted)).put("quote_id", "2"))

        assertEquals("2", parsed.quote?.id?.value)
    }

    @Test
    fun pleromaPutsTheQuoteUnderItsOwnBlock() {
        val parsed = post(status("1").put("pleroma", JSONObject().put("quote", status("2")).put("quote_id", "2").put("quote_visible", true)))

        assertEquals("2", parsed.quote?.id?.value)
    }

    @Test
    fun pleromaQuoteThatIsNotVisibleIsAPlaceholder() {
        val parsed = post(status("1").put("pleroma", JSONObject().put("quote_id", "2").put("quote_visible", false)))

        assertNull(parsed.quote)
        assertEquals(QuoteUnavailable.Hidden, parsed.quoteUnavailable)
    }

    @Test
    fun aBareQuoteIdAndAQuoteUrlAreKeptForALaterFetch() {
        val byId = post(status("1").put("quote_id", "9"))
        assertEquals(EntityId(origin, "9"), byId.quoteId)

        val byUrl = post(status("1").put("pleroma", JSONObject().put("quote_url", "https://remote.example/@a/9")))
        assertEquals("https://remote.example/@a/9", byUrl.quoteUrl)
    }

    @Test
    fun aTrailingReLinkIsAReferenceAndItsParagraphIsHidden() {
        val html = "<p>my take</p><p class=\"quote-inline\">RE: <a href=\"https://remote.example/@a/9\">https://remote.example/@a/9</a></p>"
        val parsed = post(status("1", html).put("quote_id", "9"))

        assertEquals("my take", parsed.text.trim())
        assertEquals(EntityId(origin, "9"), parsed.quoteId)

        val urlOnly = post(status("1", html))
        assertEquals("https://remote.example/@a/9", urlOnly.quoteUrl)
        assertFalse(urlOnly.text.contains("RE:"))
    }

    @Test
    fun aPostWithNoQuoteKeepsItsFullBody() {
        val parsed = post(status("1", "<p>plain</p><p class=\"quote-inline\">RE: nothing</p>"))

        assertTrue(parsed.text.contains("RE:"))
        assertFalse(parsed.carriesQuote())
    }

    @Test
    fun theQuoteApprovalOfTheViewerIsRead() {
        fun approval(value: String) = post(status("1").put("quote_approval", JSONObject().put("current_user", value))).quoteApproval

        assertEquals(QuoteApproval.Automatic, approval("automatic"))
        assertEquals(QuoteApproval.Manual, approval("manual"))
        assertEquals(QuoteApproval.Denied, approval("denied"))
        assertEquals(QuoteApproval.Unknown, post(status("1")).quoteApproval)
    }

    @Test
    fun capabilityEvidenceFromFeaturesAndFediibirdFlag() {
        fun quotes(instance: JSONObject) = MastodonCapabilityProbe.parseCapabilities(instance).quotes

        assertEquals(CapabilityStatus.Supported, quotes(JSONObject().put("version", "4.5.0")))
        assertEquals(CapabilityStatus.Supported, quotes(JSONObject().put("version", "4.4.0").put("feature_quote", true)))
        val akkoma = JSONObject().put("version", "2.7.2 (compatible; Akkoma 3.10)").put(
            "pleroma",
            JSONObject().put("metadata", JSONObject().put("features", JSONArray().put("quote_posting"))),
        )
        assertEquals(CapabilityStatus.Supported, quotes(akkoma))
        // No evidence: the choice stays open but unconfirmed.
        assertEquals(CapabilityStatus.Unknown, quotes(JSONObject().put("version", "4.4.0")))
    }

    @Test
    fun quoteWithMediaAndThePolicyFollowTheEvidence() {
        val supported = MastodonCapabilityProbe.parseCapabilities(JSONObject().put("version", "4.5.0")).posting
        assertTrue(supported.quoteWithMedia)
        assertTrue(supported.quoteApprovalPolicy)

        val unconfirmed = MastodonCapabilityProbe.parseCapabilities(JSONObject().put("version", "4.4.0")).posting
        assertFalse(unconfirmed.quoteWithMedia)
        assertFalse(unconfirmed.quoteApprovalPolicy)
    }

    private fun source(quotes: CapabilityStatus, posting: PostingCapabilities = PostingCapabilities(quoteWithMedia = true)) = MastodonSource(
        origin = origin,
        token = "t",
        api = mastodonTestClient(),
        accountId = AccountId(Connection(origin, Protocol.MASTODON), "me"),
        initialCapabilities = ServerCapabilities(timelines = setOf(Timeline.Home), canPublish = true, quotes = quotes, posting = posting),
    )

    @Test
    fun createSendsBothQuoteFieldsAndThePolicy() = runBlocking {
        server.enqueue(MockResponse().setBody(status("1").put("quote", JSONObject().put("state", "accepted").put("quoted_status", status("2"))).toString()))
        val mastodon = source(CapabilityStatus.Unknown, PostingCapabilities(quoteApprovalPolicy = true))

        val created = mastodon.create(CreatePostRequest("hi", quoteOf = EntityId(origin, "2"), quoteApprovalPolicy = QuoteApprovalPolicy.Followers))

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("quoted_status_id=2"))
        assertTrue(body.contains("quote_id=2"))
        assertTrue(body.contains("quote_approval_policy=followers"))
        assertNotNull(created.quote)
        assertEquals(CapabilityStatus.Unknown, mastodon.capabilities.quotes)
    }

    @Test
    fun aServerThatDropsTheQuoteIsMarkedUnsupportedForTheSession() = runBlocking {
        server.enqueue(MockResponse().setBody(status("1").toString()))
        val mastodon = source(CapabilityStatus.Unknown)

        val created = mastodon.create(CreatePostRequest("hi", quoteOf = EntityId(origin, "2")))

        assertFalse(created.carriesQuote())
        assertEquals(CapabilityStatus.Unsupported, mastodon.capabilities.quotes)
    }

    @Test
    fun aQuoteIsStillAttemptedWhenTheVersionIsUnknown() = runBlocking {
        server.enqueue(MockResponse().setBody(status("1").put("quote_id", "2").toString()))

        source(CapabilityStatus.Unknown).create(CreatePostRequest("hi", quoteOf = EntityId(origin, "2")))

        assertEquals(1, server.requestCount)
    }

    @Test
    fun quoteWithAPollIsRefusedAndMediaDependsOnTheServer() {
        val withPoll = CreatePostRequest("hi", quoteOf = EntityId(origin, "2"), poll = me.foxtails.palustris.domain.PollRequest(listOf("a", "b")))
        assertThrows(SourceError.Unsupported::class.java) { runBlocking { source(CapabilityStatus.Supported).create(withPoll) } }

        val withMedia = CreatePostRequest("hi", quoteOf = EntityId(origin, "2"), attachments = listOf(Attachment(url = "https://x/a.png", mimeType = "image/png", id = "m1")))
        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source(CapabilityStatus.Supported, PostingCapabilities(quoteWithMedia = false)).create(withMedia) }
        }
        assertEquals(0, server.requestCount)
    }
}
