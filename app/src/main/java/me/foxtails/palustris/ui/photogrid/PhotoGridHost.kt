package me.foxtails.palustris.ui.photogrid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.PrimaryFavouriteMode
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.hashtags.HashtagExpansionInput
import me.foxtails.palustris.ui.session.ConnectedEntryStore
import me.foxtails.palustris.ui.shell.FeedChipsContract
import me.foxtails.palustris.ui.shell.PhotoGridContract
import me.foxtails.palustris.ui.shell.PostProjectionCoordinator

/** Connects one Photo Grid owner to the connected lifetime and projection coordinator. */
@Composable
fun PhotoGridHost(
    accountId: AccountId,
    sessionGeneration: Long,
    sessionRevision: Long,
    source: SocialSource,
    postPreferences: PostPreferences,
    chips: FeedChipsContract,
    coordinator: PostProjectionCoordinator,
    entryStore: ConnectedEntryStore,
    hashtagInput: HashtagExpansionInput = HashtagExpansionInput.Disabled,
): PhotoGridContract {
    val scope = rememberCoroutineScope()
    // A hashtag feed reads the latest setting when it starts.
    val latestHashtagInput by rememberUpdatedState(hashtagInput)
    val owner = remember(accountId, sessionRevision, source) {
        PhotoGridOwner(
            accountId = accountId,
            source = source,
            sessionRevision = sessionRevision,
            scope = scope,
            applyFavouritePreference = { post -> applyFavouritePreference(source, postPreferences, post) },
            hashtagInput = { latestHashtagInput },
        )
    }
    LaunchedEffect(owner, chips.state) { owner.updateChips(chips.state) }
    LaunchedEffect(owner, postPreferences.favouriteEmoji) {
        if (source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction) {
            owner.updateFavouritePreference { post -> applyFavouritePreference(source, postPreferences, post) }
        }
    }
    val sink = remember(owner) {
        object : PostProjectionCoordinator.Sink {
            override fun applyExternalPost(updated: OwnedPost) { owner.applyExternalPost(updated) }
            override fun applyDeletedPost(deleted: OwnedPost) { owner.applyDeletedPost(deleted) }
            override fun applyPublishedPost(request: CreatePostRequest) { owner.applyPublishedPost(request) }
        }
    }
    LaunchedEffect(entryStore, sessionGeneration, owner, coordinator, sink) {
        entryStore.register(sessionGeneration, "photo-grid-$accountId-$sessionGeneration") {
            owner.release()
            coordinator.unregister(sink)
        }
    }
    DisposableEffect(coordinator, sink) {
        coordinator.register(sink)
        onDispose { coordinator.unregister(sink) }
    }
    val latestChips by rememberUpdatedState(chips)
    val state by owner.state.collectAsStateWithLifecycle()
    val actions = remember(owner) {
        object : PhotoGridContract.Actions {
            override fun ensureLoaded() = owner.ensureLoaded()
            override fun selectFeed(feed: PhotoGridFeed) = owner.selectFeed(feed)
            override fun refresh() {
                owner.refresh()
                latestChips.actions.refresh()
            }
            override fun loadMore() = owner.loadMore()
        }
    }
    return remember(state, actions, chips) { PhotoGridContract(state, actions, chips) }
}

private fun applyFavouritePreference(source: SocialSource, preferences: PostPreferences, post: Post): Post =
    if (source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction) {
        post.copy(favourited = post.myReaction == preferences.favouriteEmoji)
    } else post
