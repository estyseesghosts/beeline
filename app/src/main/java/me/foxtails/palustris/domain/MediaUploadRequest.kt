package me.foxtails.palustris.domain

import java.io.InputStream

/**
 * One file to upload to the server's media store.
 *
 * Lifetime: one upload call. [open] must return a new stream each time it is called, because a
 * stream cannot be replayed and a retry opens its own. The upload call closes every stream it opens.
 */
data class MediaUploadRequest(
    val open: () -> InputStream,
    val mimeType: String,
    val fileName: String,
    val description: String?,
    val sensitive: Boolean,
)
