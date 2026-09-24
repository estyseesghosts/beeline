# Handoff

**Status:** `docs/agents/tasks/beeline-0.4.0.md` is the active durable task state.

## Current boundary

1D3 UI is complete and reviewed. 1E1 adapter residue cleanup is committed as the current commit with message "Remove unused adapter constants". The slice removes five proven-unused private constants from `MisskeySource` and `MastodonSource` and changes no behavior.

## Next slice

The next implementation slice is 1E2 (Remove unused mapper arguments and update mapper callers/tests).

## Changed ownership

Ownership is unchanged by 1E1. Thread fields live in `DirectMessageUiState`. The ViewModel owns guarded `runThreadPage`, `continueThread` and `retryThread`, plus a separate `markReadJob` and `threadError`. The conversation footer is stateless and derives from thread state. Live service constants stay with their services: `MisskeyDirectMessageService.DIRECT_PAGE_LIMIT`, `MastodonNotificationService.DEFAULT_NOTIFICATION_LIMIT`, and `MastodonDirectMessageService.DIRECT_CONVERSATION_LIMIT`. Live push ownership stays in `MisskeyPushService`: `SECURE_CODES` and `MISSING_CODES` still serve push registration reads and secure-credential mapping. The removed facade sets were residue with no callers.

## Verification evidence

`DirectMessageSourceTest` 50 passed; `MisskeyIntegrationTest` 48 passed; `MastodonIntegrationTest` 58 passed; `:app:lintDebug` passed. Full `test assembleRelease` remains known red and was not run, per slice scope. The Python tool suite was not run because source changes touch no tool.

## Known limits

Full `test assembleRelease` remains known red and was not run. Physical-device rendering, font-scale behavior, TalkBack order, and live-server behavior are unverified.

## Hygiene

`docs/classic_navigation.md` remains staged and untouched. All other unrelated changes remain unstaged or untracked: modified `.opencode/*` and `importantdocs/writing_style.md`, deleted PNGs, and untracked `.opencode/agents` helpers, tools scripts, and caches. Keep `logs/*` unstaged. Do not stage, commit, amend, or push.

## Last safe boundary

The current 1E1 commit with message "Remove unused adapter constants" is the safe boundary. The next session must get its exact hash with `git log -1`. Git history is authoritative.
