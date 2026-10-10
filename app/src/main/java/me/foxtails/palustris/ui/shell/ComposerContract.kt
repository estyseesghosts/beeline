package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.ThreadPublication
import me.foxtails.palustris.domain.ThreadPublishListener

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
) {
    interface Actions {
        fun publish(publication: ThreadPublication, listener: ThreadPublishListener)
    }

    companion object {
        val Empty = ComposerContract(PostPreferences(), emptySet(), false, false, 0, 0, null, ComposerEmptyActions)
    }
}

private object ComposerEmptyActions : ComposerContract.Actions {
    override fun publish(publication: ThreadPublication, listener: ThreadPublishListener) = Unit
}
