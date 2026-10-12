package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.domain.FeedSource
import me.foxtails.palustris.ui.chips.FeedChipState

/**
 * The shared chip catalog as Home and the Photo Grid see it: lists, hashtags and antennas, plus the
 * actions that change the hashtags. [Empty] is an inert preview value with no extra chips.
 */
data class FeedChipsContract(
    val state: FeedChipState,
    val actions: Actions,
) {
    interface Actions {
        fun refresh()

        /** Follows or saves [value]. [onAdded] receives the chip, new or already present. */
        fun addHashtag(value: String, onAdded: (FeedSource.Hashtag) -> Unit)

        /** Unfollows or removes [tag]. [onRemoved] runs once the chip is gone from the catalog. */
        fun removeHashtag(tag: String, onRemoved: () -> Unit)

        /** Saves [values] on the device as one group named [name], or by its first tag when [name] is blank. */
        fun addHashtagGroup(name: String, values: List<String>, onAdded: (FeedSource.HashtagGroup) -> Unit)

        /** Deletes [group] from the device. */
        fun removeHashtagGroup(group: FeedSource.HashtagGroup, onRemoved: () -> Unit)

        fun clearError()
    }

    companion object {
        val Empty = FeedChipsContract(FeedChipState(loaded = true), FeedChipsEmptyActions)
    }
}

private object FeedChipsEmptyActions : FeedChipsContract.Actions {
    override fun refresh() = Unit
    override fun addHashtag(value: String, onAdded: (FeedSource.Hashtag) -> Unit) = Unit
    override fun removeHashtag(tag: String, onRemoved: () -> Unit) = Unit
    override fun addHashtagGroup(name: String, values: List<String>, onAdded: (FeedSource.HashtagGroup) -> Unit) = Unit
    override fun removeHashtagGroup(group: FeedSource.HashtagGroup, onRemoved: () -> Unit) = Unit
    override fun clearError() = Unit
}
