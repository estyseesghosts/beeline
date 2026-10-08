package me.foxtails.palustris.ui.search

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.ui.PalustrisTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchEntryStatesTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private var query by mutableStateOf("")
    private val submitted = mutableListOf<String>()

    private fun show(fontScale: Float = 1f) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                        SearchScreen(
                            sharedQuery = query,
                            sharedTab = 0,
                            onSharedQueryChange = { query = it },
                            onSearchAccounts = { submitted += it },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun field() = compose.onNodeWithContentDescription("Search field")

    private fun inMode(mode: SearchEntryMode) = SemanticsMatcher.expectValue(SearchEntryModeKey, mode)

    private val density get() = compose.activity.resources.displayMetrics.density

    @Test
    fun modeFollowsFocusAndQuery() {
        assertEquals(SearchEntryMode.Idle, searchEntryMode("", focused = false))
        assertEquals(SearchEntryMode.Idle, searchEntryMode("   ", focused = false))
        assertEquals(SearchEntryMode.Entry, searchEntryMode("", focused = true))
        assertEquals(SearchEntryMode.Entry, searchEntryMode("alice", focused = true))
        assertEquals(SearchEntryMode.Results, searchEntryMode("alice", focused = false))
    }

    @Test
    fun idleBubbleExpandsForEntryAndCollapsesToAResultsBubbleThatKeepsTheQuery() {
        show()
        field().assert(inMode(SearchEntryMode.Idle))
        val idleWidth = field().fetchSemanticsNode().boundsInRoot.width

        field().performClick()
        field().assert(inMode(SearchEntryMode.Entry))
        field().performTextInput("alice")
        assertTrue(
            "entry is wider than the bubble",
            field().fetchSemanticsNode().boundsInRoot.width > idleWidth,
        )

        field().performImeAction()
        assertEquals(listOf("alice"), submitted)
        field().assert(inMode(SearchEntryMode.Results))
        field().assertTextContains("alice")
        assertTrue(field().fetchSemanticsNode().boundsInRoot.width <= 240f * density + 1f)

        field().performClick()
        field().assert(inMode(SearchEntryMode.Entry))
        field().assertTextContains("alice")
        assertEquals("alice", query)
    }

    @Test
    fun clearingTheQueryOutsideSearchReturnsToTheIdleBubble() {
        query = "previous account"
        show()
        field().assert(inMode(SearchEntryMode.Results))

        compose.runOnIdle { query = "" }
        compose.waitForIdle()

        field().assert(inMode(SearchEntryMode.Idle))
    }

    @Test
    fun longQueryStaysWithinTheBubbleAndTheViewport() {
        query = "a".repeat(400)
        show()

        field().assert(inMode(SearchEntryMode.Results)).assertIsDisplayed()
        assertTrue(field().fetchSemanticsNode().boundsInRoot.width <= 240f * density + 1f)
    }

    @Test
    fun bubbleStaysReachableAtLargeFont() {
        show(fontScale = 2f)

        field().assertIsDisplayed().performClick()

        field().assert(inMode(SearchEntryMode.Entry))
    }
}
