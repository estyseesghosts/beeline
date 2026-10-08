package me.foxtails.palustris.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.large.LargeNavTarget
import me.foxtails.palustris.ui.motion.motionDirection
import me.foxtails.palustris.ui.shell.Destination
import me.foxtails.palustris.ui.shell.LargePostOrigin
import me.foxtails.palustris.ui.shell.LocalPage
import me.foxtails.palustris.ui.shell.NotificationsPanel
import me.foxtails.palustris.ui.shell.Overlay
import me.foxtails.palustris.ui.shell.SearchPanel

/**
 * Navigation state holder for the application shell.
 *
 * The holder owns shell navigation and selection state behind one boundary. Transitions run
 * here and report side effects through the event callbacks: transient-popup clearing,
 * search execution, and conversation start. Session-bound validation (media, reactions,
 * guarded closes) and feature-contract reads stay with the shell. Saved fields survive
 * process recreation through [Saver]. Restored state binds to its session owner through
 * [bindSession] before display. Transient selection also clears on account change through
 * [resetForAccount].
 */
internal enum class NavigatorSessionBind {
    FRESH,
    MATCHING,
    MISMATCHED,
}

internal class ShellNavigator internal constructor() {
    var destination by mutableStateOf(Destination.Home)
    var destinationTransitionDirection by mutableStateOf(0)
    var timeline by mutableStateOf(Timeline.Home)
    var page by mutableStateOf<LocalPage?>(null)
    var sheet by mutableStateOf<String?>(null)
    var searchPanelName by mutableStateOf(SearchPanel.Search.name)
    var searchQuery by mutableStateOf("")
    var searchCategory by mutableStateOf(0)
    var searchPrefill by mutableStateOf("")
    var notificationsPanelName by mutableStateOf(NotificationsPanel.Notifications.name)
    var navigationVisible by mutableStateOf(true)
    var viewedProfile by mutableStateOf<Account?>(null)
    var singlePost by mutableStateOf<OwnedPost?>(null)
    var singlePostOrigin by mutableStateOf(LargePostOrigin.Other)
    var notificationRoute by mutableStateOf<AppRoute?>(null)

    internal var overlayKey by mutableStateOf<String?>(null)

    internal var boundOrigin: String? = null
    internal var boundProtocolName: String? = null
    internal var boundLocalId: String? = null
    internal var boundRevision: Long? = null

    var reducedMotion: Boolean = false
    var onClearTransient: () -> Unit = {}
    var onSearch: (String) -> Unit = {}
    var onStartConversation: (Account) -> Unit = {}

    val overlay: Overlay?
        get() = when (overlayKey) {
            COMPOSER_OVERLAY_KEY -> Overlay.Composer
            EDIT_PROFILE_OVERLAY_KEY -> Overlay.EditProfile
            NOTIFICATION_SETTINGS_OVERLAY_KEY -> Overlay.NotificationSettings
            else -> null
        }
    val searchPanel: SearchPanel
        get() = SearchPanel.valueOf(searchPanelName)
    val notificationsPanel: NotificationsPanel
        get() = NotificationsPanel.valueOf(notificationsPanelName)

    fun openOverlayKey(key: String) {
        overlayKey = key
    }

    fun closeOverlay() {
        overlayKey = null
    }

    fun openComposerOverlay() {
        overlayKey = COMPOSER_OVERLAY_KEY
    }

    fun openEditProfileOverlay() {
        overlayKey = EDIT_PROFILE_OVERLAY_KEY
    }

    /**
     * Closes an edit-profile overlay that has no live editor behind it. A restored overlay key or a
     * key carried across an account change outlives the profile editor, which lives with the
     * session's profile owner. Call it only when the owner is bound, never on a fresh open.
     */
    fun closeEditProfileWithoutEditor(editorOpen: Boolean) {
        if (overlay == Overlay.EditProfile && !editorOpen) closeOverlay()
    }

    fun openNotificationSettingsOverlay() {
        overlayKey = NOTIFICATION_SETTINGS_OVERLAY_KEY
    }

    fun clearSelectedPost() {
        singlePost = null
        singlePostOrigin = LargePostOrigin.Other
    }

    fun openSinglePost(post: OwnedPost, origin: LargePostOrigin = LargePostOrigin.Other) {
        onClearTransient()
        singlePost = post
        singlePostOrigin = origin
    }

    fun selectDestination(item: Destination) {
        onClearTransient()
        navigationVisible = true
        if (item == Destination.Profile) viewedProfile = null
        destinationTransitionDirection = motionDirection(destination.ordinal, item.ordinal, reducedMotion)
        destination = item
        page = null
        notificationRoute = null
    }

    fun openProfile(profile: Account) {
        onClearTransient()
        clearSelectedPost()
        viewedProfile = profile
        destinationTransitionDirection = motionDirection(destination.ordinal, Destination.Profile.ordinal, reducedMotion)
        destination = Destination.Profile
        page = null
        sheet = null
        notificationRoute = null
    }

    fun selectLargeTarget(target: LargeNavTarget) {
        clearSelectedPost()
        when (target) {
            LargeNavTarget.Home -> selectDestination(Destination.Home)
            LargeNavTarget.Search -> {
                searchPanelName = SearchPanel.Search.name
                selectDestination(Destination.Search)
            }
            LargeNavTarget.PhotoGrid -> {
                searchPanelName = SearchPanel.PhotoGrid.name
                selectDestination(Destination.Search)
            }
            LargeNavTarget.Notifications -> {
                notificationsPanelName = NotificationsPanel.Notifications.name
                selectDestination(Destination.Notifications)
            }
            LargeNavTarget.DirectMessages -> {
                notificationsPanelName = NotificationsPanel.DirectMessages.name
                selectDestination(Destination.Notifications)
            }
            LargeNavTarget.Profile -> {
                viewedProfile = null
                selectDestination(Destination.Profile)
            }
        }
    }

    fun openDirectMessage(profile: Account) {
        onClearTransient()
        clearSelectedPost()
        onStartConversation(profile)
        notificationsPanelName = NotificationsPanel.DirectMessages.name
        destinationTransitionDirection = motionDirection(
            destination.ordinal,
            Destination.Notifications.ordinal,
            reducedMotion,
        )
        destination = Destination.Notifications
        page = null
        notificationRoute = null
    }

    fun openHashtagSearch(hashtag: String) {
        onClearTransient()
        clearSelectedPost()
        searchQuery = hashtag
        searchPrefill = ""
        searchPanelName = SearchPanel.Search.name
        destinationTransitionDirection = motionDirection(destination.ordinal, Destination.Search.ordinal, reducedMotion)
        destination = Destination.Search
        page = null
        onSearch(hashtag)
    }

    fun openAccountSearch(username: String) {
        onClearTransient()
        clearSelectedPost()
        searchQuery = username
        searchPrefill = ""
        searchPanelName = SearchPanel.Search.name
        destinationTransitionDirection = motionDirection(destination.ordinal, Destination.Search.ordinal, reducedMotion)
        destination = Destination.Search
        page = null
        onSearch(username)
    }

    fun clampTimeline(available: Set<Timeline>) {
        if (timeline !in available) timeline = Timeline.Home
    }

    fun syncHomeTimeline(selected: Timeline?) {
        selected?.let { timeline = it }
    }

    fun applyInitialRoute(route: AppRoute?) {
        notificationRoute = route
        if (route != null) {
            destination = Destination.Notifications
            if (route is AppRoute.NotificationSettings) {
                notificationRoute = null
                overlayKey = NOTIFICATION_SETTINGS_OVERLAY_KEY
            }
        }
    }

    /**
     * Clears account-scoped navigation selection when the active account changes.
     *
     * Destination, timeline, panels, overlays, sheets, and the route stay. Only the
     * viewed profile, the local page, the shared search fields, and the selected post
     * reset. Popup, media, and thread state reset with the shell.
     */
    fun resetForAccount() {
        viewedProfile = null
        page = null
        searchQuery = ""
        searchCategory = 0
        clearSelectedPost()
    }

    /**
     * Binds restored navigation state to the current session owner before first display.
     *
     * Fresh state adopts the current owner. Matching restoration preserves the query,
     * category, safe local page, and remembered panels. A mismatched owner clears the
     * account-bound query, category, prefill, page, viewed profile, and selected post
     * before those fields reach visible content or event callbacks, then adopts the
     * current owner. Destination, timeline, panels, non-composer overlays, sheets, route, and
     * visibility stay per [resetForAccount]. The composer overlay is never restored; the
     * composer owner keeps and verifies the editor and its reply/quote targets.
     */
    internal fun bindSession(accountId: AccountId?, sessionRevision: Long): NavigatorSessionBind {
        val currentOrigin = accountId?.connection?.origin
        val currentProtocolName = accountId?.connection?.protocol?.name
        val currentLocalId = accountId?.localId
        val hasBoundOwner = boundOrigin != null || boundProtocolName != null ||
            boundLocalId != null || boundRevision != null
        if (!hasBoundOwner) {
            boundOrigin = currentOrigin
            boundProtocolName = currentProtocolName
            boundLocalId = currentLocalId
            boundRevision = if (accountId == null) null else sessionRevision
            return NavigatorSessionBind.FRESH
        }
        val matches = accountId != null &&
            boundOrigin == currentOrigin &&
            boundProtocolName == currentProtocolName &&
            boundLocalId == currentLocalId &&
            boundRevision == sessionRevision
        if (matches) return NavigatorSessionBind.MATCHING
        viewedProfile = null
        page = null
        searchQuery = ""
        searchCategory = 0
        searchPrefill = ""
        clearSelectedPost()
        boundOrigin = currentOrigin
        boundProtocolName = currentProtocolName
        boundLocalId = currentLocalId
        boundRevision = if (accountId == null) null else sessionRevision
        return NavigatorSessionBind.MISMATCHED
    }

    companion object {
        private const val COMPOSER_OVERLAY_KEY = "Composer"
        private const val EDIT_PROFILE_OVERLAY_KEY = "EditProfile"
        private const val NOTIFICATION_SETTINGS_OVERLAY_KEY = "NotificationSettings"
        private const val NULL_SENTINEL = ""
        private const val SAVER_VERSION = 1

        private fun safeDestination(value: Any?): Destination =
            try {
                Destination.valueOf(value as String)
            } catch (_: Exception) {
                Destination.Home
            }

        private fun safeTimeline(value: Any?): Timeline =
            try {
                Timeline.valueOf(value as String)
            } catch (_: Exception) {
                Timeline.Home
            }

        private fun safeLocalPage(value: Any?): LocalPage? =
            try {
                (value as String).takeIf { it.isNotEmpty() }?.let { LocalPage.valueOf(it) }
            } catch (_: Exception) {
                null
            }

        private fun safeSearchPanel(value: Any?): String =
            try {
                SearchPanel.valueOf(value as String).name
            } catch (_: Exception) {
                SearchPanel.Search.name
            }

        private fun safeNotificationsPanel(value: Any?): String =
            try {
                NotificationsPanel.valueOf(value as String).name
            } catch (_: Exception) {
                NotificationsPanel.Notifications.name
            }

        val Saver: Saver<ShellNavigator, *> = listSaver(
            save = {
                listOf(
                    SAVER_VERSION,
                    it.boundOrigin ?: NULL_SENTINEL,
                    it.boundProtocolName ?: NULL_SENTINEL,
                    it.boundLocalId ?: NULL_SENTINEL,
                    it.boundRevision,
                    it.destination.name,
                    it.destinationTransitionDirection,
                    it.timeline.name,
                    it.page?.name ?: NULL_SENTINEL,
                    it.sheet ?: NULL_SENTINEL,
                    it.overlayKey ?: NULL_SENTINEL,
                    it.searchPanelName,
                    it.searchQuery,
                    it.searchCategory,
                    it.searchPrefill,
                    it.notificationsPanelName,
                    it.navigationVisible,
                )
            },
            restore = { saved ->
                try {
                    if (saved.size == 12) {
                        ShellNavigator().apply {
                            destination = safeDestination(saved[0])
                            destinationTransitionDirection = (saved[1] as? Number)?.toInt() ?: 0
                            timeline = safeTimeline(saved[2])
                            page = null
                            (saved[4] as? String)?.takeIf { it.isNotEmpty() }?.let { sheet = it }
                            // The composer overlay is never restored. ComposerOwner keeps the editor
                            // and its account-bound targets and verifies them on rebind, so the
                            // user reopens the same reply or quote from the compose button.
                            (saved[5] as? String)?.takeIf { it.isNotEmpty() && it != COMPOSER_OVERLAY_KEY }
                                ?.let { overlayKey = it }
                            searchPanelName = safeSearchPanel(saved[6])
                            searchQuery = ""
                            searchCategory = 0
                            searchPrefill = ""
                            notificationsPanelName = safeNotificationsPanel(saved[10])
                            navigationVisible = (saved[11] as? Boolean) ?: true
                        }
                    } else if (saved.size == 17 && (saved[0] as? Number)?.toInt() == SAVER_VERSION) {
                        ShellNavigator().apply {
                            (saved[1] as? String)?.takeIf { it.isNotEmpty() }?.let { boundOrigin = it }
                            (saved[2] as? String)?.takeIf { it.isNotEmpty() }?.let { boundProtocolName = it }
                            (saved[3] as? String)?.takeIf { it.isNotEmpty() }?.let { boundLocalId = it }
                            boundRevision = (saved[4] as? Number)?.toLong()
                            destination = safeDestination(saved[5])
                            destinationTransitionDirection = (saved[6] as? Number)?.toInt() ?: 0
                            timeline = safeTimeline(saved[7])
                            // The composer overlay is never restored. ComposerOwner keeps the editor
                            // and its account-bound targets and verifies them on rebind, so the
                            // user reopens the same reply or quote from the compose button.
                            val hasBoundOwner = boundOrigin != null || boundProtocolName != null ||
                                boundLocalId != null || boundRevision != null
                            if (!hasBoundOwner) {
                                page = null
                                searchQuery = ""
                                searchCategory = 0
                                searchPrefill = ""
                            } else {
                                page = safeLocalPage(saved[8])
                                searchQuery = saved[12] as? String ?: ""
                                searchCategory = (saved[13] as? Number)?.toInt() ?: 0
                                searchPrefill = saved[14] as? String ?: ""
                            }
                            (saved[9] as? String)?.takeIf { it.isNotEmpty() }?.let { sheet = it }
                            (saved[10] as? String)?.takeIf { it.isNotEmpty() && it != COMPOSER_OVERLAY_KEY }
                                ?.let { overlayKey = it }
                            searchPanelName = safeSearchPanel(saved[11])
                            notificationsPanelName = safeNotificationsPanel(saved[15])
                            navigationVisible = (saved[16] as? Boolean) ?: true
                        }
                    } else {
                        ShellNavigator()
                    }
                } catch (_: Exception) {
                    ShellNavigator()
                }
            },
        )
    }
}

/**
 * Creates and binds the shell navigator for the connected session.
 *
 * The navigator survives recomposition and process recreation. It clamps the timeline to
 * the available set, follows the Home selection, reasserts navigation visibility on
 * navigation change, applies the launch route once, and binds restored account-scoped
 * selection to the durable session owner synchronously before first display.
 * Event callbacks arrive from the shell every composition.
 */
@Composable
internal fun rememberShellNavigator(
    accountId: AccountId?,
    sessionRevision: Long,
    initialRoute: AppRoute?,
    availableTimelines: Set<Timeline>,
    selectedHomeTimeline: Timeline?,
    reducedMotion: Boolean,
    onClearTransient: () -> Unit,
    onSearch: (String) -> Unit,
    onStartConversation: (Account) -> Unit,
): ShellNavigator {
    val navigator = rememberSaveable(saver = ShellNavigator.Saver, init = ::ShellNavigator)
    navigator.reducedMotion = reducedMotion
    navigator.onClearTransient = onClearTransient
    navigator.onSearch = onSearch
    navigator.onStartConversation = onStartConversation
    navigator.bindSession(accountId, sessionRevision)
    LaunchedEffect(availableTimelines) { navigator.clampTimeline(availableTimelines) }
    LaunchedEffect(selectedHomeTimeline, accountId) { navigator.syncHomeTimeline(selectedHomeTimeline) }
    LaunchedEffect(navigator.destination, navigator.page, navigator.overlayKey) { navigator.navigationVisible = true }
    LaunchedEffect(initialRoute) { navigator.applyInitialRoute(initialRoute) }
    return navigator
}
