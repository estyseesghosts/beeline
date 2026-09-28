# Beeline 0.4.0 task state — slice 2C1

## Objective

Move `AccountSearchState` from `ui.feed` to `ui.search`.
Preserve fields, defaults, types, state ownership, and all connected lifetimes.

## Status sets

- Source status: `AccountSearchState` now lives in `ui/search/AccountSearchState.kt`. `FeedState` imports it for the unchanged `accountSearch` field. Search imports no longer point to `ui.feed`.
- Characterization status: named suites cover stale replies, Photo Grid selection and cancellation, external projection, connected-lifetime retirement, account switching, session replacement, sign-out removal, and saved-state recreation.
- Characterization gap: no Search-specific account-removal test exists. Existing lifecycle tests cover sign-out and connected-entry retirement. The exit gate allows this move with the gap recorded for 2C2. This slice adds no production behavior.
- Test status: focused feed, restoration, Photo Grid, projection, and session suites passed. The
  wider run covered 106 tests and failed the same two Navigation draft tests named in
  `logs/BUGS.txt:13`: `closingComposerAutosavesUnsavedText` at line 820 and
  `draftsSurviveActivityRecreationAndCanBeDeleted` at line 803. This name match is recorded from
  the reports; independent verification of the wider run is not available here.
- Audit status: the audit reported 607 findings and zero regression lines. The prior 2B3 record
  reports 608 findings in `logs/architecture-audit-2b3.txt:1`. This is a count-only difference with
  no explanation. It does not establish that an audit rule was resolved.
- Lint status: not triggered by this import-only move.
- Full gate status: `test assembleRelease` timed out after 120 seconds. It reported the two
  Navigation draft failures plus DraftActions and CapabilityCache categories before timeout.
  Their relationship to the 16 failures in `logs/BUGS.txt:7` remains unattributed and unresolved.
- Device and live-server status: unverified.

## Preserved boundaries

`FeedHost` wiring, session revision, projection subscription, pager and cancellation behavior,
preferences, and saved route behavior remain unchanged. This slice does not extract Search or
Photo Grid lifetime owners. It does not change Home-only behavior, protocol behavior, layout, or
user-visible strings.

## Characterized contracts

- `FeedViewModelRequestTest` covers accepted requests, stale refresh success and failure, stop,
  cursors, and external mutations.
- `PhotoGridFeedViewModelTest` covers independent selection, paging, and late canceled results.
- `PostProjectionCoordinatorTest` covers external updates, account and revision rejection, and
  retirement.
- `ConnectedEntryStoreTest` covers recomposition, lifetime replacement, owner clearing, and
  teardown replacement.
- `ConnectedSessionContextTest` and `SessionViewModelTest` cover account switching, session
  replacement, and sign-out removal.
- `SearchPanelRestorationTest`, `NavigationTest`, and `HomeFeedTest` cover saved route and Search
  presentation restoration.

## Changed files

- `app/src/main/java/me/foxtails/palustris/ui/search/AccountSearchState.kt`
- `app/src/main/java/me/foxtails/palustris/ui/feed/FeedState.kt`
- `app/src/main/java/me/foxtails/palustris/ui/search/SearchController.kt`
- `app/src/main/java/me/foxtails/palustris/ui/search/SearchScreen.kt`
- `app/src/main/java/me/foxtails/palustris/ui/shell/SearchContract.kt`
- `app/src/test/java/me/foxtails/palustris/ui/navigation/NavigationTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/feed/HomeFeedTest.kt`
- `docs/agents/tasks/beeline-0.4.0.md`
- `docs/agents/handoff.md`

No ownership documentation path was stale. No wiki or agent ownership page changed.

## Verification

- Grep for the old feed-qualified state name: zero matches.
- Focused Gradle run passed the seven feed, restoration, Photo Grid, projection, and session test
  classes. The wider run compiled the changed code and ran 106 tests, with only the two listed
  Navigation draft failures.
- Architecture audit command: `python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check`.
  It exited 0 with 607 findings and zero regression lines.
- Audit output record: `logs/architecture-audit-2c1.txt`.
  The 607-versus-608 comparison is unexplained and count-only; zero regression lines do not prove
  that any audit rule was resolved.
- `git diff --check` for the slice pathspec passed.

## Staging pathspec

This slice remains an unstaged worktree move and import update. The index currently contains only
the unrelated `docs/classic_navigation.md` entry. If the parent commits this slice, use an explicit
pathspec for only these files:

```text
app/src/main/java/me/foxtails/palustris/ui/search/AccountSearchState.kt
app/src/main/java/me/foxtails/palustris/ui/feed/FeedState.kt
app/src/main/java/me/foxtails/palustris/ui/search/SearchController.kt
app/src/main/java/me/foxtails/palustris/ui/search/SearchScreen.kt
app/src/main/java/me/foxtails/palustris/ui/shell/SearchContract.kt
app/src/test/java/me/foxtails/palustris/ui/navigation/NavigationTest.kt
app/src/test/java/me/foxtails/palustris/ui/feed/HomeFeedTest.kt
docs/agents/tasks/beeline-0.4.0.md
docs/agents/handoff.md
```

Do not include `docs/classic_navigation.md`, unrelated staged or unstaged files, or untracked files.
The slice `git diff --check` is clean. The full worktree check includes unrelated whitespace.
Do not stage, commit, or push in this continuation. Keep ignored logs outside staging.

## Limits

The full `test assembleRelease` gate timed out after 120 seconds. Live servers, physical devices,
API 29, RTL, TalkBack, font scale, and signed release checks remain unverified.
