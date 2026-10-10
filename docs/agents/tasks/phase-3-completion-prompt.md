# Next-agent prompt — Complete Phase 3 (Slice 3.1 verification and closeout)

This is a self-contained dispatch for the next agent. Follow it in order.

## Current state

- `main` is at `0bd8e7d` — `Extract Misskey thread service` (cherry-picked from another machine).
- Phase 0, Phase 1, and Phase 2 are complete, committed, and gate-verified.
- Slice 3.1 code is committed but **not yet verified** — the original agent could not run Gradle
  (Maven Central returned 429 in their environment).
- The cherry-pick applied cleanly with zero conflicts.
- Do not push. Do not begin Phase 4 or Phase 5.

## Working tree uncommitted changes (preserve, do not touch)

These are unrelated user/agent work that must survive the merge:

- `.opencode/agents/orchestrator.md` (modified)
- `.opencode/agents/problem_solver.md` (modified)
- `.opencode/agents/targeted_fixer.md` (modified)
- `importantdocs/writing_style.md` (modified)
- `app/src/test/java/me/foxtails/palustris/ui/photogrid/PhotoGridFeedViewModelTest.kt` (deleted)
- `currentbehaviour.png` (deleted)
- `intendedbehaviour.png` (deleted)
- Untracked: `.tmp-inspect-full/`, various `*.png` captures, `docs/agents/tasks/4c.md`,
  `docs/agents/tasks/next-phase-3-prompt.md`, `tools/scripts/adb_*.py`, `__pycache__/` dirs

## Required reading (before any edit)

1. `AGENTS.md` and every page it links as required.
2. `docs/agents/workflow.md`, `docs/agents/agent-control.md`, `docs/agents/engineering-rules.md`,
   `docs/agents/operation-rules.md`, `docs/agents/documentation-rules.md`.
3. `docs/fix_0.4.0.md` — read the project-wide invariants (section 2), the verification contract
   (section 3), and Phase 3 (section "Phase 3 — Finish the Misskey source/service boundary").
4. `docs/agents/handoff.md` and `docs/agents/tasks/hardening-0.4.0-phase2.md` for the completed
   Phase 2 record and the last safe commit.
5. `importantdocs/writing_style.md` before writing any document.

## Objective — Verify and close out Slice 3.1

The cherry-picked commit `0bd8e7d` contains the Slice 3.1 implementation:

- New file: `app/src/main/java/me/foxtails/palustris/data/misskey/MisskeyThreadService.kt`
- `MisskeySource.kt` — thread logic extracted, delegates through `request("thread")`
- `MisskeyThreadContinuationTest.kt` — updated
- `app/ktlint-baseline.xml` — MisskeySource entry removed
- `docs/agents/protocol-and-session-ownership.md` — updated
- `docs/wiki/architecture.md` — updated

Your job: verify this implementation passes all gates, fix any issues within the Slice 3.1 scope,
and produce a clean handoff for Phase 4.

## Invariants (do not change)

- No externally observable `SocialSource.threadContext` behavior changes.
- No persisted-format migration, no dependency additions.
- Protocol branches remain inside adapters and source construction.
- Account/session generations, revisions, and write authorities remain unchanged.
- Do not weaken or replace existing regression tests.
- Do not split `ShellContent`, `NotificationRepository`, `NotificationJsonCodec`,
  `DirectMessageViewModel`, `MisskeyDirectMessageService`, `SocialSource`, or `SvgIconPaths`.
- Do not add ktlint baseline exemptions.
- Do not reformat unrelated files.

## Verification contract (run in order)

### Step 1 — Focused gate

```text
.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests "me.foxtails.palustris.data.misskey.MisskeyThreadContinuationTest" --tests "me.foxtails.palustris.data.misskey.MisskeyIntegrationTest" --tests "me.foxtails.palustris.data.misskey.SocialSourceContractTest"
```

Expected: all three test classes pass.

If they fail: inspect the failure. If the failure is caused by the Slice 3.1 extraction, fix it
within scope. If it is a known flake (`MastodonIntegrationTest` cancellation or
`NotificationsViewModelTest` test-isolation), record it and rerun.

### Step 2 — Local CI-parity completion gate

```text
python -m unittest discover -s tools/tests

python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check

.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug :app:ktlintCheck :app:assembleDebug :app:assembleRelease
```

Use the Windows wrapper. Use an explicit timeout and closed standard input.

Expected:
- 51 Python tests pass.
- Architecture audit exits 0 with zero regressions.
- All JVM tests pass (approximately 1,691 tests across 161 suites).
- lint, ktlint, debug assembly, and release assembly all pass.

Known flakes: `MastodonIntegrationTest` cancellation and `NotificationsViewModelTest` test-isolation.
They pass alone; record them and rerun the gate if only a known flake fails.

### Step 3 — Inspect the diff

```text
git diff 2cc4d7a..0bd8e7d
```

Confirm the diff matches the intended Slice 3.1 scope. Confirm no unrelated changes are present.

## If verification fails

1. Identify the failing check.
2. Determine whether the failure is caused by the Slice 3.1 extraction.
3. If yes: fix it within the Slice 3.1 scope. Do not expand scope.
4. If the failure is a known flake: record it in `logs/BUGS.txt` and rerun.
5. If the failure is unrelated (pre-existing): record it and report it. Do not fix unrelated failures.
6. Stop after two failed fixes for the same root problem.

## If verification passes

1. Update `docs/agents/tasks/hardening-0.4.0-phase2.md` — add a "Phase 3" section recording:
   - Slice 3.1 commit hash (`0bd8e7d`).
   - Focused test results.
   - Full gate results.
   - Any flakes encountered.
2. Update `docs/agents/handoff.md`:
   - Record the current position (Phase 3 complete, ready for Phase 4).
   - Name the last safe commit (`0bd8e7d`).
   - List known blockers (physical-device, API 29 instrumentation, TalkBack, signing, live-server
     checks remain unverified).
3. Update `docs/agents/tasks/BUGS.txt` if any flakes were encountered.

## Commit

If you made any fixes, commit them separately with explicit reviewed file paths.
Inside the commit message, name the preceding safe commit (`0bd8e7d`) and the fix subject.

If no fixes are needed, the existing `0bd8e7d` commit stands. Do not amend it.

## Deliverables

- Slice 3.1 passes focused tests and the full CI-parity gate.
- `MisskeySource` is a clear adapter facade with no embedded thread acquisition.
- `MisskeyThreadService` owns thread transport/acquisition and the continuation store lifetime.
- No new ktlint baseline debt.
- Documentation and handoff are current.
- A clear handoff exists for Phase 4 (Slice 4.1 — record architecture metric baseline).

## Non-goals

- Do not begin Phase 4 or Phase 5.
- Do not push.
- Do not fix unrelated pre-existing failures.
- Do not expand the Slice 3.1 scope.
- Do not reformat unrelated files.
