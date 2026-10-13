package me.foxtails.palustris.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.AppPreferences
import me.foxtails.palustris.domain.AppPreferencesState
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.HiddenContentPresentation
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.Protocol
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Content warnings page edits the rules for all accounts. Its child page edits the active account. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ContentWarningSettingsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aSwitchWritesAtOnceAndTheListsWriteOnSave() {
        var rules by mutableStateOf(ContentWarningRules())
        val writes = mutableListOf<ContentWarningRules>()
        compose.setContent {
            ContentWarningSettingsScreen(rules, onChanged = { writes += it; rules = it })
        }

        compose.onNodeWithTag("content_warning_hide_all").assertIsOff().performClick()
        compose.onNodeWithTag("content_warning_hide_all").assertIsOn()
        assertEquals(listOf(ContentWarningRules(hideAll = true)), writes)

        compose.onNodeWithTag("content_warning_hide_keywords").performScrollTo().performTextReplacement("spoiler, , food")
        assertEquals(1, writes.size)
        compose.onNodeWithTag("content_warning_save").performScrollTo().performClick()

        assertEquals(ContentWarningRules(hideAll = true, hideKeywords = listOf("spoiler", "food")), writes.last())
    }

    @Test
    fun theHiddenPresentationChoiceShowsAndReportsItsState() {
        var presentation by mutableStateOf(HiddenContentPresentation.Placeholder)
        compose.setContent {
            ContentWarningSettingsScreen(
                ContentWarningRules(),
                onChanged = {},
                hiddenPresentation = presentation,
                onHiddenPresentation = { presentation = it },
            )
        }

        compose.onNodeWithTag("hidden_presentation_Placeholder").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("hidden_presentation_Remove").performScrollTo().performClick()

        compose.onNodeWithTag("hidden_presentation_Remove").assertIsSelected()
        assertEquals(HiddenContentPresentation.Remove, presentation)
    }

    @Test
    fun thePageHasNoAccountRowWithoutAnAccount() {
        compose.setContent { ContentWarningSettingsScreen(ContentWarningRules(), onChanged = {}) }

        compose.onNodeWithTag("content_warning_account_rules").assertDoesNotExist()
    }

    @Test
    fun theAccountPageWritesAccountRulesAndBackReturnsToTheParentPage() {
        var route by mutableStateOf<SettingsRoute>(SettingsRoute.ContentWarnings)
        var accountRules by mutableStateOf(ContentWarningRules())
        var globalWrites = 0
        compose.setContent {
            SettingsHost(
                state = AppPreferencesState(preferences = AppPreferences(), loaded = true),
                route = route,
                onRoute = { route = it },
                onBack = {},
                onContentWarningRules = { globalWrites++ },
                postPreferences = PostPreferences(contentWarningRules = accountRules),
                postPreferencesAccountLabel = "@person@example.org",
                onPostContentWarningRules = { accountRules = it },
            )
        }

        compose.onNodeWithTag("content_warning_account_rules").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(SettingsRoute.AccountContentWarnings, route)
        compose.onNodeWithText("These settings apply to @person@example.org.").assertIsDisplayed()

        compose.onNodeWithTag("account_content_warning_expand_all").performClick()
        compose.waitForIdle()
        assertEquals(ContentWarningRules(expandAll = true), accountRules)
        assertEquals(0, globalWrites)

        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        assertEquals(SettingsRoute.ContentWarnings, route)
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        assertEquals(SettingsRoute.Main, route)
    }

    @Test
    fun everyChildRouteReturnsToItsParentPage() {
        val account = AccountId(Connection("https://example.org", Protocol.MISSKEY), "person")

        assertEquals(SettingsRoute.ContentWarnings, SettingsRoute.AccountContentWarnings.parent())
        assertEquals(SettingsRoute.Notifications, SettingsRoute.NotificationAccount(account).parent())
        assertEquals(SettingsRoute.PrivacyAccounts, SettingsRoute.Moderation(account, ModerationKind.Muted).parent())
        assertEquals(SettingsRoute.Main, SettingsRoute.ContentWarnings.parent())
    }
}
