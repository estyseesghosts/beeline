package me.foxtails.palustris.ui.search

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.hashtags.HashtagSuggestion
import me.foxtails.palustris.domain.hashtags.TrendingHashtag
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
    private var popularLoads = 0
    private val opened = mutableListOf<Account>()

    private val actions = object : SearchExploreActions {
        override fun loadTrending() { trendingLoads++ }
        override fun loadPopularAccounts() { popularLoads++ }
        override fun suggestHashtags(text: String) { typed += text }
    }

    private fun show(
        query: String,
        tab: Int,
        accountSearch: AccountSearchState = AccountSearchState(),
        explore: SearchExploreState = SearchExploreState(),
    ) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    SearchScreen(
                        accountSearch = accountSearch,
                        explore = explore,
                        exploreActions = actions,
                        sharedQuery = query,
                        sharedTab = tab,
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
    fun blankHashtagsTabListsTrendingRowsAndATapSearchesTheHashtag() {
        show("", 1, explore = SearchExploreState(trending = listOf(TrendingHashtag("cats", 120, 400), TrendingHashtag("art", 1, null))))

        compose.onNodeWithText(text(R.string.search_trending_hashtags)).assertIsDisplayed()
        compose.onNodeWithText("120 people").assertIsDisplayed()
        compose.onNodeWithText("1 person").assertIsDisplayed()
        compose.onNodeWithTag("search_trending_row_cats", useUnmergedTree = true).performClick()

        assertEquals(listOf("#cats"), searched)
        assertEquals(1, trendingLoads)
    }

    @Test
    fun anEmptyTrendingListKeepsTheOldPromptAndShowsNoError() {
        show("", 1)

        compose.onNodeWithText(text(R.string.search_explore_hashtags)).assertIsDisplayed()
        assertTrue(compose.onAllTagged("search_trending_results").isEmpty())
        assertEquals(1, trendingLoads)
    }

    @Test
    fun aTypedHashtagShowsSuggestionsAndATapSearchesIt() {
        show("#pho", 1, explore = SearchExploreState(suggestions = listOf(HashtagSuggestion("photography", 9.0), HashtagSuggestion("phone", null))))

        compose.onNodeWithTag("search_suggestion_row_photography", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("search_suggestion_row_phone", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("search_suggestion_row_phone", useUnmergedTree = true).performClick()

        assertEquals(listOf("#phone"), searched)
        assertEquals("#pho", typed.last())
    }

    @Test
    fun aTypedHashtagWithoutSuggestionsKeepsTheReadyState() {
        show("#pho", 1)

        compose.onNodeWithText(text(R.string.search_hashtag_ready)).assertIsDisplayed()
    }

    @Test
    fun aSubmittedSearchReplacesSuggestionsAndStopsAskingForThem() {
        val answered = AccountSearchState(query = "#pho", tagQuery = "pho", posts = listOf(AppShellFixtures.post("p1", author, "Hello")))
        show("#pho", 1, accountSearch = answered, explore = SearchExploreState(suggestions = listOf(HashtagSuggestion("photography", 9.0))))

        assertTrue(compose.onAllTagged("search_suggestion_results").isEmpty())
        assertEquals("", typed.last())
    }

    @Test
    fun relatedChipsFollowTheStateAndATapSearchesThatHashtag() {
        val answered = AccountSearchState(
            query = "#caturday",
            tagQuery = "caturday",
            relatedTags = listOf("cats", "kitten"),
            posts = listOf(AppShellFixtures.post("p1", author, "Hello")),
        )
        show("#caturday", 1, accountSearch = answered)

        compose.onNodeWithTag("search_hashtag_related", useUnmergedTree = true).assertIsDisplayed()
        // No merge happened, so the "Includes" line stays hidden while the chips show.
        assertTrue(compose.onAllTagged("search_hashtag_combined").isEmpty())
        compose.onNodeWithTag("search_related_chip_kitten", useUnmergedTree = true).performClick()

        assertEquals(listOf("#kitten"), searched)
    }

    @Test
    fun noRelatedChipsWhenThereAreNone() {
        val answered = AccountSearchState(
            query = "#dogs",
            tagQuery = "dogs",
            posts = listOf(AppShellFixtures.post("p1", author, "Hello")),
        )
        show("#dogs", 1, accountSearch = answered)

        assertTrue(compose.onAllTagged("search_hashtag_related").isEmpty())
    }

    @Test
    fun trendingLoadsOnlyForABlankHashtagsTab() {
        val tab = mutableStateOf(0)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    SearchScreen(exploreActions = actions, sharedQuery = "", sharedTab = tab.value)
                }
            }
        }
        compose.waitForIdle()
        assertEquals(0, trendingLoads)

        compose.runOnIdle { tab.value = 1 }
        compose.waitForIdle()
        assertEquals(1, trendingLoads)
    }

    @Test
    fun blankProfilesTabListsPopularAccountsAndATapOpensTheProfile() {
        val popular = AppShellFixtures.account("popular")
        show("", 0, explore = SearchExploreState(popularAccounts = listOf(popular)))

        compose.onNodeWithText(text(R.string.search_popular_accounts)).assertIsDisplayed()
        compose.onNodeWithTag("search_account_row_popular", useUnmergedTree = true).performClick()

        assertEquals(listOf(popular.id), opened.map { it.id })
        assertEquals(1, popularLoads)
        assertEquals(0, trendingLoads)
    }

    @Test
    fun anEmptyPopularListKeepsTheOldPrompt() {
        show("", 0)

        compose.onNodeWithText(text(R.string.search_find_account)).assertIsDisplayed()
        assertTrue(compose.onAllTagged("search_popular_accounts").isEmpty())
    }

    @Test
    fun typingAnAccountQueryReplacesThePopularList() {
        val popular = AppShellFixtures.account("popular")
        val matched = AppShellFixtures.account("matched")
        show(
            "fixture owner", 0,
            accountSearch = AccountSearchState(query = "fixture owner", accounts = listOf(matched)),
            explore = SearchExploreState(popularAccounts = listOf(popular)),
        )

        assertTrue(compose.onAllTagged("search_popular_accounts").isEmpty())
        compose.onNodeWithTag("search_account_row_matched", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllTagged(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes()
}
