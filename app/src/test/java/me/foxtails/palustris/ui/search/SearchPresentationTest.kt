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

    private val withLinks = SearchTrendsState(
        links = listOf(me.foxtails.palustris.domain.search.TrendingLink("https://example.org/a", "A", null, null, null)),
    )

    @Test
    fun newsExistsOnlyWhileTheServerSuppliesLinks() {
        assertEquals(true, searchTabsAvailable(withLinks))
        assertEquals(false, searchTabsAvailable(SearchTrendsState()))
    }

    @Test
    fun aSelectedNewsTabFallsBackToTrendingWithoutLinks() {
        assertEquals(SearchTab.Trending, effectiveSearchTab(SearchTab.News, SearchTrendsState()))
        assertEquals(SearchTab.News, effectiveSearchTab(SearchTab.News, withLinks))
    }

    @Test
    fun theRowTakesNoSpaceWithoutTabsOrWhenTheCompactWideCaretHidesIt() {
        assertEquals(false, searchTabRowOccupiesSpace(tabsAvailable = false, useCompactWideCaret = false, rowVisible = true))
        assertEquals(true, searchTabRowOccupiesSpace(tabsAvailable = true, useCompactWideCaret = false, rowVisible = false))
        assertEquals(false, searchTabRowOccupiesSpace(tabsAvailable = true, useCompactWideCaret = true, rowVisible = false))
        assertEquals(true, searchTabRowOccupiesSpace(tabsAvailable = true, useCompactWideCaret = true, rowVisible = true))
    }
}
