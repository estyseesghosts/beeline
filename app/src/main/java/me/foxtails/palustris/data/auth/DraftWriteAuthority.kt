package me.foxtails.palustris.data.auth

import androidx.annotation.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.foxtails.palustris.domain.AccountId

/**
 * Owns durable write authority for draft rows, one generation per account.
 *
 * State owned: per-account generation and lock record.
 * Lifetime: the record lives while the account remains active or while holders remain.
 * Creation point: [activate] creates the record for a session activation.
 * Release point: account removal retires the record, and the final release removes it.
 *
 * The account lifecycle activates a writer when a session connects and revokes it on removal.
 * A draft owner captures the active generation. The owner does not issue a generation.
 * All state changes for one account run under the same per-account lock. Activation,
 * revocation, deletion, and accepted writes share that lock. A store commit checks the
 * generation under that lock. A revoked writer cannot recreate a draft after removal.
 *
 * The allocator is monotonic for the process lifetime. Removal releases the account record
 * and retains no account key. A later activation receives a new generation. The new
 * generation cannot match old work. A late commit finds no record and returns null.
 * The late commit creates no record and runs no block.
 *
 * No lock spans a network request.
 */
@Singleton
class DraftWriteAuthority @Inject constructor() {
    private class Record(initialGeneration: Long) {
        val generation = AtomicLong(initialGeneration)
        val mutex = Mutex()
        var users = 0
        @Volatile var retiring = false
    }

    private val nextGeneration = AtomicLong(0L)
    private val records = ConcurrentHashMap<AccountId, Record>()

    /** Issues the writer generation for a session activation. Revokes the previous writer. */
    suspend fun activate(accountId: AccountId): Long {
        while (true) {
            val record = acquireOrCreate(accountId)
            try {
                val generation = record.mutex.withLock {
                    if (record.retiring) {
                        null
                    } else {
                        nextGeneration.incrementAndGet().also { record.generation.set(it) }
                    }
                }
                if (generation != null) return generation
            } finally {
                release(accountId, record)
            }
        }
    }

    fun isCurrent(accountId: AccountId, generation: Long): Boolean =
        records[accountId]?.let { !it.retiring && it.generation.get() == generation } == true

    /**
     * Runs [block] under the account lock when [generation] is still current.
     * Returns null when a removal revoked the writer first.
     * A missing record rejects the commit and creates no record.
     */
    suspend fun <T> commitIfCurrent(accountId: AccountId, generation: Long, block: suspend () -> T): T? {
        val record = acquireExisting(accountId) ?: return null
        return try {
            record.mutex.withLock {
                if (record.retiring || record.generation.get() != generation) null else block()
            }
        } finally {
            release(accountId, record)
        }
    }

    /** Revokes writers without deleting rows. Serialized with accepted writes. */
    suspend fun invalidate(accountId: AccountId) {
        val record = acquireExisting(accountId) ?: return
        try {
            record.mutex.withLock {
                if (!record.retiring) record.generation.set(nextGeneration.incrementAndGet())
            }
        } finally {
            release(accountId, record)
        }
    }

    /**
     * Revokes writers, then deletes rows in the same serialized boundary.
     * The generation is invalidated before the delete callback runs.
     * A missing record runs the delete callback without creating a record.
     */
    suspend fun invalidateAndDelete(accountId: AccountId, delete: suspend () -> Unit) {
        val record = acquireExisting(accountId)
        if (record == null) {
            delete()
            return
        }
        try {
            record.mutex.withLock {
                record.retiring = true
                record.generation.set(nextGeneration.incrementAndGet())
                delete()
            }
        } finally {
            release(accountId, record)
        }
    }

    @VisibleForTesting
    internal fun hasRecordForTest(accountId: AccountId): Boolean =
        synchronized(this) { records.containsKey(accountId) }

    private fun acquireOrCreate(accountId: AccountId): Record = synchronized(this) {
        val record = records.getOrPut(accountId) { Record(Long.MIN_VALUE) }
        record.users++
        record
    }

    private fun acquireExisting(accountId: AccountId): Record? = synchronized(this) {
        val record = records[accountId] ?: return@synchronized null
        record.users++
        record
    }

    private fun release(accountId: AccountId, record: Record) = synchronized(this) {
        record.users--
        if (record.retiring && record.users == 0) records.remove(accountId, record)
    }
}
