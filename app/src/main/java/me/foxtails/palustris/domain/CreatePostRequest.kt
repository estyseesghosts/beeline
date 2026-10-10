package me.foxtails.palustris.domain

/**
 * A poll to create. [expiresInSeconds] is relative so a saved draft stays valid; null lets the
 * adapter pick the server default. [hideTotals] is honoured by Mastodon only.
 */
data class PollRequest(
    val choices: List<String>,
    val multiple: Boolean = false,
    val expiresInSeconds: Long? = null,
    val hideTotals: Boolean = false,
) {
    /** The poll as it is sent: choices trimmed, blank ones dropped. */
    fun cleaned(): PollRequest = copy(choices = choices.map(String::trim).filter(String::isNotEmpty))

    /** Why the server would refuse this poll, or null when it would take it. */
    fun problem(limits: PollLimits?): PollProblem? {
        if (limits == null) return PollProblem.Unsupported
        val shown = cleaned().choices
        return when {
            shown.size < PollLimits.MIN_OPTIONS -> PollProblem.TooFewOptions
            shown.size > limits.maxOptions -> PollProblem.TooManyOptions
            shown.any { it.length > limits.maxOptionLength } -> PollProblem.OptionTooLong
            shown.map { it.lowercase() }.toSet().size != shown.size -> PollProblem.DuplicateOptions
            expiresInSeconds != null && expiresInSeconds < limits.minExpirySeconds -> PollProblem.ExpiryOutOfRange
            expiresInSeconds != null && limits.maxExpirySeconds != null && expiresInSeconds > limits.maxExpirySeconds -> PollProblem.ExpiryOutOfRange
            else -> null
        }
    }
}

enum class PollProblem { Unsupported, TooFewOptions, TooManyOptions, OptionTooLong, DuplicateOptions, ExpiryOutOfRange }

data class CreatePostRequest(
    val text: String,
    val audience: Audience = Audience.Public,
    val contentWarning: String? = null,
    val replyTo: EntityId? = null,
    val attachments: List<Attachment> = emptyList(),
    val poll: PollRequest? = null,
    val quoteOf: EntityId? = null,
    /** Lets a server drop a duplicate of the same publish. Only servers that support it use it. */
    val idempotencyKey: String? = null,
)
