package me.foxtails.palustris.ui.navigation

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.large.TabCaretUiState
import me.foxtails.palustris.ui.shell.Destination
import me.foxtails.palustris.ui.shell.NotificationsPanel
import me.foxtails.palustris.ui.shell.SearchPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pins where the compact pill sits when the contextual caret appears and disappears. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class CompactNavigationCaretSlotTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val caret = mutableStateOf<TabCaretUiState?>(TabCaretUiState(expanded = true, onToggle = {}))
    private val action = ContextualNavigationAction(AppIcons.Compose, "Compose", true, {})
    private val shownAction = mutableStateOf<ContextualNavigationAction?>(action)

    private fun show(reserveCaretSlot: Boolean) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme {
                    Box(Modifier.width(360.dp)) {
                        CompactContextualNavigationBar(
                            destination = Destination.Home,
                            searchPanel = SearchPanel.PhotoGrid,
                            notificationsPanel = NotificationsPanel.DirectMessages,
                            action = shownAction.value,
                            account = null,
                            onOpenAccounts = {},
                            onDestinationSelected = {},
                            caretSlot = when {
                                !reserveCaretSlot -> CompactCaretSlot.Inline
                                else -> caret.value?.let(CompactCaretSlot::Shown) ?: CompactCaretSlot.Empty
                            },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun boundsOf(description: String) =
        compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot

    @Test fun reservedCaretSlotKeepsThePillAndActionInPlaceWhenTheCaretHides() {
        show(reserveCaretSlot = true)
        val pill = boundsOf("Home")
        val actionBounds = boundsOf("Compose")
        val barCenter = 360f * compose.activity.resources.displayMetrics.density / 2f
        val pillCenter = (pill.left + boundsOf("Profile").right) / 2f
        assertEquals(barCenter, pillCenter, 1f)
        compose.runOnIdle { caret.value = null }
        compose.waitForIdle()
        assertEquals(pill, boundsOf("Home"))
        assertEquals(actionBounds, boundsOf("Compose"))
    }

    @Test fun inlineCaretBarRecentersTheGroupWhenTheActionHides() {
        show(reserveCaretSlot = false)
        val pill = boundsOf("Home")
        compose.runOnIdle { shownAction.value = null }
        compose.waitForIdle()
        assertNotEquals(pill, boundsOf("Home"))
    }
}
