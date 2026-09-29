package me.foxtails.palustris.ui.notifications

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.ui.notifications.NotificationsUiState
import me.foxtails.palustris.ui.notifications.NotificationsScreen
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostContentVisibility
import me.foxtails.palustris.domain.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun showNotifications(
        connected: Boolean = false,
        accountIdentity: String = "preview",
        compactLayout: Boolean = true,
        notificationState: NotificationsUiState = NotificationsUiState(),
        onRefresh: () -> Unit = {},
        onMarkAllRead: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
    ) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                NotificationsScreen(
                    connected = connected,
                    compactLayout = compactLayout,
                    accountIdentity = accountIdentity,
                    notificationState = notificationState,
                    onRefreshNotifications = onRefresh,
                    onMarkAllRead = onMarkAllRead,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
        compose.waitForIdle()
    }


    @Test fun defaultStateShowsAllNotificationsAndRequiredChipOrder() {
        showNotifications()

        val labels = listOf("Replies", "Reposts", "Followers", "Likes")
        labels.forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed().assertIsNotSelected()
        }
        assertTrue(
            "notification chips should retain the requested order",
            labels.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.left }
                .zipWithNext().all { (left, right) -> left < right },
        )
        compose.onNodeWithText("All caught up").assertIsDisplayed()
        compose.onNodeWithText("Activity from people you follow will appear here.").assertIsDisplayed()
        compose.onNodeWithText("All", substring = false).assertDoesNotExist()
    }

    @Test fun connectedNotificationActionsHaveRequiredOrderAndMarkReadConfirmation() {
        var markAllReads = 0
        var settings = 0
        showNotifications(
            connected = true,
            onMarkAllRead = { markAllReads++ },
            onOpenSettings = { settings++ },
        )

        val labels = listOf("Mark all as read", "Replies", "Reposts", "Followers", "Likes", "Notification settings")
        val row = compose.onNodeWithContentDescription("Notification filters; swipe horizontally for more")
        labels.forEach { label -> row.performScrollToNode(hasText(label)) }
        row.performScrollToNode(hasText("Mark all as read"))
        compose.onNodeWithTag("notification_mark_all_read").assertExists().performClick()
        assertEquals(0, markAllReads)
        compose.onNodeWithText("Mark all as read?").assertIsDisplayed()
        compose.onNodeWithTag("notification_mark_all_read").performClick()
        assertEquals(1, markAllReads)
        compose.onNodeWithText("Mark all as read").assertIsDisplayed()

        row.performScrollToNode(hasText("Notification settings"))
        compose.onNodeWithTag("notification_settings").performClick()
        assertEquals(1, settings)
        row.performScrollToNode(hasText("Replies"))
        compose.onNodeWithText("Replies").assertIsNotSelected()
    }

    @Test fun disconnectedNotificationPreviewOmitsAccountActions() {
        showNotifications()

        compose.onNodeWithText("Mark all as read").assertDoesNotExist()
        compose.onNodeWithText("Notification settings").assertDoesNotExist()
    }

    @Test fun compactNotificationRowsKeepFullRefreshViewportAndFinalRowCanScrollClear() {
        val connection = Connection("https://example.org", Protocol.MASTODON)
        val receiver = Account(AccountId(connection, "receiver"), "Receiver", "@receiver@example.org")
        val notifications = (0..8).map { index ->
            val actor = Account(AccountId(connection, "actor-$index"), "Actor $index", "@actor$index@example.org")
            Notification(
                id = EntityId(connection.origin, "compact-$index"),
                accountId = receiver.id,
                createdAtEpochMillis = 0,
                activity = NotificationActivity.Favourite,
                actors = listOf(actor),
                post = Post(
                    EntityId(connection.origin, "compact-post-$index"),
                    actor,
                    "Notification $index\nA second fixture line\nA third fixture line",
                    0,
                    Audience.Public,
                ),
                rawType = "favourite",
            )
        }
        showNotifications(connected = true, notificationState = NotificationsUiState(items = notifications))

        val refresh = compose.onNodeWithTag("notification_refresh_surface", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag("notifications_content", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals("Pull-to-refresh should own the complete compact viewport", refresh.top, content.top, 0.5f)
        assertEquals("Pull-to-refresh should own the complete compact viewport", refresh.bottom, content.bottom, 0.5f)

        val filters = compose.onNodeWithContentDescription("Notification filters; swipe horizontally for more")
            .fetchSemanticsNode().boundsInRoot
        val underlappingRow = compose.onNodeWithTag("notification_row_compact-5", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("a notification row should continue behind the floating filters", underlappingRow.bottom > filters.top)

        repeat(14) {
            compose.onNodeWithTag("notifications_content", useUnmergedTree = true).performTouchInput { swipeUp() }
        }
        compose.waitForIdle()
        val finalRow = compose.onNodeWithTag("notification_row_compact-8", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("the final notification should clear the floating filters", finalRow.bottom <= filters.top)
        compose.onNodeWithTag("notification_row_compact-8", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun notificationRowKeepsScaledTextAndActionsInsideItsCard() {
        val connection = Connection("https://example.org", Protocol.MISSKEY)
        val account = Account(AccountId(connection, "receiver"), "Receiver", "@receiver@example.org")
        val actor = Account(AccountId(connection, "actor"), "A very long actor display name", "@actor@example.org")
        val notification = Notification(
            id = EntityId(connection.origin, "scaled-row"),
            accountId = account.id,
            createdAtEpochMillis = 0,
            activity = NotificationActivity.FollowRequest,
            actors = listOf(actor),
            rawType = "followRequest",
        )
        var accepted = 0
        var rejected = 0
        var dismissed = 0

        listOf(false, true).forEach { compactLayout ->
            compose.activity.runOnUiThread {
                compose.activity.setContent {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f)) {
                        NotificationsScreen(
                            connected = true,
                            compactLayout = compactLayout,
                            notificationState = NotificationsUiState(items = listOf(notification)),
                            onDismissNotification = { dismissed++ },
                            onFollowRequest = { _, accept -> if (accept) accepted++ else rejected++ },
                        )
                    }
                }
            }
            compose.waitForIdle()

            val content = compose.onNodeWithTag("notifications_content", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val row = compose.onNodeWithTag("notification_row_scaled-row", useUnmergedTree = true)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("scaled notification row should stay inside the content bounds", row.left >= content.left)
            assertTrue("scaled notification row should stay inside the content bounds", row.right <= content.right)
            assertTrue("scaled notification row should stay inside the content bounds", row.top >= content.top)
            assertTrue("scaled notification row should stay inside the content bounds", row.bottom <= content.bottom)

            listOf("Accept", "Reject", "Dismiss").forEach { label ->
                compose.onNodeWithText(label)
                    .assertIsDisplayed()
                    .assertIsEnabled()
                    .assertHasClickAction()
                    .fetchSemanticsNode().boundsInRoot.let { action ->
                        assertTrue("$label should remain inside the scaled notification row", action.left >= row.left)
                        assertTrue("$label should remain inside the scaled notification row", action.right <= row.right)
                        assertTrue("$label should remain inside the scaled notification row", action.top >= row.top)
                        assertTrue("$label should remain inside the scaled notification row", action.bottom <= row.bottom)
                    }
            }
            compose.onNodeWithText("Accept").performClick()
            compose.onNodeWithText("Reject").performClick()
            compose.onNodeWithText("Dismiss").performClick()
            compose.runOnIdle {
                assertEquals(accepted, if (compactLayout) 2 else 1)
                assertEquals(rejected, if (compactLayout) 2 else 1)
                assertEquals(dismissed, if (compactLayout) 2 else 1)
            }
        }
    }

    @Test fun notificationRowKeepsWarningHiddenAndErrorTextBoundedAtScaledDensity() {
        val connection = Connection("https://example.org", Protocol.MISSKEY)
        val account = Account(AccountId(connection, "receiver"), "Receiver", "@receiver@example.org")
        val actor = Account(AccountId(connection, "actor"), "Actor", "@actor@example.org")
        fun notification(id: String, post: Post) = Notification(
            id = EntityId(connection.origin, id),
            accountId = account.id,
            createdAtEpochMillis = 0,
            activity = NotificationActivity.FollowRequest,
            actors = listOf(actor),
            post = post,
            rawType = "followRequest",
        )
        fun post(id: String, warning: String?, visibility: PostContentVisibility) = Post(
            EntityId(connection.origin, "$id-post"),
            actor,
            "A long post body that remains bounded at large font scale.",
            0,
            Audience.Public,
            contentWarning = warning,
            contentVisibility = visibility,
        )
        val cases = listOf(
            Triple("warning-row", post("warning", "A long content warning label", PostContentVisibility.Visible), "A long content warning label"),
            Triple("hidden-row", post("hidden", null, PostContentVisibility.Hidden), "Content hidden by your settings."),
            Triple("error-row", post("error", null, PostContentVisibility.Visible), "A long follow request error message."),
        )

        cases.forEach { (id, itemPost, visibleText) ->
            var accepted = 0
            var rejected = 0
            var dismissed = 0
            val item = notification(id, itemPost)
            compose.activity.runOnUiThread {
                compose.activity.setContent {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f)) {
                        NotificationsScreen(
                            connected = true,
                            compactLayout = true,
                            notificationState = NotificationsUiState(
                                items = listOf(item),
                                actionErrors = if (id == "error-row") mapOf(item.id to "A long follow request error message.") else emptyMap(),
                            ),
                            onDismissNotification = { dismissed++ },
                            onFollowRequest = { _, accept -> if (accept) accepted++ else rejected++ },
                        )
                    }
                }
            }
            compose.waitForIdle()
            val row = compose.onNodeWithTag("notification_row_$id", useUnmergedTree = true)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val text = compose.onNodeWithText(visibleText).assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertTrue("$id text should stay inside its notification row", text.left >= row.left)
            assertTrue("$id text should stay inside its notification row", text.right <= row.right)
            assertTrue("$id text should stay inside its notification row", text.top >= row.top)
            assertTrue("$id text should stay inside its notification row", text.bottom <= row.bottom)
            listOf("Accept", "Reject", "Dismiss").forEach { label ->
                compose.onNodeWithText(label).assertIsEnabled().assertHasClickAction().performClick()
            }
            assertEquals(1, accepted)
            assertEquals(1, rejected)
            assertEquals(1, dismissed)
        }
    }

    @Test fun selectingEachFilterShowsItsPlaceholderAndSecondTapRestoresAll() {
        val cases = listOf(
            "Replies" to "Replies",
            "Reposts" to "Reposts",
            "Followers" to "Followers",
            "Likes" to "Likes",
        )

        cases.forEach { (label, _) ->
            showNotifications()
            compose.onNodeWithContentDescription(label).performClick().assertIsSelected()
            compose.onNodeWithText("Try another filter or pull down to refresh.").assertIsDisplayed()
            compose.onNodeWithText("All caught up").assertDoesNotExist()
            compose.onNodeWithContentDescription(label).performClick().assertIsNotSelected()
            compose.onNodeWithText("All caught up").assertIsDisplayed()
            listOf("Replies", "Reposts", "Followers", "Likes").forEach { filter ->
                compose.onNodeWithText(filter).assertIsNotSelected()
            }
        }
    }

    @Test fun selectingAnotherFilterSwitchesDirectly() {
        showNotifications()

        compose.onNodeWithContentDescription("Replies").performClick()
        compose.onNodeWithContentDescription("Reposts").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Replies").assertIsNotSelected()
        compose.onNodeWithText("Try another filter or pull down to refresh.").assertIsDisplayed()
    }

    @Test fun tappingTheSelectedFilterClearsBackToAllNotifications() {
        showNotifications()

        compose.onNodeWithContentDescription("Replies").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Replies").performClick().assertIsNotSelected()
        listOf("Replies", "Reposts", "Followers", "Likes").forEach { label ->
            compose.onNodeWithText(label).assertIsNotSelected()
        }
        compose.onNodeWithText("All caught up").assertIsDisplayed()
    }

    @Test fun followersFilterShowsOnlyFollowActivity() {
        val connection = Connection("https://example.org", Protocol.MISSKEY)
        val accountId = AccountId(connection, "receiver")
        val actor = Account(AccountId(connection, "actor"), "Actor", "@actor@example.org")
        val notifications = NotificationsUiState(
            items = listOf(
                Notification(
                    id = EntityId(connection.origin, "follow"),
                    accountId = accountId,
                    createdAtEpochMillis = 2,
                    activity = NotificationActivity.Follow,
                    actors = listOf(actor),
                    rawType = "follow",
                ),
                Notification(
                    id = EntityId(connection.origin, "like"),
                    accountId = accountId,
                    createdAtEpochMillis = 1,
                    activity = NotificationActivity.Favourite,
                    actors = listOf(actor),
                    rawType = "favourite",
                ),
            ),
        )
        showNotifications(connected = true, notificationState = notifications)

        compose.onNodeWithText("Followers").performClick().assertIsSelected()
        compose.onNodeWithText("Followed you").assertIsDisplayed()
        compose.onNodeWithText("Liked your post").assertDoesNotExist()
    }

    @Test fun changingAccountIdentityResetsTransientFilter() {
        showNotifications(accountIdentity = "https://example.org\u0000alice")
        compose.onNodeWithContentDescription("Likes").performClick().assertIsSelected()

        showNotifications(accountIdentity = "https://example.org\u0000bob")
        compose.onNodeWithContentDescription("Likes").assertIsNotSelected()
        compose.onNodeWithText("All caught up").assertIsDisplayed()
    }

    @Test fun notificationRowUsesScreenSpecificScrollableSemantics() {
        showNotifications(connected = true)

        compose.onNodeWithContentDescription("Notification filters; swipe horizontally for more")
            .assert(hasScrollAction())
        compose.onNodeWithContentDescription("Notification filters; swipe horizontally for more")
            .assertIsDisplayed()
        compose.onNodeWithText("Notifications").assertIsDisplayed()
    }

    @Test fun emptyInboxCanTriggerPullToRefresh() {
        var refreshes = 0
        showNotifications(onRefresh = { refreshes++ })

        compose.onNodeWithTag("notifications_content").performTouchInput {
            swipeDown(startY = top + 4f, endY = bottom - 4f)
        }
        compose.waitForIdle()

        assertTrue("empty notification inbox should support pull-to-refresh", refreshes > 0)
    }

    @Test fun hiddenPostBodyIsWithheldBehindPlaceholder() {
        val connection = Connection("https://example.org", Protocol.MASTODON)
        val receiver = Account(AccountId(connection, "receiver"), "Receiver", "@receiver@example.org")
        val actor = Account(AccountId(connection, "actor"), "Actor", "@actor@example.org")
        fun item(id: String, text: String, visibility: PostContentVisibility) = Notification(
            id = EntityId(connection.origin, id),
            accountId = receiver.id,
            createdAtEpochMillis = 0,
            activity = NotificationActivity.Mention,
            actors = listOf(actor),
            post = Post(
                EntityId(connection.origin, "$id-post"),
                actor,
                text,
                0,
                Audience.Public,
                contentVisibility = visibility,
            ),
            rawType = "mention",
        )
        showNotifications(
            connected = true,
            notificationState = NotificationsUiState(items = listOf(
                item("hidden-1", "hidden body text", PostContentVisibility.Hidden),
                item("visible-1", "visible body text", PostContentVisibility.Visible),
            )),
        )

        compose.onNodeWithText("visible body text").assertIsDisplayed()
        compose.onNodeWithText("hidden body text").assertDoesNotExist()
    }

}
