package me.foxtails.palustris.ui.photogrid

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.feed.FeedState
import me.foxtails.palustris.ui.search.SearchMode
import me.foxtails.palustris.ui.search.SearchState
import me.foxtails.palustris.ui.shell.AppShellFixtures
import me.foxtails.palustris.ui.shell.PhotoGridContract
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
class PhotoGridSearchCarryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val account = AppShellFixtures.account("carry")
    private val loadMoreCalls = mutableListOf<String>()
    private val selectedFeeds = mutableListOf<PhotoGridFeed>()

    private fun image(id: String) = Attachment(
        id = id,
        url = "https://cdn.example/$id.jpg",
        previewUrl = "https://cdn.example/$id-preview.jpg",
        mimeType = "image/jpeg",
        kind = MediaKind.Image,
        width = 640,
        height = 480,
    )

    private fun show(search: SearchState) {
        val feed = FeedState(accountSearch = search)
        val gridPost = AppShellFixtures.post("grid-home", account, "Home grid post").copy(attachments = listOf(image("home")))
        val grid = PhotoGridContract(
            state = PhotoGridFeedState(posts = listOf(OwnedPost(account.id, gridPost)), initialLoadComplete = true),
            actions = object : PhotoGridContract.Actions {
                override fun ensureLoaded() = Unit
                override fun selectFeed(feed: PhotoGridFeed) {
                    selectedFeeds += feed
                }
                override fun refresh() = Unit
                override fun loadMore() {
                    loadMoreCalls += "grid"
                }
                override fun addHashtag(value: String, onSuccess: () -> Unit) = Unit
                override fun clearPreferenceError() = Unit
            },
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                AppShellFixtures.app(
                    account = account,
                    home = AppShellFixtures.home(feed),
                    search = AppShellFixtures.search(feed, onLoadMore = { loadMoreCalls += "search" }),
                    photoGrid = grid,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun searchThenOpenGrid(query: String) {
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput(query)
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Photo grid").performClick()
        compose.waitForIdle()
    }

    private val results = SearchState(
        query = "#cats",
        mode = SearchMode.Hashtag,
        tagQuery = "cats",
        posts = listOf(AppShellFixtures.post("cat-result", account, "Cat result").copy(attachments = listOf(image("cat")))),
        nextCursor = "next",
    )

    @Test
    fun resultsOpenAsTheSelectedFirstChipAndShowTheSearchPosts() {
        show(results)
        searchThenOpenGrid("#cats")

        compose.onNodeWithTag("photo_grid_search_chip").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithContentDescription("Open post").assertIsDisplayed()
    }

    @Test
    fun anotherChipKeepsTheSearchChipAndSelectingItAgainBringsBackTheResults() {
        show(results)
        searchThenOpenGrid("#cats")

        compose.onNodeWithTag("photo_grid_timeline_Home").performClick()
        compose.onNodeWithTag("photo_grid_search_chip").assertIsNotSelected()
        compose.onNodeWithTag("photo_grid_search_chip").performClick()
        compose.onNodeWithTag("photo_grid_search_chip").assertIsSelected()
    }

    @Test
    fun dismissingTheChipRemovesItAndFallsBackToTheGridFeed() {
        show(results)
        searchThenOpenGrid("#cats")

        val node = compose.onNodeWithTag("photo_grid_search_chip").fetchSemanticsNode()
        val action = node.config.getOrNull(SemanticsActions.CustomActions)?.single()
        assertEquals("Remove search results", action?.label)
        compose.runOnUiThread { action?.action?.invoke() }
        compose.waitForIdle()

        assertTrue(compose.onAllNodesWithTagCount("photo_grid_search_chip") == 0)
    }

    @Test
    fun noResultsLeavesTheGridUnchanged() {
        show(SearchState())
        searchThenOpenGrid("#cats")

        assertTrue(compose.onAllNodesWithTagCount("photo_grid_search_chip") == 0)
        compose.onNodeWithContentDescription("Open post").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
