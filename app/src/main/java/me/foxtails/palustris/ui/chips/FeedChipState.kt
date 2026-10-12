package me.foxtails.palustris.ui.chips

import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.timelineDisplayOrder

/** Why the last hashtag change failed. The add dialog shows it. */
enum class FeedChipError { InvalidHashtag, SaveFailed }

/**
 * The chips that Home and the Photo Grid share, besides the built-in timelines: the account's lists,
 * its hashtags and, on Misskey-family servers, its antennas. Each surface picks its own selected chip.
 */
data class FeedChipState(
    val lists: List<FeedSource.UserList> = emptyList(),
    val hashtags: List<FeedSource.Hashtag> = emptyList(),
    val antennas: List<FeedSource.Antenna> = emptyList(),
    /** Local groups of hashtags searched together. They live on the device on every protocol. */
    val hashtagGroups: List<FeedSource.HashtagGroup> = emptyList(),
    /** Most tags one group may hold. Below two, the add dialog offers no group. */
    val maxGroupTags: Int = 0,
    /** True once the first refresh has answered, so a missing chip is not mistaken for one still loading. */
    val loaded: Boolean = false,
    /** True while a follow, save, unfollow or remove runs. */
    val busy: Boolean = false,
    val error: FeedChipError? = null,
) {
    /** The chips after the built-in timelines, in row order. */
    val extras: List<FeedSource> get() = lists + hashtags + hashtagGroups + antennas

    /** The whole row: [builtIn] timelines in display order, then lists, hashtags and antennas. */
    fun chips(builtIn: Collection<Timeline>): List<FeedSource> =
        timelineDisplayOrder.filter { it in builtIn }.map { FeedSource.Timeline(it) } + extras

    /** True when [feed] is a built-in timeline or one of the catalog's chips. */
    fun offers(feed: FeedSource): Boolean = feed is FeedSource.Timeline || extras.any { it.key == feed.key }
}
