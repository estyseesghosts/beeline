package me.foxtails.palustris.ui.search

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.hashtags.HashtagSuggestion
import me.foxtails.palustris.domain.search.SearchQuery
import me.foxtails.palustris.domain.search.SearchSuggestions
import me.foxtails.palustris.ui.links.PostLinkResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun SearchQuery.textOrEmpty(): String = when (this) {
    is SearchQuery.Handle -> text
    is SearchQuery.Hashtag -> text
    is SearchQuery.Url -> text
    is SearchQuery.FreeText -> text
    SearchQuery.Blank -> ""
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchControllerTest {
    private val connection = Connection("https://search.example", Protocol.MASTODON)
    private val accountId = AccountId(connection, "owner")
    private val owner = Account(accountId, "Owner", "@owner@search.example")

    private fun post(id: String) = Post(EntityId(connection.origin, id), owner, id, 0L, Audience.Public)

    private open class FakeSource : SocialSource {
        override val capabilities = ServerCapabilities()
        val suggestionQueries = mutableListOf<String>()
        val postQueries = mutableListOf<Pair<String, String?>>()
        var suggestions: suspend (SearchQuery) -> SearchSuggestions = { SearchSuggestions() }
        var posts: suspend (String, String?) -> Page<Post> = { _, _ -> Page(emptyList()) }
        var accounts: suspend (String) -> List<Account> = { emptyList() }

        override suspend fun timeline(timeline: Timeline, cursor: String?) = Page<Post>(emptyList())

        override suspend fun searchSuggestions(query: SearchQuery): SearchSuggestions {
            suggestionQueries += query.textOrEmpty()
            return suggestions(query)
        }

        override suspend fun searchPosts(query: String, cursor: String?): Page<Post> {
            postQueries += query to cursor
            return posts(query, cursor)
        }

        override suspend fun searchAccounts(query: String): List<Account> = accounts(query)
    }

    private fun controller(
        scope: TestScope,
        source: SocialSource,
        resolver: PostLinkResolver? = null,
    ) = SearchController(
        source = source,
        scope = scope,
        applyFavouritePreference = { it },
        onStateChanged = {},
        linkResolver = { resolver },
        suggestionDebounceMillis = 300,
    )

    @Test
    fun suggestionsWaitForAQuietPeriodAndAskOnceForTheLastText() = runTest {
        val source = FakeSource()
        val controller = controller(this, source)

        controller.suggest("p")
        advanceTimeBy(100)
        controller.suggest("ph")
        advanceTimeBy(100)
        controller.suggest("pho")
        advanceTimeBy(299)
        assertTrue(source.suggestionQueries.isEmpty())
        advanceTimeBy(2)
        advanceUntilIdle()

        assertEquals(listOf("pho"), source.suggestionQueries)
        assertEquals("pho", controller.state.value.suggestionsFor)
    }

    @Test
    fun aSlowOlderSuggestionAnswerNeverReplacesANewerOne() = runTest {
        val source = FakeSource()
        val slow = CompletableDeferred<SearchSuggestions>()
        source.suggestions = { query ->
            if (query.textOrEmpty() == "old") slow.await() else SearchSuggestions(hashtags = listOf(HashtagSuggestion("new", null)))
        }
        val controller = controller(this, source)

        controller.suggest("old")
        advanceTimeBy(301)
        controller.suggest("new")
        advanceTimeBy(301)
        advanceUntilIdle()
        slow.complete(SearchSuggestions(hashtags = listOf(HashtagSuggestion("old", null))))
        advanceUntilIdle()

        assertEquals("new", controller.state.value.suggestionsFor)
        assertEquals(listOf("new"), controller.state.value.suggestions.hashtags.map { it.name })
    }

    @Test
    fun suggestionsKeepAtMostThreeAccountsAndSixHashtags() = runTest {
        val source = FakeSource()
        source.suggestions = {
            SearchSuggestions(
                accounts = (1..5).map { Account(AccountId(connection, "a$it"), "A$it", "@a$it@search.example") },
                hashtags = (1..9).map { HashtagSuggestion("tag$it", null) },
            )
        }
        val controller = controller(this, source)

        controller.suggest("a")
        advanceTimeBy(301)
        advanceUntilIdle()

        assertEquals(3, controller.state.value.suggestions.accounts.size)
        assertEquals(6, controller.state.value.suggestions.hashtags.size)
    }

    @Test
    fun aBlankOrLinkFieldClearsSuggestionsWithoutAskingTheServer() = runTest {
        val source = FakeSource()
        source.suggestions = { SearchSuggestions(hashtags = listOf(HashtagSuggestion("cats", null))) }
        val controller = controller(this, source)
        controller.suggest("cat")
        advanceTimeBy(301)
        advanceUntilIdle()
        assertTrue(controller.state.value.suggestions.hashtags.isNotEmpty())

        controller.suggest("https://example.org/@someone")
        advanceUntilIdle()

        assertTrue(controller.state.value.suggestions.isEmpty)
        assertEquals(listOf("cat"), source.suggestionQueries)
    }

    @Test
    fun aFailedSuggestionCallLeavesAnEmptyPanelAndNoError() = runTest {
        val source = FakeSource()
        source.suggestions = { throw SourceError.Unsupported("search.suggestions") }
        val controller = controller(this, source)

        controller.suggest("cat")
        advanceTimeBy(301)
        advanceUntilIdle()

        assertTrue(controller.state.value.suggestions.isEmpty)
        assertNull(controller.state.value.error)
    }

    @Test
    fun freeTextSearchesPostsAndLoadMoreUsesTheCursor() = runTest {
        val source = FakeSource()
        source.posts = { _, cursor ->
            if (cursor == null) Page(listOf(post("one")), "20") else Page(listOf(post("two")), null)
        }
        val controller = controller(this, source)

        controller.search("hello world")
        advanceUntilIdle()
        assertEquals(SearchMode.Posts, controller.state.value.mode)
        assertEquals(listOf("one"), controller.state.value.posts.map { it.id.value })
        controller.loadMore()
        advanceUntilIdle()

        assertEquals(listOf("one", "two"), controller.state.value.posts.map { it.id.value })
        assertNull(controller.state.value.nextCursor)
        assertEquals(listOf("hello world" to null, "hello world" to "20"), source.postQueries)
    }

    @Test
    fun aRoleThatBlocksPostSearchIsADistinctState() = runTest {
        val source = FakeSource()
        source.posts = { _, _ -> throw SourceError.AccessDenied("search") }
        val controller = controller(this, source)

        controller.search("hello")
        advanceUntilIdle()

        assertTrue(controller.state.value.postSearchDenied)
        assertNull(controller.state.value.error)
        assertTrue(controller.state.value.posts.isEmpty())
    }

    @Test
    fun aHandleOpensTheProfileOnceAndTheTargetIsConsumed() = runTest {
        val source = FakeSource()
        source.accounts = { listOf(owner) }
        val controller = controller(this, source)

        controller.search("@owner@search.example")
        advanceUntilIdle()
        assertEquals(SearchTarget.Profile(owner), controller.state.value.target)
        assertTrue(source.postQueries.isEmpty())

        controller.consumeTarget()
        assertNull(controller.state.value.target)
    }

    @Test
    fun aHandleWithNoMatchShowsAnErrorInsteadOfAPostSearch() = runTest {
        val controller = controller(this, FakeSource())

        controller.search("@nobody@search.example")
        advanceUntilIdle()

        assertNull(controller.state.value.target)
        assertTrue(controller.state.value.error != null)
    }

    @Test
    fun aPostLinkOpensThePostAndAnUnknownLinkRunsAsTextSearch() = runTest {
        val known = OwnedPost(accountId, post("known"))
        val resolver = object : PostLinkResolver {
            override fun looksLikePost(url: String) = true
            override suspend fun resolve(url: String): OwnedPost? = known.takeIf { url.endsWith("/known") }
            override suspend fun load(id: EntityId): OwnedPost? = null
        }
        val source = FakeSource()
        val controller = controller(this, source, resolver)

        controller.search("https://search.example/@owner/known")
        advanceUntilIdle()
        assertEquals(SearchTarget.SinglePost(known.post), controller.state.value.target)

        controller.consumeTarget()
        controller.search("https://other.example/nothing")
        advanceUntilIdle()
        assertNull(controller.state.value.target)
        assertEquals(SearchMode.Posts, controller.state.value.mode)
        assertEquals(listOf("https://other.example/nothing"), source.postQueries.map { it.first })
    }

    @Test
    fun aSlowOlderSearchNeverReplacesANewerResultSet() = runTest {
        val source = FakeSource()
        source.posts = { query, _ ->
            if (query == "slow") delay(1_000)
            Page(listOf(post(query)), null)
        }
        val controller = controller(this, source)

        controller.search("slow")
        advanceTimeBy(10)
        controller.search("fast")
        advanceUntilIdle()

        assertEquals("fast", controller.state.value.query)
        assertEquals(listOf("fast"), controller.state.value.posts.map { it.id.value })
    }

    @Test
    fun resultsStayAvailableToThePhotoGridOnlyForPostAndHashtagModes() = runTest {
        val source = FakeSource()
        source.posts = { _, _ -> Page(listOf(post("one")), null) }
        source.accounts = { listOf(owner) }
        val controller = controller(this, source)

        controller.search("hello")
        advanceUntilIdle()
        assertTrue(controller.state.value.hasPostResults)

        controller.search("@owner@search.example")
        advanceUntilIdle()
        assertTrue(!controller.state.value.hasPostResults)
    }
}
