package me.foxtails.palustris.ui.composer

import androidx.compose.runtime.mutableStateOf
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.DraftMedia
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.PostLengthRule
import me.foxtails.palustris.domain.PostLimits
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.PostingCapabilities
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ThreadPublication
import me.foxtails.palustris.domain.ThreadPublishListener
import me.foxtails.palustris.ui.shell.ComposerContract
import me.foxtails.palustris.ui.shell.DraftsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerEntryRulesTest {
    private val mastodon = PostLimits(500, PostingCapabilities(lengthRule = PostLengthRule.MastodonCombined))
    private val misskey = PostLimits(
        3000,
        PostingCapabilities(lengthRule = PostLengthRule.Utf16TextOnly, maxWarningLength = 100),
    )

    private fun entry(text: String = "", warning: String = "", media: List<DraftMedia> = emptyList()) =
        ComposerEntryState(text = text, warning = warning, warningEnabled = warning.isNotEmpty(), media = media)

    private fun image() = DraftMedia(id = "m", mimeType = "image/png", width = 1, height = 1, byteSize = 1L)

    @Test
    fun mastodonCounterDropsWhenAContentWarningIsAdded() {
        assertEquals(495, entry("hello").remaining(mastodon))
        assertEquals(492, entry("hello", warning = "abc").remaining(mastodon))
    }

    @Test
    fun aHiddenContentWarningDoesNotCount() {
        val hidden = entry("hello", warning = "abc").copy(warningEnabled = false)
        assertEquals(495, hidden.remaining(mastodon))
    }

    @Test
    fun misskeyCounterIgnoresTheWarningAndTheWarningHasItsOwnCount() {
        val plain = entry("hello")
        val warned = entry("hello", warning = "abc")
        assertEquals(2995, plain.remaining(misskey))
        assertEquals(2995, warned.remaining(misskey))
        assertNull(plain.warningRemaining(mastodon))
        assertEquals(97, warned.warningRemaining(misskey))
    }

    @Test
    fun overTheLimitBlocksPost() {
        val over = entry("a".repeat(501))
        assertEquals(-1, over.remaining(mastodon))
        assertTrue(over.exceedsLimit(mastodon))
        assertFalse(ComposerEditorState(entries = listOf(over)).postable(mastodon))
        assertTrue(ComposerEditorState(entries = listOf(entry("a".repeat(500)))).postable(mastodon))
    }

    @Test
    fun aWarningOverItsOwnLimitBlocksPostOnMisskey() {
        val over = entry("hello", warning = "w".repeat(101))
        assertEquals(-1, over.warningRemaining(misskey))
        assertFalse(ComposerEditorState(entries = listOf(over)).postable(misskey))
    }

    @Test
    fun anOverLimitFollowUpBlocksPostForTheWholeThread() {
        val state = ComposerEditorState(entries = listOf(entry("fine"), entry("a".repeat(501))))
        assertFalse(state.postable(mastodon))
    }

    @Test
    fun everyEntryNeedsTextOrMedia() {
        assertFalse(ComposerEditorState(entries = listOf(entry("one"), entry("  "))).postable(PostLimits()))
        assertTrue(ComposerEditorState(entries = listOf(entry("one"), entry(media = listOf(image())))).postable(PostLimits()))
    }

    @Test
    fun withoutAServerLimitNoCounterShowsAndNothingBlocks() {
        assertNull(entry("hello").remaining(PostLimits()))
        assertTrue(ComposerEditorState(entries = listOf(entry("a".repeat(10_000)))).postable(PostLimits()))
    }

    @Test
    fun preparedTextAppliesToEveryEntry() {
        val state = ComposerEditorState(entries = listOf(entry("one"), entry("two")))
        assertEquals(listOf("ONE", "TWO"), state.withPreparedText(String::uppercase).entries.map { it.text })
    }

    @Test
    fun emojiReplacesTheSelectionAndMovesTheCursorAfterIt() {
        val field = CursorField(5)
        field.selection = androidx.compose.ui.text.TextRange(2, 4)
        assertEquals("he:x:o", field.insert("hello", ":x:"))
        assertEquals(5, field.selection.start)
        assertEquals(5, field.selection.end)
    }

    @Test
    fun emojiInsertsAtACursorWithoutSelection() {
        val field = CursorField(0)
        field.selection = androidx.compose.ui.text.TextRange(1)
        assertEquals("a:x:bc", field.insert("abc", ":x:"))
    }

    @Test
    fun aCursorPastAShortenedTextMovesToTheEnd() {
        val field = CursorField(10)
        field.clamp(3)
        assertEquals(3, field.selection.max)
        assertEquals("abc:x:", field.insert("abc", ":x:"))
    }

    private class PublishRecorder : ComposerContract.Actions {
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
    fun publishCleansLinksOfEveryEntryThroughThePrepareHook() {
        val account = Account(AccountId(Connection("https://example.org", Protocol.MASTODON), "me"), "Me", "@me@example.org")
        val recorder = PublishRecorder()
        val owner = ComposerOwner(mutableStateOf(ComposerEditorState()))
        owner.context = ComposerOwnerContext(
            account = account,
            contract = ComposerContract(
                postPreferences = PostPreferences(),
                availableAudiences = emptySet(),
                canPublish = true,
                publishing = false,
                error = null,
                actions = recorder,
                prepareText = { text -> text.replace("?utm_source=x", "") },
            ),
        )
        owner.draftsContract = DraftsContract(SavingDrafts())
        owner.setText("see https://example.org/a?utm_source=x")
        val second = owner.addEntryAfter(owner.firstEntryId)!!
        owner.setEntryText(second, "and https://example.org/b?utm_source=x")

        owner.publish { _, _ -> }

        assertEquals(
            listOf("see https://example.org/a", "and https://example.org/b"),
            recorder.published.single().entries.map { it.text },
        )
    }
}
