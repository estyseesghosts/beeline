package me.foxtails.palustris.ui.chips

import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.PalustrisTheme
import me.foxtails.palustris.ui.components.ChipCaretPresentation
import me.foxtails.palustris.ui.components.DestinationChipRow
import me.foxtails.palustris.ui.navigation.homeFeedChipEntries
import me.foxtails.palustris.ui.shell.FeedChipsContract
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChipRowCollapseTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private var visible by mutableStateOf(true)

    @Test
    fun aCollapsedRowLeavesNoChipsAndAnExpandedRowKeepsItsHeight() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    DestinationChipRow(
                        entries = homeFeedChipEntries(
                            timelines = setOf(Timeline.Home),
                            chips = FeedChipsContract.Empty,
                            selected = FeedSource.Timeline(Timeline.Home),
                        ) {},
                        rowContentDescription = "Home feeds",
                        listState = rememberLazyListState(),
                        visible = visible,
                        onToggleVisibility = {},
                        rowTestTag = "chip_row",
                        caretPresentation = ChipCaretPresentation.Hidden,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("chip_row").assertHeightIsAtLeast(48.dp)

        compose.runOnIdle { visible = false }
        compose.waitUntil(5_000) { compose.onAllNodesWithTagCount("chip_row") == 0 }

        compose.runOnIdle { visible = true }
        compose.waitForIdle()
        compose.onNodeWithTag("chip_row").assertHeightIsAtLeast(48.dp)
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
