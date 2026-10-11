package me.foxtails.palustris.ui.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.afterDeletion
import me.foxtails.palustris.domain.hashtags.HashtagExpansionInput
import me.foxtails.palustris.domain.hashtags.HashtagQuery
import me.foxtails.palustris.domain.search.SearchQuery
import me.foxtails.palustris.domain.search.classifySearchQuery
import me.foxtails.palustris.ui.UiStrings
import me.foxtails.palustris.ui.links.PostLinkResolver

/**
 * Owns the Search state of one session: the suggestions for the typed text and the result set of the last
 * submitted query. One generation counter guards the results, and [SearchSuggester] guards the suggestions,
 * so a stale answer never replaces a newer one. The Photo Grid reads [state] and pages through [loadMore].
 * It never fetches search pages itself.
 */
internal class SearchController(
    private val source: SocialSource,
    private val scope: CoroutineScope,
    private val applyFavouritePreference: (Post) -> Post,
    private val onStateChanged: (SearchState) -> Unit,
    private val uiStrings: UiStrings = UiStrings.Default,
    private val hashtagInput: () -> HashtagExpansionInput = { HashtagExpansionInput.Disabled },
    linkResolver: () -> PostLinkResolver? = { null },
    suggestionDebounceMillis: Long = SUGGESTION_DEBOUNCE_MILLIS,
) {
    private var searchJob: Job? = null
    private var generation = 0L
    private var stopped = false
    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()

    private val suggester = SearchSuggester(source, scope, suggestionDebounceMillis) { text, suggestions ->
        if (!stopped && (text.isNotEmpty() || !_state.value.suggestions.isEmpty)) {
            publish(_state.value.copy(suggestions = suggestions, suggestionsFor = text))
        }
    }

    private val runner = SearchRunner(
        source, uiStrings, applyFavouritePreference, linkResolver,
        isStale = { stopped || it != generation },
        state = { _state.value },
        publish = ::publish,
    )

    /** Starts a search. A hashtag follows the Combine related hashtags setting at this moment. */
    fun search(query: String) = start(query, combineRelated = null)

    /** Starts a search that ignores the setting. "Show only" uses it to run the plain hashtag. */
    fun searchWithoutRelated(query: String) = start(query, combineRelated = false)

    /** Reports the text of the field. See [SearchSuggester] for the quiet period and the clearing rules. */
    fun suggest(text: String) {
        if (!stopped) suggester.suggest(text)
    }

    private fun start(query: String, combineRelated: Boolean?) {
        val normalized = query.trim()
        if (stopped || normalized.isBlank()) return
        searchJob?.cancel()
        suggester.cancel()
        val requestGeneration = ++generation
        val parsed = classifySearchQuery(normalized)
        val kept = _state.value.suggestions to _state.value.suggestionsFor
        val mode = when (parsed) {
            is SearchQuery.Hashtag -> SearchMode.Hashtag
            is SearchQuery.FreeText -> SearchMode.Posts
            else -> SearchMode.Lookup
        }
        // The expansion is pure and fixed once here. Load-more reuses the stored extras.
        val input = hashtagInput()
        val expansion = (parsed as? SearchQuery.Hashtag)
            ?.let { input.expand(normalized, source.maxCombinedHashtags, combineRelated ?: input.enabled) }
        publish(
            SearchState(
                query = normalized,
                mode = mode,
                tagQuery = (parsed as? SearchQuery.Hashtag)?.text,
                combinedTags = expansion?.applied.orEmpty(),
                relatedTags = expansion?.related.orEmpty(),
                loading = true,
                suggestions = kept.first,
                suggestionsFor = kept.second,
            ),
        )
        searchJob = scope.launch {
            when (parsed) {
                is SearchQuery.Hashtag -> runner.hashtag(normalized, requestGeneration)
                is SearchQuery.FreeText -> runner.posts(normalized, requestGeneration)
                is SearchQuery.Handle -> runner.handle(parsed, requestGeneration)
                is SearchQuery.Url -> runner.url(normalized, requestGeneration)
                SearchQuery.Blank -> Unit
            }
        }
    }

    fun loadMore() {
        if (stopped) return
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.loading || current.loadingMore) return
        if (current.mode != SearchMode.Posts && current.mode != SearchMode.Hashtag) return
        searchJob?.cancel()
        val requestGeneration = generation
        searchJob = scope.launch {
            publish(current.copy(loadingMore = true, error = null))
            try {
                val page = when (current.mode) {
                    SearchMode.Hashtag -> source.searchHashtags(HashtagQuery(current.tagQuery.orEmpty(), current.combinedTags), cursor)
                    else -> source.searchPosts(current.query, cursor)
                }
                if (stopped || requestGeneration != generation || _state.value.query != current.query) return@launch
                val latest = _state.value
                publish(
                    latest.copy(
                        posts = (latest.posts + page.items.map(applyFavouritePreference)).distinctBy { it.id },
                        loadingMore = false,
                        nextCursor = page.nextCursor?.takeUnless { it == cursor },
                    ),
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (stopped || requestGeneration != generation) return@launch
                publish(_state.value.copy(loadingMore = false, error = uiStrings.sourceError(e)))
            }
        }
    }

    /** Clears the opened target so a recomposition cannot open it twice. */
    fun consumeTarget() {
        if (_state.value.target != null) publish(_state.value.copy(target = null))
    }

    fun updatePost(id: EntityId, transform: (Post) -> Post) =
        publish(_state.value.copy(posts = _state.value.posts.withUpdated(id, transform)))

    fun updateExternalPost(target: EntityId, incoming: Post) =
        publish(_state.value.copy(posts = _state.value.posts.withExternal(target, incoming)))

    fun removeDeletedPost(deleted: Post) = publish(_state.value.copy(posts = _state.value.posts.afterDeletion(deleted)))

    fun updatePosts(transform: (Post) -> Post) = publish(_state.value.copy(posts = _state.value.posts.map(transform)))

    fun applyPublishedPost(request: CreatePostRequest) {
        if (!stopped) request.countChanges().forEach { (id, change) -> updatePost(id, change) }
    }

    fun stop() {
        if (stopped) return
        stopped = true
        searchJob?.cancel()
        suggester.cancel()
        generation++
    }

    private fun publish(next: SearchState) {
        _state.value = next
        onStateChanged(next)
    }

    private companion object {
        const val SUGGESTION_DEBOUNCE_MILLIS = 300L
    }
}
