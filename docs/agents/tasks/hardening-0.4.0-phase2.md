# Objective

Complete Phase 2 of [the hardening plan](../../fix_0.4.0.md): presentation-only
decomposition of high-value Compose functions. State does not move. Keep every existing
owner, contract, invariant, and test. This phase does not touch protocol, persistence,
account scope, or navigation policy.
Status: Phase 5 complete; the hardening plan is complete except the unverified items below. Owner and Git operator: orchestrator. Last reviewed: 2026-10-07.

# Invariants

- Keep existing feature contracts, owners, navigation, restoration, and account scope.
- Keep physical clearance, compact-wide caret registration, and shell-owned Home chip state.
- Do not change protocol behavior, persisted formats, dependencies, or unrelated work.
- Do not split ShellContent. Do not add a layer only to hit a line-count target.
- Do not weaken, skip, or rewrite tests. Do not add ktlint baseline exemptions.
- Preserve unrelated worktree changes: `.opencode/agents/*.md`,
  `importantdocs/writing_style.md`, the deleted `PhotoGridFeedViewModelTest.kt` and PNGs,
  untracked captures/scripts/caches, and `docs/agents/tasks/4c.md`.
- Do not push. Do not begin Phase 3.

# Decisions

This task is larger than one safe implementation slice.
Slices 2.1 through 2.4 are complete only when each is committed and gate-verified.
The orchestrator owns implementation, records, validation, and Git.
The user prohibits problem_solver. The orchestrator reviews actual diffs directly before
explicit-path commits.
Commit each verified slice separately before starting the next one.
Known flakes: `MastodonIntegrationTest` cancellation and `NotificationsViewModelTest`
test-isolation (`UncaughtExceptionsBeforeTest`). They pass alone; record them and rerun the gate.

# Completed

- 2.1 committed: preceding safe commit `f28430a`, subject `Separate Home feed effects from
  rendering`. `HomeFeed` now keeps the fallback bubble, derived visible rows,
  `HomePagingDemand`, and list-state selection. `HomePagingEffects` and
  `HomeScrollDirectionEffect` observe that state without owning it. `HomeFeedContent`
  renders the pull-to-refresh, list, error/empty/filtered-empty states, post rows,
  loading-more/footer, and wide dock. No Home state moved. The HomeFeed import ordering is
  fixed and its ktlint baseline entry is removed.
- 2.2 committed: preceding safe commit `6920602`, subject `Extract Profile timeline
  presentation`. New `ui/profile/ProfileTimelinePresentation.kt` renders the compact and
  compact-wide one-column Profile: `ProfileTimelineList`, mobile header, details, compact
  floating category row, compact-wide `LargeBottomDock`, and destination chips.
  `ProfileScreen` retains account resolution, `LaunchedEffect(account.id)`, physical
  clearance normalization, the profile-keyed saveable category visibility, the category chip
  `LazyListState`, self-profile determination, end-clearance calculation, and presentation
  selection. Compact and compact-wide share the one presentation; no third Profile
  implementation exists. A new `ProfileScreenTest` case covers compact ↔ compact-wide
  category visibility retention. The ProfileScreen import ordering is fixed and its ktlint
  baseline entry is removed.
- 2.3 committed: preceding safe commit `b342a3f`, subject `Decompose destination chip
  rendering`. `DestinationChipRow` keeps the selected-entry lookup, selected-entry scroll
  effect, visibility animation, and `LazyRow` assembly. New private `DestinationFilterChip`
  renders one chip (colors, press animation, selection scale, test tag, and semantics).
  The renamed private `DestinationChipVisibilityButton` renders the caret. The public/internal
  call contract, geometry, caret position and rotation, accessibility descriptions,
  selectable-group semantics, reduced-motion behavior, selected-chip auto-scroll, and
  keys/test tags are unchanged. The CategoryChips import ordering is fixed and its ktlint
  baseline entry is removed.
- 2.4 committed: preceding safe commit `490f674`, subject `Share notification filter-row
  presentation`. New private `NotificationFilterRow` renders the shared `DestinationChipRow`
  configuration for both the compact and wide branches. `NotificationsScreen` keeps the filter
  state (`selectedFilterName`, `chipRowVisible`, `chipListState`, `chipEntries`) and the two
  positioning containers: the compact `Box` with `compactContextualControlsPositioningInsets`
  and the wide `LargeBottomDock` with `notificationCaretPresentation`. No test tag, container,
  behavior, or state changed. The NotificationsScreen import ordering is fixed, the unused
  `WindowInsets` import is removed, and its ktlint baseline entry is removed.

# Current slice

Phase 3 Slice 3.1 is committed at `0bd8e7d` and gate-verified. No code fixes were
required. Record updates are the only remaining change.

# Phase 3

- 3.1 committed: preceding safe commit `2cc4d7a`, subject `Extract Misskey thread service`.
  New `data/misskey/MisskeyThreadService.kt` owns thread transport and acquisition: focal
  load, ancestor walk, bounded breadth-first descent, request/batch/time/depth/node limits,
  continuation validation and consumption, error normalization, and the continuation store
  lifetime (16 entries, ten-minute idle expiry, consume-on-use). `MisskeySource` keeps
  request normalization, capability refresh, and the adapter facade. `threadContext`
  validates the focal identity and delegates through `request("thread")`. Constants moved
  with the behavior. The continuation test helper now reads the store through
  `threadService`. The `MisskeySource` ktlint baseline entry is removed with no new
  exemption. Documentation names `MisskeyThreadService` as the thread owner.
- Verification used two read-only subagents: one reviewed the extraction against the
  invariants, one checked test and documentation coverage. Both support the closeout.
  A review question on ktlint baseline consistency was resolved by the green
  `ktlintCheck` run.

# Phase 4

- 4.1 committed at `22e62d5`: complete architecture metrics baseline.
- 4.2 committed: preceding safe commit `22e62d5`, subject `Clean ktlint debt in touched files (Slice 4.2)`.
  Import blocks were ASCII-sorted in 17 files and unused imports were removed from 3
  (`AndroidNotificationPresenter`, `PalustrisApp`, `SearchScreen`). 18 baseline file entries
  were removed from `app/ktlint-baseline.xml`; the baseline holds 177 file entries. Emptying
  every other entry fails `ktlintCheck` on unedited files, so the handoff's earlier claim of 190
  pruned files did not match the tree and was not applied. No new exemption was added.
- Deferred by decision: `SavedCollectionsHost` (`filename`), `ConnectedApp` and
  `PostThreadViewModel` (`keyword-spacing`), `MastodonIntegrationTest` (`paren-spacing`),
  `NavigationTest` (`string-template` x4), `SettingsViewModelTest` (`function-expression-body` x2).
- 4.2 full gate: 52 Python tests pass, architecture audit exits 0 (675 findings, no
  regressions), JVM tests, lint, ktlint, debug assembly, and release assembly pass. No flakes.
- 4.2 device: debug APK installs and launches on emulator-5554 (API 37), `MainActivity`
  resumed, evidence `logs/phase42-emulator-launch.png`.

# Phase 5

- 5.1 documentation reconciliation (source verified). A scripted path check covered the wiki,
  the agent pages, and the task record. Fixed four stale links: `AccountManager` now links to
  `ui/session/`, `HttpClientPool` to `data/transport/`, and `overview.md` links
  `FeedViewModel`, `SavedPostsViewModel`, and `PhotoGridScreen` to their feature packages.
  `docs/wiki/architecture.md` now names the final shape: `MisskeySource` with its
  `Misskey*Service` collaborators (including `MisskeyThreadService`), the four shell adapters,
  `ProfileTimelinePresentation`, and the Home paging and rendering split. Each name exists in source.
  Remaining path-check hits are historical records or the user's deleted Photo Grid test.
- 5.2 final gate on the final tree: 52 Python tests pass, architecture audit exits 0 (675
  findings, no regressions), and `testDebugUnitTest` (forced rerun), lint, ktlint, debug
  assembly, and release assembly pass. 1,692 JVM tests ran in 161 suites with zero failures,
  errors, or skips. No flakes occurred. `function_audit` and `file_audit` are diagnostic only;
  their warnings (for example `SinglePostScreen`, `ShellContent`, `MastodonIntegrationTest`)
  stay as review candidates. Both audits also scan a stale agent worktree under `.claude/worktrees/`.
- 5.3 runtime (device verified, emulator-5554, API 37, 1169 x 1848): the debug APK installs and
  `MainActivity` resumes. Home shows the Home, Local, and Federated chips and the floating
  navigation. Search opens with its chips and composer. No `FATAL EXCEPTION` appears in
  logcat. Evidence: `logs/phase5-launch2/screenshot.png`, `logs/phase5-search.png`.
- Unverified, with reason: API 29 instrumentation (the `instrumentation-api29` job needs a push,
  which the user has not authorized); compact-wide and expanded layouts on this emulator, IME
  behavior, Profile chips, navigation restoration, and forced RTL (not exercised in this run);
  TalkBack, physical devices, and signing (no hardware or keys); live Misskey and Mastodon
  accounts (no test accounts).

# Files involved

- None. Phase 3 Slice 3.1 code is committed at `0bd8e7d`. This update touches records only.

# Verification

2.1 focused: `HomeFeedTest`, `HomePagingDemandTest`, `HomeClearanceTest` pass.
2.1 full gate: 51 Python tests pass, architecture audit exits 0 with zero regressions,
  1,691 JVM tests across 161 suites, lint, ktlint, debug assembly, and release assembly.
  The import-ordering fix removes the HomeFeed ktlint baseline entry with no new exemption.
  Two known cancellation flakes failed in separate runs and pass alone (see `logs/BUGS.txt`).
2.2 focused: `ProfileScreenTest`, `ProfileClearanceTest`, and `WideNavigationTest` pass.
2.2 full gate: 51 Python tests pass, architecture audit exits 0 with zero regressions,
  1,691 JVM tests across 161 suites, lint, ktlint, debug assembly, and release assembly.
  The ProfileScreen import-ordering fix removes its ktlint baseline entry with no new exemption.
2.3 focused: `CategoryChipsGeometryTest`, `WideNavigationTest`, `HomeFeedTest`,
  `NotificationsClearanceTest`, and `ProfileClearanceTest` pass.
2.3 full gate: 51 Python tests pass, architecture audit exits 0 with zero regressions,
  1,691 JVM tests across 161 suites, lint, ktlint, debug assembly, and release assembly.
  The CategoryChips import-ordering fix removes its ktlint baseline entry with no new exemption.
2.4 focused: `NotificationsScreenTest`, `NotificationsClearanceTest`,
  `CategoryChipsGeometryTest`, and `WideNavigationTest` pass.
2.4 full gate: 51 Python tests pass, architecture audit exits 0 with zero regressions,
  1,691 JVM tests across 161 suites, lint, ktlint, debug assembly, and release assembly.
  The NotificationsScreen import-ordering fix removes its ktlint baseline entry with no new exemption.
  Slice 2.4 was implemented by a targeted_fixer child session under a complete dispatch contract;
  the orchestrator reviewed the diff and ran the Python test suite.
3.1 focused: `MisskeyThreadContinuationTest` (12 tests) and `MisskeyIntegrationTest`
(49 tests) pass. `SocialSourceContractTest` is the abstract base; the Misskey integration
suite executes its contract. No failures occurred.
3.1 full gate: 51 Python tests pass, architecture audit exits 0 with zero regressions,
1,692 JVM tests across 161 suites pass with zero failures, and lint, ktlint, debug
assembly, and release assembly pass. No flakes occurred, so no rerun was required.
3.1 device: the fresh `0bd8e7d` debug APK installs and launches on emulator-5554
(API 37). `MainActivity` resumes with no application crash. Screenshot evidence is
`logs/phase31-emulator-launch.png`. Thread loading with a live account remains
unverified because no test account exists.

# Next

The hardening plan is complete. Close the unverified items above when hardware, accounts, or a push are authorized. Do not push without user direction.

# Blockers

Preserve unrelated agent/style edits, deleted test/PNGs, and untracked captures, scripts,
caches, and 4c.md.
Physical-device, API 29 instrumentation, TalkBack, signing, and live-server checks remain
unverified. Live thread loading on a real Misskey account also remains unverified.

# Last safe commit

9d2edf5 — Clean ktlint debt in touched files (Slice 4.2). The Phase 5 commit (H14) follows it.
