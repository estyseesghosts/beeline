package me.foxtails.palustris.ui.composer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.PostLengthRule
import me.foxtails.palustris.domain.PostLimits
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.PostingCapabilities
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.emoji.ComposerField
import me.foxtails.palustris.ui.shell.ComposerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposerBodyTest {
    @get:Rule val compose = createComposeRule()

    private val account = Account(AccountId(Connection("https://example.org", Protocol.MASTODON), "me"), "Me", "@me@example.org")
    private val mastodon = PostLimits(10, PostingCapabilities(lengthRule = PostLengthRule.MastodonCombined))
    private val misskey = PostLimits(10, PostingCapabilities(lengthRule = PostLengthRule.Utf16TextOnly, maxWarningLength = 5))

    private fun owner(): ComposerOwner = ComposerOwner(mutableStateOf(ComposerEditorState()))

    private fun contract(
        limits: PostLimits = PostLimits(),
        audiences: Set<Audience> = emptySet(),
    ) = ComposerContract(
        postPreferences = PostPreferences(),
        availableAudiences = audiences,
        canPublish = true,
        publishing = false,
        error = null,
        actions = ComposerContract.Empty.actions,
        limits = limits,
    )

    private fun show(
        owner: ComposerOwner,
        contract: ComposerContract,
        pending: () -> Pair<EmojiChoice, ComposerField>? = { null },
        onApplied: () -> Unit = {},
    ) {
        owner.context = ComposerOwnerContext(account = account, contract = contract)
        compose.setContent {
            ComposerBody(
                owner = owner,
                contract = contract,
                account = account,
                onRequestEmoji = {},
                pendingEmojiInsertion = pending(),
                onEmojiInsertionApplied = onApplied,
            )
        }
    }

    @Test
    fun counterShowsRemainingCharactersAndGoesNegativeOverTheLimit() {
        val owner = owner()
        show(owner, contract(mastodon))
        compose.onNodeWithContentDescription("10 characters remaining").assertIsDisplayed()
        compose.onNodeWithContentDescription("Post text").performTextInput("hello")
        compose.onNodeWithContentDescription("5 characters remaining").assertIsDisplayed()
        compose.onNodeWithContentDescription("Post text").performTextInput(" world!!")
        compose.onNodeWithContentDescription("-3 characters remaining").assertIsDisplayed()
    }

    @Test
    fun mastodonCounterDropsWhenAWarningIsAddedAndMisskeyShowsAWarningCount() {
        val mastodonOwner = owner()
        show(mastodonOwner, contract(mastodon))
        compose.onNodeWithContentDescription("Post text").performTextInput("hello")
        compose.runOnIdle {
            mastodonOwner.setWarningEnabled(true)
            mastodonOwner.setWarning("abc")
        }
        compose.onNodeWithContentDescription("2 characters remaining").assertIsDisplayed()
    }

    @Test
    fun misskeyCounterIgnoresTheWarningAndTheWarningFieldShowsItsOwnCount() {
        val owner = owner()
        show(owner, contract(misskey))
        compose.onNodeWithContentDescription("Post text").performTextInput("hello")
        compose.runOnIdle {
            owner.setWarningEnabled(true)
            owner.setWarning("abc")
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("5 characters remaining").assertIsDisplayed()
        compose.onNodeWithText("2 remaining").assertIsDisplayed()
    }

    @Test
    fun audienceRowOffersOnlyTheAudiencesTheServerSupports() {
        val owner = owner()
        show(owner, contract(audiences = setOf(Audience.Public, Audience.Followers)))
        compose.onNodeWithContentDescription("Audience: Public").performClick()
        compose.onNodeWithText("Followers only").assertIsDisplayed()
        compose.onNodeWithText("Only your followers can see it.").assertIsDisplayed()
        compose.onNodeWithText("Not in feeds").assertDoesNotExist()
        compose.onNodeWithText("Mentioned only").assertDoesNotExist()
        compose.onNodeWithText("Followers only").performClick()
        assertEquals(Audience.Followers, owner.editor.audience)
        compose.onNodeWithContentDescription("Audience: Followers only").assertIsDisplayed()
    }

    @Test
    fun audienceRowStaysHiddenWhenTheServerReportsNoAudiences() {
        show(owner(), contract(audiences = emptySet()))
        compose.onNodeWithContentDescription("Audience: Public").assertDoesNotExist()
    }

    @Test
    fun audienceRowShowsOnlyWhileTheFirstEntryHasFocus() {
        val owner = owner()
        val second = owner.addEntryAfter(owner.firstEntryId)!!
        owner.setEntryText(second, "later")
        show(owner, contract(audiences = setOf(Audience.Public, Audience.Followers)))
        compose.onNodeWithContentDescription("Audience: Public").assertIsDisplayed()
        compose.onNodeWithContentDescription("Post 2 text").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Audience: Public").assertDoesNotExist()
        compose.onNodeWithContentDescription("Post text").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Audience: Public").assertIsDisplayed()
    }

    @Test
    fun emojiInsertsAtTheCursorOfTheEntryItTargets() {
        val owner = owner()
        val second = owner.addEntryAfter(owner.firstEntryId)!!
        owner.setText("first")
        owner.setEntryText(second, "ab")
        var pending by mutableStateOf<Pair<EmojiChoice, ComposerField>?>(null)
        var applied = 0
        show(owner, contract(), pending = { pending }, onApplied = { applied++; pending = null })
        compose.onNodeWithContentDescription("Post 2 text").performTextInputSelection(TextRange(1))
        compose.runOnIdle { pending = EmojiChoice(":x:", ":x:") to ComposerField.Text.copy(entryId = second) }
        compose.waitForIdle()
        assertEquals("a:x:b", owner.editor.entries[1].text)
        assertEquals("first", owner.editor.first.text)
        assertEquals(1, applied)
        assertNull(pending)
    }

    @Test
    fun emojiForARemovedEntryIsDropped() {
        val owner = owner()
        var pending by mutableStateOf<Pair<EmojiChoice, ComposerField>?>(null)
        var applied = 0
        show(owner, contract(), pending = { pending }, onApplied = { applied++; pending = null })
        compose.runOnIdle { pending = EmojiChoice(":x:", ":x:") to ComposerField.Text.copy(entryId = "gone") }
        compose.waitForIdle()
        assertEquals("", owner.editor.text)
        assertEquals(1, applied)
    }
}
