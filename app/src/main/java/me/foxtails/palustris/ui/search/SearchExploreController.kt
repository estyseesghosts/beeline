package me.foxtails.palustris.ui.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.hashtags.HashtagSuggestion
import me.foxtails.palustris.domain.hashtags.HashtagSuggestionService
import me.foxtails.palustris.domain.hashtags.TrendingHashtag

/** What Search shows before a query is submitted. An empty list means "show the old prompt". */
data class SearchExploreState(
    val trending: List<TrendingHashtag> = emptyList(),
    val popularAccounts: List<Account> = emptyList(),
    val suggestions: List<HashtagSuggestion> = emptyList(),
)

/** What the Search screen asks of its owner for the lists above. Each default does nothing. */
interface SearchExploreActions {
    /** Loads trending hashtags once. The list stays empty when the server cannot supply it. */
    fun loadTrending() = Unit

    /** Loads popular accounts once. The list stays empty when the server cannot supply it. */
    fun loadPopularAccounts() = Unit

    /** Reports the hashtag that the user is typing, so the owner can fetch suggestions. */
    fun suggestHashtags(text: String) = Unit

    companion object {
        val None = object : SearchExploreActions {}
    }
}

/**
 * Loads the discovery lists of one [SearchOwner]: trending hashtags, popular accounts, and the
 * suggestions for the hashtag that the user is typing. A failed or empty request leaves its list
 * empty and shows no error. [stop] ends every request and the suggestion collector.
 */
internal class SearchExploreController(
    private val source: SocialSource,
    private val scope: CoroutineScope,
    private val suggestionService: HashtagSuggestionService,
) {
    private val _state = MutableStateFlow(SearchExploreState())
    val state: StateFlow<SearchExploreState> = _state.asStateFlow()

    private var trendingJob: Job? = null
    private var accountsJob: Job? = null
    private var suggestionJob: Job? = null
    private val typed = MutableStateFlow("")
    private var stopped = false

    /** Loads trending hashtags once. A later call retries only after an empty or failed load. */
    fun loadTrending() {
        if (stopped || trendingJob?.isActive == true || _state.value.trending.isNotEmpty()) return
        trendingJob = scope.launch {
            val trending = fetchOrEmpty { source.trendingHashtags(TRENDING_LIMIT) }
            if (!stopped) _state.value = _state.value.copy(trending = trending.distinctBy { it.name })
        }
    }

    /** Loads popular accounts once. A later call retries only after an empty or failed load. */
    fun loadPopularAccounts() {
        if (stopped || accountsJob?.isActive == true || _state.value.popularAccounts.isNotEmpty()) return
        accountsJob = scope.launch {
            val accounts = fetchOrEmpty { source.popularAccounts(POPULAR_ACCOUNTS_LIMIT) }
            if (!stopped) _state.value = _state.value.copy(popularAccounts = accounts.distinctBy { it.id })
        }
    }

    /** Reports the text of the hashtag field. The service debounces it and drops stale requests. */
    fun suggest(text: String) {
        if (stopped) return
        typed.value = text.trim()
        if (typed.value.isBlank() && _state.value.suggestions.isNotEmpty()) {
            _state.value = _state.value.copy(suggestions = emptyList())
        }
        if (suggestionJob != null) return
        suggestionJob = scope.launch {
            suggestionService.suggestions(typed, HashtagSuggestionService.SEARCH_LIMIT).collect { suggestions ->
                if (!stopped) {
                    _state.value = _state.value.copy(suggestions = suggestions.takeIf { typed.value.isNotBlank() }.orEmpty())
                }
            }
        }
    }

    fun stop() {
        if (stopped) return
        stopped = true
        trendingJob?.cancel()
        accountsJob?.cancel()
        suggestionJob?.cancel()
        suggestionService.release()
    }

    private suspend fun <T> fetchOrEmpty(request: suspend () -> List<T>): List<T> = try {
        request()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    private companion object {
        const val TRENDING_LIMIT = 20
        const val POPULAR_ACCOUNTS_LIMIT = 20
    }
}
