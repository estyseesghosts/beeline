package me.foxtails.palustris.data.misskey

import java.lang.reflect.Field
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.ThreadSessionKey
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MisskeyThreadContinuationTest {
    private var now = 0L
    private val origin = "https://example.org"
    private val account = AccountId(Connection(origin, Protocol.MISSKEY), "viewer")
    private val key = ThreadSessionKey(account, 1L, EntityId(origin, "root"))

    @Test
    fun capacityEvictsOldestAndInsertionPrunesExpiredEntries() {
        val store = store()
        repeat(16) { store.insert("token-$it", acquisition(it)) }
        now = 1
        store.insert("token-16", acquisition(16))

        assertThrows(SourceError.Unsupported::class.java) { store.consume("token-0", key) }
        assertNotNull(store.consume("token-16", key))

        now = TEN_MINUTES_NANOS + 1
        store.insert("fresh", acquisition(17))
        assertThrows(SourceError.Unsupported::class.java) { store.consume("token-1", key) }
        assertNotNull(store.consume("fresh", key))
    }

    @Test
    fun expiryAtLessThanTenMinutesSucceedsAndAfterTenMinutesRejects() {
        val store = store()
        store.insert("valid", acquisition(1))
        now = TEN_MINUTES_NANOS - 1
        assertNotNull(store.consume("valid", key))

        store.insert("expired", acquisition(2))
        now += TEN_MINUTES_NANOS
        assertThrows(SourceError.Unsupported::class.java) { store.consume("expired", key) }
    }

    @Test
    fun capacityRetainsNewestSixteenInInsertionOrder() {
        val store = store()
        repeat(17) { store.insert("token-$it", acquisition(it)) }

        assertThrows(SourceError.Unsupported::class.java) { store.consume("token-0", key) }
        repeat(16) { assertNotNull(store.consume("token-${it + 1}", key)) }
    }

    @Test
    fun missingTokenIsRejected() {
        val store = store()
        assertThrows(SourceError.Unsupported::class.java) { store.consume("never-inserted", key) }
    }

    @Test
    fun foreignAccountAndFocalRejectPreserveTheValidEntry() {
        val store = store()
        store.insert("one", acquisition(1))
        val foreignAccount = key.copy(
            fetchingAccount = AccountId(Connection(origin, Protocol.MISSKEY), "other"),
        )
        val foreignFocal = key.copy(focalId = EntityId(origin, "other-note"))

        assertThrows(SourceError.Unsupported::class.java) { store.consume("one", foreignAccount) }
        assertThrows(SourceError.Unsupported::class.java) { store.consume("one", foreignFocal) }
        assertNotNull(store.consume("one", key))
    }

    @Test
    fun singleUseAndForeignRejectPreserveTheValidEntry() {
        val store = store()
        store.insert("one", acquisition(1))
        val foreign = key.copy(sessionRevision = 2L)

        assertThrows(SourceError.Unsupported::class.java) { store.consume("one", foreign) }
        assertNotNull(store.consume("one", key))
        assertThrows(SourceError.Unsupported::class.java) { store.consume("one", key) }
    }

    @Test
    fun concurrentConsumeReturnsOneAcquisition() {
        val store = store()
        store.insert("one", acquisition(1))
        val executor = Executors.newFixedThreadPool(8)
        val successes = AtomicInteger()
        val start = CountDownLatch(1)
        val done = CountDownLatch(8)
        repeat(8) {
            executor.execute {
                start.await()
                try {
                    store.consume("one", key)
                    successes.incrementAndGet()
                } catch (_: SourceError.Unsupported) {
                    // Only one consumer can remove the entry.
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        executor.shutdownNow()
        assertEquals(1, successes.get())
    }

    @Test
    fun sourceValidatesSessionBeforeConsumingContinuation() = runBlocking {
        MockWebServer().use { server ->
            val source = sourceWithContinuation(server)
            val sessionKey = serverKey(server)
            val continuation = source.threadContext(sessionKey.focalId).continuation!!
            val foreign = continuation.copy(sessionKey = sessionKey.copy(sessionRevision = 2L))
            val requestsBeforeReject = server.requestCount

            assertThrows(SourceError.Unsupported::class.java) {
                runBlocking { source.threadContext(sessionKey.focalId, foreign) }
            }
            assertEquals(requestsBeforeReject, server.requestCount)

            server.enqueue(MockResponse().setBody("[]"))
            assertNotNull(source.threadContext(sessionKey.focalId, continuation))
        }
    }

    @Test
    fun sourceRejectsForeignAccountAndFocalWithoutNetworkOrConsumption() = runBlocking {
        MockWebServer().use { server ->
            val source = sourceWithContinuation(server)
            val serverOrigin = serverOrigin(server)
            val sessionKey = serverKey(server)
            val continuation = source.threadContext(sessionKey.focalId).continuation!!
            val foreignAccount = continuation.copy(
                sessionKey = sessionKey.copy(
                    fetchingAccount = AccountId(Connection(serverOrigin, Protocol.MISSKEY), "other"),
                ),
            )
            val foreignFocal = continuation.copy(
                sessionKey = sessionKey.copy(focalId = EntityId(serverOrigin, "other-note")),
            )
            val requestsBeforeReject = server.requestCount

            assertThrows(SourceError.Unsupported::class.java) {
                runBlocking { source.threadContext(sessionKey.focalId, foreignAccount) }
            }
            assertThrows(SourceError.Unsupported::class.java) {
                runBlocking { source.threadContext(sessionKey.focalId, foreignFocal) }
            }
            assertEquals(requestsBeforeReject, server.requestCount)

            server.enqueue(MockResponse().setBody("[]"))
            assertNotNull(source.threadContext(sessionKey.focalId, continuation))
        }
    }

    @Test
    fun sourceContinuationTokenIsOpaqueUuidAndSingleUse() {
        runBlocking {
        MockWebServer().use { server ->
            val source = sourceWithContinuation(server)
            val sessionKey = serverKey(server)
            val continuation = source.threadContext(sessionKey.focalId).continuation!!
            val parsed = UUID.fromString(continuation.token)

            assertNotNull(parsed)
            assertFalse(continuation.token.contains(sessionKey.fetchingAccount.localId))
            assertFalse(continuation.token.contains(sessionKey.focalId.value))
            assertNotEquals(sessionKey.sessionRevision.toString(), continuation.token)

            server.enqueue(MockResponse().setBody("[]"))
            source.threadContext(sessionKey.focalId, continuation)
            assertThrows(SourceError.Unsupported::class.java) {
                runBlocking { source.threadContext(sessionKey.focalId, continuation) }
            }
        }
        }
    }

    @Test
    fun sourcePreservesTransportOrderAcrossContinuations() = runBlocking {
        MockWebServer().use { server ->
            val serverOrigin = serverOrigin(server)
            val sessionKey = serverKey(server)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = JSONObject(request.body.readUtf8())
                    if (request.path == "/api/notes/show") {
                        return MockResponse().setBody(note(body.getString("noteId")))
                    }
                    if (body.getString("noteId") != "root") return MockResponse().setBody("[]")
                    if (!body.has("untilId")) {
                        // Serve the first page in non-alphabetical order.
                        // The source must keep this server order.
                        return MockResponse().setBody(
                            childrenInOrder("root", (29 downTo 0).map { "child-$it" }),
                        )
                    }
                    return MockResponse().setBody(childrenInOrder("root", listOf("zeta", "alpha")))
                }
            }
            val viewer = AccountId(Connection(serverOrigin, Protocol.MISSKEY), "viewer")
            val source = MisskeySource(
                serverOrigin,
                "token",
                MisskeyApi(),
                accountId = viewer,
                capabilityCache = CapabilityCache(),
                sessionRevision = sessionKey.sessionRevision,
                monotonicClock = { now },
            )

            var context = source.threadContext(sessionKey.focalId)
            var guard = 0
            while (context.continuation != null && guard++ < 10) {
                context = source.threadContext(sessionKey.focalId, context.continuation)
            }

            assertNull(context.continuation)
            assertEquals(
                (29 downTo 0).map { "child-$it" } + listOf("zeta", "alpha"),
                context.descendants.map { it.id.value },
            )
        }
    }

    @Test
    fun sourceReleasesContinuationStoreBeforeNetworkWork() {
        runBlocking {
        MockWebServer().use { server ->
            val requestCount = AtomicInteger()
            val networkStarted = CountDownLatch(1)
            val releaseNetwork = CountDownLatch(1)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path == "/api/notes/show") return MockResponse().setBody(note("root"))
                    return when (requestCount.incrementAndGet()) {
                        1 -> MockResponse().setBody(children("root"))
                        in 2..8 -> MockResponse().setBody("[]")
                        else -> {
                            networkStarted.countDown()
                            releaseNetwork.await(5, TimeUnit.SECONDS)
                            MockResponse().setBody("[]")
                        }
                    }
                }
            }
            val serverOrigin = serverOrigin(server)
            val sessionKey = serverKey(server)
            val viewer = AccountId(Connection(serverOrigin, Protocol.MISSKEY), "viewer")
            val source = MisskeySource(
                serverOrigin,
                "token",
                MisskeyApi(),
                accountId = viewer,
                capabilityCache = CapabilityCache(),
                sessionRevision = sessionKey.sessionRevision,
                monotonicClock = { now },
            )
            val continuation = source.threadContext(sessionKey.focalId).continuation!!
            val executor = Executors.newSingleThreadExecutor()
            val request: Future<*> = executor.submit {
                runBlocking { source.threadContext(sessionKey.focalId, continuation) }
            }

            assertTrue(networkStarted.await(5, TimeUnit.SECONDS))
            // The blocked network call must not hold the continuation lock.
            // This insert runs on the calling thread and must complete while
            // network work is still blocked.
            val store = continuationStore(source)
            store.insert("probe", acquisition(99))

            releaseNetwork.countDown()
            request.get(5, TimeUnit.SECONDS)
            executor.shutdownNow()
            assertNotNull(store.consume("probe", key))
        }
        }
    }

    private fun store() = MisskeyThreadContinuationStore { now }

    private fun serverOrigin(server: MockWebServer): String =
        server.url("/").toString().removeSuffix("/")

    private fun serverKey(server: MockWebServer): ThreadSessionKey {
        val serverOrigin = serverOrigin(server)
        return ThreadSessionKey(
            AccountId(Connection(serverOrigin, Protocol.MISSKEY), "viewer"),
            1L,
            EntityId(serverOrigin, "root"),
        )
    }

    private fun sourceWithContinuation(server: MockWebServer): MisskeySource {
        val serverOrigin = serverOrigin(server)
        val viewer = AccountId(Connection(serverOrigin, Protocol.MISSKEY), "viewer")
        server.enqueue(MockResponse().setBody(note("root")))
        server.enqueue(MockResponse().setBody(children("root")))
        repeat(20) { server.enqueue(MockResponse().setBody("[]")) }
        return MisskeySource(
            serverOrigin,
            "token",
            MisskeyApi(),
            accountId = viewer,
            capabilityCache = CapabilityCache(),
            sessionRevision = 1L,
            monotonicClock = { now },
        )
    }

    private fun continuationStore(source: MisskeySource): MisskeyThreadContinuationStore {
        val field: Field = MisskeySource::class.java.getDeclaredField("continuationStore")
        field.isAccessible = true
        return field.get(source) as MisskeyThreadContinuationStore
    }

    private fun note(id: String): String = JSONObject()
        .put("id", id)
        .put("createdAt", "2026-01-01T00:00:00.000Z")
        .put("text", "post")
        .put("visibility", "home")
        .put("user", JSONObject().put("id", "viewer").put("name", "Viewer").put("username", "viewer"))
        .toString()

    private fun children(parentId: String): String {
        val values = JSONArray()
        repeat(30) { values.put(JSONObject(note("child-$it")).put("replyId", parentId)) }
        return values.toString()
    }

    private fun childrenInOrder(parentId: String, ids: List<String>): String {
        val values = JSONArray()
        ids.forEach { values.put(JSONObject(note(it)).put("replyId", parentId)) }
        return values.toString()
    }

    private fun acquisition(id: Int) = ThreadAcquisition(
        key = key,
        focal = Post(
            id = EntityId(origin, "post-$id"),
            author = Account(account, "Viewer", "@viewer@example.org"),
            text = "post",
            publishedAtEpochMillis = 0L,
            audience = Audience.Public,
        ),
        ancestors = mutableListOf(),
        descendants = mutableListOf(),
        pending = ArrayDeque(),
        visitedRequests = mutableSetOf(),
        limitations = mutableListOf(),
        requestsUsed = 1,
    )

    private companion object {
        const val TEN_MINUTES_NANOS = 10 * 60 * 1_000_000_000L
    }
}
