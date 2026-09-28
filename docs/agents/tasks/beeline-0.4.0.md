# Beeline 0.4.0 task state — 2E shell characterization

The parent session must stage only the 2E tests and documentation pathspec. This is intentional.
This 2E slice remains uncommitted. It adds characterization assertions without extraction.

## Objective

Characterize shell ownership before any future callback-surface extraction.

## Status

- `PostProjectionCoordinatorTest` rejects a publication from a foreign account.
- `ShellCharacterizationTest` covers compact and large Search routes.
- `ProfileViewModelTest` covers editor draft continuity across a pager tab change.
- Existing assertions cover stale revisions, retired coordinators, account switching, and pager
  target isolation. The new tests preserve those owners without re-hoisting state.
- No production source changed. No callback bundle was extracted.

## Inspected path inventory

The shell path was inspected for ownership and callback flow. The session tests were inspected for
authority lifetime and account replacement. The profile pager and editor tests were inspected for
continuity and stale-target behavior.

Inspected implementation paths:

- `app/src/main/java/me/foxtails/palustris/ui/shell/ShellContent.kt`
- `app/src/main/java/me/foxtails/palustris/ui/shell/ShellDestinationContent.kt`
- `app/src/main/java/me/foxtails/palustris/ui/shell/DestinationCallbacks.kt`
- `app/src/main/java/me/foxtails/palustris/ui/session/ConnectedSessionHost.kt`
- `app/src/main/java/me/foxtails/palustris/ui/feed/HomeFeed.kt`
- `app/src/main/java/me/foxtails/palustris/ui/profile/ProfileTimelineList.kt`
- `app/src/main/java/me/foxtails/palustris/ui/profile/ProfileViewModel.kt`
- Search, Photo Grid, and direct-message contract and host paths

Inspected test paths:

- `ShellCharacterizationTest.kt`
- `HomeFeedTest.kt`
- `ProfileScreenTest.kt`
- `ProfileViewModelTest.kt`
- `ProfileTimelinePagerTest.kt`
- `PostProjectionCoordinatorTest.kt`
- `ConnectedSessionContextTest.kt`
- `ConnectedEntryStoreTest.kt`

## Excluded worktree paths

- `.opencode/**` changes and untracked agent files are pre-existing and unrelated.
- Deleted `currentbehaviour.png` and `intendedbehaviour.png`, plus untracked PNG captures, are
  pre-existing and unrelated.
- Staged `docs/classic_navigation.md` is pre-existing and unrelated.
- Deleted `app/src/test/java/me/foxtails/palustris/ui/photogrid/PhotoGridFeedViewModelTest.kt` is
  the committed 2C deletion from `92d15a8` and remains excluded.
- Helpers, caches, and other logs remain outside 2E.

Git pointers for these classifications are `git status --short`, `git diff -- .opencode`,
`git diff --cached -- docs/classic_navigation.md`, and commit `92d15a8`.

Do not stage or commit this session. The parent session must preserve the complete path inventory
above and exclude the unrelated paths.

Keep staged `docs/classic_navigation.md` separate. Do not stage welcome, OAuth, 2C, committed
2D1, committed 2D2, `.opencode`, images, helpers, caches, or logs.

No files were staged or committed in this session. The parent should use this pathspec:
`app/src/test/java/me/foxtails/palustris/ui/shell/ShellCharacterizationTest.kt`
`app/src/test/java/me/foxtails/palustris/ui/shell/PostProjectionCoordinatorTest.kt`
`app/src/test/java/me/foxtails/palustris/ui/profile/ProfileViewModelTest.kt`
`docs/agents/app-shell-ownership.md` `docs/wiki/ui-and-navigation.md`
`docs/agents/tasks/beeline-0.4.0.md` `docs/agents/handoff.md`.

## Verification

- Focused ShellCharacterizationTest passed, including 4 tests.
- Focused HomeFeedTest, ProfileScreenTest, ProfileViewModelTest, ProfileTimelinePagerTest,
  PostProjectionCoordinatorTest, ConnectedSessionContextTest, and ConnectedEntryStoreTest passed.
- Four new characterization tests were added.
- The count-only audit command remains:

`python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check`

The command exited 0 with 612 findings and zero regressions against the baseline.
The prior count-only result was 611. The requested 614 count does not match this worktree result.

- The audit exited 0 with 612 findings and zero regressions.
- The full `test assembleRelease` gate timed out after 120 seconds. Release assembly reached
  `assembleRelease`, but the test task reported existing unrelated baseline failures first.
  Reported failures include DraftActions, Mastodon artwork, CapabilityCache, Misskey continuation,
  and NotificationSyncOrchestrator tests.
Focused device, live-server, API 29, RTL, TalkBack, font-scale, signed, compact, and wide or
foldable checks remain unverified.
- Lint was not required because production source did not change.

## Preservation

Welcome, OAuth, 2C, committed 2D1, and committed 2D2 work remain untouched by this follow-up.
The staged classic navigation file remains untouched. Unrelated `.opencode` files, images, helpers,
caches, and logs remain excluded. The 2E production boundary remains untouched.
