package me.foxtails.palustris.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
 * Copies a picked image into draft media storage and decodes thumbnails from it.
 *
 * Responsibility: check one picked file and hand its bytes to [DraftMediaStore]. Lifetime: stateless;
 * each call owns one temporary copy in the cache directory and deletes it before it returns. Access to
 * a picked URI ends with the process, so the draft keeps its own encrypted copy. The checks reject
 * a file that is not a decodable image, a file over [maxBytes], and a file that cannot be read.
 * The server limits apply later, when the publisher prepares the image.
 */
class DraftMediaImporter(
    private val context: Context,
    private val store: DraftMediaStore,
    private val maxBytes: Long = MAX_IMPORT_BYTES,
) {
    suspend fun import(accountId: AccountId, draftId: String, source: Uri): DraftMedia = withContext(Dispatchers.IO) {
        val workDirectory = File(context.cacheDir, "draft-import").apply { mkdirs() }
        // A crash can leave a copy behind. Only old copies go, so a running import keeps its own.
        val stale = System.currentTimeMillis() - STALE_COPY_MILLIS
        workDirectory.listFiles()?.filter { it.lastModified() < stale }?.forEach { it.delete() }
        val copy = File.createTempFile("import-", ".img", workDirectory)
        try {
            val size = copyBounded(source, copy)
            if (size == 0L) throw DraftMediaImportException(DraftMediaImportError.NotAnImage)
            val mimeType = sniffMimeType(copy) ?: throw DraftMediaImportException(DraftMediaImportError.NotAnImage)
            val info = ImageFormatInspector.inspect(copy, mimeType)
                ?: throw DraftMediaImportException(DraftMediaImportError.NotAnImage)
            val mediaId = UUID.randomUUID().toString()
            copy.inputStream().use { store.write(accountId, draftId, mediaId, it) }
            DraftMedia(id = mediaId, mimeType = mimeType, width = info.width, height = info.height, byteSize = size)
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
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
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

    /** Copies [source] into [target] and stops with [DraftMediaImportError.TooLarge] past the limit. */
    private fun copyBounded(source: Uri, target: File): Long {
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
                        if (total > maxBytes) throw DraftMediaImportException(DraftMediaImportError.TooLarge)
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
        private const val BUFFER_BYTES = 16 * 1024
        private const val STALE_COPY_MILLIS = 60L * 60 * 1000
    }
}
