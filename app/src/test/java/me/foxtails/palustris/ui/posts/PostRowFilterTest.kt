package me.foxtails.palustris.ui.posts

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.FilterContext
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostFilterAction
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerFilterMatch
import me.foxtails.palustris.ui.PalustrisTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A collapsing filter hides the body behind a notice; a removing filter drops the row on its own surfaces only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class PostRowFilterTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val account = Account(AccountId(connection, "a"), "Author", "@a@example.org")

    private fun post(vararg matches: ServerFilterMatch) =
        Post(EntityId(connection.origin, "p1"), account, "finale spoilers here", 0, Audience.Public, serverFilters = matches.toList())

    private fun show(post: Post, context: FilterContext = FilterContext.Home, removable: Boolean = true) {
        val reveals = PostRevealOwner()
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    CompositionLocalProvider(LocalPostReveals provides reveals) {
                        PostRow(
                            OwnedPost(account.id, post),
                            PostRowPresentation(availableActions = emptySet(), filterContext = context, removable = removable),
                            PostRowEvents({}, {}, {}, {}, { _, _ -> }, null, null),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun collapsingFilterShowsTheNoticeAndRevealsOnTap() {
        show(post(ServerFilterMatch("Spoilers", PostFilterAction.Collapse)))

        compose.onNodeWithText("Filtered: Spoilers").assertIsDisplayed()
        compose.onAllNodesWithText("finale spoilers here").assertCountEquals(0)

        compose.onNodeWithTag("post_filter_show").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("finale spoilers here").assertIsDisplayed()
        compose.onAllNodesWithText("Filtered: Spoilers").assertCountEquals(0)
    }

    @Test
    fun removingFilterDropsTheRowInItsContext() {
        show(post(ServerFilterMatch("Spoilers", PostFilterAction.Remove, setOf(FilterContext.Home))))

        compose.onAllNodesWithTag("post_row_p1").assertCountEquals(0)
    }

    @Test
    fun filterForAnotherContextLeavesTheRowAlone() {
        show(post(ServerFilterMatch("Spoilers", PostFilterAction.Remove, setOf(FilterContext.Home))), FilterContext.Thread)

        compose.onNodeWithText("finale spoilers here").assertIsDisplayed()
        compose.onAllNodesWithTag("post_filter_show").assertCountEquals(0)
    }

    @Test
    fun openedPostCollapsesInsteadOfLeaving() {
        show(post(ServerFilterMatch("Spoilers", PostFilterAction.Remove)), FilterContext.Thread, removable = false)

        compose.onNodeWithText("Filtered: Spoilers").assertIsDisplayed()
        compose.onAllNodesWithText("finale spoilers here").assertCountEquals(0)
    }
}
