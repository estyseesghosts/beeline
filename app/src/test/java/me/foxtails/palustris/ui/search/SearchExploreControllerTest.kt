package me.foxtails.palustris.ui.search

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.foxtails.palustris.domain.AppLanguage
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.hashtags.HashtagCatalog
import me.foxtails.palustris.domain.hashtags.HashtagLanguagePolicy
import me.foxtails.palustris.domain.hashtags.HashtagSuggestion
import me.foxtails.palustris.domain.hashtags.HashtagSuggestionService
import me.foxtails.palustris.domain.hashtags.TrendingHashtag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchExploreControllerTest {
    private class Source : SocialSource {
        override val capabilities = ServerCapabilities()
        var trendingCalls = 0
        var trending: suspend () -> List<TrendingHashtag> = { emptyList() }
        val prefixes = mutableListOf<String>()

        override suspend fun timeline(timeline: Timeline, cursor: String?) = Page<Post>(emptyList())
        override suspend fun trendingHashtags(limit: Int): List<TrendingHashtag> {
            trendingCalls++
            return trending()
        }
        override suspend fun suggestHashtags(prefix: String, limit: Int): List<HashtagSuggestion> {
            prefixes += prefix
            return listOf(HashtagSuggestion("${prefix}x", 3.0))
        }
    }

    private fun controller(scope: TestScope, source: Source) = SearchExploreController(
        source = source,
        scope = scope,
        suggestionService = HashtagSuggestionService(
            catalog = HashtagCatalog.Empty,
            accountKey = "test",
            policy = { HashtagLanguagePolicy.forLanguage(AppLanguage.English) },
            fetchServer = source::suggestHashtags,
            clock = { scope.testScheduler.currentTime * 1_000_000L },
        ),
    )

    @Test
    fun trendingLoadsOnceAndDropsDuplicateNames() = runTest {
        val source = Source().apply {
            trending = { listOf(TrendingHashtag("cats", 5, 9), TrendingHashtag("cats", 5, 9), TrendingHashtag("art", null, null)) }
        }
        val explore = controller(this, source)

        explore.loadTrending()
        advanceUntilIdle()
        explore.loadTrending()
        advanceUntilIdle()

        assertEquals(listOf("cats", "art"), explore.state.value.trending.map { it.name })
        assertEquals(1, source.trendingCalls)
        explore.stop()
    }

    @Test
    fun aFailedTrendingRequestLeavesTheListEmptyAndTheNextCallRetries() = runTest {
        val source = Source().apply { trending = { error("down") } }
        val explore = controller(this, source)

        explore.loadTrending()
        advanceUntilIdle()
        assertTrue(explore.state.value.trending.isEmpty())

        source.trending = { listOf(TrendingHashtag("cats", 5, 9)) }
        explore.loadTrending()
        advanceUntilIdle()

        assertEquals(2, source.trendingCalls)
        assertEquals(listOf("cats"), explore.state.value.trending.map { it.name })
        explore.stop()
    }

    @Test
    fun anUnsupportedSourceLeavesTheListEmpty() = runTest {
        val source = object : SocialSource {
            override val capabilities = ServerCapabilities()
            override suspend fun timeline(timeline: Timeline, cursor: String?) = Page<Post>(emptyList())
        }
        val explore = SearchExploreController(
            source,
            this,
            HashtagSuggestionService(
                HashtagCatalog.Empty,
                "test",
                { HashtagLanguagePolicy.forLanguage(AppLanguage.English) },
                source::suggestHashtags,
            ),
        )

        explore.loadTrending()
        advanceUntilIdle()

        assertTrue(explore.state.value.trending.isEmpty())
        explore.stop()
    }

    @Test
    fun suggestionsWaitForTheDebounceAndOnlyTheLastPrefixReachesTheServer() = runTest {
        val source = Source()
        val explore = controller(this, source)

        explore.suggest("#ph")
        runCurrent()
        advanceTimeBy(100)
        explore.suggest("#pho")
        advanceTimeBy(100)
        assertTrue(explore.state.value.suggestions.isEmpty())
        advanceTimeBy(300)
        runCurrent()

        assertEquals(listOf("pho"), source.prefixes)
        assertEquals(listOf("phox"), explore.state.value.suggestions.map { it.name })
        explore.stop()
    }

    @Test
    fun aBlankFieldClearsTheSuggestionsAtOnce() = runTest {
        val source = Source()
        val explore = controller(this, source)
        explore.suggest("#pho")
        advanceTimeBy(300)
        runCurrent()
        assertTrue(explore.state.value.suggestions.isNotEmpty())

        explore.suggest("")
        runCurrent()

        assertTrue(explore.state.value.suggestions.isEmpty())
        explore.stop()
    }

    @Test
    fun stoppingCancelsAPendingRequestAndRejectsItsLateResult() = runTest {
        val gate = CompletableDeferred<List<TrendingHashtag>>()
        val source = Source().apply { trending = { gate.await() } }
        val explore = controller(this, source)
        explore.loadTrending()
        runCurrent()

        explore.stop()
        gate.complete(listOf(TrendingHashtag("late", 1, 1)))
        advanceUntilIdle()

        assertTrue(explore.state.value.trending.isEmpty())
        explore.loadTrending()
        advanceUntilIdle()
        assertEquals(1, source.trendingCalls)
    }

    @Test
    fun stoppingForgetsCachedSuggestions() = runTest {
        val source = Source()
        val service = HashtagSuggestionService(
            HashtagCatalog.Empty,
            "test",
            { HashtagLanguagePolicy.forLanguage(AppLanguage.English) },
            source::suggestHashtags,
        )
        val explore = SearchExploreController(source, this, service)
        explore.suggest("#pho")
        advanceTimeBy(300)
        runCurrent()
        service.suggest("pho", 5)
        assertEquals(listOf("pho"), source.prefixes)

        explore.stop()
        service.suggest("pho", 5)

        assertEquals(listOf("pho", "pho"), source.prefixes)
    }
}
