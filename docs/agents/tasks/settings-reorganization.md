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
- `LocalBlurEffectsEnabled` is the one switch for blur. `ConnectedApp` provides it. `rememberChromeBackdrop`
  and `ProfileHeader` read it. A new blur site must read it too.

# Completed

- S1 Main page, Appearance sections, Timeline and media page. Commit `5dc86021`.
- S2 "Disable blur effects" in Appearance, Screen and layout.

# Current slice

S3 Posting at the top level, favourite emoji, Privacy switch.

# Files involved

- `app/src/main/java/me/foxtails/palustris/ui/settings/`
- `app/src/main/java/me/foxtails/palustris/domain/AppPreferences.kt`
- `app/src/main/java/me/foxtails/palustris/data/preferences/FileAppPreferencesRepository.kt`
- `app/src/main/java/me/foxtails/palustris/ui/components/FloatingChromeBackdrop.kt`
- `app/src/main/java/me/foxtails/palustris/ui/profile/ProfileHeader.kt`
- `app/src/main/res/values/strings.xml`

# Verification

- S1 and S2: the settings unit tests pass. The local CI-parity gate passes except for the architecture audit,
  which exits 1 at the base commit. Neither slice adds a regression line.
- No device check ran. The blur setting has no check on a real screen.

# Next

S3 (Posting at the top level, favourite emoji, Privacy switch), S4 (content warning pages),
S5 (blocked and muted lists at the top level, Back order, no account picker for one account), S6 (About).

# Blockers

None.

# Last safe commit

`5dc86021` Flatten the Settings main page and add the Timeline and media page. S2 is the commit after it:
"Add a setting that disables blur effects".
