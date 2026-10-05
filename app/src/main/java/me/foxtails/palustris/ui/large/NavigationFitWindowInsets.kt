@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class,
)

package me.foxtails.palustris.ui.large

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Reads the live window inputs that the navigation fit policy needs.
 *
 * The system bar and display cutout insets merge with the mandatory system gesture insets. Every
 * read uses the physical left-to-right direction, so a forced layout direction cannot move the
 * calculated safe regions. Only mandatory gesture insets are excluded: a nonmandatory
 * back-gesture strip may still hold visible content, so placement beside one stays allowed.
 *
 * This composable supplies geometry only. The fit decision stays with `calculateNavigationFit`,
 * and IME height is never read here.
 */
@Composable
internal fun rememberNavigationFit(
    windowWidthDp: Float,
    windowHeightDp: Float,
    anchorLeft: Boolean,
): NavigationFit {
    val density = LocalDensity.current
    val posture = currentWindowAdaptiveInfoV2().windowPosture
    val permanentInsets = WindowInsets.systemBars.physicalRect(density)
        .mergedWith(WindowInsets.displayCutout.physicalRect(density)).toDpRect(density.density)
    val gestureInsets = WindowInsets.mandatorySystemGestures.physicalRect(density)
        .toDpRect(density.density)
    val features = posture.hingeList.map { hinge ->
        LargeFoldingFeature(
            bounds = Rect(
                hinge.bounds.left.toFloat(), hinge.bounds.top.toFloat(),
                hinge.bounds.right.toFloat(), hinge.bounds.bottom.toFloat(),
            ).toDpRect(density.density),
            isVertical = hinge.isVertical,
            isSeparating = hinge.isSeparating,
            isOccluding = hinge.isOccluding,
        )
    }
    return remember(
        windowWidthDp, windowHeightDp, anchorLeft, permanentInsets, gestureInsets, features,
    ) {
        calculateNavigationFit(
            windowWidthDp = windowWidthDp,
            windowHeightDp = windowHeightDp,
            systemBarInsets = permanentInsets,
            gestureInsets = gestureInsets,
            foldingFeatures = features,
            anchorLeft = anchorLeft,
        )
    }
}

/**
 * Reads one inset set physically, so a forced direction cannot swap its sides.
 *
 * The read uses `LayoutDirection.Ltr` for every edge. Only left and right depend on that argument,
 * and the safe regions here stay anchored to the physical screen edges.
 */
@Composable
private fun WindowInsets.physicalRect(density: Density): Rect {
    val leftValue = getLeft(density, LayoutDirection.Ltr)
    val rightValue = getRight(density, LayoutDirection.Ltr)
    return Rect(
        left = leftValue.toFloat(),
        top = getTop(density).toFloat(),
        right = rightValue.toFloat(),
        bottom = getBottom(density).toFloat(),
    )
}

/** Merges two physical inset sets edge by edge, keeping the larger value on each edge. */
private fun Rect.mergedWith(other: Rect): Rect = Rect(
    left = maxOf(left, other.left),
    top = maxOf(top, other.top),
    right = maxOf(right, other.right),
    bottom = maxOf(bottom, other.bottom),
)
