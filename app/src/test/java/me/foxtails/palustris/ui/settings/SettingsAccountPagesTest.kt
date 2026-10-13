package me.foxtails.palustris.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.AppPreferencesState
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The account pages: Blocks and mutes, and the notification account picker. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SettingsAccountPagesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val first = AccountId(Connection("https://example.org", Protocol.MISSKEY), "first")
    private val second = AccountId(Connection("https://example.org", Protocol.MISSKEY), "second")
    private fun ref(id: AccountId) = AccountRef(id, "@${id.localId}", null, id.localId)

    private fun host(accounts: List<AccountRef>, start: SettingsRoute = SettingsRoute.Main): () -> SettingsRoute {
        var route by mutableStateOf(start)
        compose.setContent {
            SettingsHost(
                state = AppPreferencesState(loaded = true),
                accounts = accounts,
                route = route,
                onRoute = { route = it },
                onBack = {},
                onNotificationAccount = { route = SettingsRoute.NotificationAccount(it) },
                onModeration = { accountId, kind -> route = SettingsRoute.Moderation(accountId, kind) },
            )
        }
        return { route }
    }

    private fun back() {
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
    }

    @Test
    fun blocksAndMutesOpensFromTheMainPageAndAListReturnsToIt() {
        val route = host(listOf(ref(first), ref(second)))

        compose.onNodeWithTag("settings_blocks_mutes").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(SettingsRoute.PrivacyAccounts, route())

        compose.onNodeWithTag("moderation_second_Muted").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(SettingsRoute.Moderation(second, ModerationKind.Muted), route())

        back()
        assertEquals(SettingsRoute.PrivacyAccounts, route())
        back()
        assertEquals(SettingsRoute.Main, route())
    }

    @Test
    fun notificationsOpensTheOnlyAccountAtOnceAndBackReturnsToTheMainPage() {
        val route = host(listOf(ref(first)))

        compose.onNodeWithTag("settings_notifications").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(SettingsRoute.NotificationAccount(first), route())

        back()
        assertEquals(SettingsRoute.Main, route())
    }

    @Test
    fun notificationsShowsThePickerForTwoAccountsAndBackReturnsToIt() {
        val route = host(listOf(ref(first), ref(second)))

        compose.onNodeWithTag("settings_notifications").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(SettingsRoute.Notifications, route())

        compose.onNodeWithTag("notification_account_second").performClick()
        compose.waitForIdle()
        assertEquals(SettingsRoute.NotificationAccount(second), route())

        back()
        assertEquals(SettingsRoute.Notifications, route())
    }

    @Test
    fun aRemovedAccountStillFallsBackToThePicker() {
        // The gate uses the fixed parent: after a removal the account count says nothing about the old route.
        assertEquals(
            SettingsRoute.Notifications,
            resolveAccountRoute(SettingsRoute.NotificationAccount(second), listOf(ref(first)), accountsReady = true),
        )
    }
}
