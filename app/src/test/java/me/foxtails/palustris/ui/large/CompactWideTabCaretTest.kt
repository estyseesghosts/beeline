package me.foxtails.palustris.ui.large

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.ui.shell.AppShellFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Compact-wide contextual tab caret: presentation, shared state, and stable bottom anchor. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w900dp-h900dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactWideTabCaretTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val width = mutableStateOf(445f)
    private val height = mutableStateOf(704f)
    private val density get() = compose.activity.resources.displayMetrics.density

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

    private fun showCompactWide() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                Box(Modifier.width(width.value.dp).height(height.value.dp)) {
                    AppShellFixtures.app(account = AppShellFixtures.account())
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun compactWideHomeHidesInlineCaretAndTogglesChipsContextually() {
        showCompactWide()
        compose.onNodeWithTag(CompactWideTabCaretTag, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("home_timeline_visibility", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Hide chips").assertIsDisplayed()

        compose.onNodeWithTag(CompactWideTabCaretTag, useUnmergedTree = true).performClick()
        compose.waitForIdle()

        // The same feature-owned visibility drives both presentations: chips collapse.
        compose.onNodeWithContentDescription("Show chips").assertIsDisplayed()
        compose.onNodeWithTag("home_timeline_tabs", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(CompactWideTabCaretTag, useUnmergedTree = true).assertIsDisplayed()

        compose.onNodeWithTag(CompactWideTabCaretTag, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Hide chips").assertIsDisplayed()
        compose.onNodeWithTag("home_timeline_tabs", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun compactWideWithoutTabsHidesCaretWithoutMovingNavigation() {
        showCompactWide()
        val homeCapsule = bounds(LargeNavigationCapsuleTag)
        val homeStack = bounds(LargeFloatingNavigationTag)
        compose.onNodeWithTag(CompactWideTabCaretTag, useUnmergedTree = true).assertIsDisplayed()

        compose.onNodeWithContentDescription("Direct messages").performClick()
        compose.waitForIdle()

        // Direct Messages exposes no tab chips: no contextual caret and no inline caret slot.
        compose.onNodeWithTag(CompactWideTabCaretTag, useUnmergedTree = true).assertDoesNotExist()
        // The floating capsule and the full stack stay exactly where Home placed them.
        assertEquals(homeCapsule, bounds(LargeNavigationCapsuleTag))
        assertEquals(homeStack, bounds(LargeFloatingNavigationTag))
        // The DM action keeps the same composer slot with its own icon.
        val dmAction = compose.onNodeWithContentDescription("New conversation")
            .fetchSemanticsNode().boundsInRoot
        val homeAction = homeStack.let {
            Rect(
                left = it.left,
                top = homeCapsule.bottom + 8f * density,
                right = it.right,
                bottom = homeCapsule.bottom + (8f + 56f) * density,
            )
        }
        assertEquals(homeAction.left, dmAction.left, 1f)
        assertEquals(homeAction.top, dmAction.top, 1f)
        assertEquals(homeAction.right, dmAction.right, 1f)
        assertEquals(homeAction.bottom, dmAction.bottom, 1f)
    }

    @Test fun compactWideStackBottomAnchorsWhileTabletStaysCentered() {
        showCompactWide()
        val compactStack = bounds(LargeFloatingNavigationTag)
        // Bottom-anchored: the stack bottom meets the window bottom safe edge.
        assertEquals(height.value * density, compactStack.bottom, 2f)
        assertEquals((height.value - 424f) * density, compactStack.top, 2f)

        compose.runOnIdle { width.value = 900f; height.value = 900f }
        compose.waitForIdle()
        val tabletStack = bounds(LargeFloatingNavigationTag)
        assertEquals(360f * density, tabletStack.height, 1f)
        // Centered: equal space above and below, not bottom-anchored.
        assertEquals(tabletStack.top, 900f * density - tabletStack.bottom, 2f)
        assertTrue(tabletStack.bottom < 900f * density - 1f)
    }

    @Test fun compactWideChipsStopBeforeTheContextualCaret() {
        showCompactWide()
        val caret = bounds(CompactWideTabCaretTag)
        val chips = compose.onNodeWithTag("home_timeline_tabs", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "tab chips must not render underneath the contextual caret",
            chips.right <= caret.left + 1f,
        )
    }
}
