# Objective

Reorganize the Settings pages by user task. Add three settings: "Disable blur effects", the favourite
reaction emoji, and an About page.

# Invariants

- Stored preference keys and files do not change. A new key has a safe default when it is absent.
- Application preferences stay in `AppPreferences`. Account preferences stay in `PostPreferences`.
- An account route is valid only while its exact account exists (`SettingsRouteGating.kt`).
- The profile editor stays out of Settings.
- Every settings page string has a translation in each locale. A regional folder inherits from its language folder.

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
- The favourite reaction opens the shared emoji picker and stores the submission identity of the choice. A custom
  server emoji is allowed. `MisskeyMapper.localReactionIdentity` maps `:name@.:` to `:name:` for note reactions.
- "Disable blur effects" is on by default, also for a stored file without the key (owner decision).
- `LocalBlurEffectsEnabled` is the one switch for blur. `ConnectedApp` provides it. `rememberChromeBackdrop`
  and `ProfileHeader` read it. A new blur site must read it too.
- `SettingsRoute.parent()` is the one Back order. `safeParent()` delegates to it.
- The About page lists component names only, and the license only for FFmpeg, which `docs/video.md` records. It has no
  repository link, because the repository records no public address.
- The blocked and muted lists page has the title "Blocks and mutes". Its route stays `SettingsRoute.PrivacyAccounts`,
  so a saved route still restores.
- The account content warning page reuses the labels of the global page. The separate account labels are deleted.

# Completed

- S1 Main page, Appearance sections, Timeline and media page. Commit `5dc86021`.
- S2 "Disable blur effects" in Appearance, Screen and layout. Commit `eef0694d`.
- S3 Posting at the top level, favourite reaction, Privacy switch. Commit `15f0d85f`.
- S4 Content warnings at the top level, account rules child page, Back order (`SettingsRoute.parent()`). Commit `3ad81590`.
- S5 Blocks and mutes at the top level, Privacy with the tracking switch only, no notification picker for one account.
  Commit `89bcd2b3`.
- S6 About page. Commit `34b540de`.
- S7 Owner corrections: emoji picker for the favourite reaction, blur setting on by default, translations.

# Current slice

None. All six slices are complete in source.

# Files involved

- `app/src/main/java/me/foxtails/palustris/ui/settings/`
- `app/src/main/java/me/foxtails/palustris/domain/AppPreferences.kt`
- `app/src/main/java/me/foxtails/palustris/data/preferences/FileAppPreferencesRepository.kt`
- `app/src/main/java/me/foxtails/palustris/ui/components/FloatingChromeBackdrop.kt`
- `app/src/main/java/me/foxtails/palustris/ui/profile/ProfileHeader.kt`
- `app/src/main/res/values/strings.xml`

# Verification

- S1 to S6: the settings unit tests pass. The local CI-parity gate passes except for the architecture audit,
  which exits 1 at the base commit. No slice adds a regression line.
- No device check ran. The blur setting has no check on a real screen.
- No live server check ran for the favourite reaction. The `:name@.:` form is not confirmed by a recorded response.
- No native speaker reviewed the translations.

# Next

Device checks: every page at font scale 2.0, TalkBack on the switch rows, the blur setting on a real screen, and the
favourite reaction with a custom emoji against a Misskey server (the heart must show as selected after a refresh).

# Blockers

None.

# Last safe commit

`34b540de` Add the About page to Settings. S7 is the commit after it:
"Use the emoji picker for the favourite reaction and translate the settings pages".
