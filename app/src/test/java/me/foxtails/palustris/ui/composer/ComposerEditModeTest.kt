package me.foxtails.palustris.ui.composer

import androidx.compose.runtime.mutableStateOf
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EditAttachment
import me.foxtails.palustris.domain.EditPostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Poll
import me.foxtails.palustris.domain.PollOption
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.PostEditSource
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ThreadPublication
import me.foxtails.palustris.domain.ThreadPublishListener
import me.foxtails.palustris.ui.UiStrings
import me.foxtails.palustris.ui.posts.PostEditOwner
import me.foxtails.palustris.ui.shell.ComposerContract
import me.foxtails.palustris.ui.shell.DraftsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComposerEditModeTest {
    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val accountId = AccountId(connection, "me")
    private val account = Account(accountId, "Me", "@me@example.org")
    private val stranger = Account(AccountId(connection, "other"), "Other", "@other@example.org")

    private fun ownPost(
        author: Account = account,
        poll: Poll? = null,
        attachments: List<Attachment> = emptyList(),
    ) = OwnedPost(
        accountId,
        Post(
            id = EntityId(connection.origin, "p1"),
            author = author,
            text = "<rendered>",
            publishedAtEpochMillis = 0,
            audience = Audience.Followers,
            poll = poll,
            attachments = attachments,
        ),
        sessionRevision = 1L,
    )

    private class EditActions : ComposerContract.Actions {
        private val me = AccountId(Connection("https://example.org", Protocol.MASTODON), "me")
        var source = PostEditSource("raw text", "cw")
        var loadError: String? = null
        var saveError: String? = null
        val sent = mutableListOf<Pair<EntityId, EditPostRequest>>()
        val loads = mutableListOf<EntityId>()
        override fun publish(publication: ThreadPublication, listener: ThreadPublishListener) = Unit
        override fun loadEditSource(id: EntityId, onResult: (PostEditSource) -> Unit, onError: (String) -> Unit) {
            loads += id
            loadError?.let { onError(it) } ?: onResult(source)
        }
        override fun editPost(id: EntityId, request: EditPostRequest, onResult: (OwnedPost) -> Unit, onError: (String) -> Unit) {
            sent += id to request
            saveError?.let { onError(it) } ?: onResult(OwnedPost(me, Post(id, Account(me, "Me", "@me"), "x", 0, Audience.Public), 1L))
        }
    }

    private class NoDrafts : DraftsContract.Actions {
        val saved = mutableListOf<PostDraft>()
        override fun load(onResult: (List<PostDraft>) -> Unit, onError: (String) -> Unit) = onResult(emptyList())
        override fun save(draft: PostDraft, onResult: (PostDraft) -> Unit, onError: () -> Unit) { saved += draft; onResult(draft) }
        override fun delete(draftId: String, onDone: () -> Unit, onError: (String) -> Unit) = onDone()
    }

    private fun owner(actions: EditActions, drafts: NoDrafts = NoDrafts()): ComposerOwner {
        val owner = ComposerOwner(
            mutableStateOf(ComposerEditorState()),
            UiStrings.from(androidx.test.core.app.ApplicationProvider.getApplicationContext()),
        )
        owner.context = ComposerOwnerContext(
            account = account,
            contract = ComposerContract(
                postPreferences = me.foxtails.palustris.domain.PostPreferences(),
                availableAudiences = setOf(Audience.Public, Audience.Followers),
                canPublish = true,
                publishing = false,
                error = null,
                actions = actions,
            ),
            canReply = true,
            canQuote = true,
        )
        owner.draftsContract = DraftsContract(drafts)
        return owner
    }

    @Test
    fun editOpensFilledFromTheRawSourceAndNavigatesToTheComposer() {
        val actions = EditActions()
        val owner = owner(actions)

        owner.requestEdit(ownPost())

        assertTrue(owner.navigation is ComposerNavigation.Edit)
        assertEquals(listOf(EntityId(connection.origin, "p1")), actions.loads)
        assertEquals("raw text", owner.editor.text)
        assertEquals("cw", owner.editor.warning)
        assertTrue(owner.editor.warningEnabled)
        assertFalse(requireNotNull(owner.editor.editing).loading)
        assertFalse(owner.hasChanges)
    }

    @Test
    fun editRefusesSomeoneElsesPost() {
        val actions = EditActions()
        val owner = owner(actions)

        owner.requestEdit(ownPost(author = stranger))

        assertNull(owner.navigation)
        assertTrue(actions.loads.isEmpty())
    }

    @Test
    fun aFailedSourceLoadShowsTheErrorAndKeepsSaveOff() {
        val actions = EditActions().apply { loadError = "no source" }
        val owner = owner(actions)

        owner.requestEdit(ownPost())

        assertEquals("no source", owner.editor.error)
        assertTrue(requireNotNull(owner.editor.editing).loading)
    }

    @Test
    fun saveSendsOnlyWhatChanged() {
        val actions = EditActions()
        val owner = owner(actions)
        owner.requestEdit(ownPost())

        owner.setText("changed text")
        var sent = false
        owner.publish { _, _ -> sent = true }

        val request = actions.sent.single().second
        assertEquals("changed text", request.text)
        assertNull(request.contentWarning)
        assertNull(request.attachments)
        assertNull(request.poll)
        assertTrue(sent)
        assertNull(owner.editor.editing)
    }

    @Test
    fun saveSendsAChangedWarningAndAltTextAndRemovedImages() {
        val actions = EditActions()
        val owner = owner(actions)
        owner.requestEdit(
            ownPost(
                attachments = listOf(
                    Attachment(url = "https://x/a.png", mimeType = "image/png", id = "m1", description = "old"),
                    Attachment(url = "https://x/b.png", mimeType = "image/png", id = "m2"),
                ),
            ),
        )

        owner.setWarning("new cw")
        owner.setEditAlt("m1", "new alt")
        owner.removeEditAttachment("m2")
        owner.publish { _, _ -> }

        val request = actions.sent.single().second
        assertEquals("new cw", request.contentWarning)
        assertEquals(listOf(EditAttachment("m1", "new alt")), request.attachments)
    }

    @Test
    fun anUnchangedPollIsNotSentButAChangedOneIs() {
        val poll = Poll("poll", listOf(PollOption("a", 1), PollOption("b", 2)), expiresAtEpochMillis = System.currentTimeMillis() + 3_600_000L)
        val actions = EditActions()
        val owner = owner(actions)
        owner.requestEdit(ownPost(poll = poll))
        owner.setText("x")
        owner.publish { _, _ -> }
        assertNull(actions.sent.single().second.poll)

        owner.requestEdit(ownPost(poll = poll))
        val current = requireNotNull(owner.editor.first.poll)
        owner.setPoll(owner.firstEntryId, current.copy(choices = listOf("a", "c")))
        owner.publish { _, _ -> }
        assertEquals(listOf("a", "c"), actions.sent.last().second.poll?.choices)
    }

    @Test
    fun anEndedPollThatIsLeftAloneDoesNotBlockTheSave() {
        val ended = Poll("poll", listOf(PollOption("a", 1), PollOption("b", 2)), expiresAtEpochMillis = 1_000L, expired = true)
        val actions = EditActions()
        val owner = owner(actions)
        val limits = me.foxtails.palustris.domain.PostLimits(
            posting = me.foxtails.palustris.domain.PostingCapabilities(
                poll = me.foxtails.palustris.domain.PollLimits(4, 50, 300L, 2_629_746L),
            ),
        )
        owner.requestEdit(ownPost(poll = ended))
        owner.setText("changed")

        assertTrue(owner.editor.postable(limits))
    }

    @Test
    fun aFailedSaveKeepsTheEditorAndShowsTheError() {
        val actions = EditActions().apply { saveError = "denied" }
        val owner = owner(actions)
        owner.requestEdit(ownPost())
        owner.setText("changed")

        owner.publish { _, _ -> }

        assertEquals("denied", owner.editor.error)
        assertNotNull(owner.editor.editing)
        assertFalse(owner.submitting)
    }

    @Test
    fun closingAnEditNeverMakesADraft() {
        val actions = EditActions()
        val drafts = NoDrafts()
        val owner = owner(actions, drafts)
        owner.requestEdit(ownPost())
        owner.setText("changed")

        var closed = false
        owner.save { closed = true }

        assertTrue(closed)
        assertTrue(drafts.saved.isEmpty())
        assertNull(owner.editor.editing)
    }

    @Test
    fun anEditEditorIsNotRestoredAfterTheProcessDies() {
        val actions = EditActions()
        val owner = owner(actions)
        owner.requestEdit(ownPost())
        owner.setText("changed")

        val saver = ComposerEditorState.Saver
        val saved = with(saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(owner.editor) }
        val restored = requireNotNull(saver.restore(requireNotNull(saved)))

        assertNull(restored.editing)
        assertEquals("", restored.text)
    }

    @Test
    fun theSessionEditShowsInRowsUntilAFresherCopyArrives() {
        val owner = PostEditOwner(accountId, 1L)
        val base = ownPost()
        val edited = base.post.copy(text = "edited", editedAtEpochMillis = 2_000L)
        owner.record(EntityId(connection.origin, "p1"), edited)

        assertEquals("edited", owner.applied(base).post.text)
        assertEquals(2_000L, owner.applied(base).post.editedAtEpochMillis)

        val fresher = base.copy(post = base.post.copy(text = "server newer", editedAtEpochMillis = 3_000L))
        assertEquals("server newer", owner.applied(fresher).post.text)
        val otherRevision = base.copy(sessionRevision = 2L)
        assertEquals("<rendered>", owner.applied(otherRevision).post.text)
    }
}
