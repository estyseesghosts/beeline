# Beeline 0.4.0 task state — 2D3 artwork contracts

The parent session must stage both record files as whole files. This is intentional.
This 2D3 follow-up remains uncommitted. It includes production artwork wiring and contract tests.

## Objective

Close the 2D3 artwork contract gaps with tests and current records.

## Status

- `SocialSourceContractTest` checks the Heart default for every adapter contract.
- `PostRowFavouriteArtworkTest` checks `myReaction` and `selectedReactions` without `favourited`.
- The test preserves distinct Favorite and React action availability.
- Focused artwork and Misskey contract tests passed. The selected task set contains 10 tests.
- `lintDebug` passed.
- Production artwork policy now flows from each source into feed and post presentation.

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

The profile contract path is included because it verifies both adapter artwork styles.

## Excluded worktree paths

- `.opencode/**` changes and untracked agent files are pre-existing and unrelated.
- Deleted `currentbehaviour.png` and `intendedbehaviour.png`, plus untracked PNG captures, are
  pre-existing and unrelated.
- Staged `docs/classic_navigation.md` is pre-existing and unrelated.
- Deleted `app/src/test/java/me/foxtails/palustris/ui/photogrid/PhotoGridFeedViewModelTest.kt` is
  the committed 2C deletion from `92d15a8` and remains excluded.
- Helpers, caches, and other logs remain outside 2D3.

Git pointers for these classifications are `git status --short`, `git diff -- .opencode`,
`git diff --cached -- docs/classic_navigation.md`, and commit `92d15a8`.

Do not stage or commit this session. The parent session must preserve the complete path inventory
above and exclude the unrelated paths.

Keep staged `docs/classic_navigation.md` separate. Do not stage welcome, OAuth, 2C, committed
2D1, committed 2D2, 2E, `.opencode`, images, helpers, caches, or logs.

No files were staged or committed in this session.

## Verification

The count-only audit command was:

`python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check`

The command exited 0 with 612 findings and zero regressions against the baseline.
The prior count-only result was 611. The requested 614 count does not match this worktree result.

The full `test assembleRelease` gate remains unresolved because known baseline test failures or a
timeout prevent a clean result. Release compilation and R8 reached completion before the timeout.
Focused device, live-server, API 29, RTL, TalkBack, font-scale, signed, compact, and wide or
foldable checks remain unverified.
The reviewer could not rerun the focused tests or lint in this review environment. The records
retain the implementer-reported focused-test and `lintDebug` results.

## Preservation

Welcome, OAuth, 2C, committed 2D1, and committed 2D2 work remain untouched by this follow-up.
The staged classic navigation file remains untouched. Unrelated `.opencode` files, images, helpers,
caches, and logs remain excluded. 2E remains untouched.
