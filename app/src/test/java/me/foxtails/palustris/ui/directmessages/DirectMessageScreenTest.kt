package me.foxtails.palustris.ui.directmessages

import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ConversationId
import me.foxtails.palustris.domain.ConversationIdentity
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ThreadLimitation
import me.foxtails.palustris.ui.PalustrisTheme
import me.foxtails.palustris.ui.directmessages.DirectMessageConversationScreen
import me.foxtails.palustris.ui.directmessages.DirectMessageInboxScreen
import me.foxtails.palustris.ui.directmessages.DirectMessageUiState
import me.foxtails.palustris.ui.profile.ProfileScreen
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
class DirectMessageScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val owner = accountFixture("owner", "Owner")
    private val recipient = accountFixture("recipient", "Recipient")
    private val conversation = DirectConversation(
        id = ConversationId(connection.origin, "conversation"),
        participants = listOf(owner, recipient),
        lastPost = post("last", recipient, "A private message"),
        unread = true,
        rootPostId = EntityId(connection.origin, "root"),
        identity = ConversationIdentity.Verified,
    )

    @Test
    fun inboxDisplaysPrivateConversationAndOpensIt() {
        var opened: DirectConversation? = null
        show {
            DirectMessageInboxScreen(
                accountId = owner.id,
                state = DirectMessageUiState(conversations = listOf(conversation)),
                compactLayout = false,
                onOpenConversation = { opened = it },
            )
        }

        compose.onNodeWithText("Recipient").assertIsDisplayed().performClick()
        assertEquals(conversation.id, opened?.id)
        compose.onNodeWithText("A private message").assertIsDisplayed()
    }

    @Test
    fun conversationComposerSubmitsOwnedEditorText() {
        var sent: String? = null
        show {
            var text by remember { mutableStateOf("") }
            DirectMessageConversationScreen(
                accountId = owner.id,
                state = DirectMessageUiState(
                    selectedConversationId = conversation.id,
                    selectedConversation = conversation,
                    thread = listOf(conversation.lastPost),
                    editorText = text,
                ),
                compactLayout = false,
                onEditorTextChange = { text = it },
                onSend = { sent = text },
            )
        }

        compose.onNodeWithTag("direct_message_input").performTextInput("New private message")
        compose.onNodeWithTag("direct_message_send").performClick()

        assertEquals("New private message", sent)
        compose.onNodeWithTag("direct_message_thread").assertIsDisplayed()
    }

    @Test
    fun profileMessageActionReturnsTheDisplayedRemoteAccount() {
        var messaged: Account? = null
        show {
            ProfileScreen(
                account = recipient,
                authenticatedAccountId = owner.id,
                compactLayout = false,
                onMessage = { messaged = it },
            )
        }

        compose.onNodeWithTag("profile_message_action").performClick()

        assertEquals(recipient.id, messaged?.id)
    }

    @Test
    fun compactContinuationControlInvokesContinue() {
        var continued = 0
        show {
            DirectMessageConversationScreen(
                accountId = owner.id,
                state = DirectMessageUiState(
                    selectedConversationId = conversation.id,
                    selectedConversation = conversation,
                    recipient = recipient,
                    thread = listOf(conversation.lastPost),
                    threadCursor = "c1",
                ),
                compactLayout = true,
                onContinueThread = { continued += 1 },
            )
        }

        compose.onNodeWithTag("direct_message_thread_continue").assertIsDisplayed().performClick()
        assertEquals(1, continued)
    }

    @Test
    fun wideContinuationControlInvokesContinue() {
        var continued = 0
        show {
            DirectMessageConversationScreen(
                accountId = owner.id,
                state = DirectMessageUiState(
                    selectedConversationId = conversation.id,
                    selectedConversation = conversation,
                    recipient = recipient,
                    thread = listOf(conversation.lastPost),
                    threadCursor = "c1",
                ),
                compactLayout = false,
                onContinueThread = { continued += 1 },
            )
        }

        compose.onNodeWithTag("direct_message_thread_continue").assertIsDisplayed().performClick()
        assertEquals(1, continued)
    }

    @Test
    fun threadRetryAfterErrorInvokesRetry() {
        var retried = 0
        show {
            DirectMessageConversationScreen(
                accountId = owner.id,
                state = DirectMessageUiState(
                    selectedConversationId = conversation.id,
                    selectedConversation = conversation,
                    recipient = recipient,
                    thread = listOf(conversation.lastPost),
                    threadCursor = "c1",
                    threadError = "Thread failed",
                ),
                compactLayout = false,
                onRetryThread = { retried += 1 },
            )
        }

        compose.onNodeWithText("Thread failed").assertIsDisplayed()
        compose.onNodeWithTag("direct_message_thread_retry").assertIsDisplayed().performClick()
        assertEquals(1, retried)
    }

    @Test
    fun compactRetryFooterInvokesRetry() {
        var retried = 0
        show {
            DirectMessageConversationScreen(
                accountId = owner.id,
                state = DirectMessageUiState(
                    selectedConversationId = conversation.id,
                    selectedConversation = conversation,
                    recipient = recipient,
                    thread = listOf(conversation.lastPost),
                    threadError = "Thread failed",
                ),
                compactLayout = true,
                onRetryThread = { retried += 1 },
            )
        }

        compose.onNodeWithText("Thread failed").assertIsDisplayed()
        compose.onNodeWithTag("direct_message_thread_retry").assertIsDisplayed().performClick()
        assertEquals(1, retried)
    }

    @Test
    fun retryErrorHidesContinue() {
        show {
            DirectMessageConversationScreen(
                accountId = owner.id,
                state = DirectMessageUiState(
                    selectedConversationId = conversation.id,
                    selectedConversation = conversation,
                    recipient = recipient,
                    thread = listOf(conversation.lastPost),
                    threadCursor = "c1",
                    threadError = "Thread failed",
                ),
                compactLayout = true,
            )
        }

        compose.onNodeWithTag("direct_message_thread_retry").assertIsDisplayed()
        compose.onNodeWithTag("direct_message_thread_continue").assertDoesNotExist()
    }

    @Test
    fun continuingSpinnerShowsLoading() {
        show {
            DirectMessageConversationScreen(
                accountId = owner.id,
                state = DirectMessageUiState(
                    selectedConversationId = conversation.id,
                    selectedConversation = conversation,
                    recipient = recipient,
                    thread = listOf(conversation.lastPost),
                    threadCursor = "c1",
                    threadContinuing = true,
                ),
                compactLayout = true,
            )
        }

        compose.onNodeWithTag("direct_message_thread_loading").assertIsDisplayed()
        compose.onNodeWithTag("direct_message_thread_continue").assertDoesNotExist()
        compose.onNodeWithTag("direct_message_thread_retry").assertDoesNotExist()
    }

    @Test
    fun cursorPlusLimitationsShowsContinueNotPartial() {
        show {
            DirectMessageConversationScreen(
                accountId = owner.id,
                state = DirectMessageUiState(
                    selectedConversationId = conversation.id,
                    selectedConversation = conversation,
                    recipient = recipient,
                    thread = listOf(conversation.lastPost),
                    threadCursor = "c1",
                    threadLimitations = listOf(ThreadLimitation.UncertainServerTruncation),
                ),
                compactLayout = true,
            )
        }

        compose.onNodeWithTag("direct_message_thread_continue").assertIsDisplayed()
        compose.onNodeWithText("Some messages could not be loaded").assertDoesNotExist()
    }

    @Test
    fun compactLimitedNoticeShowsStaticText() {
        show {
            DirectMessageConversationScreen(
                accountId = owner.id,
                state = DirectMessageUiState(
                    selectedConversationId = conversation.id,
                    selectedConversation = conversation,
                    recipient = recipient,
                    thread = listOf(conversation.lastPost),
                    threadLimitations = listOf(ThreadLimitation.UncertainServerTruncation),
                ),
                compactLayout = true,
            )
        }

        compose.onNodeWithText("Some messages could not be loaded").assertIsDisplayed()
        compose.onNodeWithTag("direct_message_thread_continue").assertDoesNotExist()
        compose.onNodeWithTag("direct_message_thread_retry").assertDoesNotExist()
    }

    @Test
    fun finishedThreadShowsNoExtraControl() {
        show {
            DirectMessageConversationScreen(
                accountId = owner.id,
                state = DirectMessageUiState(
                    selectedConversationId = conversation.id,
                    selectedConversation = conversation,
                    recipient = recipient,
                    thread = listOf(conversation.lastPost),
                ),
                compactLayout = false,
            )
        }

        compose.onNodeWithTag("direct_message_thread_continue").assertDoesNotExist()
        compose.onNodeWithTag("direct_message_thread_retry").assertDoesNotExist()
        compose.onNodeWithText("Some messages could not be loaded").assertDoesNotExist()
    }

    @Test
    fun limitedThreadShowsStaticNotice() {
        show {
            DirectMessageConversationScreen(
                accountId = owner.id,
                state = DirectMessageUiState(
                    selectedConversationId = conversation.id,
                    selectedConversation = conversation,
                    recipient = recipient,
                    thread = listOf(conversation.lastPost),
                    threadLimitations = listOf(ThreadLimitation.UncertainServerTruncation),
                ),
                compactLayout = false,
            )
        }

        compose.onNodeWithText("Some messages could not be loaded").assertIsDisplayed()
        compose.onNodeWithTag("direct_message_thread_continue").assertDoesNotExist()
        compose.onNodeWithTag("direct_message_thread_retry").assertDoesNotExist()
    }

    private fun show(content: @Composable () -> Unit) {
        compose.activity.runOnUiThread {
            compose.activity.setContent { PalustrisTheme { content() } }
        }
        compose.waitForIdle()
    }

    private fun accountFixture(localId: String, displayName: String) = Account(
        id = AccountId(connection, localId),
        displayName = displayName,
        handle = "@$localId@example.org",
    )

    private fun post(id: String, author: Account, text: String) = Post(
        id = EntityId(connection.origin, id),
        author = author,
        text = text,
        publishedAtEpochMillis = 0L,
        audience = Audience.Direct,
    )
}
