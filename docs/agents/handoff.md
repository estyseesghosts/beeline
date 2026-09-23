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
- Slice 0A is complete at `ba3fe53`. Slice 0B is complete at `72fafc4`. Slice L0 is complete at `cbf8698`. Slice 1A is complete at `af1f983`. Slice 1B1 is complete at `5207a96`. Slice 1B2 is complete at `9e29ef4`. Slice 1B3 is complete at `d21280e`.
- Retention has source- and test-level characterization only. Heap, disk, and Room measurements were never collected and remain unverified.

## Last Safe Commit

`d21280e` (1B3) is the last safe commit. The 1B4 hash is recorded at the next slice boundary (1C, since 1B5 is paused).

## Next Slice

Completed += `Slice 1B4 — complete (ships with this commit)`; 1B3 commit = `d21280e`.
Current slice = `1B5 — PAUSED (gate unmet, awaiting decision)`; Next = `1C` (shared quote/CW presentation rule). Resume 1B5 only if the recorded decision approves a conservative response cap per adapter.
Last safe commit = `d21280e`. The 1B4 hash is recorded at the next slice boundary (1C, since 1B5 is paused).

## Historical work and open verification

S1, P1, Q1, T1, Plan 03, and Plan 04 are complete historical series. See the [archive index](../archive/README.md) and the [Plan 04 archive](../archive/agents/plan04-utility-retention.md). V1 device verification, live-server behavior, and signed-release checks remain open.

## Verification

- 1B3 moderation cursors pin `/api/v1/accounts/blocked` or `/api/v1/accounts/muted`. Continuations allow one nonblank `max_id`, `since_id`, or `min_id`; a missing `limit` is valid, and a present `limit` must occur once with value exactly `40`. Cursor state is in-memory; no persistence migration is needed.
- 1B4 uses the existing `encodePathSegment()` helper for unfavorite IDs. `MastodonIntegrationTest.unfavoriteEncodesReservedIdCharactersAndPreservesPlainIdPath` checks literal paths, POST methods, and bearer headers for a plain ID and a composite reserved-character ID. Verification and raw-interpolation grep findings are recorded in the task state.
- `MastodonSource` calls moderation service methods without a preceding capability refresh or other I/O. Cursor rejection occurs before network work.
- `ModerationServiceTest.mastodonBlockedAndMutedPagesUseOpaqueRouteBoundCursors` checks GET method and bearer authorization on both pages, routes, encoded opaque continuation bytes, and exact returned item order. `mastodonModerationRejectsTamperedAndLegacyCursorsBeforeRequest` checks invalid query shapes and payload tampering with unchanged request counts. Link rejection and loop checks remain in `mastodonModerationRejectsInvalidLinksAndCurrentUrlLoop`.
- `mastodonModerationRejectsValuelessLimitLinksAndAllowsMissingLimit` rejects valueless, empty, and duplicate `limit` Link values and accepts a missing `limit`. Only a present value other than exactly `40` is invalid.
- 1B3 focused moderation, Mastodon integration/source contract/Misskey integration, lint, Python, and architecture gates passed. Review repair outcomes are recorded in the task state and task log.
- The moderation cursor binds origin/account/kind/variant/route/query, but not `sessionRevision` or `sourceInstance`, unlike `MastodonPageCursor`. The ViewModel keeps cursors in memory and paging checks `AccountSourceRegistry.isCurrent`. A direct `SocialSource` caller can replay a same-account cursor after source replacement. Session/source-instance binding was consciously deferred because cursors have an in-memory lifetime and paging has the current-account guard.

- `hashtagCursorRejectsAnotherQueryWithoutChangingTheCursor` uses a real cats Link cursor unchanged with `searchHashtag("dogs", cursor)`. It asserts `Unsupported("pagination.cursor")` and no additional request.
- `bookmarkCursorRejectsTimelineRouteBeforeCapabilityProbe` uses a real bookmark Link cursor with `timeline(Home, cursor)` on a source with the real capability probe. It asserts `Unsupported("pagination.cursor")` and no additional request.
- The earlier mixed case is now named `hashtagCursorPayloadTamperingAndOtherRoutesAreRejected`; its tampered query payload has a cats path and proves tampering/path validation, not unchanged-cursor cross-query rejection.
- Those outcomes apply to the 1B2 review repair, which changed tests and records only. The 1B3 source lint gate passed separately above.
- Current HEAD and last safe commit are `d21280e` (1B3). The 1B4 hash is recorded at the next slice boundary (1C, since 1B5 is paused). The staged `docs/classic_navigation.md` and unrelated worktree changes remain untouched. Existing blockers remain unchanged.
- The 1B3 review repair gates passed: focused moderation tests, adapter integration/contracts, lint, Python tests, and architecture audit. Exact outcomes are in the task state and task log.
- The 1B4 slice consists of `MastodonSource.kt`, `MastodonIntegrationTest.kt`, and these two records; the whole-worktree diff also lists unrelated pre-existing changes that are never staged or committed with a slice.

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
