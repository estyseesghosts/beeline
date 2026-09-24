package me.foxtails.palustris.ui.directmessages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.foxtails.palustris.data.directmessages.DirectMessageRepository
import me.foxtails.palustris.data.directmessages.DirectMessageStore
import me.foxtails.palustris.data.directmessages.DirectMessageWriteAuthority
import me.foxtails.palustris.di.IoDispatcher
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.ConversationId
import me.foxtails.palustris.domain.ConversationIdentity
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.DirectMessageRequest
import me.foxtails.palustris.domain.DirectMessageSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.ui.UiStrings

@HiltViewModel(assistedFactory = DirectMessageViewModel.Factory::class)
class DirectMessageViewModel @AssistedInject constructor(
    @Assisted val accountId: AccountId,
    @Assisted source: SocialSource,
    @Assisted private val writeGeneration: Long,
    store: DirectMessageStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    // One writer authority per account lifecycle owner. The Hilt singleton that
    // AccountManager.removeAccount invalidates is required here and passed to the
    // repository, so a revoked writer cannot publish a conversation state change.
    private val writeAuthority: DirectMessageWriteAuthority,
    private val uiStrings: UiStrings = UiStrings.Default,
) : ViewModel() {
    private val directSource = source as? DirectMessageSource
    private val repository = directSource?.let {
        DirectMessageRepository(accountId, it, store, writeGeneration, ioDispatcher, writeAuthority)
    }
    private val _state = MutableStateFlow(DirectMessageUiState())
    val state = _state.asStateFlow()
    private var refreshJob: Job? = null
    private var threadJob: Job? = null
    // Read acknowledgement owns a separate lifetime. It starts only after the
    // first thread page publishes, so an active markRead never blocks a tap on
    // Continue behind the threadJob guard. Best effort only. Never writes UI.
    private var markReadJob: Job? = null
    private var sendJob: Job? = null
    private var stopped = false

    /** Binds inbox publication authority. Advances on refresh and stop. */
    private var inboxEpoch = 0L

    /** Binds selection publication authority. Advances on open, start, close, and stop. */
    private var selectionEpoch = 0L

    /** Distinguishes two new conversations that both have a null conversation ID. */
    private var composeGeneration = 0L
    private var composeTarget: Account? = null
    private val acceptedCursors = mutableSetOf<String>()

    init {
        // Read the cache off the main thread. Room blocks while it decodes rows.
        viewModelScope.launch {
            val cached = withContext(ioDispatcher) { repository?.cachedConversations().orEmpty() }
            if (stopped) return@launch
            _state.value = _state.value.copy(conversations = cached)
            refresh()
        }
    }

    fun refresh() {
        if (stopped) return
        val epoch = ++inboxEpoch
        acceptedCursors.clear()
        refreshJob?.cancel()
        val repo = repository
        if (repo == null) {
            _state.value = _state.value.copy(
                loading = false,
                error = uiStrings.directMessagesUnsupported(),
            )
            return
        }
        val previous = _state.value
        val cached = previous.conversations.isNotEmpty()
        // Reserve the inbox slot synchronously. A queued paging call must not start behind it.
        _state.value = previous.copy(loading = !cached, error = null)
        refreshJob = viewModelScope.launch {
            try {
                val page = repo.conversations()
                if (epoch != inboxEpoch || stopped) return@launch
                val current = _state.value
                page.nextCursor?.let(acceptedCursors::add)
                _state.value = current.copy(
                    conversations = page.items,
                    selectedConversation = preserveSelection(
                        current.selectedConversation,
                        page.items,
                        current.selectedConversationId,
                    ),
                    nextCursor = page.nextCursor,
                    loading = false,
                    error = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (epoch != inboxEpoch || stopped) return@launch
                _state.value = _state.value.copy(loading = false, error = uiStrings.sourceError(error))
            }
        }
    }

    fun loadMore() {
        if (stopped) return
        val repo = repository
        if (repo == null) {
            _state.value = _state.value.copy(error = uiStrings.directMessagesUnsupported())
            return
        }
        val current = _state.value
        val cursor = current.nextCursor ?: return
        // Paging cannot start during refresh. Reserve the page slot synchronously.
        if (current.loading || current.loadingMore || refreshJob?.isActive == true) return
        val epoch = inboxEpoch
        _state.value = current.copy(loadingMore = true, error = null)
        viewModelScope.launch {
            try {
                val page = repo.conversations(cursor)
                if (epoch != inboxEpoch || stopped) return@launch
                val latest = _state.value
                val combined = (latest.conversations + page.items).distinctBy { it.id }
                    .sortedByDescending { it.lastPost.publishedAtEpochMillis }
                val repeatedCursor = page.nextCursor != null &&
                    (page.nextCursor == cursor || !acceptedCursors.add(page.nextCursor))
                _state.value = latest.copy(
                    conversations = combined,
                    selectedConversation = preserveSelection(
                        latest.selectedConversation,
                        combined,
                        latest.selectedConversationId,
                    ),
                    nextCursor = page.nextCursor.takeUnless { repeatedCursor },
                    loadingMore = false,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (epoch != inboxEpoch || stopped) return@launch
                _state.value = _state.value.copy(loadingMore = false, error = uiStrings.sourceError(error))
            }
        }
    }

    fun openConversation(conversation: DirectConversation) {
        if (stopped) return
        val id = conversation.id
        val selection = ++selectionEpoch
        composeTarget = null
        threadJob?.cancel()
        markReadJob?.cancel()
        _state.value = _state.value.copy(
            selectedConversationId = id,
            selectedConversation = conversation.copy(unread = false),
            recipient = conversation.participants.firstOrNull { it.id != accountId },
            thread = emptyList(),
            threadCursor = null,
            threadLimitations = emptyList(),
            threadContinuing = false,
            threadError = null,
            loadingThread = true,
            sending = false,
            error = null,
            editorText = "",
            editorRevision = _state.value.editorRevision + 1,
            conversations = _state.value.conversations.map { item ->
                if (item.id == id) item.copy(unread = false) else item
            },
        )
        val repo = repository
        if (repo == null) {
            _state.value = _state.value.copy(
                loadingThread = false,
                threadError = uiStrings.directMessagesUnsupported(),
            )
            return
        }
        threadJob = viewModelScope.launch {
            try {
                val cached = withContext(ioDispatcher) { repo.cachedThread(id) }
                if (selection != selectionEpoch || stopped) return@launch
                if (_state.value.selectedConversationId != id) return@launch
                if (cached.isNotEmpty()) {
                    _state.value = _state.value.copy(thread = mergeThread(_state.value.thread, cached))
                }
                val result = repo.thread(id)
                if (selection != selectionEpoch || stopped) return@launch
                val current = _state.value
                if (current.selectedConversationId != id) return@launch
                _state.value = current.copy(
                    thread = mergeThread(current.thread, result.posts),
                    threadCursor = result.nextCursor,
                    threadLimitations = result.limitations,
                    loadingThread = false,
                    threadContinuing = false,
                    threadError = null,
                )
                // Hand read acknowledgement to its own job after publication.
                // This returns the thread slot, so Continue can start while the
                // acknowledgement runs. Best effort. Never touches thread state.
                val readSelection = selection
                markReadJob = viewModelScope.launch {
                    try {
                        if (readSelection != selectionEpoch || stopped) return@launch
                        if (_state.value.selectedConversationId != id) return@launch
                        repo.markRead(id)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // Keep the loaded thread. Read acknowledgement stays recoverable.
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (selection != selectionEpoch || stopped) return@launch
                if (_state.value.selectedConversationId != id) return@launch
                applyThreadFailure(error)
            }
        }
    }

    /** Loads the next thread page with the stored opaque cursor. */
    fun continueThread() {
        if (stopped) return
        val current = _state.value
        val id = current.selectedConversationId ?: return
        val cursor = current.threadCursor ?: return
        runThreadPage(id, cursor)
    }

    /**
     * Retries the thread with the stored cursor when present. Falls back to a
     * fresh load when no cursor exists.
     */
    fun retryThread() {
        if (stopped) return
        val current = _state.value
        val id = current.selectedConversationId ?: return
        runThreadPage(id, current.threadCursor)
    }

    /**
     * Starts one guarded continuation or fresh retry page. The cursor stays
     * opaque and travels only to the repository. A null cursor means a fresh
     * load. Read acknowledgement runs only for fresh open loads, never here.
     */
    private fun runThreadPage(id: ConversationId, cursor: String?) {
        if (stopped) return
        val selection = selectionEpoch
        val current = _state.value
        if (current.selectedConversationId != id) return
        if (current.loadingThread || current.threadContinuing) return
        if (threadJob?.isActive == true) return
        val repo = repository
        if (repo == null) {
            _state.value = current.copy(
                loadingThread = false,
                threadContinuing = false,
                threadError = uiStrings.directMessagesUnsupported(),
            )
            return
        }
        _state.value = current.copy(
            loadingThread = cursor == null,
            threadContinuing = cursor != null,
            threadError = null,
        )
        threadJob = viewModelScope.launch {
            try {
                val result = repo.thread(id, cursor)
                if (selection != selectionEpoch || stopped) return@launch
                if (_state.value.selectedConversationId != id) return@launch
                val latest = _state.value
                _state.value = latest.copy(
                    thread = mergeThread(latest.thread, result.posts),
                    threadCursor = result.nextCursor,
                    threadLimitations = result.limitations,
                    loadingThread = false,
                    threadContinuing = false,
                    threadError = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (selection != selectionEpoch || stopped) return@launch
                if (_state.value.selectedConversationId != id) return@launch
                applyThreadFailure(error)
            }
        }
    }

    /**
     * Records a thread acquisition failure. Prior posts and the stored cursor
     * survive, and the shared inbox and send error stays untouched. A rejected
     * continuation cursor is cleared so retry falls back to a fresh load.
     */
    private fun applyThreadFailure(error: Exception) {
        val unsupportedContinuation =
            (error as? SourceError.Unsupported)?.feature == "direct.thread.continuation"
        val latest = _state.value
        _state.value = latest.copy(
            loadingThread = false,
            threadContinuing = false,
            threadError = uiStrings.sourceError(error),
            threadCursor = if (unsupportedContinuation) null else latest.threadCursor,
        )
    }

    fun startConversation(account: Account) {
        if (stopped) return
        selectionEpoch += 1
        composeGeneration += 1
        composeTarget = account
        threadJob?.cancel()
        markReadJob?.cancel()
        _state.value = _state.value.copy(
            selectedConversationId = null,
            selectedConversation = null,
            recipient = account,
            thread = emptyList(),
            threadCursor = null,
            threadLimitations = emptyList(),
            threadContinuing = false,
            threadError = null,
            loadingThread = false,
            sending = false,
            error = null,
            editorText = "",
            editorRevision = _state.value.editorRevision + 1,
        )
    }

    fun closeConversation() {
        if (stopped) return
        selectionEpoch += 1
        composeTarget = null
        threadJob?.cancel()
        markReadJob?.cancel()
        _state.value = _state.value.copy(
            selectedConversationId = null,
            selectedConversation = null,
            recipient = null,
            thread = emptyList(),
            threadCursor = null,
            threadLimitations = emptyList(),
            threadContinuing = false,
            threadError = null,
            loadingThread = false,
            sending = false,
            error = null,
            editorText = "",
            editorRevision = _state.value.editorRevision + 1,
        )
    }

    /**
     * Records the composer text for the active editor target. Each accepted change advances the
     * editor revision, so an in-flight send cannot clear newer text.
     */
    fun updateEditor(text: String) {
        if (stopped) return
        val current = _state.value
        if (current.editorText == text) return
        _state.value = current.copy(editorText = text, editorRevision = current.editorRevision + 1)
    }

    fun send() {
        if (stopped) return
        val repo = repository
        if (repo == null) {
            _state.value = _state.value.copy(
                sending = false,
                error = uiStrings.directMessagesUnsupported(),
            )
            return
        }
        val current = _state.value
        val text = current.editorText
        val editorAtSend = current.editorRevision
        val recipients = current.selectedConversation?.participants
            ?.filterNot { it.id == accountId }
            .orEmpty()
            .ifEmpty { listOfNotNull(current.recipient) }
        if (recipients.isEmpty() || text.isBlank() || current.sending) return
        if (sendJob?.isActive == true) return
        val selection = selectionEpoch
        val compose = composeGeneration
        val target = composeTarget
        val selectedId = current.selectedConversationId
        val request = DirectMessageRequest(recipients.map(Account::id), text.trim(), current.thread.lastOrNull()?.id ?: current.selectedConversation?.lastPost?.id)
        // Reserve the send slot synchronously. A selection change clears visible sending state.
        _state.value = current.copy(sending = true, error = null)
        sendJob = viewModelScope.launch {
            try {
                val post = repo.send(
                    request,
                    conversationId = selectedId,
                    recipientAccounts = recipients,
                )
                if (selection != selectionEpoch || stopped) return@launch
                val latest = _state.value
                if (latest.selectedConversationId != selectedId) return@launch
                if (selectedId == null && (latest.recipient?.id != target?.id || compose != composeGeneration)) return@launch
                val id = selectedId ?: ConversationId(accountId.connection.origin, post.id.value)
                val existing = latest.selectedConversation
                // A new compose has no server identity yet. Its identifier is the
                // sent post value, so it stays provisional in local state too.
                val identity = existing?.identity
                    ?: if (selectedId == null) ConversationIdentity.Provisional else ConversationIdentity.Verified
                val conversation = DirectConversation(
                    id = id,
                    participants = (recipients + post.author).distinctBy { it.id },
                    lastPost = post,
                    unread = false,
                    rootPostId = existing?.rootPostId ?: request.replyTo ?: post.id,
                    identity = identity,
                )
                val conversations = (latest.conversations.filterNot { it.id == id } + conversation)
                    .sortedByDescending { it.lastPost.publishedAtEpochMillis }
                // Clear only the text this send submitted. Text entered during the send stays.
                val clearEditor = latest.editorRevision == editorAtSend
                _state.value = latest.copy(
                    conversations = conversations,
                    selectedConversationId = id,
                    selectedConversation = conversation,
                    recipient = recipients.firstOrNull(),
                    thread = mergeThread(latest.thread, listOf(post)),
                    sending = false,
                    editorText = if (clearEditor) "" else latest.editorText,
                    editorRevision = if (clearEditor) latest.editorRevision + 1 else latest.editorRevision,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (selection != selectionEpoch || stopped) return@launch
                if (_state.value.selectedConversationId != selectedId) return@launch
                if (selectedId == null && compose != composeGeneration) return@launch
                _state.value = _state.value.copy(sending = false, error = uiStrings.sourceError(error))
            }
        }
    }

    fun stop() {
        if (stopped) return
        stopped = true
        inboxEpoch += 1
        selectionEpoch += 1
        refreshJob?.cancel()
        threadJob?.cancel()
        markReadJob?.cancel()
        sendJob?.cancel()
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private fun preserveSelection(
        previous: DirectConversation?,
        conversations: List<DirectConversation>,
        id: ConversationId?,
    ): DirectConversation? {
        if (id == null) return previous
        return conversations.firstOrNull { it.id == id } ?: previous
    }

    private fun mergeThread(
        existing: List<me.foxtails.palustris.domain.Post>,
        incoming: List<me.foxtails.palustris.domain.Post>,
    ): List<me.foxtails.palustris.domain.Post> {
        if (incoming.isEmpty()) return existing
        return (existing + incoming).distinctBy { it.id }
    }

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId, source: SocialSource, writeGeneration: Long): DirectMessageViewModel
    }
}
