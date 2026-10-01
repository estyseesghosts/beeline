package me.foxtails.palustris.ui.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ModerationAccount
import me.foxtails.palustris.domain.Protocol
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A rejected continuation marks the list unsupported but keeps existing rows
 * visible. The unsupported notice trails the rows instead of replacing them.
 * An initially empty unsupported list keeps the full unsupported presentation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModerationListScreenTest {
    @get:Rule val compose = createComposeRule()

    private val connection = Connection("https://example.org", Protocol.MASTODON)

    private fun member(id: String) = ModerationAccount(
        Account(AccountId(connection, id), id, "@$id@example.org"),
    )

    private fun unsupportedText(): String =
        ApplicationProvider.getApplicationContext<Context>().getString(R.string.settings_moderation_unsupported)

    private fun show(state: ModerationUiState) {
        compose.setContent {
            ModerationListScreen(
                state = state,
                onRetry = {},
                onLoadMore = {},
                onRemove = {},
            )
        }
    }

    @Test
    fun unsupportedWithRowsKeepsRowsVisible() {
        show(ModerationUiState(accounts = listOf(member("a")), unsupported = true))

        compose.onNodeWithText("@a@example.org").assertIsDisplayed()
        compose.onNodeWithText(unsupportedText()).assertIsDisplayed()
    }

    @Test
    fun errorWithRowsKeepsRowsVisible() {
        show(ModerationUiState(accounts = listOf(member("a")), error = "page failed"))

        compose.onNodeWithText("@a@example.org").assertIsDisplayed()
        compose.onNodeWithText("page failed").assertIsDisplayed()
    }

    @Test
    fun unsupportedWithoutRowsShowsUnsupportedNotice() {
        show(ModerationUiState(unsupported = true))

        compose.onNodeWithText(unsupportedText()).assertIsDisplayed()
    }
}
