# Handoff

**Status:** `docs/agents/tasks/beeline-0.4.0.md` is the active durable task state.

## Current boundary

1D3 UI is complete and reviewed. 1E1 adapter residue cleanup, 1E2 mapper argument cleanup, and 1E3 path-encoder cleanup are committed. HEAD is 4bae97d. The 1C-c review finding has a test-only repair in the worktree, uncommitted. The repair adds an image attachment to the parity-test parent fixture so the PhotoGrid half exercises the detail branch, asserts the photo pager is displayed, and keeps the muted-word reveal and no-leak checks in both halves. Fresh review of the 1C-c repair is pending verification.

## Next slice

The next step is the fresh review of the 1C-c repair. After the repair passes review, the Phase 1 closure audit confirms the 1A, 1B, 1C, 1D, and 1E gates before 2A starts. 1C-c is no longer pending; it has a repair and fresh review pending verification.

## Changed ownership

Ownership is unchanged by the 1C-c repair. The repair touches one test fixture and its assertions only. Production ownership still stands: thread fields live in `DirectMessageUiState`. The ViewModel owns guarded `runThreadPage`, `continueThread` and `retryThread`, plus a separate `markReadJob` and `threadError`. The conversation footer is stateless and derives from thread state. The 1E3 slice keeps all used `origin` parameters, all origin validation, and all request behavior unchanged. JSON parser review found no extraction because each `String.toJson` helper is a thin constructor alias with no distinct responsibility.

## Verification evidence

`SinglePostScreenTest` 40 passed; `ContentWarningPolicyTest` 3 passed; zero failures, errors, or skips in both suites; `:app:lintDebug` passed with BUILD SUCCESSFUL. Full `test assembleRelease` remains known red and was not run, per scope. The Python tool suite was not run because source changes touch no tool. The tool shell offers no `GRADLE_OPTS` or stdin control. No `GRADLE_OPTS` was set. One shell file probe was rejected. Verification continued through Gradle output and test XML reads.

## Known limits

Full `test assembleRelease` remains known red and was not run. Physical-device rendering, font-scale behavior, TalkBack order, and live-server behavior are unverified.

## Hygiene

`docs/classic_navigation.md` remains staged and untouched. All other unrelated changes remain unstaged or untracked: modified `.opencode/*` and `importantdocs/writing_style.md`, deleted PNGs, and untracked `.opencode/agents` helpers, tools scripts, and caches. `docs/beeline_0.4.0.md` stays untouched. Keep `logs/*` unstaged. Do not stage, commit, amend, or push.

## Last safe boundary

The committed HEAD 4bae97d with message "Extract Mastodon path encoding" is the last safe committed boundary. The 1C-c test repair in `SinglePostScreenTest.kt` is uncommitted in the worktree. Git history is authoritative.
