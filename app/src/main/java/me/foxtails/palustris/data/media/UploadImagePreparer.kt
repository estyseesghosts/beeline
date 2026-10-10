package me.foxtails.palustris.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.foxtails.palustris.domain.COMPRESSIBLE_IMAGE_TYPES

/** What a server accepts for one image. A null limit means the server did not report one. */
data class UploadImageLimits(
    val maxBytes: Long? = null,
    val maxPixels: Long? = null,
    val acceptedTypes: Set<String>? = null,
)

/**
 * One image ready to upload.
 *
 * Lifetime: the publisher owns it from [UploadImagePreparer.prepare] until it calls [release]
 * after the upload. [release] deletes a prepared copy and never the draft's own file.
 */
class PreparedImage internal constructor(
    val file: File,
    val mimeType: String,
    val fileName: String,
    private val temporary: Boolean,
) {
    fun release() {
        if (temporary) file.delete()
    }
}

sealed class ImagePreparationException(message: String) : Exception(message)

/** The file is not an image the platform can decode. */
class ImageUnreadable : ImagePreparationException("Image cannot be decoded")

/** No allowed re-encoding meets the server limits. The caller stops before any request. */
class ImageDoesNotFit : ImagePreparationException("Image cannot be made to fit the server limits")

/** Writes a bitmap. A seam so tests can force an encoder result. */
fun interface BitmapEncoder {
    fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int, out: OutputStream): Boolean

    companion object {
        val Default = BitmapEncoder { bitmap, format, quality, out -> bitmap.compress(format, quality, out) }
    }
}

/**
 * Turns a draft image into the file that is uploaded.
 *
 * Responsibility: client-side compression, fitting to server limits, EXIF orientation, and
 * removing location data. It never changes the draft's own file.
 * Lifetime: one call to [prepare] for each image. It keeps no state between calls. Prepared copies
 * live in [workDirectory] (the cache directory) until the publisher releases them.
 *
 * Animated images (GIF, APNG, animated WebP) are never re-encoded. They pass unchanged when they fit
 * and fail with [ImageDoesNotFit] when they do not, because flattening would destroy the animation.
 */
class UploadImagePreparer(
    private val workDirectory: File,
    private val encoder: BitmapEncoder = BitmapEncoder.Default,
) {
    suspend fun prepare(
        source: File,
        mimeType: String,
        fileName: String,
        compress: Boolean,
        limits: UploadImageLimits,
    ): PreparedImage = withContext(Dispatchers.IO) {
        val type = mimeType.lowercase()
        val info = ImageFormatInspector.inspect(source, type) ?: throw ImageUnreadable()
        if (info.animated) {
            if (!fits(type, source.length(), info.pixels, limits)) throw ImageDoesNotFit()
            return@withContext PreparedImage(source, type, fileName, temporary = false)
        }
        compressed(source, type, fileName, info, compress, limits)
            ?: original(source, type, fileName, info, limits)
            ?: fitAsJpeg(source, fileName, info, limits)
    }

    /** Returns the compressed copy, or null when compression is off, not applicable, or not smaller. */
    private fun compressed(
        source: File,
        type: String,
        fileName: String,
        info: ImageInfo,
        compress: Boolean,
        limits: UploadImageLimits,
    ): PreparedImage? {
        if (!compress || type !in COMPRESSIBLE_TYPES) return null
        val encoded = encode(source, info, COMPRESS_LONG_EDGE, webpFormat(), WEBP_QUALITY, WEBP_TYPE, ".webp")
        val smaller = encoded.file.length() < source.length()
        if (!smaller || !fits(WEBP_TYPE, encoded.file.length(), encoded.pixels, limits)) {
            encoded.file.delete()
            return null
        }
        return PreparedImage(encoded.file, WEBP_TYPE, renamed(fileName, "webp"), temporary = true)
    }

    /**
     * Returns the original file, or a copy without location data. Returns null when the original
     * cannot be uploaded as it is: it does not fit, is not a passthrough type, or cannot be cleaned.
     */
    private fun original(
        source: File,
        type: String,
        fileName: String,
        info: ImageInfo,
        limits: UploadImageLimits,
    ): PreparedImage? {
        if (type !in COMPRESSIBLE_TYPES || !fits(type, source.length(), info.pixels, limits)) return null
        return try {
            val exif = ExifInterface(source)
            if (GPS_TAGS.none(exif::hasAttribute)) return PreparedImage(source, type, fileName, temporary = false)
            val copy = newTemporaryFile(extensionOf(fileName))
            source.copyTo(copy, overwrite = true)
            try {
                ExifInterface(copy).apply {
                    GPS_TAGS.forEach { setAttribute(it, null) }
                    saveAttributes()
                }
            } catch (failure: Exception) {
                copy.delete()
                throw failure
            }
            PreparedImage(copy, type, fileName, temporary = true)
        } catch (_: Exception) {
            // A file that cannot be cleaned in place is re-encoded, which drops all metadata.
            null
        }
    }

    /** Re-encodes as JPEG, shrinking until the pixel and byte limits hold. */
    private fun fitAsJpeg(source: File, fileName: String, info: ImageInfo, limits: UploadImageLimits): PreparedImage {
        if (limits.acceptedTypes != null && JPEG_TYPE !in limits.acceptedTypes) throw ImageDoesNotFit()
        var scale = pixelScale(info, limits)
        var quality = JPEG_QUALITY
        repeat(MAX_FIT_ATTEMPTS) {
            val target = max(MIN_LONG_EDGE, (info.longEdge * scale).toInt())
            val encoded = encode(source, info, target, Bitmap.CompressFormat.JPEG, quality, JPEG_TYPE, ".jpg")
            if (fits(JPEG_TYPE, encoded.file.length(), encoded.pixels, limits)) {
                return PreparedImage(encoded.file, JPEG_TYPE, renamed(fileName, "jpg"), temporary = true)
            }
            encoded.file.delete()
            scale *= SHRINK_FACTOR
            quality = max(MIN_JPEG_QUALITY, quality - QUALITY_STEP)
        }
        throw ImageDoesNotFit()
    }

    private fun pixelScale(info: ImageInfo, limits: UploadImageLimits): Double {
        val maxPixels = limits.maxPixels ?: return 1.0
        if (info.pixels <= maxPixels) return 1.0
        return sqrt(maxPixels.toDouble() / info.pixels) * PIXEL_MARGIN
    }

    private fun fits(type: String, bytes: Long, pixels: Long, limits: UploadImageLimits): Boolean =
        (limits.acceptedTypes == null || type in limits.acceptedTypes) &&
            (limits.maxBytes == null || bytes <= limits.maxBytes) &&
            (limits.maxPixels == null || pixels <= limits.maxPixels)

    private class Encoded(val file: File, val pixels: Long)

    /** Decodes near [targetLongEdge], applies the EXIF orientation, and writes one temporary file. */
    private fun encode(
        source: File,
        info: ImageInfo,
        targetLongEdge: Int,
        format: Bitmap.CompressFormat,
        quality: Int,
        type: String,
        extension: String,
    ): Encoded {
        val longEdge = min(targetLongEdge, info.longEdge)
        val factor = longEdge.toDouble() / info.longEdge
        val scaledWidth = max(1, (info.width * factor).roundToInt())
        val scaledHeight = max(1, (info.height * factor).roundToInt())
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(info.longEdge, longEdge) }
        val decoded = BitmapFactory.decodeFile(source.path, options) ?: throw ImageUnreadable()
        val matrix = orientationMatrix(info.orientation).apply {
            preScale(scaledWidth.toFloat() / decoded.width, scaledHeight.toFloat() / decoded.height)
        }
        var result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (result !== decoded) decoded.recycle()
        if (format == Bitmap.CompressFormat.JPEG && result.hasAlpha()) result = flattenOnWhite(result)
        val file = newTemporaryFile(extension)
        try {
            file.outputStream().use { out ->
                if (!encoder.encode(result, format, quality, out)) throw ImageUnreadable()
            }
            return Encoded(file, result.width.toLong() * result.height)
        } catch (failure: Exception) {
            file.delete()
            throw failure
        } finally {
            result.recycle()
        }
    }

    private fun flattenOnWhite(bitmap: Bitmap): Bitmap {
        val flat = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        bitmap.recycle()
        return flat
    }

    /** The largest power of two that keeps the decoded long edge at or above the target. */
    private fun sampleSize(longEdge: Int, target: Int): Int {
        var sample = 1
        while (longEdge / (sample * 2) >= target) sample *= 2
        return sample
    }

    /** Maps the stored pixels to the displayed orientation. */
    private fun orientationMatrix(orientation: Int): Matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                postRotate(180f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                postRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                postRotate(-90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)
        }
    }

    private fun newTemporaryFile(extension: String): File {
        workDirectory.mkdirs()
        return File.createTempFile("upload-", extension, workDirectory)
    }

    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    private fun renamed(fileName: String, extension: String): String =
        "${fileName.substringBeforeLast('.', fileName)}.$extension"

    private fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', "").takeIf { it.isNotEmpty() }?.let { ".$it" } ?: ".img"

    private companion object {
        const val JPEG_TYPE = "image/jpeg"
        const val WEBP_TYPE = "image/webp"
        val COMPRESSIBLE_TYPES = COMPRESSIBLE_IMAGE_TYPES

        /** Misskey web level 1: the long edge is at most 2000 px, WebP at quality 85. */
        const val COMPRESS_LONG_EDGE = 2000
        const val WEBP_QUALITY = 85
        const val JPEG_QUALITY = 90
        const val MIN_JPEG_QUALITY = 70
        const val QUALITY_STEP = 5
        const val MAX_FIT_ATTEMPTS = 6
        const val MIN_LONG_EDGE = 64
        const val SHRINK_FACTOR = 0.8
        const val PIXEL_MARGIN = 0.98

        val GPS_TAGS = listOf(
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_TRACK, ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            ExifInterface.TAG_GPS_DEST_LATITUDE, ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
            ExifInterface.TAG_GPS_DEST_LONGITUDE, ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
            ExifInterface.TAG_GPS_MAP_DATUM, ExifInterface.TAG_GPS_SATELLITES,
        )
    }
}
