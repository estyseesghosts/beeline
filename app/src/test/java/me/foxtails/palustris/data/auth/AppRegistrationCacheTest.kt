package me.foxtails.palustris.data.auth

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AppRegistrationCacheTest {
    @Test
    fun reusesRegistrationAndCoordinatesConcurrentCreation() = runBlocking {
        val clock = FakeClock()
        val cache = AppRegistrationCache(clock::now)
        val creations = AtomicInteger()
        val creatorEntered = CompletableDeferred<Unit>()
        val releaseCreator = CompletableDeferred<Unit>()
        val results = (1..8).map {
            async {
                cache.getOrPut("https://example.org", setOf("read")) {
                    creations.incrementAndGet()
                    creatorEntered.complete(Unit)
                    releaseCreator.await()
                    AppRegistration("id", "secret", setOf("read"), scopesKnown = true)
                }
            }
        }

        creatorEntered.await()
        releaseCreator.complete(Unit)
        val completed = results.awaitAll()

        assertEquals(1, creations.get())
        completed.forEach { assertSame(completed.first(), it) }
    }

    @Test
    fun concurrentCallsOverlapWhileTheCreatorSuspends() = runBlocking {
        val cache = AppRegistrationCache(FakeClock()::now)
        val creatorEntered = CompletableDeferred<Unit>()
        val releaseCreator = CompletableDeferred<Unit>()
        val first = async {
            cache.getOrPut("origin", setOf("read")) {
                creatorEntered.complete(Unit)
                releaseCreator.await()
                registration(setOf("read"))
            }
        }
        creatorEntered.await()
        val second = async { cache.getOrPut("origin", setOf("read")) { error("duplicate creation") } }
        releaseCreator.complete(Unit)

        assertSame(first.await(), second.await())
    }

    @Test
    fun expiresAfterIdlePeriodAndLookupRefreshesIdleTime() {
        val clock = FakeClock()
        val cache = AppRegistrationCache(clock::now)
        val registration = registration(setOf("read"))
        cache.put("https://example.org", registration)

        clock.advance(IDLE / 2)
        assertSame(registration, cache.get("https://example.org"))
        clock.advance(IDLE / 2 + 1)
        assertSame(registration, cache.get("https://example.org"))
        clock.advance(IDLE)
        assertNull(cache.get("https://example.org"))
    }

    @Test
    fun evictsLeastRecentlyUsedEntryAndKeepsReturnedRegistrationUsable() = runBlocking {
        val cache = AppRegistrationCache()
        val first = cache.getOrPut("origin-0") { registration(setOf("read")) }
        repeat(16) { cache.put("origin-${it + 1}", registration(setOf("read"))) }

        assertEquals("id-1", first.clientId)
        assertEquals("secret", first.clientSecret)
        assertNull(cache.get("origin-0"))
    }

    @Test
    fun prunesExpiredEntriesOnInsertAndRequiredScopesForceUpgrade() = runBlocking {
        val clock = FakeClock()
        val cache = AppRegistrationCache(clock::now)
        cache.put("expired", registration(setOf("read")))
        clock.advance(IDLE)
        cache.put("new", registration(setOf("read")))
        assertNull(cache.get("expired"))

        val insufficient = registration(setOf("read"))
        val sufficient = registration(setOf("read", "push"))
        cache.put("scopes", insufficient)
        assertNull(cache.get("scopes", setOf("push")))
        assertSame(sufficient, cache.getOrPut("scopes", setOf("push")) { sufficient })
        assertSame(sufficient, cache.get("scopes", setOf("read", "push")))

        cache.put("unknown-scopes", AppRegistration("unknown", "secret"))
        assertNull(cache.get("unknown-scopes", setOf("read")))
    }

    @Test
    fun insufficientScopeLookupDoesNotPromoteEntryInLru() {
        val cache = AppRegistrationCache()
        cache.put("origin-0", registration(setOf("read")))
        repeat(15) { cache.put("origin-${it + 1}", registration(setOf("read"))) }

        assertNull(cache.get("origin-0", setOf("push")))
        cache.put("origin-16", registration(setOf("read")))

        assertNull(cache.get("origin-0"))
        assertNotNull(cache.get("origin-1"))
    }

    @Test
    fun concurrentScopeRequestsPublishOneSufficientRegistration() = runBlocking {
        val cache = AppRegistrationCache(FakeClock()::now)
        cache.put("origin", registration(setOf("read")))
        val creations = AtomicInteger()
        val creatorEntered = CompletableDeferred<Unit>()
        val releaseCreator = CompletableDeferred<Unit>()
        val results = (1..4).map {
            async {
                cache.getOrPut("origin", setOf("read", "push")) {
                    creations.incrementAndGet()
                    creatorEntered.complete(Unit)
                    releaseCreator.await()
                    registration(setOf("read", "push"))
                }
            }
        }

        creatorEntered.await()
        releaseCreator.complete(Unit)
        val completed = results.awaitAll()

        assertEquals(1, creations.get())
        completed.forEach { assertEquals(setOf("read", "push"), it.scopes) }
    }

    private fun registration(scopes: Set<String>) =
        AppRegistration("id-${scopes.size}", "secret", scopes, scopesKnown = true)

    private class FakeClock(var value: Long = 0) {
        fun now() = value
        fun advance(nanos: Long) {
            value += nanos
        }
    }

    private companion object {
        const val IDLE = 24L * 60 * 60 * 1_000_000_000
    }
}
