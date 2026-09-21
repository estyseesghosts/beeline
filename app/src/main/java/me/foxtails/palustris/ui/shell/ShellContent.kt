@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package me.foxtails.palustris.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.large.LargeLayoutMode
import me.foxtails.palustris.ui.large.LargeScreenShell
import me.foxtails.palustris.ui.layout.CompactHomeTimelineSpacing
import me.foxtails.palustris.ui.layout.CompactOverlayHorizontalPadding
import me.foxtails.palustris.ui.layout.CompactOverlayVerticalPadding
import me.foxtails.palustris.ui.layout.CompactTimelineTabsHeight
import me.foxtails.palustris.ui.layout.compactGlobalNavigationPositioningInsets
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.compactFloatingEnter
import me.foxtails.palustris.ui.motion.compactFloatingExit
import me.foxtails.palustris.ui.navigation.CompactContextualNavigationBar
import me.foxtails.palustris.ui.navigation.HomeTimelineTabs
import me.foxtails.palustris.ui.navigation.ShellNavigator
import me.foxtails.palustris.ui.navigation.contextualActionFor
import me.foxtails.palustris.ui.posts.PostPopupPresentation
import me.foxtails.palustris.ui.thread.PostThreadUiState

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
) {
    val largePresentation = presentationMode != LargeLayoutMode.Compact
    val modalOverlayOpen = navigator.overlay != null || navigator.sheet != null ||
        overlay.profileDialog || overlay.signOutDialog || overlay.mediaRequest != null ||
        overlay.profileImageRequest != null || navigator.singlePost != null ||
        overlay.emojiPickerTarget != null
    val motionScheme = LocalPalustrisMotionScheme.current

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (largePresentation) {
                    LargeScreenShell(
                        windowWidth = windowWidth,
                        selectedTarget = largeTargetFor(navigator.destination, navigator.searchPanel, navigator.notificationsPanel),
                        account = account,
                        hasDetail = navigator.singlePost != null,
                        twoPane = presentationMode == LargeLayoutMode.Expanded &&
                            (navigator.destination == Destination.Home ||
                                (navigator.destination == Destination.Profile && navigator.singlePost != null)),
                        onTargetSelected = navigator::selectLargeTarget,
                        onOpenAccounts = { overlay.clearPostActionBubble(); navigator.sheet = "Accounts" },
                        onCompose = onCompose,
                        primaryContent = { paneModifier ->
                            ShellDestinationContent(
                                paneModifier = paneModifier,
                                navigator = navigator,
                                overlay = overlay,
                                screenStates = screenStates,
                                homeListState = homeListState,
                                searchListState = searchListState,
                                photoGridScrollState = photoGridScrollState,
                                profileListState = profileListState,
                                largePresentation = largePresentation,
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
                            )
                        },
                        detailContent = { paneModifier ->
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
                    )
                    if (!largePresentation && navigator.page == null && !modalOverlayOpen) {
                        androidx.compose.animation.AnimatedVisibility(
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
                                Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), horizontalAlignment = Alignment.End) {
                                    if (navigator.destination == Destination.Home) {
                                        HomeTimelineTabs(
                                            timelines = availableTimelines,
                                            selected = navigator.timeline,
                                            modifier = Modifier.height(CompactTimelineTabsHeight),
                                            onSelect = { item ->
                                                overlay.clearPostActionBubble()
                                                val changed = item != navigator.timeline
                                                navigator.timeline = item
                                                if (changed) home?.actions?.refresh(item)
                                            },
                                        )
                                        Spacer(Modifier.height(CompactHomeTimelineSpacing))
                                    }
                                    CompactContextualNavigationBar(
                                        destination = navigator.destination,
                                        searchPanel = navigator.searchPanel,
                                        action = contextualActionFor(
                                            destination = navigator.destination,
                                            searchPanel = navigator.searchPanel,
                                            notificationsPanel = navigator.notificationsPanel,
                                            profileTarget = displayedProfile,
                                            authenticatedAccountId = account?.id,
                                            profileState = profile.state,
                                            onCompose = onCompose,
                                            onSearchToggle = {
                                                navigator.searchPanelName = if (navigator.searchPanel == SearchPanel.Search) SearchPanel.PhotoGrid.name else SearchPanel.Search.name
                                            },
                                            onNotificationsToggle = {
                                                navigator.notificationsPanelName = if (navigator.notificationsPanel == NotificationsPanel.Notifications) NotificationsPanel.DirectMessages.name else NotificationsPanel.Notifications.name
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
                me.foxtails.palustris.ui.posts.SinglePostScreen(
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
