package me.foxtails.palustris.ui.navigation

import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.PalustrisMotionScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class NavigationPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun verticalSixButtonCapsuleKeepsTargetsAndIndicatorAlignedAfterInterruptedSelection() =
        verifyVerticalCapsule(reducedMotion = false)

    @Test
    fun verticalSixButtonCapsuleSettlesImmediatelyWithReducedMotion() =
        verifyVerticalCapsule(reducedMotion = true)

    private fun verifyVerticalCapsule(reducedMotion: Boolean) {
        val selectedIndex = mutableIntStateOf(0)
        var clicks = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                    LocalPalustrisMotionScheme provides PalustrisMotionScheme.standard(reducedMotion),
                ) {
                    MaterialTheme {
                        NavigationCapsule(
                            selectedIndex = selectedIndex.intValue,
                            itemCount = WideNavigationItem.entries.size,
                            orientation = Orientation.Vertical,
                            modifier = Modifier.width(80.dp).height(352.dp),
                        ) {
                            Column(
                                Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                WideNavigationItem.entries.forEachIndexed { index, item ->
                                    NavigationButton(
                                        label = item.name,
                                        icon = item.item.icon,
                                        selected = selectedIndex.intValue == index,
                                        onClick = { selectedIndex.intValue = index; clicks++ },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val labels = WideNavigationItem.entries.map { it.name }
        val initialBounds = labels.map { compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot }
        val density = compose.activity.resources.displayMetrics.density
        initialBounds.forEach { bounds ->
            assertEquals(48f * density, bounds.width, 1f)
            assertEquals(48f * density, bounds.height, 1f)
        }
        fun indicatorBounds() = compose.onNodeWithTag(
            "selected_navigation_indicator",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        compose.onAllNodesWithTag("selected_navigation_indicator", useUnmergedTree = true).assertCountEquals(1)
        assertEquals(initialBounds[0].center.x, indicatorBounds().center.x, 1f)
        assertEquals(initialBounds[0].center.y, indicatorBounds().center.y, 1f)

        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription(labels[5]).performClick()
        compose.runOnIdle {
            assertEquals(5, selectedIndex.intValue)
            assertEquals(1, clicks)
            // The paused clock needs the click snapshot applied before its first recomposition.
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(labels[5]).assertIsSelected()
        if (reducedMotion) {
            assertEquals(initialBounds[5].center.x, indicatorBounds().center.x, 1f)
            assertEquals(initialBounds[5].center.y, indicatorBounds().center.y, 1f)
        }
        compose.mainClock.advanceTimeBy(64)
        compose.onAllNodesWithTag("selected_navigation_indicator", useUnmergedTree = true).assertCountEquals(1)
        val movingCenter = indicatorBounds().center
        if (reducedMotion) {
            assertEquals(initialBounds[5].center.x, movingCenter.x, 1f)
            assertEquals(initialBounds[5].center.y, movingCenter.y, 1f)
        } else {
            assertEquals(initialBounds[0].center.x, movingCenter.x, 1f)
            assertTrue(movingCenter.y > initialBounds[0].center.y)
            assertTrue(movingCenter.y < initialBounds[5].center.y)
        }
        labels.forEachIndexed { index, label ->
            assertEquals(initialBounds[index], compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot)
        }
        compose.onNodeWithContentDescription(labels[2]).performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        compose.onNodeWithContentDescription(labels[2]).assertIsSelected()
        compose.onAllNodesWithTag("selected_navigation_indicator", useUnmergedTree = true).assertCountEquals(1)
        assertEquals(initialBounds[2].center.x, indicatorBounds().center.x, 1f)
        assertEquals(initialBounds[2].center.y, indicatorBounds().center.y, 1f)
        labels.forEachIndexed { index, label ->
            assertEquals(initialBounds[index], compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot)
        }
        assertEquals(2, clicks)
    }

    @Test
    fun navigationButtonSupportsClickAndOptionalLongClick() {
        val selectedIndex = mutableIntStateOf(0)
        var accountLongPresses = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme {
                    NavigationButton(
                        label = "Profile",
                        icon = WideNavigationItem.Profile.item.icon,
                        selected = selectedIndex.intValue == 5,
                        onClick = { selectedIndex.intValue = 5 },
                        onLongClick = { accountLongPresses++ },
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithContentDescription("Profile").assertIsSelected()
        compose.onNodeWithContentDescription("Profile").performTouchInput { longClick() }
        assertEquals(1, accountLongPresses)
    }
}
