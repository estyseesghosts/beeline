package me.foxtails.palustris.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.foxtails.palustris.data.media.DraftMediaImporter
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.DraftMedia
import me.foxtails.palustris.domain.DraftMediaImportError
import me.foxtails.palustris.domain.DraftMediaImportException
import me.foxtails.palustris.domain.PostDraft

/**
 * Draft payloads use the same encrypted account storage key and never enter the account index.
 * Deleting a draft, deleting an account's drafts, and listing release the matching [DraftMediaStore]
 * files, so a draft row and its image copies leave together. [DraftMediaStore] keeps no in-memory state,
 * so the instance that this store builds and any other instance over the same directory are equivalent.
 */
class EncryptedDraftStore internal constructor(
    context: Context,
    private val accountFiles: AccountFileStore,
    private val media: DraftMediaStore,
) : DraftStore {
    constructor(context: Context) : this(context, AccountFileStore(context), DraftMediaStore(context))

    private val directory = File(context.noBackupFilesDir, "drafts")
    private val importer = DraftMediaImporter(context, media)

    override suspend fun importImage(accountId: AccountId?, draftId: String, source: Uri): DraftMedia {
        val owner = accountId ?: throw DraftMediaImportException(DraftMediaImportError.Unreadable)
        return importer.import(owner, draftId, source)
    }

    override suspend fun loadThumbnail(accountId: AccountId?, draftId: String, mediaId: String, maxEdge: Int): Bitmap? =
        accountId?.let { importer.thumbnail(it, draftId, mediaId, maxEdge) }

    override suspend fun list(accountId: AccountId?): List<PostDraft> = withContext(Dispatchers.IO) {
        val drafts = directory.listFiles().orEmpty().mapNotNull { file ->
            runCatching { accountFiles.readJson(file).toDraft() }.getOrNull()
                ?.takeIf { it.accountId == accountId }
        }.sortedByDescending(PostDraft::updatedAt)
        if (accountId != null) {
            // A directory counts as orphaned only when its draft file is gone, so an unreadable row keeps its images.
            runCatching { media.deleteOrphans(accountId) { draftId -> fileFor(accountId, draftId).exists() } }
        }
        drafts
    }

    override suspend fun save(draft: PostDraft) = withContext(Dispatchers.IO) {
        require(draft.accountId != null) { "A draft must belong to an account before it can be stored." }
        accountFiles.writeJson(fileFor(draft.accountId, draft.id), draft.toJson())
        // Images removed from the draft or its entries leave storage once the row that dropped them is written.
        runCatching { media.retain(draft.accountId, draft.id, draft.allMedia.mapTo(hashSetOf()) { it.id }) }
        Unit
    }

    override suspend fun delete(accountId: AccountId?, draftId: String) = withContext(Dispatchers.IO) {
        AtomicFile(fileFor(accountId, draftId)).delete()
        if (accountId != null) runCatching { media.deleteDraft(accountId, draftId) }
        Unit
    }

    override suspend fun deleteAll(accountId: AccountId?) = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty().forEach { file ->
            runCatching { accountFiles.readJson(file).toDraft() }
                .getOrNull()?.takeIf { it.accountId == accountId }?.let { AtomicFile(file).delete() }
        }
        if (accountId != null) runCatching { media.deleteAccount(accountId) }
        Unit
    }

    override suspend fun migrateLegacy(accountId: AccountId?, preferences: SharedPreferences) {
        if (accountId == null || preferences.getBoolean("migrated_v2", false)) return
        val text = preferences.getString("text", "").orEmpty()
        val warning = preferences.getString("warning", "").orEmpty()
        if (text.isNotBlank() || warning.isNotBlank()) {
            save(PostDraft(accountId = accountId, text = text, contentWarning = warning.takeIf { it.isNotBlank() }))
        }
        preferences.edit().putBoolean("migrated_v2", true).remove("text").remove("warning").apply()
    }

    private fun fileFor(accountId: AccountId?, id: String): File {
        val value = "${accountId?.connection?.origin.orEmpty()}\u0000${accountId?.localId.orEmpty()}\u0000$id"
        val name = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
        return File(directory, "$name.enc")
    }
}
