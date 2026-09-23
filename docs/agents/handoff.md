# Handoff

**Status:** `docs/agents/tasks/beeline-0.4.0.md` is the active durable task state for Beeline 0.4.0.

Historical records stay linked as history, never as a queue. See [Plan 04 archive](../archive/agents/plan04-utility-retention.md), [wide detail photo sizing](../archive/agents/wide-detail-photo-sizing.md), and [docs archive](../archive/agents/docs-archive.md).

## Where To Start

1. `AGENTS.md`.
2. `docs/agents/tasks/beeline-0.4.0.md`.
3. `docs/agents/beeline-0.4.0-ui-baseline.md` for 0B classification and open device gates.
4. `git status` and the current diff.
5. Recent commits.
6. `docs/beeline_0.4.0.md` (active plan; git-ignored planning material under `docs/` unless explicitly added).
7. `docs/260923_current_state.md` (active cleanup review; also git-ignored).
8. `docs/agents/app-shell-ownership.md` and `docs/agents/protocol-and-session-ownership.md`.
9. `logs/BUGS.txt`.

## Current Position

- Plan 04-A through 04-K have committed work: 04-A `ee52ba9`, 04-B `6a87c76`, 04-C `633cd7a`, 04-D `f0735df`, 04-E1 `b04b2e8`, 04-F `9a1b055`, 04-G `d5f694c`, 04-H1 `cc900d9`, 04-H2 `fab0131`, 04-I `51351a7`, 04-J1 `dc9ae5f`, 04-J2 `c6a2ed0`, 04-J3 `484b995`, and 04-K `9e9a0d7`.
- The earlier ditch decision `b0d3ddd` is dated history in the archive, not current status.
- Photo Grid detail photo sizing is committed in `e25c0e5` and `736a26f`. Coverage lives in `SinglePostScreenTest`.
- Slice 0A is complete at `ba3fe53`. Slice 0B is complete at `72fafc4`. Slice L0 is complete at `cbf8698`. Slice 1A is complete at `af1f983`. Slice 1B1 is complete; record its hash at the 1B2 boundary.
- Retention has source- and test-level characterization only. Heap, disk, and Room measurements were never collected and remain unverified.

## Last Safe Commit

`af1f983` (1A) is the last safe commit. Record the 1B1 hash at the 1B2 boundary.

## Next Slice

Slice 1B1 is complete. Its review repairs are complete in the current worktree. Current and next slice is 1B2: bind saved-post and hashtag cursors in `MastodonSource.kt` and remove the temporary legacy replay path. Bookmarks and hashtags still replay raw Links until 1B2; timeline cursors are route-bound. Record the 1B1 hash at the 1B2 boundary. Last safe commit is `af1f983`. Keep the red full-suite health blocker distinct from 1B2, 1B5, and device/live blockers.

## Historical work and open verification

S1, P1, Q1, T1, Plan 03, and Plan 04 are complete historical series. See the [archive index](../archive/README.md) and the [Plan 04 archive](../archive/agents/plan04-utility-retention.md). V1 device verification, live-server behavior, and signed-release checks remain open.

## Known Blockers

- No emulator or device is reachable. Connected instrumentation remains unverified.
- Live-server and signed-release behavior remain unverified.
- The Android 15 system-bar instrumentation failure remains in `logs/BUGS.txt`.
- Git-ignored planning material under `docs/decomposition_3/` and directly under `docs/` must not be force-added.
- Two localization tests must be tightened when catalogs return: `AppLocaleControllerTest.everyLocaleResolvesATranslatedValueOrFallback` and `LocalizationResourceTest.localeCatalogMatchesResourcesEnumAndAndroidConfig`.
- The residual 03-G ordering risk remains recorded in the Plan 03 task state.
- No full green gate has passed. One full run executed during 1B1 and is red with 16 failures (above).
- NavigationTest has two failures. Their pre-existing status is separately verified at `ba3fe53` with an empty `app/src` diff. The cause is not established. Investigate in a dedicated slice before phase 10.
- Fourteen failures affect DraftActionsTest (2), CapabilityCacheTest (2), MisskeyThreadContinuationTest (5), and NotificationSyncOrchestratorTest (5). Their executed test and production sources are byte-identical to `af1f983`; `git diff af1f983 --name-only` lists none of them. Direct grep finds no reference from those classes and subjects to symbols changed by L0, 1A, or 1B1. Focused runs reproduce all 14 failures. Transitive closure was not exhaustively proven. Introduction commits were not bisected because `git worktree add` was blocked by permission. The baseline was not executed. Product-versus-environment cause is not established. These failures are not attributable to the 0.4.0 slices by available evidence. Do not weaken or skip tests. A dedicated investigation slice owns these failures.
- Full unit suite is RED (16 failures in `test assembleRelease`). A dedicated investigation slice must restore full-suite green before the phase-10 release gate.
- 1B5 gate unmet: source verification shows the only response caps routed through request{} are thread reads (Mastodon: MastodonThreadService; Misskey: MisskeySource thread paths). No non-thread capped path exists, so the plan's required non-thread failure test cannot be written without adding a new conservative response cap per adapter (a behavior change). 1B5 is paused pending an explicit decision. 1E1/1E2 do not depend on 1B5 and proceed.
- 1B1 follow-up cursor tests passed. They cover identity mismatch, unsafe decoded URL properties, hardened query validation, missing Link termination, and malformed payloads. The test names and exact focused gate results are in `docs/agents/tasks/beeline-0.4.0.md`.

## Process Rules

- Keep one slice, one behavior, and one commit. Include current task state, task log, handoff, and affected documentation.
- Rewrite the handoff at each slice boundary.
- Keep ownership pages current in the same slice when their boundary changes.
- Stage only slice files. Preserve unrelated worktree changes.
- Use Beeline in user-facing text. Keep the internal codename out of user-facing content.
- Keep protocol behavior in adapters. Keep account secrets and tokens out of presentation contracts.
- Do not change a stored format without a migration in the same slice.
- Do not use subagents unless the user asks.
