package me.foxtails.palustris.ui.components

import androidx.activity.compose.setContent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.ui.PalustrisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryChipsGeometryTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun chipUsesSharedBubbleGeometryAndPreservesClickSemantics() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    FilterChipRow(
                        entries = listOf(
                            FilterChipEntry(
                                label = "Posts",
                                onClick = {},
                                testTag = "category-chip",
                            ),
                        ),
                        rowContentDescription = "categories",
                    )
                }
            }
        }

        val node = compose.onNodeWithTag("category-chip")
            .assertIsDisplayed()
            .assertHasClickAction()
            .fetchSemanticsNode()
        val expectedHeight = with(compose.density) { BeelineBubbleMinHeight.toPx() }

        assertEquals(expectedHeight, node.boundsInRoot.height, 0.01f)
        assertEquals(48.dp, BeelineBubbleMinHeight)
        assertEquals(24.dp, BeelineBubbleRadius)
        // CategoryChips.kt:101 passes this shared shape to FilterChip. Keep this assertion with the composition check.
        assertEquals(RoundedCornerShape(24.dp), BeelineBubbleShape)
    }
}
