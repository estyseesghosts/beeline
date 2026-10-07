package me.foxtails.palustris.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.LayoutDirection

/**
 * Returns the physical edge where the chip row's end chip rests after scrolling to that end.
 *
 * The chip viewport reaches the display edge, so a clearance assertion measures the resting chip, not the row.
 * The row is scrolled back to its start afterward. [physicalLeft] selects the physically left resting chip: the first chip in LTR, the last in RTL.
 */
internal fun SemanticsNodeInteraction.restingChipEdge(
    compose: ComposeTestRule,
    direction: LayoutDirection,
    physicalLeft: Boolean,
): Float {
    val count = fetchSemanticsNode().config.getOrNull(SemanticsProperties.CollectionInfo)
        ?.let { maxOf(it.columnCount, it.rowCount) } ?: fetchSemanticsNode().children.size
    val toFirst = (direction == LayoutDirection.Ltr) == physicalLeft
    performScrollToIndex(if (toFirst) 0 else count - 1)
    compose.waitForIdle()
    val row = fetchSemanticsNode().boundsInRoot
    val chips = fetchSemanticsNode().children.map { it.boundsInRoot }
        .filter { it.left >= row.left - 1f && it.right <= row.right + 1f }
    val edge = if (physicalLeft) chips.minOf { it.left } else chips.maxOf { it.right }
    // Callers keep asserting against the start position, so restore it.
    performScrollToIndex(0)
    compose.waitForIdle()
    return edge
}
