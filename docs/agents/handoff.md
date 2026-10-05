# Handoff

Status: current
Owner: Maintainers
Last reviewed: 2026-10-05

## Continuation pointer

Read [tasks/force-layout-direction.md](tasks/force-layout-direction.md) for the Display layout
direction switch task state. Read AGENTS.md and its required linked pages before continuing.
Read [tasks/beeline-0.4.0.md](tasks/beeline-0.4.0.md) for the separate Phase 4C plan.

## Current position and next action

The Display layout direction switch is implemented and test verified. `AppLayoutDirection` is a
three-value enum, `System`, `ForceRtl`, `ForceLtr`, stored as the enum name in
`app-preferences.json`. `System` is the default for an absent or unknown key, so an upgrade cannot
flip an existing user. `ui/LayoutDirectionPolicy.kt` owns the rule. `ConnectedApp` reads the device
direction, resolves the override against it, and provides `LocalLayoutDirection`.

The Display item carries a headline only, because a supporting summary would need a third string.
The label names the direction that turning the switch on produces, so it changes after the user
switches. A page below the composition root must resolve against `deviceLayoutDirection()` and never
against `LocalLayoutDirection.current`, because the published value is the forced direction there.
The page uses the device direction for its label and toggle target.

The Display page also scrolls vertically. The page previously rendered a plain `Column` with no
`verticalScroll`, so its content overflowed a short viewport and the trailing items were clipped and
unreachable. That defect predates the switch. The new item added a row and made the clipping worse.
The screen now owns a `rememberScrollState`, and the layout direction tests use the default short
viewport and scroll the switch into view.

A read-only audit of hard-coded physical sides confirms that `TextAlign` use is centered only, and it
confirms all 26 `absolutePadding` call sites across 11 files stay physical and were not edited. All
`Alignment` uses are logical. One pre-existing gap is now recorded rather than fixed: on a large
foldable, a forced right-to-left layout can move a pane away from the physical hinge, because
`LargeScreenShell` converts a physical `bounds.left` and applies it with direction-relative
`Modifier.offset`. That needs a maintainer decision on a physical anchor and stays out of this slice.

Next: decide whether to fix the foldable hinge offset, and start real right-to-left language support
as a separate task. That work needs Arabic, Hebrew, Persian, or Urdu resources and an `AppLanguage`
entry, which this task deliberately excludes.

## Last safe commit

`Add Display page scroll fix`, based on `916aac4` — `Add Display layout direction toggle`.
Resolve this checkpoint's hash from Git. Nothing was pushed.

## Limits and worktree caution

- Device rendering, real right-to-left language support, TalkBack, signing, and physical foldable
  behavior remain unverified. Beeline has no right-to-left translations today.
- The foldable hinge offset gap above is unfixed and unverified on a device.
- Other settings sub-pages are not checked for the same missing-scroll defect. `SettingsScreen` and
  the other route branches in `SettingsHost` were not audited for this.
- Physical-device IME and API 29 behavior remain unverified.
- No production floating-navigation geometry is approved. Do not invent clearance or fit values.
- One pre-existing flake exists: `MastodonIntegrationTest cancelingTimelinePageCancelsRequestAndAllowsRetry`
  can fail with `IOException: Gave up waiting for queue to shut down`. It passes in isolation and is unrelated
  to this work. Record it separately; do not weaken it.
- A separate external `gradlew.bat clean installDebug` process ran in this worktree during an earlier slice and
  removed shared test results and build outputs. It was not stopped and finished on its own. Recheck shared-output
  activity before later builds. That external install's device state remains unknown.
- Preserve modified agent definitions and `importantdocs/writing_style.md`.
- Preserve the deleted `PhotoGridFeedViewModelTest.kt` and PNGs, untracked captures, scripts, and caches.
  The Photo Grid clearance test uses a different name and must not restore that deletion.
- `docs/agents/tasks/4c.md` is untracked user input. Do not stage it.
- Stage only explicitly reviewed slice paths. Do not push.
