package me.foxtails.palustris.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.foxtails.palustris.data.auth.DraftStore
import me.foxtails.palustris.data.auth.InMemoryDraftStore
import me.foxtails.palustris.data.notifications.NotificationSyncOrchestrator
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.DEFAULT_FAVOURITE_EMOJI
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostPreferencesRepository
import me.foxtails.palustris.domain.PrimaryFavouriteMode
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.ThreadImagePreparer
import me.foxtails.palustris.domain.ThreadPublication
import me.foxtails.palustris.domain.ThreadPublishFailure
import me.foxtails.palustris.domain.ThreadPublishListener
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.adjustedBy
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.domain.mergeExternalActionFields
import me.foxtails.palustris.ui.UiStrings
import me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority
import me.foxtails.palustris.ui.posts.PostInteractionMutationOwner
import me.foxtails.palustris.ui.requiresSignIn

@HiltViewModel(assistedFactory = FeedViewModel.Factory::class)
class FeedViewModel @AssistedInject constructor(
    @Assisted val accountId: AccountId,
    @Assisted private val source: SocialSource,
    private val syncCoordinator: NotificationSyncOrchestrator,
    private val postPreferencesRepository: PostPreferencesRepository,
    @Assisted private val sessionRevision: Long,
    @Assisted private val executionAuthority: PostInteractionExecutionAuthority,
    private val draftStore: DraftStore = InMemoryDraftStore(),
    private val threadPreparer: ThreadImagePreparer? = null,
    private val uiStrings: UiStrings = UiStrings.Default,
) : ViewModel() {
    val favouriteArtworkStyle = source.favouriteArtworkStyle
    private val _feed = MutableStateFlow(FeedState())
    val feed = _feed.asStateFlow()
    val sync = syncCoordinator.observeAccount(accountId)
    private var feedJob: Job? = null
    private var setupJob: Job? = null
    private var publishJob: Job? = null
    private var preferencesJob: Job? = null
    private var favouriteEmoji = DEFAULT_FAVOURITE_EMOJI
    private var stopped = false

    /** Advances on refresh, timeline replacement, and stop. Binds publication authority. */
    private var feedEpoch = 0L

    /** Accepted page cursors for the active epoch. Protects against source cursor cycles. */
    private val acceptedCursors = mutableSetOf<String>()
    private val postProjectionListeners = mutableSetOf<(OwnedPost) -> Unit>()
    private val interactionMutations = PostInteractionMutationOwner(
        accountId = accountId,
        source = source,
        sessionRevision = sessionRevision,
        scope = viewModelScope,
        isActionAvailable = { action -> action in _feed.value.actions },
        favouriteEmoji = { favouriteEmoji },
        updatePost = ::updatePost,
        onFailure = ::feedFailure,
        executionAuthority = executionAuthority,
    )
    init {
        setupJob = viewModelScope.launch {
            if (!stopped) {
                refresh()
            }
        }
        preferencesJob = viewModelScope.launch {
            postPreferencesRepository.observe(accountId).collectLatest { preferences ->
                favouriteEmoji = preferences.favouriteEmoji
                if (!stopped) updatePosts { post ->
                    if (source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction) {
                        post.copy(favourited = post.myReaction == favouriteEmoji)
                    } else {
                        post
                    }
                }
                _feed.value = _feed.value.copy(favouriteEmoji = favouriteEmoji)
            }
        }
    }

    /**
     * Reloads the timeline from the first page. Each call advances the feed epoch.
     * A superseded launch drops its page instead of writing stale rows.
     */
    fun refresh(timeline: Timeline = _feed.value.timeline) {
        if (stopped) return
        val epoch = ++feedEpoch
        acceptedCursors.clear()
        feedJob?.cancel()
        val previous = _feed.value
        val timelineChanged = timeline != previous.timeline
        // Reserve the request synchronously. A queued paging call must not start behind it.
        _feed.value = previous.copy(
            posts = if (timelineChanged) emptyList() else previous.posts,
            ownedPosts = if (timelineChanged) emptyList() else previous.ownedPosts,
            timeline = timeline,
            loading = true,
            loadingMore = false,
            nextCursor = if (timelineChanged) null else previous.nextCursor,
            error = null,
            requestEpoch = epoch,
        )
        feedJob = viewModelScope.launch {
            try {
                val page = source.timeline(timeline)
                if (epoch != feedEpoch || stopped) return@launch
                val posts = page.items.distinctBy { it.id }.map(::applyFavouritePreference)
                page.nextCursor?.let(acceptedCursors::add)
                _feed.value = FeedState(
                    posts = posts,
                    ownedPosts = posts.map { OwnedPost(accountId, it, sessionRevision) },
                    timeline = timeline,
                    timelines = source.capabilities.timelines,
                    canPublish = source.capabilities.canPublish,
                    audiences = source.capabilities.audiences,
                    actions = effectiveActions(),
                    quoteStatus = source.capabilities.quotes,
                    savedPosts = source.capabilities.savedPosts,
                    favouriteEmoji = favouriteEmoji,
                    publishing = _feed.value.publishing,
                    publishPosted = _feed.value.publishPosted,
                    publishTotal = _feed.value.publishTotal,
                    nextCursor = page.nextCursor,
                    requestEpoch = epoch,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (epoch != feedEpoch || stopped) return@launch
                val error = uiStrings.sourceError(e)
                val needsSignIn = requiresSignIn(e)
                _feed.value = if (timelineChanged) {
                    // A failed timeline change restores the previous timeline and its rows so old
                    // rows are never labeled as the new timeline.
                    previous.copy(
                        loading = false,
                        loadingMore = false,
                        error = error,
                        needsSignIn = needsSignIn,
                        publishing = _feed.value.publishing,
                        publishPosted = _feed.value.publishPosted,
                        publishTotal = _feed.value.publishTotal,
                        requestEpoch = epoch,
                    )
                } else {
                    // Keep the accepted rows for a same-timeline refresh so a late failure cannot
                    // restore captured rows over a mutation applied while the request waited.
                    _feed.value.copy(
                        loading = false,
                        loadingMore = false,
                        error = error,
                        needsSignIn = needsSignIn,
                    )
                }
            }
        }
    }

    fun loadMore(timeline: Timeline = _feed.value.timeline) {
        if (stopped) return
        val state = _feed.value
        if (timeline != state.timeline) return
        val cursor = state.nextCursor ?: return
        if (state.loading || state.loadingMore || state.needsSignIn) return
        val epoch = feedEpoch
        // Reserve the page slot synchronously so a second queued call cannot also acquire it.
        _feed.value = state.copy(loadingMore = true, error = null)
        feedJob = viewModelScope.launch {
            try {
                val page = source.timeline(state.timeline, cursor)
                if (epoch != feedEpoch || stopped) return@launch
                val current = _feed.value
                if (current.timeline != state.timeline) return@launch
                // The input cursor must still own the current continuation. A newer page
                // that advanced the cursor first makes this page stale, even in the
                // same epoch. Overlapping same-epoch pages cannot normally occur: the
                // synchronous loadingMore reservation above serializes page acquisition.
                if (current.nextCursor != cursor) return@launch
                val newPosts = page.items.map(::applyFavouritePreference)
                // Merge the accepted page into current rows. Overlapping rows keep current fields.
                val mergedOwned = mergeAcceptedPage(current.ownedPosts, newPosts)
                val repeatedCursor = page.nextCursor != null &&
                    (page.nextCursor == cursor || !acceptedCursors.add(page.nextCursor))
                _feed.value = current.copy(
                    posts = mergedOwned.map { it.post },
                    ownedPosts = mergedOwned,
                    loadingMore = false,
                    nextCursor = page.nextCursor.takeUnless { repeatedCursor },
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (epoch != feedEpoch || stopped) return@launch
                feedFailure(e)
            }
        }
    }

    private fun mergeAcceptedPage(existing: List<OwnedPost>, incoming: List<Post>): List<OwnedPost> {
        val merged = existing.toMutableList()
        val known = existing.mapTo(mutableSetOf()) { it.post.id }
        incoming.forEach { post ->
            if (known.add(post.id)) {
                merged += OwnedPost(accountId, post, sessionRevision)
            }
        }
        return merged
    }

    /**
     * Publishes a thread in order with draft progress writes. It reserves the single publish
     * slot synchronously; the runner owns the job. A timeline refresh does not cancel a valid
     * publish. A stopped feed posts nothing more.
     */
    fun create(publication: ThreadPublication, listener: ThreadPublishListener = ThreadPublishListener.Empty) {
        if (stopped || _feed.value.publishing) return
        publishJob?.cancel()
        _feed.value = _feed.value.copy(publishing = true, publishPosted = 0, publishTotal = publication.entries.size, error = null)
        publishJob = viewModelScope.launch {
            ThreadPublicationRunner(source, threadPreparer ?: EmptyThreadPreparer, draftStore, uiStrings)
                .run(publication, accountId, sessionRevision, listener, RunnerEvents())
        }
    }

    /** Writes publish progress and results to the feed state. */
    private inner class RunnerEvents : ThreadPublicationRunner.Events {
        override val isStopped: Boolean get() = stopped

        override fun onProgress(posted: Int, total: Int) {
            if (!stopped) _feed.value = _feed.value.copy(publishPosted = posted, publishTotal = total)
        }

        override fun onSuccess(owned: List<OwnedPost>, requests: List<CreatePostRequest>) {
            _feed.value = _feed.value.copy(publishing = false, publishPosted = 0, publishTotal = 0, error = null)
        }

        override fun onFailure(failure: ThreadPublishFailure, message: String) {
            _feed.value = _feed.value.copy(publishing = false, error = message)
        }

        override fun onUnexpected(error: Exception) {
            _feed.value = _feed.value.copy(publishing = false, publishPosted = 0, publishTotal = 0)
            feedFailure(error)
        }

        override fun onRefresh() {
            refresh()
        }
    }

    fun favorite(ownedPost: OwnedPost) {
        interactionMutations.favorite(ownedPost)
    }

    fun reshare(ownedPost: OwnedPost) {
        interactionMutations.reshare(ownedPost)
    }

    fun react(ownedPost: OwnedPost, choice: EmojiChoice) {
        interactionMutations.react(ownedPost, choice)
    }

    fun bookmark(ownedPost: OwnedPost) {
        interactionMutations.bookmark(ownedPost)
    }

    /** Applies confirmed action fields to existing collections without inserting thread-only posts. */
    fun applyExternalPost(updated: OwnedPost) {
        if (stopped || updated.fetchedBy != accountId || updated.sessionRevision != sessionRevision) return
        val target = updated.effectiveTargetId()
        updateExternalPost(target, updated.post)
    }

    fun addPostProjectionListener(listener: (OwnedPost) -> Unit) {
        if (!stopped) postProjectionListeners += listener
    }

    fun applyPublishedPost(request: CreatePostRequest) {
        if (stopped) return
        request.replyTo?.let { parent ->
            updatePost(parent) { post ->
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

    fun removePostProjectionListener(listener: (OwnedPost) -> Unit) {
        postProjectionListeners -= listener
    }

    fun stop() {
        if (stopped) return
        stopped = true
        feedEpoch += 1
        setupJob?.cancel()
        feedJob?.cancel()
        publishJob?.cancel()
        preferencesJob?.cancel()
        interactionMutations.stop()
    }

    private fun effectiveActions(): Set<PostAction> = buildSet {
        addAll(source.capabilities.actions)
        if (source.capabilities.primaryFavourite.status == CapabilityStatus.Supported) {
            add(PostAction.Favorite)
        }
        if (source.capabilities.savedPosts?.status == CapabilityStatus.Supported) {
            add(PostAction.Bookmark)
        }
    }.intersect(ClientReadyPostActions)

    private fun applyFavouritePreference(post: Post): Post = if (
        source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction
    ) {
        post.copy(favourited = post.myReaction == favouriteEmoji)
    } else {
        post
    }

    private fun updatePost(ownedPost: OwnedPost, id: EntityId, transform: (Post) -> Post) {
        val currentOwnedPosts = _feed.value.ownedPosts
        val hasMatchingHomePost = currentOwnedPosts.any { owned ->
            (owned.post.id == id || owned.effectiveTargetId() == id) &&
                owned.fetchedBy == accountId && owned.sessionRevision == sessionRevision
        }
        val selectedProjection = ownedPost.copy(post = transform(ownedPost.post))
        val updatedOwnedPosts = currentOwnedPosts.map { owned ->
            if ((owned.post.id == id || owned.effectiveTargetId() == id) &&
                owned.fetchedBy == accountId && owned.sessionRevision == sessionRevision
            ) {
                owned.copy(post = transform(owned.post))
            } else {
                owned
            }
        }
        _feed.value = _feed.value.copy(
            posts = _feed.value.posts.map { if (it.id == id || it.actionTargetId == id) transform(it) else it },
            ownedPosts = updatedOwnedPosts,
        )
        updatedOwnedPosts.filterIndexed { index, owned -> owned !== currentOwnedPosts[index] }
            .forEach { updated -> postProjectionListeners.toList().forEach { it(updated) } }
        if (!hasMatchingHomePost) postProjectionListeners.toList().forEach { it(selectedProjection) }
    }

    private fun updatePost(id: EntityId, transform: (Post) -> Post) {
        val currentOwnedPosts = _feed.value.ownedPosts
        val updated = currentOwnedPosts.map { owned ->
            if ((owned.post.id == id || owned.effectiveTargetId() == id) &&
                owned.fetchedBy == accountId && owned.sessionRevision == sessionRevision
            ) owned.copy(post = transform(owned.post)) else owned
        }
        _feed.value = _feed.value.copy(
            posts = _feed.value.posts.map { if (it.id == id || it.actionTargetId == id) transform(it) else it },
            ownedPosts = updated,
        )
    }

    private fun updateExternalPost(target: EntityId, incoming: Post) {
        _feed.value = _feed.value.copy(
            posts = _feed.value.posts.map { post ->
                if (post.id == target || post.actionTargetId == target) mergeExternalActionFields(post, incoming) else post
            },
            ownedPosts = _feed.value.ownedPosts.map { owned ->
                if (owned.fetchedBy == accountId && (owned.post.id == target || owned.effectiveTargetId() == target)) {
                    owned.copy(post = mergeExternalActionFields(owned.post, incoming))
                } else owned
            },
        )
    }

    private fun mergeExternalActionFields(existing: Post, incoming: Post): Post =
        existing.mergeExternalActionFields(incoming)

    private fun updatePosts(transform: (Post) -> Post) {
        val transformed = _feed.value.posts.associate { it.id to transform(it) }
        _feed.value = _feed.value.copy(
            posts = _feed.value.posts.map { transformed[it.id] ?: it },
            ownedPosts = _feed.value.ownedPosts.map { owned ->
                if (owned.fetchedBy == accountId) owned.copy(post = transformed[owned.post.id] ?: owned.post) else owned
            },
        )
    }

    private fun feedFailure(e: Exception) {
        if (e is CancellationException) throw e
        _feed.value = _feed.value.copy(
            loading = false,
            loadingMore = false,
            error = uiStrings.sourceError(e),
            needsSignIn = requiresSignIn(e),
        )
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId, source: SocialSource, sessionRevision: Long, executionAuthority: PostInteractionExecutionAuthority): FeedViewModel
    }
}
