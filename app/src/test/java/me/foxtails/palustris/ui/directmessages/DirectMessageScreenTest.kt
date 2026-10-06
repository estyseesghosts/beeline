package me.foxtails.palustris.ui.directmessages

import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import me.foxtails.palustris.ui.directmessages.DirectMessageRecipientFinder
import me.foxtails.palustris.ui.directmessages.DirectMessageRecipientFinderState
import me.foxtails.palustris.ui.directmessages.DirectMessageUiState
import me.foxtails.palustris.ui.profile.ProfileScreen
import me.foxtails.palustris.ui.shell.AppNotificationsDestinationContent
import me.foxtails.palustris.ui.shell.DirectMessagesContract
import me.foxtails.palustris.ui.shell.NotificationsContract
import me.foxtails.palustris.ui.shell.NotificationsPanel
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
    @Config(qualifiers = "w800dp-h1000dp-420dpi")
    fun wideInboxKeepsViewportAndRowSurfaceFullWidthWhileClearingContent() {
        val conversations = (0..12).map(::conversationFixture)
        val rightClearance = 72.dp
        val bottomClearance = 96.dp
        val density = compose.activity.resources.displayMetrics.density

        listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { direction ->
            var opened: DirectConversation? = null
            show {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    DirectMessageInboxScreen(
                        accountId = owner.id,
                        state = DirectMessageUiState(conversations = conversations),
                        compactLayout = false,
                        rightObstructionClearance = rightClearance,
                        bottomObstructionClearance = bottomClearance,
                        onOpenConversation = { opened = it },
                    )
                }
            }

            compose.onNodeWithTag("direct_message_conversation_list")
                .performScrollToNode(hasText("Private message 0"))
            val inbox = compose.onNodeWithTag("direct_message_inbox", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val list = compose.onNodeWithTag("direct_message_conversation_list", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val surface = compose.onNodeWithTag(
                "direct_message_conversation_surface_conversation-0",
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot
            val rowAction = compose.onNodeWithTag(
                "direct_message_conversation_action_conversation-0",
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot
            val safeRight = list.right - rightClearance.value * density

            assertEquals("the inbox keeps its full-width viewport", inbox.left, list.left, 0.5f)
            assertEquals("the inbox keeps its full-width viewport", inbox.right, list.right, 0.5f)
            assertEquals("the inbox keeps its full-width viewport", inbox.width, list.width, 0.5f)
            assertEquals("row backgrounds keep full width", list.left, surface.left, 0.5f)
            assertEquals("the inbox list keeps its full-width viewport", list.right, surface.right, 0.5f)
            assertEquals("row backgrounds keep full width", list.width, surface.width, 0.5f)
            assertTrue("row background continues under future floating chrome", surface.right > safeRight)
            assertTrue(
                "row click target clears physical-right obstruction in $direction",
                rowAction.right <= safeRight + 1f,
            )

            val refresh = compose.onNodeWithText("Refresh").fetchSemanticsNode().boundsInRoot
            assertTrue("refresh clears physical-right obstruction in $direction", refresh.right <= safeRight + 1f)
            compose.onNodeWithTag(
                "direct_message_conversation_action_conversation-0",
                useUnmergedTree = true,
            ).performClick()
            assertEquals(conversations.first().id, opened?.id)

            compose.onNodeWithTag("direct_message_conversation_list")
                .performScrollToNode(hasText("You're up to date"))
            val finalContent = compose.onNodeWithText("You're up to date").assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertTrue(
                "final inbox content clears bottom obstruction in $direction",
                finalContent.bottom <= list.bottom - bottomClearance.value * density + 1f,
            )
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h1000dp-420dpi")
    fun notificationsDestinationPassesClearanceToWideDirectMessageInbox() {
        val rightClearance = 64.dp
        val bottomClearance = 88.dp
        var loadRequests = 0
        show {
            AppNotificationsDestinationContent(
                panel = NotificationsPanel.DirectMessages,
                account = owner,
                compactLayout = false,
                compactNavigationVisible = false,
                rightObstructionClearance = rightClearance,
                bottomObstructionClearance = bottomClearance,
                notificationAccountIdentity = owner.id.localId,
                notifications = NotificationsContract.Empty,
                onOpenNotification = {},
                onOpenSettings = {},
                directMessages = DirectMessagesContract(
                    state = DirectMessageUiState(
                        conversations = listOf(conversation),
                        nextCursor = "next-page",
                    ),
                    actions = object : DirectMessagesContract.Actions by DirectMessagesContract.Empty.actions {
                        override fun loadMore() { loadRequests++ }
                    },
                ),
            )
        }

        val list = compose.onNodeWithTag("direct_message_conversation_list", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val rowAction = compose.onNodeWithTag(
            "direct_message_conversation_action_conversation",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        val safeRight = list.right - rightClearance.value * density
        assertTrue("the shell destination forwards right clearance", rowAction.right <= safeRight + 1f)

        compose.onNodeWithTag("direct_message_conversation_list")
            .performScrollToNode(hasText("Load older messages"))
        val finalContent = compose.onNodeWithText("Load older messages").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "the shell destination forwards bottom clearance",
            finalContent.bottom <= list.bottom - bottomClearance.value * density + 1f,
        )
        assertTrue("the load-more action clears right obstruction", finalContent.right <= safeRight + 1f)
        compose.onNodeWithText("Load older messages").performClick()
        assertEquals(1, loadRequests)
    }

    @Test
    fun compactInboxIgnoresWideObstructionClearanceInputs() {
        val conversations = listOf(conversationFixture(0), conversationFixture(1))
        fun finalContentBottom(bottomClearance: Dp): Float {
            show {
                DirectMessageInboxScreen(
                    accountId = owner.id,
                    state = DirectMessageUiState(conversations = conversations),
                    compactLayout = true,
                    compactNavigationVisible = false,
                    rightObstructionClearance = 72.dp,
                    bottomObstructionClearance = bottomClearance,
                )
            }
            val list = compose.onNodeWithTag("direct_message_conversation_list", useUnmergedTree = true)
            val listBounds = list.fetchSemanticsNode().boundsInRoot
            val rowAction = compose.onNodeWithTag(
                "direct_message_conversation_action_conversation-0",
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot
            assertEquals("compact row hit bounds remain full width", listBounds.right, rowAction.right, 0.5f)
            list.performScrollToNode(hasText("You're up to date"))
            return compose.onNodeWithText("You're up to date").fetchSemanticsNode().boundsInRoot.bottom
        }

        val withoutWideClearance = finalContentBottom(0.dp)
        val withWideClearance = finalContentBottom(96.dp)
        assertEquals(
            "compact list clearance does not use wide obstruction values",
            withoutWideClearance,
            withWideClearance,
            1f,
        )
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
    @Config(qualifiers = "w800dp-h1000dp-420dpi")
    fun wideInboxAndConversationClearPhysicalLeftInBothDirections() {
        val density = compose.activity.resources.displayMetrics.density
        for (direction in LayoutDirection.entries) {
            show {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    DirectMessageInboxScreen(accountId = owner.id,
                        state = DirectMessageUiState(conversations = listOf(conversation)),
                        compactLayout = false, leftObstructionClearance = 72.dp)
                }
            }
            val list = bounds("direct_message_conversation_list")
            val surface = bounds("direct_message_conversation_surface_conversation")
            val action = bounds("direct_message_conversation_action_conversation")
            assertEquals(list.left, surface.left, 1f)
            assertTrue(action.left >= list.left + 72f * density - 1f)
            show {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    DirectMessageConversationScreen(accountId = owner.id,
                        state = DirectMessageUiState(selectedConversation = conversation,
                            selectedConversationId = conversation.id, thread = listOf(conversation.lastPost),
                            editorText = "Retained draft"),
                        compactLayout = false, leftObstructionClearance = 72.dp)
                }
            }
            val viewport = bounds("direct_message_conversation")
            assertEquals(viewport.left, bounds("direct_message_thread").left, 1f)
            val safeLeft = viewport.left + 72f * density
            listOf("direct_message_title", "direct_message_input", "direct_message_send",
                "direct_message_bubble_last").forEach { tag ->
                assertTrue("$tag clears physical left", bounds(tag).left >= safeLeft - 1f)
            }
            compose.onNodeWithTag("direct_message_input").assertTextContains("Retained draft")
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h1000dp-420dpi")
    fun wideConversationClearsPhysicalRightAndKeepsFullViewportAndTranscriptReach() {
        val rightClearance = 72.dp
        val bottomClearance = 96.dp
        val density = compose.activity.resources.displayMetrics.density
        val posts = (0..24).map { post("thread-$it", if (it % 2 == 0) owner else recipient, "Message $it") }
        listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { direction ->
            var continued = 0
            show {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    DirectMessageConversationScreen(
                        accountId = owner.id,
                        state = DirectMessageUiState(
                            selectedConversationId = conversation.id,
                            selectedConversation = conversation,
                            thread = posts,
                            threadCursor = "next",
                            error = "Send failed",
                            editorText = "Retained draft",
                        ),
                        compactLayout = false,
                        rightObstructionClearance = rightClearance,
                        bottomObstructionClearance = bottomClearance,
                        onContinueThread = { continued++ },
                    )
                }
            }
            val viewport = bounds("direct_message_conversation")
            val thread = bounds("direct_message_thread")
            assertEquals(800 * density, viewport.width, 1f)
            assertEquals(1000 * density, viewport.height, 1f)
            assertEquals(viewport.left, thread.left, 0.5f)
            assertEquals(viewport.right, thread.right, 0.5f)
            val safeRight = viewport.right - rightClearance.value * density
            compose.onNodeWithTag("direct_message_thread").performScrollToNode(hasText("Message 0"))
            listOf("direct_message_title", "direct_message_notice", "direct_message_error",
                "direct_message_input", "direct_message_send", "direct_message_bubble_thread-0",
                "direct_message_bubble_thread-1").forEach { tag ->
                assertTrue("$tag clears physical right in $direction", bounds(tag).right <= safeRight + 1f)
            }
            val back = compose.onNodeWithContentDescription("Back to messages").fetchSemanticsNode().boundsInRoot
            assertTrue("Back clears physical right in $direction", back.right <= safeRight + 1f)
            scrollThreadToEnd()
            val lastBubble = bounds("direct_message_bubble_thread-24")
            val footer = bounds("direct_message_thread_continue")
            assertTrue(lastBubble.right <= safeRight + 1f)
            assertTrue(footer.right <= safeRight + 1f)
            assertTrue("final transcript clears bottom in $direction",
                footer.bottom <= thread.bottom - bottomClearance.value * density + 1f)
            compose.onNodeWithTag("direct_message_thread_continue").assertIsDisplayed().performClick()
            assertEquals(1, continued)
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h1000dp-420dpi")
    fun wideConversationRetryAndThreadErrorClearPhysicalRight() {
        val density = compose.activity.resources.displayMetrics.density
        listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { direction ->
            var retried = 0
            show {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    DirectMessageConversationScreen(
                        accountId = owner.id,
                        state = DirectMessageUiState(recipient = recipient, threadError = "Thread failed"),
                        compactLayout = false,
                        rightObstructionClearance = 72.dp,
                        bottomObstructionClearance = 96.dp,
                        onRetryThread = { retried++ },
                    )
                }
            }
            val safeRight = bounds("direct_message_conversation").right - 72 * density
            assertTrue(bounds("direct_message_thread_retry").right <= safeRight + 1f)
            assertTrue(compose.onNodeWithText("Thread failed").fetchSemanticsNode().boundsInRoot.right <= safeRight + 1f)
            compose.onNodeWithTag("direct_message_thread_retry").assertIsDisplayed().performClick()
            assertEquals(1, retried)
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h1000dp-420dpi")
    fun conversationDestinationRetainsEditorThroughImeAndBottomClearanceChanges() {
        val density = compose.activity.resources.displayMetrics.density
        listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { direction ->
            val bottomClearance = mutableStateOf(0.dp)
            val text = mutableStateOf("")
            show {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    conversationDestination(
                        state = DirectMessageUiState(
                            recipient = recipient,
                            thread = (0..24).map { post("ime-$it", recipient, "Message $it") },
                            threadCursor = "next",
                            editorText = text.value,
                        ),
                        bottomClearance = bottomClearance.value,
                        onEditorTextChange = { text.value = it },
                    )
                }
            }
            compose.onNodeWithTag("direct_message_input").performTextInput("Owned draft")
            val editorBottoms = mutableListOf<Float>()
            for (keyboardDp in listOf(0, 360, 0)) {
                applyKeyboardInsets(keyboardDp)
                compose.runOnIdle { bottomClearance.value = 0.dp }
                val before = bounds("direct_message_input")
                val sendBefore = bounds("direct_message_send")
                compose.runOnIdle { bottomClearance.value = 96.dp }
                compose.waitForIdle()
                val after = bounds("direct_message_input")
                assertEquals("bottom clearance must not move editor", before, after)
                assertEquals("bottom clearance must not move Send", sendBefore, bounds("direct_message_send"))
                compose.onNodeWithTag("direct_message_input").assertIsDisplayed().assertTextContains("Owned draft")
                compose.onNodeWithTag("direct_message_send").assertIsDisplayed()
                val viewport = bounds("direct_message_conversation")
                assertTrue("editor clears physical right with IME $keyboardDp in $direction",
                    after.right <= viewport.right - 72 * density + 1f)
                assertTrue(bounds("direct_message_send").right <= viewport.right - 72 * density + 1f)
                assertTrue("editor clears IME or navigation bar",
                    after.bottom <= viewport.bottom - maxOf(keyboardDp, 24) * density + 1f)
                editorBottoms += after.bottom
                scrollThreadToEnd()
                assertTrue("branch forwards transcript bottom clearance",
                    bounds("direct_message_thread_continue").bottom <= bounds("direct_message_thread").bottom - 96 * density + 1f)
            }
            assertEquals("wide IME moves editor by inset difference", 336 * density,
                editorBottoms[0] - editorBottoms[1], 1f)
            assertEquals("IME close restores editor position", editorBottoms[0], editorBottoms[2], 1f)
            assertEquals("Owned draft", text.value)
        }
    }

    @Test
    fun compactConversationIgnoresWideClearanceThroughImeChanges() {
        listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { direction ->
            listOf(true, false).forEach { navigationVisible ->
                val clearance = mutableStateOf(0.dp)
                show {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        DirectMessageConversationScreen(
                            accountId = owner.id,
                            state = DirectMessageUiState(recipient = recipient, editorText = "Compact draft",
                                thread = listOf(conversation.lastPost), threadCursor = "next"),
                            compactLayout = true,
                            compactNavigationVisible = navigationVisible,
                            rightObstructionClearance = clearance.value,
                            bottomObstructionClearance = clearance.value,
                        )
                    }
                }
                for (keyboardDp in listOf(0, 360, 0)) {
                    applyKeyboardInsets(keyboardDp)
                    compose.runOnIdle { clearance.value = 0.dp }
                    compose.waitForIdle()
                    val tags = listOf("direct_message_conversation", "direct_message_thread", "direct_message_title",
                        "direct_message_input", "direct_message_send", "direct_message_thread_continue")
                    val before = tags.associateWith(::bounds)
                    compose.runOnIdle { clearance.value = 96.dp }
                    compose.waitForIdle()
                    tags.forEach { assertEquals("compact $it ignores wide inputs", before[it], bounds(it)) }
                    compose.onNodeWithTag("direct_message_input").assertIsDisplayed().assertTextContains("Compact draft")
                }
            }
        }
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

    private fun scrollThreadToEnd() {
        compose.onNodeWithTag("direct_message_thread").performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, 100000f)
        }
        compose.waitForIdle()
    }

    private fun applyKeyboardInsets(keyboardDp: Int) {
        val density = compose.activity.resources.displayMetrics.density
        val systemBottom = (24 * density).toInt()
        compose.runOnIdle {
            val content = compose.activity.findViewById<ViewGroup>(android.R.id.content)
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, systemBottom))
                .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, systemBottom))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, (keyboardDp * density).toInt()))
                .setVisible(WindowInsetsCompat.Type.ime(), keyboardDp > 0)
                .build()
            ViewCompat.dispatchApplyWindowInsets(content.getChildAt(0), insets)
        }
        compose.waitForIdle()
    }

    @Composable
    private fun conversationDestination(state: DirectMessageUiState, bottomClearance: Dp, onEditorTextChange: (String) -> Unit) {
        AppNotificationsDestinationContent(
            panel = NotificationsPanel.DirectMessages, account = owner,
            compactLayout = false, compactNavigationVisible = false,
            rightObstructionClearance = 72.dp, bottomObstructionClearance = bottomClearance,
            notificationAccountIdentity = owner.id.localId, notifications = NotificationsContract.Empty,
            onOpenNotification = {}, onOpenSettings = {},
            directMessages = DirectMessagesContract(
                state = state,
                actions = object : DirectMessagesContract.Actions by DirectMessagesContract.Empty.actions {
                    override fun updateEditor(text: String) = onEditorTextChange(text)
                },
            ),
        )
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
    fun recipientFinderSearchesAndSelectsAnAccount() {
        var selected: Account? = null
        var searchRequests = 0
        show {
            var finder by remember {
                mutableStateOf(DirectMessageRecipientFinderState(isOpen = true))
            }
            DirectMessageRecipientFinder(
                state = finder,
                onQueryChange = { query -> finder = finder.copy(query = query, searched = false) },
                onSearch = {
                    searchRequests += 1
                    finder = finder.copy(results = listOf(recipient), searched = true)
                },
                onAccountSelected = { selected = it },
                onDismiss = { finder = finder.copy(isOpen = false) },
            )
        }

        compose.onNodeWithTag("dm_recipient_query").performTextInput("@recipient@example.org")
        compose.onNodeWithTag("dm_recipient_search").performClick()
        compose.onNodeWithTag("dm_recipient_result").assertIsDisplayed().performClick()

        assertEquals(1, searchRequests)
        assertEquals(recipient.id, selected?.id)
    }

    @Test
    fun recipientFinderCanBeCancelled() {
        var dismissed = false
        show {
            DirectMessageRecipientFinder(
                state = DirectMessageRecipientFinderState(isOpen = true),
                onQueryChange = {},
                onSearch = {},
                onAccountSelected = {},
                onDismiss = { dismissed = true },
            )
        }

        compose.onNodeWithTag("dm_recipient_cancel").performClick()

        assertEquals(true, dismissed)
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

    private fun conversationFixture(index: Int) = conversation.copy(
        id = ConversationId(connection.origin, "conversation-$index"),
        lastPost = post("last-$index", recipient, "Private message $index"),
    )
}
