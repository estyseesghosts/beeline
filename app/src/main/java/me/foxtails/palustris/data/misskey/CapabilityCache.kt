package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.ServerCapabilities

/**
 * Retains fresh Misskey capability evidence for one authentication session.
 * The revision belongs in the key because a replacement token must not reuse the old session's evidence.
 *
 * The revision alone cannot separate a removed session from its replacement:
 * removal deletes the stored session, so a re-added account restarts at revision one.
 * The cache therefore owns an opaque session identity. [activate] allocates a new
 * identity on every session boundary, [invalidate] retires it, and the fenced [put]
 * stores evidence only when the identity matches the active one. A late probe from
 * an old session cannot repopulate the cache after removal or replacement.
 */
class CapabilityCache(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val values = LinkedHashMap<CapabilityCacheKey, ServerCapabilities>(
        MAX_ENTRIES,
        LOAD_FACTOR,
        true,
    )
    private val activeIdentities = mutableMapOf<AccountId, Long>()
    private var nextIdentity = 0L

    val size: Int get() = synchronized(this) { values.size }

    @Synchronized
    fun get(key: CapabilityCacheKey): ServerCapabilities? {
        pruneExpired()
        val value = values[key] ?: return null
        return value.takeIf(::isFresh) ?: values.remove(key).let { null }
    }

    /**
     * Fenced path for session sources. It returns evidence only when
     * [sessionIdentity] matches the active identity for this account.
     * A stale source gets a miss, so it cannot consume replacement evidence
     * after removal and re-add restart the revision. Anonymous sources must
     * keep the unfenced [get] because they have no session boundary.
     */
    @Synchronized
    fun get(key: CapabilityCacheKey, sessionIdentity: Long): ServerCapabilities? {
        if (activeIdentities[key.accountId] != sessionIdentity) return null
        return get(key)
    }

    /**
     * Direct path for anonymous sources and tests without a session boundary.
     * Session sources must use the fenced [put] so a retired session cannot publish.
     */
    @Synchronized
    fun put(key: CapabilityCacheKey, value: ServerCapabilities) {
        pruneExpired()
        values[key] = value
        if (values.size > MAX_ENTRIES) values.remove(values.entries.iterator().next().key)
    }

    /**
     * Stores evidence only when [sessionIdentity] matches the identity from the latest
     * [activate] call for this account. It returns false for a stale or removed session,
     * and it stores nothing in that case.
     */
    @Synchronized
    fun put(key: CapabilityCacheKey, value: ServerCapabilities, sessionIdentity: Long): Boolean {
        if (activeIdentities[key.accountId] != sessionIdentity) return false
        put(key, value)
        return true
    }

    /**
     * Starts a new session boundary for [accountId]. It drops retained evidence for the
     * account and returns a fresh opaque identity. Fenced puts from the new session
     * must carry this identity. Identities never repeat, so an old identity stays invalid.
     */
    @Synchronized
    fun activate(accountId: AccountId): Long {
        values.keys.removeAll { it.accountId == accountId }
        nextIdentity += 1
        activeIdentities[accountId] = nextIdentity
        return nextIdentity
    }

    /**
     * Reads the active identity without starting a new boundary. The source factory
     * attaches this identity to a session source so transient sources share it.
     * It returns null when no session boundary is active for this account.
     */
    @Synchronized
    fun currentIdentity(accountId: AccountId): Long? = activeIdentities[accountId]

    @Synchronized
    fun remove(key: CapabilityCacheKey) {
        values.remove(key)
    }

    @Synchronized
    fun invalidate(accountId: AccountId) {
        activeIdentities.remove(accountId)
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
