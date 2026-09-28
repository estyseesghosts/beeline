package me.foxtails.palustris.data.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Protocol

interface NotificationLaunchStore {
    fun read(): NotificationLaunch?
    fun write(launch: NotificationLaunch)
    fun clear()
}

private const val PREFERENCES = "notification_launch"
private const val KEY_ORIGIN = "origin"
private const val KEY_ACCOUNT_LOCAL_ID = "account_local_id"
private const val KEY_PROTOCOL = "protocol"
private const val KEY_NOTIFICATION_ID = "notification_id"

private class SharedPreferencesNotificationLaunchStore(
    context: Context,
) : NotificationLaunchStore {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun read(): NotificationLaunch? {
        val origin = preferences.getString(KEY_ORIGIN, null) ?: return null
        val localId = preferences.getString(KEY_ACCOUNT_LOCAL_ID, null) ?: return null
        val protocol = preferences.getString(KEY_PROTOCOL, null) ?: return null
        val notificationId = preferences.getString(KEY_NOTIFICATION_ID, null) ?: return null
        return runCatching {
            val connection = Connection(origin, Protocol.valueOf(protocol))
            NotificationLaunch(AccountId(connection, localId), EntityId(origin, notificationId))
        }.getOrNull()
    }

    override fun write(launch: NotificationLaunch) {
        preferences.edit()
            .putString(KEY_ORIGIN, launch.accountId.connection.origin)
            .putString(KEY_ACCOUNT_LOCAL_ID, launch.accountId.localId)
            .putString(KEY_PROTOCOL, launch.accountId.connection.protocol.name)
            .putString(KEY_NOTIFICATION_ID, launch.notificationId.value)
            .apply()
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }
}

internal class InMemoryNotificationLaunchStore : NotificationLaunchStore {
    private var value: NotificationLaunch? = null

    override fun read(): NotificationLaunch? = value

    override fun write(launch: NotificationLaunch) {
        value = launch
    }

    override fun clear() {
        value = null
    }
}

internal fun notificationLaunchStore(@ApplicationContext context: Context): NotificationLaunchStore =
    SharedPreferencesNotificationLaunchStore(context)
