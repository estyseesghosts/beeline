package me.foxtails.palustris.domain

/**
 * Choices that belong to a whole thread and ride with its first post: keeping it on the author's
 * server, who may quote it, and when to send it. Each shows in the composer only when the server
 * offers it.
 */
data class PostOptions(
    val localOnly: Boolean = false,
    val quotePolicy: QuoteApprovalPolicy? = null,
    /** When to publish, in epoch milliseconds, or null to publish now. */
    val scheduledAtEpochMillis: Long? = null,
) {
    val isDefault: Boolean get() = this == PostOptions()
}
