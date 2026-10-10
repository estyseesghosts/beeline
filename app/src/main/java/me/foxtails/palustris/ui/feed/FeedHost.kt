package me.foxtails.palustris.ui.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostLimits
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.ThreadPublication
import me.foxtails.palustris.domain.ThreadPublishFailure
import me.foxtails.palustris.domain.ThreadPublishListener
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority
import me.foxtails.palustris.ui.session.ConnectedEntryStore
import me.foxtails.palustris.ui.shell.HomeContract
import me.foxtails.palustris.ui.shell.HomeFeedUiState
import me.foxtails.palustris.ui.shell.PostInteractions
import me.foxtails.palustris.ui.shell.PostProjectionCoordinator

/**
 * Owns the feed presentation for one connected session.
 *
 * The feed model, Home, Search, and Photo Grid contracts, the shared post-interaction
 * actions, the composer inputs, and the projection sink stay beside the feed feature.
 * The session host receives only the narrow [Feed] bundle and keeps source resolution,
 * the projection coordinator, and composer assembly.
 */
data class FeedComposerInputs(
    val availableAudiences: Set<Audience>,
    val limits: PostLimits,
    val canPublish: Boolean,
    val publishing: Boolean,
    val publishPosted: Int,
    val publishTotal: Int,
    val error: String?,
    val publishMediaProgress: Float? = null,
)

data class Feed(
    val home: HomeContract,
    val postInteractions: PostInteractions,
    val composerInputs: FeedComposerInputs,
    val publish: (ThreadPublication, ThreadPublishListener) -> Unit,
    val react: (OwnedPost, EmojiChoice) -> Unit,
)

@Composable
fun FeedHost(
    accountId: AccountId,
    sessionGeneration: Long,
    sessionRevision: Long,
    source: SocialSource,
    executionAuthority: PostInteractionExecutionAuthority,
    coordinator: PostProjectionCoordinator,
    entryStore: ConnectedEntryStore,
): Feed {
    val feedModel = hiltViewModel<FeedViewModel, FeedViewModel.Factory>(
        key = "feed-$accountId-$sessionGeneration",
        creationCallback = { factory ->
            factory.create(accountId, source, sessionRevision, executionAuthority)
        },
    )
    val modelKey = "feed-$accountId-$sessionGeneration"
    LaunchedEffect(entryStore, sessionGeneration, feedModel) {
        entryStore.register(sessionGeneration, modelKey) { feedModel.stop() }
    }
    val feed by feedModel.feed.collectAsStateWithLifecycle()
    val homeActions = remember(feedModel) {
        object : HomeContract.Actions {
            override fun refresh(timeline: Timeline) { feedModel.refresh(timeline) }
            override fun loadMore(timeline: Timeline) { feedModel.loadMore(timeline) }
        }
    }
    val home = remember(feed, homeActions) {
        HomeContract(
            state = HomeFeedUiState(
                favouriteArtworkStyle = feedModel.favouriteArtworkStyle,
                ownedPosts = feed.ownedPosts,
                posts = feed.posts,
                loading = feed.loading,
                loadingMore = feed.loadingMore,
                nextCursor = feed.nextCursor,
                error = feed.error,
                needsSignIn = feed.needsSignIn,
                selectedTimeline = feed.timeline,
                availableTimelines = feed.timelines,
                requestEpoch = feed.requestEpoch,
            ),
            actions = homeActions,
        )
    }
    val postInteractionsActions = remember(feedModel) {
        object : PostInteractions.Actions {
            override fun favorite(post: OwnedPost) { feedModel.favorite(post) }
            override fun repost(post: OwnedPost) { feedModel.reshare(post) }
            override fun bookmark(post: OwnedPost) { feedModel.bookmark(post) }
            override fun react(post: OwnedPost, choice: EmojiChoice) { feedModel.react(post, choice) }
        }
    }
    val postInteractions = remember(feed.actions, feed.quoteStatus, postInteractionsActions) {
        PostInteractions(
            availableActions = feed.actions,
            quoteEnabled = feed.quoteStatus == CapabilityStatus.Supported,
            actions = postInteractionsActions,
        )
    }
    val postReaction = remember(feedModel) {
        { post: OwnedPost, choice: EmojiChoice -> feedModel.react(post, choice) }
    }
    val feedSink = remember(feedModel) {
        object : PostProjectionCoordinator.Sink {
            override fun applyExternalPost(updated: OwnedPost) { feedModel.applyExternalPost(updated) }
            override fun applyPublishedPost(request: CreatePostRequest) { feedModel.applyPublishedPost(request) }
        }
    }
    DisposableEffect(coordinator, feedSink, feedModel) {
        coordinator.register(feedSink)
        val feedProjection: (OwnedPost) -> Unit = { updated ->
            coordinator.forwardExternalPost(feedSink, updated)
        }
        feedModel.addPostProjectionListener(feedProjection)
        onDispose {
            feedModel.removePostProjectionListener(feedProjection)
            coordinator.unregister(feedSink)
        }
    }
    val composerInputs = remember(feed.audiences, feed.limits, feed.canPublish, feed.publishing, feed.publishPosted, feed.publishTotal, feed.publishMediaProgress, feed.error) {
        feedComposerInputs(feed)
    }
    val publish = remember(feedModel, feedSink, coordinator) {
        threadPublish(feedModel, feedSink, coordinator)
    }
    return remember(home, postInteractions, composerInputs, publish, postReaction) {
        Feed(
            home = home,
            postInteractions = postInteractions,
            composerInputs = composerInputs,
            publish = publish,
            react = postReaction,
        )
    }
}

/** Builds the composer inputs for one feed snapshot. */
private fun feedComposerInputs(feed: FeedState): FeedComposerInputs = FeedComposerInputs(
    availableAudiences = feed.audiences,
    limits = feed.limits,
    canPublish = feed.canPublish,
    publishing = feed.publishing,
    publishPosted = feed.publishPosted,
    publishTotal = feed.publishTotal,
    error = feed.error,
    publishMediaProgress = feed.publishMediaProgress,
)

/** Publishes threads through the feed model and fans accepted posts out to the other surfaces. */
private fun threadPublish(
    feedModel: FeedViewModel,
    feedSink: PostProjectionCoordinator.Sink,
    coordinator: PostProjectionCoordinator,
): (ThreadPublication, ThreadPublishListener) -> Unit = { publication, listener ->
    feedModel.create(publication, ThreadPublishForwarder(feedModel, feedSink, coordinator, listener))
}

/** Forwards each accepted post to the projection coordinator before the composer listener runs. */
private class ThreadPublishForwarder(
    private val feedModel: FeedViewModel,
    private val origin: PostProjectionCoordinator.Sink,
    private val coordinator: PostProjectionCoordinator,
    private val listener: ThreadPublishListener,
) : ThreadPublishListener {
    override fun onAccepted(created: List<OwnedPost>, requests: List<CreatePostRequest>) {
        created.forEachIndexed { index, post ->
            feedModel.applyPublishedPost(requests[index])
            coordinator.forwardPublishedPost(origin, requests[index], post)
        }
        listener.onAccepted(created, requests)
    }

    override fun onProgress(posted: Int, total: Int) = listener.onProgress(posted, total)

    override fun onError(failure: ThreadPublishFailure) = listener.onError(failure)
}
