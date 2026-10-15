package me.foxtails.palustris.data.media

import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Instant
import java.time.format.DateTimeFormatter
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Retries a media request once after a timeout, a 5xx answer or a 429 answer. A 429 answer waits for its
 * `Retry-After` time, up to [maxWaitMillis]. Seconds and HTTP-dates are honored. Any unparseable, missing,
 * negative, or past value waits one second. Every other answer, including every other 4xx, passes through
 * untouched, so a missing file is never asked for twice. The retry lives in the loader, not in a composable.
 *
 * The wait blocks one OkHttp dispatcher thread. The cap keeps that short, and the call timeout of the client
 * still bounds the whole call. [jitterMillis] adds a small production skew, up to
 * [ImageNetworkLimits.MAX_JITTER_MILLIS], so a grid of failed tiles does not retry as one burst.
 * Tests use the zero default for deterministic waits.
 */
internal class MediaRetryInterceptor(
    private val maxWaitMillis: Long = DEFAULT_MAX_WAIT_MILLIS,
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val jitterMillis: (Long) -> Long = { 0L },
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

    /** Reads `Retry-After` as seconds or as an HTTP-date. A bad value or no header waits one second. The cap applies to all. */
    private fun retryAfterMillis(response: Response): Long {
        val raw = response.header("Retry-After")?.trim() ?: return withJitter(DEFAULT_WAIT_MILLIS)
        raw.toLongOrNull()?.let { seconds ->
            if (seconds < 0L) return withJitter(DEFAULT_WAIT_MILLIS)
            return withJitter((seconds * 1_000L).coerceAtMost(maxWaitMillis))
        }
        parseHttpDateMillis(raw)?.let { target ->
            val delta = target - nowMillis()
            if (delta <= 0L) return withJitter(DEFAULT_WAIT_MILLIS)
            return withJitter(delta.coerceAtMost(maxWaitMillis))
        }
        return withJitter(DEFAULT_WAIT_MILLIS)
    }

    private fun withJitter(baseMillis: Long): Long {
        if (baseMillis <= 0L) return 0L
        val jitter = jitterMillis(baseMillis).coerceIn(0L, ImageNetworkLimits.MAX_JITTER_MILLIS)
        return baseMillis + jitter
    }

    private fun parseHttpDateMillis(raw: String): Long? = runCatching {
        DateTimeFormatter.RFC_1123_DATE_TIME.parse(raw, Instant::from).toEpochMilli()
    }.getOrNull()

    private companion object {
        const val TOO_MANY_REQUESTS = 429
        val SERVER_ERRORS = 500..599
        const val DEFAULT_WAIT_MILLIS = 1_000L
        const val DEFAULT_MAX_WAIT_MILLIS = 3_000L
    }
}
