package me.foxtails.palustris.data.media

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.VideoServerSupport
import me.foxtails.palustris.domain.VideoUploadFormat
import me.foxtails.palustris.domain.VideoUploadPolicy
import me.foxtails.palustris.domain.VideoUploadSource

/**
 * The servers that refused a WebM upload. A server without a type list (Misskey) only reveals that by
 * rejecting the file, so the uploader records it here and later uploads to that server use MP4.
 * Lifetime: the process. The set holds at most one entry per server origin, so its size stays bounded
 * by the account list. Call [clear] to release it. Maximum size is the number of known servers.
 */
class WebmRejections {
    private val origins = ConcurrentHashMap.newKeySet<String>()

    fun record(origin: String) {
        origins.add(origin)
    }

    fun contains(origin: String): Boolean = origin in origins

    fun clear() {
        origins.clear()
    }
}

/** The server a video is prepared for. [maxBytes] null means the server reported no video size limit. */
data class VideoServerTarget(
    val origin: String,
    val acceptedTypes: Set<String>?,
    val maxBytes: Long?,
)

/** One video ready to upload. [release] deletes a transcoded copy and never the source file. */
class PreparedVideo(
    val file: File,
    val mimeType: String,
    val fileName: String,
    val format: VideoUploadFormat,
    private val ownsFile: Boolean,
) {
    fun release() {
        if (ownsFile) file.delete()
    }
}

/**
 * Turns a picked video into the file to upload.
 *
 * Responsibility: probe the source, apply [VideoUploadPolicy], run the matching transcoder, and keep
 * the result under the server's size limit. Lifetime: stateless between calls; each call owns one
 * output file in [workDirectory], which [PreparedVideo.release] deletes. A WebM encode that fails
 * falls back to H.264 MP4, so the upload still happens. Cancelling the caller cancels the transcode.
 */
class VideoPreparer(
    private val workDirectory: File,
    private val probe: VideoProbe,
    private val mp4: VideoTranscoder,
    private val webm: WebmEncoder,
    private val device: DeviceVideoCapabilities,
    private val rejections: WebmRejections,
) {
    suspend fun prepare(
        source: File,
        fileName: String,
        server: VideoServerTarget,
        onProgress: (Float) -> Unit = {},
    ): PreparedVideo {
        val facts = probe.probe(source) ?: throw VideoTranscodeException("The file is not a readable video")
        val uploadSource = VideoUploadSource(facts.containerMimeType, facts.videoCodecMime, source.length())
        val support = VideoServerSupport(server.acceptedTypes, rejectsWebm = rejections.contains(server.origin))
        // The benchmark is expensive, so it only runs when WebM could actually be chosen.
        var format = VideoUploadPolicy.choose(uploadSource, support, deviceCanEncodeWebm = false)
        if (format != VideoUploadFormat.Original && VideoUploadPolicy.serverAcceptsWebm(support) &&
            device.canEncodeWebm(source)
        ) {
            format = VideoUploadPolicy.choose(uploadSource, support, deviceCanEncodeWebm = true)
        }
        if (format == VideoUploadFormat.Original) {
            return PreparedVideo(source, facts.containerMimeType, fileName, format, ownsFile = false)
        }
        val bitrate = VideoUploadPolicy.bitrateForLimit(server.maxBytes, facts.durationMs)
        if (format == VideoUploadFormat.Webm) {
            val prepared = tryWebm(source, fileName, facts, bitrate, onProgress)
            if (prepared != null) return fitToLimit(prepared, server)
        }
        return fitToLimit(encodeMp4(source, fileName, facts, bitrate, onProgress), server)
    }

    private suspend fun tryWebm(
        source: File,
        fileName: String,
        facts: VideoProbeResult,
        bitrate: Int?,
        onProgress: (Float) -> Unit,
    ): PreparedVideo? {
        val output = newOutput(fileName, "webm")
        return try {
            webm.transcode(source, output, outputHeight(facts), bitrate, onProgress)
            PreparedVideo(output, WEBM_MIME, replaceExtension(fileName, "webm"), VideoUploadFormat.Webm, ownsFile = true)
        } catch (error: VideoTranscodeException) {
            output.delete()
            null
        }
    }

    private suspend fun encodeMp4(
        source: File,
        fileName: String,
        facts: VideoProbeResult,
        bitrate: Int?,
        onProgress: (Float) -> Unit,
    ): PreparedVideo {
        val output = newOutput(fileName, "mp4")
        try {
            mp4.transcode(source, output, outputHeight(facts), bitrate, onProgress)
        } catch (error: Exception) {
            output.delete()
            throw error
        }
        return PreparedVideo(output, MP4_MIME, replaceExtension(fileName, "mp4"), VideoUploadFormat.H264Mp4, ownsFile = true)
    }

    /** A result over the server limit is a limit failure, not an upload that the server will refuse. */
    private fun fitToLimit(prepared: PreparedVideo, server: VideoServerTarget): PreparedVideo {
        val limit = server.maxBytes
        if (limit != null && prepared.file.length() > limit) {
            prepared.release()
            throw SourceError.ResourceLimit("media.file-size")
        }
        return prepared
    }

    /** The cap applies only when the source is taller, so a small video is never scaled up. */
    private fun outputHeight(facts: VideoProbeResult): Int =
        minOf(facts.height ?: VideoUploadPolicy.DEFAULT_MAX_HEIGHT, VideoUploadPolicy.DEFAULT_MAX_HEIGHT)

    private fun newOutput(fileName: String, extension: String): File {
        workDirectory.mkdirs()
        val base = fileName.substringBeforeLast('.', fileName).filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .ifEmpty { "video" }
        // createTempFile rejects a prefix under three characters, so the prefix always carries a fixed part.
        return File.createTempFile("video-$base-", ".$extension", workDirectory)
    }

    private fun replaceExtension(fileName: String, extension: String): String =
        "${fileName.substringBeforeLast('.', fileName)}.$extension"

    private companion object {
        const val WEBM_MIME = "video/webm"
        const val MP4_MIME = "video/mp4"
    }
}
