# Handoff

**Status:** Slice 2C3 is implemented in the worktree and remains uncommitted.

## Current position

Photo Grid now has an explicit connected-session owner. `PhotoGridHost` observes account-scoped
preferences, registers an independent projection sink, and releases through `ConnectedEntryStore`.
`ConnectedSessionHost` wires Photo Grid beside Search. Home retains its own feed mutations and
publication path.

## Durable task state

Use `docs/agents/tasks/beeline-0.4.0.md`.

## Verification

- Focused Photo Grid, store, Home, session, projection, restoration, and shell tests passed.
- The Photo Grid, store, and projection focused run passed 26 tests.
- `:app:lintDebug` passed.
- The architecture audit command `python tools/scripts/architecture_audit.py . --baseline
  tools/architecture-baseline.json --check` exited 0 and reported 611 findings with zero baseline
  regressions. This is a count-only result. Counts can vary across worktree states, so 611 versus
  610 is not itself a regression. The audit record is `logs/architecture-audit-2c3.txt`.
- `assembleRelease` passed.
- The full `test assembleRelease` gate is not green. It reported 16 known unrelated baseline unit-test
  failures. The 2A4, 2B, and 2C records repeat this result, which remains unresolved per
  `logs/BUGS.txt`. Phase 10 owns the green full gate; this slice does not claim it passed.
- Authenticated Photo Grid device verification remains blocked pending fresh user approval.

## Modified slice files

- `app/src/main/java/me/foxtails/palustris/ui/ConnectedApp.kt`
- `app/src/main/java/me/foxtails/palustris/ui/feed/FeedHost.kt`
- `app/src/main/java/me/foxtails/palustris/ui/feed/FeedViewModel.kt`
- `app/src/main/java/me/foxtails/palustris/ui/photogrid/PhotoGridController.kt`
- `app/src/main/java/me/foxtails/palustris/ui/photogrid/PhotoGridHost.kt`
- `app/src/main/java/me/foxtails/palustris/ui/photogrid/PhotoGridOwner.kt`
- `app/src/main/java/me/foxtails/palustris/ui/session/ConnectedSessionHost.kt`
- `app/src/test/java/me/foxtails/palustris/ui/feed/FeedViewModelReactionTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/feed/FeedViewModelRequestTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/photogrid/PhotoGridOwnerTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/session/ConnectedEntryStoreTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/session/SessionViewModelTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/shell/PostProjectionCoordinatorTest.kt`
- `docs/agents/app-shell-ownership.md`

OAuth, welcome, and staged navigation work remains preserved. No source files were staged or committed.

## Focused regression evidence

- `:app:testDebugUnitTest --tests me.foxtails.palustris.ui.photogrid.PhotoGridOwnerTest --tests
  me.foxtails.palustris.ui.session.ConnectedEntryStoreTest` passed after the Photo Grid coverage
  extension.
- Coverage now includes refresh invalidation and late results, account preference separation,
  external and optimistic row preservation, unsupported or denied capability handling, temporary
  errors, sign-in-required errors, store retirement, and a fresh owner after session replacement.

## Next slice

Review the 2C3 diff. Then use the exact path list in the task-state file to stage this slice in the
parent session. That list includes
`app/src/main/java/me/foxtails/palustris/ui/photogrid/PhotoGridController.kt`.

## Known limits

Live-server, API 29 physical, RTL, TalkBack, font-scale, signed, and wide or foldable authenticated
checks remain unverified.
