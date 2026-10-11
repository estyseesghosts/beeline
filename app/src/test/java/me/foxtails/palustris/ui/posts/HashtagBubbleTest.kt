package me.foxtails.palustris.ui.posts

import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
class HashtagBubbleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun show(hashtags: List<String>, side: BubblePlacement = BubblePlacement.Below) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme { HashtagBubble(hashtags, maxHeight = 600.dp, openingKey = 1L, side = side, onSelected = {}) }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun fewHashtagsShowNoSeeAll() {
        show(listOf("#a", "#b", "#c"))
        compose.onAllNodesWithTag("hashtag_bubble_expand").assertCountEquals(0)
        compose.onNodeWithTag("hashtag_bubble_#a").assertIsDisplayed()
    }

    @Test
    fun manyHashtagsShowFivePillsAndSeeAll() {
        show((1..8).map { "#tag$it" })
        compose.onNodeWithTag("hashtag_bubble_expand").assertIsDisplayed()
        val shown = (1..8).count { compose.onAllNodesWithTag("hashtag_bubble_#tag$it").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(5, shown)
    }

    @Test
    fun compactBubbleIsNarrowerThanItsLimit() {
        show(listOf("#a", "#bb"))
        val width = compose.onNodeWithTag("hashtag_bubble_compact").fetchSemanticsNode().size.width
        assertTrue("width $width", width < 200 * compose.activity.resources.displayMetrics.density)
    }

    @Test
    fun seeAllIsTheFarthestEntryOnEachSide() {
        val tags = listOf("#a", "#b")
        assertEquals(
            listOf(HashtagBubbleEntry.Tag("#a"), HashtagBubbleEntry.Tag("#b"), HashtagBubbleEntry.SeeAll),
            hashtagBubbleEntries(tags, showSeeAll = true, side = BubblePlacement.Below),
        )
        assertEquals(
            listOf(HashtagBubbleEntry.SeeAll, HashtagBubbleEntry.Tag("#a"), HashtagBubbleEntry.Tag("#b")),
            hashtagBubbleEntries(tags, showSeeAll = true, side = BubblePlacement.Above),
        )
        assertEquals(
            listOf(HashtagBubbleEntry.Tag("#a")),
            hashtagBubbleEntries(listOf("#a"), showSeeAll = false, side = BubblePlacement.Above),
        )
    }

    @Test
    fun sideIsBelowWhenRoomBelowIsAtLeastRoomAbove() {
        val top = Rect(0f, 100f, 50f, 140f)
        val bottom = Rect(0f, 1800f, 50f, 1840f)
        assertEquals(BubblePlacement.Below, hashtagBubbleSide(top, 2000f, 0f, 8f))
        assertEquals(BubblePlacement.Above, hashtagBubbleSide(bottom, 2000f, 0f, 8f))
        // Clearance under the anchor counts against the room below.
        assertEquals(BubblePlacement.Above, hashtagBubbleSide(Rect(0f, 900f, 50f, 940f), 2000f, 400f, 8f))
        // A tie goes below.
        assertEquals(BubblePlacement.Below, hashtagBubbleSide(Rect(0f, 100f, 50f, 100f), 208f, 0f, 0f))
    }

    @Test
    fun expandedCloseTakesTheSameEndOnEachSide() {
        show((1..8).map { "#tag$it" }, BubblePlacement.Above)
        compose.onNodeWithTag("hashtag_bubble_expand").performClick()
        val close = compose.onNodeWithTag("hashtag_bubble_close").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithTag("hashtag_bubble_#tag1").fetchSemanticsNode().boundsInRoot
        assertTrue("close above list", close.top < first.top)
    }
}
