# Handoff

**Status:** `docs/agents/tasks/beeline-0.4.0.md` is the active durable task state. This handoff is the next-agent pointer.

## Start here — maintainer directive

1. Read `AGENTS.md` and `importantdocs/writing_style.md`.
2. Reread `docs/beeline_0.4.0.md` **in full**, including its updated requirements, packet table, and execution map.
3. Read `docs/agents/tasks/beeline-0.4.0.md`, `docs/agents/beeline-0.4.0-ui-baseline.md`, and `logs/BUGS.txt`.
4. Check `git log --oneline -12`, `git status --short`, HEAD, and the current diff.
5. Obtain the maintainer's specified changes to phases 0 and 1. If they are not available, stop and ask for them.

**The maintainer requires one more traversal of phases 0 and 1 before later packets continue.** Treat the updated `docs/beeline_0.4.0.md` requirements as authoritative when an executed slice conflicts with them. Do not assume the existing commits satisfy the revised plan. First map the changes to owners, tests, and commit-sized packets. Do not implement an unspecified change.

The plan is git-ignored planning material directly under `docs/`. Do not force-add it as part of an unrelated slice. Also check `docs/260923_current_state.md`, `docs/agents/app-shell-ownership.md`, and `docs/agents/protocol-and-session-ownership.md` when the next packet touches their boundaries. Historical Plan 04 and retention records are history, not a work queue.

## Current position and last safe code-slice commit

Last safe code-slice commit: `fa087d0` (1D2). The subsequent records-sync commit changes documentation only. None of the 11 listed commits has been pushed.

Executed order, oldest to newest:

- 0A `ba3fe53`: reconciled active records and source baseline.
- 0B `72fafc4`: recorded the UI requirements baseline; device measures remain open.
- L0 `cbf8698`: repaired the pre-existing `UnusedBoxWithConstraintsScope` lint blocker in `ShellContent.kt`.
- 1A `af1f983`: repaired Mastodon block/mute relationship recovery.
- 1B1 `5207a96`: bound Mastodon timeline cursors to identity, route, query, and variant.
- 1B2 `9e29ef4`: bound bookmark and hashtag cursors; removed legacy raw-Link replay.
- 1B3 `d21280e`: pinned Mastodon moderation cursor routes and query shape.
- 1B4 `c3802c9`: encoded Mastodon unfavorite path IDs.
- 1C `71c6f7d`: shared quote visibility in `QuotePreviewCard` across rows and Photo Grid detail.
- 1D1 `e46e44c`: characterized Misskey inbox streams and the validation-only read no-op.
- 1D2 `fa087d0`: added the identity-bound composite Misskey inbox cursor, per-stream progress, fallback progress, and endpoint-order merge.

`e46e44c` is the historical 1D2 start boundary, not current HEAD. The 1D2 focused gate results are in the task state. No device or live-server verification follows from those results.

## Next slices and packet gates

**Next action:** Re-traverse phases 0 and 1 against the full updated plan and the maintainer's specified changes. Confirm which earlier results still meet the requirements. Define and verify any new work in separate packets before resuming this queue.

After that review and its required packets:

1. **1D3 — Add bounded conversation child continuation and UI partial/retry if the adapter contract supports it.** Gate: after 1D2. Split adapter and UI into two commits when needed. Cover long threads, failures, account replacement, and visible partial/retry state.
2. **1E1 — Remove only unused source constants.** Gate: after 1B and 1D. Compare live service values before deletion.
3. **1E2 — Remove unused mapper arguments and update mapper callers/tests.** It can run parallel with 1E1 only on separate branches.
4. **1B5 — Attribute response-size failures to the correct operation in `MastodonSource.kt` and then `MisskeySource.kt`.** PAUSED. Its gate requires a characterized non-thread capped failure. Source inspection found no non-thread capped path in either adapter. Await an explicit maintainer decision to approve a new conservative cap per adapter, which changes behavior, or to drop or continue pausing this packet. The plan requires separate adapter commits after its gate is met. Do not block independent 1E work on this decision.

Phase 2 follows the phase-1 source fixes and 0B gate, subject to the maintainer review:

- 2A1: Characterize shared transport request, response, cancellation, origin, and Link contracts in tests. Gate: after 1B.
- 2A2: Move the pool and generic HTTP response into `data/transport/`; update imports and tests. Gate: after 2A1; no behavior change.
- 2A3: Separate the neutral authenticated HTTP client from Misskey JSON and endpoint prefixes; migrate Mastodon callers. Gate: after 2A2.
- 2A4: Migrate auth/shared callers and DI; remove the old generic transport placement; run full contracts. Gate: after 2A3.
- 2B1: Pin notification intent extras, preferences, and pending-intent identity in tests. It can run parallel with 2A1 on a separate branch.
- 2B2: Move launch value, codec, and store below UI; preserve stored and incoming formats. Gate: after 2B1.
- 2B3: Separate prepared notification text from the UI formatter; remove data-to-UI imports. Gate: after 2B2.
- 2C1: Move `AccountSearchState` to `ui/search/` with test imports. Gate: after characterization.
- 2C2: Give Search an explicit connected lifetime, release, and projection subscription. Gate: after 2C1.
- 2C3: Give Photo Grid an explicit connected lifetime while preserving its independent preferences and pager. Gate: after 2C2 if the shared host changes; otherwise use a separate branch.
- 2D1: Require `PostPreferencesRepository` in `PostThreadViewModel.kt`. Gate: owner tests; independent of 2D2.
- 2D2: Move profile Liked eligibility behind adapter/source support. Gate: after 2C characterization, adapter contract tests, and lint.
- 2D3: Replace the post favorite-icon protocol branch with a supplied presentation rule. It can run parallel with 2D2 only if the capability contract is untouched.
- 2E is conditional and incremental. Do not run a coordinator extraction unless a later UI change needs it. A file-size metric alone is not a gate.

The plan's packet table and detailed phase text control each packet. Recheck source and tests before editing. Parallel work means separate branches or worktrees, not simultaneous edits or commits in this working tree.

## Execution and shell rules

Use one behavior and one reviewed commit per implementation slice. Have `problem_solver_high` produce a precise plan. Have `targeted_fixer` execute that plan verbatim. Stop and report contradictions instead of improvising. Use `code_reviewer_high` for networking or multi-file slices; use `code_reviewer_low` for a simple single-file slice. Repair until the reviewer approves. Have `git_handler` stage and commit only explicit slice pathspecs. Never push.

Use exactly one command per shell call. Do not chain commands with `;`, `&&`, `|`, backticks, `$env:`, or line continuations. On Windows, run Gradle only as `.\gradlew.bat --no-daemon --console=plain <task>`. Configure `GRADLE_OPTS=-Dorg.gradle.daemon=false` outside the command, keep standard input closed, and set an explicit timeout: at least 900000 ms for tests and 1800000 ms for lint or a full gate. Run focused tests first; run lint when the changed boundary requires it. The coding-task completion gate is `test assembleRelease`, but its existing red result remains unresolved.

## Blockers and verification limits

- The task-level full `test assembleRelease` gate is RED. During 1B1, release assembly completed, but 16 of 1,265 JVM tests failed. Two `NavigationTest` failures were separately reproduced at `ba3fe53` with an empty `app/src` diff; their cause is unknown.
- The other 14 failures are `DraftActionsTest` (2), `CapabilityCacheTest` (2), `MisskeyThreadContinuationTest` (5), and `NotificationSyncOrchestratorTest` (5). Focused runs reproduced them. Byte identity, changed-symbol non-reachability checks, and provenance evidence do not attribute them to the 0.4.0 slices. Transitive closure was not fully proved. Introduction commits were not bisected because `git worktree add` was permission-blocked. Product versus environment cause is unknown. A dedicated investigation slice must restore full-suite green before phase 10. Do not skip or weaken tests.
- 1B5 remains paused for the explicit decision above.
- No device is reachable for this task. Physical-device, API 29, compact/wide rendering, foldable, RTL-device, TalkBack, and connected instrumentation checks remain unverified. Live-server and signed-release behavior remain unverified. The historical Android 15 system-bar instrumentation concern is in `logs/BUGS.txt`.
- `NotificationsViewModelTest` and `MediaViewerScreenTest` have historical flakes. Investigate if they reproduce; do not label a single old isolated failure a new regression.
- Focused mocked HTTP tests do not prove live-server behavior. Compose and Robolectric tests do not prove physical rendering. Report a gate as independently verified only if the reviewing agent actually ran it; otherwise identify the agent-reported result and the reviewer's inspection limit.
- Retention has source/test characterization, not heap, disk, or Room measurements. Keep other historical limits and risks in the task state and `logs/BUGS.txt`.

## Worktree hygiene and records

Preserve modified `.opencode/agents/*` and `importantdocs/writing_style.md`; deleted `currentbehaviour.png` and `intendedbehaviour.png`; untracked `.opencode/agents/adb_handler.md`, `codebase_explorer_android.md`, and `tools/**/__pycache__/`. Preserve the pre-staged, unrelated `docs/classic_navigation.md` exactly as staged, but exclude it from every slice commit. Do not stage, discard, reformat, or commit any of those paths.

New files directly under `docs/`, including `docs/260923_current_state.md` and `docs/beeline_0.4.0.md`, are git-ignored. `docs/wiki/*` and `docs/agents/*` are tracked. The task log `logs/260923-035132.txt` is git-ignored. Read that log and `logs/BUGS.txt` for audit history; do not treat them as the current queue. The durable current state is `docs/agents/tasks/beeline-0.4.0.md`.

The architecture audit currently reports exit 0 with 607 findings and no new baseline regressions. Exit 0 does not mean the architecture is complete. Whole-worktree `git diff --check` currently reports only pre-existing trailing whitespace in `.opencode/agents/orchestrator.md` and `.opencode/agents/targeted_fixer.md`; do not edit those files to make this records slice appear clean.
