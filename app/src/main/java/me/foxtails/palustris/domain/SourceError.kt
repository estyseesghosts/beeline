package me.foxtails.palustris.domain

sealed class SourceError : Exception() {
    object Unauthorized : SourceError()
    object AccountMismatch : SourceError()
    object RateLimited : SourceError()
    data class Unsupported(val feature: String) : SourceError()
    data class AccessDenied(val feature: String) : SourceError()
    data class UnsupportedCredential(val feature: String) : SourceError()
    data class ServerUnsupported(val feature: String) : SourceError()
    data class ResourceLimit(val feature: String) : SourceError()
    data class ForeignOrigin(val feature: String) : SourceError()
    object NetworkUnavailable : SourceError()
    data class ServerError(val detail: String?) : SourceError()

    /** The server refused the value the user entered, such as an invalid expression or a list over its limit. */
    data class InvalidInput(val feature: String) : SourceError()
}
