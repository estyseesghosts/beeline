package me.foxtails.palustris.ui.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.FavouriteArtworkStyle
import me.foxtails.palustris.ui.large.CompactWideTabCaretHost
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.navigation.ShellNavigator
import me.foxtails.palustris.ui.photogrid.PhotoGridScreen
import me.foxtails.palustris.ui.search.SearchPane

/**
 * Search and Photo Grid destination wiring for the application shell.
 *
 * Adapts the shell inputs to [SearchPane] and [PhotoGridScreen]. It owns no search or Photo
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
    AnimatedStatePane(
        stateKey = navigator.searchPanel,
        modifier = Modifier.fillMaxSize(),
    ) { panel ->
        when (panel) {
            SearchPanel.Search -> ShellSearchPane(
                navigator, overlay, search, postCallbacks, navigationCallbacks, largePresentation,
                rightObstructionClearance, leftObstructionClearance, bottomObstructionClearance,
                bottomNavigationClearance, searchListState, account, sessionRevision,
            )
            SearchPanel.PhotoGrid -> ShellPhotoGridPane(
                navigator, overlay, search, photoGrid, postCallbacks, navigationCallbacks, largePresentation,
                rightObstructionClearance, leftObstructionClearance, bottomObstructionClearance,
                photoGridScrollState, account, sessionRevision, useCompactWideCaret, tabCaretHost,
                favouriteArtworkStyle,
            )
        }
    }
}
