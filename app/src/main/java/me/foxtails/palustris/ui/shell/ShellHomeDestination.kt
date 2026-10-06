package me.foxtails.palustris.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.components.ChipCaretPresentation
import me.foxtails.palustris.ui.components.DestinationChipRow
import me.foxtails.palustris.ui.feed.HomeFeed
import me.foxtails.palustris.ui.large.CompactWideTabCaretHost
import me.foxtails.palustris.ui.large.CompactWideTabCaretRegistration
import me.foxtails.palustris.ui.large.LargeBottomDock
import me.foxtails.palustris.ui.large.LargeBottomDockClearance
import me.foxtails.palustris.ui.navigation.ShellNavigator
import me.foxtails.palustris.ui.navigation.homeTimelineChipEntries

/**
 * Home destination wiring for the application shell.
 *
 * Adapts the shell inputs to [HomeFeed] and to the wide timeline dock. It owns no Home state:
 * the feed contract, list state, chip list state, chip visibility, and callbacks arrive as
 * parameters. A connected Home refreshes its feature owner on a timeline change. The empty Home
 * has no owner, so it records the selection without a refresh. The compact Home chips stay with
 * `CompactShellNavigation`, so this adapter renders only the wide bottom dock.
 *
 * `leftObstructionClearance` and `rightObstructionClearance` are physical and never reverse with
 * the layout direction. Compact layout ignores both.
 */
@Composable
internal fun ShellHomeDestination(
    animatedDestination: Destination,
    navigator: ShellNavigator,
    overlay: ShellOverlayPresenter,
    accountSwitcher: AccountSwitcher,
    postCallbacks: DestinationPostCallbacks,
    navigationCallbacks: DestinationNavigationCallbacks,
    home: HomeContract?,
    largePresentation: Boolean,
    rightObstructionClearance: Dp,
    leftObstructionClearance: Dp,
    bottomObstructionClearance: Dp,
    availableTimelines: Set<Timeline>,
    homeListState: LazyListState,
    timelineChipListState: LazyListState,
    homeChipRowVisible: Boolean,
    onToggleHomeChipRow: () -> Unit,
    useCompactWideCaret: Boolean,
    tabCaretHost: CompactWideTabCaretHost?,
) {
    // Compact-wide Home hides its inline caret and reports its feature-owned visibility so the
    // floating stack can present the same state contextually.
    val homeCaretPresentation = if (useCompactWideCaret) ChipCaretPresentation.Hidden
    else ChipCaretPresentation.Inline
    if (useCompactWideCaret) {
        CompactWideTabCaretRegistration(
            host = tabCaretHost,
            expanded = homeChipRowVisible,
            onToggle = onToggleHomeChipRow,
        )
    }
    if (home != null) HomeFeed(
        state = home.state,
        compactLayout = !largePresentation,
        rightObstructionClearance = rightObstructionClearance,
        leftObstructionClearance = leftObstructionClearance,
        bottomObstructionClearance = bottomObstructionClearance,
        onRefresh = { home.actions.refresh(navigator.timeline) },
        onLoadMore = { home.actions.loadMore(navigator.timeline) },
        onSignIn = accountSwitcher.actions::signOut,
        availableActions = postCallbacks.availableActions,
        quoteEnabled = postCallbacks.quoteEnabled,
        onScrollDirectionChanged = { if (navigator.destination == Destination.Home && animatedDestination == Destination.Home) navigator.navigationVisible = it },
        onReact = postCallbacks.onReact,
        onReply = postCallbacks.onReply,
        onReshare = postCallbacks.onReshare,
        onBookmark = postCallbacks.onBookmark,
        onReaction = postCallbacks.onReaction,
        onOpenReactionBubble = { ownedPost, bounds -> overlay.openReactionBubble(ownedPost, bounds, postCallbacks.onReaction) },
        onOpenReactionPicker = overlay::expandReactionPicker,
        onQuote = postCallbacks.onQuote,
        onOpenProfile = navigator::openProfile,
        onSearchHashtag = navigator::openHashtagSearch,
        onOpenHashtagBubble = overlay::openHashtagBubble,
        onOpenMedia = overlay::openMedia,
        onOpenPost = { post -> navigationCallbacks.onOpenPost(post, LargePostOrigin.Home) },
        onOpenUsername = navigator::openAccountSearch,
        listState = homeListState,
        topContentPadding = if (largePresentation) 16.dp else null,
        bottomContentClearance = if (largePresentation) LargeBottomDockClearance else null,
        refreshIndicatorTopPadding = if (largePresentation) 16.dp else null,
        bottomDock = if (largePresentation) ({
            DestinationChipRow(
                entries = homeTimelineChipEntries(
                    timelines = availableTimelines,
                    selected = navigator.timeline,
                ) { item ->
                    val changed = item != navigator.timeline
                    if (changed) navigator.clearSelectedPost()
                    navigator.timeline = item
                    if (changed) home.actions.refresh(item)
                },
                rowContentDescription = stringResource(R.string.home_timeline_filter_description),
                listState = timelineChipListState,
                visible = homeChipRowVisible,
                onToggleVisibility = onToggleHomeChipRow,
                selectedEntryKey = "home-timeline:${navigator.timeline.name}",
                rowTestTag = "home_timeline_tabs",
                visibilityToggleTestTag = "home_timeline_visibility",
                caretPresentation = homeCaretPresentation,
            )
        }) else null,
    ) else Box(Modifier.fillMaxSize()) {
        val wideLeft = if (largePresentation) leftObstructionClearance else 0.dp
        val wideRight = if (largePresentation) rightObstructionClearance else 0.dp
        Box(
            Modifier.absolutePadding(left = wideLeft, right = wideRight)
                .fillMaxSize(),
        ) {
            EmptyState(AppIcons.HoneyHome, stringResource(R.string.feed_timeline_empty_title), stringResource(R.string.feed_timeline_empty_subtitle, stringResource(timelineLabelRes(navigator.timeline))))
        }
        if (largePresentation) {
            LargeBottomDock(
                modifier = Modifier.align(Alignment.BottomStart)
                    .absolutePadding(
                        left = wideLeft,
                        right = wideRight,
                        bottom = bottomObstructionClearance,
                    ),
                content = {
                    DestinationChipRow(
                        entries = homeTimelineChipEntries(
                            timelines = availableTimelines,
                            selected = navigator.timeline,
                        ) { item ->
                            val changed = item != navigator.timeline
                            if (changed) navigator.clearSelectedPost()
                            navigator.timeline = item
                        },
                        rowContentDescription = stringResource(R.string.home_timeline_filter_description),
                        listState = timelineChipListState,
                        visible = homeChipRowVisible,
                        onToggleVisibility = onToggleHomeChipRow,
                        selectedEntryKey = "home-timeline:${navigator.timeline.name}",
                        rowTestTag = "home_timeline_tabs",
                        visibilityToggleTestTag = "home_timeline_visibility",
                        caretPresentation = homeCaretPresentation,
                    )
                },
            )
        }
    }
}
