package me.foxtails.palustris.ui.composer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import me.foxtails.palustris.domain.DraftMedia
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.PostLengthRule
import me.foxtails.palustris.domain.PostLimits
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.PostingCapabilities
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ThreadPublication
import me.foxtails.palustris.domain.ThreadPublishListener
import me.foxtails.palustris.ui.emoji.ComposerField
import me.foxtails.palustris.ui.shell.ComposerContract
import me.foxtails.palustris.ui.shell.DraftsContract
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
        actions: ComposerContract.Actions = ComposerContract.Empty.actions,
    ) = ComposerContract(
        postPreferences = PostPreferences(),
        availableAudiences = audiences,
        canPublish = true,
        publishing = false,
        error = null,
        actions = actions,
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

    private class Publications : ComposerContract.Actions {
        val published = mutableListOf<ThreadPublication>()
        override fun publish(publication: ThreadPublication, listener: ThreadPublishListener) {
            published += publication
        }
    }

    private class SavingDrafts : DraftsContract.Actions {
        override fun load(onResult: (List<PostDraft>) -> Unit, onError: (String) -> Unit) = onResult(emptyList())
        override fun save(draft: PostDraft, onResult: (PostDraft) -> Unit, onError: () -> Unit) = onResult(draft)
        override fun delete(draftId: String, onDone: () -> Unit, onError: (String) -> Unit) = onDone()
    }

    @Test
    fun addPostIsDisabledOnAnEmptyEntryAndEnabledWithText() {
        show(owner(), contract())
        compose.onNodeWithContentDescription("Add post").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Post text").performTextInput("hello")
        compose.onNodeWithContentDescription("Add post").assertIsEnabled()
    }

    @Test
    fun addPostWithoutAWarningInsertsAnEntryWithoutAsking() {
        val owner = owner()
        show(owner, contract())
        compose.onNodeWithContentDescription("Post text").performTextInput("one")
        compose.onNodeWithContentDescription("Add post").performClick()
        compose.waitForIdle()
        assertEquals(2, owner.editor.entries.size)
        compose.onNodeWithText("Use the same content warning?").assertDoesNotExist()
    }

    @Test
    fun addPostWithAWarningAsksAndYesCopiesIt() {
        val owner = owner()
        show(owner, contract())
        compose.onNodeWithContentDescription("Post text").performTextInput("one")
        compose.runOnIdle {
            owner.setWarningEnabled(true)
            owner.setWarning("spoilers")
        }
        compose.onNodeWithContentDescription("Add post").performClick()
        compose.onNodeWithText("Use the same content warning?").assertIsDisplayed()
        compose.onNodeWithText("Yes").performClick()
        compose.waitForIdle()
        assertEquals(2, owner.editor.entries.size)
        assertEquals("spoilers", owner.editor.entries[1].warning)
        assertEquals(true, owner.editor.entries[1].warningEnabled)
        compose.onNodeWithText("Use the same content warning?").assertDoesNotExist()
    }

    @Test
    fun addPostWithAWarningAndNoLeavesTheNewEntryWithoutOne() {
        val owner = owner()
        show(owner, contract())
        compose.onNodeWithContentDescription("Post text").performTextInput("one")
        compose.runOnIdle {
            owner.setWarningEnabled(true)
            owner.setWarning("spoilers")
        }
        compose.onNodeWithContentDescription("Add post").performClick()
        compose.onNodeWithText("No").performClick()
        compose.waitForIdle()
        assertEquals(2, owner.editor.entries.size)
        assertEquals("", owner.editor.entries[1].warning)
        assertEquals(false, owner.editor.entries[1].warningEnabled)
    }

    @Test
    fun removingAnEntryKeepsTheOthersAndTheirMedia() {
        val owner = owner()
        val second = owner.addEntryAfter(owner.firstEntryId)!!
        val third = owner.addEntryAfter(second)!!
        owner.setText("one")
        owner.setEntryText(second, "two")
        owner.setEntryText(third, "three")
        owner.addMedia(third, DraftMedia(id = "m3", mimeType = "image/png", width = 1, height = 1, byteSize = 1L))
        show(owner, contract())
        compose.onNodeWithContentDescription("Remove post 1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Remove post 2").performClick()
        compose.waitForIdle()
        assertEquals(listOf("one", "three"), owner.editor.entries.map { it.text })
        assertEquals("m3", owner.editor.entries[1].media.single().id)
    }

    @Test
    fun aThreeEntryThreadPublishesInOrderWithTheFirstAudience() {
        val actions = Publications()
        val owner = owner()
        owner.draftsContract = DraftsContract(SavingDrafts())
        show(owner, contract(audiences = setOf(Audience.Public, Audience.Followers), actions = actions))
        compose.onNodeWithContentDescription("Post text").performTextInput("one")
        compose.onNodeWithContentDescription("Add post").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Post 2 text").performTextInput("two")
        compose.onNodeWithContentDescription("Add post").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Post 3 text").performTextInput("three")
        compose.runOnIdle { owner.setAudience(Audience.Followers) }
        compose.runOnIdle { owner.publish { _, _ -> } }
        val publication = actions.published.single()
        assertEquals(listOf("one", "two", "three"), publication.entries.map { it.text })
        assertEquals(Audience.Followers, publication.audience)
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
