package me.foxtails.palustris.ui.composer

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.flow.Flow
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.AppLanguage
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.hashtags.HashtagCatalog
import me.foxtails.palustris.domain.hashtags.HashtagLanguagePolicy
import me.foxtails.palustris.domain.hashtags.HashtagSuggestion
import me.foxtails.palustris.domain.hashtags.HashtagSuggestionService
import me.foxtails.palustris.ui.shell.ComposerContract
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
class ComposerHashtagTest {
    @get:Rule val compose = createComposeRule()

    private val account = Account(AccountId(Connection("https://example.org", Protocol.MASTODON), "me"), "Me", "@me@example.org")
    private val fetched = mutableListOf<String>()

    private val service = HashtagSuggestionService(
        catalog = HashtagCatalog.Empty,
        accountKey = "test",
        policy = { HashtagLanguagePolicy.forLanguage(AppLanguage.English) },
        fetchServer = { prefix, _ ->
            fetched += prefix
            listOf("${prefix}tography", "${prefix}ne").map { HashtagSuggestion(it, 5.0) }
        },
    )

    private fun owner() = ComposerOwner(mutableStateOf(ComposerEditorState()))

    private fun show(owner: ComposerOwner) {
        val contract = ComposerContract(
            postPreferences = PostPreferences(),
            availableAudiences = emptySet(),
            canPublish = true,
            publishing = false,
            error = null,
            actions = ComposerContract.Empty.actions,
            hashtagSuggestions = { fragments: Flow<String> -> service.suggestions(fragments, HashtagSuggestionService.COMPOSER_LIMIT) },
        )
        owner.context = ComposerOwnerContext(account = account, contract = contract)
        compose.setContent {
            ComposerBody(
                owner = owner,
                contract = contract,
                account = account,
                onRequestEmoji = {},
                pendingEmojiInsertion = null,
                onEmojiInsertionApplied = {},
            )
        }
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
    }

    private fun chipsShown() = compose.onAllNodes(hasTestTag("composer_hashtag_suggestions")).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun typingAHashtagShowsChipsAndATapReplacesTheTokenAndMovesTheCursor() {
        val owner = owner()
        show(owner)
        compose.onNodeWithContentDescription("Post text").performClick()
        compose.onNodeWithContentDescription("Post text").performTextInput("hello #pho")
        settle()

        compose.onNodeWithContentDescription("Insert hashtag #photography").assertIsDisplayed()
        compose.onNodeWithContentDescription("Insert hashtag #phone").assertIsDisplayed()
        compose.onNodeWithContentDescription("Insert hashtag #photography").performClick()
        settle()

        assertEquals("hello #photography ", owner.editor.first.text)
        // The cursor sits after the space, so the next characters follow it.
        compose.onNodeWithContentDescription("Post text").performTextInput("nice")
        assertEquals("hello #photography nice", owner.editor.first.text)
        assertTrue("the chips go away once the token ends", !chipsShown())
    }

    @Test
    fun theThreadEntryThatHasFocusReceivesTheText() {
        val owner = owner()
        val second = owner.addEntryAfter(owner.firstEntryId)!!
        show(owner)
        compose.onNodeWithContentDescription("Post 2 text").performClick()
        compose.onNodeWithContentDescription("Post 2 text").performTextInput("#pho")
        settle()

        compose.onNodeWithContentDescription("Insert hashtag #phone").performClick()
        settle()

        assertEquals("#phone ", owner.editor.entry(second)?.text)
        assertEquals("", owner.editor.first.text)
    }

    @Test
    fun noChipsInTheWarningField() {
        val owner = owner()
        show(owner)
        compose.runOnIdle {
            owner.setWarningEnabled(true)
        }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasText("Content warning")).performTextInput("#pho")
        settle()

        assertTrue(!chipsShown())
        assertTrue("the warning text never reaches the server", fetched.isEmpty())
    }

    @Test
    fun noChipsWhenTheCursorIsNotInAHashtag() {
        val owner = owner()
        show(owner)
        compose.onNodeWithContentDescription("Post text").performClick()
        compose.onNodeWithContentDescription("Post text").performTextInput("just words, no tag")
        settle()
        assertTrue(!chipsShown())

        compose.onNodeWithContentDescription("Post text").performTextInput(" word#pho")
        settle()
        assertTrue(!chipsShown())
        assertTrue(fetched.isEmpty())
    }

    @Test
    fun onlyTheFragmentReachesTheServer() {
        val owner = owner()
        show(owner)
        compose.onNodeWithContentDescription("Post text").performClick()
        compose.onNodeWithContentDescription("Post text").performTextInput("my private draft about alice, then #pho")
        settle()

        assertEquals(listOf("pho"), fetched)
    }

    @Test
    fun theChipRowKeepsTheCounterAndToolbarVisible() {
        val owner = owner()
        show(owner)
        compose.onNodeWithContentDescription("Post text").performClick()
        compose.onNodeWithContentDescription("Post text").performTextInput("#pho")
        settle()

        val chips = compose.onNode(hasTestTag("composer_hashtag_suggestions")).fetchSemanticsNode().boundsInRoot
        val toolbar = compose.onNodeWithContentDescription("Add photo").fetchSemanticsNode().boundsInRoot
        assertTrue("the chips sit above the toolbar", chips.bottom <= toolbar.top + 1f)
        compose.onNodeWithContentDescription("Add photo").assertIsDisplayed()
    }
}
