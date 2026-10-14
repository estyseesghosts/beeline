package me.foxtails.palustris.domain

/** What happens to posts of muted and blocked accounts that the server still sends. An app-wide preference. */
data class AccountPostActions(
    val muted: PostFilterAction = PostFilterAction.Collapse,
    val blocked: PostFilterAction = PostFilterAction.Remove,
)

/**
 * Hides posts of accounts that the viewer muted or blocked, wherever the server still sends them: threads, quotes,
 * search, and notifications. A stateless value; the session owner builds a new one when a list changes.
 *
 * A post matches when its author or the account that boosted it is in a list. [mutedLabel] and [blockedLabel] are
 * the localized notice texts. A boost matches when the account that boosted it is in [boostMuted]; those posts
 * always remove, and an opened post collapses instead through the non-removable rule. Profiles never apply it,
 * because a profile shows the posts of the account that the viewer opened on purpose.
 */
data class AccountFilter(
    val muted: Set<AccountId> = emptySet(),
    val blocked: Set<AccountId> = emptySet(),
    val mutedAction: PostFilterAction = PostFilterAction.Collapse,
    val blockedAction: PostFilterAction = PostFilterAction.Remove,
    val mutedLabel: String = "",
    val blockedLabel: String = "",
    val boostMuted: Set<AccountId> = emptySet(),
    val boostMutedLabel: String = "",
) {
    fun matches(post: Post, context: FilterContext): List<Pair<String, PostFilterAction>> {
        if (context == FilterContext.Account || (muted.isEmpty() && blocked.isEmpty() && boostMuted.isEmpty())) return emptyList()
        val people = listOfNotNull(post.author.id, post.resharedBy?.id)
        return buildList {
            if (people.any { it in blocked }) add(blockedLabel to blockedAction)
            if (people.any { it in muted }) add(mutedLabel to mutedAction)
            val booster = post.resharedBy?.id
            if (booster != null && booster in boostMuted) add(boostMutedLabel to PostFilterAction.Remove)
        }
    }

    companion object {
        val None = AccountFilter()
    }
}
