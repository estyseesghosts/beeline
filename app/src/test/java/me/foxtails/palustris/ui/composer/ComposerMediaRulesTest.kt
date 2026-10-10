package me.foxtails.palustris.ui.composer

import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.DraftMedia
import me.foxtails.palustris.domain.DraftMediaImportError
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.PostLimits
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.PostingCapabilities
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ThreadPublication
import me.foxtails.palustris.domain.ThreadPublishListener
import me.foxtails.palustris.domain.UploadCompression
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
class ComposerMediaRulesTest {
    private val account = Account(AccountId(Connection("https://example.org", Protocol.MASTODON), "me"), "Me", "@me@example.org")
    private val mastodon = PostLimits(500, PostingCapabilities(maxAttachments = 4, quoteWithMedia = false))

    private fun image(id: String, type: String = "image/png") =
        DraftMedia(id = id, mimeType = type, width = 4, height = 3, byteSize = 9L)

    private fun entry(vararg media: DraftMedia, text: String = "") = ComposerEntryState(text = text, media = media.toList())

    private fun state(first: ComposerEntryState, quote: Boolean = false) = ComposerEditorState(
        entries = listOf(first),
        quoteOf = if (quote) EntityId("https://example.org", "q") else null,
    )

    private class ImportingDrafts(private val reject: Set<Int> = emptySet()) : DraftsContract.Actions {
        val sources = mutableListOf<Uri>()
        val draftIds = mutableListOf<String>()
        override fun load(onResult: (List<PostDraft>) -> Unit, onError: (String) -> Unit) = onResult(emptyList())
        override fun save(draft: PostDraft, onResult: (PostDraft) -> Unit, onError: () -> Unit) = onResult(draft)
        override fun delete(draftId: String, onDone: () -> Unit, onError: (String) -> Unit) = onDone()
        override fun importMedia(
            draftId: String,
            source: Uri,
            onResult: (DraftMedia) -> Unit,
            onError: (DraftMediaImportError) -> Unit,
        ) {
            val index = sources.size
            sources += source
            draftIds += draftId
            if (index in reject) onError(DraftMediaImportError.NotAnImage) else onResult(DraftMedia(mimeType = "image/png"))
        }
    }

    private class Publications : ComposerContract.Actions {
        val published = mutableListOf<ThreadPublication>()
        override fun publish(publication: ThreadPublication, listener: ThreadPublishListener) {
            published += publication
        }
    }

    private fun owner(
        drafts: DraftsContract.Actions = ImportingDrafts(),
        preferences: PostPreferences = PostPreferences(),
        limits: PostLimits = mastodon,
        actions: ComposerContract.Actions = Publications(),
    ): ComposerOwner {
        val owner = ComposerOwner(mutableStateOf(ComposerEditorState()))
        owner.context = ComposerOwnerContext(
            account = account,
            contract = ComposerContract(
                postPreferences = preferences,
                availableAudiences = emptySet(),
                canPublish = true,
                publishing = false,
                error = null,
                actions = actions,
                limits = limits,
            ),
        )
        owner.draftsContract = DraftsContract(drafts)
        return owner
    }

    private fun uris(count: Int) = (1..count).map { Uri.parse("content://media/$it") }

    @Test
    fun pickingSixImagesWithFourSlotsKeepsFourAndReportsTwoDropped() {
        val drafts = ImportingDrafts()
        val owner = owner(drafts)
        var report: MediaImportReport? = null
        owner.importImages(owner.firstEntryId, uris(6), slots = 4) { report = it }

        assertEquals(4, owner.editor.first.media.size)
        assertEquals(MediaImportReport(added = 4, dropped = 2, error = null), report)
        assertEquals(4, drafts.sources.size)
    }

    @Test
    fun aRejectedFileDoesNotStopTheOthers() {
        val owner = owner(ImportingDrafts(reject = setOf(1)))
        var report: MediaImportReport? = null
        owner.importImages(owner.firstEntryId, uris(3), slots = 4) { report = it }

        assertEquals(2, owner.editor.first.media.size)
        assertEquals(DraftMediaImportError.NotAnImage, report?.error)
        assertEquals(2, report?.added)
    }

    @Test
    fun everyImageOfAPickLandsUnderOneDraftIdThatLaterSavesReuse() {
        val drafts = ImportingDrafts()
        val owner = owner(drafts)
        owner.importImages(owner.firstEntryId, uris(2), slots = 4) {}

        assertEquals(1, drafts.draftIds.toSet().size)
        assertEquals(drafts.draftIds.first(), owner.mediaDraftId())
        assertNull(owner.editor.draftId)
        owner.setText("with pictures")
        owner.publish { _, _ -> }
        assertEquals(drafts.draftIds.first(), (owner.context.contract.actions as Publications).published.single().draftId)
    }

    @Test
    fun aPickWithNoFreeSlotImportsNothingAndNeverAllocatesADraftId() {
        val drafts = ImportingDrafts()
        val owner = owner(drafts)
        var report: MediaImportReport? = null
        owner.importImages(owner.firstEntryId, uris(2), slots = 0) { report = it }

        assertEquals(MediaImportReport(added = 0, dropped = 2, error = null), report)
        assertTrue(drafts.sources.isEmpty())
        assertNull(owner.editor.mediaDraftId)
    }

    @Test
    fun theMediaDraftIdSurvivesProcessRecreation() {
        val owner = owner()
        val id = owner.mediaDraftId()
        val saved = with(ComposerEditorState.Saver) {
            val scope = androidx.compose.runtime.saveable.SaverScope { true }
            scope.save(owner.editor)
        }
        val restored = ComposerEditorState.Saver.restore(saved!!)
        assertEquals(id, restored?.mediaDraftId)
    }

    @Test
    fun photoButtonStatesFollowSupportAccessLimitsAndQuote() {
        val limits = mastodon
        val plain = state(entry())
        val first = plain.first
        assertEquals(PhotoButtonState.Enabled, plain.photoButtonState(first, limits, CapabilityStatus.Supported, busy = false))
        assertEquals(PhotoButtonState.Enabled, plain.photoButtonState(first, limits, CapabilityStatus.Unknown, busy = false))
        assertEquals(PhotoButtonState.Disabled, plain.photoButtonState(first, limits, CapabilityStatus.Unsupported, busy = false))
        assertEquals(PhotoButtonState.Disabled, plain.photoButtonState(first, limits, CapabilityStatus.Supported, busy = true))
        assertEquals(PhotoButtonState.SignInAgain, plain.photoButtonState(first, limits, CapabilityStatus.Denied, busy = false))

        val full = state(entry(image("a"), image("b"), image("c"), image("d")))
        assertEquals(0, full.first.imageSlots(limits))
        assertEquals(PhotoButtonState.Disabled, full.photoButtonState(full.first, limits, CapabilityStatus.Supported, busy = false))

        val quoting = state(entry(), quote = true)
        assertEquals(PhotoButtonState.Disabled, quoting.photoButtonState(quoting.first, limits, CapabilityStatus.Supported, busy = false))
        val misskeyLike = limits.copy(posting = limits.posting.copy(quoteWithMedia = true))
        assertEquals(PhotoButtonState.Enabled, quoting.photoButtonState(quoting.first, misskeyLike, CapabilityStatus.Supported, busy = false))
    }

    @Test
    fun anUnreportedLimitLeavesSlotsUnbounded() {
        assertNull(entry(image("a")).imageSlots(PostLimits()))
    }

    @Test
    fun askMeansAQuestionOnlyForACompressibleImageOnAServerThatLeavesItToTheClient() {
        val ask = PostPreferences(uploadCompression = UploadCompression.Ask)
        val client = PostLimits(posting = PostingCapabilities(clientCompression = true))

        fun asks(preferences: PostPreferences, limits: PostLimits, type: String): Boolean {
            val owner = owner(preferences = preferences, limits = limits)
            owner.addMedia(owner.firstEntryId, image("m", type))
            return owner.mustAskCompression()
        }
        assertTrue(asks(ask, client, "image/png"))
        assertTrue(asks(ask, client, "image/jpeg"))
        assertFalse("a GIF never compresses", asks(ask, client, "image/gif"))
        assertFalse(asks(ask.copy(uploadCompression = UploadCompression.Always), client, "image/png"))
        assertFalse(asks(ask.copy(uploadCompression = UploadCompression.Never), client, "image/png"))
        assertFalse("the server compresses", asks(ask, PostLimits(), "image/png"))
    }

    @Test
    fun theSettingDecidesWhenNobodyAnswered() {
        fun compressFor(setting: UploadCompression): Boolean {
            val actions = Publications()
            val owner = owner(preferences = PostPreferences(uploadCompression = setting), actions = actions)
            owner.setText("hello")
            owner.publish { _, _ -> }
            return actions.published.single().compress
        }
        assertTrue(compressFor(UploadCompression.Always))
        assertFalse(compressFor(UploadCompression.Never))
        assertTrue(compressFor(UploadCompression.Ask))
    }

    @Test
    fun theCompressionAnswerBeatsTheSettingForOnePublication() {
        val actions = Publications()
        val owner = owner(preferences = PostPreferences(uploadCompression = UploadCompression.Ask), actions = actions)
        owner.setText("hello")
        owner.compressChoice = false
        owner.publish { _, _ -> }
        owner.compressChoice = null

        assertFalse(actions.published.single().compress)
    }
}
