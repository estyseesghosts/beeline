package me.foxtails.palustris.data.misskey

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpClientPoolTest {
    @Test
    fun equalConnectionsReuseClientAndProtocolIsPartOfKey() {
        val pool = HttpClientPool()
        val misskey = Connection("https://example.org", Protocol.MISSKEY)
        val equalMisskey = Connection("https://example.org", Protocol.MISSKEY)

        assertSame(pool.clientFor(misskey), pool.clientFor(equalMisskey))
        assertNotSame(
            pool.clientFor(misskey),
            pool.clientFor(Connection("https://example.org", Protocol.MASTODON)),
        )
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
    fun clientsPreserveConfigurationAndBorrowedClientRemainsUsableAfterEviction() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            val pool = HttpClientPool(
                HttpLayerConfig(connectTimeoutSeconds = 3, readTimeoutSeconds = 4, callTimeoutSeconds = 5),
            )
            val borrowed = pool.clientFor(Connection(server.url("/").toString(), Protocol.MISSKEY))
            (0..16).forEach {
                pool.clientFor(Connection("https://eviction-$it.example", Protocol.MISSKEY))
            }

            assertEquals(3_000L, borrowed.connectTimeoutMillis)
            assertEquals(4_000L, borrowed.readTimeoutMillis)
            assertEquals(5_000L, borrowed.callTimeoutMillis)
            assertTrue(!borrowed.followRedirects)
            assertTrue(!borrowed.followSslRedirects)
            val response = borrowed.newCall(Request.Builder().url(server.url("/")).build()).execute()
            assertEquals("ok", response.use { it.body.string() })
        }
    }
}
