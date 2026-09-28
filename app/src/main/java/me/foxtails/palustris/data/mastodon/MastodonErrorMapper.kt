package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.data.transport.HttpStatusFailure
import me.foxtails.palustris.domain.SourceError
import org.json.JSONObject
import java.io.IOException

object MastodonErrorMapper {
    fun map(status: Int, body: String? = null): SourceError = when (status) {
        401, 403 -> SourceError.Unauthorized
        429 -> SourceError.RateLimited
        404 -> SourceError.Unsupported("requested feature")
        else -> SourceError.ServerError(errorDetail(body))
    }

    fun map(error: Exception): SourceError = when (error) {
        is SourceError -> error
        is HttpStatusFailure -> map(error.status, error.body)
        is IOException -> SourceError.NetworkUnavailable
        else -> SourceError.ServerError(error.message)
    }

    /** Keeps authentication 404s diagnostic instead of treating a broken auth path as unsupported. */
    fun map(error: Exception, stage: String): SourceError = when (error) {
        is HttpStatusFailure -> if (error.status == 404) {
            SourceError.ServerError(errorDetail(error.body) ?: "$stage returned 404")
        } else {
            map(error)
        }
        else -> map(error)
    }

    private fun errorDetail(body: String?): String? = body?.let {
        runCatching {
            val json = JSONObject(it)
            json.optString("error").takeIf(String::isNotBlank)
                ?: json.optString("error_description").takeIf(String::isNotBlank)
        }.getOrNull()
    }
}
