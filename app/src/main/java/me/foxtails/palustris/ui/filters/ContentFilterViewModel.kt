package me.foxtails.palustris.ui.filters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.ContentFilter
import me.foxtails.palustris.domain.ContentFilterSet
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.ui.UiStrings

data class ContentFilterUiState(
    val set: ContentFilterSet? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val deleting: Set<String> = emptySet(),
    val error: String? = null,
    val unsupported: Boolean = false,
    val editor: ContentFilterEditor? = null,
    val editorError: FilterEditorError? = null,
)

/**
 * Lists and edits the filters of one account in Settings. The account's source is resolved through
 * [AccountSourceRegistry] for each call, so a replaced session never receives a stale write. Each saved
 * set reaches the screen in [ContentFilterUiState.set]; the screen hands it to the session owner.
 */
@HiltViewModel(assistedFactory = ContentFilterViewModel.Factory::class)
class ContentFilterViewModel @AssistedInject constructor(
    @Assisted private val accountId: AccountId,
    private val sourceRegistry: AccountSourceRegistry,
    private val uiStrings: UiStrings = UiStrings.Default,
) : ViewModel() {
    private val _state = MutableStateFlow(ContentFilterUiState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null

    /** Reads the filters again. The screen calls it each time the page opens. An open editor stays open. */
    fun load() {
        loadJob?.cancel()
        _state.value = _state.value.copy(loading = true, error = null, unsupported = false)
        loadJob = viewModelScope.launch {
            run({ it.contentFilters() }, onFailure = { _state.value = _state.value.copy(loading = false) }) { set ->
                _state.value = _state.value.copy(set = set, loading = false)
            }
        }
    }

    fun startNew() {
        val kind = _state.value.set?.kind ?: return
        _state.value = _state.value.copy(editor = ContentFilterEditor.new(kind), editorError = null, error = null)
    }

    fun startEdit(filter: ContentFilter) {
        val kind = _state.value.set?.kind ?: return
        _state.value = _state.value.copy(editor = ContentFilterEditor.edit(kind, filter, System.currentTimeMillis()), editorError = null, error = null)
    }

    fun updateEditor(transform: (ContentFilterEditor) -> ContentFilterEditor) {
        val editor = _state.value.editor ?: return
        if (_state.value.saving) return
        _state.value = _state.value.copy(editor = transform(editor), editorError = null)
    }

    fun dismissEditor() {
        if (_state.value.saving) return
        _state.value = _state.value.copy(editor = null, editorError = null, error = null)
    }

    fun save() {
        val editor = _state.value.editor ?: return
        if (_state.value.saving) return
        val draft = when (val result = editor.validate()) {
            is EditorResult.Invalid -> {
                _state.value = _state.value.copy(editorError = result.error)
                return
            }
            is EditorResult.Valid -> result.draft
        }
        _state.value = _state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            run({ it.saveContentFilter(editor.editingId, draft) }, onFailure = { _state.value = _state.value.copy(saving = false) }) { set ->
                _state.value = _state.value.copy(set = set, saving = false, editor = null)
            }
        }
    }

    fun delete(filter: ContentFilter) {
        if (filter.id in _state.value.deleting) return
        _state.value = _state.value.copy(deleting = _state.value.deleting + filter.id, error = null)
        viewModelScope.launch {
            run({ it.deleteContentFilter(filter.id) }, onFailure = { _state.value = _state.value.copy(deleting = _state.value.deleting - filter.id) }) { set ->
                _state.value = _state.value.copy(set = set, deleting = _state.value.deleting - filter.id)
            }
        }
    }

    /**
     * Runs [call] against the current source of the account. A result from a source that was replaced meanwhile
     * is dropped. A failure becomes text in [ContentFilterUiState.error].
     */
    private suspend fun <T> run(call: suspend (SocialSource) -> T, onFailure: () -> Unit, onSuccess: (T) -> Unit) {
        try {
            val source = sourceRegistry.sourceFor(accountId) ?: throw SessionUnavailable()
            val value = call(source)
            if (sourceRegistry.isCurrent(accountId, source)) onSuccess(value) else onFailure()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            onFailure()
            _state.value = _state.value.copy(
                error = if (error is SessionUnavailable) uiStrings.accountSessionUnavailable() else uiStrings.sourceError(error),
                unsupported = error is SourceError.Unsupported && _state.value.set == null,
            )
        }
    }

    private class SessionUnavailable : Exception()

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId): ContentFilterViewModel
    }
}
