package me.foxtails.palustris.ui.session

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.foxtails.palustris.data.auth.AccountIndex
import me.foxtails.palustris.data.auth.AuthCallback
import me.foxtails.palustris.data.auth.AuthGateway
import me.foxtails.palustris.data.auth.PendingLogin
import me.foxtails.palustris.data.auth.SessionLifecycle
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Session
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.ui.UiStrings

data class SessionUi(
    val starting: Boolean = true,
    val busy: Boolean = false,
    val account: Account? = null,
    val addingAccount: Boolean = false,
    val origin: String? = null,
    val pending: Boolean = false,
    val browserUrl: String? = null,
    val error: String? = null,
    val sessionGeneration: Long = 0,
)

@HiltViewModel
class AccountManager @Inject constructor(
    private val auth: AuthGateway,
    private val lifecycle: SessionLifecycle,
    private val uiStrings: UiStrings,
) : ViewModel() {
    private val _session = MutableStateFlow(SessionUi())
    val session = _session.asStateFlow()
    private val _accountIndex = MutableStateFlow(AccountIndex())
    val accountIndex = _accountIndex.asStateFlow()
    private val _connectedContext = MutableStateFlow<ConnectedSessionContext?>(null)
    val connectedContext = _connectedContext.asStateFlow()
    private var activeSessionValue: Session? = null
    private var pending: PendingLogin? = null
    private var authJob: Job? = null
    private var deferredCallback: String? = null
    private var sessionGeneration = 0L

    init {
        viewModelScope.launch {
            try {
                val restored = lifecycle.restore()
                pending = restored.pending
                _accountIndex.value = restored.index
                if (restored.active != null) {
                    connect(restored.active)
                    if (pending != null) {
                        _session.value = _session.value.copy(
                            addingAccount = true,
                            pending = true,
                            origin = pending?.origin,
                        )
                    }
                    deferredCallback?.let(::callback)
                } else {
                    _session.value = SessionUi(starting = false, pending = pending != null, origin = pending?.origin)
                    deferredCallback?.let(::callback)
                }
                Log.i(TAG, "oauth_callback starting-complete")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _session.value = SessionUi(starting = false, error = uiStrings.sessionRestoreFailed())
            }
        }
    }

    fun signIn(input: String, replacingAccountId: AccountId? = null) {
        if (_session.value.busy) return
        authJob = viewModelScope.launch {
            _session.value = _session.value.copy(busy = true, error = null, addingAccount = activeSessionValue != null)
            try {
                val next = auth.prepare(input).copy(replacingAccountId = replacingAccountId)
                lifecycle.writePending(next)
                pending = next
                _session.value = SessionUi(
                    starting = false,
                    pending = true,
                    account = _connectedContext.value?.account,
                    addingAccount = activeSessionValue != null,
                    origin = next.origin,
                    browserUrl = auth.browserUrl(next),
                )
            } catch (e: Exception) {
                failAuth(e)
            }
        }
    }

    /** Starts a permission upgrade without allowing a different account to replace the target. */
    fun upgradePermissions(accountId: AccountId) {
        if (_accountIndex.value.accounts.none { it.accountId == accountId }) {
            _session.value = _session.value.copy(error = uiStrings.sessionAccountUnavailable())
            return
        }
        signIn(accountId.connection.origin, replacingAccountId = accountId)
    }

    fun browserOpened() { _session.value = _session.value.copy(browserUrl = null) }

    fun browserFailed() {
        _session.value = _session.value.copy(
            browserUrl = null,
            error = uiStrings.sessionNoBrowser(),
        )
    }

    fun reopenBrowser() {
        pending?.let { _session.value = _session.value.copy(browserUrl = auth.browserUrl(it), error = null) }
    }

    fun beginAddAccount() {
        if (_session.value.busy || activeSessionValue == null) return
        _session.value = _session.value.copy(addingAccount = true, pending = false, error = null)
    }

    fun cancelSignIn() {
        authJob?.cancel()
        pending = null
        deferredCallback = null
        viewModelScope.launch {
            lifecycle.clearPending()
            val active = _connectedContext.value
            if (active == null) {
                _session.value = SessionUi(starting = false)
            } else {
                _session.value = SessionUi(
                    starting = false,
                    account = active.account,
                    origin = active.accountId.connection.origin,
                )
            }
        }
    }

    fun callback(value: String) {
        if (_session.value.starting) {
            Log.i(TAG, "oauth_callback deferred-for-restore")
            deferredCallback = value
            return
        }
        val request = pending
        if (request == null) {
            Log.i(TAG, "oauth_callback no-pending-ignore")
            return
        }
        when (val result = AuthCallback.diagnose(value, request, System.currentTimeMillis())) {
            AuthCallback.Result.Accepted -> {
                if (request.authorizationCode != null || _session.value.busy || authJob?.isActive == true) {
                    Log.i(TAG, "oauth_callback duplicate-ignore")
                    return
                }
                pending = request.copy(authorizationCode = AuthCallback.authorizationCode(value))
                Log.i(TAG, "oauth_callback accepted-storing-code")
                finishSignIn()
            }
            is AuthCallback.Result.Invalid -> {
                Log.i(TAG, "oauth_callback invalid-callback reason=${result.reason.name.lowercase()}")
                _session.value = _session.value.copy(
                    error = if (result.reason == AuthCallback.InvalidReason.CODE_COUNT &&
                        request.protocol == me.foxtails.palustris.domain.Protocol.MASTODON
                    ) uiStrings.sessionCallbackMissing() else uiStrings.sessionCallbackInvalid(),
                )
            }
        }
    }

    fun finishSignIn() {
        val request = pending ?: return
        if (_session.value.busy) return
        if (request.protocol == me.foxtails.palustris.domain.Protocol.MASTODON && request.authorizationCode == null) {
            _session.value = _session.value.copy(error = uiStrings.sessionCallbackMissing())
            return
        }
        authJob = viewModelScope.launch {
            _session.value = _session.value.copy(busy = true, error = null, browserUrl = null)
            try {
                val result = auth.complete(request)
                val account = result.account
                request.replacingAccountId?.let { expected ->
                    if (account.id != expected) throw SourceError.AccountMismatch
                }
                val saved = lifecycle.login(result)
                _accountIndex.value = saved.index
                pending = null
                connect(saved.activation)
                Log.i(TAG, "oauth_callback exchange-complete")
            } catch (e: Exception) {
                failAuth(e)
            }
        }
    }

    fun switchAccount(accountId: AccountId) {
        if (_session.value.busy) return
        viewModelScope.launch {
            try {
                val switched = lifecycle.switch(accountId)
                if (switched == null) {
                    _session.value = _session.value.copy(error = uiStrings.sessionAccountUnavailable())
                } else {
                    _accountIndex.value = switched.index
                    connect(switched.activation)
                }
            } catch (e: Exception) {
                failAuth(e)
            }
        }
    }

    /**
     * Removes one account with every account-scoped row. Live delivery stops first.
     * Writers are revoked before their rows are deleted. Storage commits before the
     * in-memory index moves, so a late write cannot resurrect deleted data.
     */
    fun removeAccount(accountId: AccountId) {
        viewModelScope.launch {
            try {
                val replacement = lifecycle.remove(accountId)
                _accountIndex.value = replacement.index
                if (loginAccountId() == accountId) {
                    val nextSession = replacement.next
                    if (nextSession == null) {
                        activeSessionValue = null
                        _connectedContext.value = null
                        _session.value = SessionUi(starting = false)
                    } else {
                        connect(nextSession)
                    }
                }
            } catch (e: Exception) {
                failAuth(e)
            }
        }
    }

    fun signOut() {
        authJob?.cancel()
        val accountId = loginAccountId()
        if (accountId != null) removeAccount(accountId) else viewModelScope.launch {
            lifecycle.clearPending()
            pending = null
            _session.value = SessionUi(starting = false)
        }
    }

    fun updateAccount(account: Account) {
        if (_connectedContext.value?.accountId != account.id) return
        viewModelScope.launch {
            try {
                val result = lifecycle.updateProfile(account) ?: return@launch
                _accountIndex.value = result.index
                _session.value = _session.value.copy(account = account)
                _connectedContext.value = _connectedContext.value
                    ?.takeIf { it.accountId == account.id }
                    ?.let { current ->
                        ConnectedSessionContext(
                            account = account,
                            sessionRevision = current.sessionRevision,
                            presentationGeneration = current.presentationGeneration,
                            source = current.source,
                            registryToken = current.registryToken,
                            directMessageGeneration = current.directMessageGeneration,
                            draftGeneration = current.draftGeneration,
                        )
                    }
            } catch (e: Exception) {
                failAuth(e)
            }
        }
    }

    private fun connect(activation: SessionLifecycle.Activation) {
        val value = activation.session
        val account = activation.account
        sessionGeneration += 1L
        activeSessionValue = value
        // Publish one accepted context. The shell never joins a separate account emission
        // with a separate session emission.
        _connectedContext.value = ConnectedSessionContext(
            account = account,
            sessionRevision = value.sessionRevision,
            presentationGeneration = sessionGeneration,
            source = activation.source,
            registryToken = activation.registryToken,
            directMessageGeneration = activation.directMessageGeneration,
            draftGeneration = activation.draftGeneration,
        )
        _session.value = SessionUi(
            starting = false,
            account = account,
            origin = value.accountId.connection.origin,
            sessionGeneration = sessionGeneration,
        )
    }

    private fun loginAccountId(): AccountId? = activeSessionValue?.accountId

    private fun failAuth(e: Exception) {
        if (e is CancellationException) throw e
        _session.value = _session.value.copy(busy = false, error = message(e), browserUrl = null)
    }

    private fun message(e: Exception): String = uiStrings.sourceError(e)

    private companion object { const val TAG = "BeelineAuth" }
}
