package me.foxtails.palustris.ui.shell

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.foxtails.palustris.domain.AccessStatus
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.PostLimits
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.ThreadPublication
import me.foxtails.palustris.domain.ThreadPublishListener
import me.foxtails.palustris.domain.effectiveCapabilityStatus
import me.foxtails.palustris.domain.hashtags.HashtagSuggestion

/**
 * Composer capabilities and publication for one connected account.
 *
 * The composer owner holds audience policy, publication state, and the publish operation. Draft
 * persistence is not part of this contract. [Empty] is an inert preview value. [publishPosted]
 * and [publishTotal] report thread progress while [publishing] is true.
 */
data class ComposerContract(
    val postPreferences: PostPreferences,
    val availableAudiences: Set<Audience>,
    val canPublish: Boolean,
    val publishing: Boolean,
    val publishPosted: Int = 0,
    val publishTotal: Int = 0,
    val error: String?,
    val actions: Actions,
    val limits: PostLimits = PostLimits(),
    /** Prepares post text at publish time, for example by removing tracking parameters. */
    val prepareText: (String) -> String = { it },
    /** What the token allows for uploads. The server capability alone cannot say. */
    val mediaAccess: AccessStatus = AccessStatus.Unknown,
    /**
     * Hashtag suggestions for the fragment typed after `#`. Only the fragment is sent to the
     * account server. The default offers none.
     */
    val hashtagSuggestions: (Flow<String>) -> Flow<List<HashtagSuggestion>> = { emptyFlow() },
) {
    /** Whether this account can attach images now: the server must support it and the token must allow it. */
    val mediaUpload: CapabilityStatus
        get() = effectiveCapabilityStatus(limits.posting.mediaUpload, mediaAccess, implemented = true)

    interface Actions {
        fun publish(publication: ThreadPublication, listener: ThreadPublishListener)

        /** Starts the sign-in-again flow that asks for the permissions this session lacks. */
        fun signInAgain() = Unit
    }

    companion object {
        val Empty = ComposerContract(PostPreferences(), emptySet(), false, false, 0, 0, null, ComposerEmptyActions)
    }
}

private object ComposerEmptyActions : ComposerContract.Actions {
    override fun publish(publication: ThreadPublication, listener: ThreadPublishListener) = Unit
}
