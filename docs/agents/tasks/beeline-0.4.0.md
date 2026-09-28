# Beeline 0.4.0 task state — Post thread preferences injection

## Record rewrite rule

The parent session must stage both task records as whole files. This is intentional.
AGENTS requires this task-state file to be rewritten at each slice boundary. AGENTS requires the
handoff to be rewritten after each slice. The 2D1 rewrite therefore supersedes the earlier 2C3
record in these files. It does not mix 2C3 implementation with 2D1. Git preserves the prior
history, including `92d15a8`, `48ddc1d`, `5140d64`, `80bfd25`, `47dbc36`, and `2b67bf9`.

## Objective

Require `PostPreferencesRepository` in `PostThreadViewModel` so the thread uses the DI-owned
account preference repository.

## Status sets

- OAuth status: committed Mastodon verification, token exchange, registration, and callback
  diagnostics remain preserved.
- 2C status: committed connected-session ownership work remains preserved. Staged classic navigation
  remains outside this slice.
- 2D1 status: `PostThreadViewModel` requires `PostPreferencesRepository`. `ThreadHost` keeps the
  assisted factory contract. `AppModule` already provides the persisted repository.
- Test status: every direct test constructor supplies one per-test `InMemoryPostPreferencesRepository`.
  Regression coverage proves preference refresh and thread replacement behavior.
- Audit status: the required architecture audit reported 611 findings and zero baseline regressions.
- Build status: the focused thread test passed. `assembleRelease` passed. The full `test assembleRelease`
  gate remains red with 16 known baseline failures recorded in `logs/BUGS.txt`.
- Device status: no device test ran for this dependency-only slice.

## Slice 2D1 files

- `app/src/main/java/me/foxtails/palustris/ui/thread/PostThreadViewModel.kt`
- `app/src/test/java/me/foxtails/palustris/ui/thread/PostThreadViewModelTest.kt`
- `docs/agents/tasks/beeline-0.4.0.md`
- `docs/agents/handoff.md`

## Preservation

- Staged `docs/classic_navigation.md` remains untouched.
- Committed welcome, OAuth, and 2C work remains untouched.
- Unrelated `.opencode` files, images, helpers, caches, and logs remain untouched.
- 2D2, 2D3, and 2E work remains untouched.
- Unrelated worktree changes remain untouched.
- No files were staged or committed in this session.

## Exact staging pathspec for the parent session

Stage only this slice with:

`app/src/main/java/me/foxtails/palustris/ui/thread/PostThreadViewModel.kt`

`app/src/test/java/me/foxtails/palustris/ui/thread/PostThreadViewModelTest.kt`

`docs/agents/tasks/beeline-0.4.0.md`

`docs/agents/handoff.md`

These four paths are the exact 2D1 code, test, and record paths.
Do not stage the ignored implementation log or unrelated files.

## Verification limits

Live-server behavior remains unverified. Thread preference rendering remains unverified on a device.
API 29 physical, RTL, TalkBack, font-scale, signed, and wide or foldable checks remain unverified.

## Records

- Ignored implementation log: `logs/260928-post-thread-preferences.txt`.
- The architecture audit count was 611 with zero baseline regressions.
- No secrets, tokens, callback values, or response bodies belong in these records.
