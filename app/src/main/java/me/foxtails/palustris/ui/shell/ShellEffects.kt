package me.foxtails.palustris.ui.shell

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.ui.composer.ComposerOwner
import me.foxtails.palustris.ui.media.MediaTransitionRegistry
import me.foxtails.palustris.ui.navigation.ShellNavigator
import me.foxtails.palustris.ui.posts.PostPopupPresentation
import me.foxtails.palustris.ui.posts.PostRepostConfirmationState

/** Applies shell side effects without owning shell state or rendering content. */
@Composable
internal fun ShellEffects(
    accountId: AccountId?,
    sessionGeneration: Long,
    sessionRevision: Long,
    composerOwner: ComposerOwner,
    navigator: ShellNavigator,
    mediaTransitionRegistry: MediaTransitionRegistry,
    overlay: ShellOverlayPresenter,
    repostConfirmationOwner: PostRepostConfirmationState,
    thread: ThreadContract,
    photoGrid: PhotoGridContract,
    photoGridScrollState: LazyStaggeredGridState,
    postActionOwner: PostPopupPresentation?,
    profileEditorOpen: Boolean,
) {
    // The profile editor lives with the session's profile owner. After process recreation or an
    // account change the saved overlay key has no editor, so close it rather than show nothing.
    LaunchedEffect(accountId, sessionGeneration, sessionRevision) {
        navigator.closeEditProfileWithoutEditor(profileEditorOpen)
    }

    LaunchedEffect(composerOwner.navigation) {
        if (composerOwner.navigation != null) {
            navigator.openComposerOverlay()
            composerOwner.consumeNavigation()
        }
    }

    LaunchedEffect(accountId) {
        mediaTransitionRegistry.releaseOtherAccounts(accountId?.toString())
        overlay.clearForAccountChange()
        repostConfirmationOwner.dismiss()
        thread.actions.deactivate()
    }
    // A same-account reauthentication changes the durable revision but not the account id.
    // Rebind the popup authority so a stale handler cannot run a later reaction selection.
    LaunchedEffect(sessionGeneration, sessionRevision) {
        mediaTransitionRegistry.endActive()
        overlay.clearForSessionChange()
    }
    LaunchedEffect(navigator.destination, navigator.searchPanel, accountId, sessionGeneration) {
        if (navigator.destination == Destination.Search && navigator.searchPanel == SearchPanel.PhotoGrid) {
            photoGrid.actions.ensureLoaded()
        }
    }
    LaunchedEffect(photoGrid.state.selectedFeed, accountId, sessionGeneration) {
        photoGridScrollState.scrollToItem(0)
        if (navigator.singlePostOrigin == LargePostOrigin.PhotoGrid) navigator.clearSelectedPost()
    }
    LaunchedEffect(navigator.singlePost?.post?.id, navigator.singlePostOrigin, navigator.singlePostOrigin.supportsComments()) {
        thread.actions.activate(navigator.singlePost, navigator.singlePostOrigin.supportsComments())
    }
    LaunchedEffect(navigator.singlePostOrigin, navigator.singlePost?.post?.id, thread.state?.focal) {
        if (navigator.singlePostOrigin != LargePostOrigin.PhotoGrid) return@LaunchedEffect
        val selected = navigator.singlePost ?: return@LaunchedEffect
        val focal = thread.state?.focal ?: return@LaunchedEffect
        if (focal.fetchedBy == selected.fetchedBy &&
            focal.sessionRevision == selected.sessionRevision &&
            focal.effectiveTargetId() == selected.effectiveTargetId()
        ) {
            // Keep the wide Photo Grid detail snapshot aligned with the thread owner after an
            // optimistic mutation. Without this, the navigator can keep rendering stale fields.
            navigator.singlePost = focal
        }
    }
    LaunchedEffect(navigator.destination, navigator.page, navigator.overlayKey, navigator.sheet, overlay.profileDialog, overlay.signOutDialog, overlay.mediaRequest, navigator.singlePost, navigator.notificationRoute) {
        overlay.clearPostActionBubble()
        postActionOwner?.dismiss()
        repostConfirmationOwner.dismiss()
        postActionOwner?.dismiss()
    }
    LaunchedEffect(overlay.pendingExpandedReactionTarget, overlay.postActionBubbleTarget) {
        overlay.promotePendingExpansion()
    }
}
