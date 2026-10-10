package me.foxtails.palustris.ui.composer

import me.foxtails.palustris.ui.posts.HASHTAG_TOKEN
import me.foxtails.palustris.ui.posts.isHashtagBoundary

/**
 * The hashtag under the cursor of a composer field. [start] is the index of `#` and [end] is the
 * exclusive end of the whole word, so a replacement covers text after the cursor as well.
 * [fragment] is the text between `#` and the cursor. Only the fragment goes to the server.
 */
internal data class HashtagToken(val start: Int, val end: Int, val fragment: String)

/** A custom emoji shortcode directly before `#`, such as `:blob:#cat`. */
private val EMOJI_SHORTCODE_BEFORE = Regex(":\\w+:\\z")
private const val SHORTCODE_LOOKBEHIND = 64

/**
 * Finds the hashtag token that contains [cursor], or null. It follows the boundary rules of
 * `PostTextPresentation`: `#` starts a token after whitespace, formatting, an approved separator,
 * an emoji, or the start of the text, and the token must end at one of the same boundaries. At
 * least one character must sit between `#` and the cursor.
 */
internal fun hashtagTokenAt(text: String, cursor: Int): HashtagToken? {
    if (cursor !in 2..text.length) return null
    var bodyStart = cursor
    while (bodyStart > 0) {
        val codePoint = text.codePointBefore(bodyStart)
        if (!isTagCharacter(codePoint)) break
        bodyStart -= Character.charCount(codePoint)
    }
    val hash = bodyStart - 1
    if (hash < 0 || text[hash] != '#' || hash + 1 >= cursor) return null
    val match = HASHTAG_TOKEN.matchAt(text, hash) ?: return null
    val end = match.range.last + 1
    if (end < cursor || !boundaryBefore(text, hash) || !boundaryAfter(text, end)) return null
    return HashtagToken(hash, end, text.substring(hash + 1, cursor))
}

private fun isTagCharacter(codePoint: Int): Boolean =
    Character.isLetterOrDigit(codePoint) || codePoint == '_'.code || Character.getType(codePoint).let {
        it == Character.NON_SPACING_MARK.toInt() ||
            it == Character.COMBINING_SPACING_MARK.toInt() ||
            it == Character.ENCLOSING_MARK.toInt()
    }

private fun boundaryBefore(text: String, hash: Int): Boolean {
    if (hash == 0) return true
    if (isHashtagBoundary(text.codePointBefore(hash))) return true
    return EMOJI_SHORTCODE_BEFORE.containsMatchIn(text.substring(maxOf(0, hash - SHORTCODE_LOOKBEHIND), hash))
}

private fun boundaryAfter(text: String, end: Int): Boolean = end >= text.length || isHashtagBoundary(text.codePointAt(end))
