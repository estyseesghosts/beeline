package me.foxtails.palustris.ui.directmessages

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.ConversationId
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ThreadLimitation

data class DirectMessageRecipientFinderState(
    val isOpen: Boolean = false,
    val query: String = "",
    val results: List<Account> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
)

data class DirectMessageUiState(
    val conversations: List<DirectConversation> = emptyList(),
    val selectedConversationId: ConversationId? = null,
    val selectedConversation: DirectConversation? = null,
    val recipient: Account? = null,
    val thread: List<Post> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = false,
    val loadingThread: Boolean = false,
    val loadingMore: Boolean = false,
    val sending: Boolean = false,
    val error: String? = null,
    /** Opaque adapter continuation for the open thread. Null means finished. */
    val threadCursor: String? = null,
    /** Accumulated adapter limitations for the open thread. */
    val threadLimitations: List<ThreadLimitation> = emptyList(),
    /** True while a continuation page is in flight. Separate from fresh load. */
    val threadContinuing: Boolean = false,
    /** Thread acquisition failure. Separate from inbox and send errors. */
    val threadError: String? = null,
    /** Composer text for the active editor target. The feature owner, not the screen, holds it. */
    val editorText: String = "",
    /** Binds accepted send completion to the text it submitted. A newer edit must survive. */
    val editorRevision: Long = 0L,
    /** Temporary recipient lookup state. Conversation and editor state remain separate. */
    val recipientFinder: DirectMessageRecipientFinderState = DirectMessageRecipientFinderState(),
)
