package me.foxtails.palustris.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.HiddenContentPresentation
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostInteractionCounts
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Reaction
import me.foxtails.palustris.ui.posts.LocalHiddenContentPresentation
import me.foxtails.palustris.ui.posts.SinglePostPresentation
import me.foxtails.palustris.ui.posts.SinglePostScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SinglePostScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val account = Account(
        AccountId(Connection("https://example.org", Protocol.MASTODON), "person"),
        "Display Name",
        "@person@example.org",
    )

    @Test fun photoPostUsesAFullWidthPagerAndKeepsTheBodyBelowIt() {
        val post = Post(
            EntityId("https://example.org", "photo-post"),
            account,
            "The complete photo post body",
            0,
            Audience.Public,
            attachments = listOf(
                image("one"),
                image("two"),
            ),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(OwnedPost(account.id, post), SinglePostPresentation.PhotoGrid, onClose = {})
            }
        }
        compose.waitForIdle()

        val pager = compose.onNodeWithTag("single_post_photo_pager", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertEquals(0f, pager.left, 1f)
        assertEquals(411f * density, pager.right, 1f)
        compose.onNodeWithText("The complete photo post body").assertIsDisplayed()
        compose.onNodeWithText("1 / 2").assertIsDisplayed()

        compose.onNodeWithTag("single_post_photo_pager", useUnmergedTree = true)
            .performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
    }

    @Test fun wideDetailUsesFiveToFourViewportWhenSpacePermits() {
        val post = Post(
            EntityId("https://example.org", "wide-roomy"),
            account,
            "Wide roomy body",
            0,
            Audience.Public,
            attachments = listOf(image("wide-roomy", width = 800, height = 1000)),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                Box(Modifier.requiredSize(360.dp, 800.dp)) {
                    SinglePostScreen(
                        OwnedPost(account.id, post),
                        SinglePostPresentation.PhotoGrid,
                        onClose = {},
                        embedded = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.waitForIdle()

        val density = compose.activity.resources.displayMetrics.density
        val pager = compose.onNodeWithTag("single_post_photo_pager", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(360f * density, pager.width, 2f)
        assertEquals(450f * density, pager.height, 2f)
        assertTrue(pager.height >= pager.width)
        compose.onNodeWithText("Wide roomy body").assertIsDisplayed()
        val body = compose.onNodeWithText("Wide roomy body")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(pager.bottom <= body.top)
    }

    @Test fun physicalRightClearanceStaysInsideTheDetailScrollContent() {
        val post = Post(
            EntityId("https://example.org", "detail-clearance"),
            account,
            "Detail content stays clear",
            0,
            Audience.Public,
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                Box(Modifier.requiredSize(360.dp, 800.dp)) {
                    SinglePostScreen(
                        ownedPost = OwnedPost(account.id, post),
                        onClose = {},
                        embedded = true,
                        rightObstructionClearance = 48.dp,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.waitForIdle()

        val density = compose.activity.resources.displayMetrics.density
        val viewport = compose.onNodeWithTag("single_post_content", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag("post_row_detail-clearance", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(360f * density, viewport.width, 1f)
        assertEquals(0f, viewport.left, 1f)
        assertTrue("detail content clears the physical right edge", row.right <= viewport.right - 48f * density + 1f)
    }

    @Test fun wideDetailClampsMediaHeightWhenSpaceIsLimited() {
        val post = Post(
            EntityId("https://example.org", "wide-tight"),
            account,
            "Wide tight body",
            0,
            Audience.Public,
            attachments = listOf(image("wide-tight")),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                Box(Modifier.requiredSize(360.dp, 500.dp)) {
                    SinglePostScreen(
                        OwnedPost(account.id, post),
                        SinglePostPresentation.PhotoGrid,
                        onClose = {},
                        embedded = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.waitForIdle()

        val density = compose.activity.resources.displayMetrics.density
        val pager = compose.onNodeWithTag("single_post_photo_pager", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(360f * density, pager.width, 2f)
        assertEquals(236f * density, pager.height, 2f)
        compose.onNodeWithText("Wide tight body").assertIsDisplayed()
    }

    @Test fun compactDetailUsesTallViewportWhenSpacePermits() {
        val post = Post(
            EntityId("https://example.org", "compact-tall"),
            account,
            "Compact tall body",
            0,
            Audience.Public,
            attachments = listOf(image("compact-tall", width = 800, height = 1000)),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                Box(Modifier.requiredSize(360.dp, 800.dp)) {
                    SinglePostScreen(
                        OwnedPost(account.id, post),
                        SinglePostPresentation.PhotoGrid,
                        onClose = {},
                        embedded = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.waitForIdle()

        val density = compose.activity.resources.displayMetrics.density
        val pager = compose.onNodeWithTag("single_post_photo_pager", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(360f * density, pager.width, 2f)
        assertEquals(450f * density, pager.height, 2f)
        assertTrue(pager.height >= pager.width)
        compose.onNodeWithText("Compact tall body").assertIsDisplayed()
        val body = compose.onNodeWithText("Compact tall body")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(pager.bottom <= body.top)
    }

    @Test fun squarePhotoUsesNaturalHeightInCompact() {
        assertAspectPager(
            id = "square-compact",
            body = "Square compact body",
            width = 1000,
            height = 1000,
            boxWidth = 360.dp,
            boxHeight = 800.dp,
            embedded = false,
            expectedWidthDp = 360f,
            expectedHeightDp = 360f,
        )
    }

    @Test fun fourToFivePhotoUsesNaturalHeightInWide() {
        assertAspectPager(
            id = "four-five-wide",
            body = "Four five wide body",
            width = 800,
            height = 1000,
            boxWidth = 360.dp,
            boxHeight = 800.dp,
            embedded = true,
            expectedWidthDp = 360f,
            expectedHeightDp = 450f,
        )
    }

    @Test fun sixteenToNinePhotoUsesNaturalHeightInWide() {
        assertAspectPager(
            id = "sixteen-nine-wide",
            body = "Sixteen nine wide body",
            width = 1600,
            height = 900,
            boxWidth = 360.dp,
            boxHeight = 800.dp,
            embedded = true,
            expectedWidthDp = 360f,
            expectedHeightDp = 202.5f,
        )
    }

    @Test fun widerThanSixteenToNineUsesNaturalHeightInCompact() {
        assertAspectPager(
            id = "wider-compact",
            body = "Wider compact body",
            width = 2100,
            height = 900,
            boxWidth = 360.dp,
            boxHeight = 800.dp,
            embedded = false,
            expectedWidthDp = 360f,
            expectedHeightDp = 154.3f,
        )
    }

    @Test fun tallerThanFourToFiveCapsAtFourToFiveInWide() {
        assertAspectPager(
            id = "taller-wide",
            body = "Taller wide body",
            width = 600,
            height = 1200,
            boxWidth = 360.dp,
            boxHeight = 800.dp,
            embedded = true,
            expectedWidthDp = 360f,
            expectedHeightDp = 450f,
        )
    }

    @Test fun shortViewportClampsSquarePhotoToAvailableHeight() {
        assertAspectPager(
            id = "square-tight",
            body = "Square tight body",
            width = 1000,
            height = 1000,
            boxWidth = 360.dp,
            boxHeight = 500.dp,
            embedded = true,
            expectedWidthDp = 360f,
            expectedHeightDp = 236f,
        )
    }

    @Test fun shortViewportKeepsNaturalWideHeightWhenItFits() {
        assertAspectPager(
            id = "wide-tight-natural",
            body = "Wide tight natural body",
            width = 1600,
            height = 900,
            boxWidth = 360.dp,
            boxHeight = 500.dp,
            embedded = false,
            expectedWidthDp = 360f,
            expectedHeightDp = 202.5f,
        )
    }

    @Test fun missingDimensionsUseViewportFallback() {
        assertAspectPager(
            id = "missing-dims",
            body = "Missing dims body",
            width = null,
            height = null,
            boxWidth = 360.dp,
            boxHeight = 800.dp,
            embedded = true,
            expectedWidthDp = 360f,
            expectedHeightDp = 450f,
        )
    }

    @Test fun invalidDimensionsUseViewportFallback() {
        assertAspectPager(
            id = "invalid-dims",
            body = "Invalid dims body",
            width = 0,
            height = 0,
            boxWidth = 360.dp,
            boxHeight = 800.dp,
            embedded = false,
            expectedWidthDp = 360f,
            expectedHeightDp = 450f,
        )
    }

    @Test fun multiPhotoPostSharesOneStableHeightAcrossPages() {
        val post = Post(
            EntityId("https://example.org", "multi-shared"),
            account,
            "Multi shared body",
            0,
            Audience.Public,
            attachments = listOf(
                image("multi-square", width = 1000, height = 1000),
                image("multi-wide", width = 1600, height = 900),
            ),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                Box(Modifier.requiredSize(360.dp, 800.dp)) {
                    SinglePostScreen(
                        OwnedPost(account.id, post),
                        SinglePostPresentation.PhotoGrid,
                        onClose = {},
                        embedded = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.waitForIdle()

        val density = compose.activity.resources.displayMetrics.density
        val first = compose.onNodeWithTag("single_post_photo_pager", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(360f * density, first.width, 2f)
        assertEquals(202.5f * density, first.height, 3f)
        compose.onNodeWithText("Multi shared body").assertIsDisplayed()
        val body = compose.onNodeWithText("Multi shared body")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(first.bottom <= body.top)
        compose.onNodeWithText("1 / 2").assertIsDisplayed()

        compose.onNodeWithTag("single_post_photo_pager", useUnmergedTree = true)
            .performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        val second = compose.onNodeWithTag("single_post_photo_pager", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(360f * density, second.width, 2f)
        assertEquals(202.5f * density, second.height, 3f)
        val bodyAfter = compose.onNodeWithText("Multi shared body")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(second.bottom <= bodyAfter.top)
    }

    @Test fun photoPostKeepsTheCompleteBodyWithoutFeedTruncation() {
        val body = "complete ".repeat(45)
        val post = Post(
            EntityId("https://example.org", "long-photo-post"),
            account,
            body,
            0,
            Audience.Public,
            attachments = listOf(image("long-body")),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(OwnedPost(account.id, post), SinglePostPresentation.PhotoGrid, onClose = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText(body, substring = false).assertIsDisplayed()
        compose.onNodeWithText("View full post").assertDoesNotExist()
    }

    @Test fun photoPostShowsInteractionsBetweenMediaAndBody() {
        val post = Post(
            EntityId("https://example.org", "photo-actions"),
            account,
            "Body below the action row",
            0,
            Audience.Public,
            attachments = listOf(image("actions")),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(
                    ownedPost = OwnedPost(account.id, post),
                    presentation = SinglePostPresentation.PhotoGrid,
                    onClose = {},
                    availableActions = PostAction.entries.toSet(),
                )
            }
        }
        compose.waitForIdle()

        val actions = compose.onNodeWithContentDescription("Post actions", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val body = compose.onNodeWithText("Body below the action row")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(actions.bottom <= body.top)
        compose.onNodeWithContentDescription("Reply").assertIsDisplayed()
        compose.onNodeWithContentDescription("Favorite").assertIsDisplayed()
    }

    @Test fun photoPostDetailExposesTheSharedQuoteAction() {
        val post = Post(
            EntityId("https://example.org", "quote-action"),
            account,
            "Quote target",
            0,
            Audience.Public,
        )
        var quotes = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(
                    ownedPost = OwnedPost(account.id, post),
                    presentation = SinglePostPresentation.PhotoGrid,
                    onClose = {},
                    availableActions = setOf(PostAction.Reshare),
                    quoteEnabled = true,
                    onQuote = { quotes++ },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Repost").performTouchInput { longClick() }
        assertEquals(1, quotes)
    }

    @Test fun photoGridQuotePreviewShowsWarningButNotWarningBody() {
        val quoted = Post(
            EntityId("https://example.org", "quoted-preview"), account, "quoted body secret", 0, Audience.Public,
            contentWarning = "Sensitive topic",
        )
        val post = Post(
            EntityId("https://example.org", "quote-preview"), account, "parent", 0, Audience.Public,
            attachments = listOf(image("quote-preview")), quote = quoted,
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(OwnedPost(account.id, post), SinglePostPresentation.PhotoGrid, onClose = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Sensitive topic").assertIsDisplayed()
        compose.onAllNodesWithText("quoted body secret", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun removeSettingKeepsHiddenQuoteCardAndOmitsItsMediaInPhotoGridDetail() {
        val quoted = Post(
            EntityId("https://example.org", "removed-quoted"), account, "secret quote body #secret", 0, Audience.Public,
            contentWarning = "Sensitive topic", attachments = listOf(image("hidden-quote-media")),
        )
        val post = Post(
            EntityId("https://example.org", "remove-parent"), account, "visible parent", 0, Audience.Public,
            attachments = listOf(image("visible-parent-media")), quote = quoted,
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(LocalHiddenContentPresentation provides HiddenContentPresentation.Remove) {
                    SinglePostScreen(OwnedPost(account.id, post), SinglePostPresentation.PhotoGrid, onClose = {},
                        contentWarningRules = ContentWarningRules(hideHashtags = listOf("secret")))
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Quoted content hidden.").assertIsDisplayed()
        compose.onAllNodesWithText("secret quote body #secret", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("single_post_photo_pager", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("post_media_frame_removed-quoted_0", useUnmergedTree = true).assertDoesNotExist()
        // Remove does not remove this visible parent or its detail pager; it applies to hidden parent content and grid entries.

        val hiddenParent = post.copy(text = "parent secret #parentmute", contentWarning = "Parent warning")
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(LocalHiddenContentPresentation provides HiddenContentPresentation.Remove) {
                    SinglePostScreen(OwnedPost(account.id, hiddenParent), SinglePostPresentation.PhotoGrid, onClose = {},
                        contentWarningRules = ContentWarningRules(hideHashtags = listOf("parentmute")))
                }
            }
        }
        compose.waitForIdle()
        compose.onAllNodesWithText("parent secret #parentmute", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("Quoted content hidden.", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("single_post_photo_pager", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun sameHiddenPostUsesTheSameQuoteDecisionInPhotoGridDetailAndPostRow() {
        val quoted = Post(
            EntityId("https://example.org", "shared-hidden-quote"), account,
            "shared quote secret #mutedtag", 0, Audience.Public,
            contentWarning = "Shared warning", attachments = listOf(image("shared-hidden-quote-media")),
        )
        val post = Post(
            EntityId("https://example.org", "shared-hidden-parent"), account,
            "shared parent", 0, Audience.Public, attachments = listOf(image("shared-parent-media")), quote = quoted,
        )
        val rules = ContentWarningRules(hideHashtags = listOf("mutedtag"))
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(OwnedPost(account.id, post), SinglePostPresentation.PhotoGrid, onClose = {}, contentWarningRules = rules)
            }
        }
        compose.waitForIdle()
        assertHiddenQuoteDecision("shared quote secret #mutedtag", "Shared warning", "shared-hidden-quote")

        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(OwnedPost(account.id, post), SinglePostPresentation.Standard, onClose = {}, contentWarningRules = rules)
            }
        }
        compose.waitForIdle()
        assertHiddenQuoteDecision("shared quote secret #mutedtag", "Shared warning", "shared-hidden-quote")
    }

    @Test fun largeSystemFontScaleKeepsHiddenQuotePlaceholderAndHidesPreview() {
        val quoted = Post(
            EntityId("https://example.org", "large-hidden-quote"), account,
            "large text secret #mutedtag", 0, Audience.Public, contentWarning = "Long translated warning",
        )
        val post = Post(EntityId("https://example.org", "large-parent"), account, "parent", 0, Audience.Public, quote = quoted)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f)) {
                    SinglePostScreen(OwnedPost(account.id, post), SinglePostPresentation.PhotoGrid, onClose = {},
                        contentWarningRules = ContentWarningRules(hideHashtags = listOf("mutedtag")))
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Quoted content hidden.").assertIsDisplayed()
        compose.onAllNodesWithText("large text secret #mutedtag", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("Long translated warning", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun photoGridQuotePreviewHidesLocallyMutedQuoteTextFromSemantics() {
        val quoted = Post(
            EntityId("https://example.org", "quoted-hidden"), account, "secret #mutedtag", 0, Audience.Public,
            contentWarning = "Sensitive topic",
        )
        val post = Post(
            EntityId("https://example.org", "quote-hidden"), account, "parent", 0, Audience.Public,
            attachments = listOf(image("quote-hidden")), quote = quoted,
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(me.foxtails.palustris.ui.posts.LocalMutedHashtags provides setOf("mutedtag")) {
                    SinglePostScreen(
                        OwnedPost(account.id, post), SinglePostPresentation.PhotoGrid, onClose = {},
                        contentWarningRules = ContentWarningRules(),
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("muted word: #mutedtag").assertIsDisplayed()
        compose.onNodeWithText("Show content").assertIsDisplayed()
        compose.onAllNodesWithText("secret #mutedtag", useUnmergedTree = true).assertCountEquals(0)

        compose.onNodeWithText("Show content").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Sensitive topic").assertIsDisplayed()
        compose.onAllNodesWithText("secret #mutedtag", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun mutedQuoteRevealDoesNotOpenQuoteAndRevealsItsBody() {
        val quoted = Post(
            EntityId("https://example.org", "isolated-quote"), account, "revealable quote #mute", 0, Audience.Public,
            url = "https://example.org/quoted-post",
        )
        val post = Post(EntityId("https://example.org", "isolated-parent"), account, "parent", 0, Audience.Public, quote = quoted)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(me.foxtails.palustris.ui.posts.LocalMutedHashtags provides setOf("mute")) {
                    SinglePostScreen(OwnedPost(account.id, post), SinglePostPresentation.PhotoGrid, onClose = {})
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Show content").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("revealable quote #mute").assertIsDisplayed()
        assertEquals(null, shadowOf(compose.activity).nextStartedActivity)

        compose.onNodeWithText("View quoted post").performClick()
        val openIntent = shadowOf(compose.activity).nextStartedActivity
        assertEquals("https://example.org/quoted-post", openIntent.data.toString())
    }

    @Test fun switchingAccountResetsMutedQuoteRevealForSamePostIdentity() {
        val secondAccount = account.copy(
            id = AccountId(Connection("https://second.example", Protocol.MASTODON), "other-person"),
        )
        val quoted = Post(EntityId("https://example.org", "same-quote-id"), account, "same quote #mute", 0, Audience.Public)
        val post = Post(EntityId("https://example.org", "same-parent-id"), account, "parent", 0, Audience.Public, quote = quoted)
        var displayed by mutableStateOf(OwnedPost(account.id, post, sessionRevision = 7L))
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(me.foxtails.palustris.ui.posts.LocalMutedHashtags provides setOf("mute")) {
                    SinglePostScreen(displayed, SinglePostPresentation.PhotoGrid, onClose = {})
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Show content").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("same quote #mute").assertIsDisplayed()

        compose.activity.runOnUiThread {
            displayed = OwnedPost(secondAccount.id, post, sessionRevision = 7L)
        }
        compose.waitForIdle()

        compose.onNodeWithText("muted word: #mute").assertIsDisplayed()
        compose.onNodeWithText("Show content").assertIsDisplayed()
        compose.onAllNodesWithText("same quote #mute", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun mutedQuoteWithMediaDoesNotRegisterQuoteMediaBeforeReveal() {
        val quoted = Post(
            EntityId("https://example.org", "muted-media-quote"), account, "media quote #mute", 0, Audience.Public,
            attachments = listOf(image("muted-quote-image")),
        )
        val post = Post(
            EntityId("https://example.org", "muted-media-parent"), account, "parent", 0, Audience.Public,
            attachments = listOf(image("visible-parent-image")), quote = quoted,
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(me.foxtails.palustris.ui.posts.LocalMutedHashtags provides setOf("mute")) {
                    SinglePostScreen(OwnedPost(account.id, post), SinglePostPresentation.PhotoGrid, onClose = {})
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("single_post_photo_pager", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("post_media_frame_muted-quote-image_0", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("muted word: #mute").assertIsDisplayed()
    }

    @Test fun largeSystemFontScaleKeepsMutedQuoteWarningAndRevealButtonWithinBounds() {
        val quoted = Post(EntityId("https://example.org", "large-muted-quote"), account, "large quote #mute", 0, Audience.Public)
        val post = Post(EntityId("https://example.org", "large-muted-parent"), account, "parent", 0, Audience.Public, quote = quoted)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f),
                    me.foxtails.palustris.ui.posts.LocalMutedHashtags provides setOf("mute"),
                ) {
                    SinglePostScreen(OwnedPost(account.id, post), SinglePostPresentation.PhotoGrid, onClose = {})
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("muted word: #mute").assertIsDisplayed()
        compose.onNodeWithText("Show content").assertIsDisplayed()
        val labelBounds = compose.onNodeWithText("muted word: #mute").fetchSemanticsNode().boundsInRoot
        val buttonBounds = compose.onNodeWithText("Show content").fetchSemanticsNode().boundsInRoot
        val root = compose.onNodeWithTag("single_post_content", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(labelBounds.width > 0f && labelBounds.height > 0f)
        assertTrue(buttonBounds.width > 0f && buttonBounds.height > 0f)
        assertTrue(labelBounds.left >= root.left && labelBounds.right <= root.right)
        assertTrue(buttonBounds.left >= root.left && buttonBounds.right <= root.right)
    }

    @Test fun quoteMutedTagMatchingIgnoresCase() {
        val quoted = Post(EntityId("https://example.org", "case-quote"), account, "quote #Tag", 0, Audience.Public)
        val post = Post(EntityId("https://example.org", "case-parent"), account, "parent", 0, Audience.Public, quote = quoted)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(me.foxtails.palustris.ui.posts.LocalMutedHashtags provides setOf("tag")) {
                    SinglePostScreen(OwnedPost(account.id, post), SinglePostPresentation.PhotoGrid, onClose = {})
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("muted word: #Tag").assertIsDisplayed()
        compose.onAllNodesWithText("quote #Tag", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun serverHiddenQuoteTakesPrecedenceOverMutedTag() {
        val quoted = Post(
            EntityId("https://example.org", "server-hidden-quote"), account, "server hidden #tag", 0,
            Audience.Public, contentVisibility = me.foxtails.palustris.domain.PostContentVisibility.Hidden,
        )
        val post = Post(EntityId("https://example.org", "server-hidden-parent"), account, "parent", 0, Audience.Public, quote = quoted)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(me.foxtails.palustris.ui.posts.LocalMutedHashtags provides setOf("tag")) {
                    SinglePostScreen(OwnedPost(account.id, post), SinglePostPresentation.PhotoGrid, onClose = {})
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Quoted content hidden.").assertIsDisplayed()
        compose.onAllNodesWithText("muted word: #tag", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("Show content", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun photoGridQuotePreviewShowsPlainQuoteBody() {
        val quoted = Post(EntityId("https://example.org", "quoted-plain"), account, "plain quote body", 0, Audience.Public)
        val post = Post(
            EntityId("https://example.org", "quote-plain"), account, "parent", 0, Audience.Public,
            attachments = listOf(image("quote-plain")), quote = quoted,
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(OwnedPost(account.id, post), SinglePostPresentation.PhotoGrid, onClose = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("plain quote body").assertIsDisplayed()
    }

    @Test fun postRowQuoteCharacterizationKeepsMutedWarningAndPlainPreviews() {
        val quoted = Post(
            EntityId("https://example.org", "row-quoted"), account, "row secret #mutedtag", 0, Audience.Public,
            contentWarning = "Sensitive topic",
        )
        val post = Post(EntityId("https://example.org", "row-parent"), account, "parent", 0, Audience.Public, quote = quoted)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(me.foxtails.palustris.ui.posts.LocalMutedHashtags provides setOf("mutedtag")) {
                    SinglePostScreen(
                        OwnedPost(account.id, post), SinglePostPresentation.Standard, onClose = {},
                        contentWarningRules = ContentWarningRules(),
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("muted word: #mutedtag").assertIsDisplayed()
        compose.onNodeWithText("Show content").assertIsDisplayed()
        compose.onAllNodesWithText("row secret #mutedtag", useUnmergedTree = true).assertCountEquals(0)

        val plainQuote = Post(EntityId("https://example.org", "row-plain"), account, "row plain quote body", 0, Audience.Public)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(
                    OwnedPost(account.id, post.copy(quote = plainQuote)),
                    SinglePostPresentation.Standard,
                    onClose = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("row plain quote body").assertIsDisplayed()
    }

    @Test fun misskeyPhotoGridQuoteUsesContentWarningRulesForHiddenPlaceholder() {
        val misskeyAccount = account.copy(
            id = AccountId(Connection("https://misskey.example", Protocol.MISSKEY), "person"),
        )
        val quoted = Post(
            EntityId("https://misskey.example", "misskey-quoted"), misskeyAccount,
            "Misskey quote secret #mutedtag", 0, Audience.Public, contentWarning = "Sensitive topic",
        )
        val post = Post(
            EntityId("https://misskey.example", "misskey-parent"), misskeyAccount,
            "parent", 0, Audience.Public, attachments = listOf(image("misskey-quote")), quote = quoted,
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(
                    OwnedPost(misskeyAccount.id, post), SinglePostPresentation.PhotoGrid, onClose = {},
                    contentWarningRules = ContentWarningRules(hideHashtags = listOf("mutedtag")),
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Quoted content hidden.").assertIsDisplayed()
        compose.onAllNodesWithText("Misskey quote secret #mutedtag", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("Sensitive topic", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun mutedQuoteWarningAndRevealMatchAcrossPostRowAndPhotoGridDetail() {
        val quoted = Post(
            EntityId("https://example.org", "muted-parity-quote"), account,
            "parity quote body #muted", 0, Audience.Public,
        )
        val post = Post(
            EntityId("https://example.org", "muted-parity-parent"), account,
            "parent", 0, Audience.Public,
            attachments = listOf(image("muted-parity-parent")), quote = quoted,
        )
        fun show(presentation: SinglePostPresentation) {
            compose.activity.runOnUiThread {
                compose.activity.setContent {
                    CompositionLocalProvider(
                        me.foxtails.palustris.ui.posts.LocalMutedHashtags provides setOf("muted"),
                    ) {
                        SinglePostScreen(OwnedPost(account.id, post), presentation, onClose = {})
                    }
                }
            }
            compose.waitForIdle()
        }

        show(SinglePostPresentation.Standard)
        compose.onNodeWithText("muted word: #muted").assertIsDisplayed()
        compose.onAllNodesWithText("parity quote body #muted", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText("Show content").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("parity quote body #muted").assertIsDisplayed()

        show(SinglePostPresentation.PhotoGrid)
        compose.onNodeWithTag("single_post_photo_pager", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("muted word: #muted").assertIsDisplayed()
        compose.onAllNodesWithText("parity quote body #muted", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText("Show content").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("parity quote body #muted").assertIsDisplayed()
    }

    @Test fun misskeyMutedQuoteShowsRevealableWarning() {
        val misskeyAccount = account.copy(
            id = AccountId(Connection("https://misskey.example", Protocol.MISSKEY), "person"),
        )
        val quoted = Post(
            EntityId("https://misskey.example", "misskey-muted-quote"), misskeyAccount,
            "Misskey quote #muted", 0, Audience.Public,
        )
        val post = Post(
            EntityId("https://misskey.example", "misskey-muted-parent"), misskeyAccount,
            "parent", 0, Audience.Public, quote = quoted,
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(
                    me.foxtails.palustris.ui.posts.LocalMutedHashtags provides setOf("muted"),
                ) {
                    SinglePostScreen(OwnedPost(misskeyAccount.id, post), SinglePostPresentation.PhotoGrid, onClose = {})
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("muted word: #muted").assertIsDisplayed()
        compose.onNodeWithText("Show content").assertIsDisplayed()
        compose.onAllNodesWithText("Misskey quote #muted", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun emptyMutedHashtagSetShowsUnmatchedQuoteBody() {
        val quoted = Post(
            EntityId("https://example.org", "unmatched-quote"), account,
            "ordinary quote body #unmatched", 0, Audience.Public,
        )
        val post = Post(
            EntityId("https://example.org", "unmatched-parent"), account,
            "parent", 0, Audience.Public, quote = quoted,
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(
                    me.foxtails.palustris.ui.posts.LocalMutedHashtags provides emptySet(),
                ) {
                    SinglePostScreen(OwnedPost(account.id, post), SinglePostPresentation.PhotoGrid, onClose = {})
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("ordinary quote body #unmatched").assertIsDisplayed()
        compose.onAllNodesWithText("muted word:", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Show content").assertCountEquals(0)
    }

    @Test fun photoPostDetailShowsRepostConfirmation() {
        val post = Post(
            EntityId("https://example.org", "photo-repost"),
            account,
            "Photo repost",
            0,
            Audience.Public,
            attachments = listOf(image("photo-repost")),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(
                    ownedPost = OwnedPost(account.id, post),
                    presentation = SinglePostPresentation.PhotoGrid,
                    onClose = {},
                    availableActions = setOf(PostAction.Reshare),
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Repost").performClick()
        compose.onNodeWithTag("repost_confirmation", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun showRepostChoice(
        reposted: Boolean = false,
        quoteEnabled: Boolean = true,
        fontScale: Float = 1f,
        onReshare: (OwnedPost) -> Unit = {},
        onQuote: (OwnedPost) -> Unit = {},
    ) {
        val post = Post(
            EntityId("https://example.org", "repost-choice"),
            account,
            "Choice post",
            0,
            Audience.Public,
            attachments = listOf(image("repost-choice")),
            reposted = reposted,
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                androidx.compose.runtime.CompositionLocalProvider(
                    me.foxtails.palustris.ui.posts.LocalPostRepostConfirmationState provides
                        me.foxtails.palustris.ui.posts.PostRepostConfirmationState(),
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                        androidx.compose.ui.platform.LocalDensity.current.density,
                        fontScale,
                    ),
                ) {
                    SinglePostScreen(
                        ownedPost = OwnedPost(account.id, post),
                        presentation = SinglePostPresentation.PhotoGrid,
                        onClose = {},
                        availableActions = setOf(PostAction.Reshare),
                        quoteEnabled = quoteEnabled,
                        onReshare = onReshare,
                        onQuote = onQuote,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun repostTapOffersRepostAndQuoteWithoutSending() {
        var reshared = 0
        showRepostChoice(onReshare = { reshared++ })

        compose.onNodeWithContentDescription("Repost").performClick()

        compose.onNodeWithTag("repost_confirmation", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("repost_choice_quote", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, reshared)
    }

    @Test fun repostChoiceSendsOnlyWhenRepostIsChosen() {
        var reshared = 0
        var quoted = 0
        showRepostChoice(onReshare = { reshared++ }, onQuote = { quoted++ })

        compose.onNodeWithContentDescription("Repost").performClick()
        compose.onNodeWithTag("repost_confirmation", useUnmergedTree = true).performClick()

        assertEquals(1, reshared)
        assertEquals(0, quoted)
        compose.onAllNodesWithTag("repost_choice", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun quoteChoiceRoutesToTheComposerWithoutReposting() {
        var reshared = 0
        var quotedPost: OwnedPost? = null
        showRepostChoice(onReshare = { reshared++ }, onQuote = { quotedPost = it })

        compose.onNodeWithContentDescription("Repost").performClick()
        compose.onNodeWithTag("repost_choice_quote", useUnmergedTree = true).performClick()

        assertEquals("repost-choice", quotedPost?.post?.id?.value)
        assertEquals(0, reshared)
        compose.onAllNodesWithTag("repost_choice", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun unsupportedQuoteIsNotOffered() {
        showRepostChoice(quoteEnabled = false)

        compose.onNodeWithContentDescription("Repost").performClick()

        compose.onNodeWithTag("repost_confirmation", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithTag("repost_choice_quote", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun largeFontOpensTheChoiceAsASheetWithBothOptions() {
        showRepostChoice(fontScale = 2f)

        compose.onNodeWithContentDescription("Repost").performClick()

        compose.onNodeWithTag("repost_choice", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("repost_confirmation", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("repost_choice_quote", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun repostedPostOffersUndoRepostAndStillOffersQuote() {
        showRepostChoice(reposted = true)

        compose.onNodeWithContentDescription("Undo repost").performClick()

        compose.onNodeWithTag("repost_confirmation", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("repost_choice_quote", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun pendingRepostIsDescribedAsPendingNotConfirmed() {
        val post = Post(
            EntityId("https://example.org", "pending-repost"),
            account,
            "Pending repost",
            0,
            Audience.Public,
            attachments = listOf(image("pending-repost")),
            reposted = true,
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                androidx.compose.runtime.CompositionLocalProvider(
                    me.foxtails.palustris.ui.posts.LocalPostPendingLookup provides
                        me.foxtails.palustris.ui.posts.PostPendingLookup { _, families ->
                            me.foxtails.palustris.ui.posts.PostActionFamily.Reshare in families
                        },
                ) {
                    SinglePostScreen(
                        ownedPost = OwnedPost(account.id, post),
                        presentation = SinglePostPresentation.PhotoGrid,
                        onClose = {},
                        availableActions = setOf(PostAction.Reshare, PostAction.Bookmark),
                    )
                }
            }
        }
        compose.waitForIdle()

        val repost = compose.onNodeWithContentDescription("Undo repost").fetchSemanticsNode()
        assertEquals("Pending", repost.config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription])
        val bookmark = compose.onNodeWithContentDescription("Bookmark").fetchSemanticsNode()
        assertEquals("Not selected", bookmark.config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription])
    }

    @Test fun photoPostDetailRendersUpdatedInteractionState() {
        val post = Post(
            EntityId("https://example.org", "photo-state"),
            account,
            "Photo state",
            0,
            Audience.Public,
            attachments = listOf(image("photo-state")),
        )
        var displayed by mutableStateOf(OwnedPost(account.id, post))
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(
                    ownedPost = displayed,
                    presentation = SinglePostPresentation.PhotoGrid,
                    onClose = {},
                    availableActions = PostAction.entries.toSet(),
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Favorite").assertIsDisplayed()

        compose.activity.runOnUiThread {
            displayed = displayed.copy(post = displayed.post.copy(favourited = true, saved = true, reposted = true))
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Unfavorite").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove bookmark").assertIsDisplayed()
        compose.onNodeWithContentDescription("Undo repost").assertIsDisplayed()
    }

    @Test
    fun wideMisskeyDetailRendersAllUpdatedInteractionStates() {
        val misskeyAccount = account.copy(
            id = AccountId(Connection("https://misskey.example", Protocol.MISSKEY), "person"),
        )
        val post = Post(
            EntityId("https://misskey.example", "misskey-photo-state"),
            misskeyAccount,
            "Misskey photo state",
            0,
            Audience.Public,
            attachments = listOf(image("misskey-photo-state")),
        )
        var displayed by mutableStateOf(OwnedPost(misskeyAccount.id, post))
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(
                    ownedPost = displayed,
                    presentation = SinglePostPresentation.PhotoGrid,
                    onClose = {},
                    availableActions = PostAction.entries.toSet(),
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Favorite").assertIsDisplayed()
        compose.onNodeWithContentDescription("Bookmark").assertIsDisplayed()

        compose.activity.runOnUiThread {
            displayed = displayed.copy(
                post = displayed.post.copy(
                    favourited = true,
                    myReaction = "👍",
                    selectedReactions = listOf(
                        me.foxtails.palustris.domain.EmojiChoice("👍", "👍", null),
                    ),
                    reposted = true,
                    saved = true,
                ),
            )
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Unfavorite").assertIsSelected()
        compose.onNodeWithContentDescription("Undo repost").assertIsSelected()
        compose.onNodeWithContentDescription("Remove bookmark").assertIsSelected()
    }

    @Test fun standardPresentationUsesThePostRowEvenWhenPhotosExist() {
        val post = Post(
            EntityId("https://example.org", "standard-photo-post"),
            account,
            "Standard detail body",
            0,
            Audience.Public,
            attachments = listOf(image("standard")),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(
                    ownedPost = OwnedPost(account.id, post),
                    presentation = SinglePostPresentation.Standard,
                    onClose = {},
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Standard detail body").assertIsDisplayed()
        compose.onNodeWithTag("single_post_photo_pager").assertDoesNotExist()
    }

    @Test
    fun standardFocalPostShowsDetailedCountsAndHidesNumericOne() {
        val post = Post(
            EntityId("https://example.org", "detailed"),
            account,
            "Detailed body",
            0,
            Audience.Public,
            reactions = listOf(Reaction("❤️", 1, false), Reaction("👍", 4, false)),
            interactionCounts = PostInteractionCounts(
                favouriteCount = 0,
                reactionCount = 5,
                repostCount = 2,
                quoteRepostCount = 0,
                replyCount = 3,
            ),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(OwnedPost(account.id, post), onClose = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("interaction_summary", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("0 favorites").assertIsDisplayed()
        compose.onNodeWithText("5 reactions").assertIsDisplayed()
        compose.onNodeWithTag("reaction_count_👍", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("reaction_count_❤️", useUnmergedTree = true).assertDoesNotExist()
        val summaryTop = compose.onNodeWithTag("interaction_summary", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top
        val metadataBottom = compose.onNodeWithContentDescription("Post metadata", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(summaryTop >= metadataBottom)
    }

    @Test
    fun photoGridFocalPostOrdersActionsReactionsBodyAndSummary() {
        val post = Post(
            EntityId("https://example.org", "photo-detailed"),
            account,
            "Photo detailed body",
            0,
            Audience.Public,
            attachments = listOf(image("photo-detailed")),
            reactions = listOf(Reaction("👍", 2, false)),
            interactionCounts = PostInteractionCounts(replyCount = 0, repostCount = 0),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(
                    ownedPost = OwnedPost(account.id, post),
                    presentation = SinglePostPresentation.PhotoGrid,
                    onClose = {},
                    availableActions = PostAction.entries.toSet(),
                )
            }
        }
        compose.waitForIdle()

        val actions = compose.onNodeWithContentDescription("Post actions", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val reaction = compose.onNodeWithTag("reaction_chip_👍", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val body = compose.onNodeWithText("Photo detailed body").fetchSemanticsNode().boundsInRoot
        val summary = compose.onNodeWithTag("interaction_summary", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(actions.bottom <= reaction.top)
        assertTrue(reaction.bottom <= body.top)
        assertTrue(body.bottom <= summary.top)
        compose.onNodeWithText("0 replies").assertIsDisplayed()
        compose.onNodeWithText("0 reposts").assertIsDisplayed()
    }

    @Test
    fun largeFontKeepsHashtagSummaryReadableAndActionsReachable() {
        val post = Post(
            EntityId("https://example.org", "large-post-presentation"),
            account,
            "Readable post body. ".repeat(180) + "\n\n#misskey #second #third",
            0,
            Audience.Public,
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f)) {
                    SinglePostScreen(
                        ownedPost = OwnedPost(account.id, post),
                        presentation = SinglePostPresentation.Standard,
                        onClose = {},
                        availableActions = PostAction.entries.toSet(),
                    )
                }
            }
        }
        compose.waitForIdle()

        val summary = compose.onNodeWithContentDescription(
            "3 hashtags: #misskey, #second and #third",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val summaryText = compose.onNodeWithText("#misskey +2", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val metadata = compose.onNodeWithContentDescription("Post metadata", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(summary.height > 32f * compose.activity.resources.displayMetrics.density)
        assertTrue(summaryText.bottom <= summary.bottom)
        assertTrue(summary.bottom <= metadata.bottom)

        val actionsNode = compose.onNodeWithContentDescription("Post actions", useUnmergedTree = true)
        actionsNode.performScrollTo()
        actionsNode.assertIsDisplayed()
        val actions = actionsNode.fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag("single_post_content", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(actions.top >= content.top)
        assertTrue(actions.bottom <= content.bottom)
        compose.onNodeWithContentDescription("Share").assertIsDisplayed()
    }

    private fun assertAspectPager(
        id: String,
        body: String,
        width: Int?,
        height: Int?,
        boxWidth: androidx.compose.ui.unit.Dp,
        boxHeight: androidx.compose.ui.unit.Dp,
        embedded: Boolean,
        expectedWidthDp: Float,
        expectedHeightDp: Float,
    ) {
        val post = Post(
            EntityId("https://example.org", id),
            account,
            body,
            0,
            Audience.Public,
            attachments = listOf(image(id, width = width, height = height)),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                Box(Modifier.requiredSize(boxWidth, boxHeight)) {
                    SinglePostScreen(
                        OwnedPost(account.id, post),
                        SinglePostPresentation.PhotoGrid,
                        onClose = {},
                        embedded = embedded,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.waitForIdle()

        val density = compose.activity.resources.displayMetrics.density
        val pager = compose.onNodeWithTag("single_post_photo_pager", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(expectedWidthDp * density, pager.width, 3f)
        assertEquals(expectedHeightDp * density, pager.height, 3f)
        compose.onNodeWithText(body).assertIsDisplayed()
        val bodyBounds = compose.onNodeWithText(body)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(pager.bottom <= bodyBounds.top)
    }

    private fun assertHiddenQuoteDecision(secret: String, warning: String, quoteId: String) {
        compose.onNodeWithText("Quoted content hidden.").assertIsDisplayed()
        compose.onAllNodesWithText(secret, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText(warning, useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("post_media_frame_${quoteId}_0", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun image(id: String, width: Int? = 640, height: Int? = 480) = Attachment(
        id = id,
        url = "https://cdn.example/$id.jpg",
        mimeType = "image/jpeg",
        kind = MediaKind.Image,
        width = width,
        height = height,
    )
}
