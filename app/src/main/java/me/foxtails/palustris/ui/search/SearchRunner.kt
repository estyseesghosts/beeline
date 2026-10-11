package me.foxtails.palustris.ui.search

import kotlinx.coroutines.CancellationException
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.hashtags.HashtagQuery
import me.foxtails.palustris.domain.search.SearchQuery
import me.foxtails.palustris.ui.UiStrings
import me.foxtails.palustris.ui.links.PostLinkResolver

/**
 * Runs the request for one submitted query and writes its answer through [publish]. It holds no state of its
 * own. [isStale] tells it that a newer search has begun, so a late answer is dropped, and [state] reads the
 * loading state that the controller published before the request started.
 */
internal class SearchRunner(
    private val source: SocialSource,
    private val uiStrings: UiStrings,
    private val applyFavouritePreference: (Post) -> Post,
    private val linkResolver: () -> PostLinkResolver?,
    private val isStale: (Long) -> Boolean,
    private val state: () -> SearchState,
    private val publish: (SearchState) -> Unit,
) {
    /** Searches post text. A role that blocks it is a distinct state, not an error. */
    suspend fun posts(query: String, generation: Long) {
        answer(generation, { failure ->
            val denied = failure is SourceError.AccessDenied
            state().copy(loading = false, postSearchDenied = denied, error = if (denied) null else uiStrings.sourceError(failure))
        }) {
            val page = source.searchPosts(query)
            state().copy(posts = page.items.distinctBy { it.id }.map(applyFavouritePreference), loading = false, nextCursor = page.nextCursor)
        }
    }

    /** Finds the account a handle names and offers it as the target to open. */
    suspend fun handle(handle: SearchQuery.Handle, generation: Long) {
        answer(generation, { state().copy(loading = false, error = uiStrings.sourceError(it)) }) {
            val account = source.searchAccounts(handle.text).firstOrNull()
            state().copy(
                loading = false,
                target = account?.let { SearchTarget.Profile(it) },
                error = if (account == null) uiStrings.sourceError(SourceError.Unsupported("account")) else null,
            )
        }
    }

    /**
     * A link opens the post or profile it names. A link that is neither runs as a text search, because a
     * server may still know the address as text.
     */
    suspend fun url(url: String, generation: Long) {
        var unresolved = false
        answer(generation, { state().copy(loading = false, error = uiStrings.sourceError(it)) }) {
            val resolver = linkResolver()
            val post = resolver?.resolve(url)
            val account = if (post == null) resolver?.resolveAccount(url) else null
            val target = post?.let { SearchTarget.SinglePost(it.post) } ?: account?.let { SearchTarget.Profile(it) }
            unresolved = target == null
            state().copy(loading = target == null, target = target)
        }
        if (unresolved && !isStale(generation)) {
            publish(state().copy(mode = SearchMode.Posts))
            posts(url, generation)
        }
    }

    /** Searches a hashtag with the extras that the controller fixed when it published the loading state. */
    suspend fun hashtag(query: String, generation: Long) {
        val loading = state()
        answer(generation, { SearchState(query = query, mode = SearchMode.Hashtag, tagQuery = loading.tagQuery, error = uiStrings.sourceError(it)) }) {
            val page = source.searchHashtags(HashtagQuery(query, loading.combinedTags))
            loading.copy(posts = page.items.distinctBy { it.id }.map(applyFavouritePreference), loading = false, nextCursor = page.nextCursor)
        }
    }

    private suspend fun answer(generation: Long, onFailure: (Exception) -> SearchState, block: suspend () -> SearchState) {
        try {
            val next = block()
            if (!isStale(generation)) publish(next)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!isStale(generation)) publish(onFailure(e))
        }
    }
}
