package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.SourceError
import java.io.IOException

object MisskeyErrorMapper {
    fun map(error: ApiFailure): SourceError = when (error.code?.uppercase()) {
        "BTL_DISABLED" -> SourceError.AccessDenied("timeline:bubble")
        "NO_FREE_SPACE" -> SourceError.ResourceLimit("media.drive-full")
        "MAX_FILE_SIZE_EXCEEDED" -> SourceError.ResourceLimit("media.file-size")
        "UNALLOWED_FILE_TYPE" -> SourceError.Unsupported("media.file-type")
        "INAPPROPRIATE" -> SourceError.Unsupported("media.inappropriate")
        "COMMENTTOOLONG" -> SourceError.Unsupported("media.alt-text-length")
        "PERMISSION_DENIED" -> SourceError.AccessDenied("media.upload")
        else -> when (error.status) {
            413 -> SourceError.ResourceLimit("media.file-size")
            401, 403 -> SourceError.Unauthorized
            429 -> SourceError.RateLimited
            404 -> SourceError.Unsupported(error.code ?: "requested feature")
            else -> SourceError.ServerError(error.code ?: error.message)
        }
    }

    fun map(error: Exception): SourceError = when (error) {
        is SourceError -> error
        is ApiFailure -> map(error)
        is IOException -> SourceError.NetworkUnavailable
        else -> SourceError.ServerError(error.message)
    }
}
