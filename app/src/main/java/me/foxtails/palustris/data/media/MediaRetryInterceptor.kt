package me.foxtails.palustris.data.media

import java.io.IOException
import java.net.SocketTimeoutException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Retries a media request once after a timeout, a 5xx answer or a 429 answer. A 429 answer waits for its
 * `Retry-After` time, up to [maxWaitMillis]. Every other answer, including every other 4xx, passes through
 * untouched, so a missing file is never asked for twice. The retry lives in the loader, not in a composable.
 *
 * The wait blocks one OkHttp dispatcher thread. The cap keeps that short, and the call timeout of the client
 * still bounds the whole call.
 */
internal class MediaRetryInterceptor(
    private val maxWaitMillis: Long = DEFAULT_MAX_WAIT_MILLIS,
    private val sleep: (Long) -> Unit = Thread::sleep,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val first = try {
            chain.proceed(chain.request())
        } catch (_: SocketTimeoutException) {
            // A timeout is retried once. Any other I/O failure is not.
            return retry(chain, waitMillis = 0L)
        }
        if (!shouldRetry(first.code)) return first
        val wait = if (first.code == TOO_MANY_REQUESTS) retryAfterMillis(first) else 0L
        first.close()
        return retry(chain, wait)
    }

    private fun retry(chain: Interceptor.Chain, waitMillis: Long): Response {
        if (waitMillis > 0L) {
            try {
                sleep(waitMillis)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted while waiting to retry")
            }
        }
        return chain.proceed(chain.request())
    }

    private fun shouldRetry(code: Int): Boolean = code == TOO_MANY_REQUESTS || code in SERVER_ERRORS

    /** Reads `Retry-After` as seconds. A date, a bad value or no header waits one second. The cap applies to all. */
    private fun retryAfterMillis(response: Response): Long {
        val seconds = response.header("Retry-After")?.trim()?.toLongOrNull()
        val requested = if (seconds != null && seconds >= 0L) seconds * 1_000L else DEFAULT_WAIT_MILLIS
        return requested.coerceAtMost(maxWaitMillis)
    }

    private companion object {
        const val TOO_MANY_REQUESTS = 429
        val SERVER_ERRORS = 500..599
        const val DEFAULT_WAIT_MILLIS = 1_000L
        const val DEFAULT_MAX_WAIT_MILLIS = 3_000L
    }
}
