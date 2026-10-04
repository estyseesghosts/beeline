package me.foxtails.palustris.ui.navigation

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.PalustrisMotionScheme
import me.foxtails.palustris.ui.shell.Destination
import me.foxtails.palustris.ui.shell.NotificationsPanel
import me.foxtails.palustris.ui.shell.SearchPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class CompactNavigationSelectionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun oneIndicatorTravelsBetweenStableSlots() = verifySelection(LayoutDirection.Ltr, false)

    @Test fun oneIndicatorTravelsInRtl() = verifySelection(LayoutDirection.Rtl, false)

    @Test fun reducedMotionMovesTheSameIndicatorImmediately() = verifySelection(LayoutDirection.Ltr, true)

    private fun verifySelection(direction: LayoutDirection, reducedMotion: Boolean) {
        val destination = mutableStateOf(Destination.Home)
        var selections = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(
                    LocalLayoutDirection provides direction,
                    LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f),
                    LocalPalustrisMotionScheme provides PalustrisMotionScheme.standard(reducedMotion),
                ) {
                    MaterialTheme {
                        Box(Modifier.width(320.dp)) {
                            CompactContextualNavigationBar(
                                destination = destination.value,
                                searchPanel = SearchPanel.PhotoGrid,
                                notificationsPanel = NotificationsPanel.DirectMessages,
                                action = null,
                                account = null,
                                onOpenAccounts = {},
                                onDestinationSelected = { destination.value = it; selections++ },
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val labels = listOf("Home", "Photo grid", "Direct messages", "Profile")
        val slots = labels.map { compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot }
        val density = compose.activity.resources.displayMetrics.density
        slots.forEach {
            assertEquals(48f * density, it.width, 1f)
            assertEquals(48f * density, it.height, 1f)
        }
        fun indicator() = compose.onNodeWithTag("selected_navigation_indicator", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        fun assertOneIndicator() = compose.onAllNodesWithTag("selected_navigation_indicator", useUnmergedTree = true)
            .assertCountEquals(1)
        assertOneIndicator()
        assertEquals(slots[0].center.x, indicator().center.x, 1f)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription(labels[3]).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(64)
        assertOneIndicator()
        val movingCenter = indicator().center.x
        if (reducedMotion) {
            assertEquals(slots[3].center.x, movingCenter, 1f)
        } else {
            assertTrue(movingCenter > minOf(slots[0].center.x, slots[3].center.x))
            assertTrue(movingCenter < maxOf(slots[0].center.x, slots[3].center.x))
        }
        // Interrupt the trip. Selection belongs to the caller, not to animation completion.
        compose.onNodeWithContentDescription(labels[1]).performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertOneIndicator()
        val selected = compose.onNodeWithContentDescription(labels[1])
        selected.assertIsSelected()
        assertEquals(slots[1].center.x, indicator().center.x, 1f)
        assertEquals(slots[1].center.y, indicator().center.y, 1f)
        labels.forEachIndexed { index, label ->
            assertEquals(slots[index], compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot)
        }
        assertEquals(Destination.Search, destination.value)
        assertEquals(2, selections)
    }
}
