package me.foxtails.palustris.ui.large

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.ui.layout.compactContextualCaretFits
import me.foxtails.palustris.ui.shell.AppShellFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Compact-narrow tab caret: contextual beside the pill when it fits, inline otherwise. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w384dp-h800dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactNarrowTabCaretTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun showApp() {
        compose.activity.runOnUiThread {
            compose.activity.setContent { AppShellFixtures.app(account = AppShellFixtures.account()) }
        }
        compose.waitForIdle()
    }

    @Test fun fitPolicyNeedsRoomForCapsuleActionCaretAndGaps() {
        // 212 + 56 + 56 + 2 * 8 = 340 dp of bar, plus 16 dp of padding on each side.
        assertFalse(compactContextualCaretFits(360.dp))
        assertFalse(compactContextualCaretFits(371.dp))
        assertTrue(compactContextualCaretFits(372.dp))
        assertTrue(compactContextualCaretFits(384.dp))
        assertTrue(compactContextualCaretFits(599.dp))
    }

    @Test fun roomyCompactNarrowPlacesTheContextualCaretLeftOfThePillAndTogglesChips() {
        showApp()
        val caret = compose.onNodeWithTag(COMPACT_WIDE_TAB_CARET_TAG, useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val pill = compose.onNodeWithContentDescription("Home").fetchSemanticsNode().boundsInRoot
        assertTrue(caret.right <= pill.left)
        assertEquals(56f * compose.activity.resources.displayMetrics.density, caret.width, 1f)
        compose.onNodeWithTag("home_timeline_visibility", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithTag(COMPACT_WIDE_TAB_CARET_TAG, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Show chips").assertIsDisplayed()
        compose.onNodeWithTag("home_timeline_tabs", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-420dpi")
    fun narrowestCompactKeepsTheInlineCaret() {
        showApp()
        compose.onNodeWithTag(COMPACT_WIDE_TAB_CARET_TAG, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("home_timeline_visibility", useUnmergedTree = true).assertIsDisplayed()
    }
}
