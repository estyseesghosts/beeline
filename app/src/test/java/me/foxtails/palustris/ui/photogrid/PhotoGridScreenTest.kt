package me.foxtails.palustris.ui.photogrid

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.PalustrisTheme
import me.foxtails.palustris.ui.large.LargeNavTarget
import me.foxtails.palustris.ui.photogrid.PhotoGridFeedState
import me.foxtails.palustris.ui.photogrid.PhotoGridScreen
import me.foxtails.palustris.ui.photogrid.photoGridAspectRatio
import me.foxtails.palustris.ui.photogrid.photoGridItems
import me.foxtails.palustris.ui.shell.Destination
import me.foxtails.palustris.ui.shell.SearchPanel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoGridScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun mediaFilterKeepsOneFirstDisplayableAttachmentPerPost() {
        val textOnly = post("text-only")
        val videoThenImage = post(
            "video-then-image",
            listOf(
                Attachment(
                    id = "video",
                    url = "https://cdn.example/video.mp4",
                    mimeType = "video/mp4",
                    kind = MediaKind.Video,
                ),
                image("second"),
            ),
        )
        val twoImages = post("two-images", listOf(image("first"), image("second")))

        val items = photoGridItems(
            listOf(
                OwnedPost(account.id, textOnly),
                OwnedPost(account.id, videoThenImage),
                OwnedPost(account.id, twoImages),
            ),
        )

        assertEquals(listOf("video-then-image", "two-images"), items.map { it.ownedPost.post.id.value })
        assertEquals("second", items.first().attachment.id)
        assertEquals("first", items.last().attachment.id)
    }

    @Test
    fun mediaFilterAdmitsVideosOnlyWhenTheyHaveAPoster() {
        fun video(id: String, poster: String?) = Attachment(
            id = id,
            url = "https://cdn.example/$id.mp4",
            previewUrl = poster,
            mimeType = "video/mp4",
            kind = MediaKind.Video,
        )
        val withPoster = post("with-poster", listOf(video("clip", "https://cdn.example/clip.jpg")))
        val withoutPoster = post("without-poster", listOf(video("bare", null)))

        val items = photoGridItems(listOf(OwnedPost(account.id, withPoster), OwnedPost(account.id, withoutPoster)))

        assertEquals(listOf("with-poster"), items.map { it.ownedPost.post.id.value })
        assertEquals(MediaKind.Video, items.single().attachment.kind)
    }

    @Test
    fun videoTileShowsAPlayBadgeAndStillOpensThePost() {
        val clip = Attachment(
            id = "clip",
            url = "https://cdn.example/clip.mp4",
            previewUrl = "https://cdn.example/clip.jpg",
            mimeType = "video/mp4",
            kind = MediaKind.Video,
            width = 640,
            height = 480,
        )
        var opened: String? = null
        show(
            PhotoGridFeedState(posts = listOf(OwnedPost(account.id, post("clip-post", listOf(clip))))),
            onOpenPost = { opened = it.post.id.value },
        )

        compose.onNodeWithContentDescription("Play video").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open post").performClick()
        assertEquals("clip-post", opened)
    }

    @Test
    fun mediaFilterExcludesAnimatedImages() {
        val animatedThenStill = post(
            "animated-then-still",
            listOf(
                image("animated").copy(kind = MediaKind.AnimatedImage, mimeType = "image/gif"),
                image("still"),
            ),
        )

        val items = photoGridItems(listOf(OwnedPost(account.id, animatedThenStill)))

        assertEquals("still", items.single().attachment.id)
    }

    @Test
    fun aspectRatioUsesOriginalAttachmentDimensionsWithFallback() {
        assertEquals(
            1.5f,
            photoGridAspectRatio(image("ratio").copy(width = 300, height = 200)),
            0.001f,
        )
        assertEquals(4f / 3f, photoGridAspectRatio(image("fallback").copy(width = null, height = null)), 0.001f)
    }

    @Test
    fun tileAspectIsClampedForExtremeAndSquareAndStandardRatios() {
        fun ratio(w: Int?, h: Int?) = photoGridAspectRatio(image("r").copy(width = w, height = h, previewWidth = null, previewHeight = null))
        assertEquals(1f, ratio(500, 500), 0.001f)
        assertEquals(0.8f, ratio(400, 500), 0.001f)
        assertEquals(16f / 9f, ratio(1600, 900), 0.001f)
        assertEquals(0.5f, ratio(100, 1000), 0.001f)
        assertEquals(16f / 9f, ratio(1000, 100), 0.001f)
        assertEquals(16f / 9f, ratio(3000, 1000), 0.001f)
        assertEquals(4f / 3f, ratio(null, null), 0.001f)
    }

    @Test
    fun collapsedContentWarningCoversTheTileUntilRulesExpandIt() {
        val warned = OwnedPost(account.id, post("warned", listOf(image("warned"))).copy(contentWarning = "Spoilers"))

        assertEquals("Spoilers", photoGridItems(listOf(warned)).single().collapsedWarning)
        assertEquals(
            null,
            photoGridItems(listOf(warned), me.foxtails.palustris.domain.ContentWarningRules(expandAll = true)).single().collapsedWarning,
        )
        assertEquals(null, photoGridItems(listOf(OwnedPost(account.id, post("plain", listOf(image("plain")))))).single().collapsedWarning)
    }

    @Test
    fun coveredWarningTileShowsOnlyTheWarningAndStillOpensThePost() {
        var opened: OwnedPost? = null
        show(
            state = PhotoGridFeedState(
                posts = listOf(OwnedPost(account.id, post("cw", listOf(image("cw"))).copy(contentWarning = "Spoilers"))),
            ),
            onOpenPost = { opened = it },
        )

        compose.onNodeWithText("Spoilers").assertIsDisplayed()
        compose.onNodeWithContentDescription("Spoilers").performClick()

        assertEquals("cw", opened?.post?.id?.value)
    }

    @Test
    fun searchPanelsMapToSeparateLargeNavigationTargets() {
        assertEquals(
            LargeNavTarget.Search,
            me.foxtails.palustris.ui.shell.largeTargetFor(
                Destination.Search,
                SearchPanel.Search,
                me.foxtails.palustris.ui.shell.NotificationsPanel.Notifications,
            ),
        )
        assertEquals(
            LargeNavTarget.PhotoGrid,
            me.foxtails.palustris.ui.shell.largeTargetFor(
                Destination.Search,
                SearchPanel.PhotoGrid,
                me.foxtails.palustris.ui.shell.NotificationsPanel.Notifications,
            ),
        )
    }

    @Test
    fun tileTapOpensTheCompletePost() {
        var opened: OwnedPost? = null
        show(
            state = PhotoGridFeedState(
                posts = listOf(OwnedPost(account.id, post("tap", listOf(image("tap-image"))))),
            ),
            onOpenPost = { opened = it },
        )

        compose.onNodeWithContentDescription("Open post").performClick()

        assertEquals("tap", opened?.post?.id?.value)
    }

    @Test
    fun sensitiveTileRequiresRevealBeforeOpening() {
        var opened: OwnedPost? = null
        show(
            state = PhotoGridFeedState(
                posts = listOf(OwnedPost(account.id, post("sensitive", listOf(image("sensitive").copy(sensitive = true))))),
            ),
            onOpenPost = { opened = it },
        )

        compose.onNodeWithText("Show sensitive media").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Open post").performClick()

        assertEquals("sensitive", opened?.post?.id?.value)
    }

    @Test
    fun pagingErrorKeepsAlreadyLoadedTiles() {
        val loaded = post("loaded", listOf(image("loaded-image")))
        show(
            state = PhotoGridFeedState(
                posts = listOf(OwnedPost(account.id, loaded)),
                error = "Connection failed",
                nextCursor = "older",
            ),
        )

        compose.onNodeWithContentDescription("Open post").assertIsDisplayed()
        compose.onNodeWithText("Couldn\'t load more photos").assertIsDisplayed()
    }

    @Test
    fun textOnlyPageContinuesToItsNextCursor() {
        val textOnly = post("text-only")
        val loadedMedia = post("loaded-media", listOf(image("loaded-image")))
            var state by mutableStateOf(
            PhotoGridFeedState(
                posts = listOf(OwnedPost(account.id, textOnly)),
                nextCursor = "first-page",
            ),
        )
        var loadCount = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    PhotoGridScreen(
                        state = state,
                        onLoadMore = {
                            loadCount++
                            state = PhotoGridFeedState(
                                posts = listOf(OwnedPost(account.id, loadedMedia)),
                            )
                        },
                    )
                }
            }
        }
        compose.waitForIdle()

        assertEquals(1, loadCount)
        compose.onNodeWithContentDescription("Open post").assertIsDisplayed()
    }

    @Test
    fun cardShowsCaptionAuthorAndFavouriteButtonThatDoesNotOpenThePost() {
        var opened = 0
        var favourited: OwnedPost? = null
        showWithActions(
            PhotoGridFeedState(posts = listOf(OwnedPost(account.id, post("card", listOf(image("card")))))),
            actions = setOf(me.foxtails.palustris.domain.PostAction.Favorite),
            onOpenPost = { opened++ },
            onFavourite = { favourited = it },
        )

        compose.onNodeWithText("card").assertIsDisplayed()
        compose.onNodeWithText("Photo Grid").assertIsDisplayed()
        compose.onNodeWithContentDescription("Favorite").performClick()

        assertEquals("card", favourited?.post?.id?.value)
        assertEquals(0, opened)
    }

    @Test
    fun noFavouriteButtonWithoutTheFavoriteAction() {
        showWithActions(
            PhotoGridFeedState(posts = listOf(OwnedPost(account.id, post("none", listOf(image("none")))))),
            actions = emptySet(),
        )

        compose.onNodeWithContentDescription("Favorite").assertDoesNotExist()
    }

    @Test
    fun captionFallsBackToAltTextAndHidesWhenBothAreEmpty() {
        fun caption(text: String, alt: String?) = photoGridCaption(
            photoGridItems(
                listOf(OwnedPost(account.id, post("c", listOf(image("c").copy(description = alt))).copy(text = text))),
            ).single(),
        )
        assertEquals("first", caption("\n first \nsecond", "alt"))
        assertEquals("alt", caption("  ", " alt "))
        assertEquals(null, caption("", null))
    }

    private fun showWithActions(
        state: PhotoGridFeedState,
        actions: Set<me.foxtails.palustris.domain.PostAction>,
        onOpenPost: (OwnedPost) -> Unit = {},
        onFavourite: (OwnedPost) -> Unit = {},
    ) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    PhotoGridScreen(
                        state = state,
                        onOpenPost = onOpenPost,
                        cardActions = PhotoGridCardActions(actions, onFavourite = onFavourite),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun show(state: PhotoGridFeedState, onOpenPost: (OwnedPost) -> Unit = {}) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    PhotoGridScreen(state = state, onOpenPost = onOpenPost)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun post(id: String, attachments: List<Attachment> = emptyList()) = Post(
        id = EntityId(account.id.connection.origin, id),
        author = account,
        text = id,
        publishedAtEpochMillis = 0,
        audience = Audience.Public,
        attachments = attachments,
    )

    private fun image(id: String) = Attachment(
        id = id,
        url = "https://cdn.example/$id.jpg",
        previewUrl = "https://cdn.example/$id-preview.jpg",
        mimeType = "image/jpeg",
        kind = MediaKind.Image,
        width = 640,
        height = 480,
    )

    private val account = Account(
        id = AccountId(Connection("https://example.org", Protocol.MASTODON), "photo-grid"),
        displayName = "Photo Grid",
        handle = "@photo-grid@example.org",
    )
}
