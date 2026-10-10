package me.foxtails.palustris.domain

import java.text.BreakIterator

/**
 * Counts post text the way the server will, so the composer rejects a post before it is sent.
 *
 * The counter is protocol-neutral: [PostLengthRule] selects the behavior. The Mastodon rule is a
 * client approximation of the server validator. The server's own error stays the final check.
 */
object PostLengthCounter {
    private val urlPattern = Regex("""https?://[^\s]*[^\s.,;:!?)\]'"]""")
    private val remoteMentionPattern = Regex("""(?<![\w=/])@(\w+)@[\w.\-]*\w""")

    /** Returns the length that counts against [PostingCapabilities.lengthRule]'s post limit. */
    fun count(posting: PostingCapabilities, text: String, warning: String = ""): Int = when (posting.lengthRule) {
        PostLengthRule.Utf16TextOnly -> text.length
        PostLengthRule.MastodonCombined ->
            graphemeCount(countableText(warning + text, posting.charactersReservedPerUrl))
    }

    /** Returns the content warning length against its own limit, or null when it shares the post limit. */
    fun warningCount(posting: PostingCapabilities, warning: String): Int? =
        if (posting.maxWarningLength == null) null else warning.length

    private fun countableText(text: String, reservedPerUrl: Int): String =
        text.replace(urlPattern, "x".repeat(reservedPerUrl))
            .replace(remoteMentionPattern) { "@${it.groupValues[1]}" }

    private fun graphemeCount(text: String): Int {
        if (text.isEmpty()) return 0
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        var count = 0
        while (iterator.next() != BreakIterator.DONE) count++
        return count
    }
}
