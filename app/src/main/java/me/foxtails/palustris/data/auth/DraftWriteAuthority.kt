package me.foxtails.palustris.data.auth

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
 * The account lifecycle activates a writer when a session connects and revokes it on removal.
 * A draft owner captures the active generation; it does not issue one. All state changes for
 * one account run under the same per-account lock: activation, revocation, deletion, and
 * accepted writes. A store commit checks the generation under that lock, so a revoked writer
 * cannot interleave a check-then-write and recreate a draft after removal.
 *
 * No lock is held during a network request. The generation is monotonic, so an activation waits
 * for any commit that is already running before it revokes the previous writer.
 */
@Singleton
class DraftWriteAuthority @Inject constructor() {
    private class Record {
        val generation: AtomicLong
        val mutex = Mutex()
        var users = 0
        var retiring = false

        constructor(generation: AtomicLong) {
            this.generation = generation
        }
    }

    private val allocators = ConcurrentHashMap<AccountId, AtomicLong>()
    private val records = ConcurrentHashMap<AccountId, Record>()

    /** Issues the writer generation for a session activation. Revokes the previous writer. */
    suspend fun activate(accountId: AccountId): Long {
        while (true) {
            val record = acquire(accountId)
            val generation = record.mutex.withLock {
                if (record.retiring) null else record.generation.incrementAndGet()
            }
            release(accountId, record)
            if (generation != null) return generation
        }
    }

    fun isCurrent(accountId: AccountId, generation: Long): Boolean =
        records[accountId]?.let { !it.retiring && it.generation.get() == generation } == true

    /**
     * Runs [block] under the account lock when [generation] is still current.
     * Returns null when a removal revoked the writer first.
     */
    suspend fun <T> commitIfCurrent(accountId: AccountId, generation: Long, block: suspend () -> T): T? {
        val record = acquire(accountId)
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
        val record = acquire(accountId)
        try {
            record.mutex.withLock { record.generation.incrementAndGet() }
        } finally {
            release(accountId, record)
        }
    }

    /** Revokes writers, then deletes rows in the same serialized boundary. */
    suspend fun invalidateAndDelete(accountId: AccountId, delete: suspend () -> Unit) {
        val record = acquire(accountId)
        try {
            record.mutex.withLock {
                record.retiring = true
                record.generation.incrementAndGet()
                delete()
            }
        } finally {
            release(accountId, record)
        }
    }

    private fun acquire(accountId: AccountId): Record = synchronized(this) {
        val allocator = allocators.getOrPut(accountId) { AtomicLong(0L) }
        val record = records.getOrPut(accountId) { Record(allocator) }
        record.users++
        record
    }

    private fun release(accountId: AccountId, record: Record) = synchronized(this) {
        record.users--
        if (record.retiring && record.users == 0) records.remove(accountId, record)
    }
}
