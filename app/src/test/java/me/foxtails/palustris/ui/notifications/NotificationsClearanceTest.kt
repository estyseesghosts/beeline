package me.foxtails.palustris.ui.notifications

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
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationCursor
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.timelineDisplayOrder
import me.foxtails.palustris.ui.feed.FeedState
import me.foxtails.palustris.ui.navigation.ShellNavigator
import me.foxtails.palustris.ui.shell.AppShellFixtures
import me.foxtails.palustris.ui.shell.Destination
import me.foxtails.palustris.ui.shell.DestinationDraftCallbacks
import me.foxtails.palustris.ui.shell.DestinationNavigationCallbacks
import me.foxtails.palustris.ui.shell.DestinationPostCallbacks
import me.foxtails.palustris.ui.shell.DirectMessagesContract
import me.foxtails.palustris.ui.shell.NotificationsPanel
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
 * Wide Notifications obstruction-clearance coverage.
 *
 * Synthetic clearance values are test inputs. They are not production measurements.
 * Physical-right assertions use the physical edge in both layout directions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationsClearanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val connection = Connection("https://fixture.example", Protocol.MASTODON)
    private val receiver = Account(AccountId(connection, "receiver"), "Receiver", "@receiver@fixture.example")
    private val actor = Account(AccountId(connection, "actor"), "Actor", "@actor@fixture.example")
    private val right = 72.dp
    private val bottom = 96.dp
    private val density get() = compose.activity.resources.displayMetrics.density

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

    private fun described(description: Int): Rect = compose
        .onNodeWithContentDescription(compose.activity.getString(description))
        .fetchSemanticsNode().boundsInRoot

    private fun chipsBounds() = described(R.string.notification_filter_description)

    private fun text(@StringRes label: Int) = compose.activity.getString(label)

    private fun show(direction: LayoutDirection, content: @Composable () -> Unit) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                key(direction) {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        Box(Modifier.fillMaxSize().testTag("notification_test_viewport")) { content() }
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
        repeat(16) { compose.onNodeWithTag("notifications_content", useUnmergedTree = true).performTouchInput { swipeUp() } }
        compose.waitForIdle()
    }

    private fun post(id: String) = Post(
        id = EntityId(connection.origin, "$id-post"),
        author = actor,
        text = "Notification fixture $id",
        publishedAtEpochMillis = 0,
        audience = Audience.Public,
    )

    private fun notification(
        id: String,
        activity: NotificationActivity = NotificationActivity.Favourite,
        withPost: Boolean = false,
    ) = Notification(
        id = EntityId(connection.origin, id),
        accountId = receiver.id,
        createdAtEpochMillis = 0,
        activity = activity,
        actors = listOf(actor),
        post = if (withPost) post(id) else null,
        rawType = "fixture",
    )

    private fun state(
        items: List<Notification>,
        checkpoint: NotificationCheckpoint? = null,
        error: String? = null,
        syncDelayed: Boolean = false,
    ) = NotificationsUiState(
        items = items,
        checkpoint = checkpoint,
        error = error,
        syncDelayed = syncDelayed,
    )

    @Test fun wideChipsAndRowActionsClearPhysicalLeft() {
        for (direction in LayoutDirection.entries) {
            show(direction) {
                NotificationsScreen(connected = true, compactLayout = false,
                    leftObstructionClearance = right,
                    notificationState = state(listOf(notification("left", withPost = true))))
            }
            val viewport = bounds("notification_test_viewport")
            val safeLeft = viewport.left + right.value * density
            assertEquals(viewport.left, bounds("notifications_content").left, 1f)
            assertTrue(bounds("notification_row_left").left < safeLeft)
            assertTrue(bounds("notification_row_action_left").left >= safeLeft - 1f)
            assertTrue(chipsBounds().left >= safeLeft - 1f)
            compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
                .filter { it.boundsInRoot.width > 0 && it.boundsInRoot.height > 0 }.forEach {
                    assertTrue("Notification interaction clears physical left", it.boundsInRoot.left >= safeLeft - 1f)
                }
        }
    }

    @Test fun wideChipsAndRowsClearPhysicalRightWhileRowSurfacesUnderlay() {
        val items = (0..5).map { notification("wide-$it", withPost = true) }
        for (direction in LayoutDirection.entries) {
            show(direction) {
                NotificationsScreen(
                    connected = true,
                    compactLayout = false,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                    notificationState = state(items),
                )
            }
            val viewport = bounds("notification_test_viewport")
            val safeRight = viewport.right - right.value * density
            // The wide Column and both list viewports keep their full size.
            val refresh = bounds("notification_refresh_surface")
            assertEquals("refresh surface keeps the full width", viewport.left, refresh.left, 1f)
            assertEquals("refresh surface keeps the full width", viewport.right, refresh.right, 1f)
            assertEquals("refresh surface reaches the viewport bottom", viewport.bottom, refresh.bottom, 1f)
            assertEquals("list content keeps the full width", viewport.left, bounds("notifications_content").left, 1f)
            assertEquals("list content keeps the full width", viewport.right, bounds("notifications_content").right, 1f)
            val dock = bounds("notification_filter_dock")
            assertTrue("bottom dock clears physical right", dock.right <= safeRight + 1f)
            assertTrue("bottom dock clears bottom obstruction",
                dock.bottom <= viewport.bottom - bottom.value * density + 1f)
            assertTrue("bottom dock is below the viewport midpoint", dock.top > viewport.center.y)
            assertTrue("chip viewport clears physical right", chipsBounds().right <= safeRight + 1f)
            val surface = bounds("notification_row_wide-0")
            val action = bounds("notification_row_action_wide-0")
            assertTrue("row surface underlays future floating chrome", surface.right > safeRight)
            assertTrue("row action clears physical right", action.right <= safeRight + 1f)
            assertClicksClearRight(safeRight)
        }
    }

    @Test fun wideRowControlsClearPhysicalRightAndKeepTheirCallbacks() {
        for (direction in LayoutDirection.entries) {
            var dismissed = 0
            var accepted = 0
            var rejected = 0
            show(direction) {
                NotificationsScreen(
                    connected = true,
                    compactLayout = false,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                    notificationState = state(listOf(notification("controls", NotificationActivity.FollowRequest))),
                    onDismissNotification = { dismissed++ },
                    onFollowRequest = { _, accept -> if (accept) accepted++ else rejected++ },
                )
            }
            val viewport = bounds("notification_test_viewport")
            val safeRight = viewport.right - right.value * density
            val dismiss = compose.onNodeWithText(text(R.string.notifications_dismiss))
                .fetchSemanticsNode().boundsInRoot
            val accept = compose.onNodeWithText(text(R.string.notifications_follow_accept))
                .fetchSemanticsNode().boundsInRoot
            val reject = compose.onNodeWithText(text(R.string.notifications_follow_reject))
                .fetchSemanticsNode().boundsInRoot
            listOf("Dismiss" to dismiss, "Accept" to accept, "Reject" to reject).forEach { (name, node) ->
                assertTrue("$name clears physical right", node.right <= safeRight + 1f)
            }
            assertClicksClearRight(safeRight)
            compose.onNodeWithText(text(R.string.notifications_follow_accept)).performClick()
            compose.onNodeWithText(text(R.string.notifications_follow_reject)).performClick()
            compose.onNodeWithText(text(R.string.notifications_dismiss)).performClick()
            compose.runOnIdle {
                assertEquals("accept fires once", 1, accepted)
                assertEquals("reject fires once", 1, rejected)
                assertEquals("dismiss fires once", 1, dismissed)
            }
        }
    }

    @Test fun wideFinalRowAndLoadOlderClearBottomObstruction() {
        val items = (0..8).map { notification("final-$it", withPost = true) }
        for (direction in LayoutDirection.entries) {
            var loaded = 0
            show(direction) {
                NotificationsScreen(
                    connected = true,
                    compactLayout = false,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                    notificationState = state(
                        items = items,
                        checkpoint = NotificationCheckpoint(
                            accountId = receiver.id,
                            query = NotificationQuery(),
                            oldest = NotificationCursor("older"),
                        ),
                    ),
                    onLoadMoreNotifications = { loaded++ },
                )
            }
            val viewport = bounds("notification_test_viewport")
            val safeRight = viewport.right - right.value * density
            scrollToEnd()
            val finalRow = bounds("notification_row_final-8")
            val dock = bounds("notification_filter_dock")
            val loadOlder = compose.onNodeWithText(text(R.string.notifications_load_older))
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("final row clears filter dock", finalRow.bottom <= dock.top)
            assertTrue("load-older clears filter dock", loadOlder.bottom <= dock.top)
            assertTrue("final row clears bottom obstruction",
                finalRow.bottom <= viewport.bottom - bottom.value * density + 1f)
            assertTrue("load-older clears bottom obstruction",
                loadOlder.bottom <= viewport.bottom - bottom.value * density + 1f)
            assertTrue("load-older clears physical right", loadOlder.right <= safeRight + 1f)
            assertClicksClearRight(safeRight)
            compose.onNodeWithText(text(R.string.notifications_load_older)).performClick()
            assertTrue("load-older callback fires", loaded > 0)
        }
    }

    @Test fun wideEmptyAndErrorStatesClearPhysicalRight() {
        for (direction in LayoutDirection.entries) {
            var refreshed = 0
            show(direction) {
                NotificationsScreen(
                    connected = true,
                    compactLayout = false,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                    notificationState = state(emptyList(), error = "Fixture notification error"),
                    onRefreshNotifications = { refreshed++ },
                )
            }
            val viewport = bounds("notification_test_viewport")
            val safeRight = viewport.right - right.value * density
            val errorText = compose.onNodeWithText("Fixture notification error")
                .fetchSemanticsNode().boundsInRoot
            assertTrue("error text clears physical right", errorText.right <= safeRight + 1f)
            val retry = compose.onNodeWithText(text(R.string.notifications_retry))
                .fetchSemanticsNode().boundsInRoot
            assertTrue("retry clears physical right", retry.right <= safeRight + 1f)
            assertClicksClearRight(safeRight)
            compose.onNodeWithText(text(R.string.notifications_retry)).performClick()
            assertEquals("retry callback fires", 1, refreshed)

            show(direction) {
                NotificationsScreen(
                    connected = true,
                    compactLayout = false,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                    notificationState = state(emptyList()),
                )
            }
            val emptyTitle = compose.onNodeWithText(text(R.string.notifications_title))
                .fetchSemanticsNode().boundsInRoot
            assertTrue("empty title clears physical right", emptyTitle.right <= safeRight + 1f)
        }
    }

    @Test fun wideSyncDelayedBannerClearsPhysicalRight() {
        for (direction in LayoutDirection.entries) {
            show(direction) {
                NotificationsScreen(
                    connected = true,
                    compactLayout = false,
                    rightObstructionClearance = right,
                    bottomObstructionClearance = bottom,
                    notificationState = state(listOf(notification("banner", withPost = true)), syncDelayed = true),
                )
            }
            val viewport = bounds("notification_test_viewport")
            val safeRight = viewport.right - right.value * density
            val banner = compose.onNodeWithText(text(R.string.notifications_sync_delayed))
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("sync-delayed banner clears physical right", banner.right <= safeRight + 1f)
            assertClicksClearRight(safeRight)
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h1000dp-420dpi")
    fun notificationsDestinationForwardsClearance() {
        val items = (0..8).map { notification("branch-$it", withPost = true) }
        for (direction in LayoutDirection.entries) {
            val navigator = ShellNavigator()
            navigator.destination = Destination.Notifications
            navigator.notificationsPanelName = NotificationsPanel.Notifications.name
            show(direction) { destination(navigator, items) }
            val viewport = bounds("notification_test_viewport")
            val safeRight = viewport.right - right.value * density
            val list = bounds("notifications_content")
            assertTrue("branch chip row clears physical right", chipsBounds().right <= safeRight + 1f)
            assertTrue("branch row action clears physical right",
                bounds("notification_row_action_branch-0").right <= safeRight + 1f)
            scrollToEnd()
            val finalRow = bounds("notification_row_branch-8")
            assertTrue("branch final row clears bottom obstruction",
                finalRow.bottom <= list.bottom - bottom.value * density + 1f)
            assertClicksClearRight(safeRight)
            assertEquals(NotificationsPanel.Notifications, navigator.notificationsPanel)
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-420dpi")
    fun compactNotificationsIgnoreWideClearance() {
        val items = (0..5).map { notification("compact-$it", withPost = true) }
        for (direction in LayoutDirection.entries) {
            val clearance = mutableStateOf(0.dp)
            show(direction) {
                NotificationsScreen(
                    connected = true,
                    compactLayout = true,
                    rightObstructionClearance = clearance.value,
                    bottomObstructionClearance = clearance.value,
                    notificationState = state(items),
                )
            }
            val chipBefore = chipsBounds()
            val rowBefore = bounds("notification_row_compact-0")
            compose.runOnIdle { clearance.value = 96.dp }
            compose.waitForIdle()
            assertEquals("compact chip row ignores wide inputs", chipBefore, chipsBounds())
            assertEquals("compact row ignores wide inputs", rowBefore, bounds("notification_row_compact-0"))
        }
    }

    @Test fun clearanceChangesRetainFilterSelectionAndScrollPosition() {
        val items = (0..8).map { notification("retain-$it", withPost = true) }
        val clearance = mutableStateOf(0.dp)
        show(LayoutDirection.Ltr) {
            NotificationsScreen(
                connected = true,
                compactLayout = false,
                rightObstructionClearance = clearance.value,
                bottomObstructionClearance = bottom,
                notificationState = state(items),
            )
        }
        compose.onNodeWithContentDescription(text(R.string.notification_filter_likes))
            .performClick().assertIsSelected()
        scrollToEnd()
        val before = bounds("notification_row_retain-8")
        compose.runOnIdle { clearance.value = right }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(text(R.string.notification_filter_likes)).assertIsSelected()
        assertEquals("scroll position is retained across clearance changes",
            before, bounds("notification_row_retain-8"))
    }

    @Composable
    private fun destination(navigator: ShellNavigator, items: List<Notification>) {
        ShellDestinationContent(
            paneModifier = Modifier.fillMaxSize(), navigator = navigator, overlay = ShellOverlayPresenter(),
            screenStates = rememberSaveableStateHolder(), homeListState = rememberLazyListState(),
            searchListState = rememberLazyListState(), photoGridScrollState = rememberLazyStaggeredGridState(),
            profileListState = rememberLazyListState(), largePresentation = true,
            rightObstructionClearance = right, bottomObstructionClearance = bottom,
            account = receiver, displayedProfile = receiver, savedTitle = R.string.app_name,
            notificationAccountIdentity = receiver.id.localId, availableTimelines = timelineDisplayOrder.toSet(),
            sessionRevision = 0L, home = null, photoGrid = PhotoGridContract.Empty,
            profile = AppShellFixtures.profile(), search = AppShellFixtures.search(FeedState()),
            bookmarks = AppShellFixtures.bookmarks(), notifications = AppShellFixtures.notifications(state(items)),
            directMessages = DirectMessagesContract.Empty, accountSwitcher = AppShellFixtures.switcher(),
            postCallbacks = DestinationPostCallbacks(emptySet(), false, {}, {}, {}, {}, { _, _ -> }, {}),
            draftCallbacks = DestinationDraftCallbacks(emptyList(), {}, {}),
            navigationCallbacks = DestinationNavigationCallbacks({ _, _ -> }, {}, {}),
        )
    }
}
