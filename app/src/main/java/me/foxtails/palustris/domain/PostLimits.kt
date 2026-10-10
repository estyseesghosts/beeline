package me.foxtails.palustris.domain

/**
 * The text limits of the connected server that the composer enforces before it publishes.
 * A null limit means the server did not report one and the app does not enforce it.
 */
data class PostLimits(
    val maxPostLength: Int? = null,
    val posting: PostingCapabilities = PostingCapabilities(),
) {
    /**
     * Characters left for one post, or null without a limit. Negative means over the limit.
     * Mastodon counts the warning with the text. Misskey counts the text alone.
     */
    fun remaining(text: String, warning: String = ""): Int? =
        maxPostLength?.let { it - PostLengthCounter.count(posting, text, warning) }

    /** Characters left for the content warning when it has its own limit, else null. */
    fun warningRemaining(warning: String): Int? =
        posting.maxWarningLength?.let { limit -> PostLengthCounter.warningCount(posting, warning)?.let { limit - it } }

    /** True when the text or the separate warning exceeds its limit. */
    fun exceeds(text: String, warning: String = ""): Boolean =
        (remaining(text, warning) ?: 0) < 0 || (warningRemaining(warning) ?: 0) < 0
}
