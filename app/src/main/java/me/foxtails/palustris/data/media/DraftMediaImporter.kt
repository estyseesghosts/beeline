package me.foxtails.palustris.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.foxtails.palustris.data.auth.DraftMediaStore
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.DraftMedia
import me.foxtails.palustris.domain.DraftMediaImportError
import me.foxtails.palustris.domain.DraftMediaImportException

/**
 * Copies a picked image or video into draft media storage and decodes thumbnails from it.
 *
 * Responsibility: check one picked file and hand its bytes to [DraftMediaStore]. Lifetime: stateless;
 * each call owns one temporary copy in the cache directory and deletes it before it returns. Access to
 * a picked URI ends with the process, so the draft keeps its own encrypted copy. The checks reject
 * a file that is neither a decodable image nor a readable video, an image over [maxBytes], a video over
 * [maxVideoBytes], and a file that cannot be read. The server limits apply later, when the publisher
 * prepares the file.
 */
class DraftMediaImporter(
    private val context: Context,
    private val store: DraftMediaStore,
    private val maxBytes: Long = MAX_IMPORT_BYTES,
    private val maxVideoBytes: Long = MAX_IMPORT_VIDEO_BYTES,
    private val videoProbe: VideoProbe = MediaMetadataVideoProbe(),
) {
    suspend fun import(accountId: AccountId, draftId: String, source: Uri): DraftMedia = withContext(Dispatchers.IO) {
        val workDirectory = File(context.cacheDir, "draft-import").apply { mkdirs() }
        // A crash can leave a copy behind. Only old copies go, so a running import keeps its own.
        val stale = System.currentTimeMillis() - STALE_COPY_MILLIS
        workDirectory.listFiles()?.filter { it.lastModified() < stale }?.forEach { it.delete() }
        val copy = File.createTempFile("import-", ".img", workDirectory)
        try {
            val size = copyBounded(source, copy, maxOf(maxBytes, maxVideoBytes))
            if (size == 0L) throw DraftMediaImportException(DraftMediaImportError.NotAnImage)
            val imageType = sniffMimeType(copy)
            val imageInfo = imageType?.let { ImageFormatInspector.inspect(copy, it) }
            val facts = if (imageInfo != null) {
                if (size > maxBytes) throw DraftMediaImportException(DraftMediaImportError.TooLarge)
                MediaFacts(requireNotNull(imageType), imageInfo.width, imageInfo.height)
            } else {
                val video = videoProbe.probe(copy) ?: throw DraftMediaImportException(DraftMediaImportError.NotAnImage)
                if (size > maxVideoBytes) throw DraftMediaImportException(DraftMediaImportError.TooLarge)
                MediaFacts(video.containerMimeType.lowercase(), video.width ?: 0, video.height ?: 0)
            }
            val mediaId = UUID.randomUUID().toString()
            copy.inputStream().use { store.write(accountId, draftId, mediaId, it) }
            DraftMedia(id = mediaId, mimeType = facts.mimeType, width = facts.width, height = facts.height, byteSize = size)
        } finally {
            copy.delete()
        }
    }

    /**
     * Decodes a draft image no larger than [maxEdge] on its long side, rotated upright. Returns null
     * when the file is missing or damaged, so a thumbnail failure never blocks editing.
     */
    suspend fun thumbnail(accountId: AccountId, draftId: String, mediaId: String, maxEdge: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                store.open(accountId, draftId, mediaId).use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    // Not an image: a draft video gets the first frame as its poster.
                    return@runCatching videoPoster(accountId, draftId, mediaId, maxEdge)
                }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
                val decoded = store.open(accountId, draftId, mediaId).use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return@runCatching null
                val orientation = store.open(accountId, draftId, mediaId).use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
                upright(decoded, orientation)
            }.getOrNull()
        }

    /**
     * Decodes the first frame of a draft video no larger than [maxEdge]. The video is decrypted to a
     * temporary file because the retriever needs a seekable source, and that file is deleted here.
     */
    private suspend fun videoPoster(accountId: AccountId, draftId: String, mediaId: String, maxEdge: Int): Bitmap? {
        val workDirectory = File(context.cacheDir, "draft-import").apply { mkdirs() }
        val plain = File.createTempFile("poster-", ".vid", workDirectory)
        val retriever = MediaMetadataRetriever()
        try {
            store.open(accountId, draftId, mediaId).use { input -> plain.outputStream().use { input.copyTo(it) } }
            retriever.setDataSource(plain.path)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            val scale = minOf(1f, maxEdge.toFloat() / maxOf(width, height))
            return retriever.getScaledFrameAtTime(
                0L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                maxOf(1, (width * scale).toInt()),
                maxOf(1, (height * scale).toInt()),
            )
        } finally {
            retriever.release()
            plain.delete()
        }
    }

    private class MediaFacts(val mimeType: String, val width: Int, val height: Int)

    /** Copies [source] into [target] and stops with [DraftMediaImportError.TooLarge] past [limit]. */
    private fun copyBounded(source: Uri, target: File, limit: Long): Long {
        val input = try {
            context.contentResolver.openInputStream(source)
        } catch (error: Exception) {
            throw DraftMediaImportException(DraftMediaImportError.Unreadable, error)
        } ?: throw DraftMediaImportException(DraftMediaImportError.Unreadable)
        var total = 0L
        try {
            input.use { stream ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > limit) throw DraftMediaImportException(DraftMediaImportError.TooLarge)
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (rejected: DraftMediaImportException) {
            throw rejected
        } catch (error: Exception) {
            // A provider may fail a read with a security or unsupported-operation error, not only an I/O error.
            throw DraftMediaImportException(DraftMediaImportError.Unreadable, error)
        }
        return total
    }

    private fun sniffMimeType(file: File): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        file.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        return bounds.outMimeType?.lowercase()?.takeIf { it.startsWith("image/") && bounds.outWidth > 0 }
    }

    private fun upright(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        /** A guard for storage, far above any server limit. The preparer fits the image to the server later. */
        const val MAX_IMPORT_BYTES = 100L * 1024 * 1024

        /**
         * The largest video kept in a draft. Storage encrypts the whole file, so this bounds the copy that
         * the publisher decrypts again. The preparer shrinks it to the server limit later.
         */
        const val MAX_IMPORT_VIDEO_BYTES = 256L * 1024 * 1024
        private const val BUFFER_BYTES = 16 * 1024
        private const val STALE_COPY_MILLIS = 60L * 60 * 1000
    }
}
