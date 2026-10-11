package me.foxtails.palustris.data.media

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The retry rule of the media loader, against a fake server. */
class MediaRetryInterceptorTest {
    private val server = MockWebServer()
    private val waits = mutableListOf<Long>()

    @Before fun start() = server.start()

    @After fun stop() = server.shutdown()

    private fun client(maxWait: Long = 3_000L) = OkHttpClient.Builder()
        .readTimeout(300, TimeUnit.MILLISECONDS)
        .addInterceptor(MediaRetryInterceptor(maxWait) { waits += it })
        .build()

    private fun get() = client().newCall(Request.Builder().url(server.url("/media")).build()).execute()

    @Test
    fun aServerErrorIsRetriedOnceAndTheSecondAnswerWins() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(200).setBody("image"))

        get().use { assertEquals(200, it.code) }

        assertEquals(2, server.requestCount)
    }

    @Test
    fun onlyOneRetryHappensWhenTheServerKeepsFailing() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(502))
        server.enqueue(MockResponse().setResponseCode(200))

        get().use { assertEquals(502, it.code) }

        assertEquals(2, server.requestCount)
    }

    @Test
    fun aRateLimitWaitsForRetryAfterAndRetries() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "2"))
        server.enqueue(MockResponse().setResponseCode(200))

        get().use { assertEquals(200, it.code) }

        assertEquals(listOf(2_000L), waits)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun theWaitIsCapped() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))
        server.enqueue(MockResponse().setResponseCode(200))

        get().use { assertEquals(200, it.code) }

        assertEquals(listOf(3_000L), waits)
    }

    @Test
    fun aRateLimitWithoutAUsableHeaderWaitsOneSecond() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "Wed, 21 Oct 2026 07:28:00 GMT"))
        server.enqueue(MockResponse().setResponseCode(200))

        get().use { assertEquals(200, it.code) }

        assertEquals(listOf(1_000L), waits)
    }

    @Test
    fun aServerErrorRetriesWithoutWaiting() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(200))

        get().use { assertEquals(200, it.code) }

        assertEquals(emptyList<Long>(), waits)
    }

    @Test
    fun aClientErrorOtherThanARateLimitIsNeverRetried() {
        for (code in listOf(400, 401, 403, 404, 410)) {
            server.enqueue(MockResponse().setResponseCode(code))
        }

        for (code in listOf(400, 401, 403, 404, 410)) {
            get().use { assertEquals(code, it.code) }
        }

        assertEquals(5, server.requestCount)
    }

    @Test
    fun aTimeoutIsRetriedOnce() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.enqueue(MockResponse().setResponseCode(200).setBody("image"))

        get().use { assertEquals(200, it.code) }

        assertEquals(2, server.requestCount)
    }
}
