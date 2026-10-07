package me.foxtails.palustris.ui.large

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Physical distance from the primary pane's content edge to the display edge, per side.
 *
 * Only a side that touches the outer margin bleeds. The shell widens the pane by this amount so a
 * chip row's whole ancestor chain reaches the display edge, which hit testing requires. Content
 * keeps the margin through the same amount of clearance or padding. Physical sides never follow
 * layout direction.
 */
internal data class PaneEdgeBleed(val left: Dp = 0.dp, val right: Dp = 0.dp) {
    val isZero: Boolean get() = left == 0.dp && right == 0.dp
}

internal val LocalPaneEdgeBleed = compositionLocalOf { PaneEdgeBleed() }
