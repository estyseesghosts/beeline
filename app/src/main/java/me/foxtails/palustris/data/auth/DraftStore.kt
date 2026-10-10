package me.foxtails.palustris.data.auth

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.DraftMedia
import me.foxtails.palustris.domain.DraftMediaImportError
import me.foxtails.palustris.domain.DraftMediaImportException
import me.foxtails.palustris.domain.PostDraft

interface DraftStore {
    suspend fun list(accountId: AccountId?): List<PostDraft>
    suspend fun save(draft: PostDraft)
    suspend fun delete(accountId: AccountId?, draftId: String)
    suspend fun deleteAll(accountId: AccountId?)
    suspend fun migrateLegacy(accountId: AccountId?, preferences: SharedPreferences)

    /** Copies a picked image into the draft's media. Stores without image storage reject it. */
    suspend fun importImage(accountId: AccountId?, draftId: String, source: Uri): DraftMedia =
        throw DraftMediaImportException(DraftMediaImportError.Unreadable)

    /** Decodes a thumbnail of a draft image, or null when the store has none. */
    suspend fun loadThumbnail(accountId: AccountId?, draftId: String, mediaId: String, maxEdge: Int): Bitmap? = null
}

/** Keeps compose-only previews and unit tests deterministic without touching Android Keystore. */
class InMemoryDraftStore : DraftStore {
    private val drafts = linkedMapOf<String, PostDraft>()
    override suspend fun list(accountId: AccountId?): List<PostDraft> = drafts.values.filter { it.accountId == accountId }.sortedByDescending(PostDraft::updatedAt)
    override suspend fun save(draft: PostDraft) { drafts[draft.id] = draft }
    override suspend fun delete(accountId: AccountId?, draftId: String) { drafts.remove(draftId)?.takeIf { it.accountId == accountId } }
    override suspend fun deleteAll(accountId: AccountId?) { drafts.entries.removeIf { it.value.accountId == accountId } }
    override suspend fun migrateLegacy(accountId: AccountId?, preferences: SharedPreferences) {
        if (accountId != null && !preferences.getBoolean("migrated_v2", false)) {
            val text = preferences.getString("text", "").orEmpty()
            val warning = preferences.getString("warning", "").orEmpty()
            if (text.isNotBlank() || warning.isNotBlank()) save(PostDraft(accountId = accountId, text = text, contentWarning = warning.takeIf { it.isNotBlank() }))
            preferences.edit().putBoolean("migrated_v2", true).remove("text").remove("warning").apply()
        }
    }
}

/** Used only when PalustrisApp is rendered directly by previews and UI tests. */
class PreferencesDraftStore(private val preferences: SharedPreferences) : DraftStore {
    private val legacyId = "legacy-local-draft"
    override suspend fun list(accountId: AccountId?): List<PostDraft> {
        val text = preferences.getString("text", "").orEmpty()
        val warning = preferences.getString("warning", "").orEmpty()
        return if (text.isBlank() && warning.isBlank()) emptyList()
        else listOf(PostDraft(legacyId, accountId, text, contentWarning = warning.takeIf { it.isNotBlank() }))
    }
    override suspend fun save(draft: PostDraft) {
        preferences.edit().putString("text", draft.text).putString("warning", draft.contentWarning.orEmpty()).apply()
    }
    override suspend fun delete(accountId: AccountId?, draftId: String) {
        if (draftId == legacyId) preferences.edit().remove("text").remove("warning").apply()
    }
    override suspend fun deleteAll(accountId: AccountId?) = delete(accountId, legacyId)
    override suspend fun migrateLegacy(accountId: AccountId?, preferences: SharedPreferences) = Unit
}
