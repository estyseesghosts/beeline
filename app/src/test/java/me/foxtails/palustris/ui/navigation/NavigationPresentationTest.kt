package me.foxtails.palustris.ui.navigation

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.AppIcons
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
    fun verticalPresentationKeepsSixTargetsAndIndicatorAlignedInLtr() =
        verifyVerticalPresentation(reducedMotion = false, direction = LayoutDirection.Ltr)

    @Test
    fun verticalPresentationKeepsSixTargetsAndProfileSwitchInRtl() =
        verifyVerticalPresentation(reducedMotion = false, direction = LayoutDirection.Rtl)

    @Test
    fun verticalPresentationSettlesImmediatelyWithReducedMotion() =
        verifyVerticalPresentation(reducedMotion = true, direction = LayoutDirection.Ltr)

    private fun verifyVerticalPresentation(reducedMotion: Boolean, direction: LayoutDirection) {
        val selectedTarget = mutableStateOf(WideNavigationItem.Home)
        val account = Account(
            id = AccountId(Connection("https://example.org", Protocol.MASTODON), "profile"),
            displayName = "Profile account",
            handle = "@profile@example.org",
        )
        var clicks = 0
        var accountSwitches = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(
                    LocalLayoutDirection provides direction,
                    LocalPalustrisMotionScheme provides PalustrisMotionScheme.standard(reducedMotion),
                ) {
                    MaterialTheme {
                        WideNavigationPresentation(
                            selectedTarget = selectedTarget.value,
                            account = account,
                            onTargetSelected = { target -> selectedTarget.value = target; clicks++ },
                            onOpenAccounts = { accountSwitches++ },
                            modifier = Modifier.width(80.dp).height(352.dp),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        val labels = WideNavigationItem.entries.map { compose.activity.getString(it.item.labelRes) }
        val initialBounds = labels.map { compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot }
        val density = compose.activity.resources.displayMetrics.density
        initialBounds.forEach { bounds ->
            assertEquals(48f * density, bounds.width, 1f)
            assertEquals(48f * density, bounds.height, 1f)
        }
        val profileAvatar = compose.onNodeWithTag("wide_navigation_profile_avatar", useUnmergedTree = true)
        profileAvatar.assertIsDisplayed()
        assertEquals(30f * density, profileAvatar.fetchSemanticsNode().boundsInRoot.width, 1f)
        initialBounds.zipWithNext().forEach { (first, second) ->
            assertTrue("vertical targets should follow the required order", first.center.y < second.center.y)
            assertEquals("vertical targets should share a center line", first.center.x, second.center.x, 1f)
        }
        fun indicatorBounds() = compose.onNodeWithTag(
            "selected_navigation_indicator",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        compose.onAllNodesWithTag("selected_navigation_indicator", useUnmergedTree = true).assertCountEquals(1)
        assertEquals(initialBounds[0].center.x, indicatorBounds().center.x, 1f)
        assertEquals(initialBounds[0].center.y, indicatorBounds().center.y, 1f)

        compose.onNodeWithContentDescription(labels[1]).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithContentDescription(labels[1]).assertIsFocused()

        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription(labels[5]).performClick()
        compose.runOnIdle {
            assertEquals(WideNavigationItem.Profile, selectedTarget.value)
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
        compose.onNodeWithContentDescription(labels[5]).performTouchInput { longClick() }
        assertEquals(1, accountSwitches)
    }

    @Test
    fun contextualActionButtonPreservesCompactBoundsEnabledStateAndCallback() {
        val enabled = mutableStateOf(false)
        var clicks = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme {
                    ContextualNavigationActionButton(
                        ContextualNavigationAction(
                            icon = AppIcons.Compose,
                            contentDescription = "Compose post",
                            enabled = enabled.value,
                            onClick = { clicks++ },
                        ),
                    )
                }
            }
        }
        val action = compose.onNodeWithContentDescription("Compose post")
        val bounds = action.fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertEquals(56f * density, bounds.width, 1f)
        assertEquals(56f * density, bounds.height, 1f)
        action.assertIsNotEnabled()

        compose.runOnIdle { enabled.value = true }
        action.assertIsEnabled().performClick()
        assertEquals(1, clicks)
    }
}
