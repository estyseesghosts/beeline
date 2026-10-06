package me.foxtails.palustris.data.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import androidx.test.core.app.ApplicationProvider
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.data.notifications.NotificationChannelKind
import me.foxtails.palustris.data.notifications.NotificationLaunch
import me.foxtails.palustris.data.notifications.NotificationLaunchRouter
import me.foxtails.palustris.data.notifications.NotificationPresentationFactory
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationLabel
import me.foxtails.palustris.domain.NotificationReaction
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowNotificationManager
import org.robolectric.shadows.ShadowPendingIntent

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class NotificationPresentationTest {
    @Test
    fun reactionTitleUsesTheSameFallbackTextForPlainAndCodedLabels() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val account = AccountId(Connection("https://example.org", Protocol.MISSKEY), "receiver")
        val base = notification(account)
        val factory = NotificationPresentationFactory(context)

        val plain = factory.prepare(
            base.copy(activity = NotificationActivity.EmojiReaction(
                NotificationReaction("heart", NotificationLabel.Plain("heart")),
            )),
            showPreview = true,
            channel = NotificationChannelKind.Social,
        )
        val coded = factory.prepare(
            base.copy(activity = NotificationActivity.EmojiReaction(
                NotificationReaction("heart", NotificationLabel.Coded(me.foxtails.palustris.domain.NotificationLabelCode.Reaction)),
            )),
            showPreview = true,
            channel = NotificationChannelKind.Social,
        )

        assertEquals("Reacted with heart", plain.title)
        assertEquals("Reacted with Reaction", coded.title)
    }

    @Test
    fun emptyPreviewFallsBackToActorsAndPreviewDisabledHidesPostText() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val account = AccountId(Connection("https://example.org", Protocol.MASTODON), "receiver")
        val actor = Account(account.copy(localId = "actor"), "Actor", "@actor@example.org")
        val notification = notification(account).copy(
            actors = listOf(actor),
            post = Post(
                id = EntityId(account.connection.origin, "post"),
                author = actor,
                text = "post text",
                publishedAtEpochMillis = 1L,
                audience = Audience.Public,
            ),
        )
        val factory = NotificationPresentationFactory(context)

        assertEquals("post text", factory.prepare(notification, true, NotificationChannelKind.RepliesAndMentions).body)
        assertEquals(
            "Actor",
            factory.prepare(
                notification.copy(post = notification.post?.copy(text = "  ")),
                true,
                NotificationChannelKind.RepliesAndMentions,
            ).body,
        )
        assertEquals("Actor", factory.prepare(notification, false, NotificationChannelKind.RepliesAndMentions).body)
        assertEquals(
            "Activity from your server",
            factory.prepare(notification.copy(actors = emptyList(), post = null), true, NotificationChannelKind.RepliesAndMentions).body,
        )
    }

    @Test
    fun contentWarningTakesPrecedenceOverPublicPostText() {
        val account = AccountId(Connection("https://example.org", Protocol.MASTODON), "receiver")
        val actor = Account(account.copy(localId = "actor"), "Actor", "@actor@example.org")
        val notification = Notification(
            id = EntityId(account.connection.origin, "event"),
            accountId = account,
            createdAtEpochMillis = 1L,
            activity = NotificationActivity.Mention,
            actors = listOf(actor),
            post = Post(
                id = EntityId(account.connection.origin, "post"),
                author = actor,
                text = "private spoiler text",
                publishedAtEpochMillis = 1L,
                audience = Audience.Public,
                contentWarning = "spoilers",
            ),
            rawType = "mention",
        )

        val presentation = NotificationPresentationFactory(
            ApplicationProvider.getApplicationContext(),
        ).prepare(notification, showPreview = true, channel = NotificationChannelKind.RepliesAndMentions)

        assertTrue(presentation.body.contains("spoilers"))
        assertFalse(presentation.body.contains("private spoiler text"))
    }

    @Test
    fun notificationUsesDistinctTapAndDismissPendingIntentsWithStableIdentity() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val account = AccountId(Connection("https://example.org", Protocol.MISSKEY), "receiver")
        val firstId = EntityId(account.connection.origin, "event-a")
        val secondId = EntityId(account.connection.origin, "event-b")
        val presenter = AndroidNotificationPresenter(context)
        val first = NotificationPresentation(
            account, firstId, "Title", "Body", NotificationChannelKind.Social,
            AndroidNotificationIds.tag(account), AndroidNotificationIds.group(account), AndroidNotificationIds.id(firstId),
        )
        val second = first.copy(notificationId = secondId, androidId = AndroidNotificationIds.id(secondId))

        assertTrue(presenter.present(first))
        val manager = context.getSystemService(NotificationManager::class.java)
        val firstNotification = Shadow.extract<ShadowNotificationManager>(manager).allNotifications.single()
        val tap = firstNotification.contentIntent
        val dismiss = firstNotification.deleteIntent
        checkNotNull(tap)
        checkNotNull(dismiss)
        val tapShadow = Shadow.extract<ShadowPendingIntent>(tap)
        val dismissShadow = Shadow.extract<ShadowPendingIntent>(dismiss)
        assertNotEquals(tap, dismiss)
        assertEquals(first.androidId, tapShadow.requestCode)
        assertEquals(first.androidId, dismissShadow.requestCode)
        assertEquals(PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, tapShadow.flags)
        assertEquals(PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, dismissShadow.flags)
        assertEquals(NotificationLaunchRouter.intentFor(NotificationLaunch(account, firstId)).action, tapShadow.savedIntent.action)
        assertEquals(NotificationLaunchRouter.intentFor(NotificationLaunch(account, firstId)).data, tapShadow.savedIntent.data)
        assertEquals(NotificationLaunchRouter.ACTION_DISMISS, dismissShadow.savedIntent.action)
        assertEquals(MainActivity::class.java.name, tapShadow.savedIntent.component?.className)
        assertEquals(AndroidNotificationDismissReceiver::class.java.name, dismissShadow.savedIntent.component?.className)
        assertEquals(firstId.value, tapShadow.savedIntent.getStringExtra(NotificationLaunchRouter.EXTRA_NOTIFICATION_ID))
        assertEquals(firstId.connection, tapShadow.savedIntent.getStringExtra(NotificationLaunchRouter.EXTRA_ORIGIN))
        assertEquals(first.accountId.localId, dismissShadow.savedIntent.getStringExtra(NotificationLaunchRouter.EXTRA_ACCOUNT_LOCAL_ID))
        assertEquals(first.accountId.connection.protocol.name, dismissShadow.savedIntent.getStringExtra(NotificationLaunchRouter.EXTRA_PROTOCOL))
        assertEquals(firstId.value, dismissShadow.savedIntent.getStringExtra(NotificationLaunchRouter.EXTRA_NOTIFICATION_ID))
        assertEquals(firstId.connection, dismissShadow.savedIntent.getStringExtra(NotificationLaunchRouter.EXTRA_ORIGIN))
        assertEquals("palustris", tapShadow.savedIntent.data?.scheme)
        assertEquals("notification", tapShadow.savedIntent.data?.host)

        assertTrue(presenter.present(second))
        val notifications = Shadow.extract<ShadowNotificationManager>(manager).allNotifications
        assertEquals(2, notifications.size)
        assertNotEquals(first.androidId, second.androidId)

        assertTrue(presenter.present(first.copy(body = "Updated body")))
        val updated = Shadow.extract<ShadowNotificationManager>(manager).allNotifications
        assertEquals(2, updated.size)
        val updatedFirst = updated.single {
            val intent = checkNotNull(it.contentIntent)
            Shadow.extract<ShadowPendingIntent>(intent).savedIntent
                .getStringExtra(NotificationLaunchRouter.EXTRA_NOTIFICATION_ID) == firstId.value
        }
        val updatedTap = checkNotNull(updatedFirst.contentIntent)
        val updatedTapIntent = Shadow.extract<ShadowPendingIntent>(updatedTap).savedIntent
        assertEquals(firstId.value, updatedTapIntent.getStringExtra(NotificationLaunchRouter.EXTRA_NOTIFICATION_ID))
        assertEquals(firstId.connection, updatedTapIntent.getStringExtra(NotificationLaunchRouter.EXTRA_ORIGIN))
    }

    private fun notification(account: AccountId): Notification = Notification(
        id = EntityId(account.connection.origin, "event"),
        accountId = account,
        createdAtEpochMillis = 1L,
        activity = NotificationActivity.Mention,
        rawType = "mention",
    )
}
