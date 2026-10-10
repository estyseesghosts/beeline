package me.foxtails.palustris.data.transport

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.foxtails.palustris.ProductIdentity
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Executes authenticated requests for one connection with no stored credentials.
 *
 * Lifetime: the caller creates this wrapper for a source or authentication operation and
 * supplies a client from the application pool. The wrapper retains no bearer token.
 */
class AuthenticatedHttpClient(
    private val client: OkHttpClient,
    private val expectedOrigin: String? = null,
) {
    suspend fun post(origin: String, path: String, body: String = "{}", bearerToken: String? = null, maxResponseBytes: Long? = null): HttpResponse =
        execute(request(origin, path, bearerToken).post(body.toRequestBody("application/json; charset=utf-8".toMediaType())).build(), maxResponseBytes)

    suspend fun postForm(origin: String, path: String, fields: List<Pair<String, String>>, bearerToken: String? = null, maxResponseBytes: Long? = null, headers: Map<String, String> = emptyMap()): HttpResponse =
        execute(request(origin, path, bearerToken).apply { headers.forEach { (name, value) -> header(name, value) } }.post(FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build()).build(), maxResponseBytes)

    suspend fun postForm(origin: String, path: String, fields: Map<String, String>, bearerToken: String? = null, maxResponseBytes: Long? = null): HttpResponse =
        postForm(origin, path, fields.entries.map { it.key to it.value }, bearerToken, maxResponseBytes)

    suspend fun postForm(url: HttpUrl, fields: List<Pair<String, String>>, bearerToken: String? = null, maxResponseBytes: Long? = null): HttpResponse =
        execute(request(url, bearerToken).post(FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build()).build(), maxResponseBytes)

    suspend fun patchForm(origin: String, path: String, fields: List<Pair<String, String>>, bearerToken: String? = null, maxResponseBytes: Long? = null): HttpResponse =
        execute(request(origin, path, bearerToken).patch(FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build()).build(), maxResponseBytes)

    suspend fun putForm(origin: String, path: String, fields: List<Pair<String, String>>, bearerToken: String? = null, maxResponseBytes: Long? = null): HttpResponse =
        execute(request(origin, path, bearerToken).put(FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build()).build(), maxResponseBytes)

    suspend fun delete(origin: String, path: String, bearerToken: String? = null, maxResponseBytes: Long? = null): HttpResponse =
        execute(request(origin, path, bearerToken).delete().build(), maxResponseBytes)

    suspend fun get(origin: String, path: String, bearerToken: String? = null, maxResponseBytes: Long? = null): HttpResponse =
        execute(request(origin, path, bearerToken).get().build(), maxResponseBytes)

    /** Reads a complete URL only after checking that it belongs to the authenticated origin. */
    suspend fun getUrl(origin: String, url: String, bearerToken: String? = null, maxResponseBytes: Long? = null): HttpResponse {
        val target = url.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid URL")
        val base = origin.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid origin")
        require(target.scheme == base.scheme && target.host == base.host && target.port == base.port &&
            target.username.isEmpty() && target.password.isEmpty() && target.fragment == null) {
            "Authenticated URL must use the connection origin"
        }
        return execute(request(target, bearerToken).get().build(), maxResponseBytes)
    }

    suspend fun getUrl(url: String, bearerToken: String? = null, maxResponseBytes: Long? = null): HttpResponse {
        val origin = expectedOrigin ?: url.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}:${it.port}" }
            ?: throw IllegalArgumentException("Invalid URL")
        return getUrl(origin, url, bearerToken, maxResponseBytes)
    }

    fun webSocket(origin: String, path: String, headers: Map<String, String> = emptyMap(), listener: WebSocketListener): WebSocket {
        val base = origin.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid stream origin")
        require(base.scheme == "https" && base.username.isEmpty() && base.password.isEmpty() && base.host.isNotBlank()) {
            "Stream origin must be HTTPS without credentials"
        }
        val url = base.newBuilder().scheme("https").encodedPath(path).query(null).fragment(null).build()
        return client.newWebSocket(Request.Builder().url(url).apply { headers.forEach { (name, value) -> header(name, value) } }.build(), listener)
    }

    /**
     * Streams one caller-owned upload input as multipart/form-data without buffering it.
     *
     * Ownership transfers at entry: this call closes the input on every terminal path
     * (successful write, read failure, pre-write failure, HTTP failure, oversized
     * response, and cancellation). A retry needs a newly opened input because the
     * consumed stream cannot be replayed. Cancellation closes the input while OkHttp
     * cancels the call, so a read that unblocks on close ends as CancellationException.
     * Field name, filename, MIME type, bearer, path, User-Agent, and response cap
     * match the previous buffered behavior. Text [fields] precede the file part.
     */
    suspend fun postMultipart(origin: String, path: String, file: InputStream, mimeType: String, fileName: String = "upload", bearerToken: String? = null, maxResponseBytes: Long? = null, fields: List<Pair<String, String>> = emptyList()): HttpResponse {
        val owner = UploadStreamOwner(file)
        try {
            val multipart = uploadForm(fields, fileName, owner.body(mimeType))
            return execute(request(origin, path, bearerToken).post(OneShotRequestBody(multipart)).build(), maxResponseBytes)
        } finally {
            owner.release()
        }
    }

    /**
     * Streams each application-owned file and closes it when OkHttp finishes writing the request.
     *
     * Ownership of every supplied file transfers at entry: files already written close
     * through their own body write, and the entry releases every file on any terminal
     * path, so unwritten files also close exactly once. Cancellation closes all owned
     * inputs while OkHttp cancels the call.
     */
    suspend fun patchMultipart(origin: String, path: String, fields: List<Pair<String, String>> = emptyList(), files: List<MultipartFileBody> = emptyList(), bearerToken: String? = null): HttpResponse {
        try {
            val multipart = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                fields.forEach { (name, value) -> addFormDataPart(name, value) }
                files.forEach { part -> addFormDataPart(part.fieldName, part.fileName, part.body()) }
            }.build()
            // Only file-carrying bodies are one-shot. A field-only body stays replayable.
            val body: RequestBody = if (files.isEmpty()) multipart else OneShotRequestBody(multipart)
            return execute(request(origin, path, bearerToken).patch(body).build())
        } finally {
            files.forEach { it.release() }
        }
    }

    private fun uploadForm(fields: List<Pair<String, String>>, fileName: String, file: RequestBody): MultipartBody =
        MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            fields.forEach { (name, value) -> addFormDataPart(name, value) }
            addFormDataPart("file", fileName, file)
        }.build()

    private fun request(origin: String, path: String, token: String?): Request.Builder {
        val base = origin.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid origin")
        val target = base.resolve(if (path.startsWith("/")) path else "/$path")
            ?: throw IllegalArgumentException("Invalid URL")
        return request(target, token)
    }

    private fun request(url: HttpUrl, token: String?): Request.Builder = Request.Builder().url(url)
        .header("Accept", "application/json").header("User-Agent", ProductIdentity.userAgent)
        .apply { token?.let { header("Authorization", "Bearer $it") } }

    internal suspend fun execute(request: Request, maxResponseBytes: Long? = null): HttpResponse = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: Call, error: IOException) { if (!continuation.isCancelled) continuation.resumeWithException(error) }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            val text = it.body?.let { body -> readBody(body, maxResponseBytes) }.orEmpty()
                             if (!it.isSuccessful) throw HttpStatusFailure(it.code, text)
                            continuation.resume(HttpResponse(text, it.headers, it.code))
                        } catch (error: Exception) { if (!continuation.isCancelled) continuation.resumeWithException(error) }
                    }
                }
            })
        }
    }

    private fun readBody(body: ResponseBody, limit: Long?): String {
        if (limit == null) return body.string()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0L
        body.byteStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw ResponseLimitExceeded()
                output.write(buffer, 0, read)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }
}

/**
 * Describes one application-owned upload file. Passing this to an upload call transfers
 * ownership of its stream to that call, which closes it exactly once on every terminal path.
 */
class MultipartFileBody(val fieldName: String, val fileName: String?, private val mimeType: String, stream: InputStream) {
    private val owner = UploadStreamOwner(stream)

    /**
     * Builds the one-shot streaming body for this file. The body reports unknown length
     * and refuses a second write because the consumed input cannot be replayed. OkHttp
     * 4.12 does not propagate one-shot through the enclosing MultipartBody, so the
     * upload entry wraps the completed body in OneShotRequestBody and this
     * consume-once guard stops silent truncated replays.
     */
    internal fun body(): RequestBody = owner.body(mimeType)

    internal fun release() = owner.release()
}
