package me.foxtails.palustris.ui.large

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Physical distance from the primary pane's content edge to the display edge, per side.
 *
 * Only a side that touches the outer margin bleeds. The shell widens the pane by this amount so a
 * chip row's whole ancestor chain reaches the display edge, which hit testing requires. Content
 * keeps the margin through the same amount of clearance or padding. Physical sides never follow
 * layout direction.
 *
 * [top] is the status bar height. Only a destination that opts in widens its pane upward, so its
 * own banner can underlap the status bar. Every other destination keeps `top` at zero.
 */
internal data class PaneEdgeBleed(val left: Dp = 0.dp, val right: Dp = 0.dp, val top: Dp = 0.dp) {
    val isZero: Boolean get() = left == 0.dp && right == 0.dp && top == 0.dp
}

internal val LocalPaneEdgeBleed = compositionLocalOf { PaneEdgeBleed() }

/** Which pane edges may bleed to the display edge. */
internal data class PaneBleedSides(val horizontal: Boolean, val top: Boolean)

/**
 * Measures how far each allowed pane edge sits from the display edge.
 *
 * A side bleeds only where the primary pane touches the outer margin. The status bar is the only
 * inset above the pane, so a top bleed is exactly the pane's window top.
 */
internal fun paneEdgeBleed(
    primary: Rect,
    windowPane: Rect,
    regionWidthPx: Float,
    windowWidthDp: Float,
    density: Float,
    sides: PaneBleedSides,
): PaneEdgeBleed {
    val marginPx = LARGE_OUTER_MARGIN_DP * density
    return PaneEdgeBleed(
        left = if (sides.horizontal && primary.left <= marginPx + 1f) windowPane.left.dp else 0.dp,
        right = if (sides.horizontal && primary.right >= regionWidthPx - marginPx - 1f) {
            (windowWidthDp - windowPane.right).coerceAtLeast(0f).dp
        } else {
            0.dp
        },
        top = if (sides.top && primary.top <= 1f) windowPane.top.dp else 0.dp,
    )
}
