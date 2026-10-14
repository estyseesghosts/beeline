package me.foxtails.palustris.ui.filters

import me.foxtails.palustris.domain.ContentFilter
import me.foxtails.palustris.domain.ContentFilterKind
import me.foxtails.palustris.domain.FilterContext
import me.foxtails.palustris.domain.FilterPattern
import me.foxtails.palustris.domain.PostFilterAction
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentFilterEditorTest {
    private fun valid(editor: ContentFilterEditor) = (editor.validate() as EditorResult.Valid).draft

    private fun error(editor: ContentFilterEditor) = (editor.validate() as EditorResult.Invalid).error

    @Test
    fun titledFilterNeedsTitleKeywordAndContext() {
        val base = ContentFilterEditor.new(ContentFilterKind.Titled)

        assertEquals(FilterEditorError.MissingTitle, error(base.copy(keywords = listOf(KeywordDraft("finale")))))
        assertEquals(FilterEditorError.MissingKeyword, error(base.copy(title = "TV", keywords = listOf(KeywordDraft("  ")))))
        assertEquals(FilterEditorError.MissingContext, error(base.copy(title = "TV", keywords = listOf(KeywordDraft("finale")), contexts = emptySet())))
    }

    @Test
    fun titledDraftTrimsAndDropsDuplicateKeywords() {
        val draft = valid(
            ContentFilterEditor.new(ContentFilterKind.Titled).copy(
                title = " TV ",
                keywords = listOf(KeywordDraft(" finale ", wholeWord = true), KeywordDraft("FINALE"), KeywordDraft(""), KeywordDraft("season")),
                action = PostFilterAction.Remove,
                expiry = FilterExpiry.Day,
            ),
        )

        assertEquals("TV", draft.title)
        assertEquals(listOf(FilterPattern.Words(listOf("finale"), wholeWord = true), FilterPattern.Words(listOf("season"))), draft.patterns)
        assertEquals(PostFilterAction.Remove, draft.action)
        assertEquals(86_400L, draft.expiresInSeconds)
    }

    @Test
    fun phraseFilterNeedsNoTitleAndKeepsOneKeyword() {
        val draft = valid(ContentFilterEditor.new(ContentFilterKind.Phrase).copy(keywords = listOf(KeywordDraft("finale"), KeywordDraft("season"))))

        assertEquals("finale", draft.title)
        assertEquals(listOf(FilterPattern.Words(listOf("finale"))), draft.patterns)
        assertEquals(null, draft.expiresInSeconds)
    }

    @Test
    fun editingKeepsTheRemainingTimeUntilAnotherExpiryIsChosen() {
        val filter = ContentFilter(
            id = "f1",
            title = "TV",
            patterns = listOf(FilterPattern.Words(listOf("finale"), wholeWord = true)),
            action = PostFilterAction.Collapse,
            contexts = setOf(FilterContext.Home),
            expiresAtEpochMillis = 10_000L,
        )
        val editor = ContentFilterEditor.edit(ContentFilterKind.Titled, filter, nowEpochMillis = 4_000L)

        assertEquals(FilterExpiry.Keep, editor.expiry)
        assertEquals(listOf(KeywordDraft("finale", wholeWord = true)), editor.keywords)
        assertEquals(6L, valid(editor).expiresInSeconds)
        assertEquals(null, valid(editor.copy(expiry = FilterExpiry.Never)).expiresInSeconds)
        assertEquals(FilterExpiry.entries, editor.expiryChoices)
        assertEquals(FilterExpiry.entries - FilterExpiry.Keep, ContentFilterEditor.new(ContentFilterKind.Titled).expiryChoices)
    }

    @Test
    fun keywordRowsAndContextsEditInPlace() {
        val editor = ContentFilterEditor.new(ContentFilterKind.Titled).addKeyword().keyword(1, KeywordDraft("season"))

        assertEquals(listOf(KeywordDraft(), KeywordDraft("season")), editor.keywords)
        assertEquals(listOf(KeywordDraft("season")), editor.removeKeyword(0).keywords)
        assertEquals(listOf(KeywordDraft()), editor.removeKeyword(0).removeKeyword(0).keywords)
        assertEquals(FilterContext.entries.toSet() - FilterContext.Thread, editor.toggleContext(FilterContext.Thread).contexts)
        assertEquals(FilterContext.entries.toSet(), editor.toggleContext(FilterContext.Thread).toggleContext(FilterContext.Thread).contexts)
    }
}
