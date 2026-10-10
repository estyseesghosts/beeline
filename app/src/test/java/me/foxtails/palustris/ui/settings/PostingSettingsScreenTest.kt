package me.foxtails.palustris.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.PostingCapabilities
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.UploadCompression
import org.junit.Assert.assertEquals
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

    private fun show(capabilities: ServerCapabilities, onChoose: (UploadCompression) -> Unit = {}) {
        compose.setContent {
            PostingSettingsScreen(
                preferences = PostPreferences(),
                onDefaultAudience = {},
                onRepliesUnlisted = {},
                showUploadCompression = capabilities.posting.clientCompression,
                onUploadCompression = onChoose,
            )
        }
    }
}
