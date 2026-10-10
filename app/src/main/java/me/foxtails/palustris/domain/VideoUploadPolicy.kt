package me.foxtails.palustris.domain

/** The container an uploaded video ends up in. */
enum class VideoUploadFormat { Original, Webm, H264Mp4 }

/** A video file as the format choice sees it. [videoCodecMime] is the first video track's MIME type, or null when unknown. */
data class VideoUploadSource(
    val containerMimeType: String,
    val videoCodecMime: String?,
    val byteSize: Long,
)

/**
 * What the target server accepts. [acceptedTypes] null means the server publishes no list, which is
 * how Misskey behaves: the client tries and falls back on a rejection. [rejectsWebm] records that
 * this server already refused a WebM upload.
 */
data class VideoServerSupport(
    val acceptedTypes: Set<String>?,
    val rejectsWebm: Boolean = false,
)

object VideoUploadPolicy {
    /** A file under this size is sent as it is. The limit is exclusive: 2 MiB itself is transcoded. */
    const val ORIGINAL_MAX_BYTES = 2L * 1024 * 1024

    const val WEBM_MIME = "video/webm"

    /** Default top resolution for transcodes and for the device benchmark. */
    const val DEFAULT_MAX_HEIGHT = 720

    /** The device must encode at least this many times faster than realtime before WebM is chosen. */
    const val MIN_REALTIME_FACTOR = 1.5

    /** Codecs every common server and player handles without further conversion. */
    private val ORIGINAL_CODECS = setOf("video/avc", "video/x-vnd.on2.vp8", "video/x-vnd.on2.vp9")

    /**
     * Picks the format for one video, in this order:
     * 1. the original, when it is small and the server accepts its container and codec;
     * 2. WebM, when the server accepts it and the device can encode it fast enough;
     * 3. H.264 in MP4.
     */
    fun choose(source: VideoUploadSource, server: VideoServerSupport, deviceCanEncodeWebm: Boolean): VideoUploadFormat {
        if (source.byteSize in 1 until ORIGINAL_MAX_BYTES && originalAccepted(source, server)) return VideoUploadFormat.Original
        if (deviceCanEncodeWebm && serverAcceptsWebm(server)) return VideoUploadFormat.Webm
        return VideoUploadFormat.H264Mp4
    }

    fun serverAcceptsWebm(server: VideoServerSupport): Boolean =
        !server.rejectsWebm && (server.acceptedTypes == null || server.acceptedTypes.any { it.equals(WEBM_MIME, ignoreCase = true) })

    private fun originalAccepted(source: VideoUploadSource, server: VideoServerSupport): Boolean {
        val codecOk = source.videoCodecMime?.lowercase() in ORIGINAL_CODECS
        val containerOk = server.acceptedTypes == null ||
            server.acceptedTypes.any { it.equals(source.containerMimeType, ignoreCase = true) }
        return codecOk && containerOk
    }

    /**
     * A video bitrate that keeps [durationMs] of video under [maxBytes], or null when there is no limit
     * or no duration. Ten percent of the budget is left for the audio track and the container.
     */
    fun bitrateForLimit(maxBytes: Long?, durationMs: Long?): Int? {
        if (maxBytes == null || durationMs == null || durationMs <= 0L) return null
        val bits = maxBytes * 8L * 9L / 10L
        return (bits * 1000L / durationMs).coerceIn(MIN_BITRATE, Int.MAX_VALUE.toLong()).toInt()
    }

    private const val MIN_BITRATE = 100_000L
}
