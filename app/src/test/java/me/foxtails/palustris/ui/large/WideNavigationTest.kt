package me.foxtails.palustris.ui.large

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.io.File
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ProfileTimelineTab
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.PalustrisTheme
import me.foxtails.palustris.ui.feed.FeedState
import me.foxtails.palustris.ui.profile.ProfileCategory
import me.foxtails.palustris.ui.profile.ProfilePageState
import me.foxtails.palustris.ui.profile.ProfileScreen
import me.foxtails.palustris.ui.profile.ProfileUiState
import me.foxtails.palustris.ui.shell.AppShellFixtures
import me.foxtails.palustris.ui.thread.PostThreadPhase
import me.foxtails.palustris.ui.thread.PostThreadUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h1000dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WideNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun showShell() {
        compose.activity.runOnUiThread { compose.activity.setContent { AppShellFixtures.app() } }
    }

    @After fun clearDraft() {
        compose.activity.getSharedPreferences("local_draft", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun wideLightLayoutShowsSharedNavigationCapsule() {
        assertCapsuleAndComposer()
    }

    @Test
    @Config(qualifiers = "w800dp-h1000dp-night-420dpi")
    fun wideDarkLayoutShowsSharedNavigationCapsule() {
        assertCapsuleAndComposer()
    }

    @Test fun wideHomeChipRowBreaksOutOfPaneMarginAtTheDisplayEdges() {
        compose.waitForIdle()
        val row = compose.onNodeWithContentDescription("Home timelines; swipe horizontally for more")
            .fetchSemanticsNode().boundsInRoot
        val root = compose.onNodeWithTag("large_screen_shell").fetchSemanticsNode().boundsInRoot
        val marginPx = LARGE_OUTER_MARGIN_DP * compose.activity.resources.displayMetrics.density
        // The pane margin does not clip chips: the row spans the display on every side that touches it.
        assertTrue("row reaches the left display edge", row.left <= root.left + 1f)
        assertTrue("row reaches the right display edge", row.right >= root.right - 1f)
        // Chips still rest clear of the margin and the caret before any scrolling.
        val first = compose.onNodeWithContentDescription("Timeline Home").fetchSemanticsNode().boundsInRoot
        assertTrue("first chip rests clear of the display edge", first.left >= root.left + marginPx - 1f)
    }

    @Test fun wideNotificationsUseBottomChipDock() {
        compose.onNodeWithContentDescription("Notifications").performClick()
        compose.waitForIdle()

        val row = compose.onNodeWithContentDescription("Notification filters; swipe horizontally for more")
        row.assert(hasScrollAction())
        val rowBounds = row.fetchSemanticsNode().boundsInRoot
        val dockBounds = compose.onNodeWithTag("notification_filter_dock")
            .fetchSemanticsNode().boundsInRoot
        val placeholderBounds = compose.onNodeWithText("All caught up").fetchSemanticsNode().boundsInRoot
        assertTrue("wide notification chips should remain inside the bottom dock", rowBounds.top >= dockBounds.top)
        assertTrue("wide notification chips should remain inside the bottom dock", rowBounds.bottom <= dockBounds.bottom)
        assertTrue("wide notification dock should follow the empty-state content", placeholderBounds.bottom < dockBounds.top)
        listOf("Replies", "Reposts", "Followers", "Likes").forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed().assertIsNotSelected()
        }
        compose.onNodeWithText("All caught up").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mark all notifications read").assertDoesNotExist()
        compose.onNodeWithContentDescription("Notification settings").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w840dp-h1000dp-420dpi")
    fun expandedHomeShowsPanePromptAndTimelineDock() {
        compose.onNodeWithTag("large_screen_shell").assertIsDisplayed()
        compose.onNodeWithText("Select a post").assertIsDisplayed()
        compose.onNodeWithContentDescription("Timeline Home").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w445dp-h704dp-420dpi")
    fun compactWideProfileUsesMobileColumnAndKeepsWideActions() {
        val profile = wideProfile()
        var selectedCategory: ProfileCategory? = null
        val post = Post(
            id = EntityId(profile.id.connection.origin, "compact-wide-profile-post"),
            author = profile,
            text = "Compact-wide profile post",
            publishedAtEpochMillis = 0,
            audience = Audience.Public,
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                AppShellFixtures.app(
                    account = profile,
                    profile = AppShellFixtures.profile(
                        ProfileUiState(
                            targetId = profile.id,
                            account = profile,
                            selectedTab = ProfileCategory.Posts,
                            pages = mapOf(
                                ProfileTimelineTab.Posts to ProfilePageState(
                                    posts = listOf(OwnedPost(profile.id, post)),
                                    terminal = true,
                                ),
                            ),
                        ),
                        onSelectCategory = { selectedCategory = it },
                    ),
                )
            }
        }
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("large_screen_shell").assertIsDisplayed()
        compose.onNodeWithTag(LARGE_NAVIGATION_CAPSULE_TAG, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("profile_large_avatar").assertDoesNotExist()
        compose.onNodeWithText("Compact-wide profile post").assertIsDisplayed()
        val header = compose.onNodeWithTag("profile_header", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag("profile_timeline_list", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        // The list viewport reaches the display edge so chips and the banner can travel there; text keeps the pane margin.
        val marginPx = LARGE_OUTER_MARGIN_DP * compose.activity.resources.displayMetrics.density
        assertEquals("compact-wide Profile banner bleeds to the display edge", list.left, header.left, 1f)
        assertEquals("compact-wide Profile banner spans the whole pane", list.width, header.width, 1f)
        val displayName = compose.onNodeWithTag("profile_display_name", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val contentPaddingPx = 16 * compose.activity.resources.displayMetrics.density
        assertEquals("compact-wide Profile text rests at the pane margin", list.left + marginPx + contentPaddingPx, displayName.left, 1f)
        val dock = compose.onNodeWithTag("profile_categories_dock").fetchSemanticsNode().boundsInRoot
        val categories = compose.onNodeWithContentDescription(
            "Profile categories; swipe horizontally for more",
        )
        categories.assertIsDisplayed().assert(hasScrollAction())
        val categoryBounds = categories.fetchSemanticsNode().boundsInRoot
        assertTrue("compact-wide category chips stay inside their dock", categoryBounds.top >= dock.top)
        assertTrue("compact-wide category chips stay inside their dock", categoryBounds.bottom <= dock.bottom)
        compose.onNodeWithText("Media").performClick()
        compose.runOnIdle { assertEquals(ProfileCategory.Media, selectedCategory) }
        compose.onNodeWithContentDescription("Edit profile").assertIsDisplayed()
        compose.onNodeWithTag("profile_edit_action").assertDoesNotExist()
        compose.onNodeWithTag("profile_edit_profile_chip").assertDoesNotExist()
    }

    @Test fun directWideSelectionFollowedByFoldingKeepsGroupedDestination() {
        compose.onNodeWithContentDescription("Photo grid").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Photo grid").assertIsSelected()
        compose.onNodeWithText("No media posts available").assertIsDisplayed()

        compose.onNodeWithContentDescription("Direct messages").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Direct messages").assertIsSelected()
        compose.onNodeWithText("Connect an account to view messages").assertIsDisplayed()
    }

    @Test fun largeSearchKeepsCategoriesAboveTheField() {
        compose.onNodeWithContentDescription("Search").performClick()
        compose.waitForIdle()

        val categories = compose.onNodeWithContentDescription("Search categories; swipe horizontally for more")
        val field = compose.onNodeWithContentDescription("Search field")
        categories.assertIsDisplayed()
        field.assertIsDisplayed()
        assertTrue(categories.fetchSemanticsNode().boundsInRoot.top < field.fetchSemanticsNode().boundsInRoot.top)
    }

    @Test
    fun largeShortPostHasExplicitDetailAffordance() {
        val account = wideProfile()
        val post = Post(
            id = EntityId(account.id.connection.origin, "wide-short-post"),
            author = account,
            text = "Short post",
            publishedAtEpochMillis = 0,
            audience = Audience.Public,
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                AppShellFixtures.app(
                    account = account,
                    home = AppShellFixtures.home(FeedState(posts = listOf(post))),
                    thread = AppShellFixtures.thread(
                        PostThreadUiState(
                            phase = PostThreadPhase.Content,
                            focal = OwnedPost(account.id, post),
                        ),
                    ),
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Open post").assertIsDisplayed()
        compose.onNodeWithText("Short post").performClick()
        compose.onNodeWithContentDescription("Close post").assertIsDisplayed()
        compose.onNodeWithText("Replies").assertIsDisplayed()
    }

    @Test fun wideProfileUsesNormalChipFlowAndKeepsSelfActionReachable() {
        val profile = wideProfile()
        val post = Post(
            id = EntityId(profile.id.connection.origin, "wide-profile-post"),
            author = profile,
            text = "Wide profile post",
            publishedAtEpochMillis = 0,
            audience = Audience.Public,
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    ProfileScreen(
                        account = profile,
                        profileState = ProfileUiState(
                            targetId = profile.id,
                            account = profile,
                            selectedTab = ProfileCategory.Posts,
                            pages = mapOf(
                                ProfileTimelineTab.Posts to ProfilePageState(
                                    posts = listOf(OwnedPost(profile.id, post)),
                                    terminal = true,
                                ),
                            ),
                        ),
                        compactLayout = false,
                        authenticatedAccountId = profile.id,
                    )
                }
            }
        }
        compose.waitForIdle()

        val categories = compose.onNodeWithContentDescription(
            "Profile categories; swipe horizontally for more",
        )
        categories.assert(hasScrollAction())
        val categoryBounds = categories.fetchSemanticsNode().boundsInRoot
        val postBounds = compose.onNodeWithText("Wide profile post").fetchSemanticsNode().boundsInRoot
        assertTrue("wide profile categories should precede the timeline in page flow", categoryBounds.bottom < postBounds.top)
        compose.onAllNodesWithText("Profile Name").onLast().assertIsDisplayed()
        compose.onNodeWithText("Edit profile").assertDoesNotExist()
        listOf("Posts", "Media", "Reposts", "Replies", "Drafts", "Bookmarks", "Show more...").forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun largeProfileDockStartsAtContentSpineAndShowsEditAction() {
        val profile = wideProfile()
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    ProfileScreen(
                        account = profile,
                        profileState = ProfileUiState(
                            targetId = profile.id,
                            account = profile,
                        ),
                        compactLayout = false,
                        largeLayout = true,
                        authenticatedAccountId = profile.id,
                        onEditProfile = {},
                    )
                }
            }
        }
        compose.waitForIdle()

        val chipRow = compose.onNodeWithContentDescription(
            "Profile categories; swipe horizontally for more",
        ).fetchSemanticsNode().boundsInRoot
        val caret = compose.onNodeWithTag("profile_categories_visibility")
            .fetchSemanticsNode().boundsInRoot
        val expectedMarginPx = 16f * compose.activity.resources.displayMetrics.density
        assertTrue("large profile caret should start at the content spine", caret.left <= expectedMarginPx)
        assertTrue("large profile caret is the first item of the chip row", chipRow.left <= caret.left)
        compose.onNodeWithText("Edit profile").assertIsDisplayed()
    }

    private fun assertCapsuleAndComposer() {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/ui-screenshots/wide-${if (view.context.resources.configuration.isNightModeActive) "dark" else "light"}.png")
                .apply { parentFile?.mkdirs() }
                .outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithContentDescription("Home").assertIsDisplayed()
        compose.onNodeWithTag("large_screen_shell").assertIsDisplayed()
        compose.onNodeWithContentDescription("Timeline Home").assertIsDisplayed()
        compose.onNodeWithContentDescription("Search").assertIsDisplayed()
        compose.onNodeWithContentDescription("Notifications").assertIsDisplayed()
        compose.onNodeWithTag(LARGE_NAVIGATION_CAPSULE_TAG, useUnmergedTree = true).assertIsDisplayed()
        listOf("Home", "Search", "Photo grid", "Notifications", "Direct messages", "Profile").forEach {
            compose.onNodeWithContentDescription(it).assertIsDisplayed()
        }
        val targets = listOf("Home", "Search", "Photo grid", "Notifications", "Direct messages", "Profile")
            .map { compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot }
        targets.zipWithNext().forEach { (above, below) ->
            assertTrue("six direct targets retain their vertical order", above.bottom <= below.top + 1f)
        }
        compose.onNodeWithContentDescription("Home").assertIsSelected()
        compose.onNodeWithContentDescription("Compose post").performClick()
        compose.onNodeWithText("Drafts").assertIsDisplayed()
    }

    private fun wideProfile() = Account(
        id = AccountId(Connection("https://example.org", Protocol.MASTODON), "wide-profile"),
        displayName = "Profile Name",
        handle = "@profile@example.org",
        biography = "A wide profile biography",
    )
}
