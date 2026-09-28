package me.foxtails.palustris.ui.notifications

import android.content.Intent
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.notifications.NotificationLaunch
import me.foxtails.palustris.ui.notifications.NotificationLaunchRouter
import me.foxtails.palustris.ui.notifications.InMemoryNotificationLaunchStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationLaunchRouterTest {
    private val router = NotificationLaunchRouter()

    @Test
    fun pendingLaunchSurvivesRouterRecreation() {
        val store = InMemoryNotificationLaunchStore()
        val launch = NotificationLaunch(account, EntityId(account.connection.origin, "event"))
        NotificationLaunchRouter(store).accept(NotificationLaunchRouter.intentFor(launch))

        val restored = NotificationLaunchRouter(store)
        assertEquals(launch, restored.pending.value)
        restored.clear()
        assertNull(NotificationLaunchRouter(store).pending.value)
    }
    private val account = AccountId(Connection("https://example.org", Protocol.MISSKEY), "receiver")
    private val launch = NotificationLaunch(account, EntityId(account.connection.origin, "event"))

    @Test
    fun roundTripIntentPreservesOnlyValidatedAccountAndEventIdentity() {
        assertEquals(launch, router.parse(NotificationLaunchRouter.intentFor(launch)))
    }

    @Test
    fun accountAndEventSpecificUriPreventsPendingIntentIdentityCollisions() {
        val secondAccount = account.copy(localId = "receiver-b")
        val firstIntent = NotificationLaunchRouter.intentFor(launch)
        val secondIntent = NotificationLaunchRouter.intentFor(NotificationLaunch(secondAccount, launch.notificationId))

        assertNotEquals(firstIntent.data, secondIntent.data)
        assertEquals(launch, router.parse(firstIntent))
        assertNull(router.parse(firstIntent.apply {
            putExtra(NotificationLaunchRouter.EXTRA_ACCOUNT_LOCAL_ID, secondAccount.localId)
        }))
    }

    @Test
    fun sameLocalIdStillDiffersAcrossConnectionAndProtocol() {
        val mastodon = NotificationLaunch(
            AccountId(Connection("https://mastodon.example", Protocol.MASTODON), "receiver"),
            EntityId("https://mastodon.example", "event"),
        )
        val misskey = NotificationLaunch(
            AccountId(Connection("https://misskey.example", Protocol.MISSKEY), "receiver"),
            EntityId("https://misskey.example", "event"),
        )

        val first = NotificationLaunchRouter.intentFor(mastodon)
        val second = NotificationLaunchRouter.intentFor(misskey)
        assertNotEquals(first.data, second.data)
        assertEquals(mastodon, router.parse(first))
        assertEquals(misskey, router.parse(second))
    }

    @Test
    fun authAndForeignNotificationUrisAreIgnored() {
        val auth = Intent(Intent.ACTION_VIEW, "palustris://auth/misskey".toUri())
        val foreign = NotificationLaunchRouter.intentFor(launch).apply {
            data = "palustris://notification/other".toUri()
        }

        assertNull(router.parse(auth))
        assertNull(router.parse(foreign))
    }

    @Test
    fun intentUsesTheViewActionUriAndAllFourContractExtras() {
        val intent = NotificationLaunchRouter.intentFor(launch)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("palustris", intent.data?.scheme)
        assertEquals("notification", intent.data?.host)
        assertEquals(listOf("open", intent.data?.pathSegments?.last()), intent.data?.pathSegments)
        assertEquals("https://example.org", intent.getStringExtra(NotificationLaunchRouter.EXTRA_ORIGIN))
        assertEquals("receiver", intent.getStringExtra(NotificationLaunchRouter.EXTRA_ACCOUNT_LOCAL_ID))
        assertEquals(Protocol.MISSKEY.name, intent.getStringExtra(NotificationLaunchRouter.EXTRA_PROTOCOL))
        assertEquals("event", intent.getStringExtra(NotificationLaunchRouter.EXTRA_NOTIFICATION_ID))
    }

    @Test
    fun bothProtocolEnumValuesRoundTrip() {
        Protocol.entries.forEach { protocol ->
            val account = AccountId(Connection("https://example.org", protocol), "receiver")
            val value = NotificationLaunch(account, EntityId(account.connection.origin, "event"))
            assertEquals(value, router.parse(NotificationLaunchRouter.intentFor(value)))
        }
    }

    @Test
    fun missingBlankAndInvalidFieldsAreRejected() {
        val fields = listOf(
            NotificationLaunchRouter.EXTRA_ORIGIN,
            NotificationLaunchRouter.EXTRA_ACCOUNT_LOCAL_ID,
            NotificationLaunchRouter.EXTRA_PROTOCOL,
            NotificationLaunchRouter.EXTRA_NOTIFICATION_ID,
        )
        fields.forEach { field ->
            assertNull(router.parse(NotificationLaunchRouter.intentFor(launch).putExtra(field, " ")))
            assertNull(router.parse(NotificationLaunchRouter.intentFor(launch).apply { removeExtra(field) }))
        }
        assertNull(router.parse(NotificationLaunchRouter.intentFor(launch).putExtra(
            NotificationLaunchRouter.EXTRA_PROTOCOL,
            "UNKNOWN",
        )))
        assertNull(router.parse(NotificationLaunchRouter.intentFor(launch).putExtra(
            NotificationLaunchRouter.EXTRA_ORIGIN,
            "https://example.org/path",
        )))
    }

    @Test
    fun malformedMismatchedAndForeignUrisAreRejected() {
        val intent = NotificationLaunchRouter.intentFor(launch)
        assertNull(router.parse(intent.apply { data = "palustris://notification/open/not-a-key".toUri() }))
        assertNull(router.parse(intent.apply { data = "palustris://notification/open/${intent.data?.pathSegments?.last()}/extra".toUri() }))
        assertNull(router.parse(intent.apply { data = "https://notification/open/key".toUri() }))
        assertNull(router.parse(intent.apply { data = "palustris://other/open/key".toUri() }))
    }

    @Test
    fun sharedPreferencesStorePreservesFormatAcrossRouterRecreationAndClear() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("notification_launch", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        val first = NotificationLaunchRouter(context)
        first.accept(NotificationLaunchRouter.intentFor(launch))

        val preferences = context.getSharedPreferences("notification_launch", android.content.Context.MODE_PRIVATE)
        assertEquals("https://example.org", preferences.getString("origin", null))
        assertEquals("receiver", preferences.getString("account_local_id", null))
        assertEquals("MISSKEY", preferences.getString("protocol", null))
        assertEquals("event", preferences.getString("notification_id", null))
        assertEquals(launch, NotificationLaunchRouter(context).pending.value)

        first.clear()
        assertTrue(preferences.all.isEmpty())
    }

    @Test
    fun partialAndCorruptStoredValuesBecomeUnavailable() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("notification_launch", android.content.Context.MODE_PRIVATE)
        preferences.edit().clear().putString("origin", "https://example.org").putString("protocol", "NOPE").commit()
        assertNull(NotificationLaunchRouter(context).pending.value)
        preferences.edit().clear().putString("origin", "https://example.org").putString("account_local_id", "receiver")
            .putString("protocol", "MISSKEY").putString("notification_id", "event").commit()
        assertEquals(launch, NotificationLaunchRouter(context).pending.value)
        preferences.edit().clear().commit()
    }

    @Test
    fun dismissActionParsesSeparatelyFromTapAction() {
        val tap = NotificationLaunchRouter.intentFor(launch)
        val dismiss = NotificationLaunchRouter.intentFor(launch).apply {
            action = NotificationLaunchRouter.ACTION_DISMISS
        }
        assertEquals(launch, router.parse(tap))
        assertNull(router.parse(dismiss))
        assertEquals(launch, router.parseDismiss(dismiss))
        assertNull(router.parseDismiss(tap))
    }

    @Test
    fun acknowledgingAnOlderLaunchDoesNotDropANewerLaunch() {
        val router = NotificationLaunchRouter(InMemoryNotificationLaunchStore())
        val first = NotificationLaunch(account, EntityId(account.connection.origin, "event-a"))
        val second = NotificationLaunch(account, EntityId(account.connection.origin, "event-b"))
        router.accept(NotificationLaunchRouter.intentFor(first))
        router.accept(NotificationLaunchRouter.intentFor(second))

        router.clear(first)
        assertEquals(second, router.pending.value)

        router.clear(second)
        assertNull(router.pending.value)
    }
}
