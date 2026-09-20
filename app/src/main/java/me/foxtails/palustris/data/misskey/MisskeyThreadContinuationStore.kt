package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.ThreadSessionKey

/**
 * Owns in-memory Misskey thread acquisitions until their next request.
 *
 * The store keeps at most 16 entries and expires entries after ten minutes of
 * idle time. A private lock protects pruning, validation, removal, and insert.
 * The store does not perform network work while it holds that lock.
 */
internal class MisskeyThreadContinuationStore(
    private val monotonicClock: () -> Long = System::nanoTime,
) {
    private data class Entry(
        val acquisition: ThreadAcquisition,
        val idleSince: Long,
    )

    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>()

    fun insert(token: String, acquisition: ThreadAcquisition) {
        synchronized(lock) {
            val now = monotonicClock()
            prune(now)
            entries[token] = Entry(acquisition, now)
            while (entries.size > CAPACITY) {
                val oldest = entries.minByOrNull { it.value.idleSince }?.key ?: break
                entries.remove(oldest)
            }
        }
    }

    fun consume(token: String, expected: ThreadSessionKey): ThreadAcquisition {
        synchronized(lock) {
            prune(monotonicClock())
            val entry = entries[token]
                ?: throw SourceError.Unsupported(FEATURE)
            if (entry.acquisition.key != expected) {
                throw SourceError.Unsupported(FEATURE)
            }
            entries.remove(token)
            return entry.acquisition
        }
    }

    private fun prune(now: Long) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value.idleSince >= IDLE_TIMEOUT_NANOS) {
                iterator.remove()
            }
        }
    }

    private companion object {
        const val CAPACITY = 16
        const val IDLE_TIMEOUT_NANOS = 10 * 60 * 1_000_000_000L
        const val FEATURE = "thread.continuation"
    }
}
