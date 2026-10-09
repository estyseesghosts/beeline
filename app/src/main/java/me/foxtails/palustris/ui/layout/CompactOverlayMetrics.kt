@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package me.foxtails.palustris.ui.layout

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val CompactNavigationHeight = 56.dp
internal val CompactTimelineTabsHeight = 56.dp
internal val CompactOverlayControlSpacing = 14.dp
internal val CompactHomeTimelineSpacing = 8.dp
internal val CompactOverlayHorizontalPadding = 16.dp
internal val CompactOverlayVerticalPadding = 12.dp
internal val CompactSearchChipRowHeight = 48.dp
internal val CompactSearchControlsSpacing = 8.dp
internal val CompactSearchFieldHeight = 56.dp
internal val CompactFilterDockHeight = CompactSearchChipRowHeight + CompactSearchControlsSpacing
internal val CompactSearchDockHeight = CompactFilterDockHeight + CompactSearchFieldHeight
internal val CompactContextualControlsPositioningClearance = CompactNavigationHeight +
    CompactOverlayControlSpacing + CompactOverlayVerticalPadding
internal val LegacyFeedBottomClearance = 96.dp
internal val CompactNavigationCapsuleWidth = 212.dp
internal val CompactNavigationControlSize = 56.dp
internal val CompactNavigationControlSpacing = 8.dp
internal val CompactNavigationBarMaxWidth = 480.dp

/** The width of the compact navigation bar: the window less both side paddings, capped at its maximum. */
internal fun compactNavigationBarWidth(windowWidth: Dp): Dp =
    (windowWidth - CompactOverlayHorizontalPadding * 2f).coerceIn(0.dp, CompactNavigationBarMaxWidth)

/**
 * Whether the compact bar has dead space left of the pill for a contextual tab caret.
 *
 * The bar is end-aligned: capsule and contextual action keep their place, and the caret needs its
 * own control and one gap in the space to their left. The contextual action slot is always counted,
 * so the choice never changes when a screen has no action. Narrower windows keep the inline caret.
 */
internal fun compactContextualCaretFits(windowWidth: Dp): Boolean {
    val required = CompactNavigationCapsuleWidth + CompactNavigationControlSize * 2f +
        CompactNavigationControlSpacing * 2f
    return compactNavigationBarWidth(windowWidth) >= required
}

/** Places compact chrome above either the system bars or the IME, never their sum. */
@Composable
internal fun compactGlobalNavigationPositioningInsets(
    ime: WindowInsets = WindowInsets.ime,
): WindowInsets =
    WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom)
        .union(ime.only(WindowInsetsSides.Bottom))

@Composable
internal fun compactContextualControlsPositioningInsets(
    navigationVisible: Boolean,
    ime: WindowInsets = WindowInsets.ime,
): WindowInsets = compactGlobalNavigationPositioningInsets(ime)
    .add(
        WindowInsets(
            bottom = if (navigationVisible) CompactContextualControlsPositioningClearance else 0.dp,
        ),
    )
    .only(WindowInsetsSides.Bottom)

@Composable
internal fun compactScrollEndClearance(
    controlStackHeight: Dp,
    navigationVisible: Boolean,
    ime: WindowInsets = WindowInsets.ime,
): Dp {
    val controlsBottom = compactContextualControlsPositioningInsets(navigationVisible, ime)
        .asPaddingValues()
        .calculateBottomPadding()
    // Controls and content use the same stack above the larger inset. The IME
    // replaces the system-bar base; it must not replace navigation clearance.
    return controlsBottom + controlStackHeight
}

@Composable
internal fun compactHomeScrollEndClearance(): Dp {
    val safeBottom = compactGlobalNavigationPositioningInsets()
        .asPaddingValues()
        .calculateBottomPadding()
    return safeBottom + CompactNavigationHeight +
        CompactHomeTimelineSpacing + CompactTimelineTabsHeight +
        (CompactOverlayVerticalPadding * 2f)
}
