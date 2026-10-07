package me.foxtails.palustris.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.R
import me.foxtails.palustris.data.notifications.NotificationLaunch
import me.foxtails.palustris.data.notifications.NotificationLaunchCodec
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification

data class NotificationPresentation(
    val accountId: AccountId,
    val notificationId: EntityId,
    val title: String,
    val body: String,
    val channel: NotificationChannelKind,
    val tag: String,
    val group: String,
    val androidId: Int,
)

enum class NotificationPresentationAvailability {
    Available,
    PermissionRequired,
    AppDisabled,
    ChannelDisabled,
}

interface NotificationPresenter {
    fun present(presentation: NotificationPresentation): Boolean
    fun dismiss(accountId: AccountId, notificationId: EntityId)
    fun availability(channel: NotificationChannelKind): NotificationPresentationAvailability =
        NotificationPresentationAvailability.Available
}

@Singleton
class NotificationPresentationFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val textResolver = NotificationTextResolver(context)

    fun prepare(
        notification: Notification,
        showPreview: Boolean,
        channel: NotificationChannelKind,
    ): NotificationPresentation {
        val title = textResolver.title(notification.activity)
        val body = textResolver.body(notification, showPreview)
        return NotificationPresentation(
            accountId = notification.accountId,
            notificationId = notification.id,
            title = title,
            body = body,
            channel = channel,
            tag = AndroidNotificationIds.tag(notification.accountId),
            group = AndroidNotificationIds.group(notification.accountId),
            androidId = AndroidNotificationIds.id(notification.id),
        )
    }
}

@Singleton
class AndroidNotificationPresenter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NotificationPresenter {
    private val manager = NotificationManagerCompat.from(context)

    override fun availability(channel: NotificationChannelKind): NotificationPresentationAvailability {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return NotificationPresentationAvailability.PermissionRequired
        if (!manager.areNotificationsEnabled()) return NotificationPresentationAvailability.AppDisabled
        ensureChannels()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(channelId(channel))?.importance == NotificationManager.IMPORTANCE_NONE
        ) return NotificationPresentationAvailability.ChannelDisabled
        return NotificationPresentationAvailability.Available
    }

    override fun present(presentation: NotificationPresentation): Boolean {
        if (availability(presentation.channel) != NotificationPresentationAvailability.Available) return false
        val launch = NotificationLaunch(
            presentation.accountId,
            presentation.notificationId,
        )
        val pendingIntent = PendingIntent.getActivity(
            context,
            presentation.androidId,
            NotificationLaunchCodec.intentFor(launch).setClass(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val deleteIntent = PendingIntent.getBroadcast(
            context,
            presentation.androidId,
            NotificationLaunchCodec.intentFor(launch).apply {
                action = NotificationLaunchCodec.ACTION_DISMISS
                setClass(context, AndroidNotificationDismissReceiver::class.java)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publicVersion = NotificationCompat.Builder(context, channelId(presentation.channel))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notifications_public_title, me.foxtails.palustris.ProductIdentity.name))
            .setContentText(context.getString(R.string.notifications_public_text, me.foxtails.palustris.ProductIdentity.name))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val builder = NotificationCompat.Builder(context, channelId(presentation.channel))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(presentation.title)
            .setContentText(presentation.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(presentation.body))
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deleteIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setGroup(presentation.group)
        return try {
            manager.notify(presentation.tag, presentation.androidId, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    override fun dismiss(accountId: AccountId, notificationId: EntityId) {
        manager.cancel(AndroidNotificationIds.tag(accountId), AndroidNotificationIds.id(notificationId))
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channels = listOf(
            NotificationChannel(CHANNEL_REPLIES, context.getString(R.string.notifications_channel_replies), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_SOCIAL, context.getString(R.string.notifications_channel_social), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_ACCOUNT, context.getString(R.string.notifications_channel_account), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_POLLS, context.getString(R.string.notifications_channel_polls), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_MESSAGES, context.getString(R.string.notifications_channel_messages), NotificationManager.IMPORTANCE_DEFAULT),
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(channels)
    }

    private companion object {
        const val CHANNEL_REPLIES = "notifications.replies"
        const val CHANNEL_SOCIAL = "notifications.social"
        const val CHANNEL_ACCOUNT = "notifications.account"
        const val CHANNEL_POLLS = "notifications.polls"
        const val CHANNEL_MESSAGES = "notifications.messages"

        fun channelId(channel: NotificationChannelKind): String = when (channel) {
            NotificationChannelKind.RepliesAndMentions -> CHANNEL_REPLIES
            NotificationChannelKind.Social -> CHANNEL_SOCIAL
            NotificationChannelKind.Account -> CHANNEL_ACCOUNT
            NotificationChannelKind.Polls -> CHANNEL_POLLS
            NotificationChannelKind.Conversations -> CHANNEL_MESSAGES
        }
    }
}
