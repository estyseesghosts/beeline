package me.foxtails.palustris.ui.posts

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Reaction
import me.foxtails.palustris.ui.PalustrisTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Slice 4 row behavior: chips highlight selection, tap toggles, long-press loads the
 * account list, and the add chip opens the picker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReactionRowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val account = Account(
        AccountId(Connection("https://example.org", Protocol.MASTODON), "person"),
        "Person",
        "@person@example.org",
    )
    private val owned = OwnedPost(
        account.id,
        Post(
            id = EntityId("https://example.org", "post"),
            author = account,
            text = "Post",
            publishedAtEpochMillis = 0,
            audience = Audience.Public,
            reactions = listOf(
                Reaction("👍", 2, selected = true),
                Reaction("❤️", 1, selected = false),
            ),
        ),
    )
    private val reactor = Account(
        AccountId(Connection("https://example.org", Protocol.MASTODON), "reactor"),
        "Reactor",
        "@reactor@example.org",
    )

    private fun show(
        ownedPost: OwnedPost = owned,
        enabled: Boolean = true,
        onReaction: (OwnedPost, EmojiChoice) -> Unit = { _, _ -> },
        onLongPressReaction: ((OwnedPost, Reaction) -> Unit)? = null,
        reactorsLoader: (suspend (OwnedPost, Reaction) -> List<Account>)? = null,
        onAddReaction: ((OwnedPost) -> Unit)? = null,
    ) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    ReactionRow(
                        reactions = ownedPost.post.reactions,
                        ownedPost = ownedPost,
                        enabled = enabled,
                        onReaction = onReaction,
                        showReactionNumbers = true,
                        onLongPressReaction = onLongPressReaction,
                        reactorsLoader = reactorsLoader,
                        onAddReaction = onAddReaction,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun selectedChipHighlightsWhileOthersDoNot() {
        show()

        compose.onNodeWithTag("reaction_chip_👍", useUnmergedTree = true).assertIsSelected()
        val unselected = compose.onNodeWithTag("reaction_chip_❤️", useUnmergedTree = true).fetchSemanticsNode()
        assertFalse(unselected.config[SemanticsProperties.Selected] == true)
    }

    @Test
    fun tapTogglesTheReaction() {
        var toggled: EmojiChoice? = null
        show(onReaction = { _, choice -> toggled = choice })

        compose.onNodeWithTag("reaction_chip_❤️", useUnmergedTree = true).performClick()

        assertEquals("❤️", toggled?.submissionValue)
    }

    @Test
    fun longPressLoadsTheAccountList() {
        var loaded: Reaction? = null
        show(reactorsLoader = { _, reaction ->
            loaded = reaction
            listOf(reactor)
        })

        compose.onNodeWithTag("reaction_chip_👍", useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()

        assertEquals("👍", loaded?.emoji)
        compose.onNodeWithTag("reaction_reactors_sheet").assertIsDisplayed()
        compose.onNodeWithText("Reactor").assertIsDisplayed()
        compose.onNodeWithText("@reactor@example.org").assertIsDisplayed()
    }

    @Test
    fun longPressCallbackObservesThePressWithoutALoader() {
        var pressed: Reaction? = null
        show(onLongPressReaction = { _, reaction -> pressed = reaction })

        compose.onNodeWithTag("reaction_chip_👍", useUnmergedTree = true).performTouchInput { longClick() }

        assertEquals("👍", pressed?.emoji)
        compose.onNodeWithTag("reaction_reactors_sheet").assertDoesNotExist()
    }

    @Test
    fun addChipOpensThePicker() {
        var opened: OwnedPost? = null
        show(onAddReaction = { opened = it })

        compose.onNodeWithTag("reaction_add").assertIsDisplayed()
        compose.onNodeWithTag("reaction_add").performClick()

        assertTrue(opened?.post?.id?.value == "post")
    }

    @Test
    fun addChipStaysHiddenWithoutACallback() {
        var toggled: EmojiChoice? = null
        show(onReaction = { _, choice -> toggled = choice })

        compose.onNodeWithTag("reaction_add").assertDoesNotExist()
        compose.onNodeWithTag("reaction_chip_❤️", useUnmergedTree = true).performClick()
        assertEquals("❤️", toggled?.submissionValue)
    }
}
