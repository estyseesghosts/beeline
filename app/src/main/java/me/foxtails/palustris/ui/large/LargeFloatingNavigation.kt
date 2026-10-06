package me.foxtails.palustris.ui.large

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.ui.navigation.ContextualNavigationAction
import me.foxtails.palustris.ui.navigation.ContextualNavigationActionButton
import me.foxtails.palustris.ui.navigation.WideNavigationItem
import me.foxtails.palustris.ui.navigation.WideNavigationPresentation

/** The six direct large-screen targets. Selection stays with the shell navigator. */
internal enum class LargeNavTarget {
    Home,
    Search,
    PhotoGrid,
    Notifications,
    DirectMessages,
    Profile,
}

/** Maps a shell target onto the shared vertical presentation item. */
internal fun LargeNavTarget.toWideNavigationItem(): WideNavigationItem = when (this) {
    LargeNavTarget.Home -> WideNavigationItem.Home
    LargeNavTarget.Search -> WideNavigationItem.Search
    LargeNavTarget.PhotoGrid -> WideNavigationItem.PhotoGrid
    LargeNavTarget.Notifications -> WideNavigationItem.Notifications
    LargeNavTarget.DirectMessages -> WideNavigationItem.DirectMessages
    LargeNavTarget.Profile -> WideNavigationItem.Profile
}

/** Maps a shared presentation item back onto the shell target. */
internal fun WideNavigationItem.toLargeNavTarget(): LargeNavTarget = when (this) {
    WideNavigationItem.Home -> LargeNavTarget.Home
    WideNavigationItem.Search -> LargeNavTarget.Search
    WideNavigationItem.PhotoGrid -> LargeNavTarget.PhotoGrid
    WideNavigationItem.Notifications -> LargeNavTarget.Notifications
    WideNavigationItem.DirectMessages -> LargeNavTarget.DirectMessages
    WideNavigationItem.Profile -> LargeNavTarget.Profile
}

/**
 * Renders the stateless floating navigation stack for a large screen.
 *
 * The stack reuses the shared vertical capsule and the shared contextual action button, so the
 * shared indicator, motion, and semantics stay authoritative. Selection and callbacks arrive as
 * parameters; this composable owns no navigation state and no measurement policy. The declared
 * size matches `NavigationCapsuleTotalHeightDp` so the caller's safe-region bounds stay exact, and
 * an absent action keeps the reserved slot instead of moving the capsule.
 *
 * Compact-wide bottom-anchors the same capsule and action inside the taller compact-wide bounds
 * and adds the contextual tab caret below the action when the current screen exposes tab chips.
 * The capsule and action keep fixed top offsets inside those bounds, so hiding the caret never
 * moves them. The caret composes conditionally with no placeholder.
 */
@Composable
internal fun LargeFloatingNavigation(
    selectedTarget: LargeNavTarget,
    account: Account?,
    action: ContextualNavigationAction?,
    onTargetSelected: (LargeNavTarget) -> Unit,
    onOpenAccounts: () -> Unit,
    modifier: Modifier = Modifier,
    isCompactWide: Boolean = false,
    tabCaret: TabCaretUiState? = null,
) {
    if (isCompactWide) {
        Box(
            modifier = modifier
                .width(NAVIGATION_CAPSULE_WIDTH_DP.dp)
                .height(NavigationCompactWideTotalHeightDp.dp)
                .testTag(LARGE_FLOATING_NAVIGATION_TAG),
        ) {
            WideNavigationPresentation(
                selectedTarget = selectedTarget.toWideNavigationItem(),
                account = account,
                onTargetSelected = { onTargetSelected(it.toLargeNavTarget()) },
                onOpenAccounts = onOpenAccounts,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(NAVIGATION_CAPSULE_WIDTH_DP.dp)
                    .height(NAVIGATION_CAPSULE_HEIGHT_DP.dp)
                    .testTag(LARGE_NAVIGATION_CAPSULE_TAG),
            )
            if (action != null) {
                ContextualNavigationActionButton(
                    action,
                    Modifier.align(Alignment.TopCenter)
                        .offset(y = (NAVIGATION_CAPSULE_HEIGHT_DP + NAVIGATION_PLACEMENT_GAP_DP).dp)
                        .size(NAVIGATION_ACTION_SIZE_DP.dp),
                )
            } else {
                Spacer(
                    Modifier.align(Alignment.TopCenter)
                        .offset(y = (NAVIGATION_CAPSULE_HEIGHT_DP + NAVIGATION_PLACEMENT_GAP_DP).dp)
                        .size(NAVIGATION_ACTION_SIZE_DP.dp),
                )
            }
            if (tabCaret != null) {
                ContextualTabCaretButton(
                    state = tabCaret,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        return
    }
    Column(
        modifier = modifier
            .width(NAVIGATION_CAPSULE_WIDTH_DP.dp)
            .height(NavigationCapsuleTotalHeightDp.dp)
            .testTag(LARGE_FLOATING_NAVIGATION_TAG),
        verticalArrangement = Arrangement.spacedBy(
            space = NAVIGATION_PLACEMENT_GAP_DP.dp,
            alignment = Alignment.CenterVertically,
        ),
    ) {
        WideNavigationPresentation(
            selectedTarget = selectedTarget.toWideNavigationItem(),
            account = account,
            onTargetSelected = { onTargetSelected(it.toLargeNavTarget()) },
            onOpenAccounts = onOpenAccounts,
            modifier = Modifier
                .width(NAVIGATION_CAPSULE_WIDTH_DP.dp)
                .height(NAVIGATION_CAPSULE_HEIGHT_DP.dp)
                .testTag(LARGE_NAVIGATION_CAPSULE_TAG),
        )
        if (action != null) {
            ContextualNavigationActionButton(action, Modifier.size(NAVIGATION_ACTION_SIZE_DP.dp))
        } else {
            Spacer(Modifier.size(NAVIGATION_ACTION_SIZE_DP.dp))
        }
    }
}

internal const val LARGE_FLOATING_NAVIGATION_TAG = "large_floating_navigation"
internal const val LARGE_NAVIGATION_CAPSULE_TAG = "large_navigation_capsule"
