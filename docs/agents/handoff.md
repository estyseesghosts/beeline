# Handoff

**Status:** `docs/agents/tasks/beeline-0.4.0.md` is the active durable task state for Beeline 0.4.0.

Historical records stay linked as history, never as a queue. See [Plan 04 archive](../archive/agents/plan04-utility-retention.md), [wide detail photo sizing](../archive/agents/wide-detail-photo-sizing.md), and [docs archive](../archive/agents/docs-archive.md).

## Where To Start

1. `AGENTS.md`.
2. `docs/agents/tasks/beeline-0.4.0.md`.
3. `git status` and the current diff.
4. Recent commits.
5. `docs/beeline_0.4.0.md` (active plan; git-ignored planning material under `docs/` unless explicitly added).
6. `docs/260923_current_state.md` (active cleanup review; also git-ignored).
7. `docs/agents/app-shell-ownership.md` and `docs/agents/protocol-and-session-ownership.md`.
8. `logs/BUGS.txt`.

## Current Position

- Plan 04-A through 04-K have committed work: 04-A `ee52ba9`, 04-B `6a87c76`, 04-C `633cd7a`, 04-D `f0735df`, 04-E1 `b04b2e8`, 04-F `9a1b055`, 04-G `d5f694c`, 04-H1 `cc900d9`, 04-H2 `fab0131`, 04-I `51351a7`, 04-J1 `dc9ae5f`, 04-J2 `c6a2ed0`, 04-J3 `484b995`, and 04-K `9e9a0d7`.
- The earlier ditch decision `b0d3ddd` is dated history in the archive, not current status.
- Photo Grid detail photo sizing is committed in `e25c0e5` and `736a26f`. Coverage lives in `SinglePostScreenTest`.
- Slice 0A is complete. This record ships together with the slice commit. The commit hash is recorded at the next slice boundary (0B). Slice 0B is the current and next slice.
- Retention has source- and test-level characterization only. Heap, disk, and Room measurements were never collected and remain unverified.

## Last Safe Commit

`8c964a3` is the last safe commit before 0A. Record this slice's own commit hash at the 0B boundary.

## Next Slice

Slice 0B (capture UI requirements and baselines per `docs/beeline_0.4.0.md`) follows `docs/agents/tasks/beeline-0.4.0.md`. Slice 0A passed 51 Python tests, and the architecture audit `--check` exited 0 with 605 findings and no new regressions against baseline. All 120 relative links across 11 touched Markdown files resolve. No source changes occurred. Gradle was not run. Device and live-server checks remain unverified.

## Historical work and open verification

S1, P1, Q1, T1, Plan 03, and Plan 04 are complete historical series. See the [archive index](../archive/README.md) and the [Plan 04 archive](../archive/agents/plan04-utility-retention.md). V1 device verification, live-server behavior, and signed-release checks remain open.

## Known Blockers

- No emulator or device is reachable. Connected instrumentation remains unverified.
- Live-server and signed-release behavior remain unverified.
- The Android 15 system-bar instrumentation failure remains in `logs/BUGS.txt`.
- Git-ignored planning material under `docs/decomposition_3/` and directly under `docs/` must not be force-added.
- Two localization tests must be tightened when catalogs return: `AppLocaleControllerTest.everyLocaleResolvesATranslatedValueOrFallback` and `LocalizationResourceTest.localeCatalogMatchesResourcesEnumAndAndroidConfig`.
- The residual 03-G ordering risk remains recorded in the Plan 03 task state.
- The full Gradle gate remains pending for the final release gate. No full gate has run for 0.4.0.

## Process Rules

- Keep one slice, one behavior, and one commit. Include current task state, task log, handoff, and affected documentation.
- Rewrite the handoff at each slice boundary.
- Keep ownership pages current in the same slice when their boundary changes.
- Stage only slice files. Preserve unrelated worktree changes.
- Use Beeline in user-facing text. Keep the internal codename out of user-facing content.
- Keep protocol behavior in adapters. Keep account secrets and tokens out of presentation contracts.
- Do not change a stored format without a migration in the same slice.
- Do not use subagents unless the user asks.
