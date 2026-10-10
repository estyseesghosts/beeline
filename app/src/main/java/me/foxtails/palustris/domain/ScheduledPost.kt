package me.foxtails.palustris.domain

/** How a server takes scheduled posts. The routes differ, so each family has its own value. */
enum class ScheduleRoute {
    /** `scheduled_at` on `POST /api/v1/statuses`, listed by `scheduled_statuses`. Mastodon, Pleroma and Akkoma. */
    Mastodon,

    /** the `notes/schedule` routes. Sharkey. */
    Sharkey,

    /** the `notes/drafts` routes with `scheduledAt` and `isActuallyScheduled`. Misskey. */
    MisskeyDrafts,
}

/**
 * What the server allows for scheduling. [minLeadSeconds] is how far ahead a time must be. [limit] is the
 * most pending scheduled posts the account may hold, or null when the server sets none.
 */
data class SchedulingCapability(
    val route: ScheduleRoute,
    val minLeadSeconds: Long,
    val limit: Int? = null,
)

/** How a server takes the local-only choice. [None] hides the toggle. */
enum class LocalOnlyRoute {
    None,

    /** `localOnly` in `notes/create`. Misskey and Sharkey. */
    MisskeyFlag,

    /** `local_only` in the status form. glitch-soc and Hometown. */
    MastodonFlag,

    /** `visibility=local`. Pleroma and Akkoma, for a public post only. */
    PleromaVisibility,
}

/**
 * A post the server holds for later. [request] carries what is needed to send it again, so a server with
 * no reschedule route (Sharkey) can cancel and recreate it.
 */
data class ScheduledPost(
    val id: String,
    val scheduledAtEpochMillis: Long,
    val text: String,
    val audience: Audience = Audience.Public,
    val contentWarning: String? = null,
    val attachmentCount: Int = 0,
    val hasPoll: Boolean = false,
    val request: CreatePostRequest? = null,
)
