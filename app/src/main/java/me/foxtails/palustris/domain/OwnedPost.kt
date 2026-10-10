package me.foxtails.palustris.domain

/** Associates a merged timeline row with the account and session that fetched it. */
data class OwnedPost(
    val fetchedBy: AccountId,
    val post: Post,
    val sessionRevision: Long = 0L,
)

/**
 * True when [accountId] wrote the post this row shows. A repost row carries the wrapped post's
 * author, so a repost of your own post is owned and your repost of someone else's post is not.
 */
fun Post.isOwnedBy(accountId: AccountId): Boolean = author.id == accountId

/**
 * True when the server can translate this post into [targetLanguage] (an ISO 639-1 code). The row
 * hides when the post is already in that language or when the server refuses private posts.
 */
fun Post.canTranslateTo(capability: TranslationCapability, targetLanguage: String): Boolean {
    if (capability.status != CapabilityStatus.Supported) return false
    if (capability.publicOnly && audience != Audience.Public && audience != Audience.Unlisted) return false
    if (text.isBlank() && contentWarning.isNullOrBlank()) return false
    val own = language?.substringBefore('-')?.lowercase()
    return own == null || own != targetLanguage.substringBefore('-').lowercase()
}
