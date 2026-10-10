package me.foxtails.palustris.ui.composer

import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.ui.shell.AppShellFixtures
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.feed.FeedState
import me.foxtails.palustris.ui.shell.DraftsContract
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class RecordingDrafts(initial: List<PostDraft> = emptyList()) : DraftsContract.Actions {
    val drafts = initial.toMutableList()
    val saved = mutableListOf<PostDraft>()
    override fun load(onResult: (List<PostDraft>) -> Unit, onError: (String) -> Unit) = onResult(drafts.toList())
    override fun save(draft: PostDraft, onResult: (PostDraft) -> Unit, onError: () -> Unit) {
        saved += draft
        drafts.removeAll { it.id == draft.id }
        drafts.add(0, draft)
        onResult(draft)
    }
    override fun delete(draftId: String, onDone: () -> Unit, onError: (String) -> Unit) {
        drafts.removeAll { it.id == draftId }
        onDone()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReplyComposerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun openReply(card: Boolean = false, drafts: DraftsContract.Actions? = null): Account {
        val account = Account(
            AccountId(Connection("https://example.org", Protocol.MASTODON), "person"),
            "Display Name",
            "@person@example.org",
        )
        val post = Post(
            id = EntityId("https://example.org", "wrapper"),
            author = account,
            text = "A post to reply to",
            publishedAtEpochMillis = 0,
            audience = Audience.Public,
            actionTargetId = EntityId("https://example.org", "original"),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                ComposerFeatureFixtures.reply(
                    account = account,
                    home = AppShellFixtures.home(
                        FeedState(
                            posts = listOf(post),
                            ownedPosts = listOf(OwnedPost(account.id, post)),
                            actions = setOf(PostAction.Reply),
                        ),
                    ),
                    postInteractions = AppShellFixtures.interactions(
                        FeedState(
                            posts = listOf(post),
                            ownedPosts = listOf(OwnedPost(account.id, post)),
                            actions = setOf(PostAction.Reply),
                        ),
                    ),
                    draftsContract = drafts?.let { DraftsContract(it) } ?: DraftsContract.Empty,
                    card = card,
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Reply").performClick()
        compose.waitForIdle()
        return account
    }

    @Test
    fun replyOpensComposerForTheEffectiveActionTarget() {
        openReply()
        compose.onNodeWithText("Replying to Display Name").assertIsDisplayed()
    }

    @Test
    fun compactComposerFillsTheWindowWithCloseDraftsAndPost() {
        openReply()
        compose.onNodeWithTag(COMPOSER_SURFACE_TAG).assertWidthIsEqualTo(411.dp)
        compose.onNodeWithContentDescription("Close composer").assertIsDisplayed()
        compose.onNodeWithText("Drafts").assertIsDisplayed()
        compose.onNodeWithTag(COMPOSER_POST_TAG).assertIsDisplayed()
        compose.onAllNodesWithText("Publish").assertCountEquals(1)
    }

    @Test
    fun closeButtonDismissesTheComposer() {
        openReply()
        compose.onNodeWithText("Replying to Display Name").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close composer").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(COMPOSER_SURFACE_TAG).assertDoesNotExist()
    }

    @Test
    fun draftsBrowserSavesFirstThenLoadsTheChosenDraft() {
        val account = Account(
            AccountId(Connection("https://example.org", Protocol.MASTODON), "person"),
            "Display Name",
            "@person@example.org",
        )
        val recording = RecordingDrafts(
            listOf(PostDraft(accountId = account.id, text = "Seeded draft")),
        )
        openReply(drafts = recording)
        compose.onNodeWithContentDescription("Post text").performTextInput("Unsent")
        compose.onNodeWithText("Drafts").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Seeded draft").assertIsDisplayed()
        compose.onNodeWithText("Seeded draft").performClick()
        compose.waitForIdle()
        assertTrue("Opening a draft saves the current editor first.", recording.saved.any { it.text == "Unsent" })
        compose.onNodeWithText("Unsent").assertDoesNotExist()
        compose.onNodeWithText("Drafts").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Unsent").assertIsDisplayed()
        compose.onNodeWithText("Seeded draft").assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w900dp-h900dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExpandedComposerSurfaceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun openReply(card: Boolean = true) {
        val account = Account(
            AccountId(Connection("https://example.org", Protocol.MASTODON), "person"),
            "Display Name",
            "@person@example.org",
        )
        val post = Post(
            id = EntityId("https://example.org", "wrapper"),
            author = account,
            text = "A post to reply to",
            publishedAtEpochMillis = 0,
            audience = Audience.Public,
            actionTargetId = EntityId("https://example.org", "original"),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                ComposerFeatureFixtures.reply(
                    account = account,
                    home = AppShellFixtures.home(
                        FeedState(
                            posts = listOf(post),
                            ownedPosts = listOf(OwnedPost(account.id, post)),
                            actions = setOf(PostAction.Reply),
                        ),
                    ),
                    postInteractions = AppShellFixtures.interactions(
                        FeedState(
                            posts = listOf(post),
                            ownedPosts = listOf(OwnedPost(account.id, post)),
                            actions = setOf(PostAction.Reply),
                        ),
                    ),
                    card = card,
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Reply").performClick()
        compose.waitForIdle()
    }

    @Test
    fun expandedComposerShowsACardAtMost600dpWide() {
        openReply()
        compose.onNodeWithTag(COMPOSER_SCRIM_TAG).assertIsDisplayed()
        compose.onNodeWithTag(COMPOSER_SURFACE_TAG).assertWidthIsEqualTo(600.dp)
    }

    @Test
    fun outsideTapDismissesTheComposer() {
        openReply()
        compose.onNodeWithTag(COMPOSER_SURFACE_TAG).assertIsDisplayed()
        compose.onNodeWithTag(COMPOSER_SCRIM_TAG).performTouchInput {
            click(Offset(20f, 20f))
        }
        compose.waitForIdle()
        compose.onNodeWithTag(COMPOSER_SURFACE_TAG).assertDoesNotExist()
    }
}
