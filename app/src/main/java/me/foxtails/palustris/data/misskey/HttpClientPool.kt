package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.Connection
import okhttp3.OkHttpClient
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit

data class HttpLayerConfig(
    val connectTimeoutSeconds: Long = 15,
    val readTimeoutSeconds: Long = 30,
    val callTimeoutSeconds: Long = 40,
)

/**
 * Shares credential-free clients by server origin and protocol.
 *
 * State owned: lookup from Connection to OkHttpClient.
 * Lifetime: lifetime of the injected singleton.
 * Retention: at most 16 entries with access-order eviction.
 * Release: eviction drops the lookup reference only. The pool never
 * closes a client, so a borrowed client remains usable.
 */
class HttpClientPool(private val config: HttpLayerConfig = HttpLayerConfig()) {
    private val clients = LinkedHashMap<Connection, OkHttpClient>(MAX_CLIENTS, 0.75f, true)
    private val lock = Any()

    fun clientFor(connection: Connection): OkHttpClient = synchronized(lock) {
        clients[connection]?.let { return@synchronized it }

        // Keep construction under the same lock as lookup so equal concurrent misses
        // publish one client. The second lookup also makes the publication invariant explicit.
        clients[connection]?.let { return@synchronized it }
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(config.callTimeoutSeconds, TimeUnit.SECONDS)
            .build()
        clients[connection] = client
        // Evict the least recently used lookup entry only. The pool never
        // closes the evicted client, so borrowed users keep a usable client.
        if (clients.size > MAX_CLIENTS) {
            val eldest = clients.entries.iterator()
            eldest.next()
            eldest.remove()
        }
        client
    }

    private companion object {
        const val MAX_CLIENTS = 16
    }
}
