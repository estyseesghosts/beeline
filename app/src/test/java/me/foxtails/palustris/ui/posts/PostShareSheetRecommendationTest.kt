package me.foxtails.palustris.ui.posts

import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.ui.PalustrisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Slice 4: the not-interested and why-recommended entries appear on recommended rows only.
 * The sheet never decides recommendation state: [showRecommendationActions] arrives from the
 * host, which gates it on the selected Recommended feed plus a Supported probe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PostShareSheetRecommendationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val connection = Connection("https://dvd.chat", Protocol.MISSKEY)
    private val owner = Account(AccountId(connection, "owner"), "Owner", "@owner@dvd.chat")
    private val author = Account(AccountId(connection, "author"), "Author", "@author@dvd.chat")

    private fun show(
        showRecommendationActions: Boolean,
        own: Boolean = false,
        onWhyRecommended: () -> Unit = {},
        onNotInterested: () -> Unit = {},
    ) {
        val postAuthor = if (own) owner else author
        val post = Post(EntityId(connection.origin, "post"), postAuthor, "Post", 0, Audience.Public, url = "https://dvd.chat/post")
        val target = PostActionTarget(OwnedPost(owner.id, post), Rect.Zero, owner.id, 0L)
        val relationship = PostRelationshipState(target = author.id, relationship = ProfileRelationship(author.id))
        // Production rows come from postShareRows, so own posts take the own-post card variant.
        val rows = postShareRows(post, owner.id, ServerCapabilities(), "en")
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    PostShareSheet(
                        target = target,
                        relationship = relationship,
                        rows = rows,
                        onDismiss = {},
                        onRelationshipAction = {},
                        onSubmitReport = {},
                        onOpenDirectMessage = {},
                        onCopyLink = {},
                        onShare = {},
                        showRecommendationActions = showRecommendationActions,
                        onWhyRecommended = onWhyRecommended,
                        onNotInterested = onNotInterested,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun recommendationEntriesStayHiddenOffRecommendedRows() {
        show(showRecommendationActions = false)

        compose.onNodeWithTag("post_share_why_recommended").assertDoesNotExist()
        compose.onNodeWithTag("post_share_not_interested").assertDoesNotExist()
        compose.onNodeWithTag("post_share_follow").assertIsDisplayed()
    }

    @Test fun recommendationEntriesShowAndFireOnRecommendedRows() {
        var why = 0
        var notInterested = 0
        show(
            showRecommendationActions = true,
            onWhyRecommended = { why++ },
            onNotInterested = { notInterested++ },
        )

        compose.onNodeWithTag("post_share_why_recommended").assertIsDisplayed().performClick()
        compose.onNodeWithTag("post_share_not_interested").assertIsDisplayed().performClick()

        assertEquals(1, why)
        assertEquals(1, notInterested)
    }

    @Test fun ownRecommendedRowsKeepNoFeedbackEntries() {
        show(showRecommendationActions = true, own = true)

        compose.onNodeWithTag("post_share_why_recommended").assertDoesNotExist()
        compose.onNodeWithTag("post_share_not_interested").assertDoesNotExist()
    }
}
