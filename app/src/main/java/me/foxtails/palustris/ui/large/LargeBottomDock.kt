package me.foxtails.palustris.ui.large

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val LargeBottomDockClearance = 88.dp
internal val LargeSearchDockClearance = 144.dp
internal val LargeBottomDockEdgeGap = 12.dp

/**
 * Physical resting insets for content inside a [LargeBottomDock]. They are not layout-direction
 * relative. A chip row rests clear of them but still scrolls to the display edge beneath them.
 */
internal data class LargeDockEdgeInsets(val left: Dp = 0.dp, val right: Dp = 0.dp)

internal val LocalLargeDockEdgeInsets = compositionLocalOf { LargeDockEdgeInsets() }

/**
 * Bottom-anchored dock that spans its container width. Physical [leftClearance] and [rightClearance]
 * (floating navigation) plus the edge gap become resting insets, not a narrower viewport.
 */
@Composable
internal fun LargeBottomDock(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leftClearance: Dp = 0.dp,
    rightClearance: Dp = 0.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = LargeBottomDockEdgeGap),
        contentAlignment = Alignment.CenterStart,
    ) {
        CompositionLocalProvider(
            LocalLargeDockEdgeInsets provides LargeDockEdgeInsets(
                left = leftClearance + LargeBottomDockEdgeGap,
                right = rightClearance + LargeBottomDockEdgeGap,
            ),
            content = content,
        )
    }
}
