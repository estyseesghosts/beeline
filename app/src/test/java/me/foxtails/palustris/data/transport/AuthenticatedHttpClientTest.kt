package me.foxtails.palustris.data.transport

import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.EventListener
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.BufferedSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun explicitMastodonApiPathsRemainUnderApiPrefix() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{}"))
            server.enqueue(MockResponse().setBody("{}"))
            val origin = server.url("/").toString().removeSuffix("/")
            val client = AuthenticatedHttpClient(OkHttpClient())

            client.postForm(origin, "api/v1/apps", emptyMap())
            client.get(origin, "api/v2/instance")

            assertEquals("/api/v1/apps", server.takeRequest().path)
            assertEquals("/api/v2/instance", server.takeRequest().path)
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

    @Test
    fun generatedInputSendsExactBytesAndClosesOnce() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            val size = 64 * 1024
            val stream = GeneratedUploadStream(size)
            val origin = server.url("/").toString().removeSuffix("/")
            AuthenticatedHttpClient(OkHttpClient()).postMultipart(origin, "upload", stream, "text/plain", bearerToken = "secret")

            val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals("/upload", request.path)
            assertEquals("Bearer secret", request.getHeader("Authorization"))
            assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
            val sent = request.body.readByteArray()
            val sentText = String(sent, Charsets.UTF_8)
            assertTrue(sentText.contains("name=\"file\""))
            assertTrue(sentText.contains("filename=\"upload\""))
            assertTrue(sentText.contains("Content-Type: text/plain"))
            assertTrue(containsSequence(sent, expectedPattern(size)))
            assertEquals(size.toLong(), stream.payloadBytes)
            assertTrue("input rewound during upload", !stream.rewound)
            assertEquals(1, stream.closes)
        }
    }

    @Test
    fun networkWriteBeginsBeforeInputConsumption() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            val events = CopyOnWriteArrayList<String>()
            val tracking = object : InputStream() {
                private val bytes = "payload".toByteArray()
                private var position = 0
                private var first = true
                override fun read(): Int {
                    val single = ByteArray(1)
                    return if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xFF
                }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (first) {
                        first = false
                        events.add("streamRead")
                    }
                    if (position >= bytes.size) return -1
                    buffer[offset] = bytes[position++]
                    return 1
                }
            }
            val instrumented = OkHttpClient.Builder().addNetworkInterceptor { chain ->
                val original = chain.request().body!!
                val wrapped = object : RequestBody() {
                    override fun contentType() = original.contentType()
                    override fun contentLength() = original.contentLength()
                    override fun isOneShot() = original.isOneShot()
                    override fun writeTo(sink: BufferedSink) {
                        events.add("networkWrite")
                        original.writeTo(sink)
                    }
                }
                chain.proceed(chain.request().newBuilder().method(chain.request().method, wrapped).build())
            }.build()
            val origin = server.url("/").toString().removeSuffix("/")
            AuthenticatedHttpClient(instrumented).postMultipart(origin, "upload", tracking, "text/plain")

            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertTrue(events.contains("networkWrite"))
            assertTrue(events.contains("streamRead"))
            assertTrue("input was fully consumed before network writing began", events.indexOf("networkWrite") < events.indexOf("streamRead"))
        }
    }

    @Test
    fun streamBodyLengthIsUnknownWithoutConsumingInput() {
        val stream = GeneratedUploadStream(16)
        val part = MultipartFileBody("file", "file.txt", "text/plain", stream).body()
        assertEquals(-1L, part.contentLength())
        assertEquals(0, stream.reads)
        val enclosing = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "file.txt", part).build()
        assertEquals(-1L, enclosing.contentLength())
        assertEquals(0, stream.reads)
    }

    @Test
    fun streamBodyIsOneShotAndRejectsSecondWrite() {
        val stream = GeneratedUploadStream(5)
        val body = MultipartFileBody("file", "file.txt", "text/plain", stream).body()
        assertTrue(body.isOneShot())
        val first = okio.Buffer()
        body.writeTo(first)
        assertTrue(first.readByteArray().contentEquals(expectedPattern(5)))
        val second = okio.Buffer()
        assertThrows(IOException::class.java) { body.writeTo(second) }
        assertEquals(0, second.size)
        assertEquals(1, stream.closes)
    }

    @Test
    fun enclosingMultipartReportsOkHttpOneShotState() {
        // OkHttp 4.12 does not propagate one-shot through MultipartBody. The upload
        // entry therefore wraps the completed body so the effective request body
        // reports the honest one-shot state. A fixed boundary keeps the wire
        // comparison deterministic.
        val boundary = "r11-test-boundary"
        fun filePart() = MultipartFileBody("file", "upload", "text/plain", ByteArrayInputStream("x".toByteArray()))
        fun fileMultipart() = MultipartBody.Builder(boundary).setType(MultipartBody.FORM)
            .addFormDataPart("file", "upload", filePart().body()).build()
        assertTrue(filePart().body().isOneShot())
        val raw = fileMultipart()
        assertFalse(raw.isOneShot())
        val effective = OneShotRequestBody(fileMultipart())
        assertTrue(effective.isOneShot())
        assertEquals(raw.contentType(), effective.contentType())
        assertEquals(raw.contentLength(), effective.contentLength())
        val rawBytes = okio.Buffer().also { raw.writeTo(it) }.readByteArray()
        val effectiveBytes = okio.Buffer().also { effective.writeTo(it) }.readByteArray()
        assertTrue(effectiveBytes.contentEquals(rawBytes))
        val patchPart = MultipartFileBody("header", "header.png", "image/png", ByteArrayInputStream("y".toByteArray()))
        val patch = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(patchPart.fieldName, patchPart.fileName, patchPart.body()).build()
        assertFalse(patch.isOneShot())
        assertTrue(OneShotRequestBody(patch).isOneShot())
    }

    @Test
    fun requestBodiesExposeEffectiveOneShotState() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            server.enqueue(MockResponse().setBody("ok"))
            server.enqueue(MockResponse().setBody("ok"))
            val seen = CopyOnWriteArrayList<Boolean>()
            val capturing = OkHttpClient.Builder().addNetworkInterceptor { chain ->
                seen.add(chain.request().body!!.isOneShot())
                chain.proceed(chain.request())
            }.build()
            val origin = server.url("/").toString().removeSuffix("/")
            val client = AuthenticatedHttpClient(capturing)
            client.postMultipart(origin, "upload", GeneratedUploadStream(16), "text/plain")
            client.patchMultipart(origin, "credentials", files = listOf(MultipartFileBody("header", "header.png", "image/png", GeneratedUploadStream(16))))
            client.patchMultipart(origin, "credentials", fields = listOf("name" to "value"))
            assertEquals(listOf(true, true, false), seen)
        }
    }

    @Test
    fun connectionFailureBeforeWriteClosesInputWithoutConsumption() = runBlocking {
        val server = MockWebServer()
        server.start()
        val origin = server.url("/").toString().removeSuffix("/")
        server.shutdown()
        val stream = GeneratedUploadStream(1024)
        val failure = runCatching {
            AuthenticatedHttpClient(OkHttpClient()).postMultipart(origin, "upload", stream, "text/plain")
        }.exceptionOrNull()
        assertTrue("Expected IOException, got $failure", failure is IOException)
        assertEquals(0, stream.payloadBytes)
        assertEquals(1, stream.closes)
    }

    @Test
    fun invalidMimeTypeClosesInputBeforeNetwork() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val stream = GeneratedUploadStream(16)
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { AuthenticatedHttpClient(OkHttpClient()).postMultipart(origin, "upload", stream, "not a mime") }
            }
            assertEquals(0, server.requestCount)
            assertEquals(1, stream.closes)
        }
    }

    @Test
    fun invalidOriginClosesInputBeforeNetwork() = runBlocking {
        val stream = GeneratedUploadStream(16)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { AuthenticatedHttpClient(OkHttpClient()).postMultipart("not a url", "upload", stream, "text/plain") }
        }
        assertEquals(1, stream.closes)
    }

    @Test
    fun inputReadFailureClosesInputAndPropagates() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            val origin = server.url("/").toString().removeSuffix("/")
            val stream = FailingReadStream()
            val failure = runCatching {
                AuthenticatedHttpClient(OkHttpClient()).postMultipart(origin, "upload", stream, "text/plain")
            }.exceptionOrNull()
            assertTrue("Expected IOException, got $failure", failure is IOException)
            assertEquals(1, stream.closes)
        }
    }

    @Test
    fun cancellationBeforeNetworkWriteClosesInputWithoutConsumption() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            val gate = CountDownLatch(1)
            val entered = CountDownLatch(1)
            val blocked = OkHttpClient.Builder().addInterceptor { chain ->
                entered.countDown()
                gate.await(10, TimeUnit.SECONDS)
                chain.proceed(chain.request())
            }.build()
            val origin = server.url("/").toString().removeSuffix("/")
            val stream = GeneratedUploadStream(128)
            val upload = async { AuthenticatedHttpClient(blocked).postMultipart(origin, "upload", stream, "text/plain") }
            try {
                withTimeout(5_000) { while (entered.count != 0L) delay(10) }
                upload.cancel()
            } finally {
                gate.countDown()
            }
            val failure = runCatching { upload.await() }.exceptionOrNull()
            assertTrue("Expected CancellationException, got $failure", failure is CancellationException)
            assertEquals(0, stream.payloadBytes)
            assertEquals(0, stream.reads)
            assertEquals(1, stream.closes)
        }
    }

    @Test
    fun cancellationDuringWriteClosesInputAndStaysCancellation() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val origin = server.url("/").toString().removeSuffix("/")
            val stream = GatedUploadStream()
            val upload = async { AuthenticatedHttpClient(OkHttpClient()).postMultipart(origin, "upload", stream, "text/plain") }
            withTimeout(5_000) { while (stream.readEntered.count != 0L) delay(10) }
            upload.cancel()
            val failure = runCatching { upload.await() }.exceptionOrNull()
            assertTrue("Expected CancellationException, got $failure", failure is CancellationException)
            assertEquals(1, stream.closes)
        }
    }

    @Test
    fun failedUploadNeverReplaysConsumedInput() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok").setSocketPolicy(SocketPolicy.DISCONNECT_DURING_REQUEST_BODY))
            val origin = server.url("/").toString().removeSuffix("/")
            val stream = GeneratedUploadStream(256 * 1024)
            val failure = runCatching {
                AuthenticatedHttpClient(OkHttpClient()).postMultipart(origin, "upload", stream, "text/plain")
            }.exceptionOrNull()
            assertTrue("Expected IOException, got $failure", failure is IOException)
            assertEquals(1, stream.closes)
            assertTrue("input rewound after failure", !stream.rewound)
            assertTrue(stream.payloadBytes <= 256 * 1024)
        }
    }

    @Test
    fun httpStatusFailureClosesInput() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(400).setBody("{\"error\":true}"))
            val origin = server.url("/").toString().removeSuffix("/")
            val stream = GeneratedUploadStream(64)
            val failure = runCatching {
                AuthenticatedHttpClient(OkHttpClient()).postMultipart(origin, "upload", stream, "text/plain")
            }.exceptionOrNull()
            assertTrue("Expected HttpStatusFailure, got $failure", failure is HttpStatusFailure)
            assertEquals(400, (failure as HttpStatusFailure).status)
            assertEquals(1, stream.closes)
        }
    }

    @Test
    fun oversizedResponseClosesInput() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("0123456789"))
            val origin = server.url("/").toString().removeSuffix("/")
            val stream = GeneratedUploadStream(64)
            assertThrows(ResponseLimitExceeded::class.java) {
                runBlocking { AuthenticatedHttpClient(OkHttpClient()).postMultipart(origin, "upload", stream, "text/plain", maxResponseBytes = 4) }
            }
            assertEquals(1, stream.closes)
        }
    }

    @Test
    fun patchPreWriteFailureClosesAllInputsUnread() = runBlocking {
        val first = GeneratedUploadStream(32)
        val second = GeneratedUploadStream(48)
        val failure = runCatching {
            AuthenticatedHttpClient(OkHttpClient()).patchMultipart(
                origin = "not a url",
                path = "credentials",
                files = listOf(
                    MultipartFileBody("first", "first.txt", "text/plain", first),
                    MultipartFileBody("second", "second.txt", "text/plain", second),
                ),
            )
        }.exceptionOrNull()
        assertTrue("Expected IllegalArgumentException, got $failure", failure is IllegalArgumentException)
        assertEquals(0, first.reads)
        assertEquals(0, second.reads)
        assertEquals(0, first.payloadBytes)
        assertEquals(0, second.payloadBytes)
        assertEquals(1, first.closes)
        assertEquals(1, second.closes)
    }

    @Test
    fun patchFailureClosesWrittenAndUnwrittenFiles() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            server.enqueue(MockResponse().setBody("ok"))
            val origin = server.url("/").toString().removeSuffix("/")
            val written = GeneratedUploadStream(32)
            val broken = FailingReadStream()
            val failure = runCatching {
                AuthenticatedHttpClient(OkHttpClient()).patchMultipart(
                    origin = origin,
                    path = "credentials",
                    files = listOf(
                        MultipartFileBody("first", "first.txt", "text/plain", written),
                        MultipartFileBody("second", "second.txt", "text/plain", broken),
                    ),
                )
            }.exceptionOrNull()
            assertTrue("Expected IOException, got $failure", failure is IOException)
            assertEquals(1, written.closes)
            assertEquals(1, broken.closes)

            val unwritten = GeneratedUploadStream(32)
            val earlyBroken = FailingReadStream()
            val earlyFailure = runCatching {
                AuthenticatedHttpClient(OkHttpClient()).patchMultipart(
                    origin = origin,
                    path = "credentials",
                    files = listOf(
                        MultipartFileBody("first", "first.txt", "text/plain", earlyBroken),
                        MultipartFileBody("second", "second.txt", "text/plain", unwritten),
                    ),
                )
            }.exceptionOrNull()
            assertTrue("Expected IOException, got $earlyFailure", earlyFailure is IOException)
            assertEquals(1, earlyBroken.closes)
            assertEquals(1, unwritten.closes)
        }
    }

    @Test
    fun patchSuccessClosesInputExactlyOnce() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("ok"))
            val origin = server.url("/").toString().removeSuffix("/")
            val stream = GeneratedUploadStream(48)
            AuthenticatedHttpClient(OkHttpClient()).patchMultipart(
                origin = origin,
                path = "credentials",
                files = listOf(MultipartFileBody("header", "header.png", "image/png", stream)),
                bearerToken = "secret",
            )
            val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals("PATCH", request.method)
            assertTrue(request.body.readByteArray().let { containsSequence(it, expectedPattern(48)) })
            assertEquals(1, stream.closes)
        }
    }

    private fun expectedPattern(size: Int): ByteArray = ByteArray(size) { index -> (index % 251).toByte() }

    private fun containsSequence(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty()) return true
        outer@ for (start in 0..haystack.size - needle.size) {
            for (offset in needle.indices) {
                if (haystack[start + offset] != needle[offset]) continue@outer
            }
            return true
        }
        return false
    }

    private class GeneratedUploadStream(private val size: Int) : InputStream() {
        var payloadBytes = 0L
            private set
        var reads = 0
            private set
        var closes = 0
            private set
        private var position = 0
        private var maxPosition = 0
        private var rewinds = 0
        val rewound: Boolean get() = rewinds > 0

        override fun read(): Int {
            val single = ByteArray(1)
            return if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (closes > 0) throw IOException("Read after close")
            if (position >= size) return -1
            reads++
            if (position < maxPosition) rewinds++
            val count = minOf(length, size - position)
            for (index in 0 until count) {
                buffer[offset + index] = (position % 251).toByte()
                position++
            }
            payloadBytes += count
            maxPosition = maxOf(maxPosition, position)
            return count
        }

        override fun available(): Int = Int.MAX_VALUE

        override fun close() {
            closes++
        }
    }

    private class FailingReadStream : InputStream() {
        var closes = 0
            private set

        override fun read(): Int = throw IOException("synthetic read failure")

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw IOException("synthetic read failure")

        override fun close() {
            closes++
        }
    }

    private class GatedUploadStream : InputStream() {
        private val lock = Object()
        val readEntered = CountDownLatch(1)
        var closes = 0
            private set
        private var closed = false
        private var position = 0

        override fun read(): Int {
            val single = ByteArray(1)
            return if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            readEntered.countDown()
            synchronized(lock) {
                while (!closed) lock.wait()
            }
            if (position >= 8) return -1
            buffer[offset] = position.toByte()
            position++
            return 1
        }

        override fun close() {
            synchronized(lock) {
                closes++
                closed = true
                lock.notifyAll()
            }
        }
    }
}
