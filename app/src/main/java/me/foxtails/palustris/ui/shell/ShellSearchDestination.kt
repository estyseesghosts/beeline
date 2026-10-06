package me.foxtails.palustris.ui.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.ui.large.CompactWideTabCaretHost
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.navigation.ShellNavigator
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
) {
    AnimatedStatePane(
        stateKey = navigator.searchPanel,
        modifier = Modifier.fillMaxSize(),
    ) { panel ->
        when (panel) {
            SearchPanel.Search -> SearchScreen(
                accountSearch = search.state,
                onSearchAccounts = search.actions::search,
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
                onOpenHashtagBubble = overlay::openHashtagBubble,
                onLoadMoreSearch = search.actions::loadMore,
                initialQuery = navigator.searchPrefill,
                sharedQuery = navigator.searchQuery,
                sharedTab = navigator.searchCategory,
                onSharedQueryChange = { navigator.searchQuery = it },
                onSharedTabChange = { navigator.searchCategory = it },
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
                useCompactWideCaret = useCompactWideCaret,
                tabCaretHost = tabCaretHost,
            )
            SearchPanel.PhotoGrid -> PhotoGridScreen(
                state = photoGrid.state,
                onRefresh = photoGrid.actions::refresh,
                onLoadMore = photoGrid.actions::loadMore,
                onSelectFeed = photoGrid.actions::selectFeed,
                onAddHashtag = photoGrid.actions::addHashtag,
                onClearPreferenceError = photoGrid.actions::clearPreferenceError,
                onOpenPost = { post -> navigationCallbacks.onOpenPost(post, LargePostOrigin.PhotoGrid) },
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
