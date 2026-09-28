package me.foxtails.palustris.ui.shell

import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.ui.feed.FeedState
import me.foxtails.palustris.ui.PalustrisApp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Shell-level behavior assertions that protect the boundaries moved by the decomposition.
 *
 * These tests exercise the real [PalustrisApp] shell. They describe visible behavior, not the
 * internal observer callbacks that the shell currently forwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellCharacterizationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun expandedReactionGestureOpensTheExpandedReactionBubble() {
        val account = AppShellFixtures.account("reaction-owner")
        val post = AppShellFixtures.post("reaction-post", account)
        val feed = FeedState(
            posts = listOf(post),
            ownedPosts = listOf(AppShellFixtures.owned(account, post)),
            actions = setOf(PostAction.React),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                AppShellFixtures.app(
                    account = account,
                    home = AppShellFixtures.home(feed),
                    postInteractions = AppShellFixtures.interactions(feed),
                    emojiPresentation = AppShellFixtures.emoji(
                        capabilities = EmojiCapabilities(reactionMutation = CapabilityStatus.Supported),
                    ),
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Favorite").performTouchInput {
            down(center)
            advanceEventTime(600)
            moveBy(Offset(0f, -150f))
            up()
        }
        compose.waitForIdle()

        compose.onNodeWithTag("reaction_bubble_expanded").assertIsDisplayed()
    }

    @Test
    fun replyPublishesToTheEffectiveActionTarget() {
        val account = AppShellFixtures.account("reply-owner")
        val original = EntityId(AppShellFixtures.connection.origin, "original")
        val post = AppShellFixtures.post("wrapper", account, actionTargetId = original)
        var request: CreatePostRequest? = null
        val feed = FeedState(
            posts = listOf(post),
            ownedPosts = listOf(AppShellFixtures.owned(account, post)),
            actions = setOf(PostAction.Reply),
            canPublish = true,
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                AppShellFixtures.app(
                    account = account,
                    home = AppShellFixtures.home(feed),
                    postInteractions = AppShellFixtures.interactions(feed),
                    draftsContract = AppShellFixtures.drafts(),
                    composer = AppShellFixtures.composer(feed, onPublish = { value, _ -> request = value }),
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Reply").performClick()
        compose.onNodeWithText("Replying to Fixture reply-owner").assertIsDisplayed()
        compose.onNodeWithContentDescription("Post text").performTextInput("A reply")
        compose.onNodeWithText("Publish").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { request != null }

        assertEquals(original, request?.replyTo)
    }

    @Test
    fun compactShellRoutesToSearchWithoutReplacingTheHomeContract() {
        val account = AppShellFixtures.account("compact-route")
        val post = AppShellFixtures.post("compact-route-post", account, "Home remains available")
        val feed = FeedState(posts = listOf(post))
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                AppShellFixtures.app(
                    account = account,
                    home = AppShellFixtures.home(feed),
                    search = AppShellFixtures.search(feed),
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Home remains available").assertIsDisplayed()
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithText("Find an account").assertIsDisplayed()
        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithText("Home remains available").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w900dp-h891dp-420dpi")
    fun largeShellRoutesToSearchWithTheSameConnectedFixture() {
        val account = AppShellFixtures.account("large-route")
        val feed = FeedState(posts = listOf(AppShellFixtures.post("large-route-post", account)))
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                AppShellFixtures.app(
                    account = account,
                    home = AppShellFixtures.home(feed),
                    search = AppShellFixtures.search(feed),
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithText("Find an account").assertIsDisplayed()
    }
}
