package me.foxtails.palustris.ui.photogrid

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
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
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.timelineDisplayOrder
import me.foxtails.palustris.ui.PalustrisTheme
import me.foxtails.palustris.ui.feed.FeedState
import me.foxtails.palustris.ui.navigation.ShellNavigator
import me.foxtails.palustris.ui.shell.AppShellFixtures
import me.foxtails.palustris.ui.shell.Destination
import me.foxtails.palustris.ui.shell.DestinationDraftCallbacks
import me.foxtails.palustris.ui.shell.DestinationNavigationCallbacks
import me.foxtails.palustris.ui.shell.DestinationPostCallbacks
import me.foxtails.palustris.ui.shell.DirectMessagesContract
import me.foxtails.palustris.ui.shell.PhotoGridContract
import me.foxtails.palustris.ui.shell.SearchPanel
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
 * Wide Photo Grid obstruction-clearance coverage.
 *
 * Synthetic clearance values are test inputs. They are not production measurements.
 * Physical-right assertions use the physical edge in both layout directions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoGridClearanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val author = AppShellFixtures.account()
    private val right = 72.dp
    private val bottom = 96.dp
    private val density get() = compose.activity.resources.displayMetrics.density

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

    private fun described(description: Int): Rect = compose
        .onNodeWithContentDescription(compose.activity.getString(description))
        .fetchSemanticsNode().boundsInRoot

    private fun chipsBounds() = described(R.string.photo_grid_filter_description)

    /** Mirrors the production tile key: origin, post connection, post id, and attachment id. */
    private fun tileKey(postId: String) =
        "${connection.origin}/${connection.origin}/$postId/$postId-image"

    private fun tileBounds(postId: String) = bounds("photo_grid_tile_${tileKey(postId)}")

    private fun text(label: Int) = compose.activity.getString(label)

    private fun show(direction: LayoutDirection, content: @Composable () -> Unit) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    key(direction) {
                        CompositionLocalProvider(LocalLayoutDirection provides direction) {
                            Box(Modifier.fillMaxSize().testTag("photo_grid_test_viewport")) { content() }
                        }
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

    private fun scrollToEnd() {
        repeat(16) { compose.onNodeWithTag("photo_grid_content", useUnmergedTree = true).performTouchInput { swipeUp() } }
        compose.waitForIdle()
    }

    private fun post(id: String, sensitive: Boolean = false) = Post(
        id = EntityId(connection.origin, id),
        author = author,
        text = "Photo Grid fixture $id",
        publishedAtEpochMillis = 0,
        audience = Audience.Public,
        attachments = listOf(
            Attachment(
                id = "$id-image",
                url = "https://fixture.example/$id.jpg",
                previewUrl = "https://fixture.example/$id-preview.jpg",
                mimeType = "image/jpeg",
                kind = MediaKind.Image,
                width = 640,
                height = 480,
                sensitive = sensitive,
            ),
        ),
    )

    private fun posts(count: Int, label: String) =
        (0 until count).map { AppShellFixtures.owned(author, post("$label-$it")) }

    private fun state(
        label: String,
        count: Int = 0,
        nextCursor: String? = null,
        error: String? = null,
    ) = PhotoGridFeedState(
        selectedFeed = PhotoGridFeed.TimelineFeed(Timeline.Home),
        availableTimelines = timelineDisplayOrder,
        savedHashtags = listOf("fixture"),
        posts = posts(count, label),
        initialLoadComplete = true,
        nextCursor = nextCursor,
        error = error,
    )

    @Test fun wideTilesKeepFullViewportAndClearPhysicalRight() {
        for (direction in LayoutDirection.entries) {
            var opened: String? = null
            show(direction) {
                PhotoGridScreen(
                    state = state("wide", count = 6),
                    compactLayout = false,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                    onOpenPost = { opened = it.post.id.value },
                )
            }
            val viewport = bounds("photo_grid_test_viewport")
            assertEquals("grid content keeps the full viewport", viewport, bounds("photo_grid_content"))
            val safeRight = viewport.right - right.value * density
            // The staggered grid distributes tiles across lanes, so no single tile owns the left edge.
            (0 until 6).forEach { index ->
                val tile = tileBounds("wide-$index")
                assertTrue("tile wide-$index starts inside the viewport", tile.left >= viewport.left - 1f)
                assertTrue("tile wide-$index clears physical right", tile.right <= safeRight + 1f)
            }
            assertClicksClearRight(safeRight)
            compose.onNodeWithTag("photo_grid_tile_${tileKey("wide-0")}", useUnmergedTree = true).performClick()
            assertEquals("wide-0", opened)
        }
    }

    @Test fun wideSensitiveTileRevealClearsPhysicalRightAndKeepsItsCallback() {
        for (direction in LayoutDirection.entries) {
            var opened: String? = null
            show(direction) {
                PhotoGridScreen(
                    state = PhotoGridFeedState(
                        selectedFeed = PhotoGridFeed.TimelineFeed(Timeline.Home),
                        availableTimelines = listOf(Timeline.Home),
                        posts = listOf(AppShellFixtures.owned(author, post("sensitive", sensitive = true))),
                        initialLoadComplete = true,
                    ),
                    compactLayout = false,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                    onOpenPost = { opened = it.post.id.value },
                )
            }
            val viewport = bounds("photo_grid_test_viewport")
            val safeRight = viewport.right - right.value * density
            assertClicksClearRight(safeRight)
            compose.onNodeWithText(text(R.string.media_show_sensitive)).assertIsDisplayed().performClick()
            assertClicksClearRight(safeRight)
            compose.onNodeWithContentDescription(text(R.string.post_open)).performClick()
            assertEquals("sensitive", opened)
        }
    }

    @Test fun wideFinalTileAndContinuationClearDockAndBottomObstruction() {
        for (direction in LayoutDirection.entries) {
            var continued = 0
            show(direction) {
                PhotoGridScreen(
                    state = state("final", count = 14, nextCursor = "next"),
                    compactLayout = false,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                    onLoadMore = { continued++ },
                )
            }
            val viewport = bounds("photo_grid_content")
            val safeRight = viewport.right - right.value * density
            val dock = bounds("photo_grid_dock")
            assertTrue("wide dock clears physical right", dock.right <= safeRight + 1f)
            assertTrue("wide dock clears bottom obstruction", dock.bottom <= viewport.bottom - bottom.value * density + 1f)
            scrollToEnd()
            val finalTile = tileBounds("final-13")
            assertTrue("final tile clears the dock", finalTile.bottom <= dock.top)
            assertTrue("final tile clears bottom obstruction",
                finalTile.bottom <= viewport.bottom - bottom.value * density + 1f)
            val continuation = compose.onNodeWithText(text(R.string.photo_grid_load_older))
                .fetchSemanticsNode().boundsInRoot
            assertTrue("continuation clears the dock", continuation.bottom <= dock.top)
            assertTrue("continuation clears physical right", continuation.right <= safeRight + 1f)
            assertClicksClearRight(safeRight)
            compose.onNodeWithText(text(R.string.photo_grid_load_older)).assertIsDisplayed().performClick()
            // Automatic paging stays feature-owned. Manual continuation adds one request.
            assertTrue(continued > 0)
        }
    }

    @Test fun widePagingErrorKeepsViewportAndClearsRetry() {
        for (direction in LayoutDirection.entries) {
            var retried = 0
            show(direction) {
                PhotoGridScreen(
                    state = state("paging", count = 3, nextCursor = "next", error = "Connection failed"),
                    compactLayout = false,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                    onLoadMore = { retried++ },
                )
            }
            val viewport = bounds("photo_grid_test_viewport")
            val safeRight = viewport.right - right.value * density
            assertEquals("grid content keeps the full viewport", viewport, bounds("photo_grid_content"))
            scrollToEnd()
            compose.onNodeWithText(text(R.string.photo_grid_paging_error)).assertIsDisplayed()
            assertClicksClearRight(safeRight)
            compose.onNodeWithText(text(R.string.notifications_retry)).performClick()
            assertEquals(1, retried)
        }
    }

    @Test fun wideEmptyAndLoadingStatesKeepFullViewportAndClearText() {
        for (direction in LayoutDirection.entries) {
            show(direction) {
                PhotoGridScreen(
                    state = state("empty"),
                    compactLayout = false,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                )
            }
            val viewport = bounds("photo_grid_test_viewport")
            val safeRight = viewport.right - right.value * density
            assertEquals("grid content keeps the full viewport", viewport, bounds("photo_grid_content"))
            val title = compose.onNodeWithText(text(R.string.photo_grid_empty_title)).fetchSemanticsNode().boundsInRoot
            assertTrue("empty title clears physical right", title.right <= safeRight + 1f)
            show(direction) {
                PhotoGridScreen(
                    state = state("loading").copy(loading = true),
                    compactLayout = false,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                )
            }
            assertEquals("loading state keeps the full viewport", viewport, bounds("photo_grid_content"))
        }
    }

    @Test
    @Config(qualifiers = "w600dp-h800dp-420dpi")
    fun wideDockAndChipsClearPhysicalRightAndKeepSelectionCallbacks() {
        for (direction in LayoutDirection.entries) {
            val clearance = mutableStateOf(0.dp)
            var selected: String? = null
            show(direction) {
                PhotoGridScreen(
                    state = PhotoGridFeedState(
                        selectedFeed = PhotoGridFeed.TimelineFeed(Timeline.Home),
                        availableTimelines = timelineDisplayOrder,
                        savedHashtags = listOf("fixture"),
                    ),
                    compactLayout = false,
                    rightObstructionClearance = clearance.value,
                    bottomObstructionClearance = bottom,
                    onSelectFeed = { selected = it.toString() },
                )
            }
            val viewport = bounds("photo_grid_test_viewport")
            val safeRight = viewport.right - right.value * density
            val unmeasuredDock = bounds("photo_grid_dock")
            assertTrue("fixture must cross physical right without clearance", chipsBounds().right > safeRight)
            compose.runOnIdle { clearance.value = right }
            compose.waitForIdle()
            val dock = bounds("photo_grid_dock")
            assertEquals("clearance must not resize the dock", unmeasuredDock.height, dock.height, 1f)
            assertTrue("dock clears physical right", dock.right <= safeRight + 1f)
            assertTrue("chip row clears physical right", chipsBounds().right <= safeRight + 1f)
            assertClicksClearRight(safeRight)
            compose.onNodeWithContentDescription(text(R.string.photo_grid_filter_description)).assert(hasScrollAction())
            val savedHashtag = "#fixture"
            compose.onNodeWithContentDescription(text(R.string.photo_grid_filter_description))
                .performScrollToNode(hasText(savedHashtag))
            assertTrue("reachable chip clears physical right",
                compose.onNodeWithText(savedHashtag).fetchSemanticsNode().boundsInRoot.right <= safeRight + 1f)
            compose.onNodeWithText(savedHashtag).assertIsDisplayed().performClick()
            assertEquals("Hashtag(tag=fixture)", selected)
            // The add entry is the last chip, so scroll it into view before touching it.
            compose.onNodeWithContentDescription(text(R.string.photo_grid_filter_description))
                .performScrollToNode(hasTestTag("photo_grid_add_hashtag"))
            compose.onNodeWithTag("photo_grid_add_hashtag", useUnmergedTree = true).performClick()
            compose.onNodeWithText(text(R.string.photo_grid_add_hashtag_title)).assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "w600dp-h1000dp-420dpi")
    fun photoGridDestinationForwardsClearanceAndKeepsPanelSelection() {
        for (direction in LayoutDirection.entries) {
            val navigator = ShellNavigator()
            navigator.destination = Destination.Search
            navigator.searchPanelName = SearchPanel.PhotoGrid.name
            val selected = mutableStateOf<PhotoGridFeed>(PhotoGridFeed.TimelineFeed(Timeline.Home))
            val selectedLabel = mutableStateOf<String?>(null)
            show(direction) { destination(navigator, selected, selectedLabel) }
            val viewport = bounds("photo_grid_test_viewport")
            val safeRight = viewport.right - right.value * density
            val gridViewport = bounds("photo_grid_content")
            assertTrue("branch grid clears physical right",
                tileBounds("branch-0").right <= safeRight + 1f)
            assertTrue("branch dock clears physical right", bounds("photo_grid_dock").right <= safeRight + 1f)
            assertTrue("branch chip row clears physical right", chipsBounds().right <= safeRight + 1f)
            assertTrue("branch dock clears bottom obstruction",
                bounds("photo_grid_dock").bottom <= gridViewport.bottom - bottom.value * density + 1f)
            assertClicksClearRight(safeRight)
            val federated = text(R.string.timeline_federated)
            compose.onNodeWithContentDescription(text(R.string.photo_grid_filter_description))
                .performScrollToNode(hasText(federated))
            compose.onNodeWithText(federated).assertIsDisplayed().performClick()
            compose.onNodeWithText(federated).assertIsSelected()
            assertEquals("TimelineFeed(timeline=Federated)", selectedLabel.value)
            assertEquals(PhotoGridFeed.TimelineFeed(Timeline.Federated), selected.value)
            assertEquals(SearchPanel.PhotoGrid, navigator.searchPanel)
            scrollToEnd()
            val finalTile = tileBounds("branch-9")
            assertTrue("branch final tile clears the dock", finalTile.bottom <= bounds("photo_grid_dock").top)
            assertTrue("branch final tile clears bottom obstruction",
                finalTile.bottom <= gridViewport.bottom - bottom.value * density + 1f)
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-420dpi")
    fun compactPhotoGridIgnoresWideClearance() {
        for (direction in LayoutDirection.entries) {
            for (navigationVisible in listOf(true, false)) {
                val clearance = mutableStateOf(0.dp)
                show(direction) {
                    PhotoGridScreen(
                        state = state("compact", count = 6, nextCursor = "next"),
                        compactLayout = true,
                        compactNavigationVisible = navigationVisible,
                        rightObstructionClearance = clearance.value,
                        bottomObstructionClearance = clearance.value,
                    )
                }
                val gridBefore = bounds("photo_grid_content")
                val chipsBefore = chipsBounds()
                val tileBefore = tileBounds("compact-0")
                scrollToEnd()
                val finalBefore = tileBounds("compact-5")
                compose.runOnIdle { clearance.value = 96.dp }
                compose.waitForIdle()
                assertEquals("compact grid ignores wide inputs", gridBefore, bounds("photo_grid_content"))
                assertEquals("compact chip row ignores wide inputs", chipsBefore, chipsBounds())
                assertEquals("compact tile ignores wide inputs", tileBefore, tileBounds("compact-0"))
                assertEquals("compact final tile ignores wide inputs", finalBefore, tileBounds("compact-5"))
            }
        }
    }

    @Test fun clearanceChangesRetainSuppliedGridState() {
        val grid = LazyStaggeredGridState(2, 24)
        val clearance = mutableStateOf(0.dp)
        show(LayoutDirection.Ltr) {
            PhotoGridScreen(
                state = state("retained", count = 20),
                compactLayout = false,
                rightObstructionClearance = clearance.value,
                bottomObstructionClearance = clearance.value,
                gridState = grid,
            )
        }
        val index = grid.firstVisibleItemIndex
        val offset = grid.firstVisibleItemScrollOffset
        compose.runOnIdle { clearance.value = 72.dp }
        compose.waitForIdle()
        assertEquals(index, grid.firstVisibleItemIndex)
        assertEquals(offset, grid.firstVisibleItemScrollOffset)
    }

    @Composable
    private fun destination(
        navigator: ShellNavigator,
        selected: MutableState<PhotoGridFeed>,
        selectedLabel: MutableState<String?>,
    ) {
        ShellDestinationContent(
            paneModifier = Modifier.fillMaxSize(), navigator = navigator, overlay = ShellOverlayPresenter(),
            screenStates = rememberSaveableStateHolder(), homeListState = rememberLazyListState(),
            searchListState = rememberLazyListState(), photoGridScrollState = rememberLazyStaggeredGridState(),
            profileListState = rememberLazyListState(), largePresentation = true,
            rightObstructionClearance = right, bottomObstructionClearance = bottom,
            account = author, displayedProfile = author, savedTitle = R.string.app_name,
            notificationAccountIdentity = "fixture", availableTimelines = timelineDisplayOrder.toSet(),
            sessionRevision = 0L, home = null, photoGrid = photoGrid(selected, selectedLabel),
            profile = AppShellFixtures.profile(), search = AppShellFixtures.search(FeedState()),
            bookmarks = AppShellFixtures.bookmarks(), notifications = AppShellFixtures.notifications(),
            directMessages = DirectMessagesContract.Empty, accountSwitcher = AppShellFixtures.switcher(),
            postCallbacks = DestinationPostCallbacks(emptySet(), false, {}, {}, {}, {}, { _, _ -> }, {}),
            draftCallbacks = DestinationDraftCallbacks(emptyList(), {}, {}),
            navigationCallbacks = DestinationNavigationCallbacks({ _, _ -> }, {}, {}),
        )
    }

    private fun photoGrid(
        selected: MutableState<PhotoGridFeed>,
        selectedLabel: MutableState<String?>,
    ) = PhotoGridContract(
        state = PhotoGridFeedState(
            selectedFeed = selected.value,
            availableTimelines = timelineDisplayOrder,
            posts = posts(10, "branch"),
            initialLoadComplete = true,
        ),
        actions = object : PhotoGridContract.Actions {
            override fun ensureLoaded() = Unit
            override fun selectFeed(feed: PhotoGridFeed) {
                selected.value = feed
                selectedLabel.value = feed.toString()
            }
            override fun refresh() = Unit
            override fun loadMore() = Unit
            override fun addHashtag(value: String, onSuccess: () -> Unit) = onSuccess()
            override fun clearPreferenceError() = Unit
        },
    )

    private val connection = Connection("https://fixture.example", Protocol.MASTODON)
}