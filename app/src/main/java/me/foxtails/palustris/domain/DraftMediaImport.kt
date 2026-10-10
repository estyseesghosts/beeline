package me.foxtails.palustris.domain

/** Why a picked file did not become draft media. */
enum class DraftMediaImportError { NotAnImage, TooLarge, Unreadable }

/** Raised by the importer when a picked file is rejected. The composer maps [reason] to a message. */
class DraftMediaImportException(val reason: DraftMediaImportError, cause: Throwable? = null) :
    Exception(reason.name, cause)

/**
 * Image types the app may re-encode before upload. GIF is never re-encoded, and an animated PNG
 * or WebP is skipped when the file is prepared. Both the composer question and the preparer use this set.
 */
val COMPRESSIBLE_IMAGE_TYPES: Set<String> = setOf("image/jpeg", "image/png", "image/webp")
