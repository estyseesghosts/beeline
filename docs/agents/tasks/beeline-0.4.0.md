# Beeline 0.4.0 task state — 1D3 UI boundary

## Objective

Execute Beeline 0.4.0 plan phases 0-2 in verified slices. This record covers the reviewed 1D3 adapter and 1D3 UI slices.

## Invariants

- Keep one authoritative state owner per lifetime.
- Bind opaque cursors to account, query, route, and variant.
- Validate origins before authenticated requests.
- Keep protocol branches in adapters.
- Do not compare opaque identifiers.
- Preserve unrelated worktree changes.
- Keep one slice to one commit.

## Current boundary

1D3 adapter and 1D3 UI are complete and reviewed. The next implementation slice is 1E1: adapter residue cleanup.

The visible footer and retry behavior is implemented. The conversation screen shows a trailing footer derived from thread state: spinner while continuing, Continue when a cursor exists, Retry on thread error with fallback to a fresh load, and a static partial notice when limitations exist without a cursor. A finished Mastodon result shows no extra control.

## Final 1D3 adapter contract and bounds

- Misskey thread acquisition is reply-rooted bounded breadth-first descent.
- Fresh calls run at most 20 ancestor reads plus at most 3 `notes/children` requests.
- Depth limit is 10. Accepted descendant cap is 200. Pending frontier cap is 200.
- Total chain budget is 40 authenticated thread requests, including root and ancestor reads.
- The thread cursor is version 4, variant `misskey-direct-thread-v2`. It carries accepted descendant IDs, cumulative limitations, loaded count, pending work, and chain request count.
- Decode validates exact keys, value types, version, identity binding, loaded consistency, and limitation bounds before any request. Bad cursors reject as `Unsupported("direct.thread.continuation")` with zero requests.
- Limitations accumulate across calls: uncertain truncation, node limit, depth limit, pending-frontier limit, ancestor limit, unavailable parent, and request limit.
- Failed requests throw normalized errors. They never become limitations. Cancellation and `ResponseLimitExceeded` propagate.
- Mastodon returns a finished result with no continuation.
- Live Misskey and Sharkey child ordering is unverified.

## Changed ownership

- `domain/DirectMessageModels.kt`: `DirectThreadResult` and `ThreadLimitation.PendingLimit`. KDoc no longer says presentation remains planned. Presentation is owned by the UI layer. The contract stays protocol-neutral.
- `domain/DirectMessageSource.kt`: `conversationThread` returns cursor plus `DirectThreadResult`.
- `data/misskey/MisskeyDirectMessageService.kt`: bounded BFS, v4 cursor, cumulative limitations.
- `data/misskey/MisskeySource.kt`: thread call propagation.
- `data/mastodon/MastodonDirectMessageService.kt` and `data/mastodon/MastodonSource.kt`: finished result, no continuation.
- `data/directmessages/DirectMessageRepository.kt`: continuation merge and preview rule.
- `ui/directmessages/DirectMessageUiState.kt`: thread fields for cursor, limitations, continuing flag, and a thread error separate from inbox and send errors.
- `ui/directmessages/DirectMessageViewModel.kt`: guarded `runThreadPage`, `continueThread` and `retryThread` entry points, separate `markReadJob` and `threadError`. Selection, start, and close reset the thread fields. Stop cancels thread and read jobs and advances selection authority, but does not reset state fields. Read acknowledgement is best effort and never writes thread state.
- `ui/directmessages/DirectMessageConversationScreen.kt`: stateless trailing footer derived from thread state.
- `ui/shell/DirectMessagesContract.kt`, `ui/directmessages/DirectMessagesHost.kt`, `ui/shell/AppNotificationsDestinationContent.kt`, `ui/shell/ShellDestinationContent.kt`: contract plumbing for footer callbacks.
- `app/src/main/res/values/strings.xml`: default thread loading, retry, continue, and partial-state resources.
- `DirectMessageViewModelTest`, `DirectMessageScreenTest`: focused UI coverage.
- `DirectMessageSourceTest`, `DirectMessageRepositoryTest`, `DirectMessageWriteAuthorityTest`, `DirectMessageDatabaseSchemaTest`: focused data coverage.
- `docs/wiki/notifications-and-direct-messages.md` and `docs/agents/protocol-and-session-ownership.md`: contract and retry-footer notes.

## Protocol neutrality

- The footer derives from `DirectThreadResult` state only. No protocol branch exists in generic Compose UI or the generic ViewModel.
- Mastodon `Finished` has no extra control.
- Misskey continuation and limited states are visible through the same footer.

## Verification

Focused gate evidence from the implementation session:

- `DirectMessageViewModelTest`: 26 passed.
- `DirectMessageScreenTest`: 13 passed, covering compact and wide Compose layouts.
- `DirectMessageSourceTest`: 50 passed.
- `DirectMessageRepositoryTest`: 19 passed.
- `DirectMessageWriteAuthorityTest`: 8 passed.
- `DirectMessageDatabaseSchemaTest`: 3 passed.
- `MisskeyIntegrationTest`: 48 passed.
- `MastodonIntegrationTest`: 58 passed.
- `:app:lintDebug`: passed.
- Full `test assembleRelease` remains known red and was not run.

## Not verified

- Live Misskey and Sharkey thread ordering.
- Physical-device rendering, font-scale behavior, and TalkBack order.
- Physical-device, API 29, RTL, and signed-release checks.
- Live-server behavior.

## Hygiene

- Staged `docs/classic_navigation.md`, `.opencode/*`, `importantdocs/writing_style.md`, deleted PNGs, and untracked tools scripts and caches remain untouched and unstaged.
- The task log at `logs/260924-1D3-ui-slice.txt` remains ignored and unstaged.
- This records session modifies docs and the ignored task log only. It changes no source and no tests. It stages nothing and commits nothing.

## Last safe boundary

The last safe boundary is the commit that contains this record. `git log -1` at the time of writing reports `a4c84bcb237c795c67a16e5f2b9a96e46f8b0bc3` (`feat(dm): implement 1D3 adapter slice with focused verification`). These record edits are uncommitted, so Git history is authoritative. The next session must get the exact last safe hash with `git log -1`. The next implementation slice is 1E1.
