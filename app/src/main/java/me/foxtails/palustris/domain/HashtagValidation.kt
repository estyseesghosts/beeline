package me.foxtails.palustris.domain

import java.util.Locale

private val exactHashtagBody = Regex("[\\p{L}\\p{N}_](?:[\\p{L}\\p{N}\\p{M}_])*")

/** Removes every `#` and full-width `＃` from [text], wherever it stands. Hashtag input never keeps the mark. */
fun stripHashtagMarks(text: String): String = text.filterNot { it == '#' || it == '＃' }

/** Returns the accepted spelling, retaining its optional leading hash. */
fun validateExactHashtag(value: String): String {
    require(value.none(Char::isISOControl)) { "Enter one exact hashtag, such as #photography." }
    val trimmed = value.trim()
    val body = trimmed.removePrefix("#")
    require(body.isNotEmpty() && body == trimmed.removePrefix("#") && body.matches(exactHashtagBody)) {
        "Enter one exact hashtag, such as #photography."
    }
    return trimmed
}

fun isExactHashtag(value: String): Boolean = runCatching { validateExactHashtag(value) }.isSuccess

fun hashtagBody(value: String): String = validateExactHashtag(value).removePrefix("#")

fun hashtagIdentity(value: String): String = hashtagBody(value).lowercase(Locale.ROOT)
