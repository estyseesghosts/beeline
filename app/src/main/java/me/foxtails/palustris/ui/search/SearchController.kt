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
import me.foxtails.palustris.domain.adjustedBy
import me.foxtails.palustris.domain.hashtags.HashtagExpansion
import me.foxtails.palustris.domain.hashtags.HashtagExpansionInput
import me.foxtails.palustris.domain.hashtags.HashtagQuery
import me.foxtails.palustris.domain.isExactHashtag
import me.foxtails.palustris.domain.mergeExternalActionFields
import me.foxtails.palustris.ui.UiStrings

internal class SearchController(
    private val source: SocialSource,
    private val scope: CoroutineScope,
    private val applyFavouritePreference: (Post) -> Post,
    private val onStateChanged: (AccountSearchState) -> Unit,
    private val uiStrings: UiStrings = UiStrings.Default,
    private val hashtagInput: () -> HashtagExpansionInput = { HashtagExpansionInput.Disabled },
) {
    private var searchJob: Job? = null
    private var generation = 0L
    private var stopped = false
    private val _state = MutableStateFlow(AccountSearchState())
    val state = _state.asStateFlow()

    /** Starts a search. A hashtag follows the Combine related hashtags setting at this moment. */
    fun search(query: String) = start(query, combineRelated = null)

    /** Starts a search that ignores the setting. "Show only" uses it to run the plain hashtag. */
    fun searchWithoutRelated(query: String) = start(query, combineRelated = false)

    private fun start(query: String, combineRelated: Boolean?) {
        if (stopped) return
        val normalized = query.trim()
        if (normalized.isBlank()) return
        searchJob?.cancel()
        val requestGeneration = ++generation
        val isHashtag = isExactHashtag(normalized)
        // The expansion is pure and fixed once here. Load-more reuses the stored extras.
        val expansion = if (isHashtag) {
            val input = hashtagInput()
            input.expand(normalized, source.maxCombinedHashtags, combineRelated ?: input.enabled)
        } else {
            HashtagExpansion.None
        }
        publish(AccountSearchState(
            query = normalized,
            tagQuery = normalized.removePrefix("#").takeIf { isHashtag },
            combinedTags = expansion.applied,
            relatedTags = expansion.related,
            loading = true,
        ))
        searchJob = scope.launch {
            if (isHashtag) searchHashtag(normalized, requestGeneration)
            else searchAccount(normalized, requestGeneration)
        }
    }

    fun loadMore() {
        if (stopped) return
        val current = _state.value
        val tag = current.tagQuery ?: return
        val cursor = current.nextCursor ?: return
        if (current.loading || current.loadingMore) return
        searchJob?.cancel()
        val requestGeneration = generation
        searchJob = scope.launch {
            publish(current.copy(loadingMore = true, error = null))
            try {
                val page = source.searchHashtags(HashtagQuery(tag, current.combinedTags), cursor)
                if (stopped || requestGeneration != generation || _state.value.query != current.query) return@launch
                val latest = _state.value
                publish(latest.copy(
                    posts = (latest.posts + page.items.map(applyFavouritePreference)).distinctBy { it.id },
                    loadingMore = false,
                    nextCursor = page.nextCursor?.takeUnless { it == cursor },
                ))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (stopped || requestGeneration != generation) return@launch
                publish(_state.value.copy(loadingMore = false, error = uiStrings.sourceError(e)))
            }
        }
    }

    fun updatePost(id: EntityId, transform: (Post) -> Post) {
        publish(_state.value.copy(
            posts = _state.value.posts.map { post ->
                if (post.id == id || post.actionTargetId == id) transform(post) else post
            },
        ))
    }

    fun updateExternalPost(target: EntityId, incoming: Post) {
        publish(_state.value.copy(
            posts = _state.value.posts.map { post ->
                if (post.id == target || post.actionTargetId == target) mergeExternalActionFields(post, incoming) else post
            },
        ))
    }

    fun updatePosts(transform: (Post) -> Post) {
        publish(_state.value.copy(posts = _state.value.posts.map(transform)))
    }

    fun applyPublishedPost(request: CreatePostRequest) {
        if (stopped) return
        request.replyTo?.let { target ->
            updatePost(target) { post ->
                post.copy(interactionCounts = post.interactionCounts.copy(
                    replyCount = post.interactionCounts.replyCount.adjustedBy(1),
                ))
            }
        }
        request.quoteOf?.let { target ->
            updatePost(target) { post ->
                post.copy(interactionCounts = post.interactionCounts.copy(
                    quoteRepostCount = post.interactionCounts.quoteRepostCount.adjustedBy(1),
                ))
            }
        }
    }

    fun stop() {
        if (stopped) return
        stopped = true
        searchJob?.cancel()
        generation++
    }

    private suspend fun searchAccount(normalized: String, requestGeneration: Long) {
        try {
            val accounts = source.searchAccounts(normalized).distinctBy { it.id }
            if (stopped || requestGeneration != generation) return
            publish(AccountSearchState(query = normalized, accounts = accounts))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (stopped || requestGeneration != generation) return
            publish(AccountSearchState(query = normalized, error = uiStrings.sourceError(e)))
        }
    }

    private suspend fun searchHashtag(normalized: String, requestGeneration: Long) {
        val tag = normalized.removePrefix("#")
        // The loading state that start() published holds the expansion for this search.
        val loading = _state.value
        try {
            val page = source.searchHashtags(HashtagQuery(normalized, loading.combinedTags))
            if (stopped || requestGeneration != generation) return
            publish(loading.copy(
                posts = page.items.distinctBy { it.id }.map(applyFavouritePreference),
                loading = false,
                nextCursor = page.nextCursor,
            ))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (stopped || requestGeneration != generation) return
            publish(AccountSearchState(query = normalized, tagQuery = tag, error = uiStrings.sourceError(e)))
        }
    }

    private fun mergeExternalActionFields(existing: Post, incoming: Post): Post =
        existing.mergeExternalActionFields(incoming)

    private fun publish(next: AccountSearchState) {
        _state.value = next
        onStateChanged(next)
    }
}
