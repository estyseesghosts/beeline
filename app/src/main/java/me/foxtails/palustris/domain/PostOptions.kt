package me.foxtails.palustris.domain

/**
 * Choices that belong to a whole thread and ride with its first post: keeping it on the author's
 * server, who may quote it, when to send it, and which post format to send. Each shows in the
 * composer only when the server offers it. A null format omits `content_type` everywhere.
 */
data class PostOptions(
    val localOnly: Boolean = false,
    val quotePolicy: QuoteApprovalPolicy? = null,
    /** When to publish, in epoch milliseconds, or null to publish now. */
    val scheduledAtEpochMillis: Long? = null,
    /** Post format such as `text/markdown`, or null to omit it. */
    val contentType: String? = null,
) {
    val isDefault: Boolean get() = this == PostOptions()
}
