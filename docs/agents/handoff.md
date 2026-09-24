# Handoff

**Status:** `docs/agents/tasks/beeline-0.4.0.md` is the active durable task state.

## Current boundary

1D3 UI is complete and reviewed. 1D3 adapter and 1D3 UI are both done. The visible footer and retry behavior is implemented.

## Next slice

The next implementation slice is 1E1: adapter residue cleanup.

## Changed ownership

Thread fields live in `DirectMessageUiState`. The ViewModel owns guarded `runThreadPage`, `continueThread` and `retryThread`, plus a separate `markReadJob` and `threadError`. The conversation footer is stateless and derives from thread state. Contract plumbing, default resources, tests, and docs are updated. Selection, start, and close reset the thread fields. Stop cancels thread and read jobs and advances selection authority, but does not reset state fields.

## Verification evidence

`DirectMessageViewModelTest` 26 passed; `DirectMessageScreenTest` 13 passed with compact and wide Compose coverage; `DirectMessageSourceTest` 50 passed; `DirectMessageRepositoryTest` 19 passed; `DirectMessageWriteAuthorityTest` 8 passed; `DirectMessageDatabaseSchemaTest` 3 passed; `MisskeyIntegrationTest` 48 passed; `MastodonIntegrationTest` 58 passed; `:app:lintDebug` passed. This records session ran no new commands and claims no new results.

## Known limits

Full `test assembleRelease` remains known red and was not run. Physical-device rendering, font-scale behavior, TalkBack order, and live-server behavior are unverified.

## Hygiene

Keep staged `docs/classic_navigation.md`, `.opencode/*`, `importantdocs/writing_style.md`, deleted PNGs, and untracked tools scripts and caches untouched and unstaged. Keep `logs/*` unstaged. Do not stage or commit during this records update.

## Last safe boundary

The last safe boundary is the commit that contains this record. The next session must get the exact last safe hash with `git log -1`. Git history is authoritative.
