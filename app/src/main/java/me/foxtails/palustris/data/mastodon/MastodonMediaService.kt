package me.foxtails.palustris.data.mastodon

import kotlinx.coroutines.delay
import me.foxtails.palustris.data.transport.AuthenticatedHttpClient
import me.foxtails.palustris.data.transport.HttpStatusFailure
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.MediaUploadRequest
import me.foxtails.palustris.domain.SourceError
import org.json.JSONObject

/**
 * Uploads one media file to a Mastodon-compatible server.
 *
 * Responsibility: the v2 upload, the wait for background processing, and the v1 fallback.
 * Lifetime: the source that owns the session creates it and holds it as long as the source lives.
 * It keeps no per-upload state, so concurrent uploads do not share anything.
 */
internal class MastodonMediaService(
    private val origin: String,
    private val token: String,
    private val api: AuthenticatedHttpClient,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun upload(request: MediaUploadRequest): Attachment {
        val response = try {
            send("api/v2/media", request)
        } catch (error: HttpStatusFailure) {
            when (error.status) {
                // A server without the v2 route only offers the deprecated v1 route, which waits for processing.
                404 -> return parse(send("api/v1/media", request).body)
                413 -> throw SourceError.ResourceLimit("media.file-size")
                else -> throw error
            }
        }
        val attachment = parse(response.body)
        if (response.status != STATUS_ACCEPTED) return attachment
        val id = attachment.id ?: throw SourceError.ServerError("media upload returned no id")
        return awaitProcessing(id)
    }

    private suspend fun send(path: String, request: MediaUploadRequest) = api.postMultipart(
        origin,
        path,
        request.open(),
        request.mimeType,
        request.fileName,
        bearerToken = token,
        maxResponseBytes = MASTODON_MAX_RESPONSE_BYTES,
        fields = listOfNotNull(request.description?.takeIf(String::isNotBlank)?.let { "description" to it }),
    )

    /**
     * Polls until processing ends. The server answers 206 while it works, 200 when the media is
     * ready, and 422 when processing failed. The wait starts at one second and backs off.
     */
    private suspend fun awaitProcessing(id: String): Attachment {
        var waited = 0L
        var step = FIRST_POLL_MILLIS
        while (waited < MAX_WAIT_MILLIS) {
            pause(step)
            waited += step
            step = minOf(step * 2, MAX_POLL_MILLIS)
            // A 422 means processing failed; it surfaces as the server's own error.
            val response = api.get(origin, "api/v1/media/${id.encodeMastodonPathSegment()}", token, MASTODON_MAX_RESPONSE_BYTES)
            if (response.status != STATUS_PARTIAL) return parse(response.body)
        }
        throw SourceError.ServerError("media processing timed out")
    }

    private fun parse(body: String): Attachment = MastodonMapper.attachment(JSONObject(body))

    /**
     * Deletes unattached media. Servers before 4.4 have no route and answer 404, which the
     * publisher treats as best-effort cleanup and ignores.
     */
    suspend fun delete(id: String) {
        api.delete(origin, "api/v1/media/${id.encodeMastodonPathSegment()}", token, MASTODON_MAX_RESPONSE_BYTES)
        Unit
    }

    private companion object {
        const val STATUS_ACCEPTED = 202
        const val STATUS_PARTIAL = 206
        const val FIRST_POLL_MILLIS = 1_000L
        const val MAX_POLL_MILLIS = 5_000L
        const val MAX_WAIT_MILLIS = 60_000L
    }
}
