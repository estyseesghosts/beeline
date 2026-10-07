package me.foxtails.palustris.ui.profile

import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.ProfileField
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.ProfileTimelineTab
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.timelineDisplayOrder
import me.foxtails.palustris.ui.PalustrisTheme
import me.foxtails.palustris.ui.components.restingChipEdge
import me.foxtails.palustris.ui.feed.FeedState
import me.foxtails.palustris.ui.navigation.ShellNavigator
import me.foxtails.palustris.ui.shell.AppShellFixtures
import me.foxtails.palustris.ui.shell.Destination
import me.foxtails.palustris.ui.shell.DestinationDraftCallbacks
import me.foxtails.palustris.ui.shell.DestinationNavigationCallbacks
import me.foxtails.palustris.ui.shell.DestinationPostCallbacks
import me.foxtails.palustris.ui.shell.DirectMessagesContract
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

/**
 * Wide Profile obstruction-clearance coverage.
 *
 * Synthetic clearance values are test inputs. They are not production measurements.
 * Physical-right assertions use the physical edge in both layout directions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileClearanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val connection = Connection("https://fixture.example", Protocol.MASTODON)
    private val viewer = Account(AccountId(connection, "viewer"), "Viewer", "@viewer@fixture.example")
    private val right = 72.dp
    private val bottom = 96.dp
    private val rowActions = setOf(
        PostAction.Reply,
        PostAction.Reshare,
        PostAction.Favorite,
        PostAction.Bookmark,
    )
    private val density get() = compose.activity.resources.displayMetrics.density

    private fun chipsRest(direction: LayoutDirection, physicalLeft: Boolean): Float = compose
        .onNodeWithContentDescription(compose.activity.getString(R.string.a11y_profile_categories))
        .restingChipEdge(compose, direction, physicalLeft)

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

    private fun described(description: Int): Rect = compose
        .onNodeWithContentDescription(compose.activity.getString(description))
        .fetchSemanticsNode().boundsInRoot

    private fun categoriesBounds() = described(R.string.a11y_profile_categories)

    private fun text(@StringRes label: Int) = compose.activity.getString(label)

    private fun show(direction: LayoutDirection, content: @Composable () -> Unit) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                key(direction) {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        PalustrisTheme {
                            Box(Modifier.fillMaxSize().testTag("profile_test_viewport")) { content() }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun safeRight(): Float {
        val viewport = bounds("profile_test_viewport")
        return viewport.right - right.value * density
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

    private fun scrollToEnd() {
        repeat(16) { compose.onNodeWithTag("profile_timeline_list", useUnmergedTree = true).performTouchInput { swipeUp() } }
        compose.waitForIdle()
    }

    private fun profile(id: String) = Account(
        id = AccountId(connection, id),
        displayName = "Profile Name",
        handle = "@profile@fixture.example",
        biography = "Profile biography",
        profileFields = listOf(ProfileField("Website", "https://fixture.example/site")),
    )

    private fun post(id: String, author: Account): Post = Post(
        id = EntityId(connection.origin, id),
        author = author,
        text = "Profile post $id",
        publishedAtEpochMillis = 0,
        audience = Audience.Public,
    )

    private fun state(
        target: Account,
        posts: List<Post> = emptyList(),
        selectedTab: ProfileCategory = ProfileCategory.Posts,
        pinnedPosts: List<Post> = emptyList(),
        nextCursor: String? = null,
        error: String? = null,
        relationship: ProfileRelationship? = null,
        relationshipSupported: Boolean? = null,
    ) = ProfileUiState(
        targetId = target.id,
        seedAccount = target,
        account = target,
        selectedTab = selectedTab,
        pinnedPosts = pinnedPosts.map { OwnedPost(target.id, it) },
        relationship = relationship,
        relationshipSupported = relationshipSupported,
        pages = mapOf(
            ProfileTimelineTab.Posts to ProfilePageState(
                posts = posts.map { OwnedPost(target.id, it) },
                nextCursor = nextCursor,
                error = error,
                terminal = nextCursor == null && error == null,
            ),
        ),
    )

    @Test
    fun wideProfileKeepsFullSizeViewportsWhileRowsAndDockClearPhysicalRight() {
        val target = profile("wide")
        val posts = (0..5).map { post("wide-$it", target) }
        for (direction in LayoutDirection.entries) {
            show(direction) {
                ProfileScreen(
                    account = target,
                    profileState = state(target, posts),
                    compactLayout = false,
                    largeLayout = true,
                    largeShowSummary = false,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                    availableActions = rowActions,
                )
            }
            val viewport = bounds("profile_test_viewport")
            val safeRight = safeRight()
            // Both list viewports keep the full size so visual content can underlay floating chrome.
            val content = bounds("profile_content")
            assertEquals("profile content keeps the full width", viewport.left, content.left, 1f)
            assertEquals("profile content keeps the full width", viewport.right, content.right, 1f)
            assertEquals("profile content reaches the viewport bottom", viewport.bottom, content.bottom, 1f)
            val list = bounds("profile_timeline_list")
            assertEquals("list content keeps the full width", viewport.left, list.left, 1f)
            assertEquals("list content keeps the full width", viewport.right, list.right, 1f)
            assertTrue("profile row clears physical right",
                bounds("post_row_wide-0").right <= safeRight + 1f)
            val divider = bounds("profile_timeline_divider_wide-0")
            assertEquals("item divider underlays future floating chrome", viewport.right, divider.right, 1f)
            assertTrue("item divider underlays future floating chrome", divider.right > safeRight)
            assertTrue("category chips rest clear of physical right", chipsRest(direction, false) <= safeRight + 1f)
            assertTrue("category dock keeps its bottom-start placement",
                bounds("profile_categories_dock").left <= 16f * density + 1f)
            assertClicksClearRight(safeRight)
        }
    }

    @Test
    fun wideSummaryClearsOnlyTheColumnThatReachesPhysicalRight() {
        val target = profile("summary")
        val posts = (0..3).map { post("summary-$it", target) }
        for (direction in LayoutDirection.entries) {
            show(direction) {
                ProfileScreen(
                    account = target,
                    profileState = state(
                        target = target,
                        posts = posts,
                        relationship = ProfileRelationship(profileId = target.id),
                        relationshipSupported = true,
                    ),
                    compactLayout = false,
                    largeLayout = true,
                    largeShowSummary = true,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                    availableActions = rowActions,
                )
            }
            val viewport = bounds("profile_test_viewport")
            val safeRight = safeRight()
            // The summary column clears physical right in RTL, where it reaches the pane edge.
            assertTrue("summary header column clears physical right",
                bounds("profile_header").right <= safeRight + 1f)
            assertTrue("message action clears physical right",
                bounds("profile_message_action").right <= safeRight + 1f)
            assertTrue("follow action clears physical right",
                bounds("profile_follow_action").right <= safeRight + 1f)
            assertTrue("summary category chips rest clear of physical right", chipsRest(direction, false) <= safeRight + 1f)
            val timeline = bounds("profile_timeline_list")
            val row = bounds("post_row_summary-0")
            if (direction == LayoutDirection.Ltr) {
                // The timeline column reaches the pane edge in LTR, so its rows take clearance.
                assertEquals("timeline pane keeps the pane physical right edge", viewport.right, timeline.right, 1f)
                assertTrue("summary profile row clears physical right", row.right <= safeRight + 1f)
            } else {
                // The summary column bounds the timeline column in RTL, so rows keep their width.
                assertTrue("timeline pane is bounded by the summary column", timeline.right < safeRight)
                assertEquals("bounded timeline row keeps its column width", timeline.right, row.right, 1f)
            }
            assertClicksClearRight(safeRight)
        }
    }

    @Test
    fun physicalLeftClearanceFollowsMirroredSummaryAndTimelineColumns() {
        val target = profile("left")
        for (direction in LayoutDirection.entries) {
            for (summary in listOf(false, true)) {
                show(direction) {
                    ProfileScreen(
                        account = target, profileState = state(target, listOf(post("left", target)),
                            relationship = ProfileRelationship(profileId = target.id), relationshipSupported = true),
                        compactLayout = false, largeLayout = true, largeShowSummary = summary,
                        leftObstructionClearance = right, availableActions = rowActions,
                    )
                }
                val viewport = bounds("profile_test_viewport")
                val safeLeft = viewport.left + right.value * density
                if (!summary) assertEquals(viewport, bounds("profile_content"))
                val timeline = bounds("profile_timeline_list")
                val row = bounds("post_row_left")
                if (!summary || direction == LayoutDirection.Rtl) {
                    assertEquals(viewport.left, timeline.left, 1f)
                    assertEquals(safeLeft, row.left, 1f)
                } else {
                    assertEquals("bounded timeline keeps its width", timeline.left, row.left, 1f)
                    assertTrue(bounds("profile_header").left >= safeLeft - 1f)
                }
                assertTrue(chipsRest(direction, true) >= safeLeft - 1f)
                compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
                    .filter { it.boundsInRoot.width > 0 && it.boundsInRoot.height > 0 }.forEach {
                        assertTrue("Profile interaction clears physical left", it.boundsInRoot.left >= safeLeft - 1f)
                    }
            }
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h1000dp-420dpi")
    fun wideFinalRowAndUpToDateFooterClearBottomObstruction() {
        val target = profile("final")
        val posts = (0..11).map { post("final-$it", target) }
        for (direction in LayoutDirection.entries) {
            show(direction) {
                ProfileScreen(
                    account = target,
                    profileState = state(target, posts),
                    compactLayout = false,
                    largeLayout = true,
                    largeShowSummary = false,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                    availableActions = rowActions,
                )
            }
            val viewport = bounds("profile_test_viewport")
            val safeRight = safeRight()
            scrollToEnd()
            val finalRow = bounds("post_row_final-11")
            val upToDate = compose.onNodeWithText(text(R.string.feed_up_to_date))
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("final profile row clears bottom obstruction",
                finalRow.bottom <= viewport.bottom - bottom.value * density + 1f)
            assertTrue("final profile row clears physical right", finalRow.right <= safeRight + 1f)
            assertTrue("up-to-date footer clears bottom obstruction",
                upToDate.bottom <= viewport.bottom - bottom.value * density + 1f)
            assertTrue("up-to-date footer clears physical right", upToDate.right <= safeRight + 1f)
            assertClicksClearRight(safeRight)
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h1000dp-420dpi")
    fun wideLoadOlderControlClearsPhysicalRightAndFires() {
        val target = profile("older")
        val posts = (0..6).map { post("older-$it", target) }
        for (direction in LayoutDirection.entries) {
            var loaded = 0
            show(direction) {
                ProfileScreen(
                    account = target,
                    profileState = state(target, posts, nextCursor = "cursor"),
                    compactLayout = false,
                    largeLayout = true,
                    largeShowSummary = false,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                    availableActions = rowActions,
                    onLoadMore = { loaded++ },
                )
            }
            val viewport = bounds("profile_test_viewport")
            val safeRight = safeRight()
            scrollToEnd()
            val loadOlder = compose.onNodeWithText(text(R.string.feed_load_older))
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("load-older clears physical right", loadOlder.right <= safeRight + 1f)
            assertTrue("load-older clears bottom obstruction",
                loadOlder.bottom <= viewport.bottom - bottom.value * density + 1f)
            assertClicksClearRight(safeRight)
            val before = loaded
            compose.onNodeWithText(text(R.string.feed_load_older)).performClick()
            assertTrue("load-older callback fires", loaded > before)
        }
    }

    @Test
    fun wideErrorAndEmptyStatesClearPhysicalRight() {
        val target = profile("states")
        for (direction in LayoutDirection.entries) {
            var refreshed = 0
            show(direction) {
                ProfileScreen(
                    account = target,
                    profileState = state(target, error = "Fixture timeline error"),
                    compactLayout = false,
                    largeLayout = true,
                    largeShowSummary = false,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                    onRefresh = { refreshed++ },
                )
            }
            val safeRight = safeRight()
            assertTrue("timeline error text clears physical right",
                compose.onNodeWithText("Fixture timeline error")
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot.right <= safeRight + 1f)
            assertTrue("timeline retry clears physical right",
                compose.onNodeWithText(text(R.string.notifications_retry))
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot.right <= safeRight + 1f)
            assertClicksClearRight(safeRight)
            compose.onNodeWithText(text(R.string.notifications_retry)).performClick()
            assertEquals("timeline retry callback fires", 1, refreshed)

            show(direction) {
                ProfileScreen(
                    account = target,
                    profileState = state(target),
                    compactLayout = false,
                    largeLayout = true,
                    largeShowSummary = false,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                )
            }
            assertTrue("empty timeline title clears physical right",
                compose.onNodeWithText(text(R.string.profile_no_posts_title))
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot.right <= safeRight + 1f)

            show(direction) {
                ProfileScreen(
                    account = null,
                    profileState = ProfileUiState(),
                    compactLayout = false,
                    largeLayout = true,
                    largeShowSummary = false,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                )
            }
            assertTrue("empty profile state clears physical right",
                compose.onNodeWithText(text(R.string.profile_empty_title))
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot.right <= safeRight + 1f)
        }
    }

    @Test
    fun widePinnedTitleAndPinnedRowsClearPhysicalRight() {
        val target = profile("pinned")
        val pinned = (0..1).map { post("pinned-$it", target) }
        for (direction in LayoutDirection.entries) {
            show(direction) {
                ProfileScreen(
                    account = target,
                    profileState = state(target, selectedTab = ProfileCategory.Featured, pinnedPosts = pinned),
                    compactLayout = false,
                    largeLayout = true,
                    largeShowSummary = false,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                    availableActions = rowActions,
                )
            }
            val viewport = bounds("profile_test_viewport")
            val safeRight = safeRight()
            assertTrue("Featured title clears physical right",
                compose.onNodeWithText(text(R.string.profile_featured_posts))
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot.right <= safeRight + 1f)
            assertTrue("pinned row clears physical right",
                bounds("post_row_pinned-0").right <= safeRight + 1f)
            assertEquals("pinned divider keeps the full width",
                viewport.right, bounds("profile_pinned_divider_pinned-0").right, 1f)
            assertClicksClearRight(safeRight)
        }
    }

    @Test
    fun wideDetailsFieldsClearPhysicalRight() {
        val target = profile("details")
        for (direction in LayoutDirection.entries) {
            show(direction) {
                ProfileScreen(
                    account = target,
                    profileState = state(target, selectedTab = ProfileCategory.ShowMore),
                    compactLayout = false,
                    largeLayout = true,
                    largeShowSummary = false,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                )
            }
            val safeRight = safeRight()
            assertTrue("details field row clears physical right",
                bounds("profile_field_0").right <= safeRight + 1f)
            assertTrue("details title clears physical right",
                compose.onNodeWithText(text(R.string.profile_details))
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot.right <= safeRight + 1f)
            assertClicksClearRight(safeRight)
        }
    }

    @Test
    fun wideInlineCategoryRowClearsPhysicalRightAndKeepsSelection() {
        val target = profile("inline")
        val posts = (0..3).map { post("inline-$it", target) }
        for (direction in LayoutDirection.entries) {
            val tab = mutableStateOf(state(target, posts))
            var selected: ProfileCategory? = null
            show(direction) {
                ProfileScreen(
                    account = target,
                    profileState = tab.value,
                    compactLayout = false,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                    availableActions = rowActions,
                    onCategorySelected = { category ->
                        selected = category
                        tab.value = tab.value.copy(selectedTab = category)
                    },
                )
            }
            val safeRight = safeRight()
            // The inline presentation renders the header inside the list. The shell never selects
            // it, so this test covers the chip row and the timeline rows it owns.
            assertTrue("inline category row clears physical right", chipsRest(direction, false) <= safeRight + 1f)
            assertTrue("timeline row clears physical right", bounds("post_row_inline-0").right <= safeRight + 1f)
            compose.onNodeWithContentDescription(text(R.string.profile_tab_media)).performClick().assertIsSelected()
            assertEquals(ProfileCategory.Media, selected)
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h1000dp-420dpi")
    fun profileDestinationForwardsClearance() {
        val target = profile("branch")
        val posts = (0..8).map { post("branch-$it", target) }
        for (direction in LayoutDirection.entries) {
            val navigator = ShellNavigator()
            navigator.destination = Destination.Profile
            show(direction) { destination(navigator, target, posts) }
            val viewport = bounds("profile_test_viewport")
            val safeRight = safeRight()
            if (direction == LayoutDirection.Ltr) {
                assertEquals("branch list keeps the pane physical right edge",
                    viewport.right, bounds("profile_timeline_list").right, 1f)
                assertTrue("branch row clears physical right",
                    bounds("post_row_branch-0").right <= safeRight + 1f)
            } else {
                assertTrue("branch summary column clears physical right",
                    bounds("profile_header").right <= safeRight + 1f)
            }
            assertTrue("branch category chips rest clear of physical right", chipsRest(direction, false) <= safeRight + 1f)
            assertTrue("branch category dock clears bottom obstruction",
                categoriesBounds().bottom <= viewport.bottom - bottom.value * density + 1f)
            scrollToEnd()
            assertTrue("branch final row clears bottom obstruction",
                bounds("post_row_branch-8").bottom <= viewport.bottom - bottom.value * density + 1f)
            assertClicksClearRight(safeRight)
            assertEquals(Destination.Profile, navigator.destination)
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-420dpi")
    fun compactProfileIgnoresWideClearance() {
        val target = profile("compact")
        val posts = (0..5).map { post("compact-$it", target) }
        for (direction in LayoutDirection.entries) {
            val clearance = mutableStateOf(0.dp)
            show(direction) {
                ProfileScreen(
                    account = target,
                    profileState = state(target, posts),
                    compactLayout = true,
                    compactNavigationVisible = true,
                    rightObstructionClearance = clearance.value,
                    bottomObstructionClearance = clearance.value,
                    availableActions = rowActions,
                )
            }
            val chipsBefore = categoriesBounds()
            val rowBefore = bounds("post_row_compact-0")
            val dividerBefore = bounds("profile_timeline_divider_compact-0")
            compose.runOnIdle { clearance.value = 96.dp }
            compose.waitForIdle()
            assertEquals("compact category row ignores wide inputs", chipsBefore, categoriesBounds())
            assertEquals("compact row ignores wide inputs", rowBefore, bounds("post_row_compact-0"))
            assertEquals("compact divider ignores wide inputs", dividerBefore, bounds("profile_timeline_divider_compact-0"))
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h1000dp-420dpi")
    fun clearanceChangesRetainCategorySelectionAndScrollPosition() {
        val target = profile("retain")
        val posts = (0..11).map { post("retain-$it", target) }
        val tab = mutableStateOf(state(target, posts))
        val clearance = mutableStateOf(0.dp)
        show(LayoutDirection.Ltr) {
            ProfileScreen(
                account = target,
                profileState = tab.value,
                compactLayout = false,
                largeLayout = true,
                largeShowSummary = false,
                rightObstructionClearance = clearance.value,
                bottomObstructionClearance = bottom,
                availableActions = rowActions,
                onCategorySelected = { category -> tab.value = tab.value.copy(selectedTab = category) },
            )
        }
        scrollToEnd()
        val before = bounds("post_row_retain-11")
        compose.runOnIdle { clearance.value = right }
        compose.waitForIdle()
        val after = bounds("post_row_retain-11")
        assertEquals("scroll position is retained across clearance changes", before.top, after.top, 0.5f)
        assertEquals("scroll position is retained across clearance changes", before.bottom, after.bottom, 0.5f)
        assertTrue("clearance moves the row content edge",
            after.right < before.right)
        compose.onNodeWithContentDescription(text(R.string.profile_tab_reposts)).performClick().assertIsSelected()
        compose.runOnIdle { clearance.value = 0.dp }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(text(R.string.profile_tab_reposts)).assertIsSelected()
    }

    @Composable
    private fun destination(navigator: ShellNavigator, target: Account, posts: List<Post>) {
        ShellDestinationContent(
            paneModifier = Modifier.fillMaxSize(), navigator = navigator, overlay = ShellOverlayPresenter(),
            screenStates = rememberSaveableStateHolder(), homeListState = rememberLazyListState(),
            searchListState = rememberLazyListState(), photoGridScrollState = rememberLazyStaggeredGridState(),
            profileListState = rememberLazyListState(), largePresentation = true,
            rightObstructionClearance = right, bottomObstructionClearance = bottom,
            account = viewer, displayedProfile = target, savedTitle = R.string.app_name,
            notificationAccountIdentity = viewer.id.localId, availableTimelines = timelineDisplayOrder.toSet(),
            sessionRevision = 0L, home = null, photoGrid = PhotoGridContract.Empty,
            profile = AppShellFixtures.profile(state(target, posts)),
            search = AppShellFixtures.search(FeedState()),
            bookmarks = AppShellFixtures.bookmarks(), notifications = AppShellFixtures.notifications(),
            directMessages = DirectMessagesContract.Empty, accountSwitcher = AppShellFixtures.switcher(),
            postCallbacks = DestinationPostCallbacks(rowActions, false, {}, {}, {}, {}, { _, _ -> }, {}),
            draftCallbacks = DestinationDraftCallbacks(emptyList(), {}, {}),
            navigationCallbacks = DestinationNavigationCallbacks({ _, _ -> }, {}, {}),
        )
    }
}

private fun SemanticsNode.isChipRowEntry() =
    parent?.config?.contains(SemanticsProperties.SelectableGroup) == true
