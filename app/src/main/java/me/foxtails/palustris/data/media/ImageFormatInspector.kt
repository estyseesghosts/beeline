package me.foxtails.palustris.data.media

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.RandomAccessFile

/** What the preparer needs to know about one image file before it decodes pixels. */
internal data class ImageInfo(
    val width: Int,
    val height: Int,
    /** An EXIF orientation value, or [ExifInterface.ORIENTATION_NORMAL]. */
    val orientation: Int,
    val animated: Boolean,
) {
    val pixels: Long get() = width.toLong() * height
    val longEdge: Int get() = maxOf(width, height)
}

/**
 * Reads image facts from the file header without decoding pixels.
 *
 * Stateless. Every function reads only the file it is given.
 */
internal object ImageFormatInspector {
    private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private const val WEBP_ANIMATION_FLAG = 0x02

    /** Returns the stored (not rotated) size, or null when the file is not a decodable image. */
    fun inspect(file: File, mimeType: String): ImageInfo? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return ImageInfo(
            width = bounds.outWidth,
            height = bounds.outHeight,
            orientation = orientation(file, mimeType),
            animated = isAnimated(file, mimeType),
        )
    }

    private fun orientation(file: File, mimeType: String): Int {
        if (mimeType.lowercase() !in ORIENTED_TYPES) return ExifInterface.ORIENTATION_NORMAL
        return runCatching {
            ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    /** GIF always counts as animated for upload: it is never re-encoded. APNG and animated WebP are sniffed. */
    fun isAnimated(file: File, mimeType: String): Boolean = when (mimeType.lowercase()) {
        "image/gif" -> true
        "image/png", "image/apng" -> runCatching { pngHasAnimationChunk(file) }.getOrDefault(false)
        "image/webp" -> runCatching { webpIsAnimated(file) }.getOrDefault(false)
        else -> false
    }

    /** Walks the chunks that precede the first IDAT. An `acTL` chunk marks an animated PNG. */
    private fun pngHasAnimationChunk(file: File): Boolean = RandomAccessFile(file, "r").use { input ->
        val signature = ByteArray(pngSignature.size)
        input.readFully(signature)
        if (!signature.contentEquals(pngSignature)) return false
        while (input.filePointer + CHUNK_HEADER_BYTES <= input.length()) {
            val length = input.readInt().toLong() and 0xFFFFFFFFL
            val type = ByteArray(4).also(input::readFully).toString(Charsets.US_ASCII)
            if (type == "acTL") return true
            if (type == "IDAT") return false
            input.seek(input.filePointer + length + CRC_BYTES)
        }
        false
    }

    /** An animated WebP starts with a VP8X chunk whose flags carry the animation bit. */
    private fun webpIsAnimated(file: File): Boolean = RandomAccessFile(file, "r").use { input ->
        val header = ByteArray(WEBP_HEADER_BYTES)
        input.readFully(header)
        val riff = header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
            header.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"
        riff && header.copyOfRange(12, 16).toString(Charsets.US_ASCII) == "VP8X" &&
            (header[20].toInt() and WEBP_ANIMATION_FLAG) != 0
    }

    private val ORIENTED_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")
    private const val CHUNK_HEADER_BYTES = 8
    private const val CRC_BYTES = 4
    private const val WEBP_HEADER_BYTES = 21
}
