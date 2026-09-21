package me.foxtails.palustris.data.notifications

import androidx.annotation.VisibleForTesting
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.data.AppMessages
import me.foxtails.palustris.data.notifications.work.NoOpNotificationDeliveryScheduler
import me.foxtails.palustris.data.notifications.work.NotificationDeliveryScheduler
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Event
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.NotificationAcknowledgement
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.SocialSource

enum class NotificationStreamStatus {
    Stopped,
    Connecting,
    Ready,
    Backoff,
    Unsupported,
}

data class NotificationSyncState(
    val unreadState: NotificationUnreadState = NotificationUnreadState.Unknown,
    val lastUpdated: Long = 0,
    val isActive: Boolean = false,
    val delayed: Boolean = false,
    val error: String? = null,
    val streamConnected: Boolean = false,
    val streamError: String? = null,
    val streamStatus: NotificationStreamStatus = NotificationStreamStatus.Stopped,
)

interface NotificationSyncController {
    fun observeAccount(accountId: AccountId): StateFlow<NotificationSyncState>

    /** Registers [source] and returns the sync token that owns it. */
    fun register(accountId: AccountId, source: SocialSource): NotificationSyncToken
    fun unregister(accountId: AccountId)
    suspend fun removeAccount(accountId: AccountId)
}

class NoOpNotificationSyncController : NotificationSyncController {
    private val states = mutableMapOf<AccountId, MutableStateFlow<NotificationSyncState>>()
    private val generations = mutableMapOf<AccountId, Long>()

    /** Allocates non-persisted generations for this controller lifetime; active entries are not history. */
    private val generationAllocator = AtomicLong()

    @Synchronized
    override fun observeAccount(accountId: AccountId): StateFlow<NotificationSyncState> = states.getOrPut(accountId) {
        MutableStateFlow(NotificationSyncState())
    }.asStateFlow()

    @Synchronized
    override fun register(accountId: AccountId, source: SocialSource): NotificationSyncToken {
        val generation = generationAllocator.incrementAndGet()
        generations[accountId] = generation
        states.getOrPut(accountId) { MutableStateFlow(NotificationSyncState()) }.also {
            it.value = it.value.copy(isActive = true, error = null)
        }
        return NotificationSyncToken(accountId, generation)
    }

    @Synchronized
    override fun unregister(accountId: AccountId) {
        generations.remove(accountId)
        states[accountId]?.value = states[accountId]?.value?.copy(isActive = false) ?: NotificationSyncState()
    }

    override suspend fun removeAccount(accountId: AccountId) {
        synchronized(this) {
            unregister(accountId)
            states.remove(accountId)
        }
    }
}

/** Application/account lifetime owner for REST reconciliation and future push/stream hints. */
@Singleton
class NotificationSyncOrchestrator @Inject constructor(
    private val repository: NotificationRepository,
    private val synchronizer: NotificationSynchronizer,
    private val sourceRegistry: AccountSourceRegistry,
    private val deliveryScheduler: NotificationDeliveryScheduler,
    private val appMessages: AppMessages = AppMessages.Default,
) : NotificationSyncController, NotificationSyncIntents, AutoCloseable {
    private constructor(dependencies: Dependencies) : this(
        dependencies.repository,
        dependencies.synchronizer,
        dependencies.sourceRegistry,
        dependencies.deliveryScheduler,
    )

    constructor() : this(Dependencies())

    private class Dependencies {
        val repository = NotificationRepository()
        val synchronizer = NotificationSynchronizer(repository)
        val sourceRegistry = AccountSourceRegistry()
        val deliveryScheduler = NoOpNotificationDeliveryScheduler()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val states = mutableMapOf<AccountId, MutableStateFlow<NotificationSyncState>>()
    private val jobs = mutableMapOf<AccountId, Job>()
    // Account locks live for the controller lifetime. Removal never detaches them,
    // so waiters keep one order per account.
    private val accountLocks = mutableMapOf<AccountId, Mutex>()
    private val generations = mutableMapOf<AccountId, Long>()

    /**
     * Tracks removal per account. The map bumps on unregister, even with no active
     * entry. A register snapshots the value before activation and rejects publish
     * when it changes. The null baseline alone cannot show removal, so the epoch
     * carries that signal. Successful publish never bumps it. Entries stay after
     * removal as a stale signal, like the repository tombstone. The key set stays
     * bounded by distinct accounts for this controller lifetime.
     */
    private val removalEpochs = mutableMapOf<AccountId, Long>()

    /** Allocates non-persisted generations for this controller lifetime; active entries are not history. */
    private val generationAllocator = AtomicLong()

    /** Allocates removal epochs for this controller lifetime. Values only move forward. */
    private val removalEpochAllocator = AtomicLong()

    /**
     * Test-only gate. It runs outside locks before each activation. Production
     * keeps it null. Tests use it to force removal to finish before activation.
     */
    @VisibleForTesting
    @Volatile
    var preActivateGate: (() -> Unit)? = null

    /**
     * Test-only gate. It runs outside locks between publication and poll start.
     * Production keeps it null. Tests use it to force removal into that window.
     */
    @VisibleForTesting
    @Volatile
    var prePollGate: (() -> Unit)? = null

    /**
     * Test-only gate. It runs outside locks after the final ownership check
     * and before delivery enqueue. Production keeps it null. Tests use it to
     * force removal into that window.
     */
    @VisibleForTesting
    @Volatile
    var preEnqueueGate: (() -> Unit)? = null

    @Synchronized
    override fun observeAccount(accountId: AccountId): StateFlow<NotificationSyncState> {
        // A removed account never materializes display or repository state. Check the
        // active map and the removal tombstone before any store read or map insert.
        if (accountId !in generations && repository.isRetired(accountId)) {
            return states[accountId]?.asStateFlow()
                ?: MutableStateFlow(NotificationSyncState()).asStateFlow()
        }
        return states.getOrPut(accountId) {
            MutableStateFlow(NotificationSyncState(
                unreadState = repository.observe(accountId).value.unreadState,
            ))
        }.asStateFlow()
    }

    @Synchronized
    override fun unregister(accountId: AccountId) {
        val generation = generations.remove(accountId)
        jobs.remove(accountId)?.cancel()
        // The per-account lock stays. Waiters keep their order through removal.
        sourceRegistry.remove(accountId)
        if (generation != null) repository.invalidate(accountId, generation)
        // Bump even with no active entry. A null-baseline register needs the change.
        removalEpochs[accountId] = removalEpochAllocator.incrementAndGet()
        states[accountId]?.value = states[accountId]?.value?.copy(isActive = false) ?: NotificationSyncState()
    }

    /**
     * Removes one account. Invalidation runs before repository removal, so late
     * stream events fail and the generation entry disappears. A new registration
     * after this call receives a fresh generation from the process-lifetime allocator.
     * Durable removal runs outside the controller lock. A trailing check under a short
     * lock then drops any entry that a racing registration published without repository
     * agreement, so a removed account cannot stay active while the repository disagrees.
     */
    override suspend fun removeAccount(accountId: AccountId) {
        synchronized(this) {
            unregister(accountId)
        }
        repository.remove(accountId)
        synchronized(this) {
            val published = generations[accountId]
            if (published != null && repository.currentToken(accountId)?.generation != published) {
                generations.remove(accountId)
                jobs.remove(accountId)?.cancel()
                sourceRegistry.remove(accountId)
                markInactiveLocked(accountId)
            }
            if (generations[accountId] == null) {
                states.remove(accountId)
            }
        }
    }

    /**
     * Registers one source. The generation allocator is monotonic for this controller lifetime
     * and never restarts after removal. Allocation alone publishes nothing. Activation runs
     * outside the controller lock and can race removal, so a stale registration undoes its
     * own activation through a compare-and-clear that touches only its own token. Adjunct
     * display state loads only for a candidate that still agrees with the active map, the
     * removal epoch, and the repository. Publish needs map agreement, epoch agreement,
     * repository agreement, and no retirement. The publish lock holds no repository
     * call; it uses only snapshots read outside the lock. The removal check in
     * [removeAccount] holds the controller lock across the in-memory
     * [NotificationRepository.currentToken] getter only, never across store or
     * network I/O. The repository monitor necessarily covers short in-memory checks plus the
     * activation store read. Callers invoke registration from an I/O context. The interface
     * does not enforce that context.
     * A concurrent removal wins through invalidate-before-delete, the repository tombstone,
     * the epoch check, stale undo, and the post-removal drop in [removeAccount]. A null
     * baseline needs the epoch and retirement checks: the active map alone cannot show
     * removal when no entry exists, and a newer token can clear the tombstone on activation.
     */
    override fun register(accountId: AccountId, source: SocialSource): NotificationSyncToken {
        val (baseline, epochBefore) = synchronized(this) {
            generations[accountId] to (removalEpochs[accountId] ?: 0L)
        }
        var lastToken = NotificationSyncToken(accountId, generationAllocator.incrementAndGet())
        repeat(MAX_ACTIVATION_ATTEMPTS) {
            val token = lastToken
            // A concurrent replacement or removal already moved the active entry or the
            // epoch. This token has not activated yet, so return inactive without it.
            if (synchronized(this) {
                generations[accountId] != baseline || (removalEpochs[accountId] ?: 0L) != epochBefore
            }
            ) return token
            // Test-only ordering gate. It runs outside locks. Production keeps it null.
            preActivateGate?.invoke()
            repository.activate(token)
            // Removal finished during activation wins at once, even when the map still
            // shows the null baseline and the newer token cleared the tombstone. Undo
            // restores the tombstone. No retry follows, or a newer retry would revive removal.
            if (synchronized(this) { (removalEpochs[accountId] ?: 0L) } != epochBefore) {
                repository.deactivate(token)
                return token
            }
            // Removal during activation wins at once. A tombstoned account never publishes.
            // No retry follows, or a newer retry would clear the tombstone and revive removal.
            if (repository.isRetired(accountId)) {
                repository.deactivate(token)
                return token
            }
            // The repository accepts a fresh allocation and a newer replacement. A rejection
            // without retirement means a concurrent replacement holds it, so retry with newer.
            val repositoryAccepted = repository.currentToken(accountId) == token
            // Read adjunct display state only for a candidate that still agrees with the
            // active map, the epoch, and the repository. Rejected and stale tokens issue
            // no store read. A race after this check still ends in stale undo below.
            val stillCandidate = synchronized(this) {
                generations[accountId] == baseline && (removalEpochs[accountId] ?: 0L) == epochBefore
            }
            val unreadState = if (repositoryAccepted && stillCandidate) {
                repository.observe(accountId).value.unreadState
            } else {
                NotificationUnreadState.Unknown
            }
            var published = false
            var stale = false
            var publishedState: MutableStateFlow<NotificationSyncState>? = null
            // Snapshot repository agreement outside the controller lock. The publish
            // lock uses only this snapshot plus map and epoch state.
            val repoTokenSnapshot = repository.currentToken(accountId)
            val repoRetiredSnapshot = repository.isRetired(accountId)
            val repoAgreesSnapshot = repositoryAccepted &&
                repoTokenSnapshot == token &&
                !repoRetiredSnapshot
            synchronized(this) {
                if (generations[accountId] != baseline || (removalEpochs[accountId] ?: 0L) != epochBefore) {
                    stale = true
                } else if (repoAgreesSnapshot) {
                    jobs.remove(accountId)?.cancel()
                    generations[accountId] = token.generation
                    sourceRegistry.register(token, source)
                    markActiveLocked(accountId, unreadState)
                    publishedState = states[accountId]
                    published = true
                }
                // Otherwise the repository rejected this allocation while the active entry
                // is unchanged. Nothing provisional was inserted, so the next attempt
                // allocates a newer generation unless removal now owns the account.
            }
            if (published) {
                // Re-verify repository agreement outside the lock. A concurrent removal
                // or replacement can move the repository after the snapshot. On mismatch
                // drop only own entry and deactivate only own token, then stay inactive
                // with no retry so a newer retry never revives removal or overtakes a winner.
                if (repository.currentToken(accountId) != token || repository.isRetired(accountId)) {
                    var undone = false
                    synchronized(this) {
                        if (generations[accountId] == token.generation) {
                            generations.remove(accountId)
                            jobs.remove(accountId)?.cancel()
                            sourceRegistry.remove(accountId)
                            markInactiveLocked(accountId)
                            undone = true
                        }
                    }
                    if (undone) repository.deactivate(token)
                    return token
                }
                // Test-only ordering gate. It runs outside locks between publication
                // and poll start. Production keeps it null.
                prePollGate?.invoke()
                // The flow comes from the publish lock. No map lookup follows, so removal
                // between publish and job start cannot throw. A stale job cancels below.
                val flow = publishedState
                if (flow != null) startPollJob(accountId, token, source, flow)
                return token
            }
            if (stale) {
                // This token lost the active map after it activated. A removal in this
                // window already revoked the token, and a winner publish means the
                // rejected token owns nothing, so no repository touch follows. The
                // activate-after-removal case already undid itself above. The loser
                // never clears winner ownership from here.
                return token
            }
            // A removal between adjunct read and publish must not retry. It must stay removed.
            if (repository.isRetired(accountId)) {
                repository.deactivate(token)
                return token
            }
            // Retry only on transient rejection with the map and the epoch intact.
            // A winner publish or epoch change means loss: exit inactive with no
            // further activation and no deactivate, so a newer retry never overtakes
            // the winner in the repository.
            if (synchronized(this) {
                generations[accountId] != baseline || (removalEpochs[accountId] ?: 0L) != epochBefore
            }
            ) return token
            lastToken = NotificationSyncToken(accountId, generationAllocator.incrementAndGet())
        }
        // Retries are exhausted. Nothing provisional was inserted, so no active entry
        // exists for the unactivated token. Return it inactive.
        return lastToken
    }

    override suspend fun refresh(accountId: AccountId, query: NotificationQuery): NotificationSyncResult {
        val token = repository.currentToken(accountId) ?: throw SourceError.Unauthorized
        val source = sourceRegistry.sourceFor(token) ?: throw SourceError.Unsupported("notifications.source")
        return synchronize(token, source, query)
    }

    override suspend fun loadOlder(accountId: AccountId, query: NotificationQuery): NotificationSyncResult {
        val token = repository.currentToken(accountId) ?: throw SourceError.Unauthorized
        val source = sourceRegistry.sourceFor(token) ?: throw SourceError.Unsupported("notifications.source")
        return lockFor(accountId).withLock { synchronizer.loadOlder(source, token, query) }
    }

    override suspend fun acknowledge(accountId: AccountId): NotificationAcknowledgement {
        val token = repository.currentToken(accountId) ?: throw SourceError.Unauthorized
        val source = sourceRegistry.sourceFor(token) ?: throw SourceError.Unsupported("notifications.source")
        return lockFor(accountId).withLock { synchronizer.applyAcknowledgement(source, token) }
    }

    suspend fun accept(event: Event): Boolean {
        val token = repository.currentToken(event.accountId) ?: return false
        return lockFor(event.accountId).withLock { repository.applyStreamEvent(token, event) }
    }

    fun setStreamConnected(accountId: AccountId, connected: Boolean, error: String? = null) {
        setStreamStatus(
            accountId,
            if (connected) NotificationStreamStatus.Ready else NotificationStreamStatus.Stopped,
            error,
        )
    }

    fun setStreamStatus(
        accountId: AccountId,
        status: NotificationStreamStatus,
        error: String? = null,
    ) {
        synchronized(this) {
            states[accountId]?.value = states[accountId]?.value?.copy(
                streamConnected = status == NotificationStreamStatus.Ready,
                streamError = error,
                streamStatus = status,
            ) ?: return
        }
    }

    /**
     * Runs one sync pass for one token. The method rejects a stale or retired token before
     * network work and before delivery. The final ownership check and the delivery enqueue
     * run atomically under a short controller lock with no repository call inside. Removal
     * clears the generation under the same lock, so removal between check and enqueue cannot
     * leave a stale enqueue. An enqueue that already ran while live stays harmless: the
     * delivery worker drops work with no session and the repository tombstone rejects claims.
     * Live behavior is unchanged.
     */
    private suspend fun synchronize(
        token: NotificationSyncToken,
        source: SocialSource,
        query: NotificationQuery,
    ): NotificationSyncResult {
        coroutineContext.ensureActive()
        if (!isCurrent(token) || repository.isRetired(token.accountId)) throw SourceError.Unauthorized
        val result = lockFor(token.accountId).withLock {
        coroutineContext.ensureActive()
        if (!isCurrent(token) || repository.isRetired(token.accountId)) throw SourceError.Unauthorized
        if (repository.checkpoint(token.accountId, query) == null) {
            synchronizer.establishBaseline(source, token, query)
        } else {
            synchronizer.catchUpNewer(source, token, query)
        }
        }
        coroutineContext.ensureActive()
        if (!isCurrent(token) || repository.isRetired(token.accountId)) throw SourceError.Unauthorized
        // Test-only ordering gate. It runs outside locks. Production keeps it null.
        preEnqueueGate?.invoke()
        synchronized(this) {
            if (!isCurrent(token)) throw SourceError.Unauthorized
            deliveryScheduler.enqueueDelivery(token.accountId)
        }
        return result
    }

    private fun lockFor(accountId: AccountId): Mutex = synchronized(this) {
        accountLocks.getOrPut(accountId, ::Mutex)
    }

    /** Marks one account active. Callers hold the controller lock and pass adjunct state read outside it. */
    private fun markActiveLocked(accountId: AccountId, unreadState: NotificationUnreadState) {
        states.getOrPut(accountId) { MutableStateFlow(NotificationSyncState()) }.also {
            it.value = it.value.copy(
                unreadState = unreadState,
                isActive = true,
                error = null,
            )
        }
    }

    /** Marks one account inactive. Callers hold the controller lock. */
    private fun markInactiveLocked(accountId: AccountId) {
        states[accountId]?.value = states[accountId]?.value?.copy(isActive = false) ?: NotificationSyncState()
    }

    /**
     * Starts polling for one accepted token. The caller passes the state flow that publication
     * captured under the controller lock. The method performs no map lookup. Each state write
     * runs atomically with its ownership check under a short controller lock: the lock verifies
     * the token is current and the captured flow is still attached, then writes the in-memory
     * flow value. A stale token cancels its job. A removed account leaves no job and no stale
     * write reaches detached collectors.
     */
    private fun startPollJob(
        accountId: AccountId,
        token: NotificationSyncToken,
        source: SocialSource,
        state: MutableStateFlow<NotificationSyncState>,
    ) {
        val job = scope.launch {
            while (isActive && isCurrent(token)) {
                try {
                    // A socket is an optimization, not the freshness authority. Keep REST
                    // reconciliation active because readiness can be overstated and events can
                    // be missed while a stream reconnects.
                    val result = synchronize(token, source, NotificationQuery())
                    // Snapshot adjunct read outside the controller lock. The write below
                    // uses only this snapshot under the lock.
                    val fallbackUnread = repository.observe(accountId).value.unreadState
                    val nextUnread = result.unreadState.takeUnless { it is NotificationUnreadState.Unknown }
                        ?: fallbackUnread
                    val wrote = synchronized(this@NotificationSyncOrchestrator) {
                        if (!isCurrent(token) || states[accountId] !== state) {
                            false
                        } else {
                            state.value = state.value.copy(
                                unreadState = nextUnread,
                                lastUpdated = System.currentTimeMillis(),
                                delayed = result.delayed,
                                error = null,
                            )
                            true
                        }
                    }
                    if (!wrote) break
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val wroteError = synchronized(this@NotificationSyncOrchestrator) {
                        if (!isCurrent(token) || states[accountId] !== state) {
                            false
                        } else {
                            state.value = state.value.copy(error = error.message ?: appMessages.notificationSyncFailed())
                            true
                        }
                    }
                    if (!wroteError) break
                }
                if (isCurrent(token)) delay(POLL_INTERVAL_MILLIS)
            }
        }
        synchronized(this) {
            if (isCurrent(token)) jobs[accountId] = job else job.cancel()
        }
    }

    @Synchronized
    private fun isCurrent(token: NotificationSyncToken): Boolean =
        generations[token.accountId] == token.generation

    override fun close() {
        scope.cancel()
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 60_000L
        // Removal advances the repository past one allocation (invalidate plus tombstone),
        // so one retry is the common case. The extra attempts cover a concurrent cycle.
        const val MAX_ACTIVATION_ATTEMPTS = 4
    }
}
