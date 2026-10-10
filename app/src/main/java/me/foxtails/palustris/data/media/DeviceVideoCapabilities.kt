package me.foxtails.palustris.data.media

import java.io.File
import me.foxtails.palustris.domain.VideoUploadPolicy

/** Persists benchmark results, keyed by device and encoder build. Lifetime: the app. */
interface VideoBenchmarkStore {
    fun read(key: String): Double?
    fun write(key: String, realtimeFactor: Double)
}

/** The facts that decide whether this process may use the WebM encoder. */
data class DeviceVideoIdentity(
    val model: String,
    val sdkLevel: Int,
    val primaryAbi: String?,
)

/**
 * Decides whether this device encodes WebM fast enough.
 *
 * Responsibility: the device gate for the WebM path. The process must run on arm64-v8a, the encoder
 * must be present, and a benchmark of a 3 second clip at [VideoUploadPolicy.MAX_SHORT_SIDE] must
 * reach [VideoUploadPolicy.MIN_REALTIME_FACTOR] times realtime. The result is cached under the
 * device model, the SDK level, and the encoder build, so a new OS or encoder build runs it again.
 * Lifetime: one per app. It holds no in-memory state; the store is the cache.
 */
class DeviceVideoCapabilities(
    private val identity: DeviceVideoIdentity,
    private val encoder: WebmEncoder,
    private val store: VideoBenchmarkStore,
) {
    /**
     * True when WebM may be used for a file like [fixture]. The benchmark runs once for each key and uses
     * [fixture] as its clip. A failed benchmark counts as too slow and is cached, because retrying a
     * failing encoder on every upload would cost more than it gains.
     */
    suspend fun canEncodeWebm(fixture: File): Boolean {
        if (identity.primaryAbi != ARM64) return false
        val build = encoder.buildId ?: return false
        val key = cacheKey(build)
        val factor = store.read(key) ?: runBenchmark(fixture).also { store.write(key, it) }
        return factor >= VideoUploadPolicy.MIN_REALTIME_FACTOR
    }

    private suspend fun runBenchmark(fixture: File): Double = try {
        encoder.benchmark(fixture, VideoUploadPolicy.MAX_SHORT_SIDE)
    } catch (error: VideoTranscodeException) {
        0.0
    }

    internal fun cacheKey(build: String): String = "${identity.model}|${identity.sdkLevel}|$build"

    private companion object {
        const val ARM64 = "arm64-v8a"
    }
}
