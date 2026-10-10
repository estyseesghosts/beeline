package me.foxtails.palustris.ui.composer

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import me.foxtails.palustris.domain.AccessStatus
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.DraftMedia
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
class ComposerMediaUiTest {
    @get:Rule val compose = createComposeRule()

    private val account = Account(AccountId(Connection("https://example.org", Protocol.MASTODON), "me"), "Me", "@me@example.org")

    private fun image(id: String, type: String = "image/png", alt: String? = null) =
        DraftMedia(id = id, mimeType = type, width = 4, height = 3, byteSize = 9L, altText = alt)

    private class Drafts : DraftsContract.Actions {
        val saved = mutableListOf<PostDraft>()
        override fun load(onResult: (List<PostDraft>) -> Unit, onError: (String) -> Unit) = onResult(emptyList())
        override fun save(draft: PostDraft, onResult: (PostDraft) -> Unit, onError: () -> Unit) {
            saved += draft
            onResult(draft)
        }
        override fun delete(draftId: String, onDone: () -> Unit, onError: (String) -> Unit) = onDone()
        override fun thumbnail(draftId: String, mediaId: String, maxEdge: Int, onResult: (Bitmap?) -> Unit) =
            onResult(Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888))
    }

    private class Actions : ComposerContract.Actions {
        val published = mutableListOf<ThreadPublication>()
        var signInRequests = 0
        override fun publish(publication: ThreadPublication, listener: ThreadPublishListener) {
            published += publication
        }
        override fun signInAgain() {
            signInRequests += 1
        }
    }

    private fun contract(
        actions: Actions,
        posting: PostingCapabilities = PostingCapabilities(maxAttachments = 4, maxAltTextLength = 10, clientCompression = true),
        mediaAccess: AccessStatus = AccessStatus.Granted,
        preferences: PostPreferences = PostPreferences(),
    ) = ComposerContract(
        postPreferences = preferences,
        availableAudiences = emptySet(),
        canPublish = true,
        publishing = false,
        error = null,
        actions = actions,
        limits = PostLimits(500, posting),
        mediaAccess = mediaAccess,
    )

    private fun ownerWith(entry: ComposerEntryState, contract: ComposerContract, drafts: Drafts = Drafts()): ComposerOwner {
        val owner = ComposerOwner(mutableStateOf(ComposerEditorState(entries = listOf(entry), mediaDraftId = "draft-1")))
        owner.context = ComposerOwnerContext(account = account, contract = contract)
        owner.draftsContract = DraftsContract(drafts)
        return owner
    }

    private fun showBody(owner: ComposerOwner, contract: ComposerContract) {
        compose.setContent {
            ComposerBody(owner, contract, account, onRequestEmoji = {}, pendingEmojiInsertion = null, onEmojiInsertionApplied = {})
        }
    }

    private fun showSurface(owner: ComposerOwner, contract: ComposerContract) {
        compose.setContent {
            ComposerSurfaceHost(
                owner = owner,
                contract = contract,
                account = account,
                card = false,
                onDismiss = {},
                onClose = {},
                onRequestEmoji = {},
                pendingEmojiInsertion = null,
                onEmojiInsertionApplied = {},
            )
        }
    }

    @Test
    fun anEntryWithOnlyMediaCanBePosted() {
        val actions = Actions()
        val contract = contract(actions)
        val owner = ownerWith(ComposerEntryState(media = listOf(image("m1"))), contract)
        showSurface(owner, contract)
        compose.onNodeWithTag(COMPOSER_POST_TAG).assertIsEnabled().performClick()
        compose.waitForIdle()

        assertEquals(1, actions.published.size)
        assertEquals("m1", actions.published.single().entries.single().media.single().id)
        assertEquals("draft-1", actions.published.single().draftId)
    }

    @Test
    fun thumbnailsShowAnAltBadgeAndRemoveKeepsTheOtherImages() {
        val actions = Actions()
        val contract = contract(actions)
        val owner = ownerWith(ComposerEntryState(text = "hi", media = listOf(image("a", alt = "A cat"), image("b"))), contract)
        showBody(owner, contract)
        compose.onNodeWithContentDescription("A cat").assertIsDisplayed()
        compose.onNodeWithContentDescription("Image without a description").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Remove image")[0].performClick()
        compose.waitForIdle()
        assertEquals(listOf("b"), owner.editor.first.media.map { it.id })
    }

    /** Shows the alt text content without a dialog window. */
    private fun showAltEditor(maxLength: Int?, onSave: (String?) -> Unit) {
        compose.setContent {
            var text by remember { mutableStateOf("") }
            Column {
                ComposerAltTextForm(image("a"), text, { text = it }, maxLength) { _, _, done -> done(null) }
                ComposerAltTextSave(text, maxLength, onSave)
            }
        }
    }

    @Test
    fun altTextLongerThanTheServerLimitCannotBeSaved() {
        var saved: String? = "untouched"
        showAltEditor(maxLength = 10) { saved = it }
        compose.onNodeWithText("Description for people who cannot see the image").performTextInput("a".repeat(11))
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("-1 characters remaining").assertIsDisplayed()
        assertEquals("untouched", saved)
    }

    @Test
    fun altTextAtTheLimitSavesAndBlankClearsIt() {
        var saved: String? = "untouched"
        showAltEditor(maxLength = 10) { saved = it }
        compose.onNodeWithText("Description for people who cannot see the image").performTextInput("a".repeat(10))
        compose.onNodeWithText("0 characters remaining").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals("a".repeat(10), saved)
    }

    @Test
    fun anAltTextWithoutALimitAlwaysSaves() {
        assertEquals(false, altTextOverLimit("x".repeat(5000), null))
        assertEquals(true, altTextOverLimit("x".repeat(11), 10))
    }

    @Test
    fun photoButtonIsDisabledWhenTheEntryIsFullOrUploadIsUnsupported() {
        val full = Actions()
        val fullContract = contract(full, PostingCapabilities(maxAttachments = 1))
        showBody(ownerWith(ComposerEntryState(text = "hi", media = listOf(image("a"))), fullContract), fullContract)
        compose.onNodeWithContentDescription("Add photo").assertIsNotEnabled()
    }

    @Test
    fun photoButtonIsDisabledWhenTheServerCannotUpload() {
        val actions = Actions()
        val contract = contract(actions, PostingCapabilities(maxAttachments = 4, mediaUpload = CapabilityStatus.Unsupported))
        showBody(ownerWith(ComposerEntryState(text = "hi"), contract), contract)
        compose.onNodeWithContentDescription("Add photo").assertIsNotEnabled()
    }

    @Test
    fun photoButtonStartsTheSignInAgainFlowWhenTheTokenCannotUpload() {
        val actions = Actions()
        val drafts = Drafts()
        val contract = contract(actions, PostingCapabilities(maxAttachments = 4, mediaUpload = CapabilityStatus.Supported), mediaAccess = AccessStatus.Denied)
        showBody(ownerWith(ComposerEntryState(text = "keep me"), contract, drafts), contract)
        compose.onNodeWithContentDescription("Add photo").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(1, actions.signInRequests)
        assertTrue("the editor is saved before the sign-in flow", drafts.saved.any { it.text == "keep me" })
    }

    private fun askContract(actions: Actions, setting: UploadCompression) =
        contract(actions, preferences = PostPreferences(uploadCompression = setting))

    @Test
    fun askWithAPngAsksOnceAndTheAnswerReachesThePublication() {
        val actions = Actions()
        val contract = askContract(actions, UploadCompression.Ask)
        showSurface(ownerWith(ComposerEntryState(text = "hi", media = listOf(image("a"))), contract), contract)
        compose.onNodeWithTag(COMPOSER_POST_TAG).performClick()
        compose.onNodeWithText("Compress images?").assertIsDisplayed()
        assertTrue(actions.published.isEmpty())
        compose.onNodeWithText("Keep originals").performClick()
        compose.waitForIdle()
        assertEquals(1, actions.published.size)
        assertEquals(false, actions.published.single().compress)
        compose.onNodeWithText("Compress images?").assertDoesNotExist()
    }

    @Test
    fun askAnswerCompressSendsCompressTrue() {
        val actions = Actions()
        val contract = askContract(actions, UploadCompression.Ask)
        showSurface(ownerWith(ComposerEntryState(text = "hi", media = listOf(image("a"))), contract), contract)
        compose.onNodeWithTag(COMPOSER_POST_TAG).performClick()
        compose.onNodeWithText("Compress").performClick()
        compose.waitForIdle()
        assertEquals(true, actions.published.single().compress)
    }

    @Test
    fun askWithOnlyAGifPostsWithoutAsking() {
        val actions = Actions()
        val contract = askContract(actions, UploadCompression.Ask)
        showSurface(ownerWith(ComposerEntryState(text = "hi", media = listOf(image("g", "image/gif"))), contract), contract)
        compose.onNodeWithTag(COMPOSER_POST_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Compress images?").assertDoesNotExist()
        assertEquals(1, actions.published.size)
    }

    private fun postWithoutAsking(setting: UploadCompression): Boolean {
        val actions = Actions()
        val contract = askContract(actions, setting)
        showSurface(ownerWith(ComposerEntryState(text = "hi", media = listOf(image("a"))), contract), contract)
        compose.onNodeWithTag(COMPOSER_POST_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Compress images?").assertDoesNotExist()
        return actions.published.single().compress
    }

    @Test
    fun alwaysPostsCompressedWithoutAsking() = assertTrue(postWithoutAsking(UploadCompression.Always))

    @Test
    fun neverPostsOriginalsWithoutAsking() = assertEquals(false, postWithoutAsking(UploadCompression.Never))
}
