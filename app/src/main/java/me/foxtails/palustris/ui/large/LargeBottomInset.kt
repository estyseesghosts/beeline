package me.foxtails.palustris.ui.large

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The bottom system-bar inset that a large pane draws under. [LargeScreenShell] does not pad its content
 * region with this inset, so feeds and the blur band reach the physical bottom edge. A pane keeps its controls
 * above it ([LargeBottomDock] pads its content) and adds it to the end of its scroll content. It is zero while a
 * compact navigation bar sits at the bottom, because that bar then owns the inset.
 */
internal val LocalLargeBottomInset = compositionLocalOf { 0.dp }
