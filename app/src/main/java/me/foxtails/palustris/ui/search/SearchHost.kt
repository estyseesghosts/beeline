package me.foxtails.palustris.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.PrimaryFavouriteMode
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.ui.session.ConnectedEntryStore
import me.foxtails.palustris.ui.shell.PostProjectionCoordinator
import me.foxtails.palustris.ui.shell.SearchContract

/** Connects one Search owner to the connected lifetime and projection coordinator. */
@Composable
fun SearchHost(
    accountId: AccountId,
    sessionGeneration: Long,
    sessionRevision: Long,
    source: SocialSource,
    postPreferences: PostPreferences,
    coordinator: PostProjectionCoordinator,
    entryStore: ConnectedEntryStore,
): SearchContract {
    val scope = rememberCoroutineScope()
    val owner = remember(accountId, sessionRevision, source) {
        SearchOwner(
            accountId = accountId,
            source = source,
            sessionRevision = sessionRevision,
            scope = scope,
            applyFavouritePreference = { post -> applyFavouritePreference(source, postPreferences, post) },
        )
    }
    LaunchedEffect(owner, postPreferences.favouriteEmoji) {
        owner.updateFavouritePreference { post -> applyFavouritePreference(source, postPreferences, post) }
    }
    val sink = remember(owner) {
        object : PostProjectionCoordinator.Sink {
            override fun applyExternalPost(updated: OwnedPost) { owner.applyExternalPost(updated) }
            override fun applyPublishedPost(request: CreatePostRequest) { owner.applyPublishedPost(request) }
        }
    }
    LaunchedEffect(entryStore, sessionGeneration, owner, coordinator, sink) {
        entryStore.register(sessionGeneration, "search-$accountId-$sessionGeneration") {
            owner.release()
            coordinator.unregister(sink)
        }
    }
    DisposableEffect(coordinator, sink) {
        coordinator.register(sink)
        onDispose { coordinator.unregister(sink) }
    }
    val state by owner.state.collectAsStateWithLifecycle()
    val actions = remember(owner) {
        object : SearchContract.Actions {
            override fun search(query: String) = owner.search(query)
            override fun loadMore() = owner.loadMore()
        }
    }
    return remember(state, actions) { SearchContract(state, actions) }
}

private fun applyFavouritePreference(
    source: SocialSource,
    preferences: PostPreferences,
    post: Post,
): Post = if (source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction) {
    post.copy(favourited = post.myReaction == preferences.favouriteEmoji)
} else {
    post
}
