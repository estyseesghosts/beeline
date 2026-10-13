package me.foxtails.palustris.ui.settings

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
