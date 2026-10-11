package me.foxtails.palustris.ui.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.adjustedBy
import me.foxtails.palustris.domain.afterDeletion
import me.foxtails.palustris.domain.hashtags.HashtagExpansionInput
import me.foxtails.palustris.domain.hashtags.HashtagQuery
import me.foxtails.palustris.domain.mergeExternalActionFields
import me.foxtails.palustris.domain.search.SearchQuery
import me.foxtails.palustris.domain.search.SearchSuggestions
import me.foxtails.palustris.domain.search.classifySearchQuery
import me.foxtails.palustris.ui.UiStrings
import me.foxtails.palustris.ui.links.PostLinkResolver

/**
 * Owns the Search state of one session: the suggestions for the typed text and the result set of the last
 * submitted query. One generation counter guards each of the two, so a stale answer never replaces a newer
 * one. The Photo Grid reads [state] and pages through [loadMore]. It never fetches search pages itself.
 */
internal class SearchController(
    private val source: SocialSource,
    private val scope: CoroutineScope,
    private val applyFavouritePreference: (Post) -> Post,
    private val onStateChanged: (SearchState) -> Unit,
    private val uiStrings: UiStrings = UiStrings.Default,
    private val hashtagInput: () -> HashtagExpansionInput = { HashtagExpansionInput.Disabled },
    private val linkResolver: () -> PostLinkResolver? = { null },
    private val suggestionDebounceMillis: Long = SUGGESTION_DEBOUNCE_MILLIS,
) {
    private var searchJob: Job? = null
    private var suggestionJob: Job? = null
    private var generation = 0L
    private var suggestionGeneration = 0L
    private var stopped = false
    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()

    /** Starts a search. A hashtag follows the Combine related hashtags setting at this moment. */
    fun search(query: String) = start(query, combineRelated = null)

    /** Starts a search that ignores the setting. "Show only" uses it to run the plain hashtag. */
    fun searchWithoutRelated(query: String) = start(query, combineRelated = false)

    /**
     * Reports the text of the field. After a quiet period of [suggestionDebounceMillis] it asks the
     * source for suggestions. A blank text or a link clears them at once.
     */
    fun suggest(text: String) {
        if (stopped) return
        suggestionJob?.cancel()
        val requestGeneration = ++suggestionGeneration
        val parsed = classifySearchQuery(text)
        if (parsed is SearchQuery.Blank || parsed is SearchQuery.Url) {
            if (!_state.value.suggestions.isEmpty) publish(_state.value.copy(suggestions = SearchSuggestions(), suggestionsFor = ""))
            return
        }
        suggestionJob = scope.launch {
            delay(suggestionDebounceMillis)
            val answer = try {
                source.searchSuggestions(parsed).limited()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // A failed suggestion call is not an error screen. The panel stays empty.
                SearchSuggestions()
            }
            if (stopped || requestGeneration != suggestionGeneration) return@launch
            publish(_state.value.copy(suggestions = answer, suggestionsFor = parsed.text))
        }
    }

    private fun start(query: String, combineRelated: Boolean?) {
        if (stopped) return
        val normalized = query.trim()
        if (normalized.isBlank()) return
        searchJob?.cancel()
        suggestionJob?.cancel()
        suggestionGeneration++
        val requestGeneration = ++generation
        val parsed = classifySearchQuery(normalized)
        val kept = _state.value.suggestions to _state.value.suggestionsFor
        when (parsed) {
            is SearchQuery.Hashtag -> {
                // The expansion is pure and fixed once here. Load-more reuses the stored extras.
                val input = hashtagInput()
                val expansion = input.expand(normalized, source.maxCombinedHashtags, combineRelated ?: input.enabled)
                publish(
                    SearchState(
                        query = normalized,
                        mode = SearchMode.Hashtag,
                        tagQuery = parsed.text,
                        combinedTags = expansion.applied,
                        relatedTags = expansion.related,
                        loading = true,
                        suggestions = kept.first,
                        suggestionsFor = kept.second,
                    ),
                )
                searchJob = scope.launch { searchHashtag(normalized, parsed.text, requestGeneration) }
            }
            is SearchQuery.FreeText -> {
                publish(freshState(normalized, SearchMode.Posts, kept))
                searchJob = scope.launch { searchPosts(normalized, requestGeneration) }
            }
            is SearchQuery.Handle -> {
                publish(freshState(normalized, SearchMode.Lookup, kept))
                searchJob = scope.launch { lookupHandle(normalized, parsed, requestGeneration) }
            }
            is SearchQuery.Url -> {
                publish(freshState(normalized, SearchMode.Lookup, kept))
                searchJob = scope.launch { lookupUrl(normalized, requestGeneration) }
            }
            SearchQuery.Blank -> Unit
        }
    }

    private fun freshState(query: String, mode: SearchMode, kept: Pair<SearchSuggestions, String>) = SearchState(
        query = query,
        mode = mode,
        loading = true,
        suggestions = kept.first,
        suggestionsFor = kept.second,
    )

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

    fun updatePost(id: EntityId, transform: (Post) -> Post) {
        publish(
            _state.value.copy(
                posts = _state.value.posts.map { post ->
                    if (post.id == id || post.actionTargetId == id) transform(post) else post
                },
            ),
        )
    }

    fun updateExternalPost(target: EntityId, incoming: Post) {
        publish(
            _state.value.copy(
                posts = _state.value.posts.map { post ->
                    if (post.id == target || post.actionTargetId == target) mergeExternalActionFields(post, incoming) else post
                },
            ),
        )
    }

    fun removeDeletedPost(deleted: Post) {
        publish(_state.value.copy(posts = _state.value.posts.afterDeletion(deleted)))
    }

    fun updatePosts(transform: (Post) -> Post) {
        publish(_state.value.copy(posts = _state.value.posts.map(transform)))
    }

    fun applyPublishedPost(request: CreatePostRequest) {
        if (stopped) return
        request.replyTo?.let { target ->
            updatePost(target) { post ->
                post.copy(
                    interactionCounts = post.interactionCounts.copy(
                        replyCount = post.interactionCounts.replyCount.adjustedBy(1),
                    ),
                )
            }
        }
        request.quoteOf?.let { target ->
            updatePost(target) { post ->
                post.copy(
                    interactionCounts = post.interactionCounts.copy(
                        quoteRepostCount = post.interactionCounts.quoteRepostCount.adjustedBy(1),
                    ),
                )
            }
        }
    }

    fun stop() {
        if (stopped) return
        stopped = true
        searchJob?.cancel()
        suggestionJob?.cancel()
        generation++
        suggestionGeneration++
    }

    private suspend fun searchPosts(normalized: String, requestGeneration: Long) {
        try {
            val page = source.searchPosts(normalized)
            if (stopped || requestGeneration != generation) return
            publish(
                _state.value.copy(
                    posts = page.items.distinctBy { it.id }.map(applyFavouritePreference),
                    loading = false,
                    nextCursor = page.nextCursor,
                ),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (stopped || requestGeneration != generation) return
            val denied = e is SourceError.AccessDenied
            publish(
                _state.value.copy(
                    loading = false,
                    postSearchDenied = denied,
                    error = if (denied) null else uiStrings.sourceError(e),
                ),
            )
        }
    }

    private suspend fun lookupHandle(normalized: String, handle: SearchQuery.Handle, requestGeneration: Long) {
        try {
            val account = source.searchAccounts(handle.text).firstOrNull()
            if (stopped || requestGeneration != generation) return
            publish(
                _state.value.copy(
                    loading = false,
                    target = account?.let { SearchTarget.Profile(it) },
                    error = if (account == null) uiStrings.sourceError(SourceError.Unsupported("account")) else null,
                ),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (stopped || requestGeneration != generation) return
            publish(_state.value.copy(loading = false, error = uiStrings.sourceError(e)))
        }
    }

    /**
     * A link opens the post or profile it names. A link that is neither runs as a text search, because a
     * server may still know the address as text.
     */
    private suspend fun lookupUrl(url: String, requestGeneration: Long) {
        val resolver = linkResolver()
        try {
            val post = resolver?.resolve(url)
            val account = if (post == null) resolver?.resolveAccount(url) else null
            if (stopped || requestGeneration != generation) return
            val target = post?.let { SearchTarget.SinglePost(it.post) } ?: account?.let { SearchTarget.Profile(it) }
            if (target != null) {
                publish(_state.value.copy(loading = false, target = target))
            } else {
                publish(freshState(url, SearchMode.Posts, _state.value.suggestions to _state.value.suggestionsFor))
                searchPosts(url, requestGeneration)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (stopped || requestGeneration != generation) return
            publish(_state.value.copy(loading = false, error = uiStrings.sourceError(e)))
        }
    }

    private suspend fun searchHashtag(normalized: String, tag: String, requestGeneration: Long) {
        // The loading state that start() published holds the expansion for this search.
        val loading = _state.value
        try {
            val page = source.searchHashtags(HashtagQuery(normalized, loading.combinedTags))
            if (stopped || requestGeneration != generation) return
            publish(
                loading.copy(
                    posts = page.items.distinctBy { it.id }.map(applyFavouritePreference),
                    loading = false,
                    nextCursor = page.nextCursor,
                ),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (stopped || requestGeneration != generation) return
            publish(SearchState(query = normalized, mode = SearchMode.Hashtag, tagQuery = tag, error = uiStrings.sourceError(e)))
        }
    }

    private fun mergeExternalActionFields(existing: Post, incoming: Post): Post =
        existing.mergeExternalActionFields(incoming)

    private fun publish(next: SearchState) {
        _state.value = next
        onStateChanged(next)
    }

    private companion object {
        const val SUGGESTION_DEBOUNCE_MILLIS = 300L
    }
}
