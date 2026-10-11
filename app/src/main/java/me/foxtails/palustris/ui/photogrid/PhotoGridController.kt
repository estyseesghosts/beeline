package me.foxtails.palustris.ui.photogrid

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.afterOwnedDeletion
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.domain.hashtags.HashtagExpansionInput
import me.foxtails.palustris.domain.hashtags.HashtagQuery
import me.foxtails.palustris.domain.mergeExternalActionFields
import me.foxtails.palustris.domain.timelineDisplayOrder
import me.foxtails.palustris.domain.timelineStatus
import me.foxtails.palustris.ui.UiStrings
import me.foxtails.palustris.ui.chips.FeedChipState
import me.foxtails.palustris.ui.requiresSignIn

internal class PhotoGridController(
    private val accountId: AccountId,
    private val source: SocialSource,
    private val sessionRevision: Long,
    private val scope: CoroutineScope,
    private val applyFavouritePreference: (Post) -> Post,
    private val uiStrings: UiStrings = UiStrings.Default,
    private val hashtagInput: () -> HashtagExpansionInput = { HashtagExpansionInput.Disabled },
) {
    private var loadingJob: Job? = null
    private var chips = FeedChipState()
    private var generation = 0L
    private val consumedCursors = mutableSetOf<String>()
    private var stopped = false
    private val _state = MutableStateFlow(PhotoGridFeedState())
    val state = _state.asStateFlow()

    /**
     * Takes the latest shared chip catalog. A selected list, hashtag or antenna that left the catalog
     * falls back to Home, so the grid never shows a feed that has no chip.
     */
    fun updateChips(latest: FeedChipState) {
        if (stopped) return
        chips = latest
        val selected = (_state.value.selectedFeed as? PhotoGridFeed.Source)?.source ?: return
        if (latest.loaded && !latest.offers(selected)) selectFeed(PhotoGridFeed.Source(FeedSource.Timeline(Timeline.Home)))
    }

    fun ensureLoaded() {
        if (stopped || _state.value.initialLoadComplete || loadingJob?.isActive == true) return
        val selected = _state.value.selectedFeed
        val available = availableTimelines()
        _state.value = _state.value.copy(availableTimelines = available)
        if (!isValidFeed(selected)) {
            _state.value = _state.value.copy(initialLoadComplete = true)
            return
        }
        startRequest(selected, ++generation, cursor = null)
    }

    fun selectFeed(feed: PhotoGridFeed) {
        if (stopped || !isValidFeed(feed)) return
        val current = _state.value
        if (current.selectedFeed == feed && (current.loading || current.initialLoadComplete)) return
        loadingJob?.cancel()
        consumedCursors.clear()
        val nextGeneration = ++generation
        _state.value = current.copy(
            selectedFeed = feed,
            posts = emptyList(),
            initialLoadComplete = false,
            loading = true,
            loadingMore = false,
            nextCursor = null,
            error = null,
            needsSignIn = false,
        )
        startRequest(feed, nextGeneration, cursor = null)
    }

    fun refresh() {
        if (stopped) return
        val selected = _state.value.selectedFeed
        loadingJob?.cancel()
        consumedCursors.clear()
        val nextGeneration = ++generation
        val available = availableTimelines()
        _state.value = _state.value.copy(
            availableTimelines = available,
            posts = emptyList(),
            initialLoadComplete = false,
            loading = true,
            loadingMore = false,
            nextCursor = null,
            error = null,
            needsSignIn = false,
        )
        if (!isValidFeed(selected)) {
            _state.value = _state.value.copy(initialLoadComplete = true, loading = false)
            return
        }
        startRequest(selected, nextGeneration, cursor = null)
    }

    fun loadMore() {
        if (stopped) return
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.loading || current.loadingMore || current.needsSignIn || !consumedCursors.add(cursor)) return
        startRequest(current.selectedFeed, generation, cursor)
    }

    fun updatePost(id: EntityId, transform: (Post) -> Post) {
        _state.value = _state.value.copy(
            posts = _state.value.posts.map { owned ->
                if (owned.post.id == id || owned.effectiveTargetId() == id) {
                    owned.copy(post = transform(owned.post))
                } else {
                    owned
                }
            },
        )
    }

    fun updateExternalPost(target: EntityId, incoming: Post) {
        _state.value = _state.value.copy(
            posts = _state.value.posts.map { owned ->
                if (owned.fetchedBy == accountId && owned.sessionRevision == sessionRevision &&
                    (owned.post.id == target || owned.effectiveTargetId() == target)
                ) {
                    owned.copy(post = mergeExternalActionFields(owned.post, incoming))
                } else owned
            },
        )
    }

    fun removeDeletedPost(deleted: OwnedPost) {
        _state.value = _state.value.copy(posts = _state.value.posts.afterOwnedDeletion(deleted))
    }

    fun updatePosts(transform: (Post) -> Post) {
        _state.value = _state.value.copy(
            posts = _state.value.posts.map { owned ->
                if (owned.fetchedBy == accountId && owned.sessionRevision == sessionRevision) {
                    owned.copy(post = transform(owned.post))
                } else owned
            },
        )
    }

    fun stop() {
        if (stopped) return
        stopped = true
        loadingJob?.cancel()
        generation++
    }

    private fun isValidFeed(feed: PhotoGridFeed): Boolean = when (feed) {
        is PhotoGridFeed.Source -> when (val item = feed.source) {
            is FeedSource.Timeline -> item.timeline in availableTimelines()
            else -> chips.offers(item)
        }
        // Search results belong to the Search owner. The grid only displays them.
        is PhotoGridFeed.Search -> false
    }

    private fun availableTimelines(): List<Timeline> {
        val available = timelineDisplayOrder.filter {
            it in source.capabilities.timelines && source.capabilities.timelineStatus(it) == CapabilityStatus.Supported
        }
        return available
    }

    /** One page of [feed]. A hashtag feed sends its extras. Search results are not fetched here, so they give null. */
    private suspend fun fetchPage(feed: PhotoGridFeed, tag: String?, combinedTags: List<String>, cursor: String?): Page<Post>? = when {
        feed !is PhotoGridFeed.Source -> null
        tag != null -> source.searchHashtags(HashtagQuery(tag, combinedTags), cursor)
        else -> source.feed(feed.source, cursor)
    }

    private fun startRequest(feed: PhotoGridFeed, requestGeneration: Long, cursor: String?) {
        loadingJob = scope.launch {
            if (stopped || requestGeneration != generation || _state.value.selectedFeed != feed) return@launch
            // The extras are fixed when a feed starts. Paging reuses them, so the cursor stays valid.
            val tag = ((feed as? PhotoGridFeed.Source)?.source as? FeedSource.Hashtag)?.tag
            val combinedTags = when {
                tag == null -> emptyList()
                cursor == null -> hashtagInput().expand(tag, source.maxCombinedHashtags).applied
                else -> _state.value.combinedTags
            }
            _state.value = _state.value.copy(
                loading = cursor == null,
                loadingMore = cursor != null,
                combinedTags = combinedTags,
                error = null,
                needsSignIn = false,
            )
            try {
                val page = fetchPage(feed, tag, combinedTags, cursor) ?: return@launch
                if (stopped || requestGeneration != generation || _state.value.selectedFeed != feed) return@launch
                val fetched = page.items.distinctBy { it.id }.map {
                    OwnedPost(accountId, applyFavouritePreference(it), sessionRevision)
                }
                val current = _state.value
                val merged = if (cursor == null) fetched else (current.posts + fetched).distinctBy { it.post.id }
                val nextCursor = page.nextCursor?.takeUnless {
                    it == cursor || it in consumedCursors
                }
                _state.value = current.copy(
                    posts = merged,
                    availableTimelines = availableTimelines(),
                    initialLoadComplete = true,
                    loading = false,
                    loadingMore = false,
                    nextCursor = nextCursor,
                    error = null,
                    needsSignIn = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (stopped || requestGeneration != generation || _state.value.selectedFeed != feed) return@launch
                if (cursor != null) consumedCursors.remove(cursor)
                _state.value = _state.value.copy(
                    loading = false,
                    loadingMore = false,
                    error = uiStrings.sourceError(e),
                    needsSignIn = requiresSignIn(e),
                )
            }
        }
    }

    private fun mergeExternalActionFields(existing: Post, incoming: Post): Post =
        existing.mergeExternalActionFields(incoming)
}
