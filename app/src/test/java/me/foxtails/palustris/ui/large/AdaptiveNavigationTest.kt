package me.foxtails.palustris.ui.large

import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.AppNavigationAnchor
import me.foxtails.palustris.ui.PalustrisTheme
import me.foxtails.palustris.ui.directmessages.DirectMessageUiState
import me.foxtails.palustris.ui.shell.AppShellFixtures
import me.foxtails.palustris.ui.shell.DirectMessagesContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Production fit, shell placement, and callback coverage with synthetic window insets. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w900dp-h900dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdaptiveNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val width = mutableStateOf(445f)
    private val height = mutableStateOf(704f)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val density get() = compose.activity.resources.displayMetrics.density

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

    private fun show(
        directMessages: DirectMessagesContract = DirectMessagesContract.Empty,
        tabletAnchor: AppNavigationAnchor = AppNavigationAnchor.Left,
        compactWideAnchor: AppNavigationAnchor = AppNavigationAnchor.Right,
    ) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                    Box(Modifier.width(width.value.dp).height(height.value.dp)) {
                        AppShellFixtures.app(
                            account = AppShellFixtures.account(),
                            directMessages = directMessages,
                            tabletNavigationAnchor = tabletAnchor,
                            compactWideNavigationAnchor = compactWideAnchor,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun outerScreenAndSquareTabletUsePhysicalAnchorsAndExactSharedBounds() {
        show()
        for (size in listOf(445f to 704f, 900f to 900f)) {
            for (layoutDirection in LayoutDirection.entries) {
                compose.runOnIdle { width.value = size.first; height.value = size.second; direction.value = layoutDirection }
                val stack = bounds(LARGE_FLOATING_NAVIGATION_TAG)
                val capsule = bounds(LARGE_NAVIGATION_CAPSULE_TAG)
                // Compact-wide reserves the contextual tab-caret slot and bottom-anchors the stack.
                // Expanded tablet keeps the capsule-plus-action stack vertically centered.
                val isCompactWide = size.first < 840f
                val expectedStackHeight = if (isCompactWide) 424f else 360f
                assertEquals(56f * density, stack.width, 1f)
                assertEquals(expectedStackHeight * density, stack.height, 1f)
                assertEquals(296f * density, capsule.height, 1f)
                assertEquals(if (size.first >= 840f) 8f * density else (size.first - 64f) * density, stack.left, 1f)
                assertEquals(stack.top, capsule.top, 1f)
                val action = compose.onNodeWithContentDescription("Compose post").fetchSemanticsNode().boundsInRoot
                assertEquals(56f * density, action.width, 1f)
                assertEquals(56f * density, action.height, 1f)
                assertEquals(capsule.bottom + 8f * density, action.top, 1f)
                if (isCompactWide) {
                    // Home exposes tab chips, so the contextual caret sits below the composer.
                    val caret = bounds(COMPACT_WIDE_TAB_CARET_TAG)
                    assertEquals(56f * density, caret.width, 1f)
                    assertEquals(56f * density, caret.height, 1f)
                    assertEquals(action.bottom + 8f * density, caret.top, 1f)
                    assertEquals(stack.bottom, caret.bottom, 1f)
                }
                assertTrue(stack.top >= 0f && stack.bottom <= size.second * density)
            }
        }
    }

    @Test fun savedAnchorsSelectSeparatePhysicalEdgesInLtrAndRtl() {
        show(
            tabletAnchor = AppNavigationAnchor.Right,
            compactWideAnchor = AppNavigationAnchor.Left,
        )
        for ((size, expectedLeft) in listOf(445f to 8f, 900f to 836f)) {
            for (layoutDirection in LayoutDirection.entries) {
                compose.runOnIdle {
                    width.value = size
                    height.value = if (size == 445f) 704f else 900f
                    direction.value = layoutDirection
                }
                assertEquals(expectedLeft * density, bounds(LARGE_FLOATING_NAVIGATION_TAG).left, 1f)
            }
        }
    }

    @Test fun dmContextualActionCallsRecipientFinderInsteadOfComposerOrPanelToggle() {
        var opened = 0
        val contract = DirectMessagesContract.Empty.copy(actions = object : DirectMessagesContract.Actions by DirectMessagesContract.Empty.actions {
            override fun openRecipientFinder() { opened++ }
        })
        show(contract)
        compose.onNodeWithContentDescription("Direct messages").performClick()
        compose.onNodeWithContentDescription("New conversation").assertIsDisplayed().performClick()
        assertEquals(1, opened)
        compose.onNodeWithContentDescription("Direct messages").assertIsSelected()
        compose.onNodeWithText("New post").assertDoesNotExist()
    }

    @Test fun resizingAcrossCompactFallbackPreservesGroupedChildMemoryAndPanePolicy() {
        show()
        compose.onNodeWithContentDescription("Photo grid").performClick()
        compose.runOnIdle { width.value = 400f }
        compose.onNodeWithTag(LARGE_FLOATING_NAVIGATION_TAG).assertDoesNotExist()
        compose.onNodeWithText("No media posts available").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Home").onLast().performClick()
        compose.onNodeWithContentDescription("Photo grid").performClick()
        compose.onNodeWithText("No media posts available").assertIsDisplayed()
        compose.runOnIdle { width.value = 445f }
        compose.onNodeWithContentDescription("Photo grid").assertIsSelected()
        compose.onNodeWithText("Select a post").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Home").onLast().performClick()
        compose.runOnIdle { width.value = 900f; height.value = 900f }
        compose.onNodeWithText("Select a post").assertIsDisplayed()
        compose.runOnIdle { height.value = 350f }
        compose.onNodeWithTag(LARGE_FLOATING_NAVIGATION_TAG).assertDoesNotExist()
        compose.onNodeWithContentDescription("Compose post").assertIsDisplayed()
        compose.onNodeWithText("Select a post").assertIsDisplayed()
    }

    @Test fun liveMandatoryInsetsMergePhysicallyAndImeDoesNotChangeFitOrPlacement() {
        show()
        fun dispatch(imeDp: Int) {
            compose.runOnIdle {
                val view = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                val bars = Insets.of(0, (24 * density).toInt(), 0, (24 * density).toInt())
                val mandatory = Insets.of(0, 0, 0, (24 * density).toInt())
                val insets = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.systemBars(), bars)
                    .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars(), bars)
                    .setInsets(WindowInsetsCompat.Type.mandatorySystemGestures(), mandatory)
                    .setInsets(WindowInsetsCompat.Type.systemGestures(), Insets.of((24 * density).toInt(), 0, (24 * density).toInt(), 0))
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, (imeDp * density).toInt()))
                    .setVisible(WindowInsetsCompat.Type.ime(), imeDp > 0).build()
                ViewCompat.dispatchApplyWindowInsets(view, insets)
            }
            compose.waitForIdle()
        }
        dispatch(0)
        val closed = bounds(LARGE_FLOATING_NAVIGATION_TAG)
        assertEquals(381f * density, closed.left, 1f)
        assertTrue(closed.top >= 24f * density && closed.bottom <= 680f * density)
        dispatch(400)
        assertEquals(closed, bounds(LARGE_FLOATING_NAVIGATION_TAG))
        compose.runOnIdle { direction.value = LayoutDirection.Rtl }
        assertEquals(closed, bounds(LARGE_FLOATING_NAVIGATION_TAG))
    }

    @Test fun wideCompactFallbackKeepsSearchFieldAndDmEditorAboveNavigation() {
        val contract = DirectMessagesContract.Empty.copy(state = DirectMessageUiState(
            recipient = AppShellFixtures.account("recipient"), editorText = "Retained editor",
        ))
        show(contract)
        compose.onNodeWithContentDescription("Search").performClick()
        compose.runOnIdle { width.value = 900f; height.value = 350f }
        val navigation = compose.onNodeWithContentDescription("Photo grid").fetchSemanticsNode().boundsInRoot
        val field = compose.onNodeWithContentDescription("Search field").fetchSemanticsNode().boundsInRoot
        assertTrue("wide Search field clears compact fallback", field.bottom <= navigation.top)
        compose.runOnIdle { height.value = 900f }
        compose.onNodeWithContentDescription("Direct messages").performClick()
        compose.runOnIdle { height.value = 350f }
        val dmNavigation = compose.onNodeWithContentDescription("Notifications").fetchSemanticsNode().boundsInRoot
        val editor = bounds("direct_message_input")
        val send = bounds("direct_message_send")
        assertTrue("wide editor clears compact fallback", editor.bottom <= dmNavigation.top)
        assertTrue("wide Send clears compact fallback", send.bottom <= dmNavigation.top)
        compose.onNodeWithTag("direct_message_input").assertTextContains("Retained editor")
    }

    @Test fun paneSlotsAndActionlessCapsuleKeepPhysicalOriginsInRtl() {
        val visible = mutableStateOf(true)
        var leftClearance = 0f
        var rightClearance = 0f
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                    PalustrisTheme {
                        LargeScreenShell(
                            windowWidth = 900.dp, selectedTarget = LargeNavTarget.Profile, account = null,
                            hasDetail = false, twoPane = true,
                            navigationFit = calculateNavigationFit(900f, 900f, Rect.Zero, anchorLeft = true),
                            anchorLeft = true, navigationVisible = visible.value, action = null,
                            onTargetSelected = {}, onOpenAccounts = {},
                            primaryContent = { modifier, left, right ->
                                leftClearance = left.value; rightClearance = right.value
                                Box(modifier.testTag("physical_primary"))
                            },
                            detailContent = { modifier, _, _ -> Box(modifier.testTag("physical_detail")) },
                        )
                    }
                }
            }
        }
        val primary = bounds("physical_primary")
        val detail = bounds("physical_detail")
        assertEquals(16f * density, primary.left, 1f)
        assertTrue(primary.right < detail.left)
        assertEquals(48f, leftClearance, 0.01f)
        assertEquals(0f, rightClearance, 0.01f)
        assertEquals(bounds(LARGE_FLOATING_NAVIGATION_TAG).top, bounds(LARGE_NAVIGATION_CAPSULE_TAG).top, 1f)
        compose.runOnIdle { direction.value = LayoutDirection.Rtl }
        assertEquals(primary, bounds("physical_primary"))
        assertEquals(detail, bounds("physical_detail"))
        compose.runOnIdle { visible.value = false }
        compose.onNodeWithTag(LARGE_FLOATING_NAVIGATION_TAG).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0f, leftClearance, 0.01f) }
    }

    @Test fun anchorClearanceFollowsThePaneUnderTheControlsInBothDirections() {
        val anchorLeft = mutableStateOf(true)
        var primaryLeft = 0f
        var primaryRight = 0f
        var detailLeft = 0f
        var detailRight = 0f
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                    PalustrisTheme {
                        LargeScreenShell(
                            windowWidth = 900.dp,
                            selectedTarget = LargeNavTarget.Profile,
                            account = null,
                            hasDetail = false,
                            twoPane = true,
                            navigationFit = calculateNavigationFit(
                                900f,
                                900f,
                                Rect.Zero,
                                anchorLeft = anchorLeft.value,
                            ),
                            anchorLeft = anchorLeft.value,
                            navigationVisible = true,
                            action = null,
                            onTargetSelected = {},
                            onOpenAccounts = {},
                            primaryContent = { modifier, left, right ->
                                primaryLeft = left.value
                                primaryRight = right.value
                                Box(modifier.testTag("anchor_primary"))
                            },
                            detailContent = { modifier, left, right ->
                                detailLeft = left.value
                                detailRight = right.value
                                Box(modifier.testTag("anchor_detail"))
                            },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(48f, primaryLeft, 0.01f)
        assertEquals(0f, primaryRight, 0.01f)
        assertEquals(0f, detailLeft, 0.01f)
        assertEquals(0f, detailRight, 0.01f)

        compose.runOnIdle { anchorLeft.value = false }
        compose.waitForIdle()
        assertEquals(0f, primaryLeft, 0.01f)
        assertEquals(0f, primaryRight, 0.01f)
        assertEquals(0f, detailLeft, 0.01f)
        assertEquals(48f, detailRight, 0.25f)
        val rightStack = bounds(LARGE_FLOATING_NAVIGATION_TAG)
        compose.runOnIdle { direction.value = LayoutDirection.Rtl }
        assertEquals(rightStack, bounds(LARGE_FLOATING_NAVIGATION_TAG))
        assertEquals(48f, detailRight, 0.25f)
    }
}
