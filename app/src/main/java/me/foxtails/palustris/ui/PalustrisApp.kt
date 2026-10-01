@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package me.foxtails.palustris.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.ui.composer.ComposerOwnerContext
import me.foxtails.palustris.ui.composer.rememberComposerOwner
import me.foxtails.palustris.ui.large.LargeLayoutMode
import me.foxtails.palustris.ui.large.largeLayoutMode
import me.foxtails.palustris.ui.media.LocalMediaTransitionRegistry
import me.foxtails.palustris.ui.media.MediaTransitionRegistry
import me.foxtails.palustris.ui.navigation.AppRoute
import me.foxtails.palustris.ui.navigation.NavigationMode
import me.foxtails.palustris.ui.navigation.NavigationModeObserver
import me.foxtails.palustris.ui.navigation.ShellBackState
import me.foxtails.palustris.ui.navigation.ShellTopSurface
import me.foxtails.palustris.ui.navigation.edgeSwipeDismiss
import me.foxtails.palustris.ui.navigation.rememberShellNavigator
import me.foxtails.palustris.ui.navigation.topSurfaceForBack
import me.foxtails.palustris.ui.posts.LocalPostPopupOwner
import me.foxtails.palustris.ui.posts.LocalPostRepostConfirmationState
import me.foxtails.palustris.ui.posts.PostRepostConfirmationState
import me.foxtails.palustris.ui.shell.AccountSwitcher
import me.foxtails.palustris.ui.shell.BookmarksContract
import me.foxtails.palustris.ui.shell.ComposerContract
import me.foxtails.palustris.ui.shell.DestinationDraftCallbacks
import me.foxtails.palustris.ui.shell.DestinationNavigationCallbacks
import me.foxtails.palustris.ui.shell.DestinationPostCallbacks
import me.foxtails.palustris.ui.shell.DirectMessagesContract
import me.foxtails.palustris.ui.shell.DraftsContract
import me.foxtails.palustris.ui.shell.EmojiPresentation
import me.foxtails.palustris.ui.shell.HomeContract
import me.foxtails.palustris.ui.shell.NotificationSettingsContract
import me.foxtails.palustris.ui.shell.NotificationsContract
import me.foxtails.palustris.ui.shell.PhotoGridContract
import me.foxtails.palustris.ui.shell.PostInteractions
import me.foxtails.palustris.ui.shell.ProfileContract
import me.foxtails.palustris.ui.shell.SearchContract
import me.foxtails.palustris.ui.shell.ShellContent
import me.foxtails.palustris.ui.shell.ShellDetailCallbacks
import me.foxtails.palustris.ui.shell.ShellEffects
import me.foxtails.palustris.ui.shell.ThreadContract
import me.foxtails.palustris.ui.shell.Destination
import me.foxtails.palustris.ui.shell.LargePostOrigin
import me.foxtails.palustris.ui.shell.NotificationsPanel
import me.foxtails.palustris.ui.shell.Overlay
import me.foxtails.palustris.ui.shell.SearchPanel
import me.foxtails.palustris.ui.shell.savedCollectionTitle
import me.foxtails.palustris.ui.shell.resolveSelectedPost
import me.foxtails.palustris.ui.shell.supportsComments
import me.foxtails.palustris.ui.shell.ShellOverlayHost
import me.foxtails.palustris.ui.shell.rememberShellOverlayPresenter

@Composable
fun PalustrisApp(
    account: Account?,
    sessionGeneration: Long,
    sessionRevision: Long,
    home: HomeContract?,
    photoGrid: PhotoGridContract,
    profile: ProfileContract,
    accountSwitcher: AccountSwitcher,
    composer: ComposerContract,
    search: SearchContract,
    postInteractions: PostInteractions,
    thread: ThreadContract,
    draftsContract: DraftsContract,
    emojiPresentation: EmojiPresentation,
    bookmarks: BookmarksContract,
    notifications: NotificationsContract,
    directMessages: DirectMessagesContract,
    initialNotificationRoute: AppRoute?,
    notificationSettings: NotificationSettingsContract,
) {
    val mediaTransitionRegistry = remember { MediaTransitionRegistry() }
    val repostConfirmationOwner = remember(account?.id, sessionGeneration, sessionRevision) { PostRepostConfirmationState() }
    val postActionOwner = LocalPostPopupOwner.current
    CompositionLocalProvider(
        LocalMediaTransitionRegistry provides mediaTransitionRegistry,
        LocalPostRepostConfirmationState provides repostConfirmationOwner,
    ) {
        val onReact = postInteractions.actions::favorite
        val onReshare = postInteractions.actions::repost
        val onBookmark = postInteractions.actions::bookmark
        val onReaction = postInteractions.actions::react
        val availableActions = postInteractions.availableActions
        val quoteEnabled = postInteractions.quoteEnabled
        val overlay = rememberShellOverlayPresenter(account, sessionRevision, emojiPresentation.capabilities.reactionMutation)
        val screenStates = rememberSaveableStateHolder()
        val homeListState = androidx.compose.foundation.lazy.rememberLazyListState()
        val searchListState = androidx.compose.foundation.lazy.rememberLazyListState()
        val photoGridScrollState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()
        val profileListState = androidx.compose.foundation.lazy.rememberLazyListState()
        val motionScheme = me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme.current
        val availableTimelines = if (account == null) Timeline.entries.toSet() else home?.state?.availableTimelines ?: setOf(Timeline.Home)
        val navigator = rememberShellNavigator(
            accountId = account?.id,
            sessionRevision = sessionRevision,
            initialRoute = initialNotificationRoute,
            availableTimelines = availableTimelines,
            selectedHomeTimeline = home?.state?.selectedTimeline,
            reducedMotion = motionScheme.reducedMotion,
            onClearTransient = overlay::clearPostActionBubble,
            onSearch = search.actions::search,
            onStartConversation = directMessages.actions::startConversation,
        )
        val profileTargetId = navigator.viewedProfile?.id ?: account?.id
        val displayedProfile = profile.state.account?.takeIf { it.id == profileTargetId } ?: navigator.viewedProfile ?: account
        val savedTitle = savedCollectionTitle(bookmarks.state?.kind)
        val notificationAccountIdentity = account?.id?.let { "${it.connection.origin}\u0000${it.localId}" } ?: "preview"
        val composerOwner = rememberComposerOwner(
            context = ComposerOwnerContext(account, composer, PostAction.Reply in availableActions, quoteEnabled, navigator.overlay == Overlay.Composer, navigator.overlay != null && navigator.overlay != Overlay.Composer),
            draftsContract = draftsContract,
            sessionGeneration = sessionGeneration,
            sessionRevision = sessionRevision,
        )
        val hasDraftChanges = composerOwner.hasChanges
        ShellEffects(account?.id, sessionGeneration, sessionRevision, composerOwner, navigator, mediaTransitionRegistry, overlay, repostConfirmationOwner, thread, photoGrid, photoGridScrollState, postActionOwner)

        fun openComposer() { overlay.clearPostActionBubble(); composerOwner.requestNew() }
        fun closeComposer() {
            if (composer.publishing || composerOwner.closing) return
            if (hasDraftChanges) composerOwner.save { navigator.closeOverlay() } else navigator.closeOverlay()
        }
        fun discardProfileEditor() { navigator.closeOverlay(); profile.actions.closeEditor() }
        fun closeProfile() {
            if (profile.state.savingProfile) return
            if (profile.state.editorDirty) overlay.profileDialog = true else discardProfileEditor()
        }
        fun openProfileEditor() {
            if (account != null && displayedProfile?.id == account.id && profile.state.editableSupported) {
                overlay.clearPostActionBubble(); profile.actions.openEditor(); navigator.openEditProfileOverlay()
            }
        }
        fun handleReply(target: OwnedPost) { overlay.clearPostActionBubble(); composerOwner.requestReply(target) }
        fun handleQuote(target: OwnedPost) { overlay.clearPostActionBubble(); composerOwner.requestQuote(target) }
        fun openNotificationTarget(route: AppRoute) {
            if (route !is AppRoute.Profile) { navigator.notificationRoute = null; return }
            val target = notifications.state.items.firstOrNull { it.target == me.foxtails.palustris.domain.NotificationTarget.Profile(route.profileId) }?.actors?.firstOrNull { it.id == route.profileId }
            if (target != null) navigator.openProfile(target) else navigator.notificationRoute = null
        }
        fun openSinglePost(post: OwnedPost, origin: LargePostOrigin = LargePostOrigin.Other) { overlay.mediaRequest = null; navigator.openSinglePost(post, origin) }
        val selectedThreadState = thread.state?.takeIf { it.focal?.effectiveTargetId() == navigator.singlePost?.effectiveTargetId() }
        val selectedPost = selectedThreadState?.focal ?: resolveSelectedPost(navigator.singlePost, navigator.singlePostOrigin, home, photoGrid, bookmarks, profile)

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val windowWidth = maxWidth
            val presentationMode = largeLayoutMode(maxWidth.value)
            val largePresentation = presentationMode != LargeLayoutMode.Compact
            val navigationMode = NavigationModeObserver.current(LocalView.current)
            SystemBars(overlay.mediaRequest != null || overlay.profileImageRequest != null, largePresentation)
            fun backState() = ShellBackState(overlay.mediaRequest != null, overlay.profileImageRequest != null, largePresentation, navigator.overlay == Overlay.NotificationSettings, navigator.overlay == Overlay.Composer, navigator.overlay == Overlay.EditProfile, navigator.singlePost != null, navigator.notificationRoute != null, navigator.page != null, navigator.destination == Destination.Home)
            fun dismissTopSurface() {
                when (topSurfaceForBack(backState())) {
                    ShellTopSurface.ProfileImage -> overlay.profileImageRequest = null
                    ShellTopSurface.NotificationSettings -> navigator.closeOverlay()
                    ShellTopSurface.Composer -> closeComposer()
                    ShellTopSurface.EditProfile -> closeProfile()
                    ShellTopSurface.SinglePost -> navigator.clearSelectedPost()
                    ShellTopSurface.NotificationRoute -> navigator.notificationRoute = null
                    ShellTopSurface.Page -> navigator.page = null
                    ShellTopSurface.Home -> navigator.selectDestination(Destination.Home)
                    null -> Unit
                }
            }
            BackHandler(enabled = topSurfaceForBack(backState()) != null, onBack = ::dismissTopSurface)
            Box(Modifier.fillMaxSize().edgeSwipeDismiss(navigationMode == NavigationMode.NonGesture && topSurfaceForBack(backState()) != null, ::dismissTopSurface)) {
                ShellContent(
                    navigator = navigator, overlay = overlay, account = account, sessionRevision = sessionRevision,
                    home = home, photoGrid = photoGrid, profile = profile, accountSwitcher = accountSwitcher, search = search,
                    bookmarks = bookmarks, notifications = notifications, directMessages = directMessages, emojiPresentation = emojiPresentation,
                    screenStates = screenStates, homeListState = homeListState, searchListState = searchListState,
                    photoGridScrollState = photoGridScrollState, profileListState = profileListState, availableTimelines = availableTimelines,
                    displayedProfile = displayedProfile, savedTitle = savedTitle, notificationAccountIdentity = notificationAccountIdentity,
                    postCallbacks = DestinationPostCallbacks(availableActions, quoteEnabled, onReact, ::handleReply, onReshare, onBookmark, onReaction, ::handleQuote),
                    draftCallbacks = DestinationDraftCallbacks(composerOwner.drafts, { overlay.clearPostActionBubble(); composerOwner.requestDraft(it) }, composerOwner::deleteDraft),
                    navigationCallbacks = DestinationNavigationCallbacks(::openSinglePost, ::openNotificationTarget, ::openProfileEditor),
                    detailCallbacks = ShellDetailCallbacks({ navigator.clearSelectedPost() }, onReact, ::handleReply, onReshare, onBookmark, onReaction, navigator::openProfile, navigator::openHashtagSearch, overlay::openHashtagBubble, { post, bounds, handler -> overlay.openReactionBubble(post, bounds, handler) }, overlay::expandReactionPicker, overlay::openMedia, navigator::openAccountSearch, thread.actions::refresh, thread.actions::continueAcquisition, quoteEnabled, ::handleQuote),
                    selectedPost = selectedPost, selectedThreadState = selectedThreadState, thread = thread, availableActions = availableActions,
                    onCompose = ::openComposer, postActionOwner = postActionOwner, presentationMode = presentationMode, windowWidth = windowWidth,
                )
            }
            ShellOverlayHost(navigator, overlay, emojiPresentation, account, onReact, ::handleReply, onReshare, composerOwner, composer, profile, notificationSettings, accountSwitcher, ::closeComposer, ::closeProfile, { navigator.closeOverlay() }, ::discardProfileEditor)
        }
    }
}
