package me.foxtails.palustris.data.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.foxtails.palustris.domain.AccountId

/**
 * Keeps the image copies of drafts, encrypted with the session key.
 *
 * State owned: files under `noBackupFilesDir/draft-media/<account>/<draft>/<media>`.
 * Lifetime: a file lives until its media leaves the draft, the draft is deleted or published,
 * or the account is removed. [EncryptedDraftStore] calls the release functions on those events,
 * so no other class deletes draft media. A picked file needs its own copy because access to the
 * picked URI ends with the process.
 */
@Singleton
class DraftMediaStore internal constructor(
    context: Context,
    private val accountFiles: AccountFileStore,
    private val orphanGraceMillis: Long = ORPHAN_GRACE_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, AccountFileStore(context))

    private val root = File(context.noBackupFilesDir, "draft-media")

    /** Encrypts [source] into the draft's media directory. Returns the plain byte count. */
    suspend fun write(accountId: AccountId, draftId: String, mediaId: String, source: InputStream): Long =
        withContext(Dispatchers.IO) {
            val target = fileFor(accountId, draftId, mediaId)
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, "${target.name}.${System.nanoTime()}.tmp")
            var written = 0L
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, accountFiles.secretKey())
                FileOutputStream(temporary).use { file ->
                    file.write(cipher.iv)
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        cipher.update(buffer, 0, read)?.let(file::write)
                        written += read
                    }
                    file.write(cipher.doFinal())
                    file.fd.sync()
                }
                java.nio.file.Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                )
            } finally {
                temporary.delete()
            }
            written
        }

    /** Opens the decrypted bytes. The caller closes the stream. A damaged file fails at the end of the read. */
    suspend fun open(accountId: AccountId, draftId: String, mediaId: String): InputStream =
        withContext(Dispatchers.IO) {
            val file = FileInputStream(fileFor(accountId, draftId, mediaId))
            try {
                val iv = ByteArray(IV_BYTES)
                var offset = 0
                while (offset < IV_BYTES) {
                    val read = file.read(iv, offset, IV_BYTES - offset)
                    if (read < 0) throw IOException("Draft media file is truncated.")
                    offset += read
                }
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, accountFiles.secretKey(), GCMParameterSpec(TAG_BITS, iv))
                CipherInputStream(file, cipher)
            } catch (error: Exception) {
                file.close()
                throw error
            }
        }

    suspend fun delete(accountId: AccountId, draftId: String, mediaId: String) = withContext(Dispatchers.IO) {
        fileFor(accountId, draftId, mediaId).delete()
        Unit
    }

    /** Deletes every file of the draft except those in [keep]. Called after the draft row is written. */
    suspend fun retain(accountId: AccountId, draftId: String, keep: Set<String>) = withContext(Dispatchers.IO) {
        val directory = draftDirectory(accountId, draftId)
        directory.listFiles().orEmpty().forEach { file ->
            if (file.name !in keep) file.delete()
        }
        if (directory.listFiles().isNullOrEmpty()) directory.delete()
        Unit
    }

    suspend fun deleteDraft(accountId: AccountId, draftId: String) = withContext(Dispatchers.IO) {
        draftDirectory(accountId, draftId).deleteRecursively()
        Unit
    }

    suspend fun deleteAccount(accountId: AccountId) = withContext(Dispatchers.IO) {
        accountDirectory(accountId).deleteRecursively()
        Unit
    }

    /**
     * Deletes draft directories for which [hasDraft] is false. A directory younger than the
     * grace period stays, because a picked image is copied in before its draft row is first saved.
     */
    suspend fun deleteOrphans(accountId: AccountId, hasDraft: (draftId: String) -> Boolean) =
        withContext(Dispatchers.IO) {
            val now = clock()
            accountDirectory(accountId).listFiles().orEmpty().forEach { directory ->
                if (!hasDraft(directory.name) && now - directory.lastModified() >= orphanGraceMillis) {
                    directory.deleteRecursively()
                }
            }
        }

    private fun accountDirectory(accountId: AccountId): File {
        val value = "${accountId.connection.origin}\u0000${accountId.localId}"
        val name = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
        return File(root, name)
    }

    private fun draftDirectory(accountId: AccountId, draftId: String): File {
        require(SAFE_NAME.matches(draftId)) { "Draft id is not a safe file name." }
        return File(accountDirectory(accountId), draftId)
    }

    private fun fileFor(accountId: AccountId, draftId: String, mediaId: String): File {
        require(SAFE_NAME.matches(mediaId)) { "Media id is not a safe file name." }
        return File(draftDirectory(accountId, draftId), mediaId)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val BUFFER_BYTES = 16 * 1024
        const val ORPHAN_GRACE_MILLIS = 24L * 60 * 60 * 1000
        val SAFE_NAME = Regex("[A-Za-z0-9_-]{1,64}")
    }
}
