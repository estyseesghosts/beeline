package me.foxtails.palustris.ui.feed

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.Reaction
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.timelineDisplayOrder
import me.foxtails.palustris.ui.components.DestinationChipRow
import me.foxtails.palustris.ui.components.restingChipEdge
import me.foxtails.palustris.ui.large.LargeBottomDockClearance
import me.foxtails.palustris.ui.navigation.ShellNavigator
import me.foxtails.palustris.ui.navigation.homeTimelineChipEntries
import me.foxtails.palustris.ui.posts.LocalMutedHashtags
import me.foxtails.palustris.ui.shell.AppShellFixtures
import me.foxtails.palustris.ui.shell.DestinationDraftCallbacks
import me.foxtails.palustris.ui.shell.DestinationNavigationCallbacks
import me.foxtails.palustris.ui.shell.DestinationPostCallbacks
import me.foxtails.palustris.ui.shell.DirectMessagesContract
import me.foxtails.palustris.ui.shell.HomeContract
import me.foxtails.palustris.ui.shell.HomeFeedUiState
import me.foxtails.palustris.ui.shell.PhotoGridContract
import me.foxtails.palustris.ui.shell.ShellDestinationContent
import me.foxtails.palustris.ui.shell.ShellOverlayPresenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeClearanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val author = AppShellFixtures.account()
    private val right = 72.dp
    private val bottom = 96.dp
    private val density get() = compose.activity.resources.displayMetrics.density

    private fun chipsRest(direction: LayoutDirection, physicalLeft: Boolean): Float = compose
        .onNodeWithTag("home_timeline_tabs", useUnmergedTree = true)
        .restingChipEdge(compose, direction, physicalLeft)

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

    private fun show(direction: LayoutDirection, content: @Composable () -> Unit) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                key(direction) {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        Box(Modifier.fillMaxSize().testTag("home_test_viewport")) { content() }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertClicksClearRight(safeRight: Float) {
        val nodes = compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("fixture must exercise interactions", nodes.isNotEmpty())
        // Chips scroll beneath floating chrome by design; chipsRest asserts where they rest.
        nodes.filter { it.boundsInRoot.width > 0 && it.boundsInRoot.height > 0 && !it.isChipRowEntry() }.forEach {
            assertTrue("click target ${it.config} crosses physical right: ${it.boundsInRoot}",
                it.boundsInRoot.right <= safeRight + 1f)
        }
    }

    @Test fun wideContentClearsPhysicalLeftWithoutShrinkingViewport() {
        val post = AppShellFixtures.post("left", author, "Left clearance")
        for (direction in LayoutDirection.entries) {
            show(direction) {
                HomeFeed(
                    state = HomeFeedUiState(posts = listOf(post)), compactLayout = false,
                    leftObstructionClearance = right,
                    availableActions = setOf(PostAction.Reply, PostAction.Favorite),
                    onRefresh = {}, onLoadMore = {}, onSignIn = {},
                )
            }
            val viewport = bounds("home_test_viewport")
            assertEquals(viewport, bounds("home_feed_list"))
            assertEquals(viewport.left, bounds("home_post_underlay_left").left, 1f)
            val safeLeft = viewport.left + right.value * density
            assertEquals(safeLeft, bounds("post_row_left").left, 1f)
            compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
                .filter { it.boundsInRoot.width > 0 && it.boundsInRoot.height > 0 }.forEach {
                    assertTrue("Home interaction clears physical left", it.boundsInRoot.left >= safeLeft - 1f)
                }
        }
    }

    @Test fun widePostControlsAndDividerKeepFullViewportInBothDirections() {
        val post = AppShellFixtures.post("controls", author, "Interactive body #fixture").copy(
            attachments = listOf(Attachment("https://fixture.example/photo.jpg", "image/jpeg", "Fixture photo")),
            reactions = listOf(Reaction("👍", 2, false)),
        )
        for (direction in LayoutDirection.entries) {
            var replies = 0
            show(direction) {
                HomeFeed(
                    state = HomeFeedUiState(posts = listOf(post), ownedPosts = listOf(AppShellFixtures.owned(author, post))),
                    compactLayout = false, topContentPadding = 16.dp,
                    rightObstructionClearance = right, bottomObstructionClearance = bottom,
                    availableActions = setOf(PostAction.Reply, PostAction.Favorite, PostAction.Bookmark, PostAction.React),
                    onRefresh = {}, onLoadMore = {}, onSignIn = {}, onReply = { replies++ }, onOpenPost = {},
                )
            }
            val viewport = bounds("home_test_viewport")
            assertEquals(viewport, bounds("home_feed_content"))
            assertEquals(viewport, bounds("home_feed_list"))
            assertEquals("outer row stays full width", viewport.right, bounds("home_post_underlay_controls").right, 1f)
            assertEquals("row starts at physical left", viewport.left, bounds("post_row_controls").left, 1f)
            assertEquals(viewport.right - right.value * density, bounds("post_row_controls").right, 1f)
            assertClicksClearRight(viewport.right - right.value * density)
            compose.onNodeWithContentDescription("Reply").performClick()
            assertEquals(1, replies)
            scrollToEnd()
            val divider = bounds("home_divider_controls")
            assertEquals(viewport.left, divider.left, 1f)
            assertEquals("divider underlay stays full width", viewport.right, divider.right, 1f)
            assertClicksClearRight(viewport.right - right.value * density)
        }
    }

    @Test fun wideFinalPostAndContinuationClearFloatingDockAndBottom() {
        val posts = (0..8).map { AppShellFixtures.post("final-$it", author, "Home fixture $it") }
        for (direction in LayoutDirection.entries) {
            var continued = 0
            show(direction) {
                HomeFeed(
                    state = HomeFeedUiState(posts = posts, nextCursor = "next"), compactLayout = false,
                    topContentPadding = 16.dp, bottomContentClearance = LargeBottomDockClearance,
                    rightObstructionClearance = right, bottomObstructionClearance = bottom,
                    onRefresh = {}, onLoadMore = { continued++ }, onSignIn = {},
                    bottomDock = {
                        DestinationChipRow(
                            entries = homeTimelineChipEntries(setOf(Timeline.Home), Timeline.Home) {},
                            rowContentDescription = "Home timelines",
                            listState = rememberLazyListState(),
                            visible = true,
                            onToggleVisibility = {},
                            rowTestTag = "home_timeline_tabs",
                        )
                    },
                )
            }
            val viewport = bounds("home_feed_list")
            val tabs = bounds("home_timeline_tabs")
            assertTrue(chipsRest(direction, false) <= viewport.right - right.value * density + 1f)
            assertTrue(tabs.bottom <= viewport.bottom - bottom.value * density + 1f)
            scrollToEnd()
            val footer = compose.onNodeWithText("Load older posts").fetchSemanticsNode().boundsInRoot
            assertTrue("footer clears dock", footer.bottom <= tabs.top)
            assertTrue("last post clears dock", bounds("post_row_final-8").bottom <= tabs.top)
            assertTrue(footer.bottom <= viewport.bottom - bottom.value * density + 1f)
            assertClicksClearRight(viewport.right - right.value * density)
            val before = continued // Automatic paging stays feature-owned; manual continuation adds one request.
            compose.onNodeWithText("Load older posts").assertIsDisplayed().performClick()
            assertTrue(continued > before)
        }
    }

    @Test fun errorAndSignInActionsClearRightAndKeepCallbacks() {
        for (direction in LayoutDirection.entries) {
            for (needsSignIn in listOf(false, true)) {
                var refreshed = 0
                var signedIn = 0
                show(direction) {
                    HomeFeed(
                        state = HomeFeedUiState(error = "Home failed", needsSignIn = needsSignIn),
                        compactLayout = false, rightObstructionClearance = right, bottomObstructionClearance = bottom,
                        onRefresh = { refreshed++ }, onLoadMore = {}, onSignIn = { signedIn++ },
                    )
                }
                val safeRight = bounds("home_feed_content").right - right.value * density
                assertEquals("error surface keeps existing spacing and underlay",
                    bounds("home_feed_content").right - 16 * density, bounds("home_feed_error").right, 1f)
                assertTrue(compose.onNodeWithText("Home failed").fetchSemanticsNode().boundsInRoot.right <= safeRight)
                assertClicksClearRight(safeRight)
                compose.onNodeWithText(if (needsSignIn) "Sign in again" else "Retry").performClick()
                assertEquals(if (needsSignIn) 0 else 1, refreshed)
                assertEquals(if (needsSignIn) 1 else 0, signedIn)
            }
        }
    }

    @Test fun emptyFilteredAndLoadingMoreStatesKeepFullViewportAndClearText() {
        for (direction in LayoutDirection.entries) {
            val post = AppShellFixtures.post("filtered", author, "Muted #quiet")
            for (state in listOf(HomeFeedUiState(), HomeFeedUiState(posts = listOf(post)), HomeFeedUiState(posts = listOf(post), loadingMore = true))) {
                show(direction) {
                    CompositionLocalProvider(LocalMutedHashtags provides setOf("quiet")) {
                        HomeFeed(state, compactLayout = false, rightObstructionClearance = right,
                            bottomObstructionClearance = bottom, onRefresh = {}, onLoadMore = {}, onSignIn = {})
                    }
                }
                assertEquals(bounds("home_test_viewport"), bounds("home_feed_list"))
                val text = if (state.posts.isEmpty()) compose.activity.getString(R.string.feed_empty_title)
                    else compose.activity.getString(R.string.feed_filtered_empty)
                assertTrue(compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.right <=
                    bounds("home_feed_list").right - right.value * density + 1f)
            }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-420dpi")
    fun compactInputsDoNotChangeViewportPostOrScrollClearance() {
        for (direction in LayoutDirection.entries) {
            val clearance = mutableStateOf(0.dp)
            val posts = (0..8).map { AppShellFixtures.post("compact-$it", author, "Compact fixture $it") }
            show(direction) {
                HomeFeed(HomeFeedUiState(posts = posts), rightObstructionClearance = clearance.value,
                    bottomObstructionClearance = clearance.value, onRefresh = {}, onLoadMore = {}, onSignIn = {})
            }
            val before = bounds("post_row_compact-0")
            compose.runOnIdle { clearance.value = 96.dp }
            assertEquals(before, bounds("post_row_compact-0"))
            assertEquals(bounds("home_test_viewport"), bounds("home_feed_list"))
            scrollToEnd()
            val finalBefore = bounds("post_row_compact-8")
            compose.runOnIdle { clearance.value = 0.dp }
            assertEquals(finalBefore, bounds("post_row_compact-8"))
        }
    }

    @Test fun clearanceChangesRetainSuppliedScrollState() {
        val clearance = mutableStateOf(0.dp)
        val list = LazyListState(3, 12)
        val posts = (0..15).map { AppShellFixtures.post("retained-$it", author, "Retained fixture $it") }
        show(LayoutDirection.Ltr) {
            HomeFeed(HomeFeedUiState(posts = posts), compactLayout = false, listState = list,
                rightObstructionClearance = clearance.value, bottomObstructionClearance = clearance.value,
                onRefresh = {}, onLoadMore = {}, onSignIn = {})
        }
        val index = list.firstVisibleItemIndex
        val offset = list.firstVisibleItemScrollOffset
        compose.runOnIdle { clearance.value = 72.dp }
        compose.waitForIdle()
        assertEquals(index, list.firstVisibleItemIndex)
        assertEquals(offset, list.firstVisibleItemScrollOffset)
    }

    @Test
    @Config(qualifiers = "w500dp-h1000dp-420dpi")
    fun homeDestinationForwardsClearanceAndPreservesTimelineSelection() {
        for (direction in LayoutDirection.entries) {
            for (withHome in listOf(true, false)) {
                val navigator = ShellNavigator()
                var refreshed: Timeline? = null
                val post = AppShellFixtures.post("branch", author, "Branch post")
                val home = if (withHome) AppShellFixtures.home(FeedState(posts = listOf(post)), onRefresh = { refreshed = it }) else null
                show(direction) { destination(navigator, home) }
                val viewport = bounds("home_test_viewport")
                val tabs = bounds("home_timeline_tabs")
                assertTrue(chipsRest(direction, false) <= viewport.right - right.value * density + 1f)
                assertTrue(tabs.bottom <= viewport.bottom - bottom.value * density + 1f)
                if (withHome) {
                    assertTrue(bounds("post_row_branch").right <= viewport.right - right.value * density + 1f)
                    scrollToEnd()
                    val footer = compose.onNodeWithText(compose.activity.getString(R.string.feed_up_to_date)).fetchSemanticsNode().boundsInRoot
                    assertTrue(footer.bottom <= tabs.top)
                }
                val federatedLabel = compose.activity.getString(R.string.timeline_federated)
                compose.onNodeWithTag("home_timeline_tabs").performScrollToNode(hasText(federatedLabel))
                assertClicksClearRight(viewport.right - right.value * density)
                val description = compose.activity.getString(R.string.large_timeline, federatedLabel)
                compose.onNodeWithContentDescription(description).assertIsDisplayed().performClick()
                compose.onNodeWithContentDescription(description).assertIsSelected()
                assertEquals(Timeline.Federated, navigator.timeline)
                assertEquals(if (withHome) Timeline.Federated else null, refreshed)
            }
        }
    }

    @Test
    @Config(qualifiers = "w500dp-h1000dp-420dpi")
    fun changedTimelineSelectionScrollsIntoView() {
        val navigator = ShellNavigator()
        show(LayoutDirection.Ltr) { destination(navigator, home = null) }

        compose.runOnIdle { navigator.timeline = Timeline.Federated }
        compose.waitForIdle()

        val label = compose.activity.getString(R.string.timeline_federated)
        val description = compose.activity.getString(R.string.large_timeline, label)
        compose.onNodeWithContentDescription(description).assertIsDisplayed().assertIsSelected()
    }

    private fun scrollToEnd() {
        repeat(16) { compose.onNodeWithTag("home_feed_list").performTouchInput { swipeUp() } }
        compose.waitForIdle()
    }

    @Composable private fun destination(navigator: ShellNavigator, home: HomeContract?) {
        ShellDestinationContent(
            paneModifier = Modifier.fillMaxSize(), navigator = navigator, overlay = ShellOverlayPresenter(),
            screenStates = rememberSaveableStateHolder(), homeListState = rememberLazyListState(),
            searchListState = rememberLazyListState(), photoGridScrollState = rememberLazyStaggeredGridState(),
            profileListState = rememberLazyListState(), largePresentation = true,
            rightObstructionClearance = right, bottomObstructionClearance = bottom,
            account = author, displayedProfile = author, savedTitle = R.string.app_name,
            notificationAccountIdentity = "fixture", availableTimelines = timelineDisplayOrder.toSet(),
            sessionRevision = 0L, home = home, photoGrid = PhotoGridContract.Empty,
            profile = AppShellFixtures.profile(), search = AppShellFixtures.search(FeedState()),
            bookmarks = AppShellFixtures.bookmarks(), notifications = AppShellFixtures.notifications(),
            directMessages = DirectMessagesContract.Empty, accountSwitcher = AppShellFixtures.switcher(),
            postCallbacks = DestinationPostCallbacks(emptySet(), false, {}, {}, {}, {}, { _, _ -> }, {}),
            draftCallbacks = DestinationDraftCallbacks(emptyList(), {}, {}),
            navigationCallbacks = DestinationNavigationCallbacks({ _, _ -> }, {}, {}),
        )
    }
}

private fun SemanticsNode.isChipRowEntry() =
    parent?.config?.contains(SemanticsProperties.SelectableGroup) == true
