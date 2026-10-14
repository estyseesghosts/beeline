package me.foxtails.palustris.domain

import me.foxtails.palustris.domain.search.SearchCapabilities

/** Supplied by an adapter and refreshed as the server's feature set changes. */
data class ServerCapabilities(
    val timelines: Set<Timeline> = emptySet(),
    val timelineStatuses: Map<Timeline, CapabilityStatus> = emptyMap(),
    val audiences: Set<Audience> = emptySet(),
    val actions: Set<PostAction> = emptySet(),
    val maxPostLength: Int? = null,
    val posting: PostingCapabilities = PostingCapabilities(),
    val canPublish: Boolean = false,
    val notifications: NotificationCapabilities = NotificationCapabilities(),
    val profile: ProfileCapabilities = ProfileCapabilities(),
    val emoji: EmojiCapabilities = EmojiCapabilities(),
    val capabilitiesLastUpdated: Long = 0,
    val quotes: CapabilityStatus = CapabilityStatus.Unknown,
    val primaryFavourite: PrimaryFavouriteCapability = PrimaryFavouriteCapability(),
    val savedPosts: SavedPostsCapability? = null,
    val likedPosts: CapabilityStatus = CapabilityStatus.Unknown,
    val threads: CapabilityStatus = CapabilityStatus.Unknown,
    val moderation: ModerationCapabilities = ModerationCapabilities(),
    /** Actions on the signed-in account's own posts. A row shows only when its status is Supported. */
    val ownPosts: OwnPostCapabilities = OwnPostCapabilities(),
    /** Server-side translation of a post. Never an on-device fallback. */
    val translation: TranslationCapability = TranslationCapability(),
    /** Whether the account may search post text. */
    val search: SearchCapabilities = SearchCapabilities(),
    /** Lists, followed hashtags and antennas as feed chips. */
    val feedChips: FeedChipCapabilities = FeedChipCapabilities(),
    /** Bumps when capability shapes change; older snapshots force a fresh probe. */
    val capabilitySchemaVersion: Int = CURRENT_CAPABILITY_SCHEMA_VERSION,
    /** Server family from the capability probe. Unknown before the first probe. */
    val flavor: ServerFlavor = ServerFlavor.Unknown,
    /**
     * Mirrors [EmojiCapabilities.reactionMutation]: Supported when the probe found a recognized
     * reaction advertisement. Unknown means the probe has no evidence either way.
     */
    val supportsReactions: CapabilityStatus = CapabilityStatus.Unknown,
    /**
     * Supported when the probe found the custom-emoji reaction advertisement. Unknown means the
     * probe has no evidence either way; never infer it from the server version.
     */
    val supportsCustomReactions: CapabilityStatus = CapabilityStatus.Unknown,
    /**
     * Which report route the probe identified. Pixelfed serves `v1.1/report`; every other
     * family takes `v1/reports`. The value records the send path; it never hides the row.
     */
    val reportRoute: ReportRoute = ReportRoute.V1Reports,
    /**
     * Which emoji-reaction mutation path the probe identified. Unknown before a probe finds
     * evidence or rules the family out.
     */
    val reactionRoute: ReactionRoute = ReactionRoute.Unknown,
    /** Which bubble-timeline path the probe identified. Only Akkoma serves one. */
    val bubbleRoute: BubbleRoute = BubbleRoute.None,
) {
    companion object {
        /**
         * Bump when capability semantics change. Revision 5 invalidates snapshots that
         * recorded reaction support from the removed sentinel mutation probe. Revision 6
         * invalidates snapshots that predate posting limits and upload capabilities. Revision 7
         * invalidates snapshots that predate the quote-with-media rule. Revision 9
         * invalidates snapshots that predate own-post actions and translation. Revision 14
         * invalidates snapshots that predate server-flavor detection and the per-flavor table.
         */
        const val CURRENT_CAPABILITY_SCHEMA_VERSION = 14
    }
}

/**
 * Which server family answered the Mastodon-family probe. The probe reads the instance
 * version compatibility marker once; adapters never re-derive it from hostnames.
 * Unknown means no probe has identified the family yet.
 */
enum class ServerFlavor {
    Mastodon,
    Glitch,
    Hometown,
    Pleroma,
    Akkoma,
    Pixelfed,
    Unknown,
}

/** Which report route the probe identified for this account's server. */
enum class ReportRoute {
    /** `POST /api/v1/reports`. Mastodon, Glitch, Hometown, Pleroma and Akkoma. */
    V1Reports,

    /** `POST /api/v1.1/report`. Pixelfed. */
    V11Report,
}

/** Which emoji-reaction mutation path the probe identified. */
enum class ReactionRoute {
    /** No reaction mutation path: Mastodon-family servers without the extension, and Pixelfed. */
    None,

    /** `PUT /api/v1/pleroma/statuses/:id/reactions/:emoji`. Pleroma and Akkoma. */
    Pleroma,

    /** No probe has identified the path yet. */
    Unknown,
}

/** Which bubble-timeline path the probe identified. Only Akkoma serves one. */
enum class BubbleRoute {
    None,

    /** `GET /api/v1/timelines/bubble`. Akkoma. */
    Akkoma,
}

/** Delete and edit support for the account's own posts. */
data class OwnPostCapabilities(
    val delete: CapabilityStatus = CapabilityStatus.Unknown,
    val edit: CapabilityStatus = CapabilityStatus.Unknown,
)

/**
 * Whether the server translates posts. [publicOnly] is true when it refuses private and direct
 * posts, so the row stays hidden on those.
 */
data class TranslationCapability(
    val status: CapabilityStatus = CapabilityStatus.Unknown,
    val publicOnly: Boolean = false,
)

/** How a server counts a post against its length limit. See [PostLengthCounter]. */
enum class PostLengthRule {
    /** The content warning and text share one limit, counted in grapheme clusters. */
    MastodonCombined,

    /** Only the text counts, in UTF-16 code units. The content warning has its own limit. */
    Utf16TextOnly,
}

/**
 * What a server accepts for a new poll. Expiry bounds are seconds; [maxExpirySeconds] is null when
 * the server sets no upper bound.
 */
data class PollLimits(
    val maxOptions: Int,
    val maxOptionLength: Int,
    val minExpirySeconds: Long,
    val maxExpirySeconds: Long?,
    /** True when the server hides vote totals on request (Mastodon only). */
    val hideTotals: Boolean = false,
) {
    val minOptions: Int get() = MIN_OPTIONS

    companion object {
        const val MIN_OPTIONS = 2
    }
}

/**
 * Posting and media limits that the composer enforces before it contacts the server.
 * A null limit means the server did not report one and the app does not enforce it.
 */
data class PostingCapabilities(
    val lengthRule: PostLengthRule = PostLengthRule.Utf16TextOnly,
    /** Null when the content warning shares the post limit. */
    val maxWarningLength: Int? = null,
    val maxAttachments: Int? = null,
    val maxAltTextLength: Int? = null,
    val maxImageBytes: Long? = null,
    val maxImagePixels: Long? = null,
    /** Largest accepted video file. Null when the server reported no video limit. */
    val maxVideoBytes: Long? = null,
    /** Accepted upload MIME types. Null lets the server decide. */
    val uploadTypes: Set<String>? = null,
    val charactersReservedPerUrl: Int = DEFAULT_CHARACTERS_PER_URL,
    val mediaUpload: CapabilityStatus = CapabilityStatus.Unknown,
    /** True when the server never shrinks an upload, so the app may compress it first. */
    val clientCompression: Boolean = false,
    /** False when the server rejects a post that quotes another post and also carries images. */
    val quoteWithMedia: Boolean = true,
    /** True when the server takes `quote_approval_policy` at creation (Mastodon 4.5 and later). */
    val quoteApprovalPolicy: Boolean = false,
    val localOnly: LocalOnlyRoute = LocalOnlyRoute.None,
    /** Null when the server cannot hold a post for later, or the account may hold none. */
    val scheduling: SchedulingCapability? = null,
    /** Null when the server takes no poll at creation. */
    val poll: PollLimits? = null,
    /**
     * Content types the server accepts at creation, such as `text/plain` and `text/markdown`.
     * Null when the server lists none, in which case the composer sends no `content_type`.
     */
    val postFormats: List<String>? = null,
) {
    /** True when the server may take a video: it lists no types, or lists at least one video type. */
    val acceptsVideo: Boolean get() = uploadTypes == null || uploadTypes.any { it.startsWith("video/") }

    companion object {
        const val DEFAULT_CHARACTERS_PER_URL = 23
    }
}

enum class CapabilityStatus { Supported, Denied, Unsupported, TemporarilyUnavailable, Unknown }

fun capabilityStatus(error: SourceError): CapabilityStatus = when (error) {
    is SourceError.Unsupported, is SourceError.ServerUnsupported, is SourceError.ForeignOrigin,
    SourceError.AccountMismatch -> CapabilityStatus.Unsupported
    is SourceError.AccessDenied, is SourceError.UnsupportedCredential, SourceError.Unauthorized -> CapabilityStatus.Denied
    SourceError.NetworkUnavailable, SourceError.RateLimited, is SourceError.ServerError,
    is SourceError.ResourceLimit -> CapabilityStatus.TemporarilyUnavailable
    is SourceError.InvalidInput -> CapabilityStatus.Unknown
}

enum class ProfileCapability { LikedPosts }

data class ProfileCapabilityQuery(
    val target: AccountId,
    val capability: ProfileCapability,
)

data class ProfileCapabilityResult(val status: CapabilityStatus)

val timelineDisplayOrder: List<Timeline> = listOf(
    Timeline.Home,
    Timeline.Local,
    Timeline.Social,
    Timeline.Bubble,
    Timeline.Federated,
)

fun ServerCapabilities.timelineStatus(timeline: Timeline): CapabilityStatus =
    timelineStatuses[timeline] ?: if (timeline in timelines) CapabilityStatus.Supported else CapabilityStatus.Unknown

fun ServerCapabilities.withTimelineStatuses(statuses: Map<Timeline, CapabilityStatus>): ServerCapabilities = copy(
    timelines = statuses.filterValues { it == CapabilityStatus.Supported }.keys,
    timelineStatuses = statuses,
)

data class ProfileCapabilities(
    val details: CapabilityStatus = CapabilityStatus.Unknown,
    val timelines: CapabilityStatus = CapabilityStatus.Unknown,
    val relationships: CapabilityStatus = CapabilityStatus.Unknown,
    val followActions: CapabilityStatus = CapabilityStatus.Unknown,
    val pinnedPosts: CapabilityStatus = CapabilityStatus.Unknown,
    val editable: EditableProfileCapabilities = EditableProfileCapabilities(),
)

data class EditableProfileCapabilities(
    val read: CapabilityStatus = CapabilityStatus.Unknown,
    val update: CapabilityStatus = CapabilityStatus.Unknown,
    val advancedSettings: CapabilityStatus = CapabilityStatus.Unknown,
    val imageDescriptions: CapabilityStatus = CapabilityStatus.Unknown,
    val imageUpload: CapabilityStatus = CapabilityStatus.Unknown,
    val imageDeletion: CapabilityStatus = CapabilityStatus.Unknown,
)

enum class ReactionSelectionMode { Single, Independent, Unknown }

data class EmojiCapabilities(
    val catalog: CapabilityStatus = CapabilityStatus.Unknown,
    val reactionListing: CapabilityStatus = CapabilityStatus.Unknown,
    val reactionMutation: CapabilityStatus = CapabilityStatus.Unknown,
    val selectionMode: ReactionSelectionMode = ReactionSelectionMode.Unknown,
)

enum class NotificationReadSemantics { PerNotification, AccountWide, TimelineMarker, Unknown }

enum class NotificationUnreadPrecision { Exact, LowerBound, Boolean, Unknown }

enum class NotificationCategory { All, Mentions, Replies, Quotes, Social, Polls, System }

data class NotificationCapabilities(
    val listing: CapabilityStatus = CapabilityStatus.Unknown,
    val supportedCategories: Set<NotificationCategory> = emptySet(),
    val readSemantics: NotificationReadSemantics = NotificationReadSemantics.Unknown,
    val unreadCountPrecision: NotificationUnreadPrecision = NotificationUnreadPrecision.Unknown,
    val grouping: CapabilityStatus = CapabilityStatus.Unknown,
    val dismissal: CapabilityStatus = CapabilityStatus.Unknown,
    val policyManagement: CapabilityStatus = CapabilityStatus.Unknown,
    val followRequestActions: CapabilityStatus = CapabilityStatus.Unknown,
    val streaming: CapabilityStatus = CapabilityStatus.Unknown,
    val webPush: CapabilityStatus = CapabilityStatus.Unknown,
    /**
     * Whether the account can read the server-side unread state. Unsupported when the server
     * has no unread route (Pixelfed has no `unread_count`); Unknown pending a probe or load.
     */
    val unread: CapabilityStatus = CapabilityStatus.Unknown,
)

data class PushProviderInfo(
    val status: CapabilityStatus,
    val vapidPublicKey: String? = null,
)

/** Whether this account can upload media now: the server must support it and the token must allow it. */
fun ServerCapabilities.effectiveMediaUpload(access: AccessGrant): CapabilityStatus =
    effectiveCapabilityStatus(posting.mediaUpload, access.mediaUploadStatus(), implemented = true)

/**
 * True when the reaction picker may offer server custom emoji. Supported means the probe
 * found the custom-emoji advertisement. Akkoma without that advertisement stays optimistic:
 * its API spec accepts custom reactions, so the picker offers them until a custom `PUT`
 * rejection demotes [ServerCapabilities.supportsCustomReactions]. Every other flavor
 * without the advertisement stays unicode-only.
 */
fun ServerCapabilities.allowsCustomReactions(): Boolean = when {
    supportsCustomReactions == CapabilityStatus.Supported -> true
    supportsCustomReactions == CapabilityStatus.Unknown && flavor == ServerFlavor.Akkoma -> true
    else -> false
}

/**
 * Resolves a feature's usable state without treating a failed request as proof of server absence.
 */
fun effectiveCapabilityStatus(
    server: CapabilityStatus,
    access: AccessStatus,
    implemented: Boolean,
): CapabilityStatus = when {
    server == CapabilityStatus.Unsupported -> CapabilityStatus.Unsupported
    access == AccessStatus.Denied || server == CapabilityStatus.Denied -> CapabilityStatus.Denied
    server == CapabilityStatus.TemporarilyUnavailable -> CapabilityStatus.TemporarilyUnavailable
    !implemented -> CapabilityStatus.Unsupported
    server == CapabilityStatus.Unknown || access == AccessStatus.Unknown -> CapabilityStatus.Unknown
    else -> CapabilityStatus.Supported
}
