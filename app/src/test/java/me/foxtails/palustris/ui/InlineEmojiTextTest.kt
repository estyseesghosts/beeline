package me.foxtails.palustris.ui.emoji

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostMention
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ValidatedUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class InlineEmojiTextTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val blob = CustomEmoji(
        shortcode = "blob_cat",
        animatedUrl = ValidatedUrl.https("https://cdn.example/blob_cat.gif"),
        staticUrl = ValidatedUrl.https("https://cdn.example/blob_cat.png"),
        submissionValue = ":blob_cat:",
    )

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.activity.runOnUiThread { compose.activity.setContent { content() } }
        compose.waitForIdle()
    }

    @Test
    fun knownTokenRendersAndShortcodeStaysReadableInTheText() {
        show {
            InlineEmojiText("hello :blob_cat: world", mapOf("blob_cat" to blob))
        }

        compose.onNodeWithText("hello :blob_cat: world", substring = true).assertIsDisplayed()
    }

    @Test
    fun unknownTokenRemainsPlainText() {
        show {
            InlineEmojiText("no :missing: here", mapOf("blob_cat" to blob))
        }

        compose.onNodeWithText("no :missing: here", substring = true).assertIsDisplayed()
    }

    @Test
    fun accountDisplayNameUsesTheAccountsOwnEmojiMap() {
        val account = Account(
            AccountId(Connection("https://example.org", Protocol.MASTODON), "person"),
            "Named :blob_cat: Person",
            "@person@example.org",
            emoji = mapOf("blob_cat" to blob),
        )
        show { AccountDisplayName(account) }

        compose.onNodeWithText("Named :blob_cat: Person", substring = true).assertIsDisplayed()
    }

    @Test
    fun postTextUsesThePostsOwnEmojiMap() {
        val post = Post(
            id = EntityId("https://example.org", "post"),
            author = Account(
                AccountId(Connection("https://example.org", Protocol.MASTODON), "person"),
                "Person",
                "@person@example.org",
            ),
            text = "post :blob_cat: body",
            publishedAtEpochMillis = 0,
            audience = Audience.Public,
            emoji = mapOf("blob_cat" to blob),
        )
        show { PostText(post) }

        compose.onNodeWithText("post :blob_cat: body", substring = true).assertIsDisplayed()
    }

    @Test
    fun linksSurviveInsideEmojiAwareText() {
        show {
            InlineEmojiText(
                "read [the guide](https://example.org/guide) :blob_cat:",
                mapOf("blob_cat" to blob),
            )
        }

        compose.onNodeWithText("read the guide", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun customEmojiInsideLinkStillRendersAsInlineContent() {
        show {
            InlineEmojiText(
                "[the :blob_cat: guide](https://example.org/guide)",
                mapOf("blob_cat" to blob),
            )
        }

        compose.onNodeWithText("the :blob_cat: guide", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun postEntityBubblesExposeLabelsAndRouteClicksToTheirEntityCallbacks() {
        var openedUrl = ""
        var openedUsername = ""
        var searchedHashtag = ""
        show {
            InlineEmojiText(
                "read https://example.org/guide from @handle@mastodon.social about #Zurich",
                emptyMap(),
                enableInlineEntities = true,
                onOpenUrl = { openedUrl = it },
                onOpenUsername = { openedUsername = it },
                onSearchHashtag = { searchedHashtag = it },
            )
        }

        compose.onNodeWithContentDescription("Link example.org").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Username @handle").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Hashtag #Zurich").assertIsDisplayed().performClick()

        assertEquals("https://example.org/guide", openedUrl)
        assertEquals("@handle@mastodon.social", openedUsername)
        assertEquals("#Zurich", searchedHashtag)
    }

    @Test
    fun entityLogogramsKeepSpaceFromTheirLabelsAtNormalAndLargeText() {
        for (scale in listOf(1f, 2f)) {
            show {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                        androidx.compose.ui.platform.LocalDensity.current.density,
                        scale,
                    ),
                ) {
                    InlineEmojiText(
                        "see https://example.org/a-very-long-path about #Zurich",
                        emptyMap(),
                        enableInlineEntities = true,
                    )
                }
            }
            val icons = compose.onAllNodesWithTag("entity_icon", useUnmergedTree = true)
            val labels = compose.onAllNodesWithTag("entity_label", useUnmergedTree = true)
            assertEquals(2, icons.fetchSemanticsNodes().size)
            for (i in 0 until 2) {
                val icon = icons[i].fetchSemanticsNode().boundsInRoot
                val label = labels[i].fetchSemanticsNode().boundsInRoot
                assertTrue("icon must end before the label at scale $scale", icon.right + 1f <= label.left)
            }
        }
    }

    @Test
    fun entityBubbleIsOneDpShorterThanItsPlaceholder() {
        var placeholderPx = 0f
        show {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
            placeholderPx = with(density) { style.fontSize.toPx() } * 1.5f
            InlineEmojiText("see https://example.org/a", emptyMap(), style = style, enableInlineEntities = true)
        }
        val bubble = compose.onNodeWithContentDescription("Link example.org").fetchSemanticsNode().boundsInRoot
        val oneDp = compose.activity.resources.displayMetrics.density
        assertEquals(placeholderPx - oneDp, bubble.height, 1.5f)
    }

    @Test
    fun entityLabelsAreDirectionIsolatedButTargetsStayExact() {
        var openedUrl = ""
        show {
            InlineEmojiText(
                "مرحبا https://example.org/guide مرحبا",
                emptyMap(),
                enableInlineEntities = true,
                onOpenUrl = { openedUrl = it },
            )
        }
        compose.onNodeWithContentDescription("Link example.org").performClick()
        assertEquals("https://example.org/guide", openedUrl)
        val label = compose.onNodeWithTag("entity_label", useUnmergedTree = true).fetchSemanticsNode()
        val text = label.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text }
        assertEquals("⁦example.org⁩", text)
    }

    @Test
    fun ordinaryTextTapUsesTheFallbackCallback() {
        var tapped = false
        show {
            InlineEmojiText(
                "plain post body",
                emptyMap(),
                onTextTap = { tapped = true },
            )
        }

        compose.onNodeWithText("plain post body").performClick()

        assertTrue(tapped)
    }

    @Test
    fun entityTapDoesNotAlsoUseTheFallbackCallback() {
        var tapped = false
        var openedUrl = ""
        show {
            InlineEmojiText(
                "read https://example.org/guide from the post",
                emptyMap(),
                enableInlineEntities = true,
                onOpenUrl = { openedUrl = it },
                onTextTap = { tapped = true },
            )
        }

        compose.onNodeWithContentDescription("Link example.org").performClick()

        assertEquals("https://example.org/guide", openedUrl)
        assertFalse(tapped)
    }

    private class FakeMentionProfiles(private val known: Map<String, MentionProfile>) : MentionProfilePresentation {
        val requested = mutableListOf<String>()
        override fun profile(mention: PostMention): MentionProfile? = known[mention.handle]
        override fun request(mention: PostMention) {
            requested += mention.handle
        }
        override fun seed(accounts: List<Account>) = Unit
    }

    private val localMention = PostMention("@local@example.org")
    private val localAccount = Account(
        AccountId(Connection("https://example.org", Protocol.MASTODON), "local"),
        "Local :blob_cat: Person",
        "@local@example.org",
    )

    @Test
    fun anUnresolvedMentionShowsTheHandleAndAPersonIconAndRoutesToSearch() {
        val profiles = FakeMentionProfiles(emptyMap())
        val searched = mutableListOf<String>()
        show {
            androidx.compose.runtime.CompositionLocalProvider(LocalMentionProfiles provides profiles) {
                InlineEmojiText(
                    "hi @local", emptyMap(), enableInlineEntities = true,
                    mentions = listOf(localMention), onOpenUsername = { searched += it },
                )
            }
        }

        compose.onNodeWithTag("entity_label", useUnmergedTree = true).assertTextContains("@local", substring = true)
        compose.onAllNodesWithTag("entity_icon", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithContentDescription("Mention: @local, @local@example.org").performClick()

        assertEquals(listOf("@local@example.org"), profiles.requested)
        assertEquals(listOf("@local@example.org"), searched)
    }

    @Test
    fun aResolvedMentionShowsTheDisplayNameAndTheAvatarAndOpensTheProfile() {
        val profiles = FakeMentionProfiles(mapOf(localMention.handle to MentionProfile.Resolved(localAccount)))
        val opened = mutableListOf<Account>()
        val searched = mutableListOf<String>()
        show {
            androidx.compose.runtime.CompositionLocalProvider(LocalMentionProfiles provides profiles) {
                InlineEmojiText(
                    "hi @local", emptyMap(), enableInlineEntities = true,
                    mentions = listOf(localMention), onOpenUsername = { searched += it }, onOpenProfile = { opened += it },
                )
            }
        }

        compose.onNodeWithTag("entity_label", useUnmergedTree = true).assertTextContains("Local Person", substring = true)
        compose.onAllNodesWithTag("entity_avatar", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithContentDescription("Mention: Local Person, @local@example.org").performClick()

        assertEquals(listOf(localAccount), opened)
        assertTrue(searched.isEmpty())
    }

    @Test
    fun aMentionWithABlankDisplayNameFallsBackToTheUserName() {
        val blank = localAccount.copy(displayName = ":blob_cat:")
        val profiles = FakeMentionProfiles(mapOf(localMention.handle to MentionProfile.Resolved(blank)))
        show {
            androidx.compose.runtime.CompositionLocalProvider(LocalMentionProfiles provides profiles) {
                InlineEmojiText("hi @local", emptyMap(), enableInlineEntities = true, mentions = listOf(localMention))
            }
        }

        compose.onNodeWithTag("entity_label", useUnmergedTree = true).assertTextContains("@local", substring = true)
    }
}
