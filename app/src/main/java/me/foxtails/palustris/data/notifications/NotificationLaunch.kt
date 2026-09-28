package me.foxtails.palustris.data.notifications

import android.content.Intent
import androidx.core.net.toUri
import java.security.MessageDigest
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Protocol

data class NotificationLaunch(
    val accountId: AccountId,
    val notificationId: EntityId,
)

/** Encodes and validates the app-owned notification tap and dismiss intent contract. */
object NotificationLaunchCodec {
    const val EXTRA_ORIGIN = "me.foxtails.palustris.notification.origin"
    const val EXTRA_ACCOUNT_LOCAL_ID = "me.foxtails.palustris.notification.account"
    const val EXTRA_PROTOCOL = "me.foxtails.palustris.notification.protocol"
    const val EXTRA_NOTIFICATION_ID = "me.foxtails.palustris.notification.id"
    const val SCHEME = "palustris"
    const val HOST = "notification"
    const val PATH = "/open"
    const val ACTION_DISMISS = "me.foxtails.palustris.action.NOTIFICATION_DISMISSED"

    fun intentFor(launch: NotificationLaunch): Intent = Intent(Intent.ACTION_VIEW).apply {
        data = "$SCHEME://$HOST$PATH/${launchKey(launch)}".toUri()
        putExtra(EXTRA_ORIGIN, launch.accountId.connection.origin)
        putExtra(EXTRA_ACCOUNT_LOCAL_ID, launch.accountId.localId)
        putExtra(EXTRA_PROTOCOL, launch.accountId.connection.protocol.name)
        putExtra(EXTRA_NOTIFICATION_ID, launch.notificationId.value)
    }

    fun parse(intent: Intent): NotificationLaunch? = parse(intent, Intent.ACTION_VIEW)

    fun parseDismiss(intent: Intent): NotificationLaunch? = parse(intent, ACTION_DISMISS)

    private fun parse(intent: Intent, expectedAction: String): NotificationLaunch? {
        if (intent.action != expectedAction) return null
        val origin = intent.getStringExtra(EXTRA_ORIGIN)?.trim().orEmpty()
        val localId = intent.getStringExtra(EXTRA_ACCOUNT_LOCAL_ID)?.trim().orEmpty()
        val protocol = intent.getStringExtra(EXTRA_PROTOCOL)?.trim().orEmpty()
        val notificationId = intent.getStringExtra(EXTRA_NOTIFICATION_ID)?.trim().orEmpty()
        if (origin.isBlank() || localId.isBlank() || protocol.isBlank() || notificationId.isBlank()) return null
        val uri = intent.data ?: return null
        if (uri.scheme != SCHEME || uri.host != HOST || uri.pathSegments.size != 2 || uri.pathSegments.first() != "open") return null
        val connection = runCatching { Connection(origin, Protocol.valueOf(protocol)) }.getOrNull() ?: return null
        if (connection.origin != origin) return null
        val launch = NotificationLaunch(AccountId(connection, localId), EntityId(origin, notificationId))
        return launch.takeIf { uri.pathSegments.last() == launchKey(it) }
    }

    private fun launchKey(launch: NotificationLaunch): String = MessageDigest.getInstance("SHA-256")
        .digest(("${launch.accountId.connection.origin}\u0000${launch.accountId.connection.protocol}\u0000" +
            "${launch.accountId.localId}\u0000${launch.notificationId.value}").toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
