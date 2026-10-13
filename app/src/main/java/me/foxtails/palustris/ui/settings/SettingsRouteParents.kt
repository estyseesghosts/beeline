package me.foxtails.palustris.ui.settings

import me.foxtails.palustris.data.auth.AccountRef

/**
 * The page that Back opens from this route. A child page returns to its own parent page, not to the main
 * page. The main page has no parent; Back closes Settings there.
 */
internal fun SettingsRoute.parent(): SettingsRoute = when (this) {
    SettingsRoute.AccountContentWarnings -> SettingsRoute.ContentWarnings
    is SettingsRoute.NotificationAccount -> SettingsRoute.Notifications
    is SettingsRoute.Moderation -> SettingsRoute.PrivacyAccounts
    else -> SettingsRoute.Main
}

/**
 * The Back target when the signed-in accounts are known. With exactly one account the notification
 * account picker is skipped in both directions, so Back never lands on a one-row picker.
 */
internal fun SettingsRoute.parent(accounts: List<AccountRef>): SettingsRoute =
    parent().takeUnless { it == SettingsRoute.Notifications && accounts.size == 1 } ?: SettingsRoute.Main

/** The route that a main page row opens. A picker with one account opens that account at once. */
internal fun SettingsRoute.skippingSingleAccountPicker(accounts: List<AccountRef>): SettingsRoute =
    if (this == SettingsRoute.Notifications && accounts.size == 1) {
        SettingsRoute.NotificationAccount(accounts.single().accountId)
    } else {
        this
    }
