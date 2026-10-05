package me.foxtails.palustris.ui.large

import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min

internal enum class LargeLayoutMode {
    Compact,
    Single,
    Expanded,
}

internal data class LargeFoldingFeature(
    val bounds: Rect,
    val isVertical: Boolean,
    val isSeparating: Boolean,
    val isOccluding: Boolean,
)

/** The physical window-space origin of the pane content. */
internal data class LargeContentOriginPx(val x: Float, val y: Float)

/** Resolves the current content origin after the system insets. */
internal fun largeContentOriginPx(
    leftInsetPx: Float,
    topInsetPx: Float,
): LargeContentOriginPx = LargeContentOriginPx(x = leftInsetPx, y = topInsetPx)

/** Converts window-coordinate folding bounds to the pane-content coordinate space. */
internal fun largeFeatureBoundsInContentPx(
    windowBounds: Rect,
    contentOrigin: LargeContentOriginPx,
): Rect = windowBounds.translate(-contentOrigin.x, -contentOrigin.y)

internal data class LargePaneLayout(
    val mode: LargeLayoutMode,
    val safeRegions: List<Rect>,
    val primary: Rect,
    val detail: Rect?,
)

internal fun largeLayoutMode(windowWidthDp: Float): LargeLayoutMode = when {
    windowWidthDp < 600f -> LargeLayoutMode.Compact
    windowWidthDp < 840f -> LargeLayoutMode.Single
    else -> LargeLayoutMode.Expanded
}

/** Calculates pane bounds after the outer margins and any separating feature are removed. */
internal fun calculateLargePaneLayout(
    windowWidthDp: Float,
    contentWidthDp: Float,
    contentHeightDp: Float,
    density: Float,
    foldingFeatures: List<LargeFoldingFeature> = emptyList(),
    outerMarginDp: Float = 16f,
    dividerDp: Float = 1f,
    minimumListDp: Float = 320f,
    minimumDetailDp: Float = 360f,
): LargePaneLayout {
    val mode = largeLayoutMode(windowWidthDp)
    val scale = density.coerceAtLeast(0.1f)
    val width = contentWidthDp * scale
    val height = contentHeightDp * scale
    val margin = outerMarginDp * scale
    val divider = dividerDp * scale
    val minimumList = minimumListDp * scale
    val minimumDetail = minimumDetailDp * scale
    val content = Rect(margin, 0f, max(margin, width - margin), max(0f, height))

    val structuralFeatures = foldingFeatures.filter {
        (it.isSeparating || it.isOccluding) && it.bounds.overlaps(content)
    }
    val verticalFeatures = structuralFeatures.filter { it.isVertical }
    val safeRegions = verticalFeatures.fold(listOf(content)) { regions, feature ->
        regions.flatMap { region ->
            if (!feature.bounds.overlaps(region)) {
                listOf(region)
            } else {
                listOfNotNull(
                    Rect(region.left, region.top, min(region.right, feature.bounds.left), region.bottom)
                        .takeIf { it.width > 0f },
                    Rect(max(region.left, feature.bounds.right), region.top, region.right, region.bottom)
                        .takeIf { it.width > 0f },
                )
            }
        }
    }

    val horizontalFeatures = structuralFeatures.filter { !it.isVertical }
    val postureRegions = horizontalFeatures.fold(safeRegions) { regions, feature ->
        regions.flatMap { region ->
            if (!feature.bounds.overlaps(region)) {
                listOf(region)
            } else {
                listOfNotNull(
                    Rect(region.left, region.top, region.right, min(region.bottom, feature.bounds.top))
                        .takeIf { it.height > 0f },
                    Rect(region.left, max(region.top, feature.bounds.bottom), region.right, region.bottom)
                        .takeIf { it.height > 0f },
                )
            }
        }
    }

    val largestRegion = postureRegions.maxByOrNull { it.width * it.height } ?: content
    if (mode != LargeLayoutMode.Expanded || horizontalFeatures.isNotEmpty()) {
        return LargePaneLayout(mode, postureRegions, largestRegion, null)
    }

    if (postureRegions.size >= 2) {
        val left = postureRegions.first()
        val right = postureRegions.last()
        if (left.width >= minimumList && right.width >= minimumDetail) {
            return LargePaneLayout(mode, safeRegions, left, right)
        }
    }

    val available = largestRegion.width
    val preferredList = available * 0.45f
    val listWidth = preferredList.coerceIn(
        minimumList,
        (available - minimumDetail - divider).coerceAtLeast(minimumList),
    )
    val canSplit = available >= minimumList + divider + minimumDetail
    if (!canSplit) return LargePaneLayout(mode, safeRegions, largestRegion, null)

    val primary = Rect(largestRegion.left, largestRegion.top, largestRegion.left + listWidth, largestRegion.bottom)
    val detail = Rect(primary.right + divider, largestRegion.top, largestRegion.right, largestRegion.bottom)
    return LargePaneLayout(mode, safeRegions, primary, detail)
}

/** The minimum useful content width that must remain after placing the vertical navigation capsule. */
internal const val MinimumUsefulContentWidthDp = 360f

/** The gap between the floating navigation stack and the physical safe-region edge. */
internal const val NavigationPlacementGapDp = 8f

/** The shared vertical capsule width. The shared presentation owns this value. */
internal const val NavigationCapsuleWidthDp = 56f

/**
 * The shared vertical capsule height.
 *
 * Six 48 dp targets plus the shared capsule vertical padding of 4 dp on each side.
 */
internal const val NavigationCapsuleHeightDp = 296f

/** The shared contextual action size. `ContextualNavigationActionButton` owns this value. */
internal const val NavigationActionSizeDp = 56f

/** The width the vertical navigation stack reserves, including its physical placement gap. */
internal val NavigationCapsuleTotalWidthDp = NavigationCapsuleWidthDp + NavigationPlacementGapDp

/** The height of the vertical navigation stack: capsule, gap, and contextual action. */
internal val NavigationCapsuleTotalHeightDp =
    NavigationCapsuleHeightDp + NavigationPlacementGapDp + NavigationActionSizeDp

/** The result of the navigation fit policy. */
internal data class NavigationFit(
    val useVerticalNavigation: Boolean,
    val safeRegion: Rect?,
)

/**
 * Calculates whether the vertical navigation stack can fit in a safe region.
 *
 * A safe region must hold the complete stack and leave the approved useful content width. The fit
 * reads permanent window geometry only, so IME height never changes permanent presentation. Pane
 * and detail selection stay independent of this policy.
 *
 * `gestureInsets` carries the mandatory system gesture insets. The shell supplies them because a
 * nonmandatory back-gesture strip may still hold visible content. Insets on the same edge merge by
 * their larger value, so overlapping insets never remove the same space twice.
 */
internal fun calculateNavigationFit(
    windowWidthDp: Float,
    windowHeightDp: Float,
    systemBarInsets: Rect,
    gestureInsets: Rect = Rect(0f, 0f, 0f, 0f),
    cutoutBounds: Rect? = null,
    foldingFeatures: List<LargeFoldingFeature> = emptyList(),
    anchorLeft: Boolean = false,
): NavigationFit {
    // Use only the permanent window geometry. IME insets never affect the fit.
    val content = Rect(
        max(systemBarInsets.left, gestureInsets.left),
        max(systemBarInsets.top, gestureInsets.top),
        windowWidthDp - max(systemBarInsets.right, gestureInsets.right),
        windowHeightDp - max(systemBarInsets.bottom, gestureInsets.bottom),
    )

    val cutoutRegions = if (cutoutBounds != null && cutoutBounds.overlaps(content)) {
        listOfNotNull(
            Rect(content.left, content.top, min(content.right, cutoutBounds.left), content.bottom)
                .takeIf { it.width > 0f },
            Rect(max(content.left, cutoutBounds.right), content.top, content.right, content.bottom)
                .takeIf { it.width > 0f },
        )
    } else {
        listOf(content)
    }

    val structuralFeatures = foldingFeatures.filter { it.isSeparating || it.isOccluding }
    val safeRegions = structuralFeatures.fold(cutoutRegions) { regions, feature ->
        regions.flatMap { region ->
            when {
                !feature.bounds.overlaps(region) -> listOf(region)
                feature.isVertical -> listOfNotNull(
                    Rect(region.left, region.top, min(region.right, feature.bounds.left), region.bottom)
                        .takeIf { it.width > 0f },
                    Rect(max(region.left, feature.bounds.right), region.top, region.right, region.bottom)
                        .takeIf { it.width > 0f },
                )
                else -> listOfNotNull(
                    Rect(region.left, region.top, region.right, min(region.bottom, feature.bounds.top))
                        .takeIf { it.height > 0f },
                    Rect(region.left, max(region.top, feature.bounds.bottom), region.right, region.bottom)
                        .takeIf { it.height > 0f },
                )
            }
        }
    }

    val fittingRegions = safeRegions.filter { region ->
        region.width >= NavigationCapsuleTotalWidthDp &&
            region.height >= NavigationCapsuleTotalHeightDp &&
            (region.width - NavigationCapsuleTotalWidthDp) >= MinimumUsefulContentWidthDp
    }
    // The anchor selects the fitting safe region nearest its own physical edge.
    val fittingRegion = if (anchorLeft) fittingRegions.minByOrNull { it.left }
    else fittingRegions.maxByOrNull { it.right }

    return NavigationFit(
        useVerticalNavigation = fittingRegion != null,
        safeRegion = fittingRegion,
    )
}

/** Converts a window-space pixel rect into density-independent dp. */
internal fun Rect.toDpRect(density: Float): Rect {
    val scale = density.coerceAtLeast(0.1f)
    return Rect(left / scale, top / scale, right / scale, bottom / scale)
}
