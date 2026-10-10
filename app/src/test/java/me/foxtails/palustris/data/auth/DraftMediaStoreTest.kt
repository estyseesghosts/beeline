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
}
