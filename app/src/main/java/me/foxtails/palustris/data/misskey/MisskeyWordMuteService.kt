package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.ContentFilter
import me.foxtails.palustris.domain.ContentFilterDraft
import me.foxtails.palustris.domain.ContentFilterInput
import me.foxtails.palustris.domain.ContentFilterKind
import me.foxtails.palustris.domain.ContentFilterSet
import me.foxtails.palustris.domain.FilterExpressions
import me.foxtails.palustris.domain.FilterPattern
import me.foxtails.palustris.domain.PostFilterAction
import me.foxtails.palustris.domain.SourceError
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads and edits the word mutes of one Misskey-family account. It holds no state.
 *
 * `mutedWords` collapse and `hardMutedWords` remove. Each entry is an array of words that must all appear, or a
 * `/source/flags` expression. Every write reads the account again, changes one entry, and sends both lists through
 * `i/update`, so entries that Beeline cannot read stay as they are.
 */
class MisskeyWordMuteService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val maxResponseBytes: Long = MISSKEY_MAX_RESPONSE_BYTES,
) {
    suspend fun filters(): ContentFilterSet = wordMuteSet(account())

    /** Replaces the entry [id], or adds a new one when [id] is null or no longer exists. */
    suspend fun save(id: String?, draft: ContentFilterDraft): ContentFilterSet {
        val pattern = draft.patterns.singleOrNull() ?: throw SourceError.InvalidInput("moderation.filters.save")
        val lists = WordLists.from(account())
        val stored = storedValue(pattern)
        val position = id?.let(lists::remove)
        lists.insert(draft.action, stored, position?.takeIf { it.first == draft.action }?.second)
        return update(lists)
    }

    suspend fun delete(id: String): ContentFilterSet {
        val lists = WordLists.from(account())
        lists.remove(id)
        return update(lists)
    }

    private suspend fun account(): JSONObject = JSONObject(api.post(origin, "i", JSONObject().put("i", token), maxResponseBytes).body)

    private suspend fun update(lists: WordLists): ContentFilterSet {
        val body = JSONObject().put("i", token).put("mutedWords", JSONArray(lists.soft)).put("hardMutedWords", JSONArray(lists.hard))
        val response = try {
            api.post(origin, "i/update", body, maxResponseBytes)
        } catch (failure: ApiFailure) {
            throw when (failure.code?.uppercase()) {
                "INVALID_REGEXP" -> SourceError.InvalidInput(ContentFilterInput.EXPRESSION)
                "TOO_MANY_MUTED_WORDS" -> SourceError.InvalidInput(ContentFilterInput.LIMIT)
                else -> failure
            }
        }
        // i/update answers with the account. Old forks may answer without the lists; read them again then.
        val json = runCatching { JSONObject(response.body) }.getOrNull()?.takeIf { it.has("mutedWords") } ?: account()
        return wordMuteSet(json)
    }

    /** Both lists as raw JSON values, so unknown entries survive a write unchanged. */
    private class WordLists(val soft: MutableList<Any>, val hard: MutableList<Any>) {
        /** Removes the entry [id] and returns its list and index, or null when it is gone. */
        fun remove(id: String): Pair<PostFilterAction, Int>? {
            val action = actionOf(id) ?: return null
            val list = list(action)
            val index = list.indexOfFirst { entryId(action, it) == id }
            if (index < 0) return null
            list.removeAt(index)
            return action to index
        }

        fun insert(action: PostFilterAction, value: Any, index: Int?) {
            val list = list(action)
            if (index != null && index <= list.size) list.add(index, value) else list.add(value)
        }

        private fun list(action: PostFilterAction) = if (action == PostFilterAction.Remove) hard else soft

        companion object {
            fun from(account: JSONObject) = WordLists(raw(account.optJSONArray("mutedWords")), raw(account.optJSONArray("hardMutedWords")))

            private fun raw(values: JSONArray?): MutableList<Any> = (0 until (values?.length() ?: 0)).mapNotNull { values?.opt(it) }.toMutableList()
        }
    }

    internal companion object {
        fun wordMuteSet(account: JSONObject): ContentFilterSet = ContentFilterSet(
            ContentFilterKind.WordMute,
            entries(account.optJSONArray("mutedWords"), PostFilterAction.Collapse) + entries(account.optJSONArray("hardMutedWords"), PostFilterAction.Remove),
        )

        private fun entries(values: JSONArray?, action: PostFilterAction): List<ContentFilter> =
            (0 until (values?.length() ?: 0)).mapNotNull { index -> values?.opt(index)?.let { entry(it, action) } }

        /**
         * An array entry gives words that must all appear, matched with capitalization. A string gives an expression.
         * A `/literal/i` expression shows as its literal, since Beeline writes case-insensitive words that way.
         */
        private fun entry(value: Any, action: PostFilterAction): ContentFilter? {
            val pattern: FilterPattern = when (value) {
                is JSONArray -> (0 until value.length()).map { value.optString(it) }.filter(String::isNotEmpty)
                    .takeIf { it.isNotEmpty() }?.let { FilterPattern.Words(it, ignoreCase = false) } ?: return null
                is String -> FilterExpressions.parse(value) ?: return null
                else -> return null
            }
            return ContentFilter(id = entryId(action, value)!!, title = title(pattern), patterns = listOf(pattern), action = action)
        }

        private fun title(pattern: FilterPattern): String = when (pattern) {
            is FilterPattern.Words -> pattern.words.joinToString(" ")
            is FilterPattern.Expression -> pattern.takeIf { it.flags == "i" }?.let { FilterExpressions.literalOrNull(it.source) }
                ?: FilterExpressions.format(pattern)
        }

        /** Case-sensitive words become a one-word array. Case-insensitive words become `/escaped/i`. */
        fun storedValue(pattern: FilterPattern): Any = when (pattern) {
            is FilterPattern.Words -> if (pattern.ignoreCase) {
                "/${FilterExpressions.escapeLiteral(pattern.words.joinToString(" "))}/i"
            } else {
                JSONArray(pattern.words)
            }
            is FilterPattern.Expression -> FilterExpressions.format(pattern)
        }

        /** The opaque ID of one entry: its list and its JSON. Equal entries in one list share an ID. */
        fun entryId(action: PostFilterAction, value: Any): String? {
            val json = when (value) {
                is JSONArray -> value.toString()
                is String -> JSONObject.quote(value)
                else -> return null
            }
            return (if (action == PostFilterAction.Remove) "h:" else "s:") + json
        }

        private fun actionOf(id: String): PostFilterAction? = when {
            id.startsWith("h:") -> PostFilterAction.Remove
            id.startsWith("s:") -> PostFilterAction.Collapse
            else -> null
        }
    }
}
