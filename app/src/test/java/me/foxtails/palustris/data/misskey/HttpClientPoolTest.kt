package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.data.transport.HttpClientPool
import me.foxtails.palustris.data.transport.HttpLayerConfig
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpClientPoolTest {
    @Test
    fun equalKeysReuseSameClient() {
        val pool = HttpClientPool()
        val first = Connection("https://example.org", Protocol.MISSKEY)
        val equal = Connection("https://example.org", Protocol.MISSKEY)

        assertSame(pool.clientFor(first), pool.clientFor(equal))
    }

    @Test
    fun protocolIsPartOfKey() {
        val pool = HttpClientPool()
        val misskey = Connection("https://example.org", Protocol.MISSKEY)
        val mastodon = Connection("https://example.org", Protocol.MASTODON)

        assertNotSame(pool.clientFor(misskey), pool.clientFor(mastodon))
    }

    @Test
    fun concurrentEqualMissesConstructOneClient() {
        val pool = HttpClientPool()
        val connection = Connection("https://concurrent.example", Protocol.MISSKEY)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(16)
        val clients = Collections.synchronizedSet(mutableSetOf<Any>())

        repeat(16) {
            executor.execute {
                start.await()
                clients += pool.clientFor(connection)
            }
        }
        start.countDown()
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals(1, clients.size)
    }

    @Test
    fun lookupIsBoundedAndUsesAccessOrder() {
        val pool = HttpClientPool()
        val connections = (0..15).map {
            Connection("https://$it.example", Protocol.MISSKEY)
        }
        val clients = connections.map(pool::clientFor)
        val first = clients.first()
        val second = clients[1]

        // Touch the oldest entry before inserting the seventeenth distinct key.
        assertSame(first, pool.clientFor(connections.first()))
        pool.clientFor(Connection("https://new.example", Protocol.MISSKEY))

        assertSame(first, pool.clientFor(connections.first()))
        assertNotSame(second, pool.clientFor(connections[1]))
    }

    @Test
    fun retentionStaysBoundedAfterManyDistinctKeys() {
        val pool = HttpClientPool()
        val early = Connection("https://early.example", Protocol.MISSKEY)
        val earlyClient = pool.clientFor(early)
        val recentConnections = (0 until 32).map {
            Connection("https://many-$it.example", Protocol.MISSKEY)
        }
        val recentClients = recentConnections.map(pool::clientFor)

        // The newest entry remains in the lookup after many insertions.
        assertSame(recentClients.last(), pool.clientFor(recentConnections.last()))
        // The earliest entry left the lookup, so lookup creates a new client.
        assertNotSame(earlyClient, pool.clientFor(early))
    }

    @Test
    fun clientsPreserveTimeoutAndRedirectPolicy() {
        val pool = HttpClientPool(
            HttpLayerConfig(connectTimeoutSeconds = 3, readTimeoutSeconds = 4, callTimeoutSeconds = 5),
        )
        val client = pool.clientFor(Connection("https://policy.example", Protocol.MISSKEY))

        assertEquals(3_000, client.connectTimeoutMillis)
        assertEquals(4_000, client.readTimeoutMillis)
        assertEquals(5_000, client.callTimeoutMillis)
        assertTrue(!client.followRedirects)
        assertTrue(!client.followSslRedirects)
    }

    @Test
    fun evictedBorrowedClientStillPerformsRequests() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            val pool = HttpClientPool()
            val borrowed = pool.clientFor(Connection(server.url("/").toString(), Protocol.MISSKEY))
            (0..16).forEach {
                pool.clientFor(Connection("https://eviction-$it.example", Protocol.MISSKEY))
            }

            val response = borrowed.newCall(Request.Builder().url(server.url("/")).build()).execute()
            assertEquals("ok", response.use { requireNotNull(it.body).string() })
        }
    }

    @Test
    fun responseLimitKeepsBelowAndAtBoundaryAndRejectsAbove() {
        runBlocking {
            MockWebServer().also { it.start() }.use { server ->
                val limit = 4L * 1024 * 1024
                server.enqueue(MockResponse().setBody("x".repeat((limit - 1).toInt())))
                server.enqueue(MockResponse().setBody("x".repeat(limit.toInt())))
                server.enqueue(MockResponse().setBody("x".repeat((limit + 1).toInt())))
                val origin = server.url("/").toString().removeSuffix("/")
                val api = MisskeyApi(HttpClientPool().clientFor(Connection(origin, Protocol.MISSKEY)))

                assertEquals(limit - 1, api.get(origin, "below", maxResponseBytes = limit).body.length.toLong())
                assertEquals(limit, api.get(origin, "at", maxResponseBytes = limit).body.length.toLong())
                assertThrows(ResponseLimitExceeded::class.java) {
                    runBlocking { api.get(origin, "above", maxResponseBytes = limit) }
                }
            }
        }
    }

    @Test
    fun serverAddressRejectsUnsafeOriginsBeforeUse() {
        listOf(
            "http://example.org",
            "https://user:pass@example.org",
            "https://example.org/path",
            "https://example.org?token=secret",
            "https://example.org#fragment",
        ).forEach { input ->
            assertThrows(IllegalArgumentException::class.java) { ServerAddress.normalize(input) }
        }
        assertEquals("https://example.org", ServerAddress.normalize(" example.org/ "))
    }
}
