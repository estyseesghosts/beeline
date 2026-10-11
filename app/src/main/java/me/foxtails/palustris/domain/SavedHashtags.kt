package me.foxtails.palustris.domain

import kotlinx.coroutines.flow.Flow

/**
 * The hashtags an account saved on this device. A Misskey-family account uses them as feed chips. The stored
 * JSON key stays `hashtags` in `photo-grid-preferences.json`, so tags saved before the rename survive.
 */
data class SavedHashtags(val tags: List<String> = emptyList())

interface SavedHashtagRepository {
    fun observe(accountId: AccountId): Flow<SavedHashtags>
    suspend fun update(accountId: AccountId, transform: (SavedHashtags) -> SavedHashtags)
    suspend fun remove(accountId: AccountId)
}
