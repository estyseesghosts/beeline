package me.foxtails.palustris.data.media

import java.io.File

/** Facts about one video file that the format choice and the transcode need. */
data class VideoProbeResult(
    val containerMimeType: String,
    /** MIME type of the first video track, or null when the file has none or it cannot be read. */
    val videoCodecMime: String?,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
)

/** Reads a video file's container, codec, duration, and size. Implementations never keep the file open. */
fun interface VideoProbe {
    /** Returns null when the file is not a readable video. */
    fun probe(file: File): VideoProbeResult?
}

/** Why a transcode could not produce a file. */
class VideoTranscodeException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Encodes a video to one fixed output format.
 *
 * Responsibility: one transcode from [input] to [output]. Lifetime: stateless between calls.
 * [maxHeight] caps the output height and never upscales. [videoBitrate] null lets the encoder decide.
 * [onProgress] receives 0 to 1. A failure throws [VideoTranscodeException]. Cancelling the
 * calling coroutine stops the encode and removes any partial [output].
 */
interface VideoTranscoder {
    suspend fun transcode(
        input: File,
        output: File,
        maxHeight: Int,
        videoBitrate: Int?,
        onProgress: (Float) -> Unit,
    )
}

/**
 * A VP9 and Opus encoder in a WebM container. [buildId] identifies the encoder build for the
 * benchmark cache. A null [buildId] means the encoder is not present on this device.
 */
interface WebmEncoder : VideoTranscoder {
    val buildId: String?

    /** Encodes a short clip of [fixture] and returns how many times faster than realtime it ran. */
    suspend fun benchmark(fixture: File, maxHeight: Int): Double
}

/** The WebM encoder before FFmpeg is present. It reports itself unavailable, so uploads use H.264 MP4. */
object UnavailableWebmEncoder : WebmEncoder {
    override val buildId: String? = null

    override suspend fun benchmark(fixture: File, maxHeight: Int): Double = 0.0

    override suspend fun transcode(
        input: File,
        output: File,
        maxHeight: Int,
        videoBitrate: Int?,
        onProgress: (Float) -> Unit,
    ): Unit = throw VideoTranscodeException("WebM encoding is not available on this device")
}
