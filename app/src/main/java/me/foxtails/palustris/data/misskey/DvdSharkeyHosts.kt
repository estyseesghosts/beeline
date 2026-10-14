package me.foxtails.palustris.data.misskey

import java.net.URI

/**
 * Single owner for the DVD Sharkey host gate. Both the capability probe and the recommendations
 * feed service match through [isDvdSharkeyHost] so the `dvd.chat` string lives in exactly one
 * place. A second fork adds its own host or endpoint signature beside this helper.
 * Responsibility: normalize connection hosts and match the DVD gate. Lifetime: stateless, none.
 */
internal const val DVD_SHARKEY_HOST = "dvd.chat"

/** True when [origin] addresses the DVD Sharkey host, compared case-insensitively. Unparseable origins never match. */
internal fun isDvdSharkeyHost(origin: String): Boolean {
    val host = runCatching { URI(origin).host }.getOrNull()
    return host.equals(DVD_SHARKEY_HOST, ignoreCase = true)
}
