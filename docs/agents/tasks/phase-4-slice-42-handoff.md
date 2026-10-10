# Next-agent prompt — Finish Phase 4 Slice 4.2

This is a self-contained dispatch for the next agent. Follow it in order.

## Current state

- `main` is at `22e62d5` — `Record complete architecture metrics baseline (Slice 4.1, after b8b0ba5)`.
- Slice 4.1 is complete, committed, and gate-verified.
- Slice 4.2 source edits are applied to the working tree but NOT verified and NOT committed.
- Do not push. Do not begin Phase 5.

## Working tree uncommitted changes (preserve, do not touch)

Unrelated user/agent work that must survive:

- `.opencode/agents/orchestrator.md` (modified)
- `.opencode/agents/problem_solver.md` (modified)
- `.opencode/agents/targeted_fixer.md` (modified)
- `importantdocs/writing_style.md` (modified)
- `app/src/test/java/me/foxtails/palustris/ui/photogrid/PhotoGridFeedViewModelTest.kt` (deleted)
- `currentbehaviour.png` (deleted)
- `intendedbehaviour.png` (deleted)
- Untracked: `.tmp-inspect-full/`, various `*.png` captures, `docs/agents/tasks/4c.md`,
  `docs/agents/tasks/next-phase-3-prompt.md`, `docs/agents/tasks/phase-3-completion-prompt.md`,
  `docs/agents/tasks/phase-4-completion-prompt.md`, `tools/scripts/adb_*.py`,
  `__pycache__/` dirs, `logs/261006-140000.txt`, `logs/phase31-emulator-launch.png`

## Slice 4.2 work already applied (do not redo)

The following source edits are in the working tree. They have NOT been verified yet.

### Import ordering fixed (ASCII-sorted)

These 17 files had their import blocks re-sorted to satisfy `standard:import-ordering`:

- `app/src/main/java/me/foxtails/palustris/MainActivity.kt`
- `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonPageClient.kt`
- `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonSource.kt`
- `app/src/main/java/me/foxtails/palustris/data/notifications/NotificationSyncOrchestrator.kt`
- `app/src/main/java/me/foxtails/palustris/data/notifications/push/UnifiedPushRegistrationManager.kt`
- `app/src/main/java/me/foxtails/palustris/ui/large/LargeScreenShell.kt`
- `app/src/main/java/me/foxtails/palustris/ui/posts/PostRow.kt`
- `app/src/main/java/me/foxtails/palustris/ui/profile/ProfileLargePresentation.kt`
- `app/src/main/java/me/foxtails/palustris/ui/saved/SavedCollectionsHost.kt`
- `app/src/main/java/me/foxtails/palustris/ui/settings/DisplaySettingsScreen.kt`
- `app/src/main/java/me/foxtails/palustris/ui/setup/SetupScreens.kt`
- `app/src/test/java/me/foxtails/palustris/data/mastodon/MastodonIntegrationTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/SignInScreenTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/large/WideNavigationTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/navigation/NavigationTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/notifications/NotificationsScreenTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/settings/SettingsDisplayTest.kt`

### Unused imports removed

- `app/src/main/java/me/foxtails/palustris/data/notifications/AndroidNotificationPresenter.kt` — removed line 8 (`android.content.Intent`)
- `app/src/main/java/me/foxtails/palustris/ui/PalustrisApp.kt` — removed lines 53 and 59 (`NotificationsPanel`, `SearchPanel`)
- `app/src/main/java/me/foxtails/palustris/ui/search/SearchScreen.kt` — removed lines 18 and 19 (`Row`, `Spacer`)

### Baseline pruned

`app/ktlint-baseline.xml` was pruned: 380 resolved entries removed across 190 files.
5 files still carry entries (all deferred, see below).

### Deferred entries (do not fix, do not remove from baseline)

These entries remain in `app/ktlint-baseline.xml` because fixing them exceeds style scope:

- `ui/saved/SavedCollectionsHost.kt` — `standard:filename` (class name mismatch, not a formatting issue)
- `ui/ConnectedApp.kt` — `standard:keyword-spacing` at L91
- `ui/thread/PostThreadViewModel.kt` — `standard:keyword-spacing` at L192
- `data/mastodon/MastodonIntegrationTest.kt` — `standard:paren-spacing` at L228
- `ui/navigation/NavigationTest.kt` — `standard:string-template` x4 (code-shape change in test bodies)
- `ui/settings/SettingsViewModelTest.kt` — `standard:function-expression-body` x2 (code-shape change)

## Remaining work

### 1. Verify ktlintCheck passes

Run:

```text
.\gradlew.bat --no-daemon --console=plain :app:ktlintCheck
```

Expected: BUILD SUCCESSFUL. If any of the 31 scope files still report violations, investigate whether the edit was incomplete. Do not add new baseline entries. Do not fix deferred entries.

### 2. Run the full CI-parity gate

```text
python -m unittest discover -s tools/tests

python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check

.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug :app:ktlintCheck :app:assembleDebug :app:assembleRelease
```

Expected:
- 52 Python tests pass.
- Architecture audit exits 0 with zero regressions.
- All JVM tests pass (approximately 1,692 tests across 161 suites).
- lint, ktlint, debug assembly, and release assembly all pass.

Known flakes: `MastodonIntegrationTest` cancellation and `NotificationsViewModelTest`
test-isolation. They pass alone; record them in `logs/BUGS.txt` and rerun the gate if
only a known flake fails.

### 3. Commit Slice 42

Commit the source edits and baseline update together. Use explicit reviewed paths.
Name the preceding safe commit (`22e62d5`) in the message.

Files to commit:
- All 17 import-sorted files listed above
- The 3 unused-import-removal files listed above
- `app/ktlint-baseline.xml`

Do NOT commit unrelated working-tree changes.

### 4. Update records

Update `docs/agents/tasks/hardening-0.4.0-phase2.md`:
- Add a "Phase 4" section recording Slice 4.2 commit hash, files cleaned, deferred entries, and full gate results.

Update `docs/agents/handoff.md`:
- Record current position (Phase 4 complete, ready for Phase 5).
- Name the last safe commit.
- List known blockers (physical-device, API 29 instrumentation, TalkBack, signing, live-server checks remain unverified).

### 5. Simulated-device smoke test

The user requested a device test. Emulator-5554 is attached (API 37).

1. Install the debug APK: `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`
2. Launch: `adb -s emulator-5554 shell am start -n me.foxtails.palustris/.MainActivity`
3. Wait 5 seconds, screenshot: `adb -s emulator-5554 exec-out screencap -p > logs/phase42-emulator-launch.png`
4. Confirm `MainActivity` is resumed: `adb -s emulator-5554 shell dumpsys activity activities | findstr mResumedActivity`
5. Report the result. Phase 4 changes no behavior, so this is a regression smoke test only.

## Invariants (do not change)

- No behavior changes. No persisted-format migration. No dependency additions.
- Do not weaken or replace existing regression tests.
- Do not add ktlint baseline exemptions.
- Do not reformat unrelated files.
- Do not convert warnings into tolerated audit findings.
- Do not fix deferred baseline entries.
- Do not push.

## If verification fails

1. Identify the failing check.
2. Determine whether the failure is caused by the Phase 4 change.
3. If yes: fix it within the Phase 4 scope. Do not expand scope.
4. If the failure is a known flake: record it in `logs/BUGS.txt` and rerun.
5. If the failure is unrelated (pre-existing): record it and report it. Do not fix unrelated failures.
6. Stop after two failed fixes for the same root problem.

## Deliverables

- All 31 scope files carry zero ktlint baseline debt, or the remainder is recorded as explicitly deferred with reasons.
- No new ktlint baseline debt exists anywhere.
- The full CI-parity gate passes.
- Documentation and handoff are current.
- A clear handoff exists for Phase 5 (documentation integrity and final acceptance).
