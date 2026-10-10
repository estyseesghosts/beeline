package me.foxtails.palustris.ui.composer

import androidx.compose.runtime.mutableStateOf
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.DraftMedia
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.PostDraftEntry
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.UiStrings
import me.foxtails.palustris.ui.composer.ComposerEditorState
import me.foxtails.palustris.ui.composer.ComposerNavigation
import me.foxtails.palustris.ui.composer.ComposerOwner
import me.foxtails.palustris.ui.composer.ComposerOwnerContext
import me.foxtails.palustris.ui.shell.ComposerContract
import me.foxtails.palustris.ui.shell.DraftsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComposerOwnerTest {
    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val accountId = AccountId(connection, "owner")
    private val account = Account(accountId, "Owner", "@owner@example.org")
    private val otherAccount = Account(AccountId(connection, "other"), "Other", "@other@example.org")

    private fun post(
        id: String,
        author: Account = account,
        audience: Audience = Audience.Public,
        actionTargetId: EntityId? = null,
    ) = Post(
        id = EntityId(connection.origin, id),
        author = author,
        text = id,
        publishedAtEpochMillis = 0L,
        audience = audience,
        actionTargetId = actionTargetId,
    )

    private class RecordingDrafts : DraftsContract.Actions {
        val saved = mutableListOf<PostDraft>()
        val deleted = mutableListOf<String>()
        var failSave = false
        var loadResult: List<PostDraft> = emptyList()

        override fun load(onResult: (List<PostDraft>) -> Unit, onError: (String) -> Unit) = onResult(loadResult)
        override fun save(draft: PostDraft, onResult: (PostDraft) -> Unit, onError: () -> Unit) {
            if (failSave) onError() else { saved += draft; onResult(draft) }
        }
        override fun delete(draftId: String, onDone: () -> Unit, onError: (String) -> Unit) {
            deleted += draftId
            onDone()
        }
    }

    private class DeferredDrafts : DraftsContract.Actions {
        val saved = mutableListOf<PostDraft>()
        val deleted = mutableListOf<String>()
        var pendingSave: (() -> Unit)? = null

        override fun load(onResult: (List<PostDraft>) -> Unit, onError: (String) -> Unit) = onResult(emptyList())
        override fun save(draft: PostDraft, onResult: (PostDraft) -> Unit, onError: () -> Unit) {
            saved += draft
            pendingSave = { onResult(draft) }
        }
        override fun delete(draftId: String, onDone: () -> Unit, onError: (String) -> Unit) {
            deleted += draftId
            onDone()
        }
    }

    private class RecordingComposer : ComposerContract.Actions {
        val published = mutableListOf<CreatePostRequest>()
        var acceptedPost: OwnedPost? = null
        override fun publish(request: CreatePostRequest, onAccepted: (OwnedPost) -> Unit) {
            published += request
            acceptedPost?.let(onAccepted)
        }
    }

    private fun contract(
        actions: RecordingComposer,
        audiences: Set<Audience> = setOf(Audience.Public, Audience.Unlisted, Audience.Followers),
        preferences: PostPreferences = PostPreferences(),
    ) = ComposerContract(
        postPreferences = preferences,
        availableAudiences = audiences,
        canPublish = true,
        publishing = false,
        error = null,
        actions = actions,
    )

    private fun owner(
        composer: RecordingComposer = RecordingComposer(),
        drafts: DraftsContract.Actions = RecordingDrafts(),
        canReply: Boolean = true,
        canQuote: Boolean = true,
        composerOpen: Boolean = false,
        overlayOpen: Boolean = false,
    ): ComposerOwner {
        val owner = ComposerOwner(
            mutableStateOf(ComposerEditorState()),
            UiStrings.from(androidx.test.core.app.ApplicationProvider.getApplicationContext()),
        )
        owner.context = ComposerOwnerContext(
            account = account,
            contract = contract(composer),
            canReply = canReply,
            canQuote = canQuote,
            composerOpen = composerOpen,
            overlayOpen = overlayOpen,
        )
        owner.draftsContract = DraftsContract(drafts)
        return owner
    }

    @Test
    fun replyOpensForTheEffectiveActionTarget() {
        val owner = owner()
        owner.requestReply(OwnedPost(accountId, post("a", actionTargetId = EntityId(connection.origin, "orig"))))

        assertTrue(owner.navigation is ComposerNavigation.Reply)
        assertTrue(owner.isReply)
        assertEquals("orig", (owner.quoteTarget?.post?.actionTargetId ?: owner.quoteTarget?.post?.id)?.value)
    }

    @Test
    fun replyRejectsAForeignAccountPost() {
        val owner = owner()
        owner.requestReply(OwnedPost(otherAccount.id, post("foreign", author = otherAccount)))

        assertNull(owner.navigation)
        assertFalse(owner.isReply)
    }

    @Test
    fun replyRejectsWhileTheEditorIsDirty() {
        val owner = owner()
        owner.setText("unsaved text")
        owner.requestReply(OwnedPost(accountId, post("a")))

        assertNull(owner.navigation)
        assertFalse(owner.isReply)
        assertEquals("unsaved text", owner.editor.text)
    }

    @Test
    fun quoteSetsTheQuoteTarget() {
        val owner = owner()
        owner.requestQuote(OwnedPost(accountId, post("q")))

        assertTrue(owner.navigation is ComposerNavigation.Quote)
        assertFalse(owner.isReply)
        assertEquals("q", owner.quoteTarget?.post?.id?.value)
    }

    @Test
    fun draftRestoresTextWarningAndAudienceWithoutDirtyChanges() {
        val owner = owner()
        val draft = PostDraft(
            id = "draft-1",
            accountId = accountId,
            text = "saved body",
            audience = Audience.Unlisted,
            contentWarning = "spoiler",
        )
        owner.requestDraft(draft)

        assertEquals("draft-1", owner.editor.draftId)
        assertEquals("saved body", owner.editor.text)
        assertTrue(owner.editor.warningEnabled)
        assertEquals("spoiler", owner.editor.warning)
        assertEquals(Audience.Unlisted, owner.editor.audience)
        assertFalse(owner.hasChanges)
        assertTrue(owner.navigation is ComposerNavigation.Draft)
    }

    @Test
    fun saveClearsTheDirtyBaselineOnlyAfterSuccess() {
        val drafts = RecordingDrafts()
        val owner = owner(drafts = drafts)
        owner.setText("body to save")
        owner.save()

        assertEquals(1, drafts.saved.size)
        assertEquals("body to save", owner.editor.savedText)
        assertFalse(owner.hasChanges)
        assertNull(owner.editor.error)
        assertFalse(owner.closing)
    }

    @Test
    fun failedSaveKeepsTheTextForRecovery() {
        val drafts = RecordingDrafts().apply { failSave = true }
        val owner = owner(drafts = drafts)
        owner.setText("body to recover")
        owner.save()

        assertEquals("body to recover", owner.editor.text)
        assertTrue(owner.hasChanges)
        assertTrue(owner.editor.error != null)
        assertFalse(owner.closing)
    }

    @Test
    fun publishRejectsAnUnavailableAudienceWithoutPublishing() {
        val composer = RecordingComposer()
        val owner = owner(composer = composer)
        owner.setText("body")
        owner.setAudience(Audience.Direct)
        owner.publish { _, _ -> }

        assertTrue(composer.published.isEmpty())
        assertEquals("This audience is not available on this server.", owner.editor.error)
    }

    @Test
    fun publishSavesThenPublishesAndClearsOnAcceptance() {
        val composer = RecordingComposer().apply { acceptedPost = OwnedPost(accountId, post("sent")) }
        val drafts = RecordingDrafts()
        val owner = owner(composer = composer, drafts = drafts)
        owner.setText("hello world")
        var sentReply = true
        var sentQuote = true
        owner.publish { reply, quote ->
            sentReply = reply
            sentQuote = quote
        }

        assertEquals(1, composer.published.size)
        assertEquals("hello world", composer.published.single().text)
        assertFalse(sentReply)
        assertFalse(sentQuote)
        assertEquals("", owner.editor.text)
        assertFalse(owner.hasChanges)
        assertEquals(1, drafts.deleted.size)
    }

    @Test
    fun sessionReplacementClearsRestoredTargets() {
        val owner = owner()
        owner.requestReply(OwnedPost(accountId, post("a")))
        owner.consumeNavigation()
        owner.resetTargets()

        assertNull(owner.quoteTarget)
        assertFalse(owner.isReply)
        assertNull(owner.navigation)
    }

    @Test
    fun duplicatePublishIsRejectedWhileASaveIsPending() {
        val drafts = DeferredDrafts()
        val composer = RecordingComposer()
        val owner = owner(composer = composer, drafts = drafts)
        owner.setText("body")
        owner.publish { _, _ -> }
        owner.publish { _, _ -> }

        assertEquals(1, drafts.saved.size)
        assertTrue(owner.submitting)
        assertTrue(composer.published.isEmpty())
    }

    @Test
    fun obsoleteSaveCallbackCannotPublishAfterSessionReplacement() {
        val drafts = DeferredDrafts()
        val composer = RecordingComposer().apply { acceptedPost = OwnedPost(accountId, post("sent")) }
        val owner = owner(composer = composer, drafts = drafts)
        owner.setText("body")
        owner.publish { _, _ -> }
        owner.sessionRevision = 7L
        drafts.pendingSave?.invoke()

        assertTrue(composer.published.isEmpty())
        assertFalse(owner.submitting)
    }

    @Test
    fun newerEditsDuringPublishSurviveAcceptance() {
        val drafts = DeferredDrafts()
        val composer = RecordingComposer().apply { acceptedPost = OwnedPost(accountId, post("sent")) }
        val owner = owner(composer = composer, drafts = drafts)
        owner.setText("first")
        owner.publish { _, _ -> }
        owner.setText("first and second")
        drafts.pendingSave?.invoke()

        assertEquals("first", composer.published.single().text)
        assertEquals("first and second", owner.editor.text)
        assertFalse(owner.submitting)
    }

    private fun roundTrip(state: ComposerEditorState): ComposerEditorState {
        val saver = ComposerEditorState.Saver
        val saved = with(saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(state) }
        return saver.restore(saved!!)!!
    }

    private fun restoredOwner(
        state: ComposerEditorState,
        drafts: DraftsContract.Actions = RecordingDrafts(),
        revision: Long = 0L,
        restoredAccount: Account = account,
    ): ComposerOwner {
        val owner = ComposerOwner(
            mutableStateOf(state),
            UiStrings.from(androidx.test.core.app.ApplicationProvider.getApplicationContext()),
        )
        owner.context = ComposerOwnerContext(account = restoredAccount, contract = contract(RecordingComposer()))
        owner.draftsContract = DraftsContract(drafts)
        owner.sessionRevision = revision
        return owner
    }

    private fun replyState(): ComposerEditorState {
        val source = owner()
        source.requestReply(OwnedPost(accountId, post("a", audience = Audience.Followers)))
        source.setText("typed reply")
        return roundTrip(source.editor)
    }

    @Test
    fun restoredReplyKeepsItsVerifiedTargetForTheSameSession() {
        val restored = restoredOwner(replyState())
        restored.bindSession()

        assertTrue(restored.isReply)
        assertEquals("typed reply", restored.editor.text)
        assertEquals("a", restored.quoteTarget?.post?.id?.value)
        assertEquals(Audience.Followers, restored.editor.audience)
    }

    @Test
    fun restoredReplyIsClearedForAnotherAccountInsteadOfBecomingANewPost() {
        val restored = restoredOwner(replyState(), restoredAccount = otherAccount)
        restored.bindSession()

        assertFalse(restored.isReply)
        assertNull(restored.quoteTarget)
        assertEquals("", restored.editor.text)
    }

    @Test
    fun restoredReplyUnderANewRevisionBecomesAReplyDraft() {
        val drafts = RecordingDrafts()
        val restored = restoredOwner(replyState(), drafts = drafts, revision = 3L)
        restored.bindSession()

        assertEquals(1, drafts.saved.size)
        assertEquals("a", drafts.saved.single().replyTo?.value)
        assertEquals("typed reply", drafts.saved.single().text)
        assertFalse(restored.isReply)
        assertEquals("", restored.editor.text)
    }

    @Test
    fun failedDraftSaveKeepsTheReplyEditableOnTheNewRevision() {
        val drafts = RecordingDrafts().apply { failSave = true }
        val restored = restoredOwner(replyState(), drafts = drafts, revision = 3L)
        restored.bindSession()

        assertTrue(restored.isReply)
        assertEquals("typed reply", restored.editor.text)
        assertEquals(3L, restored.editor.boundRevision)
        assertTrue(restored.editor.error != null)
    }

    @Test
    fun newRequestOnARestoredReplyKeepsItsAudience() {
        val restored = restoredOwner(replyState())
        restored.bindSession()
        restored.requestNew()

        assertEquals(Audience.Followers, restored.editor.audience)
        assertTrue(restored.isReply)
    }

    @Test
    fun legacyEditorSnapshotsRestoreWithoutTargets() {
        val saver = ComposerEditorState.Saver
        val legacy = listOf(null, "body", "", "", "", false, "Public", "Public", null)
        val restored = saver.restore(legacy)!!

        assertEquals("body", restored.text)
        assertFalse(restored.hasTargets)
    }

    private fun media(id: String, alt: String? = null) = DraftMedia(id = id, mimeType = "image/png", width = 4, height = 3, byteSize = 9L, altText = alt)

    private fun threadOwner(drafts: DraftsContract.Actions = RecordingDrafts()): Pair<ComposerOwner, List<String>> {
        val owner = owner(drafts = drafts)
        owner.setText("one")
        val second = owner.addEntryAfter(owner.firstEntryId)!!
        owner.setEntryText(second, "two")
        val third = owner.addEntryAfter(second)!!
        owner.setEntryText(third, "three")
        owner.addMedia(third, media("m3"))
        return owner to listOf(owner.firstEntryId, second, third)
    }

    @Test
    fun threeEntriesSurviveSaveAndRestore() {
        val (owner, ids) = threadOwner()
        owner.setAltText(ids[2], "m3", "a cat")
        val restored = restoredOwner(roundTrip(owner.editor))

        assertEquals(ids, restored.editor.entries.map { it.id })
        assertEquals(listOf("one", "two", "three"), restored.editor.entries.map { it.text })
        assertEquals("a cat", restored.editor.entries[2].media.single().altText)
        assertEquals(owner.hasChanges, restored.hasChanges)
    }

    @Test
    fun dirtyCheckSeesAChangeInTheSecondEntry() {
        val drafts = RecordingDrafts()
        val (owner, ids) = threadOwner(drafts)
        owner.save()
        assertFalse(owner.hasChanges)

        owner.setEntryText(ids[1], "two, edited")
        assertTrue(owner.hasChanges)
        owner.setEntryText(ids[1], "two")
        assertFalse(owner.hasChanges)
        owner.removeEntry(ids[2])
        assertTrue(owner.hasChanges)
    }

    @Test
    fun savedDraftSplitsTheFirstEntryFromFollowUps() {
        val drafts = RecordingDrafts()
        val (owner, ids) = threadOwner(drafts)
        owner.save()

        val saved = drafts.saved.single()
        assertEquals("one", saved.text)
        assertEquals(listOf(ids[1], ids[2]), saved.followUps.map { it.id })
        assertEquals(listOf("m3"), saved.allMedia.map { it.id })
    }

    @Test
    fun replyTargetStaysOnTheFirstEntry() {
        val owner = owner()
        owner.requestReply(OwnedPost(accountId, post("a")))
        val second = owner.addEntryAfter(owner.firstEntryId)!!
        owner.setEntryText(second, "follow-up")
        owner.removeEntry(owner.firstEntryId)

        assertEquals(2, owner.editor.entries.size)
        assertTrue(owner.isReply)
        assertEquals("a", owner.editor.replyTo?.value)
    }

    @Test
    fun aDraftLoadsAsEntriesWithMedia() {
        val owner = owner()
        val draft = PostDraft(
            id = "d1",
            accountId = accountId,
            text = "first",
            media = listOf(media("m1")),
            followUps = listOf(PostDraftEntry("e2", "second", "cw", listOf(media("m2")))),
        )
        owner.requestDraft(draft)

        assertEquals(listOf("first", "second"), owner.editor.entries.map { it.text })
        assertTrue(owner.editor.entries[1].warningEnabled)
        assertEquals(listOf("m2"), owner.editor.entries[1].media.map { it.id })
        assertFalse(owner.hasChanges)
    }

    @Test
    fun unknownEntryIdsChangeNothing() {
        val owner = owner()
        assertNull(owner.addEntryAfter("missing"))
        owner.removeEntry(owner.firstEntryId)
        assertEquals(1, owner.editor.entries.size)
    }
}
