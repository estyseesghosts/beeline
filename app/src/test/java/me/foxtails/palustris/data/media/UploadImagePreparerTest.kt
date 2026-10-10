package me.foxtails.palustris.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UploadImagePreparerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val work by lazy { folder.newFolder("work") }
    private val preparer by lazy { UploadImagePreparer(work) }
    private val open = UploadImageLimits()

    @Test
    fun compressionShrinksALargeJpegToAWebpWithinTwoThousandPixels() = runBlocking {
        val source = jpeg("big.jpg", 4000, 3000)

        val prepared = preparer.prepare(source, "image/jpeg", "big.jpg", compress = true, limits = open)

        assertEquals("image/webp", prepared.mimeType)
        assertEquals("big.webp", prepared.fileName)
        val size = bounds(prepared.file)
        assertEquals(2000, maxOf(size.first, size.second))
        assertEquals(1500, minOf(size.first, size.second))
        assertTrue(prepared.file.length() < source.length())
        assertTrue(prepared.file != source)
    }

    @Test
    fun aGifPassesUnchanged() = runBlocking {
        val source = folder.newFile("anim.gif").apply { writeBytes(TINY_GIF) }

        val prepared = preparer.prepare(source, "image/gif", "anim.gif", compress = true, limits = open)

        assertEquals(source, prepared.file)
        assertEquals("image/gif", prepared.mimeType)
        prepared.release()
        assertTrue(source.exists())
        assertArrayEquals(TINY_GIF, source.readBytes())
    }

    @Test
    fun aGifOverTheByteLimitFailsInsteadOfFlattening() {
        val source = folder.newFile("anim.gif").apply { writeBytes(TINY_GIF) }

        assertThrows(ImageDoesNotFit::class.java) {
            runBlocking {
                preparer.prepare(source, "image/gif", "anim.gif", true, UploadImageLimits(maxBytes = 10))
            }
        }
    }

    @Test
    fun aResultThatIsNotSmallerKeepsTheOriginal() = runBlocking {
        val source = jpeg("small.jpg", 64, 64)
        val bloated = BitmapEncoder { bitmap, format, quality, out ->
            out.write(ByteArray(source.length().toInt() + 100))
            true
        }

        val prepared = UploadImagePreparer(work, bloated)
            .prepare(source, "image/jpeg", "small.jpg", compress = true, limits = open)

        assertEquals(source, prepared.file)
        assertEquals("image/jpeg", prepared.mimeType)
        assertTrue(work.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun compressionOffKeepsAFittingImageUntouched() = runBlocking {
        val source = jpeg("keep.jpg", 800, 600)

        val prepared = preparer.prepare(source, "image/jpeg", "keep.jpg", compress = false, limits = open)

        assertEquals(source, prepared.file)
    }

    @Test
    fun anImageOverTheByteLimitBecomesAJpegThatFits() = runBlocking {
        val source = noisyPng("noise.png", 1200, 1200)
        val limit = 150_000L
        assertTrue(source.length() > limit)

        val prepared = preparer.prepare(source, "image/png", "noise.png", compress = false, UploadImageLimits(maxBytes = limit))

        assertEquals("image/jpeg", prepared.mimeType)
        assertEquals("noise.jpg", prepared.fileName)
        assertTrue(prepared.file.length() <= limit)
        assertNotNull(BitmapFactory.decodeFile(prepared.file.path))
    }

    @Test
    fun anImageOverThePixelLimitIsScaledDown() = runBlocking {
        val source = jpeg("pixels.jpg", 2000, 1000)

        val prepared = preparer.prepare(source, "image/jpeg", "pixels.jpg", compress = false, UploadImageLimits(maxPixels = 500_000))

        val size = bounds(prepared.file)
        assertTrue(size.first.toLong() * size.second <= 500_000)
        assertEquals("image/jpeg", prepared.mimeType)
    }

    @Test
    fun aTypeTheServerDoesNotAcceptIsReEncodedAsJpeg() = runBlocking {
        val source = noisyPng("type.png", 200, 200)
        val limits = UploadImageLimits(acceptedTypes = setOf("image/jpeg"))

        val prepared = preparer.prepare(source, "image/png", "type.png", compress = false, limits)

        assertEquals("image/jpeg", prepared.mimeType)
    }

    @Test
    fun anImageThatCannotFitFailsBeforeAnyUpload() {
        val source = noisyPng("hopeless.png", 600, 600)

        assertThrows(ImageDoesNotFit::class.java) {
            runBlocking {
                preparer.prepare(source, "image/png", "hopeless.png", compress = false, UploadImageLimits(maxBytes = 200))
            }
        }
        assertTrue(work.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun aFileThatIsNotAnImageIsUnreadable() {
        val source = folder.newFile("text.jpg").apply { writeText("not an image") }

        assertThrows(ImageUnreadable::class.java) {
            runBlocking { preparer.prepare(source, "image/jpeg", "text.jpg", compress = true, limits = open) }
        }
    }

    @Test
    fun orientationIsAppliedWhenTheImageIsReEncoded() = runBlocking {
        val source = jpeg("rotated.jpg", 400, 200)
        ExifInterface(source).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        val prepared = preparer.prepare(source, "image/jpeg", "rotated.jpg", compress = true, limits = open)

        assertEquals(200 to 400, bounds(prepared.file))
    }

    @Test
    fun locationDataIsRemovedFromACopyAndTheDraftFileIsKept() = runBlocking {
        val source = jpeg("gps.jpg", 300, 200)
        ExifInterface(source).apply {
            setLatLong(48.8584, 2.2945)
            saveAttributes()
        }
        assertNotNull(ExifInterface(source).latLong)

        val prepared = preparer.prepare(source, "image/jpeg", "gps.jpg", compress = false, limits = open)

        assertTrue(prepared.file != source)
        assertNull(ExifInterface(prepared.file).latLong)
        assertNotNull(ExifInterface(source).latLong)
        prepared.release()
        assertFalse(prepared.file.exists())
        assertTrue(source.exists())
    }

    @Test
    fun aReEncodedImageCarriesNoLocationData() = runBlocking {
        val source = jpeg("gps2.jpg", 3000, 2000)
        ExifInterface(source).apply {
            setLatLong(48.8584, 2.2945)
            saveAttributes()
        }

        val prepared = preparer.prepare(source, "image/jpeg", "gps2.jpg", compress = true, limits = open)

        assertEquals("image/webp", prepared.mimeType)
        assertNull(ExifInterface(prepared.file).latLong)
    }

    private fun bounds(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        return options.outWidth to options.outHeight
    }

    private fun jpeg(name: String, width: Int, height: Int): File {
        val pixels = IntArray(width * height) { index ->
            Color.rgb((index % width) * 255 / width, (index / width) * 255 / height, 128)
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return folder.newFile(name).also { file ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            bitmap.recycle()
        }
    }

    private fun noisyPng(name: String, width: Int, height: Int): File {
        val random = Random(7)
        val pixels = IntArray(width * height) { Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)) }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return folder.newFile(name).also { file ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private companion object {
        /** A valid 1x1 GIF. */
        val TINY_GIF = byteArrayOf(
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, 0x80.toByte(), 0x00, 0x00,
            0x00, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x21, 0xF9.toByte(), 0x04, 0x01, 0x00,
            0x00, 0x00, 0x00, 0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44,
            0x01, 0x00, 0x3B,
        )
    }
}
