package me.foxtails.palustris.data.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A request for the first frame of the video at [url], for videos whose server sent no poster. */
data class VideoFrameRequest(val url: String)

/**
 * Reads the first frame of a remote video through the platform retriever, which asks the server for
 * only the byte ranges it needs. The video is never copied into the image cache.
 */
class VideoFrameFetcher(
    private val data: VideoFrameRequest,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(data.url, emptyMap())
            val frame = firstFrame(retriever) ?: throw IllegalStateException("No video frame")
            DrawableResult(
                drawable = frame.toDrawable(options.context.resources),
                isSampled = false,
                dataSource = DataSource.NETWORK,
            )
        } finally {
            retriever.release()
        }
    }

    private fun firstFrame(retriever: MediaMetadataRetriever): Bitmap? {
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
        if (width == null || height == null || width <= 0 || height <= 0) {
            return retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
        val scale = minOf(1f, MAX_FRAME_EDGE_PX.toFloat() / maxOf(width, height))
        return retriever.getScaledFrameAtTime(
            0,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
        )
    }

    class Factory : Fetcher.Factory<VideoFrameRequest> {
        override fun create(data: VideoFrameRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            VideoFrameFetcher(data, options)
    }

    private companion object {
        const val MAX_FRAME_EDGE_PX = 1080
    }
}
