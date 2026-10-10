package me.foxtails.palustris.domain

import kotlinx.coroutines.flow.Flow

const val DEFAULT_FAVOURITE_EMOJI = "❤️"
const val MAX_FAVOURITE_EMOJI_LENGTH = 64

/** Whether images are shrunk on this device before upload. */
enum class UploadCompression { Always, Never, Ask }

data class PostPreferences(
    val favouriteEmoji: String = DEFAULT_FAVOURITE_EMOJI,
    val defaultAudience: Audience = Audience.Public,
    val repliesUnlisted: Boolean = false,
    val contentWarningRules: ContentWarningRules = ContentWarningRules(),
    val localMutedHashtags: List<String> = emptyList(),
    val uploadCompression: UploadCompression = UploadCompression.Always,
)

fun normalizeFavouriteEmoji(value: String?): String = value
    ?.trim()
    ?.takeIf { it.isNotEmpty() && it.length <= MAX_FAVOURITE_EMOJI_LENGTH && it.none(Char::isISOControl) }
    ?: DEFAULT_FAVOURITE_EMOJI

fun normalizeLocalMutedHashtags(values: List<String>): List<String> = values
    .asSequence()
    .map { it.trim().removePrefix("#") }
    .filter(String::isNotEmpty)
    .map(String::lowercase)
    .distinct()
    .take(100)
    .toList()

interface PostPreferencesRepository {
    fun observe(accountId: AccountId): Flow<PostPreferences>
    suspend fun update(accountId: AccountId, transform: (PostPreferences) -> PostPreferences)
    suspend fun remove(accountId: AccountId)
}
