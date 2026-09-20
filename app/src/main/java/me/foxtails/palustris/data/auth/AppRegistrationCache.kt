package me.foxtails.palustris.data.auth

import java.util.LinkedHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AppRegistration(
    val clientId: String,
    val clientSecret: String,
    val scopes: Set<String> = emptySet(),
    val scopesKnown: Boolean = false,
)

/**
 * Retains Mastodon application credentials by origin.
 *
 * The access-order map is bounded and expires entries after 24 hours of idle time. Per-origin
 * mutexes serialize registration creation without holding the map lock across network work.
 */
class AppRegistrationCache(
    private val clock: () -> Long = System::nanoTime,
) {
    private data class Entry(val registration: AppRegistration, var lastAccessNanos: Long)
    private class CreationGate {
        val mutex = Mutex()
        var users = 0
    }

    private val lock = Any()
    private val registrations = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private val creationLocks = java.util.concurrent.ConcurrentHashMap<String, CreationGate>()

    fun get(origin: String): AppRegistration? = synchronized(lock) {
        val now = clock()
        pruneExpired(now)
        registrations[origin]?.also { it.lastAccessNanos = now }?.registration
    }

    fun get(origin: String, requiredScopes: Set<String>): AppRegistration? = synchronized(lock) {
        val now = clock()
        pruneExpired(now)
        val entry = registrations.entries.firstOrNull { it.key == origin }?.value
        if (entry == null || !entry.registration.supports(requiredScopes)) {
            null
        } else {
            registrations.remove(origin)
            registrations[origin] = entry
            entry.lastAccessNanos = now
            entry.registration
        }
    }

    suspend fun getOrPut(origin: String, create: suspend () -> AppRegistration): AppRegistration =
        getOrPutInternal(origin, emptySet(), false, create)

    suspend fun getOrPut(
        origin: String,
        requiredScopes: Set<String>,
        create: suspend () -> AppRegistration,
    ): AppRegistration = getOrPutInternal(origin, requiredScopes, true, create)

    fun put(origin: String, registration: AppRegistration) = synchronized(lock) {
        val now = clock()
        pruneExpired(now)
        registrations[origin] = Entry(registration, now)
        evictIfNeeded()
    }

    private suspend fun getOrPutInternal(
        origin: String,
        requiredScopes: Set<String>,
        requireScopes: Boolean,
        create: suspend () -> AppRegistration,
    ): AppRegistration {
        val cached = if (requireScopes) get(origin, requiredScopes) else get(origin)
        if (cached != null) return cached

        val creationGate = synchronized(creationLocks) {
            creationLocks.computeIfAbsent(origin) { CreationGate() }.also { it.users++ }
        }
        return try {
            creationGate.mutex.withLock {
                val current = if (requireScopes) get(origin, requiredScopes) else get(origin)
                if (current != null) return@withLock current

                val created = create()
                val published = synchronized(lock) {
                    val now = clock()
                    pruneExpired(now)
                    val existing = registrations.entries.firstOrNull { it.key == origin }?.value
                    if (existing != null && (!requireScopes || existing.registration.supports(requiredScopes))) {
                        registrations.remove(origin)
                        registrations[origin] = existing
                        existing.lastAccessNanos = now
                        existing.registration
                    } else {
                        registrations[origin] = Entry(created, now)
                        evictIfNeeded()
                        created
                    }
                }
                published
            }
        } finally {
            synchronized(creationLocks) {
                creationGate.users--
                if (creationGate.users == 0) creationLocks.remove(origin, creationGate)
            }
        }
    }

    private fun pruneExpired(now: Long) {
        registrations.entries.removeIf { now - it.value.lastAccessNanos >= IDLE_EXPIRY_NANOS }
    }

    private fun evictIfNeeded() {
        while (registrations.size > MAX_ENTRIES) {
            registrations.entries.iterator().apply { next(); remove() }
        }
    }

    private fun AppRegistration.supports(requiredScopes: Set<String>): Boolean =
        scopesKnown && requiredScopes.all { it in scopes }

    private companion object {
        const val MAX_ENTRIES = 16
        const val IDLE_EXPIRY_NANOS = 24L * 60 * 60 * 1_000_000_000
    }
}
