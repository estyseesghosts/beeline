package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.data.AppMessages
import me.foxtails.palustris.data.transport.AuthenticatedHttpClient
import me.foxtails.palustris.data.transport.HttpResponse
import me.foxtails.palustris.data.transport.HttpStatusFailure
import me.foxtails.palustris.data.transport.MultipartFileBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.InputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

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

/** Misskey adapter. It owns the `/api/` prefix and delegates HTTP execution to neutral transport. */
class MisskeyApi(
    client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS).build(),
    private val appMessages: AppMessages = AppMessages.Default,
) {
    private val transport = AuthenticatedHttpClient(client)

    suspend fun post(origin: String, endpoint: String, body: JSONObject = JSONObject(), maxResponseBytes: Long? = null): HttpResponse =
        execute { transport.post(origin, "api/$endpoint", body.toString(), maxResponseBytes = maxResponseBytes) }

    suspend fun postForm(origin: String, endpoint: String, fields: Map<String, String>, bearerToken: String? = null, maxResponseBytes: Long? = null): HttpResponse =
        postForm(origin, endpoint, fields.entries.map { it.key to it.value }, bearerToken, maxResponseBytes)

    suspend fun postForm(origin: String, endpoint: String, fields: List<Pair<String, String>>, bearerToken: String? = null, maxResponseBytes: Long? = null): HttpResponse =
        execute { transport.postForm(origin, endpoint, fields, bearerToken, maxResponseBytes) }

    suspend fun postForm(endpointUrl: HttpUrl, fields: List<Pair<String, String>>, bearerToken: String? = null, maxResponseBytes: Long? = null): HttpResponse =
        execute { transport.postForm(endpointUrl, fields, bearerToken, maxResponseBytes) }

    suspend fun patchForm(origin: String, endpoint: String, fields: List<Pair<String, String>>, bearerToken: String? = null, maxResponseBytes: Long? = null): HttpResponse =
        execute { transport.patchForm(origin, endpoint, fields, bearerToken, maxResponseBytes) }

    suspend fun putForm(origin: String, endpoint: String, fields: List<Pair<String, String>>, bearerToken: String? = null, maxResponseBytes: Long? = null): HttpResponse =
        execute { transport.putForm(origin, endpoint, fields, bearerToken, maxResponseBytes) }

    suspend fun delete(origin: String, endpoint: String, bearerToken: String? = null, maxResponseBytes: Long? = null): HttpResponse =
        execute { transport.delete(origin, endpoint, bearerToken, maxResponseBytes) }

    suspend fun postMultipart(origin: String, endpoint: String, file: InputStream, mimeType: String, fileName: String = "upload", bearerToken: String? = null, maxResponseBytes: Long? = null): HttpResponse =
        execute { transport.postMultipart(origin, endpoint, file, mimeType, fileName, bearerToken, maxResponseBytes) }

    suspend fun patchMultipart(origin: String, endpoint: String, fields: List<Pair<String, String>> = emptyList(), files: List<MultipartFileBody> = emptyList(), bearerToken: String? = null): HttpResponse =
        execute { transport.patchMultipart(origin, endpoint, fields, files, bearerToken) }

    suspend fun get(origin: String, endpoint: String, bearerToken: String? = null, maxResponseBytes: Long? = null): HttpResponse =
        execute { transport.get(origin, "api/$endpoint", bearerToken, maxResponseBytes) }

    suspend fun getUrl(url: String, bearerToken: String? = null, maxResponseBytes: Long? = null): HttpResponse =
        execute { transport.getUrl(url, bearerToken, maxResponseBytes) }

    fun webSocket(origin: String, path: String, headers: Map<String, String> = emptyMap(), listener: WebSocketListener): WebSocket =
        transport.webSocket(origin, path, headers, listener)

    private suspend fun execute(request: suspend () -> HttpResponse): HttpResponse = try {
        request()
    } catch (failure: HttpStatusFailure) {
        val error = runCatching { JSONObject(failure.body).optJSONObject("error") }.getOrNull()
        val code = error?.optString("code")?.takeIf(String::isNotBlank)
        throw ApiFailure(failure.status, code, appMessages.serverRequestFailed(failure.status, code))
    }
}

fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
