package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.domain.FilterContext
import me.foxtails.palustris.domain.PostFilterAction
import me.foxtails.palustris.domain.ServerFilterMatch
import org.json.JSONArray

/** Maps Mastodon filter JSON to protocol-neutral filter values. */
internal object MastodonFilterMapper {
    /**
     * Reads the `filtered` array of a status. Each entry is a `FilterResult` with its `filter`.
     * An entry without a filter object is skipped; a filter without known contexts applies everywhere.
     */
    fun matches(values: JSONArray?): List<ServerFilterMatch> {
        if (values == null) return emptyList()
        return (0 until values.length()).mapNotNull { index ->
            val filter = values.optJSONObject(index)?.optJSONObject("filter") ?: return@mapNotNull null
            ServerFilterMatch(
                title = filter.optString("title"),
                action = action(filter.optString("filter_action")),
                contexts = contexts(filter.optJSONArray("context")),
            )
        }
    }

    /** `hide` removes the post. `warn`, `blur` (4.4), and unknown future actions collapse it, which never exposes it. */
    fun action(value: String): PostFilterAction = if (value == "hide") PostFilterAction.Remove else PostFilterAction.Collapse

    fun contexts(values: JSONArray?): Set<FilterContext> {
        val names = (0 until (values?.length() ?: 0)).mapNotNull { values?.optString(it) }
        return names.mapNotNull(::context).toSet().ifEmpty { FilterContext.entries.toSet() }
    }

    fun context(name: String): FilterContext? = when (name) {
        "home" -> FilterContext.Home
        "notifications" -> FilterContext.Notifications
        "public" -> FilterContext.Public
        "thread" -> FilterContext.Thread
        "account" -> FilterContext.Account
        else -> null
    }

    fun contextName(context: FilterContext): String = when (context) {
        FilterContext.Home -> "home"
        FilterContext.Notifications -> "notifications"
        FilterContext.Public -> "public"
        FilterContext.Thread -> "thread"
        FilterContext.Account -> "account"
    }
}
