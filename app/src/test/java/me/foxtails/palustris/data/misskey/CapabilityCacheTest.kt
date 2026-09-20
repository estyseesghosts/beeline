package me.foxtails.palustris.data.misskey

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityProbe
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Timeline
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CapabilityCacheTest {
    @Test
    fun freshLookupReturnsEntry() {
        val now = 1_000L
        val cache = CapabilityCache { now }
        val account = account("one")
        val first = capabilities(now)
        cache.put(CapabilityCacheKey("https://example", account, 1L), first)

        assertSame(first, cache.get(CapabilityCacheKey("https://example", account, 1L)))
    }

    @Test
    fun oldSessionEntryCannotAuthorizeNewSession() {
        val cache = CapabilityCache { 1_000L }
        val account = account("one")
        val oldKey = CapabilityCacheKey("https://example", account, 1L)
        cache.put(oldKey, capabilities(1_000L))

        assertNull(cache.get(CapabilityCacheKey("https://example", account, 2L)))
    }

    @Test
    fun expiredLookupMissesAndPrunesEntry() {
        var now = 1_000L
        val cache = CapabilityCache { now }
        val account = account("one")
        cache.put(CapabilityCacheKey("https://example", account, 1L), capabilities(now))
        now += 5 * 60 * 1000L

        assertNull(cache.get(CapabilityCacheKey("https://example", account, 1L)))
        assertEquals(0, cache.size)
    }

    @Test
    fun lookupPrunesExpiredEntriesBeforeReturningFreshEntry() {
        var now = 1_000L
        val cache = CapabilityCache { now }
        val account = account("one")
        val expired = CapabilityCacheKey("https://example", account, 1L)
        val fresh = CapabilityCacheKey("https://example", account, 2L)
        cache.put(expired, capabilities(now))
        now += 5 * 60 * 1000L
        cache.put(fresh, capabilities(now))

        assertEquals(now, cache.get(fresh)?.capabilitiesLastUpdated)
        assertNull(cache.get(expired))
        assertEquals(1, cache.size)
    }

    @Test
    fun insertionPrunesExpiredEntriesBeforeInsertion() {
        var now = 1_000L
        val cache = CapabilityCache { now }
        val account = account("one")
        val expired = CapabilityCacheKey("https://example", account, 1L)
        cache.put(expired, capabilities(now))
        now += 5 * 60 * 1000L
        val fresh = CapabilityCacheKey("https://example", account, 2L)
        cache.put(fresh, capabilities(now))
        assertEquals(1, cache.size)
        assertNull(cache.get(expired))
        assertEquals(now, cache.get(fresh)?.capabilitiesLastUpdated)
    }

    @Test
    fun insertionEvictsLeastRecentlyUsedEntryAtThirtyTwoDeterministically() {
        val now = 1_000L
        val cache = CapabilityCache { now }
        val account = account("one")
        fun key(revision: Long) = CapabilityCacheKey("https://example", account, revision)
        repeat(32) { revision -> cache.put(key(revision.toLong()), capabilities(now + revision)) }
        cache.get(key(0L))
        cache.put(key(32L), capabilities(now))

        assertEquals(32, cache.size)
        assertNull(cache.get(key(1L)))
        assertEquals(now, cache.get(key(0L))?.capabilitiesLastUpdated)
    }

    @Test
    fun accountInvalidationRemovesAllRevisionsOnlyForThatAccount() {
        val now = 1_000L
        val cache = CapabilityCache { now }
        val removed = account("removed")
        val kept = account("kept")
        cache.put(CapabilityCacheKey("https://example", removed, 1L), capabilities(now))
        cache.put(CapabilityCacheKey("https://example", removed, 2L), capabilities(now))
        cache.put(CapabilityCacheKey("https://example", kept, 1L), capabilities(now))

        cache.invalidate(removed)

        assertNull(cache.get(CapabilityCacheKey("https://example", removed, 1L)))
        assertNull(cache.get(CapabilityCacheKey("https://example", removed, 2L)))
        assertEquals(now, cache.get(CapabilityCacheKey("https://example", kept, 1L))?.capabilitiesLastUpdated)
    }

    @Test
    fun sameOriginAccountsKeepIndependentSnapshots() {
        val now = 1_000L
        val cache = CapabilityCache { now }
        val first = account("first")
        val second = account("second")
        val firstValue = capabilities(1_001L)
        val secondValue = capabilities(1_002L)
        cache.put(CapabilityCacheKey("https://example", first, 1L), firstValue)
        cache.put(CapabilityCacheKey("https://example", second, 1L), secondValue)

        assertSame(firstValue, cache.get(CapabilityCacheKey("https://example", first, 1L)))
        assertSame(secondValue, cache.get(CapabilityCacheKey("https://example", second, 1L)))
    }

    @Test
    fun lateOldSessionProbeDoesNotPublishCacheOrCallback() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[]"))
            val probeStarted = CompletableDeferred<Unit>()
            val releaseProbe = CompletableDeferred<Unit>()
            var current = true
            var callbackCount = 0
            val cache = CapabilityCache { 1_000L }
            val probe = object : CapabilityProbe {
                override suspend fun probeCapabilities(connection: Connection): ServerCapabilities {
                    probeStarted.complete(Unit)
                    releaseProbe.await()
                    return capabilities(1_000L)
                }
            }
            val source = MisskeySource(
                origin = server.url("/").toString().removeSuffix("/"),
                token = "old-token",
                api = MisskeyApi(),
                accountId = account("one"),
                capabilityProbe = probe,
                capabilityCache = cache,
                sessionRevision = 1L,
                isCurrentSession = { current },
                onCapabilitiesUpdated = { callbackCount++ },
            )

            val request = launch { source.timeline(Timeline.Home) }
            probeStarted.await()
            current = false
            releaseProbe.complete(Unit)
            request.join()

            assertEquals(0L, source.capabilities.capabilitiesLastUpdated)
            assertEquals(0, cache.size)
            assertEquals(0, callbackCount)
        }
    }

    private fun account(id: String) = AccountId(Connection("https://example", Protocol.MISSKEY), id)

    private fun capabilities(timestamp: Long) = ServerCapabilities(capabilitiesLastUpdated = timestamp)
}
