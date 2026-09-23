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
- Slice 0A is complete at `ba3fe53`. Slice 0B is complete at `72fafc4`. Slice L0 is complete at `cbf8698`. Slice 1A is complete at `af1f983`. Slice 1B1 is complete at `5207a96`. Slice 1B2 is complete at `9e29ef4`. Slice 1B3 is complete at `d21280e`. Slice 1B4 is complete at `c3802c9`.
- Slice 1C completed at `71c6f7d`, the start boundary and last safe commit for 1D1. Slice 1D1 is complete in the current worktree; its hash is recorded at the 1D2 boundary.
- Retention has source- and test-level characterization only. Heap, disk, and Room measurements were never collected and remain unverified.

## Last Safe Commit

`71c6f7d` (1C complete) was the last safe commit at the start of 1D1. The 1D1 hash is recorded at the 1D2 boundary.

## Next Slice

Completed += `Slice 1D1 — complete (ships with this commit)`; the 1D1 hash is recorded at the 1D2 boundary.
Current slice = `1D2 — Implement composite inbox cursor and merge order in MisskeyDirectMessageService.kt`; Next = 1D2. 1B5 remains paused because its non-thread response-cap gate is unmet.
Last safe commit = `71c6f7d` (1C complete at 1D1 start); the 1D1 hash is recorded at the 1D2 boundary.

## Historical work and open verification

S1, P1, Q1, T1, Plan 03, and Plan 04 are complete historical series. See the [archive index](../archive/README.md) and the [Plan 04 archive](../archive/agents/plan04-utility-retention.md). V1 device verification, live-server behavior, and signed-release checks remain open.

## Verification

- 1B3 moderation cursors pin `/api/v1/accounts/blocked` or `/api/v1/accounts/muted`. Continuations allow one nonblank `max_id`, `since_id`, or `min_id`; a missing `limit` is valid, and a present `limit` must occur once with value exactly `40`. Cursor state is in-memory; no persistence migration is needed.
- 1B4 uses the existing `encodePathSegment()` helper for unfavorite IDs. `MastodonIntegrationTest.unfavoriteEncodesReservedIdCharactersAndPreservesPlainIdPath` checks literal paths, POST methods, and bearer headers for a plain ID and a composite reserved-character ID. Verification and raw-interpolation grep findings are recorded in the task state.
- 1D1 tests assert the decoded `mentioned` cursor value, timestamp-merged order `m-new, s-new, m-old` with unequal fixture timestamps, and both continuation POST methods and complete bodies with `untilId: m-old`. Read tests keep repeated valid calls request-free and require blank/foreign-origin IDs to throw `Unsupported("direct.read")` without requests. The server-failure test walks every cause message for token absence. The fixture token is a non-secret test double asserted only as part of expected request-body bytes; production tokens never appear in tests or errors; error cause chains are asserted token-free. Production behavior did not change apart from a comment documenting the validation-only no-op until a verified endpoint exists.
- `MastodonSource` calls moderation service methods without a preceding capability refresh or other I/O. Cursor rejection occurs before network work.
- `ModerationServiceTest.mastodonBlockedAndMutedPagesUseOpaqueRouteBoundCursors` checks GET method and bearer authorization on both pages, routes, encoded opaque continuation bytes, and exact returned item order. `mastodonModerationRejectsTamperedAndLegacyCursorsBeforeRequest` checks invalid query shapes and payload tampering with unchanged request counts. Link rejection and loop checks remain in `mastodonModerationRejectsInvalidLinksAndCurrentUrlLoop`.
- `mastodonModerationRejectsValuelessLimitLinksAndAllowsMissingLimit` rejects valueless, empty, and duplicate `limit` Link values and accepts a missing `limit`. Only a present value other than exactly `40` is invalid.
- 1B3 focused moderation, Mastodon integration/source contract/Misskey integration, lint, Python, and architecture gates passed. Review repair outcomes are recorded in the task state and task log.
- The moderation cursor binds origin/account/kind/variant/route/query, but not `sessionRevision` or `sourceInstance`, unlike `MastodonPageCursor`. The ViewModel keeps cursors in memory and paging checks `AccountSourceRegistry.isCurrent`. A direct `SocialSource` caller can replay a same-account cursor after source replacement. Session/source-instance binding was consciously deferred because cursors have an in-memory lifetime and paging has the current-account guard.

- `hashtagCursorRejectsAnotherQueryWithoutChangingTheCursor` uses a real cats Link cursor unchanged with `searchHashtag("dogs", cursor)`. It asserts `Unsupported("pagination.cursor")` and no additional request.

- 1C shares row and Photo Grid quote preview rendering through `ui/posts/QuotePreviewCard.kt`. Hidden quote text does not enter composition or semantics. The visible card retains author, placeholder, label, and open action. Full decisions and tests are in the active task state.
- 1C explicitly keeps hidden quote cards present. `HiddenContentPresentation.Remove` remains limited to parent-post handling and Photo Grid filtering. Parent handling and Photo Grid geometry were not changed.
- The implementing agent reported that focused quote tests, `ContentWarningPolicyTest`, `PostTextPresentationTest`, lint, Python tests, and the architecture audit passed. The independent reviewer confirmed source and diff only because shell access denied Python and Gradle reruns.
- The full `test assembleRelease` gate did not run for 1C. Its red status remains a task-level blocker. Device, foldable, RTL, and TalkBack checks remain unverified.
- `bookmarkCursorRejectsTimelineRouteBeforeCapabilityProbe` uses a real bookmark Link cursor with `timeline(Home, cursor)` on a source with the real capability probe. It asserts `Unsupported("pagination.cursor")` and no additional request.
- The earlier mixed case is now named `hashtagCursorPayloadTamperingAndOtherRoutesAreRejected`; its tampered query payload has a cats path and proves tampering/path validation, not unchanged-cursor cross-query rejection.
- Those outcomes apply to the 1B2 review repair, which changed tests and records only. The 1B3 source lint gate passed separately above.
- At 1C start, HEAD and last safe commit were `c3802c9` (1B4). The staged `docs/classic_navigation.md` and unrelated worktree changes remain untouched. Existing blockers remain unchanged.
- 1C review repair now queries the complete hidden quote body in the Remove absence assertion. The sibling consistency and font-scale assertions already use their complete fixture bodies; focused test, Python, and architecture audit reruns passed.
- The 1B3 review repair gates passed: focused moderation tests, adapter integration/contracts, lint, Python tests, and architecture audit. Exact outcomes are in the task state and task log.
- The 1B4 slice consists of `MastodonSource.kt`, `MastodonIntegrationTest.kt`, and these two records; the whole-worktree diff also lists unrelated pre-existing changes that are never staged or committed with a slice.
- 1D1 focused adapter/repository tests passed. Python, architecture, lint, and protocol regression results are recorded in the active task state and task log.

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
