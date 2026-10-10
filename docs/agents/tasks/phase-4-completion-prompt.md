# Next-agent prompt — Complete Phase 4 (Slices 4.1 and 4.2)

This is a self-contained dispatch for the next agent. Follow it in order.

## Current state

- `main` is at `b8b0ba5` — `Record Phase 3 Slice 3.1 verification and closeout`.
- Phase 0, Phase 1, Phase 2, and Phase 3 are complete, committed, and gate-verified.
- The last code commit is `0bd8e7d` (`Extract Misskey thread service`).
- The last record commit is `b8b0ba5`. It touches only `docs/agents/handoff.md` and
  `docs/agents/tasks/hardening-0.4.0-phase2.md`.
- Do not push. Do not begin Phase 5.

## Working tree uncommitted changes (preserve, do not touch)

These are unrelated user/agent work that must survive:

- `.opencode/agents/orchestrator.md` (modified)
- `.opencode/agents/problem_solver.md` (modified)
- `.opencode/agents/targeted_fixer.md` (modified)
- `importantdocs/writing_style.md` (modified)
- `app/src/test/java/me/foxtails/palustris/ui/photogrid/PhotoGridFeedViewModelTest.kt` (deleted)
- `currentbehaviour.png` (deleted)
- `intendedbehaviour.png` (deleted)
- Untracked: `.tmp-inspect-full/`, various `*.png` captures (including
  `logs/phase31-emulator-launch.png`), `docs/agents/tasks/4c.md`,
  `docs/agents/tasks/next-phase-3-prompt.md`,
  `docs/agents/tasks/phase-3-completion-prompt.md`, `tools/scripts/adb_*.py`,
  `__pycache__/` dirs, `logs/261006-140000.txt`

## Required reading (before any edit)

1. `AGENTS.md` and every page it links as required.
2. `docs/agents/workflow.md`, `docs/agents/agent-control.md`, `docs/agents/engineering-rules.md`,
   `docs/agents/operation-rules.md`, `docs/agents/documentation-rules.md`.
3. `docs/fix_0.4.0.md` — Phase 4 (Slices 4.1 and 4.2) and the verification contract
   (section 3). Phase 4 changes no behavior.
4. `docs/agents/handoff.md` and `docs/agents/tasks/hardening-0.4.0-phase2.md` for the
   completed Phase 3 record and the last safe commit.
5. `importantdocs/writing_style.md` before writing any document.

## Pre-verified facts (do not re-derive from memory; confirm against source)

- `tools/architecture-baseline.json` holds `fileMetrics` with one entry
  (`ui/SvgIconPaths.kt`) and an empty `functionMetrics` list. Thresholds, allowlists,
  `resolvedFindings` (29), and retention rules exist. Slice 4.1 must populate the two
  metric maps from the current tree.
- The six files named in Slice 4.2 (`ShellDestinationContent.kt`, `HomeFeed.kt`,
  `ProfileScreen.kt`, `NotificationsScreen.kt`, `CategoryChips.kt`, `MisskeySource.kt`)
  already carry zero `app/ktlint-baseline.xml` entries. Confirm this before editing.
- 31 project-touched files still carry baseline entries. 28 trace to `63d8231`
  (`Repair existing ktlint gate violations`). Three trace to `3c5b2ca` (H01):
  `ui/ConnectedApp.kt`, `ui/settings/DisplaySettingsScreen.kt`,
  `ui/settings/SettingsDisplayTest.kt`. The full list is under "Slice 4.2 scope" below.
- `app/ktlint-baseline.xml` holds 195 files with entries. The global cleanup of the
  remaining ~164 files is explicitly deferred. Do not touch them.
- Phase 4 needs no device test. It changes no behavior. The full JVM gate is the
  acceptance signal.

## Objective — Complete Phase 4 in two slice commits

### Slice 4.1 — Record a complete architecture metric baseline

From a clean tree run:

```text
python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --record-baseline tools/architecture-baseline.json
```

Then review the resulting diff by hand. The record operation must preserve
thresholds, existing findings, resolved findings, retention rules, and allowlists.
It must populate production `fileMetrics` and production `functionMetrics`.
Do not convert current warnings into tolerated `existingFindings`. Growth
measurement is the purpose, not warning suppression.

Verification for Slice 4.1:

```text
python -m unittest discover -s tools/tests

python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check
```

Then re-run the exact `--record-baseline` command a second time and confirm
`git diff -- tools/architecture-baseline.json` is empty. Deterministic output is
the exit gate. If the second run differs, stop and investigate. Do not commit a
nondeterministic baseline.

Commit Slice 4.1 separately with explicit reviewed paths. Name the preceding safe
commit (`b8b0ba5`) in the message.

### Slice 4.2 — Ratchet ktlint debt only on touched files

For each file in the scope list below:

1. Read its remaining baseline errors in `app/ktlint-baseline.xml`.
2. Fix the source issue (import order, unused import, spacing, blank lines).
3. Remove only the resolved entries from the baseline.
4. Do not reformat unrelated code in the same file.

Confirm the six Slice 4.2 named files are clean and report that result. Do not
edit them if they carry no entries.

Fix only genuinely resolved entries. If a fix risks behavior or exceeds style
scope, leave the entry and record the file as deferred. Never add a new baseline
exemption. Never weaken a test to satisfy a style rule.

#### Slice 4.2 scope (project-touched files with remaining entries)

Production files:

- `src/main/java/me/foxtails/palustris/MainActivity.kt`
- `src/main/java/me/foxtails/palustris/data/directmessages/DirectMessageRepository.kt`
- `src/main/java/me/foxtails/palustris/data/mastodon/MastodonPageClient.kt`
- `src/main/java/me/foxtails/palustris/data/mastodon/MastodonSource.kt`
- `src/main/java/me/foxtails/palustris/data/notifications/AndroidNotificationPresenter.kt`
- `src/main/java/me/foxtails/palustris/data/notifications/NotificationSyncOrchestrator.kt`
- `src/main/java/me/foxtails/palustris/data/notifications/push/UnifiedPushRegistrationManager.kt`
- `src/main/java/me/foxtails/palustris/domain/SocialSource.kt`
- `src/main/java/me/foxtails/palustris/ui/ConnectedApp.kt`
- `src/main/java/me/foxtails/palustris/ui/PalustrisApp.kt`
- `src/main/java/me/foxtails/palustris/ui/directmessages/DirectMessageConversationScreen.kt`
- `src/main/java/me/foxtails/palustris/ui/large/LargeScreenShell.kt`
- `src/main/java/me/foxtails/palustris/ui/notifications/NotificationLaunchHost.kt`
- `src/main/java/me/foxtails/palustris/ui/photogrid/PhotoGridScreen.kt`
- `src/main/java/me/foxtails/palustris/ui/posts/PostRow.kt`
- `src/main/java/me/foxtails/palustris/ui/profile/ProfileLargePresentation.kt`
- `src/main/java/me/foxtails/palustris/ui/saved/SavedCollectionsHost.kt`
- `src/main/java/me/foxtails/palustris/ui/search/SearchScreen.kt`
- `src/main/java/me/foxtails/palustris/ui/settings/DisplaySettingsScreen.kt`
- `src/main/java/me/foxtails/palustris/ui/setup/SetupScreens.kt`
- `src/main/java/me/foxtails/palustris/ui/thread/PostThreadViewModel.kt`

Test files (fix style only; change no assertion):

- `src/test/java/me/foxtails/palustris/ModerationServiceTest.kt`
- `src/test/java/me/foxtails/palustris/data/mastodon/MastodonIntegrationTest.kt`
- `src/test/java/me/foxtails/palustris/data/notifications/push/PushCancellationTest.kt`
- `src/test/java/me/foxtails/palustris/ui/SignInScreenTest.kt`
- `src/test/java/me/foxtails/palustris/ui/large/WideNavigationTest.kt`
- `src/test/java/me/foxtails/palustris/ui/navigation/NavigationTest.kt`
- `src/test/java/me/foxtails/palustris/ui/notifications/NotificationsScreenTest.kt`
- `src/test/java/me/foxtails/palustris/ui/search/SearchPanelRestorationTest.kt`
- `src/test/java/me/foxtails/palustris/ui/settings/SettingsDisplayTest.kt`
- `src/test/java/me/foxtails/palustris/ui/settings/SettingsViewModelTest.kt`

`SocialSource.kt`, `NotificationSyncOrchestrator.kt`, `DirectMessageRepository.kt`,
and `PostRow.kt` sit near protected boundaries. Touch style only. Change no
contract, no test, and no ownership rule.

Do not fix `HomePagingDemand.kt`, `EditProfileScreen.kt`, or any other file the
project did not touch. Their entries are deferred global debt.

Verification for Slice 4.2: run the full local CI-parity gate (see below). It
covers `ktlintCheck` over the whole tree.

Commit Slice 4.2 separately with explicit reviewed paths. Name the Slice 4.1
commit in the message.

## Invariants (do not change)

- No behavior changes. No persisted-format migration. No dependency additions.
- Protocol branches remain inside adapters and source construction.
- Account/session generations, revisions, and write authorities remain unchanged.
- Do not weaken or replace existing regression tests.
- Do not split any file listed as a non-target in `docs/fix_0.4.0.md` section 2.
- Do not add ktlint baseline exemptions.
- Do not reformat unrelated files.
- Do not convert warnings into tolerated audit findings.

## Verification contract (run in order after Slice 4.2)

```text
python -m unittest discover -s tools/tests

python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check

.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug :app:ktlintCheck :app:assembleDebug :app:assembleRelease
```

Use the Windows wrapper. Use an explicit timeout and closed standard input.

Expected:

- 51 Python tests pass.
- Architecture audit exits 0 with zero regressions.
- All JVM tests pass (approximately 1,692 tests across 161 suites).
- lint, ktlint, debug assembly, and release assembly all pass.

Known flakes: `MastodonIntegrationTest` cancellation and `NotificationsViewModelTest`
test-isolation. They pass alone; record them in `logs/BUGS.txt` and rerun the gate if
only a known flake fails.

## If verification fails

1. Identify the failing check.
2. Determine whether the failure is caused by the Phase 4 change.
3. If yes: fix it within the Phase 4 scope. Do not expand scope.
4. If the failure is a known flake: record it in `logs/BUGS.txt` and rerun.
5. If the failure is unrelated (pre-existing): record it and report it. Do not fix
   unrelated failures.
6. Stop after two failed fixes for the same root problem.

## If verification passes

1. Update `docs/agents/tasks/hardening-0.4.0-phase2.md` — add a "Phase 4" section recording:
   - Slice 4.1 commit hash and the baseline diff summary.
   - Slice 4.2 commit hash and the files cleaned.
   - Full gate results.
   - Any flakes encountered.
2. Update `docs/agents/handoff.md`:
   - Record the current position (Phase 4 complete, ready for Phase 5).
   - Name the last safe commit.
   - List known blockers (physical-device, API 29 instrumentation, TalkBack, signing,
     live-server checks remain unverified).
3. Update `logs/BUGS.txt` if any flakes were encountered.

## Commit

Commit Slice 4.1 and Slice 4.2 separately with explicit reviewed file paths.
Inside each message, name the preceding safe commit and the slice subject.
Commit the record updates with the Slice 4.2 commit or as a records-only commit
after it. Do not amend existing history.

## Deliverables

- `tools/architecture-baseline.json` carries production `fileMetrics` and
  `functionMetrics`. A second record run produces no diff.
- No hardening-touched file carries ktlint baseline debt, or the remainder is
  recorded as explicitly deferred with reasons.
- No new ktlint baseline debt exists anywhere.
- The full CI-parity gate passes.
- Documentation and handoff are current.
- A clear handoff exists for Phase 5 (documentation integrity and final acceptance).

## Non-goals

- Do not begin Phase 5.
- Do not push.
- Do not fix unrelated pre-existing failures.
- Do not run the global ~164-file formatting cleanup.
- Do not run a device test. Phase 4 changes no behavior.
- Do not reformat unrelated files.
