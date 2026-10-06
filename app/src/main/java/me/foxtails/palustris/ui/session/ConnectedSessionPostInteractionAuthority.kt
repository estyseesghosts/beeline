package me.foxtails.palustris.ui.session

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority

/** Owns the post-mutation authority for one connected account and session revision. */
internal class ConnectedSessionPostInteractionAuthority(
    val accountId: AccountId,
    val sessionRevision: Long,
    val authority: PostInteractionExecutionAuthority,
)
