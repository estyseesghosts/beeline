# Beeline 0.4.0 task state — 1E1 boundary

## Objective

Execute Beeline 0.4.0 plan phases 0-2 in verified slices. This record covers the reviewed 1D3 adapter and 1D3 UI slices plus the completed 1E1 adapter residue cleanup.

## Invariants

- Keep one authoritative state owner per lifetime.
- Bind opaque cursors to account, query, route, and variant.
- Validate origins before authenticated requests.
- Keep protocol branches in adapters.
- Do not compare opaque identifiers.
- Preserve unrelated worktree changes.
- Keep one slice to one commit.

## Current boundary

1D3 adapter and 1D3 UI are complete and reviewed. 1E1 adapter residue cleanup is committed as the current commit with message "Remove unused adapter constants". The next implementation slice is 1E2 (Remove unused mapper arguments and update mapper callers/tests).

The visible footer and retry behavior from 1D3 stays unchanged. The 1E1 slice removes only proven-unused private constants from the two source facades. It changes no behavior, no protocol branch, and no shared UI or domain code.

## 1E1 change

- `data/misskey/MisskeySource.kt`: removed unused `DIRECT_PAGE_LIMIT`, `SECURE_CREDENTIAL_FAILURE_CODES`, and `MISSING_PUSH_REGISTRATION_CODES` from the facade companion. The live direct inbox limit stays in `MisskeyDirectMessageService.DIRECT_PAGE_LIMIT`, which still serves `requestBody`. Live push ownership stays in `MisskeyPushService.SECURE_CODES` and `MisskeyPushService.MISSING_CODES`, which still serve push registration reads and secure-credential mapping.
- `data/mastodon/MastodonSource.kt`: removed unused `DEFAULT_NOTIFICATION_LIMIT` from the companion and the unused file-private `DIRECT_CONVERSATION_LIMIT`. The live limits stay in `MastodonNotificationService` and `MastodonDirectMessageService`, which still serve notification and conversation requests.
- Repo-wide caller recheck before removal found references only at the removed declaration sites and at the live service constants. No test, resource, or adapter references the removed names.
- No new test was required. Removal of unreferenced private constants has no behavior path.

## Changed ownership

Ownership is unchanged by 1E1. For the record, 1D3 ownership still stands:

- `domain/DirectMessageModels.kt`: `DirectThreadResult` and `ThreadLimitation.PendingLimit`. Presentation is owned by the UI layer. The contract stays protocol-neutral.
- `domain/DirectMessageSource.kt`: `conversationThread` returns cursor plus `DirectThreadResult`.
- `data/misskey/MisskeyDirectMessageService.kt`: bounded BFS, v4 cursor, cumulative limitations.
- `data/misskey/MisskeySource.kt`: thread call propagation.
- `data/mastodon/MastodonDirectMessageService.kt` and `data/mastodon/MastodonSource.kt`: finished result, no continuation.
- `data/directmessages/DirectMessageRepository.kt`: continuation merge and preview rule.
- `ui/directmessages/DirectMessageUiState.kt`: thread fields for cursor, limitations, continuing flag, and a thread error separate from inbox and send errors.
- `ui/directmessages/DirectMessageViewModel.kt`: guarded `runThreadPage`, `continueThread` and `retryThread` entry points, separate `markReadJob` and `threadError`.
- `ui/directmessages/DirectMessageConversationScreen.kt`: stateless trailing footer derived from thread state.
- `ui/shell/DirectMessagesContract.kt`, `ui/directmessages/DirectMessagesHost.kt`, `ui/shell/AppNotificationsDestinationContent.kt`, `ui/shell/ShellDestinationContent.kt`: contract plumbing for footer callbacks.
- `app/src/main/res/values/strings.xml`: default thread loading, retry, continue, and partial-state resources.
- `docs/wiki/notifications-and-direct-messages.md` and `docs/agents/protocol-and-session-ownership.md`: contract and retry-footer notes.

## Protocol neutrality

- 1E1 keeps all protocol behavior in adapters. It removes no live service constant and moves no logic between adapters.
- The footer still derives from `DirectThreadResult` state only. No protocol branch exists in generic Compose UI or the generic ViewModel.
- Mastodon `Finished` has no extra control.
- Misskey continuation and limited states stay visible through the same footer.

## Verification

Focused gate evidence from this 1E1 session, run after the removal:

- `DirectMessageSourceTest`: 50 passed, 0 failed.
- `MisskeyIntegrationTest`: 48 passed, 0 failed.
- `MastodonIntegrationTest`: 58 passed, 0 failed.
- `:app:lintDebug`: passed.
- Full `test assembleRelease` remains known red and was not run, per slice scope.
- The Python tool suite was not run. Source changes touch no tool.

## Not verified

- Live Misskey and Sharkey thread ordering.
- Physical-device rendering, font-scale behavior, and TalkBack order.
- Physical-device, API 29, RTL, and signed-release checks.
- Live-server behavior.

## Hygiene

- `docs/classic_navigation.md` remains staged and untouched. All other unrelated changes remain unstaged or untracked: modified `.opencode/*` and `importantdocs/writing_style.md`, deleted PNGs, and untracked `.opencode/agents` helpers, tools scripts, and caches.
- The task log at `logs/260924-1E1-slice.txt` remains ignored and unstaged.
- This records update stages nothing, commits nothing, amends nothing, and pushes nothing.

## Last safe boundary

The current 1E1 commit with message "Remove unused adapter constants" is the safe boundary. The next session must get its exact hash with `git log -1`. Git history is authoritative. The next implementation slice after this boundary is 1E2.
