package me.foxtails.palustris.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.PostingCapabilities
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.UploadCompression
import me.foxtails.palustris.ui.emoji.EmojiCatalogState
import me.foxtails.palustris.ui.shell.EmojiPresentation
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
class PostingSettingsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val misskey = ServerCapabilities(posting = PostingCapabilities(clientCompression = true))
    private val mastodon = ServerCapabilities(posting = PostingCapabilities(clientCompression = false))

    @Test
    fun compressionRowsShowWhenTheServerReportsClientCompression() {
        show(misskey)

        compose.onNodeWithText("Image compression").assertExists()
        compose.onNodeWithText("Always compress").assertExists()
    }

    @Test
    fun compressionRowsAreAbsentWhenTheServerDoesNotReportIt() {
        show(mastodon)

        compose.onNodeWithText("Image compression").assertDoesNotExist()
        compose.onNodeWithText("Always compress").assertDoesNotExist()
    }

    @Test
    fun choosingAnOptionReportsIt() {
        val chosen = mutableListOf<UploadCompression>()
        show(misskey) { chosen += it }

        compose.onNodeWithText("Ask each time").performClick()

        assertEquals(listOf(UploadCompression.Ask), chosen)
    }

    @Test
    fun thePageNamesItsAccountAndReportsAudienceAndReplyChoices() {
        var preferences by mutableStateOf(PostPreferences())
        compose.setContent {
            PostingSettingsScreen(
                preferences = preferences,
                actions = PostingSettingsActions(
                    onDefaultAudience = { preferences = preferences.copy(defaultAudience = it) },
                    onRepliesUnlisted = { preferences = preferences.copy(repliesUnlisted = it) },
                ),
                accountLabel = "@person@example.org",
            )
        }

        compose.onNodeWithText("These settings apply to @person@example.org.").assertExists()
        compose.onNodeWithText("Public").assertIsSelected()
        compose.onNodeWithText("Followers only").assertIsNotSelected().performClick()
        compose.onNodeWithTag("posting_replies_unlisted").assertIsOff().performClick()

        compose.onNodeWithText("Followers only").assertIsSelected()
        compose.onNodeWithTag("posting_replies_unlisted").assertIsOn()
        assertEquals(Audience.Followers, preferences.defaultAudience)
        assertTrue(preferences.repliesUnlisted)
    }

    @Test
    fun theFavouriteReactionIsAbsentWhereAFavouriteIsNotAReaction() {
        compose.setContent {
            PostingSettingsScreen(PostPreferences(), PostingSettingsActions(), PostingSettingsSupport(favouriteEmoji = false))
        }

        compose.onNodeWithText("Favourite reaction").assertDoesNotExist()
        compose.onNodeWithTag("favourite_emoji_row").assertDoesNotExist()
    }

    @Test
    fun theRowShowsTheStoredReactionAndThePickerStoresAUnicodeEmoji() {
        var preferences by mutableStateOf(PostPreferences())
        showFavourite({ preferences }) { preferences = preferences.copy(favouriteEmoji = it) }

        compose.onNodeWithTag("favourite_emoji_current", useUnmergedTree = true).assertTextEquals("❤️")
        compose.onNodeWithTag("emoji_picker_sheet").assertDoesNotExist()
        compose.onNodeWithTag("favourite_emoji_row").performScrollTo().performClick()
        compose.onNodeWithTag("emoji_picker_sheet").assertExists()
        compose.onNodeWithTag("emoji_picker_cell_👍").performClick()
        compose.waitForIdle()

        assertEquals("👍", preferences.favouriteEmoji)
        compose.onNodeWithTag("emoji_picker_sheet").assertDoesNotExist()
        compose.onNodeWithTag("favourite_emoji_current", useUnmergedTree = true).assertTextEquals("👍")
    }

    @Test
    fun thePickerStoresTheSubmissionIdentityOfACustomServerEmoji() {
        var preferences by mutableStateOf(PostPreferences())
        var loads = 0
        val custom = CustomEmoji("blobcat", animatedUrl = null, staticUrl = null, submissionValue = ":blobcat:")
        val emoji = EmojiPresentation(
            catalog = EmojiCatalogState(items = listOf(custom), hasSnapshot = true),
            capabilities = EmojiCapabilities(),
            actions = object : EmojiPresentation.Actions {
                override fun loadCatalog() { loads++ }
                override fun retryCatalog() = Unit
                override fun toggleGroupCollapsed(groupId: String) = Unit
                override fun toggleGroupPinned(groupId: String) = Unit
                override fun togglePinnedEmoji(identity: String) = Unit
            },
        )
        showFavourite({ preferences }, emoji) { preferences = preferences.copy(favouriteEmoji = it) }

        compose.onNodeWithTag("favourite_emoji_row").performScrollTo().performClick()
        compose.onNodeWithTag("emoji_picker_cell_:blobcat:").performClick()
        compose.waitForIdle()

        assertEquals(":blobcat:", preferences.favouriteEmoji)
        // The catalog owner is asked to load, so the image of a stored custom emoji can show.
        assertTrue(loads > 0)
        // The stored custom emoji shows as its image, not as its text.
        compose.onNodeWithTag("favourite_emoji_current", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun showFavourite(
        preferences: () -> PostPreferences,
        emoji: EmojiPresentation = EmojiPresentation.Empty,
        onFavouriteEmoji: (String) -> Unit,
    ) {
        compose.setContent {
            PostingSettingsScreen(
                preferences = preferences(),
                actions = PostingSettingsActions(onFavouriteEmoji = onFavouriteEmoji),
                support = PostingSettingsSupport(favouriteEmoji = true),
                emoji = emoji,
            )
        }
    }

    private fun show(capabilities: ServerCapabilities, onChoose: (UploadCompression) -> Unit = {}) {
        compose.setContent {
            PostingSettingsScreen(
                preferences = PostPreferences(),
                actions = PostingSettingsActions(onUploadCompression = onChoose),
                support = PostingSettingsSupport(uploadCompression = capabilities.posting.clientCompression),
            )
        }
    }
}
