package me.foxtails.palustris.ui.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostLimits
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.ThreadPublication
import me.foxtails.palustris.domain.ThreadPublishListener
import me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority
import me.foxtails.palustris.ui.session.ConnectedEntryStore
import me.foxtails.palustris.ui.shell.FeedChipsContract
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
    /** False when the server has not confirmed that it takes quotes. */
    val quoteConfirmed: Boolean = true,
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
    chips: FeedChipsContract = FeedChipsContract.Empty,
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
    val latestChips by rememberUpdatedState(chips)
    val homeActions = remember(feedModel) {
        object : HomeContract.Actions {
            override fun refresh(feed: FeedSource) {
                feedModel.refresh(feed)
                latestChips.actions.refresh()
            }
            override fun loadMore(feed: FeedSource) { feedModel.loadMore(feed) }
        }
    }
    val home = remember(feed, homeActions, chips) {
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
                selectedFeed = feed.feedSource,
                availableTimelines = feed.timelines,
                requestEpoch = feed.requestEpoch,
            ),
            actions = homeActions,
            chips = chips,
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
            quoteEnabled = feed.quoteStatus != CapabilityStatus.Unsupported &&
                feed.quoteStatus != CapabilityStatus.Denied,
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
            override fun applyDeletedPost(deleted: OwnedPost) { feedModel.applyDeletedPost(deleted) }
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
    val composerInputs = remember(feed.audiences, feed.limits, feed.canPublish, feed.publishing, feed.publishPosted, feed.publishTotal, feed.publishMediaProgress, feed.error, feed.quoteStatus) {
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
    quoteConfirmed = feed.quoteStatus == CapabilityStatus.Supported,
)
