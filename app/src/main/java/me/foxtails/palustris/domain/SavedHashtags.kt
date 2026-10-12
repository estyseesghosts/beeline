package me.foxtails.palustris.domain

import kotlinx.coroutines.flow.Flow

/**
 * The hashtags an account saved on this device. A Misskey-family account uses them as feed chips. The stored
 * JSON key stays `hashtags` in `photo-grid-preferences.json`, so tags saved before the rename survive.
 */
data class SavedHashtags(
    val tags: List<String> = emptyList(),
    /** Local hashtag groups, each searched as one feed. */
    val groups: List<SavedHashtagGroup> = emptyList(),
)

/** One local hashtag group: a [name] for its chip and the tag bodies it searches together. */
data class SavedHashtagGroup(val name: String, val tags: List<String>)

interface SavedHashtagRepository {
    fun observe(accountId: AccountId): Flow<SavedHashtags>
    suspend fun update(accountId: AccountId, transform: (SavedHashtags) -> SavedHashtags)
    suspend fun remove(accountId: AccountId)
}
