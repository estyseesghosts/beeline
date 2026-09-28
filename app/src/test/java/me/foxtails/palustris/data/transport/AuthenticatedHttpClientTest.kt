package me.foxtails.palustris.data.transport

import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedHttpClientTest {
    @Test
    fun foreignAuthenticatedUrlIsRejectedBeforeNetwork() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val client = AuthenticatedHttpClient(OkHttpClient())

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { client.getUrl(origin, "https://foreign.example/status", "secret") }
            }
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun cancellationCancelsInFlightCallAndRemainsCancellationException() = runBlocking {
        val failed = CountDownLatch(1)
        val canceled = AtomicBoolean(false)
        val http = OkHttpClient.Builder()
            .eventListener(object : EventListener() {
                override fun callFailed(call: okhttp3.Call, ioe: java.io.IOException) {
                    canceled.set(call.isCanceled())
                    failed.countDown()
                }
            })
            .build()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val origin = server.url("/").toString().removeSuffix("/")
            val request = async { AuthenticatedHttpClient(http).get(origin, "slow") }
            delay(100)
            server.takeRequest(5, TimeUnit.SECONDS)
            request.cancel()
            val cancellation = runCatching { request.await() }.exceptionOrNull()
            assertTrue("Expected cancellation, got $cancellation", cancellation is CancellationException)
            assertTrue("Expected OkHttp callFailed", failed.await(5, TimeUnit.SECONDS))
            assertTrue("Expected canceled OkHttp call", canceled.get())
        }
    }

    @Test
    fun responseLimitAllowsBelowAndAtBoundaryAndRejectsAbove() = runBlocking {
        MockWebServer().use { server ->
            val limit = 4L
            server.enqueue(MockResponse().setBody("abc"))
            server.enqueue(MockResponse().setBody("abcd"))
            server.enqueue(MockResponse().setBody("abcde"))
            val origin = server.url("/").toString().removeSuffix("/")
            val client = AuthenticatedHttpClient(OkHttpClient())

            assertEquals("abc", client.get(origin, "below", maxResponseBytes = limit).body)
            assertEquals("abcd", client.get(origin, "at", maxResponseBytes = limit).body)
            assertThrows(ResponseLimitExceeded::class.java) {
                runBlocking { client.get(origin, "above", maxResponseBytes = limit) }
            }
            Unit
        }
    }

    @Test
    fun redirectsStayDisabledAndDoNotSendBearerToTarget() = runBlocking {
        MockWebServer().use { source ->
            MockWebServer().use { target ->
                target.start()
                source.enqueue(MockResponse().setResponseCode(302).setHeader("Location", target.url("/target")))
                val origin = source.url("/").toString().removeSuffix("/")
                val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()
                assertThrows(HttpStatusFailure::class.java) {
                    runBlocking { AuthenticatedHttpClient(http).get(origin, "start", "secret") }
                }

                assertEquals("Bearer secret", source.takeRequest().getHeader("Authorization"))
                assertEquals(0, target.requestCount)
            }
        }
    }

    @Test
    fun websocketOriginValidationAcceptsHttpsAndRejectsUnsafeOrigins() {
        val client = AuthenticatedHttpClient(OkHttpClient())
        assertThrows(IllegalArgumentException::class.java) {
            client.webSocket("http://example.org", "/stream", listener = object : okhttp3.WebSocketListener() {})
        }
        assertThrows(IllegalArgumentException::class.java) {
            client.webSocket("https://user:pass@example.org", "/stream", listener = object : okhttp3.WebSocketListener() {})
        }
        val socket = client.webSocket("https://example.org", "/stream", listener = object : okhttp3.WebSocketListener() {})
        socket.cancel()
    }

    @Test
    fun multipartStreamClosesAfterRequestBodyWrite() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            val closed = AtomicBoolean(false)
            val stream = object : FilterInputStream(ByteArrayInputStream("file".toByteArray())) {
                override fun close() {
                    closed.set(true)
                    super.close()
                }
            }
            val origin = server.url("/").toString().removeSuffix("/")
            AuthenticatedHttpClient(OkHttpClient()).patchMultipart(
                origin = origin,
                path = "upload",
                files = listOf(MultipartFileBody("file", "file.txt", "text/plain", stream)),
            )
            assertTrue(closed.get())
            assertTrue(server.takeRequest().body.readByteString().size > 0)
        }
    }
}
