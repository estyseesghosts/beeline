package me.foxtails.palustris.data.mastodon

import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelAndJoin
import me.foxtails.palustris.data.mastodon.MastodonMapper
import me.foxtails.palustris.data.mastodon.MastodonSource
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.transport.HttpResponse
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityProbe
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EditableProfileCapabilities
import me.foxtails.palustris.domain.EditableProfileField
import me.foxtails.palustris.domain.EditableProfilePatch
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.ProfileCapabilities
import me.foxtails.palustris.domain.ProfileTimelineQuery
import me.foxtails.palustris.domain.ProfileTimelineTab
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SourceError
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.Headers
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonIntegrationTest {
    private val origin get() = server.url("/").toString().removeSuffix("/")
    private lateinit var server: MockWebServer

    private val localAccount = JSONObject()
        .put("id", "local-user")
        .put("username", "alice")
        .put("acct", "alice")
        .put("display_name", "Alice")
        .put("avatar", "https://example.org/avatar.png")
        .put("note", "<p>Bio &amp; details</p>")
        .put("header", "https://example.org/banner.png")
        .put("followers_count", 42)
        .put("following_count", 17)
        .put("statuses_count", 99)
        .put("locked", true)
        .put("bot", false)
        .put("fields", JSONArray()
            .put(JSONObject().put("name", "Website").put("value", "<a href=\"https://example.org\">example.org</a>"))
            .put(JSONObject().put("name", "Matrix").put("value", "@alice:example.org")))

    @Before
    fun startServer() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun mapperConvertsHtmlVisibilityMediaPollAndActions() {
        val post = MastodonMapper.post(status("status-1").apply {
            put("content", "<p>Hello <span class=\"h-card\"><a href=\"https://example.org/@bob\">@bob</a></span><br>Tea &amp; cake</p>")
                .put("visibility", "direct")
                .put("spoiler_text", "Warning")
                .put("sensitive", true)
                .put("favourites_count", 4)
                .put("favourited", true)
                .put("media_attachments", JSONArray().put(JSONObject()
                    .put("type", "image")
                .put("url", "https://example.org/photo.jpg")
                .put("preview_url", "https://example.org/photo-small.jpg")
                .put("description", "A photo")
                .put("sensitive", false)))
                .put("poll", JSONObject().put("options", JSONArray().put(JSONObject().put("title", "Yes").put("votes_count", 3))))
        }, origin)

        assertEquals("Hello @bob\nTea & cake", post.text)
        assertEquals(Audience.Direct, post.audience)
        assertEquals("Warning", post.contentWarning)
        assertEquals("A photo", post.attachments.single().description)
        assertTrue(post.attachments.single().sensitive)
        assertEquals("image/*", post.attachments.single().mimeType)
        assertEquals("Yes", post.pollOptions.single().text)
        assertTrue(PostAction.Favorite in post.availableActions)
        assertFalse(PostAction.React in post.availableActions)
        assertEquals("Bio & details", MastodonMapper.account(localAccount, origin).biography)
        assertEquals(listOf("Website", "Matrix"), MastodonMapper.account(localAccount, origin).profileFields.map { it.name })
        assertEquals("example.org", MastodonMapper.account(localAccount, origin).profileFields.first().value)
        assertEquals("https://example.org/banner.png", MastodonMapper.account(localAccount, origin).bannerUrl)
        assertEquals(42L, MastodonMapper.account(localAccount, origin).followersCount)
        assertEquals(17L, MastodonMapper.account(localAccount, origin).followingCount)
        assertEquals(99L, MastodonMapper.account(localAccount, origin).postsCount)
        assertTrue(MastodonMapper.account(localAccount, origin).locked)
    }

    @Test
    fun mapperPreservesHashtagAndPhraseLinksAsMarkdown() {
        val post = MastodonMapper.post(status("links").put(
            "content", "<p>[#tag](https://example.org/tags/tag) and <a href=\"https://example.org/guide\">the guide</a></p>",
        ), origin)

        assertEquals("[#tag](https://example.org/tags/tag) and [the guide](https://example.org/guide)", post.text)
    }

    @Test
    fun mapperPreservesReblogAsResharedPost() {
        val resharedBy = JSONObject(localAccount.toString()).put("id", "resharer").put("username", "bob").put("acct", "bob")
            .put("display_name", "Bob")
        val outer = status("boost-1").put("account", resharedBy).put("reblog", status("original-1"))

        val post = MastodonMapper.post(outer, origin)

        assertEquals("boost-1", post.id.value)
        assertEquals("local-user", post.author.id.localId)
        assertEquals("Bob", post.resharedBy?.displayName)
        assertEquals("Original", post.text)
    }

    @Test
    fun mapperMapsReplyParentAccountAndKeepsQuoteAsAuthoredPost() {
        val status = status("reply").put("in_reply_to_id", "parent").put("in_reply_to_account_id", "parent-user")
            .put("quoted_status", status("quoted").put("account", localAccount))

        val post = MastodonMapper.post(status, origin)

        assertEquals("parent-user", post.replyToAuthorId?.localId)
        assertEquals("quoted", post.quote?.id?.value)
        assertEquals(null, post.resharedBy)
    }

    @Test
    fun mapperTreatsExplicitNullReplyFieldsAsNoReply() {
        val post = MastodonMapper.post(status("top-level")
            .put("in_reply_to_id", JSONObject.NULL)
            .put("in_reply_to_account_id", JSONObject.NULL), origin)

        assertNull(post.replyTo)
        assertNull(post.replyToAuthorId)
    }

    @Test
    fun sharedLinkParserCharacterizesRelationSyntaxAndHeaderSelection() {
        // These tests pin the shared parsing shape, not Misskey cursor ownership or pagination.
        fun response(link: String) = HttpResponse("", Headers.headersOf("Link", link))

        assertEquals("https://example.org/next", response("<https://example.org/next>; rel=next").linkHeaderCursor())
        assertEquals("https://example.org/next", response("<https://example.org/next>; rel=\"prev next\"").linkHeaderCursor())
        assertEquals("https://example.org/next", response("<https://example.org/next> ; rel = \"  next  \"").linkHeaderCursor())
        assertNull(response("https://example.org/next; rel=next").linkHeaderCursor())
        assertNull(response("<https://example.org/next>; REL=next").linkHeaderCursor())

        val multiple = Headers.Builder()
            // OkHttp exposes repeated Link header fields as multiple values.
            .add("Link", "<https://example.org/first>; rel=next")
            .add("Link", "<https://example.org/second>; rel=next")
            .build()
        assertEquals("https://example.org/second", HttpResponse("", multiple).linkHeaderCursor())
    }

    @Test
    fun quoteRejectsAForeignOriginBeforeSendingAnAuthenticatedRequest() = runBlocking {
        val source = MastodonSource(
            origin = origin,
            token = "test-token",
            api = mastodonTestClient(),
            accountId = AccountId(Connection(origin, Protocol.MASTODON), "local-user"),
            initialCapabilities = ServerCapabilities(
                quotes = CapabilityStatus.Supported,
            ),
        )

        assertThrows(SourceError.ForeignOrigin::class.java) {
            runBlocking {
                source.create(
                    CreatePostRequest(
                        text = "quote",
                        quoteOf = EntityId("https://foreign.example", "foreign-post"),
                    ),
                )
            }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun sourceLoadsCanonicalPostContextAndHonorsRefreshHint() = runBlocking {
        val ancestor = status("ancestor").put("in_reply_to_id", "older")
        val reply = status("reply").put("in_reply_to_id", "focal")
        server.enqueue(MockResponse().setBody(status("focal").toString()))
        server.enqueue(
            MockResponse()
                .setBody(JSONObject()
                    .put("ancestors", JSONArray().put(ancestor))
                    .put("descendants", JSONArray().put(reply))
                    .toString())
                .addHeader("Mastodon-Async-Refresh", "id=\"job\", retry=3, result_count=1"),
        )
        val source = source()

        val context = source.threadContext(EntityId(origin, "focal"))

        assertEquals("focal", context.focal.id.value)
        assertEquals(listOf("ancestor"), context.ancestors.map { it.id.value })
        assertEquals(listOf("reply"), context.descendants.map { it.id.value })
        assertEquals(3_000L, context.refreshHint?.minimumDelayMillis)
        assertEquals("/api/v1/statuses/focal", server.takeRequest().path)
        assertEquals("/api/v1/statuses/focal/context", server.takeRequest().path)
    }

    @Test
    fun threadContextRejectsContinuationFromAnotherSession() = runBlocking {
        val source = source()
        val other = AccountId(Connection(origin, Protocol.MASTODON), "other-user")
        val continuation = me.foxtails.palustris.domain.ThreadContinuation(
            me.foxtails.palustris.domain.ThreadSessionKey(other, 0L, EntityId(origin, "focal")),
            "opaque",
        )

        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.threadContext(EntityId(origin, "focal"), continuation) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun sourceUsesLinkCursorAndBearerTimelineRequest() = runBlocking {
        server.enqueue(MockResponse().setBody("[${status("newest")}]" ).addHeader(
            "Link", "<$origin/api/v1/timelines/home?max_id=newest>; rel=\"next\"",
        ))
        server.enqueue(MockResponse().setBody("[${status("older")}]"))
        val source = source()

        val first = source.timeline(me.foxtails.palustris.domain.Timeline.Home)
        val second = source.timeline(me.foxtails.palustris.domain.Timeline.Home, first.nextCursor)

        assertEquals("newest", first.items.single().id.value)
        assertEquals("older", second.items.single().id.value)
        assertTrue(first.nextCursor!!.startsWith("http://").not())
        val firstRequest = server.takeRequest()
        val secondRequest = server.takeRequest()
        assertEquals("/api/v1/timelines/home", firstRequest.path)
        assertEquals("/api/v1/timelines/home?max_id=newest", secondRequest.path)
        assertEquals("Bearer token", secondRequest.getHeader("Authorization"))
    }

    @Test
    fun oversizedPostResponseFailsWithPostResourceLimit() = runBlocking {
        server.enqueue(MockResponse().setBody(" ".repeat(4 * 1024 * 1024 + 1)))
        val source = source()

        val error = assertThrows(SourceError.ResourceLimit::class.java) {
            runBlocking { source.post(EntityId(origin, "large-post")) }
        }

        assertEquals("post", error.feature)
        assertEquals("/api/v1/statuses/large-post", server.takeRequest().path)
    }

    @Test
    fun cancelingTimelinePageCancelsRequestAndAllowsRetry() = runBlocking {
        server.enqueue(MockResponse().setBody("[]").addHeader(
            "Link", "<$origin/api/v1/timelines/home?max_id=next>; rel=\"next\"",
        ))
        server.enqueue(MockResponse().setBody("[]").setBodyDelay(5, TimeUnit.SECONDS))
        server.enqueue(MockResponse().setBody("[${status("retried")}]"))
        val source = source()
        val cursor = source.timeline(me.foxtails.palustris.domain.Timeline.Home).nextCursor
        assertNotNull(cursor)
        assertEquals("/api/v1/timelines/home", server.takeRequest(5, TimeUnit.SECONDS)?.path)

        val pending = async(Dispatchers.IO) {
            source.timeline(me.foxtails.palustris.domain.Timeline.Home, cursor)
        }
        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("The second-page request should reach the server", request)
        assertEquals("/api/v1/timelines/home?max_id=next", request!!.path)
        pending.cancelAndJoin()
        assertTrue(pending.isCancelled)
        try {
            pending.await()
            throw AssertionError("Canceled page must not deliver a result")
        } catch (_: CancellationException) {
            // Cancellation is the expected result, not a mapped page failure.
        }

        val retry = source.timeline(me.foxtails.palustris.domain.Timeline.Home, cursor)
        assertEquals(listOf("retried"), retry.items.map { it.id.value })
        assertEquals("/api/v1/timelines/home?max_id=next", server.takeRequest(5, TimeUnit.SECONDS)?.path)
    }

    @Test
    fun timelineLocalAndFederatedKeepTheirRouteFiltersOnSecondPage() = runBlocking {
        val source = source()
        server.enqueue(MockResponse().setBody("[${status("local-one")}]" ).addHeader(
            "Link", "<$origin/api/v1/timelines/public?local=true&max_id=local-one>; rel=\"next\"",
        ))
        server.enqueue(MockResponse().setBody("[]"))
        val local = source.timeline(me.foxtails.palustris.domain.Timeline.Local)
        source.timeline(me.foxtails.palustris.domain.Timeline.Local, local.nextCursor)
        assertEquals("/api/v1/timelines/public?local=true", server.takeRequest().path)
        val localSecond = server.takeRequest()
        assertEquals("/api/v1/timelines/public?local=true&max_id=local-one", localSecond.path)
        assertEquals("Bearer token", localSecond.getHeader("Authorization"))

        server.enqueue(MockResponse().setBody("[]").addHeader(
            "Link", "<$origin/api/v1/timelines/public?max_id=fed-one>; rel=\"next\"",
        ))
        server.enqueue(MockResponse().setBody("[]"))
        val federated = source.timeline(me.foxtails.palustris.domain.Timeline.Federated)
        source.timeline(me.foxtails.palustris.domain.Timeline.Federated, federated.nextCursor)
        assertEquals("/api/v1/timelines/public", server.takeRequest().path)
        val federatedSecond = server.takeRequest()
        assertEquals("/api/v1/timelines/public?max_id=fed-one", federatedSecond.path)
        assertEquals("Bearer token", federatedSecond.getHeader("Authorization"))
    }

    @Test
    fun timelineRejectsRawAndCrossRouteCursorsBeforeAnotherRequest() = runBlocking {
        server.enqueue(MockResponse().setBody("[]").addHeader(
            "Link", "<$origin/api/v1/timelines/public?local=true&max_id=x>; rel=\"next\"",
        ))
        val source = source()
        val local = source.timeline(me.foxtails.palustris.domain.Timeline.Local)
        val count = server.requestCount
        for (cursor in listOf(local.nextCursor!!, "$origin/api/v1/timelines/home?max_id=x", "v1/timelines/home?max_id=x")) {
            try {
                source.timeline(me.foxtails.palustris.domain.Timeline.Federated, cursor)
                throw AssertionError("Invalid pagination cursor should fail")
            } catch (error: SourceError.Unsupported) {
                assertEquals("pagination.cursor", error.feature)
            }
            assertEquals(count, server.requestCount)
        }
    }

    @Test
    fun timelineRejectsInvalidLocalLinkBeforeReturningCursor() = runBlocking {
        server.enqueue(MockResponse().setBody("[]").addHeader(
            "Link", "<$origin/api/v1/timelines/public?max_id=x>; rel=\"next\"",
        ))
        try {
            source().timeline(me.foxtails.palustris.domain.Timeline.Local)
            throw AssertionError("Invalid Link should fail")
        } catch (error: SourceError.Unsupported) {
            assertEquals("pagination.link", error.feature)
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun timelineCursorIsBoundToAccountSessionAndSourceInstance() = runBlocking {
        server.enqueue(MockResponse().setBody("[]").addHeader(
            "Link", "<$origin/api/v1/timelines/home?max_id=x>; rel=\"next\"",
        ))
        val sourceA = source()
        val cursor = sourceA.timeline(me.foxtails.palustris.domain.Timeline.Home).nextCursor!!
        val count = server.requestCount
        val otherAccount = MastodonSource(origin, "token", mastodonTestClient(), AccountId(Connection(origin, Protocol.MASTODON), "other"))
        val otherSession = MastodonSource(origin, "token", mastodonTestClient(), AccountId(Connection(origin, Protocol.MASTODON), "local-user"), sessionRevision = 1L)
        val otherInstance = source()
        listOf(otherAccount, otherSession, otherInstance).forEach { other ->
            val error = assertThrows(SourceError.Unsupported::class.java) {
                runBlocking { other.timeline(me.foxtails.palustris.domain.Timeline.Home, cursor) }
            }
            assertEquals("pagination.cursor", error.feature)
            assertEquals(count, server.requestCount)
        }
    }

    @Test
    fun timelineCursorRejectsUnsafeDecodedUrlsAndHardenedQueries() = runBlocking {
        server.enqueue(MockResponse().setBody("[]").addHeader(
            "Link", "<$origin/api/v1/timelines/home?max_id=x>; rel=\"next\"",
        ))
        val source = source()
        val cursor = source.timeline(me.foxtails.palustris.domain.Timeline.Home).nextCursor!!
        val json = JSONObject(String(java.util.Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8))
        val badUrls = listOf(
            "$origin:${server.port + 1}/api/v1/timelines/home?max_id=x",
            "${origin.replace("http://", "http://user:pass@")}/api/v1/timelines/home?max_id=x",
            "$origin/api/v1/timelines/home?max_id=x#fragment",
            "${origin.replace("http://", "https://")}/api/v1/timelines/home?max_id=x",
            "$origin/api/v1/elsewhere?max_id=x",
            "$origin/api/v1/timelines/home?max_id=x&unknown=y",
            "$origin/api/v1/timelines/home?max_id=x&max_id=y",
            "$origin/api/v1/timelines/home?max_id=",
        )
        val count = server.requestCount
        badUrls.forEach { url ->
            val payload = JSONObject(json.toString()).put("url", url).toString().toByteArray(Charsets.UTF_8)
            val tampered = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
            val error = assertThrows(SourceError.Unsupported::class.java) {
                runBlocking { source.timeline(me.foxtails.palustris.domain.Timeline.Home, tampered) }
            }
            assertEquals("pagination.cursor", error.feature)
            assertEquals(count, server.requestCount)
        }
        server.enqueue(MockResponse().setBody("[]").addHeader(
            "Link", "<$origin/api/v1/timelines/public?local=true&max_id=x>; rel=\"next\"",
        ))
        val local = source.timeline(me.foxtails.palustris.domain.Timeline.Local)
        val localJson = JSONObject(String(java.util.Base64.getUrlDecoder().decode(local.nextCursor!!), Charsets.UTF_8))
            .put("url", "$origin/api/v1/timelines/public?local=false&max_id=x")
        val tamperedLocal = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(localJson.toString().toByteArray(Charsets.UTF_8))
        val error = assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.timeline(me.foxtails.palustris.domain.Timeline.Local, tamperedLocal) }
        }
        assertEquals("pagination.cursor", error.feature)
        assertEquals(count + 1, server.requestCount)
    }

    @Test
    fun timelineRejectsMalformedCursorsAndStopsWhenLinkIsMissing() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))
        val source = source()
        val page = source.timeline(me.foxtails.palustris.domain.Timeline.Home)
        assertNull(page.nextCursor)
        val count = server.requestCount
        val versionPayload = JSONObject().put("version", 2).put("variant", "mastodon-page-v1")
            .put("origin", origin).put("account", "local-user").put("sessionRevision", 0)
            .put("sourceInstance", "instance").put("route", "timeline:Home").put("query", "Home")
            .put("url", "$origin/api/v1/timelines/home?max_id=x").toString().toByteArray(Charsets.UTF_8)
        val unknownVersion = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(versionPayload)
        val malformed = listOf("%%%", "eyJ2ZXJzaW9uIjo", unknownVersion)
        malformed.forEach { value ->
            val error = assertThrows(SourceError.Unsupported::class.java) {
                runBlocking { source.timeline(me.foxtails.palustris.domain.Timeline.Home, value) }
            }
            assertEquals("pagination.cursor", error.feature)
            assertEquals(count, server.requestCount)
        }
    }

    @Test
    fun invalidCursorSkipsCapabilityProbeWhileValidCursorRefreshesAndPages() = runBlocking {
        val invalidSource = staleSchemaSource(MastodonCapabilityProbe(mastodonTestClient()))
        val error = assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { invalidSource.timeline(me.foxtails.palustris.domain.Timeline.Home, "not-a-cursor") }
        }
        assertEquals("pagination.cursor", error.feature)
        assertEquals(0, server.requestCount)

        server.enqueue(MockResponse().setBody(JSONObject()
            .put("pleroma", JSONObject().put("metadata", JSONObject()
                .put("features", JSONArray().put("pleroma_emoji_reactions"))))
            .toString()))
        server.enqueue(MockResponse().setBody("[${status("newest")}]" ).addHeader(
            "Link", "<$origin/api/v1/timelines/home?max_id=newest>; rel=\"next\"",
        ))
        server.enqueue(MockResponse().setBody("[${status("older")}]"))
        val validSource = staleSchemaSource(MastodonCapabilityProbe(mastodonTestClient()))
        val first = validSource.timeline(me.foxtails.palustris.domain.Timeline.Home)
        val second = validSource.timeline(me.foxtails.palustris.domain.Timeline.Home, first.nextCursor)

        assertEquals(listOf("newest", "older"), listOf(first.items.single().id.value, second.items.single().id.value))
        assertEquals(3, server.requestCount)
        assertEquals("/api/v2/instance", server.takeRequest().path)
        assertEquals("/api/v1/timelines/home", server.takeRequest().path)
        val continuation = server.takeRequest()
        assertEquals("/api/v1/timelines/home?max_id=newest", continuation.path)
        assertEquals("Bearer token", continuation.getHeader("Authorization"))
    }

    @Test
    fun cursorEqualToCurrentRequestUrlIsRejectedBeforeRequest() = runBlocking {
        val route = MastodonPageRoute(
            "timeline:Home", "v1/timelines/home", "/api/v1/timelines/home", "Home", "timeline",
        )
        val cursor = MastodonPageCursor.encode(
            MastodonPageCursor.Identity(origin, "local-user", 0L, "instance", route.name, route.query),
            "$origin/api/v1/timelines/home",
        )
        val pageClient = MastodonPageClient(origin, "token", mastodonTestClient(), "local-user", 0L, "instance")
        val error = assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { pageClient.getPage(route, cursor) }
        }
        assertEquals("pagination.cursor", error.feature)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun cursorPayloadTamperingAndInvalidQueryShapesAreRejectedWithoutRequests() = runBlocking {
        server.enqueue(MockResponse().setBody("[]").addHeader(
            "Link", "<$origin/api/v1/timelines/home?max_id=x>; rel=\"next\"",
        ))
        val source = source()
        val valid = source.timeline(me.foxtails.palustris.domain.Timeline.Home).nextCursor!!
        val original = JSONObject(String(java.util.Base64.getUrlDecoder().decode(valid), Charsets.UTF_8))
        val count = server.requestCount
        val payloads = listOf(
            JSONObject(original.toString()).put("query", "Local"),
            JSONObject(original.toString()).put("variant", "other"),
            JSONObject(original.toString()).put("version", "1"),
            JSONObject(original.toString()).put("url", "$origin/api/v1/timelines/home?max_id=x&since_id=y"),
            JSONObject(original.toString()).put("url", "$origin/api/v1/timelines/home?max_id=x&local=true&local=true"),
            JSONObject(original.toString()).put("url", "$origin/api/v1/timelines/home?max_id=x&local="),
        )
        payloads.forEach { json ->
            val cursor = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.toString().toByteArray(Charsets.UTF_8))
            val error = assertThrows(SourceError.Unsupported::class.java) {
                runBlocking { source.timeline(me.foxtails.palustris.domain.Timeline.Home, cursor) }
            }
            assertEquals("pagination.cursor", error.feature)
            assertEquals(count, server.requestCount)
        }
    }

    @Test
    fun sourceSearchesHashtagWithBearerAndLinkCursor() = runBlocking {
        server.enqueue(MockResponse().setBody("[${status("tag-newest")}]").addHeader(
            "Link", "<$origin/api/v1/timelines/tag/cats?limit=40&max_id=tag-newest>; rel=\"next\"",
        ))
        server.enqueue(MockResponse().setBody("[${status("tag-older")}]"))
        val source = source()

        val first = source.searchHashtag("#cats")
        val second = source.searchHashtag("cats", first.nextCursor)

        assertEquals("tag-newest", first.items.single().id.value)
        assertEquals("tag-older", second.items.single().id.value)
        assertTrue(first.nextCursor!!.startsWith("http://").not())
        val firstRequest = server.takeRequest()
        val secondRequest = server.takeRequest()
        assertEquals("/api/v1/timelines/tag/cats?limit=40", firstRequest.path)
        assertEquals("/api/v1/timelines/tag/cats?limit=40&max_id=tag-newest", secondRequest.path)
        assertEquals("Bearer token", firstRequest.getHeader("Authorization"))
        assertEquals("Bearer token", secondRequest.getHeader("Authorization"))
    }

    @Test
    fun bookmarksUseOpaqueRouteBoundCursorAndPreserveTransportOrder() = runBlocking {
        server.enqueue(MockResponse().setBody("[${status("newest")},${status("middle")}]" ).addHeader(
            "Link", "<$origin/api/v1/bookmarks?limit=40&max_id=middle>; rel=\"next\"",
        ))
        server.enqueue(MockResponse().setBody("[${status("older")}]"))
        val source = source()

        val first = source.savedPosts()
        val second = source.savedPosts(first.nextCursor)

        assertEquals(listOf("newest", "middle"), first.items.map { it.id.value })
        assertEquals(listOf("older"), second.items.map { it.id.value })
        assertTrue(first.nextCursor!!.startsWith("http://").not())
        assertEquals("/api/v1/bookmarks?limit=40", server.takeRequest().path)
        val continuation = server.takeRequest()
        assertEquals("/api/v1/bookmarks?limit=40&max_id=middle", continuation.path)
        assertEquals("Bearer token", continuation.getHeader("Authorization"))
        val count = server.requestCount
        val error = assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.searchHashtag("cats", first.nextCursor) }
        }
        assertEquals("pagination.cursor", error.feature)
        assertEquals(count, server.requestCount)
    }

    @Test
    fun hashtagCursorPayloadTamperingAndOtherRoutesAreRejected() = runBlocking {
        server.enqueue(MockResponse().setBody("[]").addHeader(
            "Link", "<$origin/api/v1/timelines/tag/cats?limit=40&max_id=x>; rel=\"next\"",
        ))
        val source = source()
        val cursor = source.searchHashtag("#cats").nextCursor!!
        val count = server.requestCount
        server.enqueue(MockResponse().setBody("[]"))
        assertEquals(0, source.searchHashtag("cats", cursor).items.size)
        assertEquals("/api/v1/timelines/tag/cats?limit=40", server.takeRequest().path)
        val continuation = server.takeRequest()
        assertEquals("/api/v1/timelines/tag/cats?limit=40&max_id=x", continuation.path)
        assertEquals("Bearer token", continuation.getHeader("Authorization"))
        val replayCount = server.requestCount
        for (invalid in listOf(sourceCursorTamper(cursor, "query", "dogs"), "%%%", "$origin/api/v1/timelines/tag/cats?max_id=x")) {
            val error = assertThrows(SourceError.Unsupported::class.java) {
                runBlocking { source.searchHashtag("dogs", invalid) }
            }
            assertEquals("pagination.cursor", error.feature)
            assertEquals(replayCount, server.requestCount)
        }
        val otherSources = listOf(
            MastodonSource(origin, "token", mastodonTestClient(), AccountId(Connection(origin, Protocol.MASTODON), "other")),
            MastodonSource(origin, "token", mastodonTestClient(), AccountId(Connection(origin, Protocol.MASTODON), "local-user"), sessionRevision = 1L),
            source(),
        )
        otherSources.forEach { other ->
            val error = assertThrows(SourceError.Unsupported::class.java) {
                runBlocking { other.searchHashtag("cats", cursor) }
            }
            assertEquals("pagination.cursor", error.feature)
            assertEquals(replayCount, server.requestCount)
        }
        assertEquals(count + 1, replayCount)
    }

    @Test
    fun hashtagCursorRejectsAnotherQueryWithoutChangingTheCursor() = runBlocking {
        server.enqueue(MockResponse().setBody("[]").addHeader(
            "Link", "<$origin/api/v1/timelines/tag/cats?limit=40&max_id=x>; rel=\"next\"",
        ))
        val source = source()
        val cursor = source.searchHashtag("cats").nextCursor!!
        val requestCount = server.requestCount

        val error = assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.searchHashtag("dogs", cursor) }
        }

        assertEquals("pagination.cursor", error.feature)
        assertEquals(requestCount, server.requestCount)
    }

    @Test
    fun bookmarkCursorRejectsTimelineRouteBeforeCapabilityProbe() = runBlocking {
        server.enqueue(MockResponse().setBody("[]").addHeader(
            "Link", "<$origin/api/v1/bookmarks?limit=40&max_id=x>; rel=\"next\"",
        ))
        val source = staleSchemaSource(MastodonCapabilityProbe(mastodonTestClient()))
        val cursor = source.savedPosts().nextCursor!!
        val requestCount = server.requestCount

        val error = assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.timeline(me.foxtails.palustris.domain.Timeline.Home, cursor) }
        }

        assertEquals("pagination.cursor", error.feature)
        assertEquals(requestCount, server.requestCount)
    }

    @Test
    fun invalidBookmarkAndHashtagLinksFailBeforeReturningPages() = runBlocking {
        server.enqueue(MockResponse().setBody("[]").addHeader(
            "Link", "<$origin/api/v1/elsewhere?max_id=x>; rel=\"next\"",
        ))
        val bookmarkError = assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source().savedPosts() }
        }
        assertEquals("pagination.link", bookmarkError.feature)
        server.enqueue(MockResponse().setBody("[]").addHeader(
            "Link", "<$origin/api/v1/timelines/tag/dogs?limit=41&max_id=x>; rel=\"next\"",
        ))
        val hashtagError = assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source().searchHashtag("cats") }
        }
        assertEquals("pagination.link", hashtagError.feature)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun bookmarkCursorRejectsAlteredPathsQueriesLoopsAndLegacyValuesBeforeRequests() = runBlocking {
        server.enqueue(MockResponse().setBody("[]").addHeader(
            "Link", "<$origin/api/v1/bookmarks?limit=40&max_id=x>; rel=\"next\"",
        ))
        val source = source()
        val cursor = source.savedPosts().nextCursor!!
        val count = server.requestCount
        val original = JSONObject(String(java.util.Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8))
        val badUrls = listOf(
            "$origin/api/v1/elsewhere?max_id=x",
            "$origin/api/v1/bookmarks?limit=41&max_id=x",
            "$origin/api/v1/bookmarks?limit=40&max_id=x&local=true",
            "$origin/api/v1/bookmarks?limit=40&max_id=x&max_id=y",
            "$origin/api/v1/bookmarks?limit=40&max_id=",
            "$origin/api/v1/bookmarks?limit=40&max_id=x&since_id=y",
            "$origin/api/v1/bookmarks?limit=40&max_id=x#fragment",
        )
        badUrls.forEach { badUrl ->
            val altered = sourceCursorTamper(cursor, "url", badUrl)
            val error = assertThrows(SourceError.Unsupported::class.java) {
                runBlocking { source.savedPosts(altered) }
            }
            assertEquals("pagination.cursor", error.feature)
            assertEquals(count, server.requestCount)
        }
        for (legacy in listOf("$origin/api/v1/bookmarks?limit=40&max_id=x", "v1/bookmarks?limit=40&max_id=x", "%%%")) {
            val error = assertThrows(SourceError.Unsupported::class.java) {
                runBlocking { source.savedPosts(legacy) }
            }
            assertEquals("pagination.cursor", error.feature)
            assertEquals(count, server.requestCount)
        }
        val route = MastodonPageRoute("bookmarks", "v1/bookmarks?limit=40", "/api/v1/bookmarks", "", "bookmarks")
        val loop = MastodonPageCursor.encode(
            MastodonPageCursor.Identity(origin, "local-user", 0L, "instance", route.name, route.query),
            "$origin/api/v1/bookmarks?limit=40",
        )
        val loopError = assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { MastodonPageClient(origin, "token", mastodonTestClient(), "local-user", 0L, "instance").getPage(route, loop) }
        }
        assertEquals("pagination.cursor", loopError.feature)
        assertEquals(count, server.requestCount)
        assertEquals("bookmarks", original.getString("route"))
    }

    private fun sourceCursorTamper(cursor: String, key: String, value: String): String {
        val json = JSONObject(String(java.util.Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8))
            .put(key, value)
        return java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.toString().toByteArray(Charsets.UTF_8))
    }

    @Test
    fun sourceMapsCreateFavoriteRenoteNotificationsMediaAndSearch() = runBlocking {
        server.enqueue(MockResponse().setBody(status("created").toString()))
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("[${notification("notification-1")}]"))
        server.enqueue(MockResponse().setBody(JSONObject()
            .put("id", "media-1")
            .put("type", "image")
            .put("url", "https://example.org/uploaded.jpg")
            .put("preview_url", "https://example.org/uploaded-small.jpg")
            .toString()))
        server.enqueue(MockResponse().setBody(JSONObject().put("statuses", JSONArray().put(status("found"))).toString()))
        val source = source()

        val created = source.create(CreatePostRequest(
            text = "Created",
            audience = Audience.Followers,
            contentWarning = "CW",
            replyTo = EntityId(origin, "parent"),
        ))
        source.favorite(EntityId(origin, "favorite-1"))
        source.renote(EntityId(origin, "renote-1"))
        val notifications = source.notifications(NotificationQuery())
        val attachment = source.uploadMedia(ByteArrayInputStream("bytes".toByteArray()), "image/jpeg")
        val search = source.search("hello world")

        assertEquals("created", created.id.value)
        val createRequest = server.takeRequest()
        val createBody = createRequest.body.readUtf8()
        assertEquals("Bearer token", createRequest.getHeader("Authorization"))
        assertTrue(createBody.contains("visibility=private"))
        assertTrue(createBody.contains("spoiler_text=CW"))
        assertTrue(createBody.contains("in_reply_to_id=parent"))
        val favoriteRequest = server.takeRequest()
        val renoteRequest = server.takeRequest()
        assertEquals("/api/v1/statuses/favorite-1/favourite", favoriteRequest.path)
        assertEquals("Bearer token", favoriteRequest.getHeader("Authorization"))
        assertEquals("/api/v1/statuses/renote-1/reblog", renoteRequest.path)
        assertEquals("Bearer token", renoteRequest.getHeader("Authorization"))
        assertEquals("notification-1", notifications.items.single().id.value)
        assertEquals(AccountId(Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), "local-user"), notifications.items.single().accountId)
        assertEquals("https://example.org/uploaded.jpg", attachment.url)
        assertEquals("found", search.single().id.value)
        assertEquals("/api/v1/notifications", server.takeRequest().path)
        val uploadRequest = server.takeRequest()
        assertEquals("/api/v1/media", uploadRequest.path)
        assertEquals("Bearer token", uploadRequest.getHeader("Authorization"))
        assertTrue(uploadRequest.body.readUtf8().contains("bytes"))
        assertEquals("/api/v2/search?q=hello+world", server.takeRequest().path)
    }

    @Test
    fun unfavoriteEncodesReservedIdCharactersAndPreservesPlainIdPath() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("{}"))
        val source = source()

        source.unfavorite(EntityId(origin, "plain-id"))
        source.unfavorite(EntityId(origin, "slash/? space%#"))

        val plainRequest = server.takeRequest()
        assertEquals("POST", plainRequest.method)
        assertEquals("/api/v1/statuses/plain-id/unfavourite", plainRequest.path)
        assertEquals("Bearer token", plainRequest.getHeader("Authorization"))

        val reservedRequest = server.takeRequest()
        assertEquals("POST", reservedRequest.method)
        assertEquals("/api/v1/statuses/slash%2F%3F%20space%25%23/unfavourite", reservedRequest.path)
        assertEquals("Bearer token", reservedRequest.getHeader("Authorization"))
    }

    @Test
    fun sourceLoadsApiEightSelfProfileWithBearerAndRawValues() = runBlocking {
        server.enqueue(MockResponse().setBody(profileResponse("local-user").toString()))
        val source = editableSource(editableCapabilities())

        val profile = source.loadEditableProfile()

        assertEquals("local-user", profile.id)
        assertEquals("<p>Raw <b>note</b></p>", profile.biography)
        assertEquals("Site", profile.fields.single().name)
        assertEquals("<a href=\"https://example.org\">site</a>", profile.fields.single().value)
        val request = server.takeRequest()
        assertEquals("/api/v1/profile", request.path)
        assertEquals("Bearer token", request.getHeader("Authorization"))
    }

    @Test
    fun sourcePatchesApiEightProfileWithChangedFieldsOnly() = runBlocking {
        server.enqueue(MockResponse().setBody(profileResponse("local-user").toString()))
        val source = editableSource(editableCapabilities(advancedSettings = true, imageDescriptions = true))

        source.updateEditableProfile(
            EditableProfilePatch(
                displayName = "New name",
                fields = listOf(EditableProfileField("Site", "https://example.org")),
                attributionDomains = listOf("one.example", "two.example"),
                avatarDescription = "A picture",
            ),
        )

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/profile", request.path)
        assertEquals("Bearer token", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("display_name=New%20name"))
        assertFalse(body.contains("note="))
        assertTrue(body.contains("fields_attributes%5B0%5D%5Bname%5D=Site"))
        assertTrue(body.contains("fields_attributes%5B0%5D%5Bvalue%5D=https%3A%2F%2Fexample.org"))
        assertTrue(body.contains("attribution_domains%5B%5D=one.example"))
        assertTrue(body.contains("attribution_domains%5B%5D=two.example"))
        assertTrue(body.contains("avatar_description=A%20picture"))
    }

    @Test
    fun apiSevenUsesLegacySelfProfileEndpointsAndNeverTheProfileApi() = runBlocking {
        server.enqueue(MockResponse().setBody(legacyCredentialResponse("local-user").toString()))
        server.enqueue(MockResponse().setBody(legacyCredentialResponse("local-user").toString()))
        val source = editableSource(
            editableCapabilities().copy(read = CapabilityStatus.Unsupported, update = CapabilityStatus.Unsupported),
        )

        val profile = source.loadEditableProfile()
        source.updateEditableProfile(EditableProfilePatch(displayName = "New name"))

        assertEquals("Plaintext note", profile.biography)
        val loadRequest = server.takeRequest()
        assertEquals("/api/v1/accounts/verify_credentials", loadRequest.path)
        assertEquals("Bearer token", loadRequest.getHeader("Authorization"))
        val updateRequest = server.takeRequest()
        assertEquals("PATCH", updateRequest.method)
        assertEquals("/api/v1/accounts/update_credentials", updateRequest.path)
        assertTrue(updateRequest.body.readUtf8().contains("display_name=New%20name"))
        assertEquals(null, server.takeRequest(0, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test
    fun unknownCapabilitiesUseLegacyEndpointsBeforeAnyProbe() = runBlocking {
        server.enqueue(MockResponse().setBody(legacyCredentialResponse("local-user").toString()))
        val source = editableSource(
            editableCapabilities().copy(read = CapabilityStatus.Unknown, update = CapabilityStatus.Unknown),
        )

        source.loadEditableProfile()

        assertEquals("/api/v1/accounts/verify_credentials", server.takeRequest().path)
    }

    @Test
    fun mismatchedSelfProfileIdFailsBeforeStateChanges() = runBlocking {
        server.enqueue(MockResponse().setBody(profileResponse("other-user").toString()))
        val source = editableSource(editableCapabilities())

        assertThrows(SourceError.AccountMismatch::class.java) {
            runBlocking { source.loadEditableProfile() }
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun missingSelfProfileIdIsRejectedAsMismatch() = runBlocking<Unit> {
        server.enqueue(MockResponse().setBody("""{"note":"No id"}"""))
        val source = editableSource(editableCapabilities())

        assertThrows(SourceError.AccountMismatch::class.java) {
            runBlocking { source.loadEditableProfile() }
        }
    }

    @Test
    fun patchRejectsAdvancedFieldsWithoutCapabilityBeforeNetwork() = runBlocking {
        val source = editableSource(editableCapabilities(advancedSettings = false))

        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking {
                source.updateEditableProfile(EditableProfilePatch(
                    fields = listOf(EditableProfileField("Site", "https://example.org")),
                ))
            }
        }
        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking {
                source.updateEditableProfile(EditableProfilePatch(locked = true))
            }
        }
        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking {
                source.updateEditableProfile(EditableProfilePatch(avatarDescription = "A picture"))
            }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun sourceLoadsCustomEmojiCatalogWithBearer() = runBlocking {
        server.enqueue(MockResponse().setBody("""[
            {"shortcode":"blobcat","url":"https://example.org/blobcat.gif","static_url":"https://example.org/blobcat.png","category":"Cats","visible_in_picker":true}
        ]"""))
        val source = source()

        val emojis = source.customEmojis()

        assertEquals(listOf("blobcat"), emojis.map { it.shortcode })
        val request = server.takeRequest()
        assertEquals("/api/v1/custom_emojis", request.path)
        assertEquals("Bearer token", request.getHeader("Authorization"))
    }

    @Test
    fun extensionReactionsUseEncodedPutAndDeleteRoutes() = runBlocking {
        server.enqueue(MockResponse().setBody(status("reacted").toString()))
        server.enqueue(MockResponse().setBody(""))
        val source = reactionSource()
        val postId = EntityId(origin, "status-1")

        source.react(postId, EmojiChoice(":custom/emoji:", ":custom/emoji:", null))
        source.removeReaction(postId, EmojiChoice(":custom/emoji:", ":custom/emoji:", null))

        val putRequest = server.takeRequest()
        assertEquals("PUT", putRequest.method)
        assertEquals("/api/v1/pleroma/statuses/status-1/reactions/:custom%2Femoji:", putRequest.path)
        assertEquals("Bearer token", putRequest.getHeader("Authorization"))
        val deleteRequest = server.takeRequest()
        assertEquals("DELETE", deleteRequest.method)
        assertEquals("/api/v1/pleroma/statuses/status-1/reactions/:custom%2Femoji:", deleteRequest.path)
        assertEquals("Bearer token", deleteRequest.getHeader("Authorization"))
    }

    @Test
    fun extensionReactionsRejectForeignOriginsBeforeNetwork() = runBlocking {
        val source = reactionSource()
        val foreign = EntityId("https://other.example", "status-1")

        assertThrows(SourceError.ForeignOrigin::class.java) {
            runBlocking { source.react(foreign, EmojiChoice(":a:", ":a:", null)) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun reactionMutationsRequireTheVerifiedExtensionCapability() = runBlocking {
        val source = source()

        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.react(EntityId(origin, "status-1"), EmojiChoice(":a:", ":a:", null)) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun failedReactionMutationKeepsExtensionSupport() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"Record not found"}"""))
        val source = reactionSource()

        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.react(EntityId(origin, "status-1"), EmojiChoice(":a:", ":a:", null)) }
        }
        // A resource 404 is not proof that the extension is unsupported. Only the
        // advertisement establishes reaction support, so support stays available.
        assertEquals(CapabilityStatus.Supported, source.capabilities.emoji.reactionMutation)
        assertTrue(PostAction.React in source.capabilities.actions)
        assertTrue(source.capabilities.canPublish)
        assertTrue(PostAction.Favorite in source.capabilities.actions)
    }

    @Test
    fun failedAddAndRemoveKeepSupportForLaterPosts() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"Record not found"}"""))
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"Forbidden"}"""))
        server.enqueue(MockResponse().setBody(status("reacted").toString()))
        val source = reactionSource()
        val first = EntityId(origin, "status-1")
        val second = EntityId(origin, "status-2")

        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.react(first, EmojiChoice(":a:", ":a:", null)) }
        }
        assertThrows(SourceError.Unauthorized::class.java) {
            runBlocking { source.removeReaction(first, EmojiChoice(":a:", ":a:", null)) }
        }
        // Denial on one resource must not erase server support for another resource.
        assertEquals(CapabilityStatus.Supported, source.capabilities.emoji.reactionMutation)
        assertTrue(PostAction.React in source.capabilities.actions)

        source.react(second, EmojiChoice(":a:", ":a:", null))

        assertEquals(
            listOf(
                "/api/v1/pleroma/statuses/status-1/reactions/:a:",
                "/api/v1/pleroma/statuses/status-1/reactions/:a:",
                "/api/v1/pleroma/statuses/status-2/reactions/:a:",
            ),
            List(3) { server.takeRequest().path },
        )
    }

    @Test
    fun refreshedCapabilitiesPublishReactionSupportThroughTheFlow() = runBlocking {
        server.enqueue(MockResponse().setBody("[${status("newest")}]"))
        val probe = object : CapabilityProbe {
            override suspend fun probeCapabilities(connection: Connection): ServerCapabilities =
                reactionCapabilities()
        }
        val source = staleSchemaSource(probe)
        assertFalse(PostAction.React in source.capabilities.actions)

        val published = source.observeCapabilities()
        source.timeline(me.foxtails.palustris.domain.Timeline.Home)

        // A stale schema revision forces a fresh probe. The refreshed evidence must reach
        // the collected flow without a catalog or navigation change.
        assertEquals(CapabilityStatus.Supported, published.first().emoji.reactionMutation)
        assertTrue(PostAction.React in published.first().actions)
    }

    @Test
    fun refreshedCapabilitiesReachTheCapabilityCallback() = runBlocking {
        server.enqueue(MockResponse().setBody("[${status("newest")}]"))
        val callbackSnapshots = mutableListOf<ServerCapabilities>()
        val probe = object : CapabilityProbe {
            override suspend fun probeCapabilities(connection: Connection): ServerCapabilities =
                reactionCapabilities()
        }
        val source = staleSchemaSource(probe, onCapabilitiesUpdated = { callbackSnapshots += it })

        source.timeline(me.foxtails.palustris.domain.Timeline.Home)

        // The account/session owner must receive the refreshed snapshot to persist it.
        assertEquals(CapabilityStatus.Supported, callbackSnapshots.single().emoji.reactionMutation)
    }

    @Test
    fun metadataFailureBoundsCapabilityRetries() = runBlocking {
        server.enqueue(MockResponse().setBody("[${status("one")}]"))
        server.enqueue(MockResponse().setBody("[${status("two")}]"))
        var now = 1_000_000L
        var probes = 0
        val probe = object : CapabilityProbe {
            override suspend fun probeCapabilities(connection: Connection): ServerCapabilities {
                probes++
                throw RuntimeException("offline")
            }
        }
        val source = staleSchemaSource(probe, clock = { now })

        source.timeline(me.foxtails.palustris.domain.Timeline.Home)
        now += 1_000L
        source.timeline(me.foxtails.palustris.domain.Timeline.Home)

        // A failed probe must not retry on every request during an outage.
        assertEquals(1, probes)
    }

    @Test
    fun sourceLoadsMastodonProfileAndLooksUpExactHandle() = runBlocking {
        server.enqueue(MockResponse().setBody(localAccount.toString()))
        server.enqueue(MockResponse().setBody(localAccount.toString()))
        val source = source()

        val profile = source.profile(AccountId(me.foxtails.palustris.domain.Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), "local-user"))
        val result = source.searchAccounts("@alice@example.org")

        assertEquals("Bio & details", profile.biography)
        assertEquals("Matrix", result.single().profileFields[1].name)
        val profileRequest = server.takeRequest()
        assertEquals("/api/v1/accounts/local-user", profileRequest.path)
        assertEquals("Bearer token", profileRequest.getHeader("Authorization"))
        val lookupRequest = server.takeRequest()
        assertEquals("/api/v1/accounts/lookup?acct=alice%40example.org", lookupRequest.path)
        assertEquals("Bearer token", lookupRequest.getHeader("Authorization"))
    }

    @Test
    fun profileMapsOneHopMovedDestinationWithoutAnotherRequest() = runBlocking {
        val destination = account("new-user", "newalice", "New Alice")
            .put("acct", "newalice@remote.example")
            .put("moved", account("third-user", "third", "Third"))
        server.enqueue(MockResponse().setBody(JSONObject(localAccount.toString()).put("moved", destination).toString()))

        val profile = source().profile(AccountId(Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), "local-user"))

        assertEquals("new-user", profile.movedTo?.id?.localId)
        assertEquals("New Alice", profile.movedTo?.displayName)
        assertEquals("@newalice@remote.example", profile.movedTo?.handle)
        assertEquals("https://example.org/avatar.png", profile.movedTo?.avatarUrl)
        assertNull(profile.movedTo?.movedTo)
        assertEquals(1, server.requestCount)
        assertEquals("/api/v1/accounts/local-user", server.takeRequest().path)
    }

    @Test
    fun mapperIgnoresMissingNullAndMalformedMovedDestinations() {
        assertNull(MastodonMapper.account(localAccount, origin).movedTo)
        assertNull(MastodonMapper.account(JSONObject(localAccount.toString()).put("moved", JSONObject.NULL), origin).movedTo)
        assertNull(MastodonMapper.account(JSONObject(localAccount.toString()).put("moved", JSONObject().put("username", "broken")), origin).movedTo)
        assertNull(MastodonMapper.account(JSONObject(localAccount.toString()).put("moved", JSONObject()
            .put("id", "").put("username", "")), origin).movedTo)
    }

    @Test
    fun profileTimelineUsesSafeAccountPathAndFiltersMixedStatusesLocally() = runBlocking {
        val target = AccountId(Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), "local-user")
        val other = account("other-user", "other", "Other")
        val root = status("root").put("in_reply_to_id", JSONObject.NULL).put("in_reply_to_account_id", JSONObject.NULL)
        val media = status("media").put("media_attachments", JSONArray().put(JSONObject()
            .put("type", "image").put("url", "https://example.org/photo.jpg")))
        val reply = status("reply").put("in_reply_to_id", "parent").put("in_reply_to_account_id", "other-user")
        val selfReply = status("self-reply").put("in_reply_to_id", "parent").put("in_reply_to_account_id", "local-user")
        val boost = status("boost").put("account", localAccount).put("reblog", status("original").put("account", other))
        val quote = status("quote").put("quoted_status", status("quoted").put("account", other))
        server.enqueue(MockResponse().setBody(JSONArray().put(root).put(media).put(reply).put(selfReply).put(boost).put(quote).toString()))

        val page = source().profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Posts))

        assertEquals(setOf("root", "media", "quote"), page.items.map { it.id.value }.toSet())
        assertEquals(0, page.items.count { it.id.value == "boost" })
        assertEquals("/api/v1/accounts/local-user/statuses", server.takeRequest().requestUrl?.encodedPath)
    }

    @Test
    fun profileTimelineSendsCategoryHintsAndLimit() = runBlocking {
        val target = AccountId(Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), "local-user")
        val timelineTabs = ProfileTimelineTab.entries.filter { it != ProfileTimelineTab.Liked }
        timelineTabs.forEach { tab ->
            server.enqueue(MockResponse().setBody("[${status("${tab.name}-row")} ]"))
        }
        server.enqueue(MockResponse().setBody("[${status("liked-row")}]"))
        val source = source()

        timelineTabs.forEach { tab ->
            source.profileTimeline(ProfileTimelineQuery(target, tab))
        }
        val liked = source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Liked))

        val expected = mapOf(
            ProfileTimelineTab.Posts to mapOf("exclude_replies" to "true", "exclude_reblogs" to "true"),
            ProfileTimelineTab.Media to mapOf("only_media" to "true", "exclude_reblogs" to "true"),
            ProfileTimelineTab.Reposts to mapOf("exclude_replies" to "true", "exclude_reblogs" to "false"),
            ProfileTimelineTab.Replies to mapOf("exclude_replies" to "false", "exclude_reblogs" to "true"),
        )
        timelineTabs.forEach { tab ->
            val request = server.takeRequest()
            val url = request.requestUrl ?: error("Missing request URL")
            assertEquals("40", url.queryParameter("limit"))
            expected.getValue(tab).forEach { (name, value) -> assertEquals(value, url.queryParameter(name)) }
        }
        // The Liked tab uses the favourites endpoint, not the account statuses endpoint.
        val likedRequest = server.takeRequest()
        assertEquals("/api/v1/favourites", likedRequest.requestUrl?.encodedPath)
        assertEquals("40", likedRequest.requestUrl?.queryParameter("limit"))
        assertEquals(listOf("liked-row"), liked.items.map { it.id.value })
    }

    @Test
    fun profileTimelineReusesValidatedLinkCursorAndRejectsForeignCursor() = runBlocking {
        val target = AccountId(Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), "local-user")
        server.enqueue(MockResponse().setBody("[${status("newest")}] ").addHeader(
            "Link", "<$origin/api/v1/accounts/local-user/statuses?limit=40&max_id=newest>; rel=\"next\"",
        ))
        server.enqueue(MockResponse().setBody("[${status("older")}]"))
        val source = source()

        val first = source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Posts))
        val second = source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Posts), first.nextCursor)

        assertEquals("newest", first.items.single().id.value)
        assertEquals("older", second.items.single().id.value)
        val initialRequest = server.takeRequest()
        val continuationRequest = server.takeRequest()
        assertEquals("/api/v1/accounts/local-user/statuses?limit=40&exclude_replies=true&exclude_reblogs=true", initialRequest.path)
        assertEquals("/api/v1/accounts/local-user/statuses?limit=40&max_id=newest", continuationRequest.path)
        assertEquals("Bearer token", continuationRequest.getHeader("Authorization"))

        MockWebServer().use { foreign ->
            val foreignCursor = foreign.url("/api/v1/accounts/local-user/statuses?max_id=foreign").toString()
            assertThrows(SourceError.Unsupported::class.java) {
                runBlocking {
                    source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Posts), foreignCursor)
                }
            }
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun profileLikedTimelineUsesFavouritesForSelfAndRejectsAnotherAccount() = runBlocking {
        val target = AccountId(Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), "local-user")
        server.enqueue(MockResponse().setBody("[${status("liked")}]"))
        val source = source()

        val page = source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Liked))

        assertEquals(listOf("liked"), page.items.map { it.id.value })
        val request = server.takeRequest()
        assertEquals("/api/v1/favourites", request.requestUrl?.encodedPath)
        assertEquals("40", request.requestUrl?.queryParameter("limit"))

        // Mastodon exposes favourites only for the signed-in account.
        val other = AccountId(Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), "other-user")
        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.profileTimeline(ProfileTimelineQuery(other, ProfileTimelineTab.Liked)) }
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun profileRelationshipFollowUnfollowAndPinnedPostsUseTargetBoundEndpoints() = runBlocking {
        val target = AccountId(Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), "local-user")
        server.enqueue(MockResponse().setBody("[{\"id\":\"local-user\",\"following\":false,\"followed_by\":true,\"requested\":false}]"))
        server.enqueue(MockResponse().setBody("{\"following\":true,\"followed_by\":true,\"requested\":false}"))
        server.enqueue(MockResponse().setBody("{\"following\":false,\"followed_by\":true,\"requested\":false}"))
        server.enqueue(MockResponse().setBody("[${status("pinned")},${status("foreign-pinned").put("account", account("other-user", "other", "Other"))}]"))
        server.enqueue(MockResponse().setResponseCode(404).setBody("{\"error\":\"unsupported\"}"))
        val source = source()

        assertTrue(source.profileRelationship(target).followedBy)
        assertTrue(source.followProfile(target).following)
        assertFalse(source.unfollowProfile(target).following)
        assertEquals(listOf("pinned"), source.pinnedPosts(target).map { it.id.value })
        assertTrue(source.pinnedPosts(target).isEmpty())

        assertEquals("/api/v1/accounts/relationships?id%5B%5D=local-user", server.takeRequest().path)
        assertEquals("/api/v1/accounts/local-user/follow", server.takeRequest().path)
        assertEquals("/api/v1/accounts/local-user/unfollow", server.takeRequest().path)
        assertEquals("/api/v1/accounts/local-user/statuses?pinned=true&limit=40", server.takeRequest().path)
        assertEquals("/api/v1/accounts/local-user/statuses?pinned=true&limit=40", server.takeRequest().path)
    }

    @Test
    fun profileDetailsEscapesOpaqueAccountIdPathSegment() = runBlocking {
        val id = "segment/with?query"
        val accountId = AccountId(Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), id)
        server.enqueue(MockResponse().setBody(JSONObject(localAccount.toString()).put("id", id).toString()))

        source().profile(accountId)

        assertEquals("/api/v1/accounts/segment%2Fwith%3Fquery", server.takeRequest().path)
    }

    @Test
    fun sourceRejectsUnsupportedCreateFieldsBeforeNetworkRequests() = runBlocking {
        val requestOrigin = server.url("/").toString().removeSuffix("/")
        val unsupported = listOf(
            CreatePostRequest("text", quoteOf = EntityId(requestOrigin, "quoted")),
            CreatePostRequest("text", attachments = listOf(me.foxtails.palustris.domain.Attachment("https://example.org/photo.jpg", "image/jpeg", null))),
            CreatePostRequest("text", poll = me.foxtails.palustris.domain.PollRequest(listOf("yes", "no"))),
        )

        unsupported.forEach { request ->
            assertThrows(SourceError.Unsupported::class.java) {
                runBlocking { source().create(request) }
            }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun mapperHandlesMastodonQuoteEnvelopeStates() {
        val quoted = status("quoted").put("content", "<p>Quoted</p>")
        val accepted = status("accepted").put("quote", JSONObject()
            .put("state", "accepted")
            .put("quoted_status", quoted))
        assertEquals("quoted", MastodonMapper.post(accepted, origin).quote?.id?.value)

        listOf("pending", "rejected", "deleted", "blocked", "unknown").forEach { state ->
            val unavailable = status("$state-quote").put("quote", JSONObject()
                .put("state", state)
                .put("quoted_status", quoted))
            assertNull(MastodonMapper.post(unavailable, origin).quote)
        }
    }

    @Test
    fun paginationRejectsForeignHostsChangedPortsAndSchemeChangesBeforeSendingBearer() = runBlocking {
        MockWebServer().use { other ->
            server.enqueue(MockResponse().setBody("[${status("newest")}]"))
            val source = source()
            val first = source.timeline(me.foxtails.palustris.domain.Timeline.Home)
            val invalidCursors = listOf(
                other.url("/api/v1/timelines/home?max_id=1").toString(),
                "${origin.replace(Regex(":\\d+$"), ":${other.port}")}/api/v1/timelines/home?max_id=1",
                "https://${server.hostName}:${server.port}/api/v1/timelines/home?max_id=1",
            )
            invalidCursors.forEach { cursor ->
                try {
                    source.timeline(me.foxtails.palustris.domain.Timeline.Home, cursor)
                    throw AssertionError("Invalid pagination URL should be rejected")
                } catch (_: SourceError.Unsupported) {
                    // Expected: bearer credentials must not be sent to this URL.
                }
            }
            assertEquals("newest", first.items.single().id.value)
            assertEquals(0, other.requestCount)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun sourceMapsMastodonErrors() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("{\"error\":\"missing\"}"))

        try {
            source().post(EntityId(origin, "missing"))
            throw AssertionError("Missing status should fail")
        } catch (error: SourceError.Unsupported) {
            assertEquals("requested feature", error.feature)
        }
    }

    private fun source() = MastodonSource(
        origin = origin,
        token = "token",
        api = mastodonTestClient(),
        accountId = AccountId(Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), "local-user"),
    )

    private fun sourceWith(capabilities: ServerCapabilities) = MastodonSource(
        origin = origin,
        token = "token",
        api = mastodonTestClient(),
        accountId = AccountId(Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), "local-user"),
        initialCapabilities = capabilities,
    )

    private fun editableCapabilities(
        advancedSettings: Boolean = false,
        imageDescriptions: Boolean = false,
    ): EditableProfileCapabilities = EditableProfileCapabilities(
        read = CapabilityStatus.Supported,
        update = CapabilityStatus.Supported,
        advancedSettings = if (advancedSettings) CapabilityStatus.Supported else CapabilityStatus.Unsupported,
        imageDescriptions = if (imageDescriptions) CapabilityStatus.Supported else CapabilityStatus.Unsupported,
    )

    private fun editableSource(editable: EditableProfileCapabilities) = sourceWith(
        ServerCapabilities(
            timelines = setOf(me.foxtails.palustris.domain.Timeline.Home),
            profile = ProfileCapabilities(editable = editable),
            capabilitySchemaVersion = ServerCapabilities.CURRENT_CAPABILITY_SCHEMA_VERSION,
        ),
    )

    private fun reactionCapabilities() = ServerCapabilities(
        timelines = setOf(me.foxtails.palustris.domain.Timeline.Home),
        actions = setOf(PostAction.Reply, PostAction.Favorite, PostAction.React),
        canPublish = true,
        emoji = EmojiCapabilities(
            catalog = CapabilityStatus.Supported,
            reactionListing = CapabilityStatus.Supported,
            reactionMutation = CapabilityStatus.Supported,
            selectionMode = me.foxtails.palustris.domain.ReactionSelectionMode.Independent,
        ),
        capabilitySchemaVersion = ServerCapabilities.CURRENT_CAPABILITY_SCHEMA_VERSION,
    )

    private fun reactionSource() = sourceWith(reactionCapabilities())

    /** A source with a probe and a stale schema revision, so the next request re-probes. */
    private fun staleSchemaSource(
        probe: CapabilityProbe,
        clock: () -> Long = { 0L },
        onCapabilitiesUpdated: ((ServerCapabilities) -> Unit)? = null,
    ) = MastodonSource(
        origin = origin,
        token = "token",
        api = mastodonTestClient(),
        accountId = AccountId(Connection(origin, Protocol.MASTODON), "local-user"),
        initialCapabilities = ServerCapabilities(
            timelines = setOf(me.foxtails.palustris.domain.Timeline.Home),
            capabilitySchemaVersion = 0,
        ),
        capabilityProbe = probe,
        clock = clock,
        onCapabilitiesUpdated = onCapabilitiesUpdated,
    )


    private fun profileResponse(id: String) = JSONObject()
        .put("id", id)
        .put("display_name", "Self")
        .put("note", "<p>Raw <b>note</b></p>")
        .put("fields", JSONArray()
            .put(JSONObject().put("name", "Site").put("value", "<a href=\"https://example.org\">site</a>")))
        .put("avatar", JSONObject.NULL)
        .put("header", JSONObject.NULL)
        .put("locked", false)
        .put("bot", false)
        .put("hide_collections", false)
        .put("discoverable", false)
        .put("indexable", false)
        .put("show_media", false)
        .put("show_media_replies", false)
        .put("show_featured", false)
        .put("attribution_domains", JSONArray())

    private fun legacyCredentialResponse(id: String) = JSONObject()
        .put("id", id)
        .put("username", "alice")
        .put("acct", "alice")
        .put("display_name", "Self")
        .put("note", "<p>Rendered note</p>")
        .put("fields", JSONArray()
            .put(JSONObject().put("name", "Site").put("value", "<a href=\"https://example.org\">site</a>")))
        .put("source", JSONObject()
            .put("note", "Plaintext note")
            .put("fields", JSONArray()
                .put(JSONObject().put("name", "Site").put("value", "https://example.org"))))

    private fun status(id: String) = JSONObject()
        .put("id", id)
        .put("created_at", "2026-09-06T10:00:00Z")
        .put("account", localAccount)
        .put("content", "<p>Original</p>")
        .put("visibility", "public")

    private fun account(id: String, username: String, displayName: String) =
        JSONObject(localAccount.toString())
            .put("id", id)
            .put("username", username)
            .put("acct", username)
            .put("display_name", displayName)

    private fun notification(id: String) = JSONObject()
        .put("id", id)
        .put("type", "mention")
        .put("created_at", "2026-09-06T10:00:00Z")
        .put("account", localAccount)
        .put("status", status("notification-status"))
}
