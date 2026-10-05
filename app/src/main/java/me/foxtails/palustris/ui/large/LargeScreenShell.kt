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
import androidx.compose.ui.Modifier
import androidx.compose.ui.AbsoluteAlignment
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
 */
internal fun floatingNavigationBounds(fit: NavigationFit, anchorLeft: Boolean): Rect? {
    if (!fit.useVerticalNavigation) return null
    val region = fit.safeRegion ?: return null
    if (region.width < NavigationCapsuleTotalWidthDp || region.height < NavigationCapsuleTotalHeightDp) return null
    val width = NavigationCapsuleWidthDp
    val height = NavigationCapsuleTotalHeightDp
    val left = if (anchorLeft) region.left + NavigationPlacementGapDp
    else region.right - NavigationPlacementGapDp - width
    val top = region.top + (region.height - height) / 2f
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
    val controls = if (navigationVisible) floatingNavigationBounds(navigationFit, anchorLeft) else null
    Box(modifier.fillMaxSize().testTag(LargeScreenShellTag)) {
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(bars).testTag(LargeContentRegionTag),
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
            val primaryClearance = navigationPaneClearance(windowPane, controls, anchorLeft).dp
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
            val primaryLeftClearance = if (anchorLeft) primaryClearance else 0.dp
            val primaryRightClearance = if (anchorLeft) 0.dp else primaryClearance
            val detailLeftClearance = if (anchorLeft) detailClearance else 0.dp
            val detailRightClearance = if (anchorLeft) 0.dp else detailClearance
            PaneSlot(primary, density) { paneModifier ->
                if (hasDetail && !showDetail) {
                    detailContent(paneModifier, primaryLeftClearance, primaryRightClearance)
                } else {
                    primaryContent(paneModifier, primaryLeftClearance, primaryRightClearance)
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
        if (controls != null) {
            LargeFloatingNavigation(
                selectedTarget = selectedTarget,
                account = account,
                action = action,
                onTargetSelected = onTargetSelected,
                onOpenAccounts = onOpenAccounts,
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

internal const val LargeScreenShellTag = "large_screen_shell"
internal const val LargeContentRegionTag = "large_content_region"
