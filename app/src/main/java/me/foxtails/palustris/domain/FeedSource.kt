package me.foxtails.palustris.domain

/** Where a saved hashtag chip lives: followed on the server (Mastodon) or saved on the device. */
enum class HashtagOrigin { Server, Device }

/**
 * One source of posts that Home or the Photo Grid can show, and one chip in their shared row.
 * Compare two sources with [key], because a title or origin can change while the source stays the same.
 */
sealed interface FeedSource {
    /** Stable identity across renames. */
    val key: String

    data class Timeline(val timeline: me.foxtails.palustris.domain.Timeline) : FeedSource {
        override val key: String get() = "timeline:${timeline.name}"
    }

    data class UserList(val id: String, val title: String) : FeedSource {
        override val key: String get() = "list:$id"
    }

    data class Hashtag(val tag: String, val origin: HashtagOrigin) : FeedSource {
        override val key: String get() = "hashtag:${hashtagIdentity(tag)}"
    }

    data class Antenna(val id: String, val name: String) : FeedSource {
        override val key: String get() = "antenna:$id"
    }
}

/** True when both sources name the same feed. */
fun FeedSource.sameFeedAs(other: FeedSource): Boolean = key == other.key

/** Which chip kinds the server offers, so a kind it lacks never appears. */
data class FeedChipCapabilities(
    val lists: CapabilityStatus = CapabilityStatus.Unknown,
    /** Hashtags the account follows on the server. Unsupported means Beeline saves tags on the device. */
    val followedTags: CapabilityStatus = CapabilityStatus.Unknown,
    val antennas: CapabilityStatus = CapabilityStatus.Unknown,
)
