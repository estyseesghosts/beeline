package me.foxtails.palustris.ui.emoji

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EmojiPickerGroupIds
import me.foxtails.palustris.domain.EmojiPickerPreferences
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Reaction
import me.foxtails.palustris.domain.ReactionSelectionMode
import me.foxtails.palustris.domain.ValidatedUrl
import me.foxtails.palustris.ui.PalustrisTheme
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
class EmojiPickerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val account = Account(
        AccountId(Connection("https://example.org", Protocol.MASTODON), "person"),
        "Person",
        "@person@example.org",
    )
    private val post = Post(
        id = EntityId("https://example.org", "post"),
        author = account,
        text = "Post",
        publishedAtEpochMillis = 0,
        audience = Audience.Public,
        reactions = listOf(
            Reaction(":blob:", 2, selected = true),
            Reaction("👍", 1, selected = false),
        ),
    )
    private val catalogEmoji = listOf(
        CustomEmoji(
            shortcode = "blob",
            animatedUrl = ValidatedUrl.https("https://cdn.example/blob.gif"),
            staticUrl = ValidatedUrl.https("https://cdn.example/blob.png"),
            category = "blobs",
            submissionValue = ":blob:",
        ),
        CustomEmoji(
            shortcode = "wave",
            animatedUrl = ValidatedUrl.https("https://cdn.example/wave.gif"),
            staticUrl = ValidatedUrl.https("https://cdn.example/wave.png"),
            category = null,
            submissionValue = ":wave:",
        ),
    )

    private fun show(
        target: EmojiPickerTarget?,
        catalog: EmojiCatalogState = EmojiCatalogState(items = catalogEmoji),
        mutationSupported: Boolean = true,
        selectionMode: ReactionSelectionMode = ReactionSelectionMode.Single,
        onEmojiSelected: (EmojiChoice) -> Unit = {},
        onTogglePinnedEmoji: (String) -> Unit = {},
        onLoadCatalog: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                EmojiPickerHost(
                    target = target,
                    catalog = catalog,
                    selectionMode = selectionMode,
                    mutationSupported = mutationSupported,
                    onLoadCatalog = onLoadCatalog,
                    onRetryCatalog = {},
                    onDismiss = onDismiss,
                    onEmojiSelected = onEmojiSelected,
                    onTogglePinnedEmoji = onTogglePinnedEmoji,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun nullTargetRendersNothing() {
        show(
            target = null,
        )
        compose.onNodeWithTag("emoji_picker_sheet").assertDoesNotExist()
    }

    @Test
    fun reactionTargetShowsPickerVisibleServerEntriesAndUnicodeDefaults() {
        show(target = EmojiPickerTarget.Reaction(OwnedPost(account.id, post)))

        compose.onNodeWithTag("emoji_picker_sheet").assertIsDisplayed()
        compose.onNodeWithTag("emoji_picker_scrollbar").assertIsDisplayed()
        compose.onNodeWithTag("emoji_picker_cell_:blob:", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("emoji_picker_cell_:wave:", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun selectingACellReportsTheStructuredChoice() {
        var selected: EmojiChoice? = null
        show(
            target = EmojiPickerTarget.Reaction(OwnedPost(account.id, post)),
            onEmojiSelected = { selected = it },
        )

        compose.onNodeWithTag("emoji_picker_cell_:blob:", useUnmergedTree = true).performClick()

        assertEquals(":blob:", selected?.submissionValue)
        assertEquals("blob", selected?.emoji?.shortcode)
    }

    @Test
    fun searchFiltersChoices() {
        show(target = EmojiPickerTarget.Composer(ComposerField.Text))

        compose.onNodeWithTag("emoji_picker_search").performTextInput("wave")
        compose.onNodeWithTag("emoji_picker_cell_:wave:", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("emoji_picker_cell_:blob:", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun searchCloseClearsQueryWithoutDismissingSheet() {
        show(target = EmojiPickerTarget.Composer(ComposerField.Text))

        compose.onNodeWithTag("emoji_picker_search").performTextInput("wave")
        compose.onNodeWithTag("emoji_picker_hide_keyboard").performClick()

        compose.onNodeWithTag("emoji_picker_sheet").assertIsDisplayed()
        compose.onNodeWithTag("emoji_picker_cell_:wave:", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("emoji_picker_cell_:blob:", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun emptySearchCloseDismissesThroughTheHost() {
        var dismissed = false
        show(
            target = EmojiPickerTarget.Composer(ComposerField.Text),
            onDismiss = { dismissed = true },
        )

        compose.onNodeWithTag("emoji_picker_hide_keyboard").performClick()

        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun keyboardDoneHidesKeyboardWithoutDismissingSheet() {
        var dismissed = false
        show(
            target = EmojiPickerTarget.Composer(ComposerField.Text),
            onDismiss = { dismissed = true },
        )

        compose.onNodeWithTag("emoji_picker_search").performTextInput("wave")
        compose.onNodeWithTag("emoji_picker_search").performImeAction()

        compose.onNodeWithTag("emoji_picker_sheet").assertIsDisplayed()
        compose.runOnIdle { assertTrue(!dismissed) }
    }

    @Test
    fun readOnlyReactionListAppearsWhenMutationIsUnsupported() {
        show(
            target = EmojiPickerTarget.Reaction(OwnedPost(account.id, post)),
            mutationSupported = false,
        )

        compose.onNodeWithTag("emoji_picker_sheet").assertIsDisplayed()
        compose.onNodeWithTag("emoji_picker_grid").assertDoesNotExist()
        compose.onNodeWithText(":blob:", substring = true).assertIsDisplayed()
        compose.onNodeWithText("👍", substring = true).assertIsDisplayed()
    }

    @Test
    fun loadingAndEmptyStatesRemainReadable() {
        show(target = EmojiPickerTarget.Composer(ComposerField.Warning), catalog = EmojiCatalogState(initialLoading = true))
        compose.onNodeWithTag("emoji_picker_sheet").assertIsDisplayed()
        show(target = EmojiPickerTarget.Composer(ComposerField.Warning), catalog = EmojiCatalogState(empty = true))
        compose.onNodeWithTag("emoji_picker_sheet").assertIsDisplayed()
    }

    @Test
    fun categorySectionsAppearInTheGrid() {
        show(target = EmojiPickerTarget.Composer(ComposerField.Text))

        compose.onNodeWithText("blobs").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTag("emoji_picker_cell_:blob:").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun extractedGridSupportsCompactCustomChoicesAndExpandedSelection() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    EmojiChoiceGrid(
                        catalogItems = catalogEmoji,
                        selectedIdentities = setOf(":blob:"),
                        compact = true,
                        testTag = "reaction_bubble_grid",
                        onEmojiSelected = {},
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("reaction_bubble_grid").assertIsDisplayed()
        compose.onNodeWithTag("emoji_picker_cell_:blob:", useUnmergedTree = true).assertIsSelected()
        compose.onNodeWithTag("emoji_picker_cell_👍", useUnmergedTree = true).assertIsDisplayed()

        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    EmojiChoiceGrid(
                        catalogItems = catalogEmoji,
                        selectedIdentities = setOf(":blob:"),
                        onEmojiSelected = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("emoji_picker_cell_:blob:", useUnmergedTree = true).assertIsSelected()
        compose.onNodeWithTag("emoji_picker_cell_:wave:", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun fullGroupsFollowPinnedRecentPostSpecificServerAndUnicodeOrder() {
        val groups = buildEmojiPickerGroups(
            catalogItems = catalogEmoji + catalogEmoji.first().copy(shortcode = "other", submissionValue = ":other:", category = "other"),
            additionalChoices = listOf(
                EmojiChoice(":missing:", ":missing:"),
                EmojiChoice("🎈", "🎈"),
            ),
            recentIdentities = listOf(":blob:", "🎈"),
            selectedIdentities = setOf(":blob:"),
            searchQuery = "",
            preferences = EmojiPickerPreferences(
                pinnedGroups = listOf("server:blobs"),
                pinnedEmoji = listOf(":blob:"),
            ),
        )

        assertEquals(
            listOf(
                EmojiPickerGroupIds.Favorite,
                "server:blobs",
                EmojiPickerGroupIds.Recent,
                EmojiPickerGroupIds.PostSpecific,
                "server:",
                "server:other",
                EmojiPickerGroupIds.Unicode,
            ),
            groups.map { it.id },
        )
        assertEquals("🎈", groups[2].choices.first().submissionValue)
        assertEquals(":missing:", groups[3].choices.first().submissionValue)
        assertEquals(":blob:", groups[0].choices.first().submissionValue)
    }

    @Test
    fun longPressingUnpinnedEmojiOpensPinConfirmationWithoutSelecting() {
        var selected: EmojiChoice? = null
        var pinned: String? = null
        show(
            target = EmojiPickerTarget.Composer(ComposerField.Text),
            onEmojiSelected = { selected = it },
            onTogglePinnedEmoji = { pinned = it },
        )

        compose.onNodeWithTag("emoji_picker_cell_:wave:", useUnmergedTree = true).performTouchInput { longClick() }

        compose.onNodeWithTag("emoji_pin_confirmation").assertIsDisplayed()
        compose.onNodeWithText("pin emoji?").assertIsDisplayed()
        compose.onNodeWithTag("emoji_pin_confirmation").performClick()

        assertEquals(null, selected)
        assertEquals(":wave:", pinned)
        compose.onNodeWithTag("emoji_pin_confirmation").assertDoesNotExist()
    }

    @Test
    fun pendingTileIgnoresTapsAndReportsSaving() {
        var selected: EmojiChoice? = null
        show(
            target = EmojiPickerTarget.Composer(ComposerField.Text),
            catalog = EmojiCatalogState(items = catalogEmoji, pendingPins = setOf(":wave:")),
            onEmojiSelected = { selected = it },
        )

        val tile = compose.onNodeWithTag("emoji_picker_cell_:wave:", useUnmergedTree = true)
        tile.performClick()

        assertEquals(null, selected)
        tile.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Saving"))
        assertFalse(tile.fetchSemanticsNode().config.contains(SemanticsActions.CustomActions))
    }

    @Test
    fun failedPinWriteShowsMessageAndNoPinnedState() {
        show(
            target = EmojiPickerTarget.Composer(ComposerField.Text),
            catalog = EmojiCatalogState(items = catalogEmoji, pinFailed = true),
        )

        compose.onNodeWithTag("emoji_pin_failure").assertIsDisplayed()
        compose.onNodeWithTag("emoji_picker_cell_:wave:", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Unpinned emoji"))
    }

    @Test
    fun pinAccessibilityActionOpensTheSameConfirmationAsLongPress() {
        var pinned: String? = null
        show(
            target = EmojiPickerTarget.Composer(ComposerField.Text),
            onTogglePinnedEmoji = { pinned = it },
        )

        val actions = compose.onNodeWithTag("emoji_picker_cell_:wave:", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Pin emoji"), actions.map { it.label })
        compose.activity.runOnUiThread { actions.single().action() }
        compose.waitForIdle()

        compose.onNodeWithText("pin emoji?").assertIsDisplayed()
        assertEquals(null, pinned)
        compose.onNodeWithTag("emoji_pin_confirmation").performClick()
        assertEquals(":wave:", pinned)
    }

    @Test
    fun combinedGlyphTileKeepsTheMinimumTarget() {
        val family = "👨‍👩‍👧"
        val combined = post.copy(reactions = listOf(Reaction(family, 1, selected = false)))
        show(target = EmojiPickerTarget.Reaction(OwnedPost(account.id, combined, 1L)))
        compose.onNodeWithTag("emoji_picker_search").performTextInput(family)

        compose.onNodeWithTag("emoji_picker_cell_$family", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    @Config(fontScale = 2f)
    fun tilesKeepTheMinimumTargetAtDoubleFontScale() {
        show(target = EmojiPickerTarget.Composer(ComposerField.Text))

        compose.onNodeWithTag("emoji_picker_cell_:wave:", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun recentsSurviveExpandingFromCompactToFull() {
        val recents = EmojiRecents()
        var compact by mutableStateOf(true)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                if (compact) {
                    EmojiChoiceGrid(catalogItems = catalogEmoji, compact = true, recents = recents, onEmojiSelected = {})
                } else {
                    EmojiChoiceGrid(catalogItems = catalogEmoji, compact = false, recents = recents, onEmojiSelected = {})
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("emoji_picker_cell_:wave:", useUnmergedTree = true).performClick()
        assertEquals(listOf(":wave:"), recents.identities)
        compose.runOnIdle { compact = false }
        compose.waitForIdle()

        compose.onNodeWithText("Recent").assertIsDisplayed()
        assertEquals(listOf(":wave:"), recents.identities)
    }

    @Test
    fun pinConfirmationClearsWhenTheCatalogScopeChanges() {
        var catalog by mutableStateOf(EmojiCatalogState(items = catalogEmoji, scope = "first"))
        var pinned: String? = null
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                EmojiPickerHost(
                    target = EmojiPickerTarget.Composer(ComposerField.Text),
                    catalog = catalog,
                    selectionMode = ReactionSelectionMode.Single,
                    mutationSupported = true,
                    onLoadCatalog = {},
                    onRetryCatalog = {},
                    onDismiss = {},
                    onEmojiSelected = {},
                    onTogglePinnedEmoji = { pinned = it },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("emoji_picker_cell_:wave:", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithTag("emoji_pin_confirmation").assertIsDisplayed()

        compose.runOnIdle { catalog = catalog.copy(scope = "second") }
        compose.waitForIdle()

        compose.onNodeWithTag("emoji_pin_confirmation").assertDoesNotExist()
        assertEquals(null, pinned)
    }

    @Test
    fun longPressWithoutConfirmationNeitherSelectsNorPins() {
        var selected: EmojiChoice? = null
        var pinned: String? = null
        show(
            target = EmojiPickerTarget.Composer(ComposerField.Text),
            onEmojiSelected = { selected = it },
            onTogglePinnedEmoji = { pinned = it },
        )

        compose.onNodeWithTag("emoji_picker_cell_:wave:", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithTag("emoji_pin_confirmation").assertIsDisplayed()

        assertEquals(null, selected)
        assertEquals(null, pinned)
    }

    @Test
    fun expandingKeepsTheSelectedReactionSelected() {
        var compact by mutableStateOf(true)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                if (compact) {
                    EmojiChoiceGrid(
                        catalogItems = catalogEmoji,
                        selectedIdentities = setOf(":blob:"),
                        compact = true,
                        onEmojiSelected = {},
                    )
                } else {
                    EmojiChoiceGrid(
                        catalogItems = catalogEmoji,
                        selectedIdentities = setOf(":blob:"),
                        compact = false,
                        onEmojiSelected = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("emoji_picker_cell_:blob:", useUnmergedTree = true).assertIsSelected()

        compose.runOnIdle { compact = false }
        compose.waitForIdle()

        compose.onNodeWithTag("emoji_picker_cell_:blob:", useUnmergedTree = true).assertIsSelected()
    }

    @Test
    fun longPressingPinnedEmojiOpensRemovalConfirmation() {
        var pinned: String? = null
        show(
            target = EmojiPickerTarget.Composer(ComposerField.Text),
            catalog = EmojiCatalogState(
                items = catalogEmoji,
                preferences = me.foxtails.palustris.domain.EmojiPickerPreferences(pinnedEmoji = listOf(":wave:")),
            ),
            onTogglePinnedEmoji = { pinned = it },
        )

        compose.onNodeWithTag("emoji_picker_cell_:wave:", useUnmergedTree = true).performTouchInput { longClick() }

        compose.onNodeWithText("remove emoji?").assertIsDisplayed()
        compose.onNodeWithTag("emoji_pin_confirmation").performClick()
        assertEquals(":wave:", pinned)
    }

    @Test
    fun favoriteGroupPreservesPinnedCustomAndUnicodeOrderWithoutDuplicates() {
        val groups = buildEmojiPickerGroups(
            catalogItems = catalogEmoji,
            additionalChoices = listOf(EmojiChoice("🎈", "🎈")),
            recentIdentities = listOf(":blob:", "🎈"),
            selectedIdentities = emptySet(),
            searchQuery = "",
            preferences = EmojiPickerPreferences(pinnedEmoji = listOf("🎈", ":blob:", "🎈")),
        )

        assertEquals(EmojiPickerGroupIds.Favorite, groups.first().id)
        assertEquals(listOf("🎈", ":blob:"), groups.first().choices.map { it.submissionValue })
        assertTrue(groups.drop(1).none { group -> group.choices.any { it.submissionValue == "🎈" || it.submissionValue == ":blob:" } })
    }

    @Test
    fun collapsedGroupsStayCollapsedWhenSearching() {
        val groups = buildEmojiPickerGroups(
            catalogItems = catalogEmoji,
            additionalChoices = emptyList(),
            recentIdentities = emptyList(),
            selectedIdentities = emptySet(),
            searchQuery = "blob",
            preferences = EmojiPickerPreferences(collapsedGroups = setOf("server:blobs")),
        )

        val blobs = groups.first { it.id == "server:blobs" }
        assertTrue(blobs.collapsed)
        assertTrue(blobs.choices.isEmpty())
    }

    @Test
    fun groupHeadersExposeIndependentCollapseAndPinControls() {
        var collapsed: String? = null
        var pinned: String? = null
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    EmojiChoiceGrid(
                        catalogItems = catalogEmoji,
                        preferences = me.foxtails.palustris.domain.EmojiPickerPreferences(
                            pinnedEmoji = listOf(":blob:"),
                        ),
                        actions = EmojiPreferenceActions(
                            toggleGroupCollapsed = { collapsed = it },
                            toggleGroupPinned = { pinned = it },
                        ),
                        onEmojiSelected = {},
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Collapse Favorite Emoji", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Pin blobs", useUnmergedTree = true).performClick()

        assertEquals(EmojiPickerGroupIds.Favorite, collapsed)
        assertEquals(EmojiPickerGroupIds.server("blobs"), pinned)
    }
}
