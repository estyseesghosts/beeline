package me.foxtails.palustris.ui.photogrid

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.FavouriteArtworkStyle
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.PalustrisTheme
import me.foxtails.palustris.ui.shell.ShellOverlayPresenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class PhotoQuickViewTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val account = Account(
        id = AccountId(Connection("https://example.org", Protocol.MASTODON), "quick"),
        displayName = "Quick",
        handle = "@quick@example.org",
    )
    private val ownedPost = OwnedPost(
        account.id,
        Post(
            id = EntityId("https://example.org", "q"),
            author = account,
            text = "caption",
            publishedAtEpochMillis = 0,
            audience = Audience.Public,
            attachments = listOf(
                Attachment(
                    id = "q-image",
                    url = "https://cdn.example/q.jpg",
                    previewUrl = "https://cdn.example/q-preview.jpg",
                    mimeType = "image/jpeg",
                    kind = MediaKind.Image,
                    width = 640,
                    height = 480,
                    description = "alt",
                ),
            ),
        ),
    )
    private val target = PhotoQuickViewTarget(ownedPost, 0, Rect.Zero)

    @Test fun reactionCapableSourceOffersHeartAndReact() {
        val entries = photoQuickViewEntries(setOf(PostAction.Favorite, PostAction.React, PostAction.Reply, PostAction.Reshare))
        assertEquals(
            listOf(
                PhotoQuickViewEntry.Heart,
                PhotoQuickViewEntry.React,
                PhotoQuickViewEntry.Reply,
                PhotoQuickViewEntry.Repost,
                PhotoQuickViewEntry.Share,
            ),
            entries,
        )
    }

    @Test fun favouriteOnlySourceOffersOneFavouriteEntry() {
        assertEquals(
            listOf(PhotoQuickViewEntry.Favourite, PhotoQuickViewEntry.Share),
            photoQuickViewEntries(setOf(PostAction.Favorite)),
        )
        assertEquals(listOf(PhotoQuickViewEntry.Share), photoQuickViewEntries(emptySet()))
    }

    @Test fun shareClosesTheQuickViewAndReportsTheEntry() {
        var dismissed = 0
        var shared: OwnedPost? = null
        show(setOf(PostAction.Favorite), onDismiss = { dismissed++ }, onShare = { post, _ -> shared = post })
        compose.onNodeWithTag("photo_quick_view_share").performClick()
        assertEquals(1, dismissed)
        assertEquals(ownedPost, shared)
    }

    @Test fun heartAndReactAreBothShownAndReactClosesFirst() {
        var dismissed = 0
        var reacted = 0
        show(setOf(PostAction.Favorite, PostAction.React), onDismiss = { dismissed++ }, onReact = { _, _ -> reacted++ })
        compose.onNodeWithTag("photo_quick_view_heart").assertIsDisplayed()
        compose.onNodeWithTag("photo_quick_view_react").performClick()
        assertEquals(1, dismissed)
        assertEquals(1, reacted)
    }

    @Test fun favouriteOnlyHidesReact() {
        show(setOf(PostAction.Favorite))
        compose.onNodeWithTag("photo_quick_view_favourite").assertIsDisplayed()
        compose.onNodeWithTag("photo_quick_view_react").assertDoesNotExist()
    }

    @Test fun scrimTapAndBackDismiss() {
        var dismissed = 0
        show(emptySet(), onDismiss = { dismissed++ })
        compose.onNodeWithTag("photo_quick_view").performTouchInput { click(topLeft) }
        assertEquals(1, dismissed)
        compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(2, dismissed)
    }

    @Test fun repostAsksForConfirmationBeforeRunning() {
        var reposted = 0
        show(setOf(PostAction.Reshare), onRepost = { reposted++ })
        compose.onNodeWithTag("photo_quick_view_repost").performClick()
        assertEquals(0, reposted)
        compose.onNodeWithTag("photo_quick_view_repost_confirm").performClick()
        assertEquals(1, reposted)
    }

    @Test fun longPressOnAGridCardOpensTheQuickViewAndTapStillOpensThePost() {
        var quick: PhotoQuickViewTarget? = null
        var opened = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    PhotoGridScreen(
                        state = PhotoGridFeedState(posts = listOf(ownedPost)),
                        onOpenPost = { opened++ },
                        cardActions = PhotoGridCardActions(onQuickView = { quick = it }),
                    )
                }
            }
        }
        compose.waitForIdle()
        val tile = compose.onNodeWithTag(
            "photo_grid_tile_https://example.org/https://example.org/q/q-image",
            useUnmergedTree = true,
        )
        tile.performTouchInput { longClick() }
        assertEquals(0, opened)
        assertEquals(ownedPost, quick?.ownedPost)
        tile.performClick()
        assertEquals(1, opened)
    }

    @Test fun unrevealedSensitiveCardDoesNotOpenTheQuickView() {
        var quick: PhotoQuickViewTarget? = null
        val sensitive = ownedPost.copy(
            post = ownedPost.post.copy(attachments = ownedPost.post.attachments.map { it.copy(sensitive = true) }),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    PhotoGridScreen(
                        state = PhotoGridFeedState(posts = listOf(sensitive)),
                        cardActions = PhotoGridCardActions(onQuickView = { quick = it }),
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag(
            "photo_grid_tile_https://example.org/https://example.org/q/q-image",
            useUnmergedTree = true,
        ).performTouchInput { longClick() }
        assertNull(quick)
    }

    @Test fun releasingOverAnEntryRunsIt() = dragAndRelease(releaseOnShare = true)

    @Test fun releasingElsewhereKeepsTheQuickViewOpen() = dragAndRelease(releaseOnShare = false)

    private fun dragAndRelease(releaseOnShare: Boolean) {
        run {
            var shared = 0
            var opened by androidx.compose.runtime.mutableStateOf<PhotoQuickViewTarget?>(null)
            compose.activity.runOnUiThread {
                compose.activity.setContent {
                    PalustrisTheme {
                      androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                        PhotoGridScreen(
                            state = PhotoGridFeedState(posts = listOf(ownedPost)),
                            cardActions = PhotoGridCardActions(onQuickView = { opened = it }),
                        )
                        opened?.let {
                            PhotoQuickView(
                                target = it,
                                availableActions = emptySet(),
                                favouriteArtworkStyle = FavouriteArtworkStyle.Heart,
                                onDismiss = { opened = null },
                                onFavourite = {},
                                onReact = { _, _ -> },
                                onReply = {},
                                onRepost = {},
                                onShare = { _, _ -> shared++ },
                            )
                        }
                      }
                    }
                }
            }
            compose.waitForIdle()
            val tile = compose.onNodeWithTag(
                "photo_grid_tile_https://example.org/https://example.org/q/q-image",
                useUnmergedTree = true,
            )
            val tileTopLeft = tile.fetchSemanticsNode().boundsInRoot.topLeft
            tile.performTouchInput {
                down(center)
                advanceEventTime(1_000)
                move(10)
            }
            compose.waitForIdle()
            compose.onNodeWithTag("photo_quick_view_share").assertExists()
            val target = if (releaseOnShare) {
                compose.onNodeWithTag("photo_quick_view_share").fetchSemanticsNode().boundsInRoot.center
            } else {
                androidx.compose.ui.geometry.Offset(5f, 5f)
            }
            tile.performTouchInput {
                moveTo(target - tileTopLeft)
                up()
            }
            compose.waitForIdle()
            assertEquals(if (releaseOnShare) 1 else 0, shared)
            if (!releaseOnShare) compose.onNodeWithTag("photo_quick_view_share").assertExists()
        }
    }

    @Test fun presenterDropsTheTargetOnSessionOrAccountChange() {
        val overlay = ShellOverlayPresenter().apply {
            account = this@PhotoQuickViewTest.account
            sessionRevision = 0L
            reactionMutation = CapabilityStatus.Supported
        }
        overlay.openPhotoQuickView(target)
        assertEquals(target, overlay.photoQuickView)
        overlay.clearForSessionChange()
        assertNull(overlay.photoQuickView)
        overlay.openPhotoQuickView(target)
        overlay.clearForAccountChange()
        assertNull(overlay.photoQuickView)
        overlay.sessionRevision = 5L
        overlay.openPhotoQuickView(target)
        assertNull("stale revision is rejected", overlay.photoQuickView)
        overlay.sessionRevision = 0L
        overlay.account = account.copy(id = AccountId(account.id.connection, "other"))
        overlay.openPhotoQuickView(target)
        assertTrue("foreign post is rejected", overlay.photoQuickView == null)
    }

    private fun show(
        actions: Set<PostAction>,
        onDismiss: () -> Unit = {},
        onReact: (OwnedPost, Rect) -> Unit = { _, _ -> },
        onRepost: (OwnedPost) -> Unit = {},
        onShare: (OwnedPost, Rect) -> Unit = { _, _ -> },
    ) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    PhotoQuickView(
                        target = target,
                        availableActions = actions,
                        favouriteArtworkStyle = FavouriteArtworkStyle.Heart,
                        onDismiss = onDismiss,
                        onFavourite = {},
                        onReact = onReact,
                        onReply = {},
                        onRepost = onRepost,
                        onShare = onShare,
                    )
                }
            }
        }
        compose.waitForIdle()
    }
}
