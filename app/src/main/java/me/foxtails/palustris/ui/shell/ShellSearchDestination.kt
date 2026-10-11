package me.foxtails.palustris.ui.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.FavouriteArtworkStyle
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.large.CompactWideTabCaretHost
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.navigation.ShellNavigator
import me.foxtails.palustris.ui.photogrid.PhotoGridCardActions
import me.foxtails.palustris.ui.photogrid.PhotoGridFeed
import me.foxtails.palustris.ui.photogrid.PhotoGridScreen
import me.foxtails.palustris.ui.search.SearchScreen

/**
 * Search and Photo Grid destination wiring for the application shell.
 *
 * Adapts the shell inputs to [SearchScreen] and [PhotoGridScreen]. It owns no search or Photo
 * Grid state: the query and category stay with [ShellNavigator], account results stay with the
 * search owner, and Photo Grid keeps its own feed, selection, paging, and scroll state. The
 * [AnimatedStatePane] swaps the two panels without resetting either owner.
 *
 * `leftObstructionClearance` and `rightObstructionClearance` are physical and never reverse with
 * the layout direction. Compact layout ignores both.
 */
@Composable
internal fun ShellSearchDestination(
    navigator: ShellNavigator,
    overlay: ShellOverlayPresenter,
    search: SearchContract,
    photoGrid: PhotoGridContract,
    postCallbacks: DestinationPostCallbacks,
    navigationCallbacks: DestinationNavigationCallbacks,
    largePresentation: Boolean,
    rightObstructionClearance: Dp,
    leftObstructionClearance: Dp,
    bottomObstructionClearance: Dp,
    bottomNavigationClearance: Dp,
    searchListState: LazyListState,
    photoGridScrollState: LazyStaggeredGridState,
    account: Account?,
    sessionRevision: Long,
    useCompactWideCaret: Boolean,
    tabCaretHost: CompactWideTabCaretHost?,
    favouriteArtworkStyle: FavouriteArtworkStyle = FavouriteArtworkStyle.Heart,
) {
    // The grid shows the live Search results while they match the field. Another chip or a dismiss
    // only changes which feed the grid shows. The Search owner keeps the pages and the cursor.
    val results = search.state
    val liveQuery = results.query.takeIf { results.hasPostResults && it == navigator.searchQuery.trim() }
    var dismissedQuery by rememberSaveable { mutableStateOf<String?>(null) }
    val carriedQuery = liveQuery?.takeIf { it != dismissedQuery }
    var searchChipSelected by rememberSaveable(carriedQuery) { mutableStateOf(true) }
    val showCarried = carriedQuery != null && searchChipSelected
    val gridState = if (carriedQuery == null) {
        photoGrid.state
    } else {
        photoGrid.state.copy(carriedSearch = carriedQuery).let { base ->
            if (!showCarried) {
                base
            } else {
                base.copy(
                    selectedFeed = PhotoGridFeed.Search(carriedQuery),
                    posts = account?.id?.let { id -> results.posts.map { OwnedPost(id, it, sessionRevision) } }.orEmpty(),
                    initialLoadComplete = !results.loading,
                    loading = results.loading,
                    loadingMore = results.loadingMore,
                    nextCursor = results.nextCursor,
                    error = results.error,
                    needsSignIn = false,
                )
            }
        }
    }
    AnimatedStatePane(
        stateKey = navigator.searchPanel,
        modifier = Modifier.fillMaxSize(),
    ) { panel ->
        when (panel) {
            SearchPanel.Search -> SearchScreen(
                search = search.state,
                onSearch = search.actions::search,
                onSuggest = search.actions::suggest,
                onConsumeTarget = search.actions::consumeTarget,
                onAccountClick = navigator::openProfile,
                availableActions = postCallbacks.availableActions,
                onReact = postCallbacks.onReact,
                onReply = postCallbacks.onReply,
                onReshare = postCallbacks.onReshare,
                onBookmark = postCallbacks.onBookmark,
                onReaction = postCallbacks.onReaction,
                onOpenReactionBubble = { ownedPost, bounds ->
                    overlay.openReactionBubble(ownedPost, bounds, postCallbacks.onReaction)
                },
                onOpenReactionPicker = overlay::expandReactionPicker,
                quoteEnabled = postCallbacks.quoteEnabled,
                onQuote = postCallbacks.onQuote,
                onSearchHashtag = navigator::openHashtagSearch,
                onShowOnlyHashtag = search.actions::searchWithoutRelated,
                onOpenHashtagBubble = overlay::openHashtagBubble,
                onLoadMoreSearch = search.actions::loadMore,
                trends = search.trends,
                trendsActions = search.actions,
                initialQuery = navigator.searchPrefill,
                sharedQuery = navigator.searchQuery,
                onSharedQueryChange = { navigator.searchQuery = it },
                rightObstructionClearance = rightObstructionClearance,
                leftObstructionClearance = leftObstructionClearance,
                bottomObstructionClearance = bottomObstructionClearance,
                bottomNavigationClearance = bottomNavigationClearance,
                listState = searchListState.takeIf { largePresentation },
                largeLayout = largePresentation,
                compactLayout = !largePresentation,
                compactNavigationVisible = !largePresentation,
                mediaOwner = account?.id,
                sessionRevision = sessionRevision,
                onOpenMedia = overlay::openMedia,
                onOpenPost = { post -> navigationCallbacks.onOpenPost(post, LargePostOrigin.Search) },
                onOpenUsername = navigator::openAccountSearch,
            )
            SearchPanel.PhotoGrid -> PhotoGridScreen(
                state = gridState,
                onRefresh = if (showCarried) ({ search.actions.search(results.query) }) else photoGrid.actions::refresh,
                onLoadMore = if (showCarried) search.actions::loadMore else photoGrid.actions::loadMore,
                onSelectFeed = { feed ->
                    searchChipSelected = feed is PhotoGridFeed.Search
                    if (feed !is PhotoGridFeed.Search) photoGrid.actions.selectFeed(feed)
                },
                onDismissSearch = {
                    dismissedQuery = carriedQuery
                    searchChipSelected = false
                },
                chips = photoGrid.chips,
                onOpenPost = { post -> navigationCallbacks.onOpenPost(post, LargePostOrigin.PhotoGrid) },
                cardActions = PhotoGridCardActions(
                    availableActions = postCallbacks.availableActions,
                    favouriteArtworkStyle = favouriteArtworkStyle,
                    onFavourite = postCallbacks.onReact,
                    onQuickView = overlay::openPhotoQuickView,
                ),
                compactLayout = !largePresentation,
                compactNavigationVisible = !largePresentation,
                rightObstructionClearance = rightObstructionClearance,
                leftObstructionClearance = leftObstructionClearance,
                bottomObstructionClearance = bottomObstructionClearance,
                gridState = photoGridScrollState,
                useCompactWideCaret = useCompactWideCaret,
                tabCaretHost = tabCaretHost,
            )
        }
    }
}
