package me.foxtails.palustris.domain

/**
 * Where a post appears. Mastodon filters name the places they apply to. Misskey word mutes and
 * account mutes apply everywhere.
 */
enum class FilterContext { Home, Notifications, Public, Thread, Account }

/** What happens to a post that a filter matches: it collapses behind a notice, or it leaves the list. */
enum class PostFilterAction { Collapse, Remove }

/**
 * One filter that the server matched against a post. Mastodon 4.0 and later send these on each
 * status. The server matches every active filter, so [contexts] still limits where it applies.
 */
data class ServerFilterMatch(
    val title: String,
    val action: PostFilterAction,
    val contexts: Set<FilterContext> = FilterContext.entries.toSet(),
)

/** The decision for one post on one surface. [reason] is the filter title or the matched word that the notice shows. */
data class PostFilterVerdict(val reason: String, val action: PostFilterAction)

/**
 * The one decision for whether a filter collapses or removes a post. Every surface calls it with
 * its own [FilterContext], so a filter for Home only does not touch a thread.
 */
object PostFilterPolicy {
    fun evaluate(post: Post, context: FilterContext): PostFilterVerdict? =
        verdict(post.serverFilters.filter { context in it.contexts }.map { it.title to it.action })

    /** Remove wins over Collapse. The notice names every match of the winning action. */
    internal fun verdict(matches: List<Pair<String, PostFilterAction>>): PostFilterVerdict? {
        if (matches.isEmpty()) return null
        val action = if (matches.any { it.second == PostFilterAction.Remove }) PostFilterAction.Remove else PostFilterAction.Collapse
        val reason = matches.filter { it.second == action }.map { it.first }.filter(String::isNotBlank).distinct().joinToString(", ")
        return PostFilterVerdict(reason, action)
    }
}

/** Home and lists take Home filters. Every other feed is public: local, federated, hashtags, and antennas. */
fun FeedSource.filterContext(): FilterContext = when (this) {
    is FeedSource.Timeline -> if (timeline == Timeline.Home) FilterContext.Home else FilterContext.Public
    is FeedSource.UserList -> FilterContext.Home
    else -> FilterContext.Public
}
