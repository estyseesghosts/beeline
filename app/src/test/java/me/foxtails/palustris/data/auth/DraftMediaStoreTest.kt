package me.foxtails.palustris.data.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.DraftMedia
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.PostDraftEntry
import me.foxtails.palustris.domain.Protocol
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DraftMediaStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val accountFiles = AccountFileStore(context, key)
    private val media = DraftMediaStore(context, accountFiles, orphanGraceMillis = 0L)
    private val drafts = EncryptedDraftStore(context, accountFiles, media)
    private val account = AccountId(Connection("https://example.org", Protocol.MASTODON), "owner")
    private val other = AccountId(Connection("https://example.org", Protocol.MASTODON), "other")

    @Before
    fun clean() {
        File(context.noBackupFilesDir, "draft-media").deleteRecursively()
        File(context.noBackupFilesDir, "drafts").deleteRecursively()
    }

    @After
    fun cleanUp() = clean()

    private fun mediaFile(draft: String, id: String): File? =
        File(context.noBackupFilesDir, "draft-media").walkTopDown().firstOrNull { it.isFile && it.name == id && it.parentFile?.name == draft }

    private fun mediaCount(): Int =
        File(context.noBackupFilesDir, "draft-media").walkTopDown().count { it.isFile }

    private fun write(owner: AccountId, draft: String, id: String, bytes: ByteArray = id.toByteArray()) =
        runBlocking { media.write(owner, draft, id, ByteArrayInputStream(bytes)) }

    @Test
    fun writtenBytesRoundTripAndAreNotStoredInTheClear() = runBlocking {
        val plain = ByteArray(40_000) { (it % 251).toByte() }
        assertEquals(40_000L, media.write(account, "d1", "m1", ByteArrayInputStream(plain)))

        val stored = mediaFile("d1", "m1")!!.readBytes()
        assertFalse(stored.toList().windowed(16).any { it == plain.take(16) })
        assertArrayEquals(plain, media.open(account, "d1", "m1").use { it.readBytes() })
    }

    @Test
    fun aDamagedFileFailsInsteadOfReturningBytes() = runBlocking {
        write(account, "d1", "m1", ByteArray(100) { 7 })
        val file = mediaFile("d1", "m1")!!
        file.writeBytes(file.readBytes().also { it[it.size - 3] = (it[it.size - 3] + 1).toByte() })

        val failed = runCatching { media.open(account, "d1", "m1").use { it.readBytes() } }.isFailure
        assertTrue(failed)
    }

    @Test
    fun unsafeNamesAreRejected() {
        val failed = runCatching { write(account, "../d1", "m1") }.isFailure
        assertTrue(failed)
        assertTrue(runCatching { write(account, "d1", "..") }.isFailure)
    }

    @Test
    fun aDraftWithThreeEntriesRoundTripsItsMedia() = runBlocking {
        val draft = PostDraft(
            id = "d1",
            accountId = account,
            text = "one",
            media = listOf(DraftMedia("m1", "image/webp", 10, 20, 123L, "alt one")),
            followUps = listOf(
                PostDraftEntry("e2", "two", "cw", listOf(DraftMedia("m2", "image/png", null, null, 5L, null))),
                PostDraftEntry("e3", "three"),
            ),
        )
        drafts.save(draft)

        val loaded = drafts.list(account).single()
        assertEquals(draft.media, loaded.media)
        assertEquals(draft.followUps, loaded.followUps)
    }

    @Test
    fun aDraftSavedBeforeThreadsLoadsAsOneEntryWithoutMedia() = runBlocking {
        val legacy = JSONObject()
            .put("id", "old")
            .put("origin", account.connection.origin)
            .put("localId", account.localId)
            .put("protocol", account.connection.protocol.name)
            .put("text", "plain draft")
            .put("audience", "Public")
            .put("updatedAt", 5L)
            .put("attachments", JSONArray())
        accountFiles.writeJson(File(context.noBackupFilesDir, "drafts/old.enc"), legacy)

        val loaded = drafts.list(account).single()
        assertEquals("plain draft", loaded.text)
        assertTrue(loaded.media.isEmpty())
        assertTrue(loaded.followUps.isEmpty())
    }

    @Test
    fun deletingADraftDeletesItsMedia() = runBlocking {
        write(account, "d1", "m1")
        write(account, "d2", "m2")
        drafts.save(PostDraft(id = "d1", accountId = account, text = "x", media = listOf(DraftMedia("m1", "image/png"))))
        drafts.save(PostDraft(id = "d2", accountId = account, text = "y", media = listOf(DraftMedia("m2", "image/png"))))

        drafts.delete(account, "d1")

        assertNull(mediaFile("d1", "m1"))
        assertTrue(mediaFile("d2", "m2") != null)
    }

    @Test
    fun savingADraftDropsMediaItNoLongerLists() = runBlocking {
        write(account, "d1", "m1")
        write(account, "d1", "m2")
        drafts.save(PostDraft(id = "d1", accountId = account, text = "x", media = listOf(DraftMedia("m1", "image/png"))))

        assertTrue(mediaFile("d1", "m1") != null)
        assertNull(mediaFile("d1", "m2"))
    }

    @Test
    fun removingAnAccountDeletesItsMediaOnly() = runBlocking {
        write(account, "d1", "m1")
        write(other, "d9", "m9")
        drafts.save(PostDraft(id = "d1", accountId = account, text = "x", media = listOf(DraftMedia("m1", "image/png"))))

        drafts.deleteAll(account)

        assertNull(mediaFile("d1", "m1"))
        assertEquals(1, mediaCount())
    }

    @Test
    fun listingRemovesDirectoriesWithoutADraftRow() = runBlocking {
        write(account, "kept", "m1")
        write(account, "orphan", "m2")
        drafts.save(PostDraft(id = "kept", accountId = account, text = "x", media = listOf(DraftMedia("m1", "image/png"))))

        drafts.list(account)

        assertTrue(mediaFile("kept", "m1") != null)
        assertNull(mediaFile("orphan", "m2"))
    }

    @Test
    fun aFreshOrphanSurvivesTheGracePeriod() = runBlocking {
        val graced = DraftMediaStore(context, accountFiles, orphanGraceMillis = 60_000L)
        graced.write(account, "pending", "m1", ByteArrayInputStream(byteArrayOf(1)))

        graced.deleteOrphans(account) { false }

        assertTrue(mediaFile("pending", "m1") != null)
    }

    private fun roundTrip(size: Int) = runBlocking {
        val plain = ByteArray(size) { (it * 31 % 251).toByte() }
        assertEquals(size.toLong(), media.write(account, "big", "m$size", ByteArrayInputStream(plain)))
        assertArrayEquals(plain, media.open(account, "big", "m$size").use { it.readBytes() })
    }

    @Test
    fun aLargeFileSpansSeveralChunksAndRoundTrips() = roundTrip(DraftMediaCipher.CHUNK_BYTES * 2 + 12_345)

    @Test
    fun aFileOfExactlyWholeChunksRoundTrips() {
        roundTrip(DraftMediaCipher.CHUNK_BYTES)
        roundTrip(DraftMediaCipher.CHUNK_BYTES * 3)
    }

    @Test
    fun anEmptyFileRoundTrips() = roundTrip(0)

    @Test
    fun aDroppedFinalChunkIsDetected() = runBlocking {
        write(account, "d1", "m1", ByteArray(DraftMediaCipher.CHUNK_BYTES * 2 + 10) { 3 })
        val file = mediaFile("d1", "m1")!!
        val record = 12 + DraftMediaCipher.CHUNK_BYTES + 16
        file.writeBytes(file.readBytes().copyOf(16 + record * 2))

        assertTrue(runCatching { media.open(account, "d1", "m1").use { it.readBytes() } }.isFailure)
    }

    @Test
    fun reorderedChunksAreDetected() = runBlocking {
        write(account, "d1", "m1", ByteArray(DraftMediaCipher.CHUNK_BYTES * 2 + 10) { (it % 7).toByte() })
        val file = mediaFile("d1", "m1")!!
        val bytes = file.readBytes()
        val record = 12 + DraftMediaCipher.CHUNK_BYTES + 16
        val swapped = bytes.copyOfRange(0, 16) + bytes.copyOfRange(16 + record, 16 + 2 * record) +
            bytes.copyOfRange(16, 16 + record) + bytes.copyOfRange(16 + 2 * record, bytes.size)
        file.writeBytes(swapped)

        assertTrue(runCatching { media.open(account, "d1", "m1").use { it.readBytes() } }.isFailure)
    }

    @Test
    fun aFileInTheOlderSingleMessageFormatStillOpens() = runBlocking {
        val plain = ByteArray(5_000) { (it % 13).toByte() }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply { init(javax.crypto.Cipher.ENCRYPT_MODE, key) }
        val legacy = cipher.iv + cipher.doFinal(plain)
        write(account, "d1", "seed", ByteArray(1))
        val file = mediaFile("d1", "seed")!!
        file.writeBytes(legacy)

        assertArrayEquals(plain, media.open(account, "d1", "seed").use { it.readBytes() })
    }

    @Test
    fun aLargeFileInTheOlderFormatFailsAtOnceInsteadOfStallingTheApp() = runBlocking {
        write(account, "d1", "seed", ByteArray(1))
        val file = mediaFile("d1", "seed")!!
        file.writeBytes(ByteArray(9 * 1024 * 1024) { 1 })

        assertTrue(runCatching { media.open(account, "d1", "seed").use { it.readBytes() } }.isFailure)
    }
}
