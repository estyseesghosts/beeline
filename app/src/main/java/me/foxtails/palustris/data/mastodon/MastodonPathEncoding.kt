package me.foxtails.palustris.data.mastodon

import java.net.URLEncoder

/**
 * Encode one opaque Mastodon identifier as one adapter-owned REST URL path segment.
 *
 * This helper is a stateless pure function. It owns no state and has no lifetime.
 * URLEncoder writes a space as `+`. A path segment requires `%20`. The replacement keeps path meaning.
 * Query encoding is a different contract. Do not use this helper for query values.
 */
internal fun String.encodeMastodonPathSegment(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")
