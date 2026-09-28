package me.foxtails.palustris.data.notifications

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns pending notification launch state and persists accepted launches across recreation. */
@Singleton
class NotificationLaunchRouter internal constructor(
    private val store: NotificationLaunchStore,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(notificationLaunchStore(context))

    constructor() : this(InMemoryNotificationLaunchStore())

    private val _pending = MutableStateFlow(store.read())
    val pending: StateFlow<NotificationLaunch?> = _pending.asStateFlow()

    fun accept(intent: Intent) {
        NotificationLaunchCodec.parse(intent)?.let {
            store.write(it)
            _pending.value = it
        }
    }

    fun clear() {
        store.clear()
        _pending.value = null
    }

    /** Acknowledges only the accepted launch, so a newer launch stays pending. */
    fun clear(launch: NotificationLaunch) {
        if (_pending.value == launch) clear()
    }

    fun parse(intent: Intent): NotificationLaunch? = NotificationLaunchCodec.parse(intent)

    fun parseDismiss(intent: Intent): NotificationLaunch? = NotificationLaunchCodec.parseDismiss(intent)

    companion object {
        const val EXTRA_ORIGIN = NotificationLaunchCodec.EXTRA_ORIGIN
        const val EXTRA_ACCOUNT_LOCAL_ID = NotificationLaunchCodec.EXTRA_ACCOUNT_LOCAL_ID
        const val EXTRA_PROTOCOL = NotificationLaunchCodec.EXTRA_PROTOCOL
        const val EXTRA_NOTIFICATION_ID = NotificationLaunchCodec.EXTRA_NOTIFICATION_ID
        const val SCHEME = NotificationLaunchCodec.SCHEME
        const val HOST = NotificationLaunchCodec.HOST
        const val PATH = NotificationLaunchCodec.PATH
        const val ACTION_DISMISS = NotificationLaunchCodec.ACTION_DISMISS

        fun intentFor(launch: NotificationLaunch) = NotificationLaunchCodec.intentFor(launch)
    }
}
