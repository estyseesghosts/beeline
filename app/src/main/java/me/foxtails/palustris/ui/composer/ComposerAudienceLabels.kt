package me.foxtails.palustris.ui.composer

import androidx.annotation.StringRes
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Audience

@get:StringRes
internal val Audience.labelRes: Int
    get() = when (this) {
        Audience.Public -> R.string.audience_everyone
        Audience.Unlisted -> R.string.audience_unlisted
        Audience.Followers -> R.string.audience_followers
        Audience.Direct -> R.string.audience_direct
    }

@get:StringRes
internal val Audience.descriptionRes: Int
    get() = when (this) {
        Audience.Public -> R.string.audience_everyone_description
        Audience.Unlisted -> R.string.audience_unlisted_description
        Audience.Followers -> R.string.audience_followers_description
        Audience.Direct -> R.string.audience_direct_description
    }
