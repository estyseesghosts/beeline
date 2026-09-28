# Beeline 0.4.0 task state — Photo Grid connected lifetime

## Objective

Give Photo Grid an explicit connected-session lifetime without changing routes, layouts, or protocol behavior.

## Status sets

- Photo Grid status: `PhotoGridOwner` owns account and session-revision state. `PhotoGridHost` owns
  preference observation, projection registration, and connected teardown. `FeedViewModel` and
  `FeedHost` retain Home behavior only.
- OAuth status: Mastodon verification uses `/api/v1/accounts/verify_credentials`. Token exchange uses
  root `/oauth/token`. Registration uses `/api/v1/apps`. The capability probe uses `/api/v2/instance`.
  Existing callback validation and Misskey behavior remain intact.
- Focused test status: `:app:testDebugUnitTest --tests
  me.foxtails.palustris.ui.photogrid.PhotoGridOwnerTest --tests
  me.foxtails.palustris.ui.session.ConnectedEntryStoreTest` passed 26 tests. The extended focused
  Home, projection, restoration, session, and Photo Grid screen set also passed.
- Audit status: `python tools/scripts/architecture_audit.py . --baseline
  tools/architecture-baseline.json --check` exited 0 and reported 611 findings with zero baseline
  regressions. This is a count-only result from `logs/architecture-audit-2c3.txt`. Counts can vary
  across worktree states, so 611 versus 610 is not itself a regression.
- Build status: `:app:lintDebug` passed after the regression coverage update. `assembleRelease` passed.
  The full `test assembleRelease` gate is not green: it reported 16 known unrelated baseline unit-test
  failures. This repeated result appears in the 2A4, 2B, and 2C records and remains unresolved per
  `logs/BUGS.txt`. Phase 10 owns the green full gate; this slice does not claim it passed.
- Device status: the debug APK passed the welcome and dummy callback checks. Authenticated Photo Grid
  verification requires fresh user approval and remains blocked.

## Slice 2C3 files

- `app/src/main/java/me/foxtails/palustris/ui/photogrid/PhotoGridOwner.kt`
- `app/src/main/java/me/foxtails/palustris/ui/photogrid/PhotoGridHost.kt`
- `app/src/main/java/me/foxtails/palustris/ui/session/ConnectedSessionHost.kt`
- `app/src/main/java/me/foxtails/palustris/ui/ConnectedApp.kt`
- `app/src/main/java/me/foxtails/palustris/ui/feed/FeedHost.kt`
- `app/src/main/java/me/foxtails/palustris/ui/feed/FeedViewModel.kt`
- `app/src/main/java/me/foxtails/palustris/ui/photogrid/PhotoGridController.kt`
- `app/src/test/java/me/foxtails/palustris/ui/photogrid/PhotoGridOwnerTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/session/ConnectedEntryStoreTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/shell/PostProjectionCoordinatorTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/feed/FeedViewModelRequestTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/feed/FeedViewModelReactionTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/session/SessionViewModelTest.kt`
- `docs/agents/app-shell-ownership.md`

## Preservation

- OAuth diagnostics from commits `5140d64`, `80bfd25`, `47dbc36`, and `2b67bf9` remain preserved.
- Staged `docs/classic_navigation.md` and unrelated dirty files remain untouched.
- No source files were staged or committed in this session.

## Exact staging pathspec for the parent session

Stage only the 2C3 slice with:

`app/src/main/java/me/foxtails/palustris/ui/ConnectedApp.kt`
`app/src/main/java/me/foxtails/palustris/ui/feed/FeedHost.kt`
`app/src/main/java/me/foxtails/palustris/ui/feed/FeedViewModel.kt`
`app/src/main/java/me/foxtails/palustris/ui/photogrid/PhotoGridController.kt`
`app/src/main/java/me/foxtails/palustris/ui/photogrid/PhotoGridHost.kt`
`app/src/main/java/me/foxtails/palustris/ui/photogrid/PhotoGridOwner.kt`
`app/src/main/java/me/foxtails/palustris/ui/session/ConnectedSessionHost.kt`
`app/src/test/java/me/foxtails/palustris/ui/feed/FeedViewModelReactionTest.kt`
`app/src/test/java/me/foxtails/palustris/ui/feed/FeedViewModelRequestTest.kt`
`app/src/test/java/me/foxtails/palustris/ui/photogrid/PhotoGridOwnerTest.kt`
`app/src/test/java/me/foxtails/palustris/ui/session/SessionViewModelTest.kt`
`app/src/test/java/me/foxtails/palustris/ui/session/ConnectedEntryStoreTest.kt`
`app/src/test/java/me/foxtails/palustris/ui/shell/PostProjectionCoordinatorTest.kt`
`docs/agents/app-shell-ownership.md`
`docs/agents/handoff.md`
`docs/agents/tasks/beeline-0.4.0.md`

This excludes committed OAuth and welcome work, staged `docs/classic_navigation.md`, unrelated
worktree files, and audit or task logs.

## Verification limits

Live-server behavior remains unverified. API 29 physical, RTL, TalkBack, font-scale, signed, and
wide or foldable authenticated checks remain unverified.

## Records

- Implementation log: `logs/260928-photogrid-lifetime.txt`.
- Audit record: `logs/architecture-audit-2c3.txt`.
- No secrets, tokens, callback values, or response bodies belong in these records.
