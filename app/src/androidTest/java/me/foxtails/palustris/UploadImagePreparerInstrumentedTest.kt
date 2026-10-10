package me.foxtails.palustris

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.media.ImageDoesNotFit
import me.foxtails.palustris.data.media.UploadImageLimits
import me.foxtails.palustris.data.media.UploadImagePreparer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the preparer against the device's real image codecs. */
@RunWith(AndroidJUnit4::class)
class UploadImagePreparerInstrumentedTest {
    private lateinit var directory: File
    private lateinit var preparer: UploadImagePreparer

    @Before
    fun setUp() {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        directory = File(cache, "upload-prep-test").also { it.deleteRecursively(); it.mkdirs() }
        preparer = UploadImagePreparer(File(directory, "work"))
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun largeJpegBecomesWebpAtMostTwoThousandPixels() = runBlocking {
        val source = jpeg("big.jpg", 4000, 3000)

        val prepared = preparer.prepare(source, "image/jpeg", "big.jpg", true, UploadImageLimits())

        assertEquals("image/webp", prepared.mimeType)
        val size = bounds(prepared.file)
        assertEquals(2000, maxOf(size.first, size.second))
        assertTrue(prepared.file.length() < source.length())
        prepared.release()
    }

    @Test
    fun orientationRotatesTheReEncodedImage() = runBlocking {
        val source = jpeg("rot.jpg", 400, 200)
        ExifInterface(source).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        val prepared = preparer.prepare(source, "image/jpeg", "rot.jpg", true, UploadImageLimits())

        assertEquals(200 to 400, bounds(prepared.file))
        prepared.release()
    }

    @Test
    fun locationIsRemovedFromTheUploadCopyOnly() = runBlocking {
        val source = jpeg("gps.jpg", 300, 200)
        ExifInterface(source).apply {
            setLatLong(48.8584, 2.2945)
            saveAttributes()
        }

        val prepared = preparer.prepare(source, "image/jpeg", "gps.jpg", false, UploadImageLimits())

        assertNull(ExifInterface(prepared.file).latLong)
        assertTrue(ExifInterface(source).latLong != null)
        prepared.release()
    }

    @Test
    fun imageThatCannotFitFailsWithoutLeavingFiles() {
        val source = jpeg("hopeless.jpg", 800, 600)

        assertThrows(ImageDoesNotFit::class.java) {
            runBlocking { preparer.prepare(source, "image/jpeg", "hopeless.jpg", false, UploadImageLimits(maxBytes = 100)) }
        }
        assertTrue(File(directory, "work").listFiles().orEmpty().isEmpty())
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
        return File(directory, name).also { file ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            bitmap.recycle()
        }
    }
}
