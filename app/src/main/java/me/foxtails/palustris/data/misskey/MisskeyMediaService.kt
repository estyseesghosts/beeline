package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.MediaUploadRequest
import org.json.JSONObject

/**
 * Uploads one file to the account's Misskey drive.
 *
 * Responsibility: the `drive/files/create` form, which needs the token as a form field.
 * Lifetime: the source that owns the session creates it and holds it as long as the source lives.
 * It keeps no per-upload state.
 */
internal class MisskeyMediaService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
) {
    suspend fun upload(request: MediaUploadRequest): Attachment {
        val fields = buildList {
            add("i" to token)
            add("name" to request.fileName)
            request.description?.takeIf(String::isNotBlank)?.let { add("comment" to it) }
            add("isSensitive" to request.sensitive.toString())
            add("force" to "true")
        }
        val body = api.postMultipart(
            origin,
            "drive/files/create",
            request.open(),
            request.mimeType,
            request.fileName,
            maxResponseBytes = MISSKEY_MAX_RESPONSE_BYTES,
            fields = fields,
        ).body
        return MisskeyMapper.driveFile(JSONObject(body))
    }
}
