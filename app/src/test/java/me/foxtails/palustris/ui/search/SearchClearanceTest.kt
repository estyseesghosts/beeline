package me.foxtails.palustris.ui.search

import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
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
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.Reaction
import me.foxtails.palustris.domain.timelineDisplayOrder
import me.foxtails.palustris.ui.feed.FeedState
import me.foxtails.palustris.ui.navigation.ShellNavigator
import me.foxtails.palustris.ui.shell.AppShellFixtures
import me.foxtails.palustris.ui.shell.Destination
import me.foxtails.palustris.ui.shell.DestinationDraftCallbacks
import me.foxtails.palustris.ui.shell.DestinationNavigationCallbacks
import me.foxtails.palustris.ui.shell.DestinationPostCallbacks
import me.foxtails.palustris.ui.shell.DirectMessagesContract
import me.foxtails.palustris.ui.shell.PhotoGridContract
import me.foxtails.palustris.ui.shell.SearchContract
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

/**
 * Wide Search obstruction-clearance coverage.
 *
 * Synthetic clearance values are test inputs. They are not production measurements.
 * Physical-right assertions use the physical edge in both layout directions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchClearanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val author = AppShellFixtures.account()
    private val matched = AppShellFixtures.account("matched")
    private val right = 72.dp
    private val bottom = 96.dp
    private val wideRight = 120.dp
    private val density get() = compose.activity.resources.displayMetrics.density

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

    private fun described(description: Int): Rect = compose
        .onNodeWithContentDescription(compose.activity.getString(description))
        .fetchSemanticsNode().boundsInRoot

    private fun fieldBounds() = described(R.string.search_field)
    private fun chipsBounds() = described(R.string.search_categories_description)
    private fun text(@StringRes label: Int) = compose.activity.getString(label)

    private fun show(direction: LayoutDirection, content: @Composable () -> Unit) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                key(direction) {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        Box(Modifier.fillMaxSize().testTag("search_test_viewport")) { content() }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertClicksClearRight(safeRight: Float) {
        val nodes = compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("fixture must exercise interactions", nodes.isNotEmpty())
        nodes.filter { it.boundsInRoot.width > 0 && it.boundsInRoot.height > 0 }.forEach {
            assertTrue("click target ${it.config} crosses physical right: ${it.boundsInRoot}",
                it.boundsInRoot.right <= safeRight + 1f)
        }
    }

    private fun scrollToEnd(tag: String) {
        repeat(16) { compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { swipeUp() } }
        compose.waitForIdle()
    }

    private fun posts(count: Int, label: String) = (0 until count).map {
        AppShellFixtures.post("$label-$it", author, "Search $label fixture $it")
    }

    @Test fun wideSearchRowsAndDockClearPhysicalLeft() {
        val result = AppShellFixtures.post("left", author, "Search left")
        for (direction in LayoutDirection.entries) {
            show(direction) {
                SearchScreen(
                    accountSearch = AccountSearchState(query = "#fixture", tagQuery = "fixture", posts = listOf(result)),
                    compactLayout = false, largeLayout = true, sharedQuery = "#fixture", sharedTab = 0,
                    leftObstructionClearance = right, mediaOwner = author.id,
                    availableActions = setOf(PostAction.Reply, PostAction.Favorite),
                )
            }
            val viewport = bounds("search_test_viewport")
            assertEquals(viewport, bounds("search_content"))
            assertEquals(viewport.left, bounds("search_divider_left").left, 1f)
            val safeLeft = viewport.left + right.value * density
            assertEquals(safeLeft, bounds("post_row_left").left, 1f)
            assertTrue(fieldBounds().left >= safeLeft - 1f)
            assertTrue(chipsBounds().left >= safeLeft - 1f)
            compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
                .filter { it.boundsInRoot.width > 0 && it.boundsInRoot.height > 0 }.forEach {
                    assertTrue("Search interaction clears physical left", it.boundsInRoot.left >= safeLeft - 1f)
                }
        }
    }

    @Test fun wideResultRowAndDividerKeepFullViewportAndClearPhysicalRight() {
        val result = AppShellFixtures.post("wide-row", author, "Interactive search body #fixture").copy(
            attachments = listOf(Attachment("https://fixture.example/photo.jpg", "image/jpeg", "Fixture photo")),
            reactions = listOf(Reaction("👍", 2, false)),
        )
        for (direction in LayoutDirection.entries) {
            var replies = 0
            var favourites = 0
            show(direction) {
                SearchScreen(
                    accountSearch = AccountSearchState(query = "#fixture", tagQuery = "fixture", posts = listOf(result)),
                    compactLayout = false, largeLayout = true,
                    sharedQuery = "#fixture", sharedTab = 0,
                    rightObstructionClearance = right, bottomObstructionClearance = bottom,
                    availableActions = setOf(PostAction.Reply, PostAction.Favorite, PostAction.Bookmark),
                    mediaOwner = author.id,
                    onReply = { replies++ }, onReact = { favourites++ },
                )
            }
            val viewport = bounds("search_test_viewport")
            assertEquals("Search content keeps the full viewport", viewport, bounds("search_content"))
            assertEquals("result list keeps the full viewport", viewport, bounds("search_hashtag_results"))
            val safeRight = viewport.right - right.value * density
            assertEquals("row keeps the physical left edge", viewport.left, bounds("post_row_wide-row").left, 1f)
            assertEquals("interaction content clears physical right", safeRight, bounds("post_row_wide-row").right, 1f)
            val divider = bounds("search_divider_wide-row")
            assertEquals("divider underlay keeps the physical left edge", viewport.left, divider.left, 1f)
            assertEquals("divider underlay stays full width", viewport.right, divider.right, 1f)
            compose.onNodeWithContentDescription(text(R.string.post_reply)).performClick()
            compose.onNodeWithContentDescription(text(R.string.post_action_favorite)).performClick()
            assertEquals(1, replies)
            assertEquals(1, favourites)
            assertClicksClearRight(safeRight)
        }
    }

    @Test fun wideFinalResultAndContinuationClearDockAndBottomObstruction() {
        for (direction in LayoutDirection.entries) {
            var loaded = 0
            show(direction) {
                SearchScreen(
                    accountSearch = AccountSearchState(
                        query = "#fixture", tagQuery = "fixture",
                        posts = posts(12, "final"), nextCursor = "next",
                    ),
                    compactLayout = false, largeLayout = true,
                    sharedQuery = "#fixture", sharedTab = 0,
                    rightObstructionClearance = right, bottomObstructionClearance = bottom,
                    onLoadMoreSearch = { loaded++ },
                )
            }
            val viewport = bounds("search_test_viewport")
            val dock = bounds("search_dock")
            val safeRight = viewport.right - right.value * density
            assertTrue("wide dock clears physical right", dock.right <= safeRight + 1f)
            assertEquals("wide dock stays at the viewport bottom", viewport.bottom, dock.bottom, 1f)
            scrollToEnd("search_hashtag_results")
            val last = bounds("post_row_final-11")
            assertTrue("final result clears the dock", last.bottom <= dock.top)
            assertTrue("final result clears bottom obstruction", last.bottom <= viewport.bottom - bottom.value * density + 1f)
            val footer = compose.onNodeWithText(text(R.string.search_load_older)).fetchSemanticsNode().boundsInRoot
            assertTrue("continuation clears the dock", footer.bottom <= dock.top)
            assertTrue("continuation clears bottom obstruction", footer.bottom <= viewport.bottom - bottom.value * density + 1f)
            assertTrue("continuation clears physical right", footer.right <= safeRight + 1f)
            assertClicksClearRight(safeRight)
            compose.onNodeWithText(text(R.string.search_load_older)).assertIsDisplayed().performClick()
            assertEquals(1, loaded)
        }
    }

    @Test fun wideAccountResultsKeepFullViewportAndClearRowClickBounds() {
        // A spaced query is not an exact hashtag, so the account pane renders.
        val query = "fixture owner"
        for (direction in LayoutDirection.entries) {
            var opened: Account? = null
            val clearance = mutableStateOf(0.dp)
            show(direction) {
                SearchScreen(
                    accountSearch = AccountSearchState(query = query, accounts = listOf(matched)),
                    compactLayout = false, largeLayout = true,
                    sharedQuery = query, sharedTab = 0,
                    rightObstructionClearance = clearance.value, bottomObstructionClearance = bottom,
                    onAccountClick = { opened = it },
                )
            }
            val viewport = bounds("search_test_viewport")
            val safeRight = viewport.right - right.value * density
            assertTrue("fixture must cross physical right without clearance",
                bounds("search_account_row_matched").right > safeRight)
            compose.runOnIdle { clearance.value = right }
            compose.waitForIdle()
            assertEquals("account content keeps the full viewport", viewport, bounds("search_content"))
            assertEquals("account list keeps the full viewport", viewport, bounds("search_account_results"))
            assertEquals("account row keeps its existing start inset",
                viewport.left + 8 * density, bounds("search_account_row_matched").left, 1f)
            assertTrue("account row clears physical right",
                bounds("search_account_row_matched").right <= safeRight + 1f)
            assertClicksClearRight(safeRight)
            compose.onNodeWithTag("search_account_row_matched", useUnmergedTree = true).performClick()
            assertEquals(matched.id, opened?.id)
        }
    }

    @Test
    @Config(qualifiers = "w600dp-h800dp-420dpi")
    fun wideDockClearsPhysicalRightWithoutMovingOrResizingAndKeepsChipsSelectable() {
        for (direction in LayoutDirection.entries) {
            val selectedTab = mutableStateOf(0)
            val clearance = mutableStateOf(0.dp)
            show(direction) {
                SearchScreen(
                    compactLayout = false, largeLayout = true,
                    sharedQuery = "", sharedTab = selectedTab.value,
                    rightObstructionClearance = clearance.value,
                    onSharedTabChange = { selectedTab.value = it },
                )
            }
            val viewport = bounds("search_test_viewport")
            val safeRight = viewport.right - wideRight.value * density
            val unmeasuredDock = bounds("search_dock")
            assertTrue("fixture must cross physical right without clearance", fieldBounds().right > safeRight)
            compose.runOnIdle { clearance.value = wideRight }
            compose.waitForIdle()
            val dock = bounds("search_dock")
            assertEquals("clearance must not resize the measured dock", unmeasuredDock.height, dock.height, 1f)
            assertEquals("clearance must not move the dock", unmeasuredDock.top, dock.top, 1f)
            assertEquals("clearance must not move the dock", unmeasuredDock.bottom, dock.bottom, 1f)
            assertTrue("dock clears physical right", dock.right <= safeRight + 1f)
            assertTrue("chip row clears physical right", chipsBounds().right <= safeRight + 1f)
            assertTrue("search field clears physical right", fieldBounds().right <= safeRight + 1f)
            assertClicksClearRight(safeRight)
            compose.onNodeWithContentDescription(text(R.string.search_categories_description)).assert(hasScrollAction())
            val lastCategory = text(R.string.search_category_for_you)
            compose.onNodeWithContentDescription(text(R.string.search_categories_description))
                .performScrollToNode(hasText(lastCategory))
            assertTrue("reachable chip clears physical right",
                compose.onNodeWithText(lastCategory).fetchSemanticsNode().boundsInRoot.right <= safeRight + 1f)
            compose.onNodeWithText(lastCategory).assertIsDisplayed().performClick()
            compose.onNodeWithText(lastCategory).assertIsSelected()
            assertEquals(3, selectedTab.value)
            assertEquals("Search content keeps the full viewport", viewport, bounds("search_content"))
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h1000dp-420dpi")
    fun wideFieldKeepsImePositionAndDockWhenBottomClearanceChanges() {
        for (direction in LayoutDirection.entries) {
            val clearance = mutableStateOf(0.dp)
            show(direction) {
                SearchScreen(
                    accountSearch = AccountSearchState(query = "#fixture", tagQuery = "fixture", posts = posts(12, "ime")),
                    compactLayout = false, largeLayout = true,
                    rightObstructionClearance = right, bottomObstructionClearance = clearance.value,
                )
            }
            compose.onNode(hasSetTextAction()).performTextInput("#fixture")
            var viewportTop = Float.NaN
            var viewportBottom = Float.NaN
            val fieldBottoms = mutableListOf<Float>()
            for (keyboardDp in listOf(0, 360, 0)) {
                applyKeyboardInsets(keyboardDp)
                compose.runOnIdle { clearance.value = 0.dp }
                compose.waitForIdle()
                val viewport = bounds("search_content")
                if (viewportTop.isNaN()) {
                    viewportTop = viewport.top
                    viewportBottom = viewport.bottom
                } else {
                    assertEquals("IME motion must not resize the Search viewport", viewportTop, viewport.top, 0.5f)
                    assertEquals("IME motion must not resize the Search viewport", viewportBottom, viewport.bottom, 0.5f)
                }
                val dockBefore = bounds("search_dock")
                val fieldBefore = fieldBounds()
                compose.runOnIdle { clearance.value = 96.dp }
                compose.waitForIdle()
                assertEquals("bottom clearance must not move the dock", dockBefore, bounds("search_dock"))
                assertEquals("bottom clearance must not move the field", fieldBefore, fieldBounds())
                assertTrue("field clears physical right at IME $keyboardDp in $direction",
                    fieldBounds().right <= viewport.right - right.value * density + 1f)
                compose.onNodeWithContentDescription(text(R.string.search_field))
                    .assertIsDisplayed().assertTextContains("#fixture", substring = true)
                fieldBottoms += fieldBounds().bottom
                scrollToEnd("search_hashtag_results")
                assertTrue("final result clears the dock at IME $keyboardDp",
                    bounds("post_row_ime-11").bottom <= bounds("search_dock").top)
            }
            assertEquals("wide Search applies no IME field inset", fieldBottoms[0], fieldBottoms[1], 1f)
            assertEquals("wide Search keeps its field position after IME close", fieldBottoms[0], fieldBottoms[2], 1f)
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-420dpi")
    fun compactSearchIgnoresWideClearanceThroughImeChanges() {
        val query = "fixture owner"
        for (direction in LayoutDirection.entries) {
            for (navigationVisible in listOf(true, false)) {
                val clearance = mutableStateOf(0.dp)
                show(direction) {
                    SearchScreen(
                        accountSearch = AccountSearchState(query = query, accounts = listOf(matched)),
                        compactLayout = true, compactNavigationVisible = navigationVisible,
                        rightObstructionClearance = clearance.value,
                        bottomObstructionClearance = clearance.value,
                    )
                }
                compose.onNode(hasSetTextAction()).performTextInput(query)
                val fieldBottoms = mutableListOf<Float>()
                for (keyboardDp in listOf(0, 360, 0)) {
                    applyKeyboardInsets(keyboardDp)
                    compose.runOnIdle { clearance.value = 0.dp }
                    compose.waitForIdle()
                    val content = bounds("search_content")
                    val accounts = bounds("search_account_results")
                    val chips = chipsBounds()
                    val field = fieldBounds()
                    compose.runOnIdle { clearance.value = 96.dp }
                    compose.waitForIdle()
                    assertEquals("compact content keeps the full viewport", content, bounds("search_content"))
                    assertEquals("compact account list keeps the full viewport", accounts, bounds("search_account_results"))
                    assertEquals("compact chip row ignores wide inputs", chips, chipsBounds())
                    assertEquals("compact field ignores wide inputs", field, fieldBounds())
                    compose.onNodeWithContentDescription(text(R.string.search_field))
                        .assertIsDisplayed().assertTextContains(query, substring = true)
                    fieldBottoms += fieldBounds().bottom
                    compose.runOnIdle { clearance.value = 0.dp }
                    compose.waitForIdle()
                }
                assertEquals("compact IME keeps the shared control gap",
                    336f * density, fieldBottoms[0] - fieldBottoms[1], 1f)
                assertEquals("compact IME dismissal restores the field position",
                    fieldBottoms[0], fieldBottoms[2], 1f)
            }
        }
    }

    @Test
    @Config(qualifiers = "w600dp-h1000dp-420dpi")
    fun searchDestinationForwardsClearanceAndKeepsQueryAndCategoryState() {
        for (direction in LayoutDirection.entries) {
            val navigator = ShellNavigator()
            navigator.destination = Destination.Search
            navigator.searchQuery = "#fixture"
            show(direction) {
                destination(navigator, AppShellFixtures.search(
                    FeedState(
                        accountSearch = AccountSearchState(
                            query = "#fixture", tagQuery = "fixture", posts = posts(10, "branch"),
                        ),
                    ),
                ))
            }
            val viewport = bounds("search_test_viewport")
            val safeRight = viewport.right - wideRight.value * density
            assertTrue("branch dock clears physical right", bounds("search_dock").right <= safeRight + 1f)
            assertTrue("branch chip row clears physical right", chipsBounds().right <= safeRight + 1f)
            assertTrue("branch field clears physical right", fieldBounds().right <= safeRight + 1f)
            assertTrue("branch result clears physical right", bounds("post_row_branch-0").right <= safeRight + 1f)
            assertEquals("branch content keeps the full viewport", viewport, bounds("search_content"))
            assertClicksClearRight(safeRight)
            compose.onNodeWithText(text(R.string.search_category_hashtags)).assertIsDisplayed().performClick()
            assertEquals(1, navigator.searchCategory)
            assertEquals("#fixture", navigator.searchQuery)
            compose.onNodeWithText(text(R.string.search_category_profiles)).assertIsDisplayed().performClick()
            assertEquals(0, navigator.searchCategory)
            assertEquals("#fixture", navigator.searchQuery)
            val dock = bounds("search_dock")
            scrollToEnd("search_hashtag_results")
            assertTrue("branch final result clears the dock", bounds("post_row_branch-9").bottom <= dock.top)
            assertTrue("branch final result clears bottom obstruction",
                bounds("post_row_branch-9").bottom <= viewport.bottom - bottom.value * density + 1f)
        }
    }

    @Test fun clearanceChangesRetainSuppliedListState() {
        val list = LazyListState(3, 12)
        val clearance = mutableStateOf(0.dp)
        show(LayoutDirection.Ltr) {
            SearchScreen(
                accountSearch = AccountSearchState(query = "#fixture", tagQuery = "fixture", posts = posts(16, "retained")),
                compactLayout = false, largeLayout = true,
                sharedQuery = "#fixture", sharedTab = 0, listState = list,
                rightObstructionClearance = clearance.value, bottomObstructionClearance = clearance.value,
            )
        }
        val index = list.firstVisibleItemIndex
        val offset = list.firstVisibleItemScrollOffset
        compose.runOnIdle { clearance.value = 72.dp }
        compose.waitForIdle()
        assertEquals(index, list.firstVisibleItemIndex)
        assertEquals(offset, list.firstVisibleItemScrollOffset)
    }

    private fun applyKeyboardInsets(keyboardDp: Int) {
        val density = compose.activity.resources.displayMetrics.density
        val systemBottom = (24 * density).toInt()
        compose.runOnIdle {
            val content = compose.activity.findViewById<ViewGroup>(android.R.id.content)
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, systemBottom))
                .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, systemBottom))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, (keyboardDp * density).toInt()))
                .setVisible(WindowInsetsCompat.Type.ime(), keyboardDp > 0)
                .build()
            ViewCompat.dispatchApplyWindowInsets(content.getChildAt(0), insets)
        }
        compose.waitForIdle()
    }

    @Composable
    private fun destination(navigator: ShellNavigator, search: SearchContract) {
        ShellDestinationContent(
            paneModifier = Modifier.fillMaxSize(), navigator = navigator, overlay = ShellOverlayPresenter(),
            screenStates = rememberSaveableStateHolder(), homeListState = rememberLazyListState(),
            searchListState = rememberLazyListState(), photoGridScrollState = rememberLazyStaggeredGridState(),
            profileListState = rememberLazyListState(), largePresentation = true,
            rightObstructionClearance = wideRight, bottomObstructionClearance = bottom,
            account = author, displayedProfile = author, savedTitle = R.string.app_name,
            notificationAccountIdentity = "fixture", availableTimelines = timelineDisplayOrder.toSet(),
            sessionRevision = 0L, home = null, photoGrid = PhotoGridContract.Empty,
            profile = AppShellFixtures.profile(), search = search,
            bookmarks = AppShellFixtures.bookmarks(), notifications = AppShellFixtures.notifications(),
            directMessages = DirectMessagesContract.Empty, accountSwitcher = AppShellFixtures.switcher(),
            postCallbacks = DestinationPostCallbacks(emptySet(), false, {}, {}, {}, {}, { _, _ -> }, {}),
            draftCallbacks = DestinationDraftCallbacks(emptyList(), {}, {}),
            navigationCallbacks = DestinationNavigationCallbacks({ _, _ -> }, {}, {}),
        )
    }
}
