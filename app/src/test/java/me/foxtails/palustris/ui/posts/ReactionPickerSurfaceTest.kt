package me.foxtails.palustris.ui.posts

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class ReactionPickerSurfaceTest {
    private val density = 3f
    private val width = 1080
    private val height = 2340
    private val middle = Rect(400f, 1200f, 460f, 1260f)

    private fun surface(anchor: Rect, fontScale: Float = 1f, expanded: Boolean = false) =
        reactionPickerSurface(anchor, width, height, density, fontScale, expanded)

    @Test
    fun missingOrOffscreenAnchorUsesTheSheetForBothSizes() {
        listOf(
            Rect.Zero,
            Rect(-100f, 500f, -40f, 560f),
            Rect(400f, height + 10f, 460f, height + 70f),
        ).forEach { anchor ->
            assertEquals(ReactionPickerSurface.Sheet, surface(anchor, expanded = false))
            assertEquals(ReactionPickerSurface.Sheet, surface(anchor, expanded = true))
        }
    }

    @Test
    fun compactStaysAnchoredAtAnyFontScale() {
        assertEquals(ReactionPickerSurface.Anchored, surface(middle, fontScale = 2f, expanded = false))
    }

    @Test
    fun expandedStaysAnchoredWithRoomAndNormalFont() {
        assertEquals(ReactionPickerSurface.Anchored, surface(middle, fontScale = 1f, expanded = true))
    }

    @Test
    fun expandedMovesToTheSheetAtLargeFontScale() {
        assertEquals(ReactionPickerSurface.Sheet, surface(middle, fontScale = 1.5f, expanded = true))
    }

    @Test
    fun expandedMovesToTheSheetWhenNeitherSideHasRoom() {
        // About 100 dp above and below the anchor: less than the minimum expanded height.
        val cramped = Rect(400f, 300f, 460f, 360f)
        val shortWindow = reactionPickerSurface(cramped, width, 660, density, 1f, expanded = true)

        assertEquals(ReactionPickerSurface.Sheet, shortWindow)
    }

    @Test
    fun availableHeightUsesTheRoomierSide() {
        val anchor = Rect(0f, 300f, 60f, 360f)

        assertEquals((height - 360) / 3 - 16, availableHeightDp(anchor, height, density))
    }
}
