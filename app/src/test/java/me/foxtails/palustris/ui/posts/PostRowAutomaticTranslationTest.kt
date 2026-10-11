package me.foxtails.palustris.ui.posts

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Translation
import me.foxtails.palustris.ui.PalustrisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A row reports itself to the owner while the setting is on, and shows the answer without a tap. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class PostRowAutomaticTranslationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val account = Account(AccountId(connection, "a"), "Author", "@a@example.org")
    private val post = Post(EntityId(connection.origin, "p1"), account, "hola amigos", 0, Audience.Public)

    private class FakePresentation(override val automatic: Boolean) : PostTranslationPresentation {
        val entries = mutableStateMapOf<EntityId, TranslationEntry>()
        val requested = mutableListOf<String>()
        override fun entry(id: EntityId) = entries[id]
        override fun translate(ownedPost: OwnedPost, targetLanguage: String) = Unit
        override suspend fun requestAutomatic(ownedPost: OwnedPost, targetLanguage: String) {
            requested += ownedPost.post.id.value
            entries[ownedPost.post.id] = TranslationEntry.Ready(Translation("hello friends", sourceLanguage = "es"))
        }
        override fun toggleOriginal(id: EntityId) = Unit
        override fun dismissFailure(id: EntityId) = Unit
    }

    private fun show(presentation: PostTranslationPresentation) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    CompositionLocalProvider(LocalPostTranslations provides presentation) {
                        PostRow(
                            OwnedPost(account.id, post),
                            PostRowPresentation(availableActions = emptySet()),
                            PostRowEvents({}, {}, {}, {}, { _, _ -> }, null, null),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun settingOnShowsTheTranslationWithoutATap() {
        val presentation = FakePresentation(automatic = true)

        show(presentation)

        assertEquals(listOf("p1"), presentation.requested)
        compose.onNodeWithText("hello friends").assertIsDisplayed()
    }

    @Test
    fun settingOffAsksForNothingAndShowsTheOriginal() {
        val presentation = FakePresentation(automatic = false)

        show(presentation)

        assertEquals(emptyList<String>(), presentation.requested)
        compose.onNodeWithText("hola amigos").assertIsDisplayed()
        compose.onAllNodesWithText("hello friends").assertCountEquals(0)
    }
}
