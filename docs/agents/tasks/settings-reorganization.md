# Objective

Reorganize the Settings pages by user task. Add three settings: "Disable blur effects", the favourite
reaction emoji, and an About page.

# Invariants

- Stored preference keys and files do not change. A new key has a safe default when it is absent.
- Application preferences stay in `AppPreferences`. Account preferences stay in `PostPreferences`.
- An account route is valid only while its exact account exists (`SettingsRouteGating.kt`).
- The profile editor stays out of Settings.
- New strings are English only. Other locales fall back to English.

# Decisions

- The Appearance page keeps the route `SettingsRoute.Display` and the file `DisplaySettingsScreen.kt`.
  The string key `settings_display` keeps its translations; only the English value changed.
- `SettingsScreen` takes one `onRoute` callback instead of one callback for each row.
- `SettingsSwitchRow` is the one switch row for settings pages. The row toggles; the switch shows state.
- Posting and account content warning rules apply to the active account. The page names that account.
  A per-page account picker is not part of this task.
- `docs/wiki/changelog.md` has uncommitted owner changes, so this task does not edit it.
- `SettingsHost` takes `PostingSettingsSupport` and `PostingSettingsActions` instead of one parameter for each
  Posting command. `SettingsOverlayHost` binds the actions to the active account.
- The favourite reaction field accepts one Unicode emoji only. A custom server emoji needs a live check of the
  `myReaction` form first (Misskey can return `:name@.:`), and the emoji catalog is not available in Settings.
- `LocalBlurEffectsEnabled` is the one switch for blur. `ConnectedApp` provides it. `rememberChromeBackdrop`
  and `ProfileHeader` read it. A new blur site must read it too.
- `SettingsRoute.parent()` is the one Back order. `safeParent()` delegates to it.
- The account content warning page reuses the labels of the global page. The separate account labels are deleted.

# Completed

- S1 Main page, Appearance sections, Timeline and media page. Commit `5dc86021`.
- S2 "Disable blur effects" in Appearance, Screen and layout. Commit `eef0694d`.
- S3 Posting at the top level, favourite reaction, Privacy switch. Commit `15f0d85f`.
- S4 Content warnings at the top level, account rules child page, Back order (`SettingsRoute.parent()`).

# Current slice

S5 Blocked and muted lists at the top level. Privacy keeps only the tracking switch.

# Files involved

- `app/src/main/java/me/foxtails/palustris/ui/settings/`
- `app/src/main/java/me/foxtails/palustris/domain/AppPreferences.kt`
- `app/src/main/java/me/foxtails/palustris/data/preferences/FileAppPreferencesRepository.kt`
- `app/src/main/java/me/foxtails/palustris/ui/components/FloatingChromeBackdrop.kt`
- `app/src/main/java/me/foxtails/palustris/ui/profile/ProfileHeader.kt`
- `app/src/main/res/values/strings.xml`

# Verification

- S1 to S4: the settings unit tests pass. The local CI-parity gate passes except for the architecture audit,
  which exits 1 at the base commit. No slice adds a regression line.
- No device check ran. The blur setting has no check on a real screen.
- No live server check ran for the favourite reaction.

# Next

S5 (blocked and muted lists at the top level, no account picker for one account), S6 (About).

# Blockers

None.

# Last safe commit

`15f0d85f` Move Posting to the Settings main page and add the favourite reaction. S4 is the commit after it:
"Move Content warnings to the Settings main page with an account rules page".
