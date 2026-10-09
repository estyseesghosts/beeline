package me.foxtails.palustris.ui.posts

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Reaction
import me.foxtails.palustris.domain.ValidatedUrl
import me.foxtails.palustris.ui.PalustrisTheme
import me.foxtails.palustris.ui.emoji.EmojiCatalogState
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.PalustrisMotionScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val OPENED_AFTER_MILLIS = 600_000L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PostActionBubbleHostTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val owner = AccountId(Connection("https://example.org", Protocol.MASTODON), "person")
    private val post = Post(
        id = EntityId("https://example.org", "post"),
        author = Account(owner, "Person", "@person@example.org"),
        text = "Post",
        publishedAtEpochMillis = 0,
        audience = Audience.Public,
        reactions = listOf(Reaction(":blob:", 2, selected = true)),
    )
    private val owned = OwnedPost(owner, post, 1L)
    private val catalog = EmojiCatalogState(
        items = listOf(
            CustomEmoji(
                shortcode = "blob",
                animatedUrl = ValidatedUrl.https("https://cdn.example/blob.gif"),
                staticUrl = ValidatedUrl.https("https://cdn.example/blob.png"),
                submissionValue = ":blob:",
            ),
        ),
    )
    private val anchor = Rect(400f, 1200f, 460f, 1260f)
    private val supported = EmojiCapabilities(reactionMutation = CapabilityStatus.Supported)

    private fun show(
        target: PostActionBubbleTarget?,
        capabilities: EmojiCapabilities = supported,
        state: EmojiCatalogState = catalog,
        reducedMotion: Boolean = false,
        onDismiss: () -> Unit = {},
        onSelected: (OwnedPost, EmojiChoice) -> Unit = { _, _ -> },
        onModeChanged: (PostActionBubbleTarget.Reaction) -> Unit = {},
    ) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    CompositionLocalProvider(
                        LocalPalustrisMotionScheme provides PalustrisMotionScheme.standard(reducedMotion),
                    ) {
                        PostActionBubbleHost(
                            target = target,
                            emojiCatalog = state,
                            emojiCapabilities = capabilities,
                            onDismiss = onDismiss,
                            onHashtagSelected = {},
                            onReactionSelected = onSelected,
                            onReactionModeChanged = onModeChanged,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun compactReactionBubbleShowsTheSelectedReactionAndReportsTheChoiceWithItsTarget() {
        var chosen: Pair<OwnedPost, EmojiChoice>? = null
        var dismissed = false
        show(
            target = PostActionBubbleTarget.Reaction(owned, anchor),
            onSelected = { target, choice -> chosen = target to choice },
            onDismiss = { dismissed = true },
        )

        compose.onNodeWithTag("reaction_bubble_compact").assertIsDisplayed()
        compose.onNodeWithTag("emoji_picker_cell_:blob:", useUnmergedTree = true).performClick()

        assertEquals(owned, chosen?.first)
        assertEquals(":blob:", chosen?.second?.submissionValue)
        assertEquals(true, dismissed)
    }

    @Test
    fun expandedReactionBubbleShowsSearchAndTheSameSelectedChoice() {
        show(target = PostActionBubbleTarget.Reaction(owned, anchor, ReactionBubbleMode.Expanded))

        compose.onNodeWithTag("reaction_bubble_expanded").assertIsDisplayed()
        compose.onNodeWithTag("emoji_picker_search", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("emoji_picker_cell_:blob:", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun swipingUpOnTheCompactBubbleAsksForTheExpandedMode() {
        var changed: PostActionBubbleTarget.Reaction? = null
        show(
            target = PostActionBubbleTarget.Reaction(owned, anchor),
            onModeChanged = { changed = it },
        )

        // The gesture ignores pointers that began before the bubble opened, so move input time ahead.
        compose.onNodeWithTag("reaction_bubble_compact").performTouchInput {
            advanceEventTime(OPENED_AFTER_MILLIS)
            swipeUp()
        }
        compose.waitForIdle()

        assertNotNull(changed)
        assertEquals(ReactionBubbleMode.Expanded, changed?.mode)
        assertEquals(owned, changed?.ownedPost)
    }

    @Test
    fun missingAnchorOpensTheFullPickerInTheSheetWithTheSameChoice() {
        var chosen: EmojiChoice? = null
        show(
            target = PostActionBubbleTarget.Reaction(owned, Rect.Zero),
            onSelected = { _, choice -> chosen = choice },
        )

        compose.onNodeWithTag("reaction_picker_sheet").assertIsDisplayed()
        compose.onNodeWithTag("emoji_picker_search", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("emoji_picker_cell_:blob:", useUnmergedTree = true).performClick()

        assertEquals(":blob:", chosen?.submissionValue)
    }

    @Test
    @Config(fontScale = 2f)
    fun expandedPickerFallsBackToTheSheetAtDoubleFontScale() {
        show(target = PostActionBubbleTarget.Reaction(owned, anchor, ReactionBubbleMode.Expanded))

        compose.onNodeWithTag("reaction_picker_sheet").assertIsDisplayed()
        compose.onNodeWithTag("reaction_bubble_expanded").assertDoesNotExist()
    }

    @Test
    fun unsupportedReactionsDismissInsteadOfShowingAPicker() {
        var dismissed = false
        show(
            target = PostActionBubbleTarget.Reaction(owned, anchor),
            capabilities = EmojiCapabilities(reactionMutation = CapabilityStatus.Unsupported),
            onDismiss = { dismissed = true },
        )

        compose.onNodeWithTag("reaction_bubble_compact").assertDoesNotExist()
        compose.onNodeWithTag("reaction_picker_sheet").assertDoesNotExist()
        assertEquals(true, dismissed)
    }

    @Test
    fun reducedMotionShowsTheBubbleWithoutEnterAnimation() {
        show(target = PostActionBubbleTarget.Reaction(owned, anchor), reducedMotion = true)

        compose.onNodeWithTag("reaction_bubble_compact").assertIsDisplayed()
    }

    @Test
    fun failedPinWriteShowsInsideTheCompactBubble() {
        show(
            target = PostActionBubbleTarget.Reaction(owned, anchor),
            state = catalog.copy(pinFailed = true),
        )

        compose.onNodeWithTag("emoji_pin_failure", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun clearingTheTargetRemovesTheBubble() {
        var target by mutableStateOf<PostActionBubbleTarget?>(PostActionBubbleTarget.Reaction(owned, anchor))
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    PostActionBubbleHost(
                        target = target,
                        emojiCatalog = catalog,
                        emojiCapabilities = supported,
                        onDismiss = {},
                        onHashtagSelected = {},
                        onReactionSelected = { _, _ -> },
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("reaction_bubble_compact").assertIsDisplayed()

        compose.runOnIdle { target = null }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTagCount("reaction_bubble_compact") == 0
        }

        assertNull(target)
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
