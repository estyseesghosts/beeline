@file:OptIn(androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)

package me.foxtails.palustris.ui.large

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.ui.navigation.ContextualNavigationAction

/**
 * Places the floating navigation stack inside the selected safe region.
 *
 * The returned rect is physical window-space dp. It holds the complete stack, so the shared
 * capsule and contextual action never reach a cutout, a system bar, a mandatory gesture strip,
 * or a structural hinge. Invalid or failed fits return no bounds; the caller keeps compact navigation.
 *
 * Compact-wide bottom-anchors the taller stack from the bottom safe edge and always reserves the
 * tab-caret slot, so the capsule and action stay fixed whether the caret is present or not.
 */
internal fun floatingNavigationBounds(
    fit: NavigationFit,
    anchorLeft: Boolean,
    isCompactWide: Boolean = false,
): Rect? {
    if (!fit.useVerticalNavigation) return null
    val region = fit.safeRegion ?: return null
    val width = NAVIGATION_CAPSULE_WIDTH_DP
    val height = if (isCompactWide) NavigationCompactWideTotalHeightDp else NavigationCapsuleTotalHeightDp
    if (region.width < NavigationCapsuleTotalWidthDp || region.height < height) return null
    val left = if (anchorLeft) region.left + NAVIGATION_PLACEMENT_GAP_DP
    else region.right - NAVIGATION_PLACEMENT_GAP_DP - width
    val top = if (isCompactWide) region.bottom - height
    else region.top + (region.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

/**
 * Reserves interaction content for the pane that the floating stack covers.
 *
 * The value is measured against the pane, not the window, so a two-pane layout clears only the
 * pane that holds the controls. The pane and the stack share the physical window coordinate space.
 */
internal fun navigationPaneClearance(pane: Rect, controls: Rect?, anchorLeft: Boolean): Float {
    if (controls == null || !pane.overlaps(controls)) return 0f
    return if (anchorLeft) (controls.right - pane.left).coerceIn(0f, pane.width)
    else (pane.right - controls.left).coerceIn(0f, pane.width)
}

@Composable
internal fun LargeScreenShell(
    windowWidth: Dp,
    selectedTarget: LargeNavTarget,
    account: Account?,
    hasDetail: Boolean,
    twoPane: Boolean,
    navigationFit: NavigationFit,
    anchorLeft: Boolean,
    navigationVisible: Boolean,
    action: ContextualNavigationAction?,
    onTargetSelected: (LargeNavTarget) -> Unit,
    onOpenAccounts: () -> Unit,
    primaryContent: @Composable (Modifier, Dp, Dp) -> Unit,
    detailContent: @Composable (Modifier, Dp, Dp) -> Unit,
    modifier: Modifier = Modifier,
    isCompactWide: Boolean = false,
    tabCaret: TabCaretUiState? = null,
    chipEdgeBleed: Boolean = false,
    topEdgeBleed: Boolean = false,
    reserveHiddenNavigation: Boolean = false,
) {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val density = LocalDensity.current
    // Pane placement is physical. A forced layout direction must not move a pane across a hinge.
    val physicalDirection = LayoutDirection.Ltr
    val bars = WindowInsets.systemBars
    val origin = largeContentOriginPx(
        leftInsetPx = bars.getLeft(density, physicalDirection).toFloat(),
        topInsetPx = bars.getTop(density).toFloat(),
    )
    // A transient overlay hides the stack but keeps its space, so panes do not reflow behind it.
    val controls = if (navigationVisible || reserveHiddenNavigation) {
        floatingNavigationBounds(navigationFit, anchorLeft, isCompactWide)
    } else {
        null
    }
    Box(modifier.fillMaxSize().testTag(LARGE_SCREEN_SHELL_TAG)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(bars).testTag(LARGE_CONTENT_REGION_TAG),
        ) {
            val features = adaptiveInfo.windowPosture.hingeList.map { hinge ->
                LargeFoldingFeature(
                    bounds = largeFeatureBoundsInContentPx(hinge.bounds, origin),
                    isVertical = hinge.isVertical,
                    isSeparating = hinge.isSeparating,
                    isOccluding = hinge.isOccluding,
                )
            }
            val layout = calculateLargePaneLayout(
                windowWidthDp = windowWidth.value,
                contentWidthDp = maxWidth.value,
                contentHeightDp = maxHeight.value,
                density = density.density,
                foldingFeatures = features,
            )
            val showDetail = hasDetail && twoPane && layout.detail != null
            val primary = primaryPaneBounds(layout, twoPane)
            val windowPane = Rect(
                left = (primary.left + origin.x) / density.density,
                top = (primary.top + origin.y) / density.density,
                right = (primary.right + origin.x) / density.density,
                bottom = (primary.bottom + origin.y) / density.density,
            )
            // A chip row breaks out of the outer margin on the sides where the primary pane touches it.
            // Single-pane detail keeps the margin, and so does every side that borders another pane.
            val bleed = paneEdgeBleed(
                primary = primary,
                windowPane = windowPane,
                regionWidthPx = with(density) { maxWidth.toPx() },
                windowWidthDp = windowWidth.value,
                density = density.density,
                sides = PaneBleedSides(
                    horizontal = chipEdgeBleed && !(hasDetail && !showDetail),
                    top = topEdgeBleed && !(hasDetail && !showDetail),
                ),
            )
            val primarySlot = Rect(
                left = primary.left - bleed.left.value * density.density,
                top = primary.top - bleed.top.value * density.density,
                right = primary.right + bleed.right.value * density.density,
                bottom = primary.bottom,
            )
            val bleedPane = Rect(
                left = windowPane.left - bleed.left.value,
                top = windowPane.top - bleed.top.value,
                right = windowPane.right + bleed.right.value,
                bottom = windowPane.bottom,
            )
            val primaryClearance = navigationPaneClearance(bleedPane, controls, anchorLeft).dp
            // On a split layout, either physical anchor can overlap the detail pane instead.
            val detailWindowPane = layout.detail?.let { detail ->
                Rect(
                    left = (detail.left + origin.x) / density.density,
                    top = (detail.top + origin.y) / density.density,
                    right = (detail.right + origin.x) / density.density,
                    bottom = (detail.bottom + origin.y) / density.density,
                )
            }
            val detailClearance = detailWindowPane?.let {
                navigationPaneClearance(it, controls, anchorLeft).dp
            } ?: primaryClearance
            // The widened pane keeps the margin as clearance on a bleeding side.
            val primaryLeftClearance = maxOf(if (anchorLeft) primaryClearance else 0.dp, bleed.left)
            val primaryRightClearance = maxOf(if (anchorLeft) 0.dp else primaryClearance, bleed.right)
            val detailLeftClearance = if (anchorLeft) detailClearance else 0.dp
            val detailRightClearance = if (anchorLeft) 0.dp else detailClearance
            PaneSlot(primarySlot, density) { paneModifier ->
                if (hasDetail && !showDetail) {
                    detailContent(paneModifier, primaryLeftClearance, primaryRightClearance)
                } else {
                    CompositionLocalProvider(LocalPaneEdgeBleed provides bleed) {
                        primaryContent(paneModifier, primaryLeftClearance, primaryRightClearance)
                    }
                }
            }
            if (twoPane && (showDetail || !hasDetail)) {
                layout.detail?.let { detail ->
                    PaneSlot(detail, density) { paneModifier ->
                        detailContent(paneModifier, detailLeftClearance, detailRightClearance)
                    }
                }
            }
        }
        if (controls != null && navigationVisible) {
            LargeFloatingNavigation(
                selectedTarget = selectedTarget,
                account = account,
                action = action,
                onTargetSelected = onTargetSelected,
                onOpenAccounts = onOpenAccounts,
                isCompactWide = isCompactWide,
                tabCaret = tabCaret?.takeIf { isCompactWide },
                // The stack is anchored physically, so its placement never reverses with direction.
                modifier = Modifier.align(AbsoluteAlignment.TopLeft)
                    .absoluteOffset(controls.left.dp, controls.top.dp),
            )
        }
    }
}

internal fun primaryPaneBounds(layout: LargePaneLayout, twoPane: Boolean): Rect =
    if (layout.mode == LargeLayoutMode.Expanded && !twoPane && layout.detail != null &&
        layout.safeRegions.size == 1
    ) {
        layout.safeRegions.single()
    } else {
        layout.primary
    }

@Composable
private fun BoxScope.PaneSlot(
    bounds: Rect,
    density: Density,
    content: @Composable (Modifier) -> Unit,
) {
    val left = with(density) { bounds.left.toDp() }
    val top = with(density) { bounds.top.toDp() }
    val width = with(density) { bounds.width.toDp() }
    val height = with(density) { bounds.height.toDp() }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        content(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(left, top).width(width).height(height))
    }
}

internal const val LARGE_SCREEN_SHELL_TAG = "large_screen_shell"
internal const val LARGE_CONTENT_REGION_TAG = "large_content_region"
