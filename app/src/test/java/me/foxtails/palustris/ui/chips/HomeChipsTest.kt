package me.foxtails.palustris.ui.chips

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.HashtagOrigin
import me.foxtails.palustris.ui.feed.FeedState
import me.foxtails.palustris.ui.shell.AppShellFixtures
import me.foxtails.palustris.ui.shell.FeedChipsContract
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
class HomeChipsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val account = AppShellFixtures.account("chips")
    private val removed = mutableListOf<String>()
    private val added = mutableListOf<String>()
    private val refreshed = mutableListOf<FeedSource>()
    private var chips by mutableStateOf(FeedChipsContract.Empty)

    private fun contract(state: FeedChipState) = FeedChipsContract(
        state,
        object : FeedChipsContract.Actions {
            override fun refresh() = Unit
            override fun addHashtag(value: String, onAdded: (FeedSource.Hashtag) -> Unit) {
                added += value
                onAdded(FeedSource.Hashtag(value.removePrefix("#"), HashtagOrigin.Server))
            }
            override fun removeHashtag(tag: String, onRemoved: () -> Unit) {
                removed += tag
                onRemoved()
            }
            override fun clearError() = Unit
        },
    )

    private fun show(state: FeedChipState) {
        chips = contract(state)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                AppShellFixtures.app(
                    account = account,
                    home = AppShellFixtures.home(FeedState(), chips = chips, onRefreshFeed = { refreshed += it }),
                )
            }
        }
        compose.waitForIdle()
    }

    private fun scrollTo(tag: String) {
        compose.onNodeWithTag("home_timeline_tabs").performScrollToNode(hasTestTag(tag))
    }

    private val full = FeedChipState(
        lists = listOf(FeedSource.UserList("l1", "Artists")),
        hashtags = listOf(FeedSource.Hashtag("cats", HashtagOrigin.Server)),
        antennas = listOf(FeedSource.Antenna("n1", "Birds")),
        loaded = true,
    )

    @Test
    fun theRowShowsListsHashtagsAntennasAndTheAddControl() {
        show(full)

        scrollTo("home_list_l1")
        compose.onNodeWithTag("home_list_l1").assertIsDisplayed()
        scrollTo("home_hashtag_cats")
        compose.onNodeWithTag("home_hashtag_cats").assertIsDisplayed()
        scrollTo("home_antenna_n1")
        compose.onNodeWithTag("home_antenna_n1").assertIsDisplayed()
        scrollTo("home_add_hashtag")
        compose.onNodeWithTag("home_add_hashtag").assertIsDisplayed()
    }

    @Test
    fun selectingAChipLoadsThatFeedOnHome() {
        show(full)

        scrollTo("home_list_l1")
        compose.onNodeWithTag("home_list_l1").performClick()

        compose.onNodeWithTag("home_list_l1").assertIsSelected()
        assertEquals(FeedSource.UserList("l1", "Artists"), refreshed.last())
    }

    @Test
    fun aLongPressOnAServerHashtagOffersUnfollowAndOnlyTheButtonRemoves() {
        show(full)
        scrollTo("home_hashtag_cats")

        compose.onNodeWithTag("home_hashtag_cats").performTouchInput { longClick() }
        compose.onNodeWithText("Unfollow #cats").assertIsDisplayed()
        assertTrue(removed.isEmpty())
        compose.onNodeWithTag("feed_chip_hashtag_action").performClick()

        assertEquals(listOf("cats"), removed)
    }

    @Test
    fun aLongPressOnADeviceHashtagOffersRemove() {
        show(FeedChipState(hashtags = listOf(FeedSource.Hashtag("photo", HashtagOrigin.Device)), loaded = true))
        scrollTo("home_hashtag_photo")

        compose.onNodeWithTag("home_hashtag_photo").performTouchInput { longClick() }

        compose.onNodeWithText("Remove #photo").assertIsDisplayed()
    }

    @Test
    fun aLongPressOnAListOrAntennaOpensNothing() {
        show(full)

        scrollTo("home_list_l1")
        compose.onNodeWithTag("home_list_l1").performTouchInput { longClick() }
        scrollTo("home_antenna_n1")
        compose.onNodeWithTag("home_antenna_n1").performTouchInput { longClick() }

        assertTrue(compose.onAllNodesWithTagCount("feed_chip_hashtag_action") == 0)
        assertTrue(removed.isEmpty())
    }

    @Test
    fun theHashtagChipExposesATalkBackActionThatOpensTheSameMenu() {
        show(full)
        scrollTo("home_hashtag_cats")

        val node = compose.onNodeWithTag("home_hashtag_cats").fetchSemanticsNode()
        val action = node.config.getOrNull(SemanticsActions.CustomActions)?.single()
        assertEquals("Unfollow #cats", action?.label)
        compose.runOnUiThread { action?.action?.invoke() }
        compose.waitForIdle()

        compose.onNodeWithTag("feed_chip_hashtag_action").assertIsDisplayed()
    }

    @Test
    fun aListAndAnAntennaChipHaveNoLongPressAction() {
        show(full)
        scrollTo("home_list_l1")

        val node = compose.onNodeWithTag("home_list_l1").fetchSemanticsNode()

        assertTrue(node.config.getOrNull(SemanticsActions.CustomActions).isNullOrEmpty())
    }

    @Test
    @Config(qualifiers = "w600dp-h800dp-420dpi")
    fun addingAHashtagSelectsItsChip() {
        // The row alone, and a wider screen: Robolectric never goes idle while a dialog opens at phone width.
        var selected by mutableStateOf<FeedSource>(FeedSource.Timeline(me.foxtails.palustris.domain.Timeline.Home))
        val state = full
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                me.foxtails.palustris.ui.PalustrisTheme {
                    me.foxtails.palustris.ui.components.DestinationChipRow(
                        entries = me.foxtails.palustris.ui.navigation.homeFeedChipEntries(
                            timelines = setOf(me.foxtails.palustris.domain.Timeline.Home),
                            chips = contract(state),
                            selected = selected,
                        ) { selected = it },
                        rowContentDescription = "Home feeds",
                        listState = androidx.compose.foundation.lazy.rememberLazyListState(),
                        visible = true,
                        onToggleVisibility = {},
                        rowTestTag = "home_timeline_tabs",
                    )
                }
            }
        }
        compose.waitForIdle()
        scrollTo("home_add_hashtag")

        compose.onNodeWithTag("home_add_hashtag").performClick()
        compose.onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("#dogs")) }
        compose.onNodeWithText("Add").performClick()

        assertEquals(listOf("dogs"), added)
        assertEquals(FeedSource.Hashtag("dogs", HashtagOrigin.Server), selected)
    }

    @Test
    fun aSelectedChipThatLeavesTheCatalogFallsBackToHome() {
        show(full)
        scrollTo("home_list_l1")
        compose.onNodeWithTag("home_list_l1").performClick()
        assertEquals(FeedSource.UserList("l1", "Artists"), refreshed.last())

        compose.runOnUiThread { chips = contract(full.copy(lists = emptyList())) }
        compose.waitForIdle()

        assertEquals(FeedSource.Timeline(me.foxtails.palustris.domain.Timeline.Home), refreshed.last())
        compose.onNodeWithTag("home_timeline_tab_Home").assertIsSelected()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTagCount(tag: String) =
        onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size
}
