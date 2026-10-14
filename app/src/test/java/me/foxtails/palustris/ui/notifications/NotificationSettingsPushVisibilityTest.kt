package me.foxtails.palustris.ui.notifications

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.foxtails.palustris.data.auth.AccountIndex
import me.foxtails.palustris.data.auth.SessionStore
import me.foxtails.palustris.data.notifications.InMemoryNotificationStore
import me.foxtails.palustris.data.notifications.NotificationPermissionController
import me.foxtails.palustris.data.notifications.NotificationPresentation
import me.foxtails.palustris.data.notifications.NotificationPresentationFactory
import me.foxtails.palustris.data.notifications.NotificationPresenter
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.data.notifications.NotificationSettingsRepository
import me.foxtails.palustris.data.notifications.push.PushRegistrationManager
import me.foxtails.palustris.data.notifications.push.PushRegistrationWorkResult
import me.foxtails.palustris.data.notifications.push.UnifiedPushConnector
import me.foxtails.palustris.data.notifications.work.NotificationWorkScheduler
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.NotificationCapabilities
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ReportRoute
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.ServerFlavor
import me.foxtails.palustris.domain.Session
import me.foxtails.palustris.ui.notifications.NotificationSettingsScreen
import me.foxtails.palustris.ui.notifications.NotificationSettingsUiState
import me.foxtails.palustris.ui.notifications.NotificationSettingsViewModel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The server push section shows only when the server takes push subscriptions (Slice 6). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationSettingsPushVisibilityTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun initializeWorkManager() {
        runCatching {
            WorkManagerTestInitHelper.initializeTestWorkManager(
                ApplicationProvider.getApplicationContext(),
            )
        }
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun pushSectionHidesWhenTheServerTakesNoPushSubscriptions() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                NotificationSettingsScreen(state = NotificationSettingsUiState(pushAvailable = false))
            }
        }

        compose.onNodeWithText("Push provider").assertDoesNotExist()
        compose.onNodeWithText("Test push connection").assertDoesNotExist()
        compose.onNodeWithText("Refresh push providers").assertDoesNotExist()
        compose.onNodeWithText("Test Android notification").assertIsDisplayed()
    }

    @Test
    fun pushSectionShowsWhenTheServerTakesPushSubscriptions() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                NotificationSettingsScreen(state = NotificationSettingsUiState(pushAvailable = true))
            }
        }

        compose.onNodeWithText("Push provider").assertIsDisplayed()
        compose.onNodeWithText("Test push connection").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Test Android notification").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pixelfedSessionStartsWithServerPushHidden() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val account = AccountId(Connection("https://pixelfed.example", Protocol.MASTODON), "viewer")
        val pixelfed = ServerCapabilities(
            notifications = NotificationCapabilities(webPush = CapabilityStatus.Unsupported),
            reportRoute = ReportRoute.V11Report,
            flavor = ServerFlavor.Pixelfed,
        )

        val viewModel = settingsViewModel(account, Session(account, "token", pixelfed))

        assertFalse(viewModel.state.value.pushAvailable)
    }

    @Test
    fun unprobedSessionKeepsServerPushVisible() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val account = AccountId(Connection("https://unprobed.example", Protocol.MASTODON), "viewer")

        val viewModel = settingsViewModel(account, Session(account, "token", ServerCapabilities()))

        assertTrue(viewModel.state.value.pushAvailable)
    }

    private fun settingsViewModel(account: AccountId, session: Session): NotificationSettingsViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = NotificationRepository(InMemoryNotificationStore())
        return NotificationSettingsViewModel(
            account,
            NotificationSettingsRepository(repository),
            repository,
            NotificationWorkScheduler(context),
            NoPushManager(),
            GrantedPushPermission,
            NotificationPresentationFactory(context),
            NoPushPresenter(),
            EmptyDistributors(),
            SessionStoreWith(session),
            context,
        )
    }

    private class SessionStoreWith(private val session: Session?) : SessionStore {
        override fun read(accountId: AccountId): Session? = session?.takeIf { it.accountId == accountId }
        override fun write(accountId: AccountId, session: Session) = Unit
        override fun delete(accountId: AccountId) = Unit
        override fun readIndex(): AccountIndex = AccountIndex()
        override fun writeIndex(index: AccountIndex) = Unit
        override fun clear() = Unit
    }

    private class EmptyDistributors : UnifiedPushConnector {
        override fun availableDistributors(): List<String> = emptyList()
        override fun acknowledgedDistributor(): String? = null
        override fun saveDistributor(packageName: String) = Unit
        override fun register(instanceName: String, messageForDistributor: String?, vapidPublicKey: String?) = Unit
        override fun unregister(instanceName: String) = Unit
    }

    private class NoPushPresenter : NotificationPresenter {
        override fun present(presentation: NotificationPresentation): Boolean = false
        override fun dismiss(accountId: AccountId, notificationId: EntityId) = Unit
    }

    private object GrantedPushPermission : NotificationPermissionController {
        override fun isGranted(): Boolean = true
    }

    private class NoPushManager : PushRegistrationManager {
        override fun onSessionAvailable(accountId: AccountId) = Unit
        override suspend fun enable(accountId: AccountId) = Unit
        override suspend fun retry(accountId: AccountId) = Unit
        override suspend fun processPendingEndpoint(accountId: AccountId) = PushRegistrationWorkResult.NoWork
        override suspend fun disable(accountId: AccountId) = Unit
    }
}
