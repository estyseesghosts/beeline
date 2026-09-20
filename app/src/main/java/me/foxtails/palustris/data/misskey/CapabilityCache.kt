package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.ServerCapabilities

/**
 * Retains fresh Misskey capability evidence for one authentication session.
 * The revision belongs in the key because a replacement token must not reuse the old session's evidence.
 */
class CapabilityCache(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val values = LinkedHashMap<CapabilityCacheKey, ServerCapabilities>(
        MAX_ENTRIES,
        LOAD_FACTOR,
        true,
    )

    val size: Int get() = synchronized(this) { values.size }

    @Synchronized
    fun get(key: CapabilityCacheKey): ServerCapabilities? {
        pruneExpired()
        val value = values[key] ?: return null
        return value.takeIf(::isFresh) ?: values.remove(key).let { null }
    }

    @Synchronized
    fun put(key: CapabilityCacheKey, value: ServerCapabilities) {
        pruneExpired()
        values[key] = value
        if (values.size > MAX_ENTRIES) values.remove(values.entries.iterator().next().key)
    }

    @Synchronized
    fun remove(key: CapabilityCacheKey) {
        values.remove(key)
    }

    @Synchronized
    fun invalidate(accountId: AccountId) {
        values.keys.removeAll { it.accountId == accountId }
    }

    private fun pruneExpired() {
        values.entries.removeAll { !isFresh(it.value) }
    }

    private fun isFresh(value: ServerCapabilities): Boolean =
        clock() - value.capabilitiesLastUpdated < CAPABILITIES_TTL_MILLIS

    private companion object {
        const val MAX_ENTRIES = 32
        const val LOAD_FACTOR = 0.75f
        const val CAPABILITIES_TTL_MILLIS = 5 * 60 * 1000L
    }
}

/** The account session revision prevents capability reuse after token replacement. */
data class CapabilityCacheKey(
    val origin: String,
    val accountId: AccountId,
    val sessionRevision: Long,
)
