# Handoff

**Status:** 2D3 artwork wiring, contract tests, and records are implemented in the worktree and remain uncommitted.

The parent session must stage both record files as whole files. This is intentional.
The task-state file is `docs/agents/tasks/beeline-0.4.0.md`.

## Current position

The SocialSource contract test now checks the default Heart artwork style.
The artwork test covers reaction-only selection through `myReaction` and `selectedReactions`.
The test preserves distinct Favorite and React action availability.
Production artwork policy now flows from each source into feed and post presentation.

## Verification

- Focused artwork and Misskey contract tests passed. The selected task set contains 10 tests.
- `lintDebug` passed.
- The audit command was `python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check`.
- The audit exited 0 with 612 findings and zero regressions.
- The earlier count was 611. The requested 614 count does not match this worktree result.
- The full gate remains unresolved because of the known timeout or baseline failure state.

## Complete 2D3 path inventory

- `app/src/main/java/me/foxtails/palustris/domain/FavouriteArtworkStyle.kt`
- `app/src/main/java/me/foxtails/palustris/domain/SocialSource.kt`
- `app/src/main/java/me/foxtails/palustris/data/misskey/MisskeySource.kt`
- `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonSource.kt`
- `app/src/main/java/me/foxtails/palustris/ui/feed/FeedHost.kt`
- `app/src/main/java/me/foxtails/palustris/ui/feed/FeedState.kt`
- `app/src/main/java/me/foxtails/palustris/ui/feed/FeedViewModel.kt`
- `app/src/main/java/me/foxtails/palustris/ui/feed/HomeFeed.kt`
- `app/src/main/java/me/foxtails/palustris/ui/shell/HomeContract.kt`
- `app/src/main/java/me/foxtails/palustris/ui/posts/PostRow.kt`
- `app/src/main/java/me/foxtails/palustris/ui/posts/PostRowCallSurface.kt`
- `app/src/main/java/me/foxtails/palustris/ui/posts/PostInteractionPresentation.kt`
- `app/src/test/java/me/foxtails/palustris/ProfileSourceContractTest.kt`
- `app/src/test/java/me/foxtails/palustris/SocialSourceContractTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/posts/PostRowFavouriteArtworkTest.kt`
- `docs/agents/tasks/beeline-0.4.0.md`
- `docs/agents/handoff.md`

## Exclusions and Git pointers

- `.opencode/**` changes and untracked agent files are pre-existing and unrelated.
- Deleted `currentbehaviour.png` and `intendedbehaviour.png`, plus untracked PNG captures, are
  pre-existing and unrelated.
- Staged `docs/classic_navigation.md` is pre-existing and unrelated.
- Deleted `app/src/test/java/me/foxtails/palustris/ui/photogrid/PhotoGridFeedViewModelTest.kt` is
  the committed 2C deletion from `92d15a8` and remains excluded.
- Helpers, caches, and other logs remain outside 2D3.

Use `git status --short` and `git diff -- .opencode` for worktree pointers. Use
`git diff --cached -- docs/classic_navigation.md` for the staged classic file.
Do not stage or commit this session.

No files were staged or committed in this session.

## Next slice

Preserve the complete path inventory and exclusions in the task state. Do not stage or commit this session.

## Known limits

The full `test assembleRelease` gate remains unresolved because known baseline test failures or a
timeout prevent a clean result. Release compilation and R8 reached completion before timeout.
Live-server, device, API 29, RTL, TalkBack, font-scale, signed, compact, and wide or foldable
checks remain unverified.
The reviewer could not rerun focused tests or lint. The records retain the implementer-reported
focused-test and `lintDebug` results.
The requested 614 audit count does not match this worktree, which reports 612 with exit 0 and zero
regressions. The earlier count was 611.
