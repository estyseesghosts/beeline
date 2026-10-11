package me.foxtails.palustris.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchPresentationTest {
    @Test
    fun blankTextShowsTrends() {
        assertEquals(SearchPresentation.Trends, searchPresentation("  ", SearchState(query = "cats", mode = SearchMode.Posts)))
    }

    @Test
    fun textThatDiffersFromTheSubmittedQueryShowsSuggestions() {
        assertEquals(SearchPresentation.Suggestions, searchPresentation("cat", SearchState(query = "cats", mode = SearchMode.Posts)))
    }

    @Test
    fun theSubmittedQueryShowsItsResults() {
        assertEquals(SearchPresentation.Posts, searchPresentation("cats", SearchState(query = "cats", mode = SearchMode.Posts)))
        assertEquals(SearchPresentation.Hashtag, searchPresentation("#cats", SearchState(query = "#cats", mode = SearchMode.Hashtag)))
    }

    @Test
    fun aFinishedLookupWithoutATargetFallsBackToSuggestions() {
        assertEquals(
            SearchPresentation.Suggestions,
            searchPresentation("@a@b.c", SearchState(query = "@a@b.c", mode = SearchMode.Lookup)),
        )
        assertEquals(
            SearchPresentation.Lookup,
            searchPresentation("@a@b.c", SearchState(query = "@a@b.c", mode = SearchMode.Lookup, loading = true)),
        )
    }
}
