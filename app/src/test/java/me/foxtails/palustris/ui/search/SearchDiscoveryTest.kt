package me.foxtails.palustris.ui.search

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.hashtags.HashtagSuggestion
import me.foxtails.palustris.domain.hashtags.TrendingHashtag
import me.foxtails.palustris.domain.search.SearchSuggestions
import me.foxtails.palustris.domain.search.TrendingLink
import me.foxtails.palustris.ui.PalustrisTheme
import me.foxtails.palustris.ui.shell.AppShellFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchDiscoveryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val author = AppShellFixtures.account()
    private val searched = mutableListOf<String>()
    private val typed = mutableListOf<String>()
    private var trendingLoads = 0
    private val opened = mutableListOf<Account>()

    private val trendsActions = object : SearchTrendsActions {
        override fun loadTrends() {
            trendingLoads++
        }

        override fun refreshTrends() {
            trendingLoads++
        }
    }

    private fun show(
        query: String,
        search: SearchState = SearchState(),
        trends: SearchTrendsState = SearchTrendsState(),
        initialTab: SearchTab = SearchTab.Trending,
    ) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    var tab by remember { mutableStateOf(initialTab) }
                    SearchPane(
                        tab = tab,
                        onSelectTab = { tab = it },
                        search = search,
                        trends = trends,
                        trendsActions = trendsActions,
                        sharedQuery = query,
                        onSuggest = { typed += it },
                        onSearchHashtag = { searched += it },
                        onAccountClick = { opened += it },
                        mediaOwner = author.id,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun text(id: Int) = compose.activity.getString(id)

    @Test
    fun blankQueryShowsTrendingTagsAndATapSearchesTheHashtag() {
        show(
            "",
            trends = SearchTrendsState(
                tags = listOf(TrendingHashtag("cats", 120, 400), TrendingHashtag("art", 1, null)),
                loaded = true,
            ),
        )

        compose.onNodeWithText("cats: 400 posts").assertIsDisplayed()
        compose.onNodeWithText("art: 1 user").assertIsDisplayed()
        compose.onNodeWithTag("search_trending_row_cats", useUnmergedTree = true).performClick()

        assertEquals(listOf("#cats"), searched)
        assertEquals(1, trendingLoads)
    }

    @Test
    fun trendingPostsAndLinksAppearWhenTheServerReportsThem() {
        show(
            "",
            trends = SearchTrendsState(
                posts = listOf(AppShellFixtures.post("t1", author, "Trending hello")),
                links = listOf(TrendingLink("https://example.org/a", "A headline", null, "Example", null)),
                loaded = true,
            ),
        )

        compose.onNodeWithText("Trending hello").assertIsDisplayed()
        assertTrue(compose.onAllTagged("search_trending_results").isEmpty())
        compose.onNodeWithTag("search_tabs").assertIsDisplayed()
        compose.onNodeWithTag("search_tab_News").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("A headline").assertIsDisplayed()
    }

    @Test
    fun aTagWithNeitherCountShowsOnlyItsName() {
        show("", trends = SearchTrendsState(tags = listOf(TrendingHashtag("plain", null, null)), loaded = true))

        compose.onNodeWithText("plain").assertIsDisplayed()
    }

    @Test
    fun withoutLinksThereIsNoTabRowAndASelectedNewsTabShowsTrending() {
        show(
            "",
            trends = SearchTrendsState(tags = listOf(TrendingHashtag("cats", null, 3)), loaded = true),
            initialTab = SearchTab.News,
        )

        assertTrue(compose.onAllTagged("search_tabs").isEmpty())
        compose.onNodeWithText("cats: 3 posts").assertIsDisplayed()
    }

    @Test
    fun theTabRowStaysWhileResultsShowAndATabTapKeepsTheQuery() {
        show(
            "cats",
            search = SearchState(query = "cats", mode = SearchMode.Posts, posts = listOf(AppShellFixtures.post("r1", author, "A cat post"))),
            trends = SearchTrendsState(links = listOf(TrendingLink("https://example.org/a", "A headline", null, null, null)), loaded = true),
        )

        compose.onNodeWithTag("search_tab_News").assertIsDisplayed()
        compose.onNodeWithTag("search_tab_News").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("A cat post").assertIsDisplayed()
    }

    @Test
    fun anEmptyTrendsAnswerShowsOneEmptyState() {
        show("", trends = SearchTrendsState(loaded = true))

        compose.onNodeWithText(text(R.string.search_trends_empty)).assertIsDisplayed()
    }

    @Test
    fun aFailedTrendsLoadShowsTheErrorAndRetryLoadsAgain() {
        show("", trends = SearchTrendsState(loaded = true, error = "offline"))

        compose.onNodeWithText(text(R.string.search_trends_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.search_trends_retry)).performClick()
        assertEquals(2, trendingLoads)
    }

    @Test
    fun typingShowsPeopleAndHashtagSuggestionsAndTapsRoute() {
        val matched = AppShellFixtures.account("matched")
        show(
            "pho",
            search = SearchState(
                suggestionsFor = "pho",
                suggestions = SearchSuggestions(
                    accounts = listOf(matched),
                    hashtags = listOf(HashtagSuggestion("photography", 9.0), HashtagSuggestion("phone", null)),
                ),
            ),
        )

        compose.onNodeWithText(text(R.string.search_suggestions_people)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.search_suggestions_hashtags)).assertIsDisplayed()
        compose.onNodeWithTag("search_suggestion_row_phone", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("search_account_row_matched", useUnmergedTree = true).performClick()

        assertEquals(listOf("#phone"), searched)
        assertEquals(listOf(matched.id), opened.map { it.id })
        assertEquals("pho", typed.last())
    }

    @Test
    fun aSectionWithNoRowsDoesNotAppear() {
        show(
            "pho",
            search = SearchState(
                suggestionsFor = "pho",
                suggestions = SearchSuggestions(hashtags = listOf(HashtagSuggestion("phone", null))),
            ),
        )

        assertTrue(compose.onAllNodes(hasText(text(R.string.search_suggestions_people))).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText(text(R.string.search_suggestions_hashtags)).assertIsDisplayed()
    }

    @Test
    fun noSuggestionsKeepsThePressEnterPrompt() {
        show("pho")

        compose.onNodeWithText(text(R.string.search_ready_prompt)).assertIsDisplayed()
    }

    @Test
    fun aSubmittedSearchReplacesSuggestions() {
        val answered = SearchState(
            query = "#pho",
            mode = SearchMode.Hashtag,
            tagQuery = "pho",
            posts = listOf(AppShellFixtures.post("p1", author, "Hello")),
            suggestions = SearchSuggestions(hashtags = listOf(HashtagSuggestion("photography", 9.0))),
            suggestionsFor = "#pho",
        )
        show("#pho", search = answered)

        assertTrue(compose.onAllTagged("search_suggestion_results").isEmpty())
        compose.onNodeWithTag("search_hashtag_results", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun relatedChipsFollowTheStateAndATapSearchesThatHashtag() {
        val answered = SearchState(
            query = "#caturday",
            mode = SearchMode.Hashtag,
            tagQuery = "caturday",
            relatedTags = listOf("cats", "kitten"),
            posts = listOf(AppShellFixtures.post("p1", author, "Hello")),
        )
        show("#caturday", search = answered)

        compose.onNodeWithTag("search_hashtag_related", useUnmergedTree = true).assertIsDisplayed()
        // No merge happened, so the "Includes" line stays hidden while the chips show.
        assertTrue(compose.onAllTagged("search_hashtag_combined").isEmpty())
        compose.onNodeWithTag("search_related_chip_kitten", useUnmergedTree = true).performClick()

        assertEquals(listOf("#kitten"), searched)
    }

    @Test
    fun noRelatedChipsWhenThereAreNone() {
        val answered = SearchState(
            query = "#dogs",
            mode = SearchMode.Hashtag,
            tagQuery = "dogs",
            posts = listOf(AppShellFixtures.post("p1", author, "Hello")),
        )
        show("#dogs", search = answered)

        assertTrue(compose.onAllTagged("search_hashtag_related").isEmpty())
    }

    @Test
    fun aDeniedPostSearchNamesThePolicyInsteadOfAnEmptyAnswer() {
        show("hello", search = SearchState(query = "hello", mode = SearchMode.Posts, postSearchDenied = true))

        compose.onNodeWithText(text(R.string.search_posts_denied)).assertIsDisplayed()
    }

    @Test
    fun aPostSearchShowsItsRows() {
        show(
            "hello",
            search = SearchState(query = "hello", mode = SearchMode.Posts, posts = listOf(AppShellFixtures.post("p1", author, "Hello"))),
        )

        compose.onNodeWithTag("search_post_results", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun anEmptyPostSearchShowsTheServerHint() {
        show("hello", search = SearchState(query = "hello", mode = SearchMode.Posts))

        compose.onNodeWithText(text(R.string.search_no_posts_server_hint)).assertIsDisplayed()
    }

    private fun AndroidComposeTestRule<*, *>.onAllTagged(tag: String) =
        onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes()
}
