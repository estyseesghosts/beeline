# Next-agent prompt — Complete Phase 5 (documentation integrity and final acceptance)

This is a self-contained dispatch for the next agent. Follow it in order.
The authoritative plan is `docs/fix_0.4.0.md`, section "Phase 5" (Slices 5.1, 5.2, 5.3) and
the commit sequence entry `H14`. Source and Git outrank every plan and completion claim.

## use subagents

- you have finder, which you are expected to use as your codebase explorer. tracing functions, sweeping through files, etc. 
- you have implementer, which you are expected to use for bite-sized narrowly scoped implementations. 
- don't ever give your subagents long prompts.
- be specific with what you request from your subagents.

## Current state

- `main` is at `9d2edf5` — `Clean ktlint debt in touched files (Slice 4.2)`. Its preceding safe
  commit is `22e62d5`.
- Phases 0 through 4 are committed and gate-verified. Phase 5 has not started.
- Do not push. Do not start any new refactor.

## Working tree changes to preserve, do not touch

- `.opencode/agents/orchestrator.md`, `.opencode/agents/problem_solver.md`,
  `.opencode/agents/targeted_fixer.md`, `importantdocs/writing_style.md` (modified)
- `app/src/test/java/me/foxtails/palustris/ui/photogrid/PhotoGridFeedViewModelTest.kt`,
  `currentbehaviour.png`, `intendedbehaviour.png` (deleted)
- Untracked: `.tmp-inspect-full/`, `*.png` captures, `docs/agents/tasks/*prompt*.md`,
  `docs/agents/tasks/4c.md`, `tools/scripts/adb_*.py`, `__pycache__/`, `logs/` captures

Commit only reviewed paths with explicit `git add <path>`.

## Slice 5.1 — Reconcile architecture documentation with final source

Read first: `docs/agents/documentation-rules.md`, `docs/agents/documentation-inventory.md`,
`importantdocs/writing_style.md`.

Review at minimum:

- `docs/wiki/architecture.md`
- `docs/wiki/ui-and-navigation.md`
- `docs/wiki/build-test-and-release.md`
- `docs/agents/app-shell-ownership.md`
- `docs/agents/protocol-and-session-ownership.md`
- `docs/agents/handoff.md`
- `docs/agents/tasks/hardening-0.4.0-phase2.md`

Also check the other wiki pages and `docs/agents/README.md` for drift if a quick path check
finds any.

Required result:

- Every documented primary owner (class, file, package) resolves to a real current file.
  Script a path check, then confirm ownership claims by reading source.
- Known drift: `AccountManager` references that predate its move to `ui/session/`.
- Name the final shape: shell adapters (`ShellHomeDestination`, `ShellSearchDestination`,
  `AppNotificationsDestinationContent`, `ShellProfileDestination`), `ProfileTimelinePresentation`,
  Home paging/rendering split, and `MisskeySource` with its `Misskey*Service` collaborators
  including `MisskeyThreadService`. Verify each name exists before documenting it.
- Archived plans stay historical. Fix only broken links where repository policy requires it.
- Product text uses **Beeline**. Palustris stays internal; preserve compatibility identifiers.

## Slice 5.2 — Final JVM and build acceptance

Run the complete CI-parity gate on the final tree, using the Gradle wrapper with
`--no-daemon --console=plain`, an explicit timeout, and closed standard input:

```text
python -m unittest discover -s tools/tests

python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check

.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug :app:ktlintCheck :app:assembleDebug :app:assembleRelease
```

Then inspect `git status`, `git diff`, and `git diff --cached`. Run the diagnostic audits:

```text
python tools/scripts/function_audit.py .
python tools/scripts/file_audit.py .
```

The final audits are diagnostic only. Do not start a refactor because warnings remain.

Required result: zero architecture regressions, no new ktlint baseline debt, all JVM and
Compose tests green, lint green, debug and release assembly green.

Known flakes: `MastodonIntegrationTest` cancellation and `NotificationsViewModelTest`
isolation. They pass alone. Record them in `logs/BUGS.txt` and rerun if only a known flake fails.
Phase 4 baseline: 52 Python tests, 675 audit findings, about 1,692 JVM tests in 161 suites.

Run the Gradle gate in the background and write documentation while it runs.
Do not edit source during a gate run.

## Slice 5.3 — Runtime acceptance

This is separate evidence from JVM tests. Do what the environment allows and record the rest
as unverified. Never substitute mocks for live checks.

- **API 29 instrumentation:** needs the GitHub `instrumentation-api29` job, which needs a push.
  The user has not authorized a push. Record it as unverified and do not push.
- **Emulator-5554 (API 37) is attached.** Install `app/build/outputs/apk/debug/app-debug.apk`,
  launch `me.foxtails.palustris/.MainActivity`, and capture screenshots to `logs/`. Use the
  existing `tools/scripts/adb_*.py` helpers. Check what applies on this emulator: compact narrow
  navigation, chip placement, IME-open composer/search/DM behavior, Profile chips, navigation
  restoration, and forced RTL with unchanged physical navigation anchors.
- **Compact-wide (445 x 704 dp) and expanded/tablet:** attempt only with a matching emulator or
  `wm size`/`wm density` override. Reset the override afterward. Otherwise record as unverified.
- **TalkBack, physical devices, signing, live Misskey and Mastodon accounts:** no test
  accounts or hardware exist. Record each as unverified.
- A SIGABRT from an emulator HAL process (`android.hardwar`) appears in the crash log. It is
  not the application. Check `logcat` for `FATAL EXCEPTION` with the app package instead.
- Subagents may not run ADB. You run all ADB work.

## Records and commit

- Add a "Phase 5" section to `docs/agents/tasks/hardening-0.4.0-phase2.md`: the slice 5.1 changes,
  gate results, audit warnings, runtime evidence, and every unverified item with its reason.
  Rewrite task state; do not append history.
- Rewrite `docs/agents/handoff.md`: current position (hardening plan complete or the exact
  remaining gaps), last safe commit `9d2edf5`, and known blockers.
- Create `logs/YYMMDD-HHMMSS.txt` for the goal, slices, files, and risks. Update `logs/BUGS.txt`
  for flakes and blockers.
- Commit documentation and records together in one commit (`H14`). Name `9d2edf5` as the
  preceding safe commit in the message. Do not commit unrelated changes.
- If Slice 5.1 needs source changes (it should not), stop and ask the user.

## Invariants

- No behavior change, persisted-format migration, or dependency addition.
- Do not weaken or replace regression tests. Do not add ktlint baseline exemptions.
- Do not fix the six deferred ktlint entries (`SavedCollectionsHost`, `ConnectedApp`,
  `PostThreadViewModel`, `MastodonIntegrationTest`, `NavigationTest`, `SettingsViewModelTest`).
- Do not convert audit warnings into tolerated findings. Do not push.
- Never log secrets or complete API responses. Keep records free of credentials.

## If verification fails

1. Identify the failing check and whether Phase 5 caused it. Documentation edits should not.
2. A known flake: record it and rerun. A pre-existing unrelated failure: record and report it.
3. Stop after two failed fixes for the same root problem.

## Deliverables

- Every documented primary owner resolves to a real file.
- The full CI-parity gate passes on the final tree.
- Runtime evidence exists where possible, and each unverified item is recorded with a reason.
- Task record, handoff, and logs are current, and one H14 commit holds them.
- Final report names the commit hash, gate results, and remaining unverified items.
