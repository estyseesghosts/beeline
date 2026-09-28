package me.foxtails.palustris.data.misskey

import java.io.ByteArrayInputStream
import me.foxtails.palustris.data.transport.MultipartFileBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import me.foxtails.palustris.ProductIdentity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MisskeyApiTest {
    @Test
    fun postPinsJsonRequestAndReturnsJsonAndNonJsonBodies() = runBlocking {
        MockWebServer().also { it.start() }.use { server ->
            server.enqueue(MockResponse().setBody("{\"ok\":true}"))
            server.enqueue(MockResponse().setBody("plain text"))
            val api = MisskeyApi()
            val origin = server.url("/").toString().removeSuffix("/")

            val json = api.post(origin, "notes?limit=2", JSONObject().put("text", "hello"))
            val plain = api.get(origin, "health")
            val jsonRequest = server.takeRequest()
            val plainRequest = server.takeRequest()

            assertEquals("{\"ok\":true}", json.body)
            assertEquals("plain text", plain.body)
            assertEquals("POST", jsonRequest.method)
            assertEquals("/api/notes?limit=2", jsonRequest.path)
            assertEquals("application/json; charset=utf-8", jsonRequest.getHeader("Content-Type"))
            assertEquals("{\"text\":\"hello\"}", jsonRequest.body.readUtf8())
            assertEquals("GET", plainRequest.method)
            assertEquals("/api/health", plainRequest.path)
            assertEquals(ProductIdentity.userAgent, jsonRequest.getHeader("User-Agent"))
            assertEquals("application/json", jsonRequest.getHeader("Accept"))
        }
    }

    @Test
    fun formAndMultipartRequestsPinBearerAndContentTypes() = runBlocking {
        MockWebServer().also { it.start() }.use { server ->
            server.enqueue(MockResponse().setBody("form"))
            server.enqueue(MockResponse().setBody("multipart"))
            val api = MisskeyApi()
            val origin = server.url("/").toString().removeSuffix("/")
            val closed = ClosingInputStream("file-bytes".toByteArray())

            api.postForm(origin, "api/v1/statuses", listOf("status" to "hello world"), "token")
            api.patchMultipart(
                origin,
                "api/v1/accounts/update_credentials",
                files = listOf(MultipartFileBody("header", "header.png", "image/png", closed)),
                bearerToken = "token",
            )

            val form = server.takeRequest()
            val multipart = server.takeRequest()
            assertEquals("POST", form.method)
            assertEquals("/api/v1/statuses", form.path)
            assertEquals("Bearer token", form.getHeader("Authorization"))
            assertTrue(form.getHeader("Content-Type")!!.startsWith("application/x-www-form-urlencoded"))
            assertEquals("status=hello%20world", form.body.readUtf8())
            assertEquals("PATCH", multipart.method)
            assertEquals("Bearer token", multipart.getHeader("Authorization"))
            assertTrue(multipart.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
            assertTrue(multipart.body.readUtf8().contains("file-bytes"))
            assertTrue(closed.closed)
        }
    }

    @Test
    fun errorResponsesExposeApiFailureWithoutMappingAdapterErrors() = runBlocking {
        MockWebServer().also { it.start() }.use { server ->
            server.enqueue(MockResponse().setResponseCode(400).setBody("{\"error\":{\"code\":\"Bad\"}}"))
            server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
            val api = MisskeyApi()
            val origin = server.url("/").toString().removeSuffix("/")

            val jsonFailure = assertThrows(ApiFailure::class.java) { runBlocking { api.get(origin, "first") } }
            val plainFailure = assertThrows(ApiFailure::class.java) { runBlocking { api.get(origin, "second") } }
            assertEquals(400, jsonFailure.status)
            assertEquals("Bad", jsonFailure.code)
            assertEquals(503, plainFailure.status)
            assertEquals(null, plainFailure.code)
        }
    }

    @Test
    fun cancellationCancelsTheInFlightCallAndDoesNotBecomeApiFailure() = runBlocking {
        MockWebServer().also { it.start() }.use { server ->
            server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
            val origin = server.url("/").toString().removeSuffix("/")
            val canceledCall = AtomicBoolean(false)
            val callFailure = AtomicReference<IOException?>(null)
            val client = OkHttpClient.Builder()
                .eventListenerFactory {
                    object : EventListener() {
                        override fun callFailed(call: Call, ioe: IOException) {
                            canceledCall.set(call.isCanceled())
                            callFailure.set(ioe)
                        }
                    }
                }
                .build()
            val request = async {
                MisskeyApi(client).get(origin, "slow")
            }
            var completionCause: Throwable? = null
            request.invokeOnCompletion { completionCause = it }
            yield()
            delay(100)
            assertNotNull(server.takeRequest(10, TimeUnit.SECONDS))
            request.cancelAndJoin()
            assertTrue(request.isCancelled)
            assertTrue(completionCause is CancellationException)
            // MisskeyApi creates its Call internally, so EventListener is the closest direct
            // proof available without changing production seams: OkHttp reports that Call as canceled.
            assertTrue(canceledCall.get())
            assertNotNull(callFailure.get())
            assertTrue(completionCause !is ApiFailure)
        }
    }

    @Test
    fun multipartFileBodyClosesItsApplicationStream() {
        val closed = ClosingInputStream("bytes".toByteArray())
        val body = MultipartFileBody("file", "file", "text/plain", closed).body()
        val sink = okio.Buffer()
        body.writeTo(sink)
        assertEquals("bytes", sink.readUtf8())
        assertTrue(closed.closed)
    }

    private class ClosingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false

        override fun close() {
            closed = true
            super.close()
        }
    }
}
