package me.foxtails.palustris.ui.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.afterDeletion
import me.foxtails.palustris.domain.hashtags.TrendingHashtag
import me.foxtails.palustris.domain.search.TrendingLink
import me.foxtails.palustris.ui.UiStrings

/** What Trends shows. A section that the server leaves empty is hidden by the screen. */
data class SearchTrendsState(
    val tags: List<TrendingHashtag> = emptyList(),
    val posts: List<Post> = emptyList(),
    val links: List<TrendingLink> = emptyList(),
    val nextCursor: String? = null,
    /** True once a load has answered, so an empty answer shows the empty state instead of a spinner. */
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
) {
    val isEmpty: Boolean get() = tags.isEmpty() && posts.isEmpty() && links.isEmpty()
}

/** What the Search screen asks of its owner for Trends. Each default does nothing. */
interface SearchTrendsActions {
    /** Loads Trends once. A later call retries only after a failed or empty load. */
    fun loadTrends() = Unit

    /** Loads the next page of trending posts. */
    fun loadMoreTrends() = Unit

    /** Loads Trends again. */
    fun refreshTrends() = Unit

    companion object {
        val None = object : SearchTrendsActions {}
    }
}

/**
 * Loads Trends for one session: tags, trending posts and, on Mastodon, links. The first load fills all
 * three sections. [stop] ends every request. A failed load shows an error only when nothing is loaded.
 */
internal class SearchTrendsController(
    private val source: SocialSource,
    private val scope: CoroutineScope,
    private val applyFavouritePreference: (Post) -> Post,
    private val uiStrings: UiStrings = UiStrings.Default,
) {
    private val _state = MutableStateFlow(SearchTrendsState())
    val state: StateFlow<SearchTrendsState> = _state.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private var stopped = false

    fun load() {
        if (stopped || job?.isActive == true) return
        val current = _state.value
        if (current.loaded && !current.isEmpty) return
        refresh()
    }

    fun refresh() {
        if (stopped) return
        job?.cancel()
        val requestGeneration = ++generation
        _state.value = _state.value.copy(loading = true, loadingMore = false, error = null)
        job = scope.launch {
            try {
                val trends = source.trends()
                if (stopped || requestGeneration != generation) return@launch
                _state.value = SearchTrendsState(
                    tags = trends.tags.distinctBy { it.name.lowercase() },
                    posts = trends.posts.items.distinctBy { it.id }.map(applyFavouritePreference),
                    links = trends.links.distinctBy { it.url },
                    nextCursor = trends.posts.nextCursor,
                    loaded = true,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (stopped || requestGeneration != generation) return@launch
                _state.value = _state.value.copy(loading = false, loaded = true, error = uiStrings.sourceError(e))
            }
        }
    }

    fun loadMore() {
        if (stopped) return
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.loading || current.loadingMore) return
        val requestGeneration = generation
        job = scope.launch {
            _state.value = current.copy(loadingMore = true, error = null)
            try {
                val page = source.trendingPosts(cursor)
                if (stopped || requestGeneration != generation) return@launch
                val latest = _state.value
                _state.value = latest.copy(
                    posts = (latest.posts + page.items.map(applyFavouritePreference)).distinctBy { it.id },
                    nextCursor = page.nextCursor?.takeUnless { it == cursor },
                    loadingMore = false,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (stopped || requestGeneration != generation) return@launch
                _state.value = _state.value.copy(loadingMore = false, error = uiStrings.sourceError(e))
            }
        }
    }

    fun updatePost(id: EntityId, transform: (Post) -> Post) = setPosts(_state.value.posts.withUpdated(id, transform))

    fun updateExternalPost(target: EntityId, incoming: Post) = setPosts(_state.value.posts.withExternal(target, incoming))

    fun removeDeletedPost(deleted: Post) {
        _state.value = _state.value.copy(posts = _state.value.posts.afterDeletion(deleted))
    }

    fun updatePosts(transform: (Post) -> Post) = setPosts(_state.value.posts.map(transform))

    fun stop() {
        if (stopped) return
        stopped = true
        job?.cancel()
        generation++
    }

    private fun setPosts(posts: List<Post>) {
        _state.value = _state.value.copy(posts = posts)
    }
}
