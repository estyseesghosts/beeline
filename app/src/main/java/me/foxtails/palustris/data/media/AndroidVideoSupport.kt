@file:Suppress("UnsafeOptInUsageError")

package me.foxtails.palustris.data.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reads video facts with the platform's metadata retriever and extractor. Both are released on every path. */
class MediaMetadataVideoProbe : VideoProbe {
    override fun probe(file: File): VideoProbeResult? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") return null
            val container = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: return null
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val turned = rotation == 90 || rotation == 270
            VideoProbeResult(
                containerMimeType = container,
                videoCodecMime = firstVideoCodec(file),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                width = if (turned) rawHeight else rawWidth,
                height = if (turned) rawWidth else rawHeight,
            )
        } catch (error: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun firstVideoCodec(file: File): String? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.path)
            (0 until extractor.trackCount)
                .mapNotNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
                .firstOrNull { it.startsWith("video/") }
        } catch (error: Exception) {
            null
        } finally {
            extractor.release()
        }
    }
}

/**
 * Encodes H.264 video and AAC audio into MP4 with Media3 Transformer, which uses the device's
 * hardware codecs. Transformer needs a thread with a looper, so each call runs on the main
 * thread and polls its progress there. Cancelling the caller cancels the export and deletes the output.
 */
class Media3Mp4Transcoder(private val context: Context) : VideoTranscoder {
    override suspend fun transcode(
        input: File,
        output: File,
        maxHeight: Int,
        videoBitrate: Int?,
        onProgress: (Float) -> Unit,
    ) {
        withContext(Dispatchers.Main.immediate) {
            val finished = CompletableDeferred<Unit>()
            val transformer = buildTransformer(videoBitrate, finished)
            val edited = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
                .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(maxHeight))))
                .build()
            try {
                transformer.start(edited, output.path)
                coroutineScope {
                    val poller = launch {
                        val holder = ProgressHolder()
                        while (isActive) {
                            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                                onProgress(holder.progress / PERCENT)
                            }
                            delay(PROGRESS_TICK_MS)
                        }
                    }
                    try {
                        finished.await()
                    } finally {
                        poller.cancel()
                    }
                }
                onProgress(1f)
            } catch (error: Throwable) {
                transformer.cancel()
                output.delete()
                throw error
            }
        }
    }

    private fun buildTransformer(videoBitrate: Int?, finished: CompletableDeferred<Unit>): Transformer {
        val encoderFactory = DefaultEncoderFactory.Builder(context).apply {
            videoBitrate?.let { setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(it).build()) }
        }.build()
        return Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactory)
            .addListener(
                object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        finished.complete(Unit)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        finished.completeExceptionally(VideoTranscodeException("The video could not be converted", exportException))
                    }
                },
            )
            .build()
    }

    private companion object {
        const val PERCENT = 100f
        const val PROGRESS_TICK_MS = 250L
    }
}

/** Keeps benchmark results in a private preferences file. They are not secret and need no account scope. */
class PreferencesVideoBenchmarkStore(context: Context) : VideoBenchmarkStore {
    private val preferences = context.getSharedPreferences("video-benchmark", Context.MODE_PRIVATE)

    override fun read(key: String): Double? =
        if (preferences.contains(key)) java.lang.Double.longBitsToDouble(preferences.getLong(key, 0L)) else null

    override fun write(key: String, realtimeFactor: Double) {
        preferences.edit { putLong(key, java.lang.Double.doubleToRawLongBits(realtimeFactor)) }
    }
}
