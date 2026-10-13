package me.foxtails.palustris.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.PostingCapabilities
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.UploadCompression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        compose.onNodeWithTag("favourite_emoji_input").assertDoesNotExist()
    }

    @Test
    fun aPresetBecomesTheFavouriteReaction() {
        var preferences by mutableStateOf(PostPreferences())
        showFavourite({ preferences }) { preferences = preferences.copy(favouriteEmoji = it) }

        compose.onNodeWithTag("favourite_emoji_❤️").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("favourite_emoji_⭐").performScrollTo().assertIsNotSelected().performClick()

        assertEquals("⭐", preferences.favouriteEmoji)
        compose.onNodeWithTag("favourite_emoji_⭐").assertIsSelected()
        compose.onNodeWithTag("favourite_emoji_❤️").assertIsNotSelected()
    }

    @Test
    fun aTypedEmojiBecomesTheFavouriteReactionAndShowsAsASelectedChoice() {
        var preferences by mutableStateOf(PostPreferences())
        showFavourite({ preferences }) { preferences = preferences.copy(favouriteEmoji = it) }

        compose.onNodeWithTag("favourite_emoji_use").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("favourite_emoji_input").performTextInput(" 🦊 ")
        compose.onNodeWithTag("favourite_emoji_use").assertIsEnabled().performClick()

        assertEquals("🦊", preferences.favouriteEmoji)
        compose.onNodeWithTag("favourite_emoji_🦊").performScrollTo().assertIsSelected()
    }

    @Test
    fun textThatIsNotAUnicodeEmojiCannotBecomeTheFavouriteReaction() {
        val chosen = mutableListOf<String>()
        showFavourite({ PostPreferences() }) { chosen += it }

        compose.onNodeWithTag("favourite_emoji_input").performScrollTo().performTextInput(":blobcat:")
        compose.onNodeWithTag("favourite_emoji_use").assertIsNotEnabled()

        assertTrue(chosen.isEmpty())
        assertFalse(isFavouriteEmojiInput(""))
        assertFalse(isFavouriteEmojiInput("like"))
        assertFalse(isFavouriteEmojiInput("👍 👍"))
        assertFalse(isFavouriteEmojiInput("👍".repeat(20)))
        assertTrue(isFavouriteEmojiInput("❤️"))
        assertTrue(isFavouriteEmojiInput("👨‍👩‍👧"))
    }

    private fun showFavourite(preferences: () -> PostPreferences, onFavouriteEmoji: (String) -> Unit) {
        compose.setContent {
            PostingSettingsScreen(
                preferences = preferences(),
                actions = PostingSettingsActions(onFavouriteEmoji = onFavouriteEmoji),
                support = PostingSettingsSupport(favouriteEmoji = true),
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
