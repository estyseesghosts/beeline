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
import me.foxtails.palustris.ui.PalustrisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PostShareSheetOwnPostTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val connection = Connection("https://example.org", Protocol.MISSKEY)
    private val me = AccountId(connection, "me")

    private fun show(rows: PostShareRows, ownPost: OwnPostActionState = OwnPostActionState(), onDelete: () -> Unit = {}, onTranslate: () -> Unit = {}) {
        val post = Post(
            EntityId(connection.origin, "post"),
            Account(me, "Me", "@me@example.org"),
            "Post",
            0,
            Audience.Public,
            url = "https://example.org/post",
        )
        val target = PostActionTarget(OwnedPost(me, post), Rect.Zero, me, 0L)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    PostShareSheet(
                        target = target,
                        relationship = PostRelationshipState(target = me, relationship = ProfileRelationship(me)),
                        rows = rows,
                        ownPost = ownPost,
                        onDismiss = {},
                        onRelationshipAction = {},
                        onSubmitReport = {},
                        onOpenDirectMessage = {},
                        onCopyLink = {},
                        onShare = {},
                        onDelete = onDelete,
                        onTranslate = onTranslate,
                    )
                }
            }
        }
    }

    @Test
    fun ownPostReplacesRelationshipRowsWithDeleteAndKeepsShare() {
        show(PostShareRows(own = true, edit = false, delete = true, translate = false))

        compose.onNodeWithTag("post_share_delete").assertIsDisplayed()
        compose.onNodeWithTag("post_share_system").assertIsDisplayed()
        compose.onNodeWithTag("post_share_copy").assertIsDisplayed()
        compose.onNodeWithTag("post_share_follow").assertDoesNotExist()
        compose.onNodeWithTag("post_share_message").assertDoesNotExist()
        compose.onNodeWithTag("post_share_problem_heading").assertDoesNotExist()
        compose.onNodeWithTag("post_share_pm").assertDoesNotExist()
        compose.onNodeWithTag("post_share_edit").assertDoesNotExist()
    }

    @Test
    fun deleteConfirmsOnTheSecondTap() {
        var deleted = 0
        show(PostShareRows(own = true, edit = false, delete = true, translate = false), onDelete = { deleted++ })

        compose.onNodeWithTag("post_share_delete").performClick()
        assertEquals(0, deleted)
        compose.onNodeWithTag("post_share_delete").performClick()
        assertEquals(1, deleted)
    }

    @Test
    fun sheetShowsShareAloneWhenNoOwnRowIsSupported() {
        show(PostShareRows(own = true, edit = false, delete = false, translate = false))

        compose.onNodeWithTag("post_share_delete").assertDoesNotExist()
        compose.onNodeWithTag("post_share_edit").assertDoesNotExist()
        compose.onNodeWithTag("post_share_system").assertIsDisplayed()
    }

    @Test
    fun translateShowsOnOwnPostsToo() {
        var translated = 0
        show(PostShareRows(own = true, edit = false, delete = false, translate = true), onTranslate = { translated++ })

        compose.onNodeWithTag("post_share_translate").performClick()

        assertEquals(1, translated)
    }

    @Test
    fun othersPostGetsATranslateRowInTheExistingCard() {
        show(PostShareRows(own = false, edit = false, delete = false, translate = true))

        compose.onNodeWithTag("post_share_follow").assertIsDisplayed()
        compose.onNodeWithTag("post_share_translate").assertIsDisplayed()
    }
}
