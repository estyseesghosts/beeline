# Beeline 0.4.0 task state — slice 2C2

## Objective

Give Search one explicit connected owner. Keep Search state, cancellation, account identity, session
revision, and post projection subscription outside `FeedViewModel`. Keep Photo Grid in that ViewModel.

## Status sets

- Source status: `SearchOwner` and `SearchHost` own Search state and actions. `FeedViewModel` retains
  Home and Photo Grid behavior and no longer forwards Search requests or post updates.
- Lifetime status: `ConnectedEntryStore` registers one stable Search key per connected generation.
  Replacement, removal, store clearing, and owner release cancel Search work. Coordinator registration
  is idempotent and unregisters on disposal or owner retirement.
- Projection status: Search accepts only matching account and session revision. Its coordinator sink
  receives external and accepted publication updates independently of Home.
- Test status: the Search owner suite passes all 8 tests. It covers cooperative cancellation, session
  replacement, account isolation, revision rejection, recreation registration, release-once teardown,
  coordinator updates, optimistic projection, and operation without Home. The focused Feed request,
  session, projection, and Search restoration suites pass 41 tests in total.
- Audit status: the audit exited 0 with 610 findings and no reported regression lines. Slice 2C1
  recorded 607 findings and zero regression lines. The count difference does not prove a fix.
- Full gate status: `test assembleRelease` reached release packaging and timed out at 120 seconds.
  It reported known baseline failures in DraftActions, CapabilityCache, Misskey thread continuation,
  and NotificationSyncOrchestrator before timeout. No test was weakened.
- Lint status: `:app:lintDebug` passed.
- Device and live-server status: unverified.

## Preserved boundaries

No protocol, layout, route, saved-state, preference, or Photo Grid behavior changed. Search does not
select behavior from host names. Search receives the connected source and post preferences from the
session composition root.

## Changed files

- `app/src/main/java/me/foxtails/palustris/ui/search/SearchOwner.kt`
- `app/src/main/java/me/foxtails/palustris/ui/search/SearchHost.kt`
- `app/src/main/java/me/foxtails/palustris/ui/search/SearchController.kt`
- `app/src/main/java/me/foxtails/palustris/ui/feed/FeedViewModel.kt`
- `app/src/main/java/me/foxtails/palustris/ui/feed/FeedState.kt`
- `app/src/main/java/me/foxtails/palustris/ui/feed/FeedHost.kt`
- `app/src/main/java/me/foxtails/palustris/ui/session/ConnectedSessionHost.kt`
- `app/src/main/java/me/foxtails/palustris/ui/shell/SearchContract.kt`
- `app/src/test/java/me/foxtails/palustris/ui/feed/FeedViewModelReactionTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/search/SearchOwnerTest.kt`
- `docs/wiki/ui-and-navigation.md`
- `docs/agents/app-shell-ownership.md`
- `docs/agents/tasks/beeline-0.4.0.md`
- `docs/agents/handoff.md`

## Staging pathspec

Do not stage or commit in this continuation. If the parent commits this slice, stage only the files
listed above. Keep the pre-existing dirty files and ignored logs outside the pathspec.

## Limits

Live servers, physical devices, API 29, RTL, TalkBack, font scale, and signed release checks remain
unverified. The current worktree also contains unrelated dirty files from before this slice.
