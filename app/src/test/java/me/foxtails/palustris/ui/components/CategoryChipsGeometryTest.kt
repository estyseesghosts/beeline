package me.foxtails.palustris.ui.components

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.ui.PalustrisTheme
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.PalustrisMotionScheme
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
                    DestinationChipRow(
                        entries = listOf(
                            FilterChipEntry(
                                label = "Posts",
                                onClick = {},
                                testTag = "category-chip",
                            ),
                        ),
                        rowContentDescription = "categories",
                        listState = rememberLazyListState(),
                        visible = true,
                        onToggleVisibility = {},
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
        // The shared renderer passes this bubble shape to FilterChip. Keep the check beside its composition test.
        assertEquals(RoundedCornerShape(24.dp), BeelineBubbleShape)
    }

    @Test
    fun caretStaysVisibleAndSelectionAndScrollSurviveCollapse() {
        val visible = mutableStateOf(true)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    DestinationChipRow(
                        entries = (0..8).map { index ->
                            FilterChipEntry(
                                label = "Category $index",
                                selected = index == 8,
                                onClick = {},
                                testTag = "category-chip-$index",
                                key = "category-$index",
                            )
                        },
                        rowContentDescription = "categories",
                        listState = rememberLazyListState(),
                        visible = visible.value,
                        onToggleVisibility = { visible.value = !visible.value },
                        rowTestTag = "category-row",
                        visibilityToggleTestTag = "category-visibility",
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("categories")
            .performScrollToNode(hasText("Category 8"))
        compose.onNodeWithTag("category-chip-8").assertIsSelected()
        val expandedCaret = compose.onNodeWithTag("category-visibility")
            .fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Hide chips").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("category-chip-8").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show chips").assertIsDisplayed().assertHasClickAction()
        val collapsedCaret = compose.onNodeWithTag("category-visibility")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(expandedCaret.left, collapsedCaret.left, 0.01f)
        assertEquals(expandedCaret.top, collapsedCaret.top, 0.01f)
        compose.onNodeWithContentDescription("Show chips").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("category-chip-8").assertIsDisplayed().assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-420dpi")
    fun compactNarrowKeepsCaretAndChipTargetsVisible() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    DestinationChipRow(
                        entries = listOf(FilterChipEntry(label = "Home", onClick = {}, testTag = "compact-chip")),
                        rowContentDescription = "categories",
                        listState = rememberLazyListState(),
                        visible = true,
                        onToggleVisibility = {},
                        modifier = Modifier.fillMaxWidth(),
                        visibilityToggleTestTag = "compact-caret",
                    )
                }
            }
        }

        compose.onNodeWithTag("compact-chip").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("compact-caret").assertIsDisplayed().assertHasClickAction()
        assertEquals(
            48.dp,
            with(compose.density) {
                compose.onNodeWithTag("compact-caret").fetchSemanticsNode().boundsInRoot.width.toDp()
            },
        )
    }

    @Test
    @Config(qualifiers = "w445dp-h704dp-420dpi")
    fun outerScreenKeepsTheCaretInlineInLtrAndRtl() {
        for (direction in LayoutDirection.entries) {
            compose.activity.runOnUiThread {
                compose.activity.setContent {
                    PalustrisTheme {
                        CompositionLocalProvider(LocalLayoutDirection provides direction) {
                            DestinationChipRow(
                                entries = listOf(FilterChipEntry(label = "Home", onClick = {})),
                                rowContentDescription = "categories",
                                listState = rememberLazyListState(),
                                visible = true,
                                onToggleVisibility = {},
                                modifier = Modifier.fillMaxWidth(),
                                rowTestTag = "outer-chip-row",
                                visibilityToggleTestTag = "outer-chip-caret",
                            )
                        }
                    }
                }
            }
            compose.waitForIdle()
            val row = compose.onNodeWithTag("outer-chip-row").fetchSemanticsNode().boundsInRoot
            val caret = compose.onNodeWithTag("outer-chip-caret").assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertEquals(48.dp, with(compose.density) { caret.width.toDp() })
            if (direction == LayoutDirection.Ltr) {
                assertEquals(true, caret.center.x < row.center.x)
            } else {
                assertEquals(true, caret.center.x > row.center.x)
            }
            compose.onNodeWithContentDescription("Hide chips").assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "w900dp-h900dp-420dpi")
    fun tabletCaretAndChipCollapseUseReducedMotion() {
        val visible = mutableStateOf(true)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    CompositionLocalProvider(
                        LocalPalustrisMotionScheme provides PalustrisMotionScheme.standard(reducedMotion = true),
                    ) {
                        DestinationChipRow(
                            entries = listOf(
                                FilterChipEntry(label = "Posts", selected = true, onClick = {}, testTag = "tablet-chip"),
                            ),
                            rowContentDescription = "categories",
                            listState = rememberLazyListState(),
                            visible = visible.value,
                            onToggleVisibility = { visible.value = !visible.value },
                            modifier = Modifier.fillMaxWidth(),
                            rowTestTag = "tablet-chip-row",
                            visibilityToggleTestTag = "tablet-chip-caret",
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tablet-chip-caret").assertIsDisplayed().performClick()
        compose.onNodeWithTag("tablet-chip").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show chips").assertIsDisplayed()
        compose.onNodeWithTag("tablet-chip-caret").performClick()
        compose.onNodeWithTag("tablet-chip").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun hiddenCaretKeepsChipStateWithoutAnInlineControl() {
        val visible = mutableStateOf(true)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    DestinationChipRow(
                        entries = listOf(
                            FilterChipEntry(label = "Posts", selected = true, onClick = {}, testTag = "hidden-chip"),
                        ),
                        rowContentDescription = "categories",
                        listState = rememberLazyListState(),
                        visible = visible.value,
                        onToggleVisibility = { visible.value = !visible.value },
                        modifier = Modifier.fillMaxWidth(),
                        rowTestTag = "hidden-chip-row",
                        visibilityToggleTestTag = "hidden-chip-caret",
                        caretPresentation = ChipCaretPresentation.Hidden,
                    )
                }
            }
        }
        compose.waitForIdle()

        // The inline control is absent, but chip state still flows through the shared plumbing.
        compose.onNodeWithTag("hidden-chip-caret").assertDoesNotExist()
        compose.onNodeWithTag("hidden-chip").assertIsDisplayed().assertIsSelected()
        compose.runOnIdle { visible.value = false }
        compose.onNodeWithTag("hidden-chip").assertDoesNotExist()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithTag("hidden-chip").assertIsDisplayed()
    }
}
