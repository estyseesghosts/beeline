package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.data.AppMessages
import me.foxtails.palustris.data.transport.HttpResponse
import me.foxtails.palustris.data.transport.AuthenticatedHttpClient
import me.foxtails.palustris.data.transport.HttpStatusFailure
import me.foxtails.palustris.data.transport.MultipartFileBody
import me.foxtails.palustris.data.transport.ResponseLimitExceeded
import me.foxtails.palustris.ProductIdentity
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.InputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okio.source

object ServerAddress {
    fun normalize(input: String, messages: AppMessages = AppMessages.Default): String {
        val value = input.trim()
        val url = (if ("://" in value) value else "https://$value").toHttpUrlOrNull()
        require(url != null && url.scheme == "https" && url.username.isEmpty() && url.password.isEmpty() &&
            url.encodedPath == "/" && url.query == null && url.fragment == null) {
            messages.instanceDomainInvalid()
        }
        return url.toString().removeSuffix("/")
    }
}

class ApiFailure(val status: Int, val code: String? = null, message: String = "") : IOException(message)


/** No redirects: an authenticated request must never forward its token to another host. */
class MisskeyApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS).build(),
    private val appMessages: AppMessages = AppMessages.Default,
) {
    private val transport = AuthenticatedHttpClient(client)

    /** Temporary bridge for MastodonAuth; slice 2A4 removes this auth constructor and bridge. */
    fun authenticatedClient(): AuthenticatedHttpClient =
        AuthenticatedHttpClient(client)
    suspend fun post(
        origin: String,
        endpoint: String,
        body: JSONObject = JSONObject(),
        maxResponseBytes: Long? = null,
    ): HttpResponse =
        execute(Request.Builder().url("$origin/api/$endpoint")
            .header("Accept", "application/json")
            .header("User-Agent", ProductIdentity.userAgent)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build(), maxResponseBytes)

    suspend fun postForm(
        origin: String,
        endpoint: String,
        fields: Map<String, String>,
        bearerToken: String? = null,
        maxResponseBytes: Long? = null,
    ): HttpResponse = postForm(origin, endpoint, fields.entries.map { it.key to it.value }, bearerToken, maxResponseBytes)

    suspend fun postForm(
        origin: String,
        endpoint: String,
        fields: List<Pair<String, String>>,
        bearerToken: String? = null,
        maxResponseBytes: Long? = null,
    ): HttpResponse = postForm(endpointUrl = "$origin/$endpoint", fields = fields, bearerToken = bearerToken, maxResponseBytes = maxResponseBytes)

    /** Uses a fully built URL so callers can keep opaque path/query values encoded safely. */
    suspend fun postForm(
        endpointUrl: HttpUrl,
        fields: List<Pair<String, String>>,
        bearerToken: String? = null,
        maxResponseBytes: Long? = null,
    ): HttpResponse = postForm(endpointUrl.toString(), fields, bearerToken, maxResponseBytes)

    private suspend fun postForm(
        endpointUrl: String,
        fields: List<Pair<String, String>>,
        bearerToken: String?,
        maxResponseBytes: Long?,
    ): HttpResponse =
        execute(Request.Builder().url(endpointUrl)
            .header("Accept", "application/json")
            .header("User-Agent", ProductIdentity.userAgent)
            .apply { bearerToken?.let { header("Authorization", "Bearer $it") } }
            .post(FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build())
            .build(), maxResponseBytes)

    suspend fun patchForm(
        origin: String,
        endpoint: String,
        fields: List<Pair<String, String>>,
        bearerToken: String? = null,
        maxResponseBytes: Long? = null,
    ): HttpResponse = execute(Request.Builder().url("$origin/$endpoint")
        .header("Accept", "application/json")
        .header("User-Agent", ProductIdentity.userAgent)
        .apply { bearerToken?.let { header("Authorization", "Bearer $it") } }
        .patch(FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build())
        .build(), maxResponseBytes)

    suspend fun putForm(
        origin: String,
        endpoint: String,
        fields: List<Pair<String, String>>,
        bearerToken: String? = null,
        maxResponseBytes: Long? = null,
    ): HttpResponse = execute(Request.Builder().url("$origin/$endpoint")
        .header("Accept", "application/json")
        .header("User-Agent", ProductIdentity.userAgent)
        .apply { bearerToken?.let { header("Authorization", "Bearer $it") } }
        .put(FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build())
        .build(), maxResponseBytes)

    suspend fun delete(
        origin: String,
        endpoint: String,
        bearerToken: String? = null,
        maxResponseBytes: Long? = null,
    ): HttpResponse = execute(Request.Builder().url("$origin/$endpoint")
        .header("Accept", "application/json")
        .header("User-Agent", ProductIdentity.userAgent)
        .apply { bearerToken?.let { header("Authorization", "Bearer $it") } }
        .delete()
        .build(), maxResponseBytes)

    suspend fun postMultipart(
        origin: String,
        endpoint: String,
        file: InputStream,
        mimeType: String,
        fileName: String = "upload",
        bearerToken: String? = null,
        maxResponseBytes: Long? = null,
    ): HttpResponse = execute(Request.Builder().url("$origin/$endpoint")
        .header("Accept", "application/json")
        .header("User-Agent", ProductIdentity.userAgent)
        .apply { bearerToken?.let { header("Authorization", "Bearer $it") } }
        .post(MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, file.readBytes().toRequestBody(mimeType.toMediaType()))
            .build())
        .build(), maxResponseBytes)

    /** PATCH multipart for avatar and header bytes; application-owned streams close after the request. */
    suspend fun patchMultipart(
        origin: String,
        endpoint: String,
        fields: List<Pair<String, String>> = emptyList(),
        files: List<MultipartFileBody> = emptyList(),
        bearerToken: String? = null,
    ): HttpResponse {
        return transport.patchMultipart(origin, endpoint, fields, files, bearerToken)
    }

    suspend fun get(
        origin: String,
        endpoint: String,
        bearerToken: String? = null,
        maxResponseBytes: Long? = null,
    ): HttpResponse =
        execute(Request.Builder().url("$origin/api/$endpoint")
            .header("Accept", "application/json")
            .header("User-Agent", ProductIdentity.userAgent)
            .apply { bearerToken?.let { header("Authorization", "Bearer $it") } }
            .get().build(), maxResponseBytes)

    suspend fun getUrl(url: String, bearerToken: String? = null, maxResponseBytes: Long? = null): HttpResponse =
        execute(Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("User-Agent", ProductIdentity.userAgent)
            .apply { bearerToken?.let { header("Authorization", "Bearer $it") } }
            .get().build(), maxResponseBytes)

    fun webSocket(origin: String, path: String, headers: Map<String, String> = emptyMap(), listener: WebSocketListener): WebSocket {
        val base = origin.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid stream origin")
        require(base.scheme == "https" && base.username.isEmpty() && base.password.isEmpty() && base.host.isNotBlank()) {
            "Stream origin must be HTTPS without credentials"
        }
        val url = base.newBuilder()
            // OkHttp's WebSocket factory upgrades an HTTPS request itself. HttpUrl only
            // accepts HTTP(S) schemes, so keeping HTTPS here also preserves validation.
            .scheme("https")
            .encodedPath(path)
            .query(null)
            .fragment(null)
            .build()
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        return client.newWebSocket(request, listener)
    }

    private suspend fun execute(request: Request, maxResponseBytes: Long? = null): HttpResponse = try {
        transport.execute(request, maxResponseBytes)
    } catch (failure: HttpStatusFailure) {
        val error = runCatching { JSONObject(failure.body).optJSONObject("error") }.getOrNull()
        val code = error?.optString("code")?.takeIf(String::isNotBlank)
        throw ApiFailure(failure.status, code, appMessages.serverRequestFailed(failure.status, code))
    }
}

fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
