package me.foxtails.palustris.ui.filters

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import me.foxtails.palustris.domain.ContentFilter
import me.foxtails.palustris.domain.ContentFilterKind
import me.foxtails.palustris.domain.ContentFilterSet
import me.foxtails.palustris.domain.FilterContext
import me.foxtails.palustris.domain.FilterPattern
import me.foxtails.palustris.domain.PostFilterAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The filter list, the delete confirmation, and the editor fields of each filter kind. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContentFilterSettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val filter = ContentFilter(
        id = "f1",
        title = "TV",
        patterns = listOf(FilterPattern.Words(listOf("finale")), FilterPattern.Words(listOf("season"))),
        action = PostFilterAction.Remove,
    )

    @Test
    fun listShowsKeywordsAndActionAndConfirmsBeforeDeleting() {
        var deleted: ContentFilter? = null
        compose.setContent {
            ContentFilterSettingsScreen(
                ContentFilterUiState(set = ContentFilterSet(ContentFilterKind.Titled, listOf(filter))),
                ContentFilterSettingsActions(onDelete = { deleted = it }),
            )
        }

        compose.onNodeWithText("TV").assertIsDisplayed()
        compose.onNodeWithText("finale, season · Removes posts").assertIsDisplayed()

        compose.onNodeWithTag("filter_delete_f1").performClick()
        assertNull(deleted)
        compose.onNodeWithTag("filter_delete_confirm").performClick()
        assertEquals(filter, deleted)
    }

    @Test
    fun titledEditorHasTitleAndSeveralKeywords() {
        var editor = ContentFilterEditor.new(ContentFilterKind.Titled)
        compose.setContent {
            ContentFilterSettingsScreen(
                ContentFilterUiState(set = ContentFilterSet(ContentFilterKind.Titled), editor = editor),
                ContentFilterSettingsActions(onEditor = { change -> editor = change(editor) }),
            )
        }

        compose.onNodeWithTag("filter_title").assertIsDisplayed()
        compose.onNodeWithTag("filter_add_keyword").performClick()
        assertEquals(2, editor.keywords.size)
        compose.onNodeWithTag("filter_context_Thread").performScrollTo().performClick()
        assertEquals(FilterContext.entries.toSet() - FilterContext.Thread, editor.contexts)
        compose.onNodeWithTag("filter_action_remove").performScrollTo().performClick()
        assertEquals(PostFilterAction.Remove, editor.action)
    }

    @Test
    fun phraseEditorHasOneKeywordAndNoTitle() {
        compose.setContent {
            ContentFilterSettingsScreen(
                ContentFilterUiState(set = ContentFilterSet(ContentFilterKind.Phrase), editor = ContentFilterEditor.new(ContentFilterKind.Phrase)),
                ContentFilterSettingsActions(),
            )
        }

        compose.onAllNodesWithTag("filter_title").assertCountEquals(0)
        compose.onAllNodesWithTag("filter_add_keyword").assertCountEquals(0)
        compose.onNodeWithTag("filter_keyword_0").assertIsDisplayed()
        compose.onAllNodesWithTag("filter_expiry_Keep").assertCountEquals(0)
    }

    @Test
    fun wordMutesListInTwoSectionsAndEditInTwoModes() {
        val soft = ContentFilter("s", "finale", listOf(FilterPattern.Words(listOf("finale"), ignoreCase = false)), PostFilterAction.Collapse)
        val hard = ContentFilter("h", "/sp(oi)+ler/", listOf(FilterPattern.Expression("sp(oi)+ler")), PostFilterAction.Remove)
        compose.setContent {
            ContentFilterSettingsScreen(ContentFilterUiState(set = ContentFilterSet(ContentFilterKind.WordMute, listOf(soft, hard))), ContentFilterSettingsActions())
        }

        compose.onNodeWithText("Add word mute").assertIsDisplayed()
        compose.onNodeWithText("Collapse").assertIsDisplayed()
        compose.onNodeWithText("Remove").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Matches capitalization").assertIsDisplayed()
        compose.onNodeWithText("Regular expression").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun advancedWordMuteEditorShowsTheExpressionField() {
        compose.setContent {
            ContentFilterSettingsScreen(
                ContentFilterUiState(
                    set = ContentFilterSet(ContentFilterKind.WordMute),
                    editor = ContentFilterEditor.new(ContentFilterKind.WordMute).copy(mode = WordMuteMode.Advanced),
                    editorError = FilterEditorError.ExpressionRejected,
                ),
                ContentFilterSettingsActions(),
            )
        }

        compose.onNodeWithTag("filter_expression").assertIsDisplayed()
        compose.onAllNodesWithTag("filter_phrase").assertCountEquals(0)
        compose.onAllNodesWithTag("filter_context_Home").assertCountEquals(0)
        compose.onNodeWithText("The server cannot use this regular expression. Try a simpler one.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun editorErrorNamesTheMissingField() {
        compose.setContent {
            ContentFilterSettingsScreen(
                ContentFilterUiState(
                    set = ContentFilterSet(ContentFilterKind.Titled),
                    editor = ContentFilterEditor.new(ContentFilterKind.Titled),
                    editorError = FilterEditorError.MissingTitle,
                ),
                ContentFilterSettingsActions(),
            )
        }

        compose.onNodeWithText("Enter a title.").performScrollTo().assertIsDisplayed()
    }
}
