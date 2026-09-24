# Handoff

**Status:** `docs/agents/tasks/beeline-0.4.0.md` is the active durable task state.

## Current boundary

1D3 UI is complete and reviewed. 1E1 adapter residue cleanup is committed as d5da867 with message "Remove unused adapter constants". 1E2 mapper argument cleanup is implemented and verified in the worktree and uncommitted. The slice removes the unused `origin` parameters from `MastodonMapper.editableProfile` and `MastodonMapper.legacyEditableProfile` and updates every current caller in `MastodonSelfProfileService` and `MastodonMapperTest`. It changes no behavior.

## Next slice

The 1E2 slice is verified and ready for commit through `git_handler`. After the 1E2 commit, the next implementation slice is 1E3 (Review the remaining Phase 1 Mastodon path encoders and JSON parsers in their call sites, per `docs/beeline_0.4.0.md` section 1E).

## Changed ownership

Ownership is unchanged by 1E2. Thread fields live in `DirectMessageUiState`. The ViewModel owns guarded `runThreadPage`, `continueThread` and `retryThread`, plus a separate `markReadJob` and `threadError`. The conversation footer is stateless and derives from thread state. The 1E2 slice keeps the service `origin` field and all origin validation and request behavior unchanged. Used mapper `origin` parameters stay untouched.

## Verification evidence

`MastodonMapperTest` 12 passed; `MastodonIntegrationTest` 58 passed; `MastodonSourceContractTest` 6 passed; zero failures, errors, or skips in all three suites; `:app:lintDebug` passed with BUILD SUCCESSFUL and zero Error-severity issues. Full `test assembleRelease` remains known red and was not run, per slice scope. The Python tool suite was not run because source changes touch no tool.

## Known limits

Full `test assembleRelease` remains known red and was not run. Physical-device rendering, font-scale behavior, TalkBack order, and live-server behavior are unverified.

## Hygiene

`docs/classic_navigation.md` remains staged and untouched. All other unrelated changes remain unstaged or untracked: modified `.opencode/*` and `importantdocs/writing_style.md`, deleted PNGs, and untracked `.opencode/agents` helpers, tools scripts, and caches. `docs/beeline_0.4.0.md` stays untouched. Keep `logs/*` unstaged. Do not stage, commit, amend, or push.

## Last safe boundary

The committed 1E1 commit d5da867 with message "Remove unused adapter constants" is the last safe committed boundary. The verified 1E2 work is uncommitted in the worktree. Git history is authoritative.
