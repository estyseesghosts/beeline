package me.foxtails.palustris.domain

/** Protocol-neutral access capabilities requested or observed for one account session. */
enum class AccessScope {
    NotificationsRead,
    NotificationsWrite,
    FollowRequests,
    Push,
    PrimaryFavouriteWrite,
    SavedPostsRead,
    SavedPostsWrite,
    LikedPostsRead,
    ModerationRead,
    ModerationWrite,
    MediaUpload,
}

enum class AccessStatus { Granted, Denied, Unknown }

data class AccessGrant(
    val requested: Set<AccessScope> = emptySet(),
    val known: Map<AccessScope, AccessStatus> = emptyMap(),
) {
    fun status(scope: AccessScope): AccessStatus = known[scope] ?: AccessStatus.Unknown

    /**
     * Upload access. A grant with no [AccessScope.MediaUpload] record predates the scope, so its
     * token was never asked for it and cannot upload. The user must sign in again.
     */
    fun mediaUploadStatus(): AccessStatus = known[AccessScope.MediaUpload] ?: AccessStatus.Denied
}
