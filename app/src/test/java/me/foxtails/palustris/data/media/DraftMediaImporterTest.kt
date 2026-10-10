package me.foxtails.palustris.data.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.crypto.KeyGenerator
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.auth.AccountFileStore
import me.foxtails.palustris.data.auth.DraftMediaStore
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.DraftMediaImportError
import me.foxtails.palustris.domain.DraftMediaImportException
import me.foxtails.palustris.domain.Protocol
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DraftMediaImporterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val store = DraftMediaStore(context, AccountFileStore(context, key))
    private val account = AccountId(Connection("https://example.org", Protocol.MASTODON), "owner")

    @Before
    fun clean() {
        File(context.noBackupFilesDir, "draft-media").deleteRecursively()
        File(context.cacheDir, "draft-import").deleteRecursively()
    }

    @After
    fun cleanUp() = clean()

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun register(name: String, bytes: ByteArray): Uri {
        val uri = Uri.parse("content://picked/$name")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        return uri
    }

    private fun rejection(block: suspend () -> Unit): DraftMediaImportError {
        try {
            runBlocking { block() }
        } catch (error: DraftMediaImportException) {
            return error.reason
        }
        fail("The import should have been rejected.")
        throw AssertionError()
    }

    @Test
    fun importCopiesTheImageIntoEncryptedDraftStorageWithItsFacts() = runBlocking {
        val bytes = png(40, 30)
        val importer = DraftMediaImporter(context, store)
        val media = importer.import(account, "draft-1", register("ok", bytes))

        assertEquals("image/png", media.mimeType)
        assertEquals(40, media.width)
        assertEquals(30, media.height)
        assertEquals(bytes.size.toLong(), media.byteSize)
        assertArrayEquals(bytes, store.open(account, "draft-1", media.id).use { it.readBytes() })
        val leftovers = File(context.cacheDir, "draft-import").listFiles().orEmpty().map { it.name }
        // Robolectric's native decoder keeps the file open on Windows, so only other systems can check this.
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) {
            assertTrue("the temporary copy is gone: $leftovers", leftovers.isEmpty())
        }
    }

    @Test
    fun aFileThatIsNotAnImageIsRejected() {
        val importer = DraftMediaImporter(context, store)
        val reason = rejection { importer.import(account, "d", register("text", "not an image".toByteArray())) }
        assertEquals(DraftMediaImportError.NotAnImage, reason)
    }

    @Test
    fun anEmptyFileIsRejected() {
        val importer = DraftMediaImporter(context, store)
        assertEquals(DraftMediaImportError.NotAnImage, rejection { importer.import(account, "d", register("empty", ByteArray(0))) })
    }

    @Test
    fun aFileOverTheGuardIsRejectedAsTooLarge() {
        val bytes = png(40, 30)
        val importer = DraftMediaImporter(context, store, maxBytes = bytes.size - 1L)
        assertEquals(DraftMediaImportError.TooLarge, rejection { importer.import(account, "d", register("big", bytes)) })
    }

    @Test
    fun anUnreadableUriIsRejected() {
        val importer = DraftMediaImporter(context, store)
        assertEquals(DraftMediaImportError.Unreadable, rejection { importer.import(account, "d", Uri.parse("content://picked/missing")) })
    }

    @Test
    fun aRejectedImportLeavesNoDraftFile() {
        val importer = DraftMediaImporter(context, store)
        rejection { importer.import(account, "draft-2", register("text", "nope".toByteArray())) }
        assertNull(File(context.noBackupFilesDir, "draft-media").listFiles()?.firstOrNull())
    }

    @Test
    fun thumbnailDecodesWithinTheEdgeAndIsNullForAMissingImage() = runBlocking {
        val importer = DraftMediaImporter(context, store)
        val media = importer.import(account, "draft-3", register("big", png(800, 400)))

        val thumbnail = importer.thumbnail(account, "draft-3", media.id, maxEdge = 200)
        assertNotNull(thumbnail)
        assertTrue(maxOf(thumbnail!!.width, thumbnail.height) <= 400)
        assertNull(importer.thumbnail(account, "draft-3", "missing", maxEdge = 200))
    }
}
