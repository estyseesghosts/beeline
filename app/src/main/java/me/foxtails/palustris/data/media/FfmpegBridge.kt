package me.foxtails.palustris.data.media

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Receives transcode progress from native code, 0 to 1. Called on the encoding thread. */
fun interface FfmpegProgress {
    fun onProgress(fraction: Float)
}

/**
 * The JNI entry points in `app/src/main/cpp/ffmpeg_jni.cpp`. Responsibility: only the native signatures.
 * Callers use [FfmpegBridge]. [buildId] is null on ABIs without the FFmpeg libraries.
 * The native calls block, so call them off the main thread.
 */
internal object FfmpegNative {
    @JvmStatic external fun buildId(): String?

    /** Returns `[durationMs, width, height]` or null when the file has no readable video stream. */
    @JvmStatic external fun probe(path: String): LongArray?

    /** Returns false when [cancel] stopped the encode. Throws [RuntimeException] when it fails. */
    @JvmStatic external fun transcodeWebm(
        input: String,
        output: String,
        maxHeight: Int,
        videoBitrate: Int,
        progress: FfmpegProgress,
        cancel: AtomicBoolean,
    ): Boolean

    /** Encodes up to 3 seconds to nowhere and returns media seconds divided by wall seconds. */
    @JvmStatic external fun benchmarkVp9(fixture: String, maxHeight: Int): Double
}

/**
 * VP9 and Opus in WebM through the LGPL FFmpeg build (tools/ffmpeg).
 *
 * Responsibility: adapt the JNI calls to [WebmEncoder]. Lifetime: stateless. The native library loads
 * once; when it is missing or its ABI has no FFmpeg, [buildId] is null and the device gate fails.
 * Cancelling the caller raises a flag that the native loop reads between packets, then waits for the
 * loop to return before it deletes the partial output.
 */
class FfmpegBridge(private val native: NativeApi = JniNativeApi) : WebmEncoder {
    /** What the bridge needs from native code. A fake in unit tests stands in for [FfmpegNative]. */
    interface NativeApi {
        val buildId: String?
        fun transcode(input: File, output: File, maxHeight: Int, bitrate: Int, progress: FfmpegProgress, cancel: AtomicBoolean): Boolean
        fun benchmark(fixture: File, maxHeight: Int): Double
    }

    override val buildId: String? get() = native.buildId

    override suspend fun benchmark(fixture: File, maxHeight: Int): Double = withContext(Dispatchers.Default) {
        try {
            native.benchmark(fixture, maxHeight)
        } catch (error: RuntimeException) {
            throw VideoTranscodeException("The WebM benchmark failed", error)
        }
    }

    override suspend fun transcode(
        input: File,
        output: File,
        maxHeight: Int,
        videoBitrate: Int?,
        onProgress: (Float) -> Unit,
    ) {
        val cancel = AtomicBoolean(false)
        try {
            val completed = coroutineScope {
                val work = async(Dispatchers.Default) {
                    native.transcode(input, output, maxHeight, videoBitrate ?: 0, FfmpegProgress(onProgress), cancel)
                }
                try {
                    work.await()
                } catch (cancelled: CancellationException) {
                    cancel.set(true)
                    // The native loop checks the flag between packets; wait so it no longer writes the file.
                    withContext(NonCancellable) { runCatching { work.await() } }
                    throw cancelled
                }
            }
            if (!completed) throw CancellationException("The WebM encode was cancelled")
        } catch (error: RuntimeException) {
            output.delete()
            if (error is CancellationException) throw error
            throw VideoTranscodeException("The video could not be converted to WebM", error)
        } catch (error: Throwable) {
            output.delete()
            throw error
        }
    }
}

/** The production [FfmpegBridge.NativeApi]. The library loads on first use and a failure counts as "absent". */
object JniNativeApi : FfmpegBridge.NativeApi {
    private val loaded: Boolean by lazy {
        try {
            System.loadLibrary("beeline_ffmpeg")
            true
        } catch (error: UnsatisfiedLinkError) {
            false
        }
    }

    override val buildId: String? get() = if (loaded) FfmpegNative.buildId() else null

    override fun transcode(
        input: File,
        output: File,
        maxHeight: Int,
        bitrate: Int,
        progress: FfmpegProgress,
        cancel: AtomicBoolean,
    ): Boolean = FfmpegNative.transcodeWebm(input.path, output.path, maxHeight, bitrate, progress, cancel)

    override fun benchmark(fixture: File, maxHeight: Int): Double = FfmpegNative.benchmarkVp9(fixture.path, maxHeight)
}
