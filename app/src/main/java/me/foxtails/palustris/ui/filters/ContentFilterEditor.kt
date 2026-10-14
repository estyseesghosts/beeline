package me.foxtails.palustris.ui.filters

import me.foxtails.palustris.domain.ContentFilter
import me.foxtails.palustris.domain.ContentFilterDraft
import me.foxtails.palustris.domain.ContentFilterKind
import me.foxtails.palustris.domain.FilterContext
import me.foxtails.palustris.domain.FilterPattern
import me.foxtails.palustris.domain.PostFilterAction

/** How long a new or edited filter lasts. [Keep] keeps the remaining time of the filter being edited. */
enum class FilterExpiry(val seconds: Long?) {
    Keep(null),
    Never(null),
    HalfHour(30 * 60L),
    Hour(60 * 60L),
    SixHours(6 * 60 * 60L),
    TwelveHours(12 * 60 * 60L),
    Day(24 * 60 * 60L),
    Week(7 * 24 * 60 * 60L),
}

/** One keyword row of a titled or phrase filter. */
data class KeywordDraft(val text: String = "", val wholeWord: Boolean = false)

enum class FilterEditorError { MissingTitle, MissingKeyword, MissingContext }

/**
 * The editor of one filter, as plain values. A [ContentFilterKind.Titled] filter has a title and several keywords.
 * A [ContentFilterKind.Phrase] filter has one keyword, which is also its title. [remainingSeconds] is the time
 * left on the edited filter for [FilterExpiry.Keep].
 */
data class ContentFilterEditor(
    val kind: ContentFilterKind,
    val editingId: String? = null,
    val title: String = "",
    val keywords: List<KeywordDraft> = listOf(KeywordDraft()),
    val action: PostFilterAction = PostFilterAction.Collapse,
    val contexts: Set<FilterContext> = FilterContext.entries.toSet(),
    val expiry: FilterExpiry = FilterExpiry.Never,
    val remainingSeconds: Long? = null,
) {
    /** The draft to save, or the first problem that blocks saving. */
    fun validate(): EditorResult {
        val words = keywords.filter { it.text.isNotBlank() }.distinctBy { it.text.trim().lowercase() }
        val phrase = kind == ContentFilterKind.Phrase
        if (!phrase && title.isBlank()) return EditorResult.Invalid(FilterEditorError.MissingTitle)
        if (words.isEmpty()) return EditorResult.Invalid(FilterEditorError.MissingKeyword)
        if (contexts.isEmpty()) return EditorResult.Invalid(FilterEditorError.MissingContext)
        val kept = if (phrase) words.take(1) else words
        return EditorResult.Valid(
            ContentFilterDraft(
                title = if (phrase) kept.single().text.trim() else title.trim(),
                patterns = kept.map { FilterPattern.Words(listOf(it.text.trim()), wholeWord = it.wholeWord) },
                action = action,
                contexts = contexts,
                expiresInSeconds = if (expiry == FilterExpiry.Keep) remainingSeconds else expiry.seconds,
            ),
        )
    }

    /** The expiry choices: [FilterExpiry.Keep] only while an expiring filter is edited. */
    val expiryChoices: List<FilterExpiry>
        get() = FilterExpiry.entries.filter { it != FilterExpiry.Keep || remainingSeconds != null }

    fun keyword(index: Int, value: KeywordDraft): ContentFilterEditor =
        copy(keywords = keywords.mapIndexed { position, old -> if (position == index) value else old })

    fun addKeyword(): ContentFilterEditor = copy(keywords = keywords + KeywordDraft())

    /** Removes the keyword at [index]. The last row stays, emptied, so the form always has one field. */
    fun removeKeyword(index: Int): ContentFilterEditor =
        copy(keywords = keywords.filterIndexed { position, _ -> position != index }.ifEmpty { listOf(KeywordDraft()) })

    fun toggleContext(context: FilterContext): ContentFilterEditor =
        copy(contexts = if (context in contexts) contexts - context else contexts + context)

    companion object {
        fun new(kind: ContentFilterKind): ContentFilterEditor = ContentFilterEditor(kind = kind)

        /** The editor for [filter]. [nowEpochMillis] gives the remaining time of an expiring filter. */
        fun edit(kind: ContentFilterKind, filter: ContentFilter, nowEpochMillis: Long): ContentFilterEditor {
            val remaining = filter.expiresAtEpochMillis?.let { ((it - nowEpochMillis) / 1000).coerceAtLeast(1) }
            return ContentFilterEditor(
                kind = kind,
                editingId = filter.id,
                title = filter.title,
                keywords = filter.patterns.filterIsInstance<FilterPattern.Words>()
                    .map { KeywordDraft(it.words.joinToString(" "), it.wholeWord) }
                    .ifEmpty { listOf(KeywordDraft()) },
                action = filter.action,
                contexts = filter.contexts,
                expiry = if (remaining != null) FilterExpiry.Keep else FilterExpiry.Never,
                remainingSeconds = remaining,
            )
        }
    }
}

sealed interface EditorResult {
    data class Valid(val draft: ContentFilterDraft) : EditorResult
    data class Invalid(val error: FilterEditorError) : EditorResult
}
