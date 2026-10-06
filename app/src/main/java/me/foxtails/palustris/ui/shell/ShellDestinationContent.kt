package me.foxtails.palustris.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.large.CompactWideTabCaretHost
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.motion.SpringAnimatedContent
import me.foxtails.palustris.ui.navigation.AppRoute
import me.foxtails.palustris.ui.navigation.ShellNavigator
import me.foxtails.palustris.ui.notifications.NotificationRouteResolver

/**
 * Destination router for the application shell.
 *
 * It applies the scaffold and inset policy, animates destination changes, scopes saveable
 * destination state, resolves the notification-detail and local-page routes, and dispatches to
 * the feature shell adapters: [ShellHomeDestination], [ShellSearchDestination],
 * [AppNotificationsDestinationContent], and [ShellProfileDestination]. It owns no feature state.
 * Navigation and overlay holders, scroll states, contracts, and callbacks arrive as parameters.
 * Saveable holders and scroll states stay with the shell and pass through unchanged, so
 * restoration keys and scroll positions stay stable. Post, draft, and navigation callbacks
 * travel as three bundles so the dispatch stays reviewable.
 *
 * `leftObstructionClearance` and `rightObstructionClearance` are physical. They never reverse with
 * the layout direction. Compact layout ignores both, and a row or tile that stays transparent
 * underlays the floating navigation while its interactive content clears it.
 */
@Composable
internal fun ShellDestinationContent(
    paneModifier: Modifier,
    navigator: ShellNavigator,
    overlay: ShellOverlayPresenter,
    screenStates: SaveableStateHolder,
    homeListState: LazyListState,
    searchListState: LazyListState,
    photoGridScrollState: LazyStaggeredGridState,
    profileListState: LazyListState,
    largePresentation: Boolean,
    compactWidePresentation: Boolean = false,
    rightObstructionClearance: Dp,
    leftObstructionClearance: Dp = 0.dp,
    bottomObstructionClearance: Dp,
    bottomNavigationClearance: Dp = 0.dp,
    account: Account?,
    displayedProfile: Account?,
    savedTitle: Int,
    notificationAccountIdentity: String,
    availableTimelines: Set<Timeline>,
    sessionRevision: Long,
    home: HomeContract?,
    photoGrid: PhotoGridContract,
    profile: ProfileContract,
    search: SearchContract,
    bookmarks: BookmarksContract,
    notifications: NotificationsContract,
    directMessages: DirectMessagesContract,
    accountSwitcher: AccountSwitcher,
    postCallbacks: DestinationPostCallbacks,
    draftCallbacks: DestinationDraftCallbacks,
    navigationCallbacks: DestinationNavigationCallbacks,
    homeChipListState: LazyListState? = null,
    homeChipRowVisible: Boolean = true,
    onToggleHomeChipRow: () -> Unit = {},
    useCompactWideCaret: Boolean = false,
    tabCaretHost: CompactWideTabCaretHost? = null,
) {
    val timelineChipListState = homeChipListState ?: rememberLazyListState()
    Scaffold(
        modifier = paneModifier.fillMaxSize(),
        // Compact page bodies receive top/horizontal system insets only.
        // Content must measure through the floating assembly; scrollables
        // add end clearance inside their scroll range instead.
        contentWindowInsets = when {
            navigator.page == null && navigator.notificationRoute == null && navigator.destination == Destination.Profile ->
                WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
            !largePresentation && navigator.page == null && navigator.notificationRoute == null ->
                WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            else -> ScaffoldDefaults.contentWindowInsets
        },
        topBar = {
            AppDestinationTopBar(
                page = navigator.page,
                notificationRoute = navigator.notificationRoute,
                savedTitle = savedTitle,
                onBack = { overlay.clearPostActionBubble(); if (navigator.page != null) navigator.page = null else navigator.notificationRoute = null },
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            SpringAnimatedContent(
                stateKey = navigator.destination,
                direction = navigator.destinationTransitionDirection,
                modifier = Modifier.fillMaxSize(),
            ) { animatedDestination ->
                screenStates.SaveableStateProvider(animatedDestination.name) {
                    AnimatedStatePane(
                        stateKey = navigator.notificationRoute ?: navigator.page?.name ?: "${animatedDestination.name}:content",
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (navigator.notificationRoute != null) {
                            AppNotificationDetailContent(
                                route = navigator.notificationRoute!!,
                                items = notifications.state.items,
                                onSearchHashtag = navigator::openHashtagSearch,
                                onOpenHashtagBubble = overlay::openHashtagBubble,
                                onOpenPost = { post -> navigationCallbacks.onOpenPost(post, LargePostOrigin.Notification) },
                                onOpenTarget = (navigator.notificationRoute as? AppRoute.Profile)?.let { route ->
                                    { navigationCallbacks.onOpenNotificationTarget(route) }
                                },
                                availableActions = postCallbacks.availableActions,
                                onReact = postCallbacks.onReact,
                                onReply = postCallbacks.onReply,
                                onReshare = postCallbacks.onReshare,
                                onBookmark = postCallbacks.onBookmark,
                                onReaction = postCallbacks.onReaction,
                                onQuote = postCallbacks.onQuote,
                                quoteEnabled = postCallbacks.quoteEnabled,
                                onOpenReactionBubble = { post, bounds -> overlay.openReactionBubble(post, bounds, postCallbacks.onReaction) },
                                sessionRevision = sessionRevision,
                                largeLayout = largePresentation,
                            )
                        } else if (navigator.page != null) {
                            AppLocalPageContent(
                                page = navigator.page,
                                savedPostsState = bookmarks.state,
                                drafts = draftCallbacks.drafts,
                                onLoadDraft = draftCallbacks.onLoadDraft,
                                onDeleteDraft = draftCallbacks.onDeleteDraft,
                                onRefreshSavedPosts = bookmarks.actions::refresh,
                                onLoadMoreSavedPosts = bookmarks.actions::loadMore,
                                onUnsaveSavedPost = bookmarks.actions::remove,
                                onUpgradeSavedPermissions = bookmarks.actions::upgradePermissions,
                                onReact = postCallbacks.onReact,
                                onReply = postCallbacks.onReply,
                                onReshare = postCallbacks.onReshare,
                                onSavedPostReaction = bookmarks.actions::react,
                                onOpenSavedReactionBubble = { post, bounds -> overlay.openReactionBubble(post, bounds, bookmarks.actions::react) },
                                onOpenReactionPicker = overlay::expandReactionPicker,
                                onOpenMedia = overlay::openMedia,
                                onOpenPost = navigationCallbacks.onOpenPost,
                                onOpenProfile = navigator::openProfile,
                                onSearchHashtag = navigator::openHashtagSearch,
                                onOpenHashtagBubble = overlay::openHashtagBubble,
                                onOpenUsername = navigator::openAccountSearch,
                                availableActions = postCallbacks.availableActions,
                                largeLayout = largePresentation,
                            )
                        } else when (animatedDestination) {
                            Destination.Home -> ShellHomeDestination(
                                animatedDestination = animatedDestination,
                                navigator = navigator,
                                overlay = overlay,
                                accountSwitcher = accountSwitcher,
                                postCallbacks = postCallbacks,
                                navigationCallbacks = navigationCallbacks,
                                home = home,
                                largePresentation = largePresentation,
                                rightObstructionClearance = rightObstructionClearance,
                                leftObstructionClearance = leftObstructionClearance,
                                bottomObstructionClearance = bottomObstructionClearance,
                                availableTimelines = availableTimelines,
                                homeListState = homeListState,
                                timelineChipListState = timelineChipListState,
                                homeChipRowVisible = homeChipRowVisible,
                                onToggleHomeChipRow = onToggleHomeChipRow,
                                useCompactWideCaret = useCompactWideCaret,
                                tabCaretHost = tabCaretHost,
                            )
                            Destination.Search -> ShellSearchDestination(
                                navigator = navigator,
                                overlay = overlay,
                                search = search,
                                photoGrid = photoGrid,
                                postCallbacks = postCallbacks,
                                navigationCallbacks = navigationCallbacks,
                                largePresentation = largePresentation,
                                rightObstructionClearance = rightObstructionClearance,
                                leftObstructionClearance = leftObstructionClearance,
                                bottomObstructionClearance = bottomObstructionClearance,
                                bottomNavigationClearance = bottomNavigationClearance,
                                searchListState = searchListState,
                                photoGridScrollState = photoGridScrollState,
                                account = account,
                                sessionRevision = sessionRevision,
                                useCompactWideCaret = useCompactWideCaret,
                                tabCaretHost = tabCaretHost,
                            )
                            Destination.Notifications -> AppNotificationsDestinationContent(
                                panel = navigator.notificationsPanel,
                                account = account,
                                compactLayout = !largePresentation,
                                compactNavigationVisible = navigator.navigationVisible,
                                rightObstructionClearance = rightObstructionClearance,
                                leftObstructionClearance = leftObstructionClearance,
                                bottomObstructionClearance = bottomObstructionClearance,
                                bottomNavigationClearance = bottomNavigationClearance,
                                useCompactWideCaret = useCompactWideCaret,
                                tabCaretHost = tabCaretHost,
                                notificationAccountIdentity = notificationAccountIdentity,
                                notifications = notifications,
                                onOpenNotification = { notification ->
                                    overlay.clearPostActionBubble()
                                    if (largePresentation) navigator.clearSelectedPost()
                                    navigator.notificationRoute = NotificationRouteResolver.resolve(notification)
                                },
                                onOpenSettings = {
                                    if (account != null) {
                                        overlay.clearPostActionBubble()
                                        navigator.openNotificationSettingsOverlay()
                                    }
                                },
                                directMessages = directMessages,
                            )
                            Destination.Profile -> ShellProfileDestination(
                                navigator = navigator,
                                overlay = overlay,
                                profile = profile,
                                postCallbacks = postCallbacks,
                                navigationCallbacks = navigationCallbacks,
                                account = account,
                                displayedProfile = displayedProfile,
                                largePresentation = largePresentation,
                                compactWidePresentation = compactWidePresentation,
                                rightObstructionClearance = rightObstructionClearance,
                                leftObstructionClearance = leftObstructionClearance,
                                bottomObstructionClearance = bottomObstructionClearance,
                                profileListState = profileListState,
                                useCompactWideCaret = useCompactWideCaret,
                                tabCaretHost = tabCaretHost,
                            )
                        }
                    }
                }
            }
        }
    }
}
