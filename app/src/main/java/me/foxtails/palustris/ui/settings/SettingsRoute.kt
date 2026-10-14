package me.foxtails.palustris.ui.settings

import me.foxtails.palustris.domain.AccountId

sealed interface SettingsRoute {
    data object Main : SettingsRoute
    data object Display : SettingsRoute
    data object Notifications : SettingsRoute
    data class NotificationAccount(val accountId: AccountId) : SettingsRoute
    data object Privacy : SettingsRoute
    data object Language : SettingsRoute
    data object TimelineMedia : SettingsRoute
    data object ContentWarnings : SettingsRoute
    data object AccountContentWarnings : SettingsRoute
    data object Posting : SettingsRoute
    data object PrivacyAccounts : SettingsRoute
    data object About : SettingsRoute
    data class Moderation(val accountId: AccountId, val kind: ModerationKind) : SettingsRoute
}

/** The lists of one account on the Blocks and mutes page. [Filters] opens the filter editor instead of a list. */
enum class ModerationKind { Blocked, Muted, Hashtags, Filters }
