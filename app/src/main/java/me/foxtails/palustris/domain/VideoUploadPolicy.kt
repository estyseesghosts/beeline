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

    /** The upload tier is 900p: the shorter edge of a transcode never exceeds this many pixels. */
    const val MAX_SHORT_SIDE = 900

    /**
     * The output height cap that keeps the shorter edge within [MAX_SHORT_SIDE]. A landscape video is capped
     * at 900 pixels tall. A portrait video gets a taller cap, so a 1080x1920 clip becomes 900x1600 and not
     * 506x900. An unknown size gets the landscape cap.
     */
    fun heightCap(width: Int?, height: Int?): Int {
        if (width == null || height == null || width <= 0 || height <= width) return MAX_SHORT_SIDE
        return (MAX_SHORT_SIDE.toLong() * height / width).toInt()
    }

    /** The device must encode at least this many times faster than realtime before WebM is chosen. */
    const val MIN_REALTIME_FACTOR = 1.5

    /**
     * Picks the format for one video, in this order:
     * 1. the original, when it is under [ORIGINAL_MAX_BYTES] and the server accepts its container. The codec does not
     *    matter: a small file is never worth a conversion, and a Mastodon type list names containers only;
     * 2. WebM, when the server accepts it and the device can encode it fast enough;
     * 3. H.264 in MP4.
     */
    fun choose(source: VideoUploadSource, server: VideoServerSupport, deviceCanEncodeWebm: Boolean): VideoUploadFormat {
        if (source.byteSize in 1 until ORIGINAL_MAX_BYTES && originalAccepted(source, server)) return VideoUploadFormat.Original
        if (deviceCanEncodeWebm && serverAcceptsWebm(server)) return VideoUploadFormat.Webm
        return VideoUploadFormat.H264Mp4
    }

    /** Codecs that every common server and player handles without further conversion. */
    private val EFFICIENT_CODECS = setOf("video/avc", "video/x-vnd.on2.vp8", "video/x-vnd.on2.vp9")

    /** A source up to this many times the quality bitrate counts as already efficient. */
    private const val EFFICIENT_BITRATE_FACTOR = 2.5

    /**
     * True when converting [source] to H.264 would not pay off: it already uses a common codec, needs no downscale to
     * the 900p tier, and its bitrate is within [EFFICIENT_BITRATE_FACTOR] of the quality target. Hardware encoders often
     * overshoot a low bitrate request, so such a conversion tends to return a file as large as the source.
     */
    fun alreadyEfficient(source: VideoUploadSource, width: Int?, height: Int?, durationMs: Long?): Boolean {
        if (source.videoCodecMime?.lowercase() !in EFFICIENT_CODECS) return false
        if (width == null || height == null || durationMs == null || width <= 0 || height <= 0 || durationMs <= 0L) return false
        if (height > heightCap(width, height)) return false
        val actualBitrate = source.byteSize * 8L * 1000L / durationMs
        val target = width.toDouble() * height * NOMINAL_FPS * H264_BITS_PER_PIXEL
        return actualBitrate <= target * EFFICIENT_BITRATE_FACTOR
    }

    /** True when the server takes the source's container as it is. A server without a type list takes everything. */
    fun containerAccepted(source: VideoUploadSource, server: VideoServerSupport): Boolean = originalAccepted(source, server)

    /**
     * The software decoder in the WebM path handles sources up to this many pixels a frame (1080p) well.
     * A larger source, such as 4K HEVC, decodes slower than realtime in software, so it uses the hardware MP4 path.
     */
    const val MAX_WEBM_SOURCE_PIXELS = 1920L * 1080L

    fun webmSourceIsCheapToDecode(width: Int?, height: Int?): Boolean =
        width != null && height != null && width > 0 && height > 0 && width.toLong() * height <= MAX_WEBM_SOURCE_PIXELS

    fun serverAcceptsWebm(server: VideoServerSupport): Boolean =
        !server.rejectsWebm && (server.acceptedTypes == null || server.acceptedTypes.any { it.equals(WEBM_MIME, ignoreCase = true) })

    private fun originalAccepted(source: VideoUploadSource, server: VideoServerSupport): Boolean =
        server.acceptedTypes == null || server.acceptedTypes.any { it.equals(source.containerMimeType, ignoreCase = true) }

    /**
     * A video bitrate that keeps [durationMs] of video under [maxBytes], or null when there is no limit
     * or no duration. Ten percent of the budget is left for the audio track and the container.
     */
    fun bitrateForLimit(maxBytes: Long?, durationMs: Long?): Int? {
        if (maxBytes == null || durationMs == null || durationMs <= 0L) return null
        val bits = maxBytes * 8L * 9L / 10L
        return (bits * 1000L / durationMs).coerceIn(MIN_BITRATE, Int.MAX_VALUE.toLong()).toInt()
    }

    /** Output bits for each pixel of each frame in an H.264 transcode. Typical phone video is well above this. */
    private const val H264_BITS_PER_PIXEL = 0.07

    /** VP9 reaches the same quality with about three quarters of the H.264 bitrate. */
    private const val VP9_RELATIVE_BITRATE = 0.75

    private const val NOMINAL_FPS = 30.0

    /**
     * The video bitrate to ask the encoder for. It is the smallest of: a quality target for the output size, 90 percent
     * of the source's own bitrate (a conversion should not grow the file), and the budget that keeps the result under
     * [maxBytes]. [webm] lowers the quality target because VP9 is more efficient.
     */
    fun targetBitrate(outputWidth: Int, outputHeight: Int, sourceBytes: Long, durationMs: Long?, maxBytes: Long?, webm: Boolean): Int {
        val quality = outputWidth.toDouble() * outputHeight * NOMINAL_FPS * H264_BITS_PER_PIXEL * (if (webm) VP9_RELATIVE_BITRATE else 1.0)
        val candidates = mutableListOf(quality.toLong())
        if (durationMs != null && durationMs > 0 && sourceBytes > 0) {
            candidates += (sourceBytes * 8L * 1000L / durationMs) * 9L / 10L
        }
        bitrateForLimit(maxBytes, durationMs)?.let { candidates += it.toLong() }
        return candidates.min().coerceIn(MIN_BITRATE, Int.MAX_VALUE.toLong()).toInt()
    }

    private const val MIN_BITRATE = 100_000L
}
