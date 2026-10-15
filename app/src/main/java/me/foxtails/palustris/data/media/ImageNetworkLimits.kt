package me.foxtails.palustris.data.media

import okhttp3.Dispatcher

/**
 * Shared concurrency policy for credential-free remote image fetching.
 *
 * Responsibility: bound how many image requests run at once, in total and per host.
 * Lifetime: stateless. Each image client owns its dispatcher for the client lifetime.
 * Retention: none. No cache, no queue, no account state.
 */
object ImageNetworkLimits {
    const val MAX_REQUESTS = 16
    const val MAX_REQUESTS_PER_HOST = 4
    const val MAX_JITTER_MILLIS = 250L

    fun newDispatcher(): Dispatcher = Dispatcher().apply {
        maxRequests = MAX_REQUESTS
        maxRequestsPerHost = MAX_REQUESTS_PER_HOST
    }
}
