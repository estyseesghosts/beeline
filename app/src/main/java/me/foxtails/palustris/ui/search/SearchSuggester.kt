package me.foxtails.palustris.ui.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.search.SearchQuery
import me.foxtails.palustris.domain.search.SearchSuggestions
import me.foxtails.palustris.domain.search.classifySearchQuery

/**
 * Turns the text of the field into suggestions. A quiet period of [debounceMillis] passes before it asks the
 * source. A generation counter drops a slow answer when newer text has arrived. A blank text or a link
 * clears the suggestions at once, and a failed call leaves an empty panel instead of an error.
 */
internal class SearchSuggester(
    private val source: SocialSource,
    private val scope: CoroutineScope,
    private val debounceMillis: Long,
    private val onAnswer: (text: String, suggestions: SearchSuggestions) -> Unit,
) {
    private var job: Job? = null
    private var generation = 0L

    fun suggest(text: String) {
        cancel()
        val requestGeneration = generation
        val parsed = classifySearchQuery(text)
        if (parsed is SearchQuery.Blank || parsed is SearchQuery.Url) {
            onAnswer("", SearchSuggestions())
            return
        }
        job = scope.launch {
            delay(debounceMillis)
            val answer = try {
                source.searchSuggestions(parsed).limited()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                SearchSuggestions()
            }
            if (requestGeneration == generation) onAnswer(parsed.text, answer)
        }
    }

    /** Drops any request in flight and any answer still to come. */
    fun cancel() {
        job?.cancel()
        generation++
    }
}
