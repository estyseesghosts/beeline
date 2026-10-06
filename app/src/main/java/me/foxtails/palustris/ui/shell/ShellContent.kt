@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package me.foxtails.palustris.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.large.LargeLayoutMode
import me.foxtails.palustris.ui.large.LargeScreenShell
import me.foxtails.palustris.ui.large.NavigationFit
import me.foxtails.palustris.ui.large.toWideNavigationItem
import me.foxtails.palustris.ui.layout.CompactHomeTimelineSpacing
import me.foxtails.palustris.ui.layout.CompactNavigationHeight
import me.foxtails.palustris.ui.layout.CompactOverlayHorizontalPadding
import me.foxtails.palustris.ui.layout.CompactOverlayVerticalPadding
import me.foxtails.palustris.ui.layout.CompactTimelineTabsHeight
import me.foxtails.palustris.ui.layout.compactGlobalNavigationPositioningInsets
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.compactFloatingEnter
import me.foxtails.palustris.ui.motion.compactFloatingExit
import me.foxtails.palustris.ui.components.DestinationChipRow
import me.foxtails.palustris.ui.large.CompactWideTabCaretHost
import me.foxtails.palustris.ui.large.rememberCompactWideTabCaretHost
import me.foxtails.palustris.ui.navigation.CompactContextualNavigationBar
import me.foxtails.palustris.ui.navigation.homeTimelineChipEntries
import me.foxtails.palustris.ui.navigation.ShellNavigator
import me.foxtails.palustris.ui.navigation.contextualActionFor
import me.foxtails.palustris.ui.navigation.wideContextualAction
import me.foxtails.palustris.ui.posts.PostPopupPresentation
import me.foxtails.palustris.ui.posts.SinglePostScreen
import me.foxtails.palustris.ui.thread.PostThreadUiState

/**
 * Renders the compact bottom navigation used when the vertical stack does not fit.
 *
 * This is the existing compact surface: the grouped capsule, its contextual action, and the Home
 * timeline tabs. It owns no navigation state. Selection and callbacks stay with the navigator and
 * the feature contracts.
 */
@Composable
private fun BoxScope.CompactShellNavigation(
    navigator: ShellNavigator,
    overlay: ShellOverlayPresenter,
    account: Account?,
    profile: ProfileContract,
    home: HomeContract?,
    availableTimelines: Set<Timeline>,
    displayedProfile: Account?,
    navigationCallbacks: DestinationNavigationCallbacks,
    onCompose: () -> Unit,
    showTimelineTabs: Boolean,
    homeChipListState: LazyListState,
    homeChipRowVisible: Boolean,
    onToggleHomeChipRow: () -> Unit,
) {
    val motionScheme = LocalPalustrisMotionScheme.current
    AnimatedVisibility(
        visible = navigator.navigationVisible,
        enter = motionScheme.compactFloatingEnter(bottom = true),
        exit = motionScheme.compactFloatingExit(bottom = true),
        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).zIndex(1f),
    ) {
        Box(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(compactGlobalNavigationPositioningInsets())
                .padding(CompactOverlayHorizontalPadding, CompactOverlayVerticalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                if (showTimelineTabs && navigator.destination == Destination.Home) {
                    DestinationChipRow(
                        entries = homeTimelineChipEntries(
                            timelines = availableTimelines,
                            selected = navigator.timeline,
                        ) { item ->
                            overlay.clearPostActionBubble()
                            val changed = item != navigator.timeline
                            navigator.timeline = item
                            if (changed) home?.actions?.refresh(item)
                        },
                        rowContentDescription = stringResource(R.string.home_timeline_filter_description),
                        listState = homeChipListState,
                        visible = homeChipRowVisible,
                        onToggleVisibility = onToggleHomeChipRow,
                        selectedEntryKey = "home-timeline:${navigator.timeline.name}",
                        modifier = Modifier.height(CompactTimelineTabsHeight),
                        rowTestTag = "home_timeline_tabs",
                        visibilityToggleTestTag = "home_timeline_visibility",
                    )
                    Spacer(Modifier.height(CompactHomeTimelineSpacing))
                }
                CompactContextualNavigationBar(
                    destination = navigator.destination,
                    searchPanel = navigator.searchPanel,
                    notificationsPanel = navigator.notificationsPanel,
                    action = contextualActionFor(
                        destination = navigator.destination,
                        searchPanel = navigator.searchPanel,
                        notificationsPanel = navigator.notificationsPanel,
                        profileTarget = displayedProfile,
                        authenticatedAccountId = account?.id,
                        profileState = profile.state,
                        onCompose = onCompose,
                        onSearchToggle = {
                            navigator.searchPanelName = if (navigator.searchPanel == SearchPanel.Search) {
                                SearchPanel.PhotoGrid.name
                            } else {
                                SearchPanel.Search.name
                            }
                        },
                        onNotificationsToggle = {
                            navigator.notificationsPanelName =
                                if (navigator.notificationsPanel == NotificationsPanel.Notifications) {
                                    NotificationsPanel.DirectMessages.name
                                } else {
                                    NotificationsPanel.Notifications.name
                                }
                        },
                        onEditProfile = navigationCallbacks.onEditProfile,
                        onFollowProfile = profile.actions::follow,
                        onUnfollowProfile = profile.actions::unfollow,
                    ),
                    account = account,
                    onOpenAccounts = { overlay.clearPostActionBubble(); navigator.sheet = "Accounts" },
                    onDestinationSelected = navigator::selectDestination,
                )
            }
        }
    }
}

@Composable
internal fun ShellContent(
    navigator: ShellNavigator,
    overlay: ShellOverlayPresenter,
    account: Account?,
    sessionRevision: Long,
    home: HomeContract?,
    photoGrid: PhotoGridContract,
    profile: ProfileContract,
    accountSwitcher: AccountSwitcher,
    search: SearchContract,
    bookmarks: BookmarksContract,
    notifications: NotificationsContract,
    directMessages: DirectMessagesContract,
    emojiPresentation: EmojiPresentation,
    screenStates: SaveableStateHolder,
    homeListState: LazyListState,
    searchListState: LazyListState,
    photoGridScrollState: LazyStaggeredGridState,
    profileListState: LazyListState,
    availableTimelines: Set<Timeline>,
    displayedProfile: Account?,
    savedTitle: Int,
    notificationAccountIdentity: String,
    postCallbacks: DestinationPostCallbacks,
    draftCallbacks: DestinationDraftCallbacks,
    navigationCallbacks: DestinationNavigationCallbacks,
    detailCallbacks: ShellDetailCallbacks,
    selectedPost: OwnedPost?,
    selectedThreadState: PostThreadUiState?,
    thread: ThreadContract,
    availableActions: Set<PostAction>,
    onCompose: () -> Unit,
    postActionOwner: PostPopupPresentation?,
    presentationMode: LargeLayoutMode,
    windowWidth: androidx.compose.ui.unit.Dp,
    rightObstructionClearance: Dp = 0.dp,
    bottomObstructionClearance: Dp = 0.dp,
    leftObstructionClearance: Dp = 0.dp,
    navigationFit: NavigationFit = NavigationFit(useVerticalNavigation = false, safeRegion = null),
    anchorLeft: Boolean = presentationMode == LargeLayoutMode.Expanded,
) {
    val homeChipListState = rememberLazyListState()
    var homeChipRowVisible by rememberSaveable { mutableStateOf(true) }
    val compactWideTabCaretHost = rememberCompactWideTabCaretHost()
    val largePresentation = presentationMode != LargeLayoutMode.Compact
    // Vertical navigation activates below the pane width cutoff, so the destination presentation
    // follows the fit policy rather than the window width alone. Pane and back policy keep using
    // the width-derived mode, which stays independent of navigation presentation.
    val wideContent = largePresentation || navigationFit.useVerticalNavigation
    // Single-pane windows keep wide shell chrome without taking expanded tablet destination layouts.
    val compactWidePresentation = wideContent && presentationMode != LargeLayoutMode.Expanded
    val compactNavigation = !navigationFit.useVerticalNavigation
    // Compact-wide hides the inline chip caret and drives the same feature-owned visibility from
    // the contextual caret, but only while the floating stack is actually visible. A failed fit or
    // a modal overlay keeps the inline control so chips remain toggleable.
    val modalOverlayOpenBeforeContent = navigator.overlay != null || navigator.sheet != null ||
        overlay.profileDialog || overlay.signOutDialog || overlay.mediaRequest != null ||
        overlay.profileImageRequest != null || navigator.singlePost != null ||
        overlay.emojiPickerTarget != null
    val useCompactWideCaret = compactWidePresentation && navigationFit.useVerticalNavigation &&
        navigator.page == null && !modalOverlayOpenBeforeContent
    // The compact bar is the only chrome in a wide pane layout when the vertical stack cannot
    // fit, so wide content keeps its own end clearance for that bar.
    val compactFallbackClearance = if (wideContent && compactNavigation && navigator.navigationVisible) {
        val base = compactGlobalNavigationPositioningInsets().asPaddingValues().calculateBottomPadding()
        val paneBottomInset = with(LocalDensity.current) { WindowInsets.systemBars.getBottom(this).toDp() }
        (base - paneBottomInset).coerceAtLeast(0.dp) +
            CompactNavigationHeight + CompactOverlayVerticalPadding * 2f
    } else {
        0.dp
    }
    val modalOverlayOpen = modalOverlayOpenBeforeContent
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (wideContent) {
                    val selectedTarget = largeTargetFor(
                        navigator.destination, navigator.searchPanel, navigator.notificationsPanel,
                    )
                    LargeScreenShell(
                        windowWidth = windowWidth,
                        selectedTarget = selectedTarget,
                        account = account,
                        hasDetail = largePresentation && navigator.singlePost != null,
                        twoPane = presentationMode == LargeLayoutMode.Expanded &&
                            (navigator.destination == Destination.Home ||
                                (navigator.destination == Destination.Profile && navigator.singlePost != null)),
                        navigationFit = navigationFit,
                        anchorLeft = anchorLeft,
                        // Modal surfaces and local pages hide the floating stack, as compact does.
                        navigationVisible = navigator.page == null && !modalOverlayOpen,
                        action = wideContextualAction(
                            target = selectedTarget.toWideNavigationItem(),
                            profileTarget = displayedProfile,
                            authenticatedAccountId = account?.id,
                            profileState = profile.state,
                            directMessagesEnabled = account != null,
                            onCompose = onCompose,
                            onNewConversation = directMessages.actions::openRecipientFinder,
                            onEditProfile = navigationCallbacks.onEditProfile,
                            onFollowProfile = profile.actions::follow,
                            onUnfollowProfile = profile.actions::unfollow,
                        ),
                        onTargetSelected = navigator::selectLargeTarget,
                        onOpenAccounts = { overlay.clearPostActionBubble(); navigator.sheet = "Accounts" },
                        isCompactWide = compactWidePresentation,
                        tabCaret = compactWideTabCaretHost.caretState,
                        primaryContent = { paneModifier, paneLeftClearance, paneRightClearance ->
                            ShellDestinationContent(
                                paneModifier = paneModifier,
                                navigator = navigator,
                                overlay = overlay,
                                screenStates = screenStates,
                                homeListState = homeListState,
                                searchListState = searchListState,
                                photoGridScrollState = photoGridScrollState,
                                profileListState = profileListState,
                                largePresentation = wideContent,
                                compactWidePresentation = compactWidePresentation,
                                useCompactWideCaret = useCompactWideCaret,
                                tabCaretHost = compactWideTabCaretHost,
                                leftObstructionClearance = leftObstructionClearance + paneLeftClearance,
                                rightObstructionClearance = rightObstructionClearance + paneRightClearance,
                                bottomObstructionClearance = bottomObstructionClearance + compactFallbackClearance,
                                bottomNavigationClearance = compactFallbackClearance,
                                account = account,
                                displayedProfile = displayedProfile,
                                savedTitle = savedTitle,
                                notificationAccountIdentity = notificationAccountIdentity,
                                availableTimelines = availableTimelines,
                                sessionRevision = sessionRevision,
                                home = home,
                                photoGrid = photoGrid,
                                profile = profile,
                                search = search,
                                bookmarks = bookmarks,
                                notifications = notifications,
                                directMessages = directMessages,
                                accountSwitcher = accountSwitcher,
                                postCallbacks = postCallbacks,
                                draftCallbacks = draftCallbacks,
                                navigationCallbacks = navigationCallbacks,
                                homeChipListState = homeChipListState,
                                homeChipRowVisible = homeChipRowVisible,
                                onToggleHomeChipRow = { homeChipRowVisible = !homeChipRowVisible },
                            )
                        },
                        detailContent = { paneModifier, detailLeftClearance, detailRightClearance ->
                            val detail = detailActionsFor(
                                origin = navigator.singlePostOrigin,
                                profile = profile,
                                bookmarks = bookmarks,
                                fallback = fallbackDetailActions(detailCallbacks),
                                thread = detailThreadOwner(
                                    hasSelection = navigator.singlePost != null,
                                    origin = navigator.singlePostOrigin,
                                    thread = thread,
                                ),
                            )
                            AppLargeDetailPane(
                                selected = selectedPost,
                                origin = navigator.singlePostOrigin,
                                availableActions = availableActions,
                                threadState = selectedThreadState,
                                callbacks = detailCallbacks.withResolvedActions(detail),
                                modifier = paneModifier,
                                leftObstructionClearance = detailLeftClearance,
                                rightObstructionClearance = detailRightClearance,
                            )
                        },
                    )
                } else {
                    ShellDestinationContent(
                        paneModifier = Modifier.fillMaxSize(),
                        navigator = navigator,
                        overlay = overlay,
                        screenStates = screenStates,
                        homeListState = homeListState,
                        searchListState = searchListState,
                        photoGridScrollState = photoGridScrollState,
                        profileListState = profileListState,
                        largePresentation = largePresentation,
                        compactWidePresentation = compactWidePresentation,
                        useCompactWideCaret = false,
                        tabCaretHost = compactWideTabCaretHost,
                        rightObstructionClearance = rightObstructionClearance,
                        leftObstructionClearance = leftObstructionClearance,
                        bottomObstructionClearance = bottomObstructionClearance,
                        account = account,
                        displayedProfile = displayedProfile,
                        savedTitle = savedTitle,
                        notificationAccountIdentity = notificationAccountIdentity,
                        availableTimelines = availableTimelines,
                        sessionRevision = sessionRevision,
                        home = home,
                        photoGrid = photoGrid,
                        profile = profile,
                        search = search,
                        bookmarks = bookmarks,
                        notifications = notifications,
                        directMessages = directMessages,
                        accountSwitcher = accountSwitcher,
                        postCallbacks = postCallbacks,
                        draftCallbacks = draftCallbacks,
                        navigationCallbacks = navigationCallbacks,
                        homeChipListState = homeChipListState,
                        homeChipRowVisible = homeChipRowVisible,
                        onToggleHomeChipRow = { homeChipRowVisible = !homeChipRowVisible },
                    )
                }
                // Compact fallback. A failed fit keeps the existing bottom bar, including inside a
                // wide pane layout, so a window that cannot host the vertical stack keeps one
                // navigation surface.
                if (compactNavigation && navigator.page == null && !modalOverlayOpen) {
                    CompactShellNavigation(
                        navigator = navigator,
                        overlay = overlay,
                        account = account,
                        profile = profile,
                        home = home,
                        availableTimelines = availableTimelines,
                        displayedProfile = displayedProfile,
                        navigationCallbacks = navigationCallbacks,
                        onCompose = onCompose,
                        showTimelineTabs = !wideContent,
                        homeChipListState = homeChipListState,
                        homeChipRowVisible = homeChipRowVisible,
                        onToggleHomeChipRow = { homeChipRowVisible = !homeChipRowVisible },
                    )
                }
            }
        }
        ShellBubbleHost(
            navigator = navigator,
            overlay = overlay,
            postActionOwner = postActionOwner,
            emojiPresentation = emojiPresentation,
            largePresentation = largePresentation,
            account = account,
            sessionRevision = sessionRevision,
            directMessages = directMessages,
        )
        if (!largePresentation) {
            selectedPost?.let {
                val detail = detailActionsFor(
                    origin = navigator.singlePostOrigin,
                    profile = profile,
                    bookmarks = bookmarks,
                    fallback = fallbackDetailActions(detailCallbacks),
                )
                val resolved = detailCallbacks.withResolvedActions(detail)
                SinglePostScreen(
                    ownedPost = it,
                    presentation = navigator.singlePostOrigin.singlePostPresentation(),
                    onClose = resolved.onClose,
                    availableActions = availableActions,
                    onReact = resolved.onReact,
                    onReply = resolved.onReply,
                    onReshare = resolved.onReshare,
                    onBookmark = resolved.onBookmark,
                    onReaction = resolved.onReaction,
                    onOpenProfile = resolved.onOpenProfile,
                    onSearchHashtag = resolved.onSearchHashtag,
                    onOpenHashtagBubble = resolved.onOpenHashtagBubble,
                    onOpenReactionBubble = { post, bounds -> resolved.onOpenReactionBubble(post, bounds, resolved.onReaction) },
                    onOpenReactionPicker = resolved.onOpenReactionPicker,
                    onOpenMedia = resolved.onOpenMedia,
                    onOpenUsername = resolved.onOpenUsername,
                    threadState = selectedThreadState.takeIf { navigator.singlePostOrigin.supportsComments() },
                    onThreadRefresh = resolved.onThreadRefresh,
                    onThreadContinue = resolved.onThreadContinue,
                )
            }
        }
    }
}
