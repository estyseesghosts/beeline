# Force Layout Direction

Status: in progress
Owner: Maintainers
Last reviewed: 2026-10-05

## Objective

Add one switch to the Display options page that forces the application layout direction.

The switch names the direction that turning it on produces. It adds exactly two strings and two
behaviors. `ui/ConnectedApp.kt` publishes the effective direction from its existing
`CompositionLocalProvider`.

## Invariants

- Every `absolutePadding` clearance site keeps following the physical right edge. Do not edit them.
- The Profile wide summary column branch on `LocalLayoutDirection` keeps following the effective
  direction. Do not edit it.
- System bar and window insets stay physical. Do not convert them to logical insets.
- `LargeScreenShell` rail placement, `LargeLayoutMode` content origins, and
  `PostActionBubblePlacement` keep reading the layout direction.
- `MainActivity`, `AppLocaleOwner`, and `AppLocaleController` keep the application language. Do not
  add a second locale owner.
- Compact-narrow navigation, group memory, back behavior, and route restoration do not change.
- A right-to-left layout is not a language change. Every user-visible string stays unchanged.
- No existing test assertion is weakened.

## Decisions

- Android has no public per-application API that sets layout direction independently of the locale.
  `Configuration.setLayoutDirection` is not public API. `LocaleManager.applicationLocales` changes
  every string. The forcing is therefore a Compose-level `LocalLayoutDirection` override at the one
  composition root.
- The preference is a three-value enum, `System`, `ForceRtl`, `ForceLtr`, stored as the enum name.
  `System` is the default when the key is absent. The maintainer confirmed this shape.
- The switch is checked when the stored value is not `System`. Turning it on stores the explicit
  override for the direction opposite the base direction. Turning it off stores `System`.
- `System` never appears as a label, so the item needs no third string and no supporting summary.
- The label changes after the user switches, because the base direction is read before the stored
  override resolves.
- `enumOrDefault` already falls back safely for a missing key, so no stored-format version change
  is required. A user who already has the file keeps `System`.
- `System` as the default cannot flip an existing right-to-left user on upgrade, because an absent
  key resolves to the device direction.
- `deviceLayoutDirection()` reads `LocalConfiguration.current.layoutDirection`. A caller below the
  composition root must resolve against that value and never against `LocalLayoutDirection.current`,
  because the published value is the forced direction there.
- Real right-to-left language support is a separate task. The app has no right-to-left translations.
- A pre-existing wide-layout gap is recorded, not fixed: `LargeScreenShell` applies a physical
  `bounds.left` with direction-relative `Modifier.offset`, so forced right-to-left can move a pane
  away from the physical hinge. That needs a maintainer decision on a physical anchor.

## Owners and existing abstractions

| Behavior | Owner |
| --- | --- |
| `AppPreferences` and the new enum | `domain/AppPreferences.kt` |
| File persistence, `read()` and `persist()` | `data/preferences/FileAppPreferencesRepository.kt` |
| Preference commands | `ui/settings/SettingsViewModel.kt` |
| Display route callbacks | `ui/settings/SettingsHost.kt` |
| Callback wiring to the view model | `ui/settings/SettingsOverlayHost.kt` |
| Effective layout direction rule | `ui/LayoutDirectionPolicy.kt` |
| Effective layout direction publication | `ui/ConnectedApp.kt` |
| Application language | `MainActivity`, `AppLocaleOwner`, `AppLocaleController` |

## Non-goals and fail gates

Do not add a right-to-left language to `AppLanguage`. Do not add translations. Do not use a hidden
API. Do not convert physical insets to logical insets. Do not change any clearance call site. Do not
edit `docs/agents/tasks/beeline-0.4.0.md`. Do not push.

Stop and report before editing if the direction cannot be applied without a locale change, if a third
string appears to be required, if the default value can flip an existing right-to-left user, if any
`absolutePadding` clearance site would need to change, or if scope expands into right-to-left
translation work.

## Current slice

Complete. The slice adds the enum, the persisted key, the view model command, the Display page item,
and the composition root override, with their tests and documentation coverage.

## Current slice

Fix the Display page scroll defect, then re-verify.

The previous current-slice entry is retained below as the delivered slice.

### Delivered slice

Add the enum, the persisted key, the view model command, the Display page item, and the
composition root override, with their tests.

The page rendered a plain `Column` with no `verticalScroll`. Its content is taller than a short
viewport, so the trailing items were clipped and could not be reached. The defect predates this
switch. The new item added one more row and made the clipping worse. The earlier layout direction
tests worked around it with a tall viewport qualifier instead of fixing the cause.

- `DisplaySettingsScreen` owns a `rememberScrollState` and applies `verticalScroll`.
- The layout direction tests now use the default short viewport and scroll the switch into view.
- Two tests cover the scroll itself on a deliberately short viewport.

## Files involved

- `app/src/main/java/me/foxtails/palustris/domain/AppPreferences.kt`
- `app/src/main/java/me/foxtails/palustris/data/preferences/FileAppPreferencesRepository.kt`
- `app/src/main/java/me/foxtails/palustris/ui/LayoutDirectionPolicy.kt`
- `app/src/main/java/me/foxtails/palustris/ui/settings/SettingsViewModel.kt`
- `app/src/main/java/me/foxtails/palustris/ui/settings/SettingsHost.kt`
- `app/src/main/java/me/foxtails/palustris/ui/settings/SettingsOverlayHost.kt`
- `app/src/main/java/me/foxtails/palustris/ui/settings/DisplaySettingsScreen.kt`
- `app/src/main/java/me/foxtails/palustris/ui/ConnectedApp.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/me/foxtails/palustris/ui/settings/SettingsDisplayTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/settings/SettingsViewModelTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/AppLayoutDirectionTest.kt`
- `docs/agents/app-shell-ownership.md`
- `docs/agents/handoff.md`
- `docs/wiki/ui-and-navigation.md`

## Completed

- Slice 1 — the enum, the persisted key, the view model command, the Display page item, the
  composition root override, tests, and documentation. Commit `916aac4`.
- Slice 2 — the Display page scroll fix, its tests, and documentation.

## Verification

Passed: `AppLayoutDirectionTest` covers the resolve rule, the forced override in both device
directions, the toggle target, and that a forced override never changes the device direction below
the root.

Passed: `SettingsDisplayTest` 16 tests, including the label in both device directions, the switch
state for each stored value, that the label follows the device direction and not the forced
direction, reversibility in both directions, and file round trip with an absent and an unknown key.
The layout direction tests use a tall viewport qualifier because the Display page does not scroll.

Passed: `SettingsViewModelTest` covers that the command writes only its own field, stays reversible,
and keeps the committed value with a retry after a failed write.

Passed: 19 suites and 212 tests across the settings, layout direction, clearance, and navigation
suites, with zero failures, errors, or skips. The Home, Search, Photo Grid, Notifications, and
Profile clearance suites and the direct message and navigation suites pass unchanged.

Passed: a read-only audit confirms `TextAlign` use is centered only, all 26 `absolutePadding` call
sites stay physical and were not edited, and all `Alignment` uses are logical.

## Next

Decide whether to fix the wide-layout hinge offset gap, then start real right-to-left language
support as a separate task. That task needs right-to-left resources and an `AppLanguage` entry.

## Blockers

None for this slice. The wide-layout hinge offset gap is recorded as unfixed.

## Last safe commit

`916aac4` `Add Display layout direction toggle`.