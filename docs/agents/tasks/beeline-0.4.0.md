# Beeline 0.4.0 task state — 1E3 boundary

## Objective

Execute Beeline 0.4.0 plan phases 0-2 in verified slices. This record covers the reviewed 1D3 adapter and 1D3 UI slices, the completed 1E1 adapter residue cleanup, the completed 1E2 mapper argument cleanup, and the 1E3 path-encoder slice.

## Invariants

- Keep one authoritative state owner per lifetime.
- Bind opaque cursors to account, query, route, and variant.
- Validate origins before authenticated requests.
- Keep protocol branches in adapters.
- Do not compare opaque identifiers.
- Preserve unrelated worktree changes.
- Keep one slice to one commit.

## Current boundary

1D3 adapter and 1D3 UI are complete and reviewed. 1E1 adapter residue cleanup is committed. 1E2 mapper argument cleanup is committed. 1E3 path-encoder cleanup is implemented and verified in the worktree and awaits commit. The next step after 1E3 is the Phase 1 closure audit. The audit confirms the 1A, 1B, 1C, 1D, and 1E gates, including the pending 1C-c review, before 2A starts.

The visible footer and retry behavior from 1D3 stays unchanged. The 1E1 slice removes only proven-unused private constants from the two source facades. The 1E2 slice removes only proven-unused origin parameters from the two self-profile mappers. The 1E3 slice replaces only the four duplicate Mastodon path encoders with one helper. All three slices change no behavior, no protocol branch, and no shared UI or domain code.

## 1E3 change

- `data/mastodon/MastodonPathEncoding.kt`: added internal `String.encodeMastodonPathSegment`. It encodes one opaque Mastodon identifier as one adapter-owned REST URL path segment. It is a stateless pure function. It converts `+` to `%20` for path segments. Query encoding remains a separate contract.
- `data/mastodon/MastodonDirectMessageService.kt`: replaced three call sites. Removed the private helper and its now-unused `URLEncoder` import.
- `data/mastodon/MastodonNotificationService.kt`: replaced two call sites. Removed the private helper.
- `data/mastodon/MastodonSource.kt`: replaced ten call sites. Removed the private helper. Kept the `URLEncoder` import because query encoders still use it.
- `data/mastodon/MastodonThreadService.kt`: replaced one call site. Removed the private helper.
- `data/mastodon/MastodonPathEncodingTest.kt`: added eight focused tests for plain IDs, slash, question mark, space as `%20`, literal plus as `%2B`, percent, hash, and empty input. Existing `MastodonIntegrationTest` covers the source path contract.
- The four `String.toJson` parsers remain unchanged. JSON parser review found no extraction because each helper is a thin constructor alias with no distinct responsibility.
- Query encoders, `HttpUrl` builders, used origin parameters, mapper behavior, Misskey code, domain code, and UI code stay unchanged.

## Changed ownership

Ownership is unchanged by 1E3. For the record, 1D3 ownership still stands:

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

- 1E3 keeps all protocol behavior in adapters. It moves no logic between adapters.
- The helper lives in `data/mastodon`. It encodes Mastodon path segments only.
- The footer still derives from `DirectThreadResult` state only. No protocol branch exists in generic Compose UI or the generic ViewModel.
- Mastodon `Finished` has no extra control.
- Misskey continuation and limited states stay visible through the same footer.

## Verification

Focused gate evidence from this 1E3 session, run after the replacement:

- `MastodonPathEncodingTest`: 8 passed, 0 failed, 0 errors, 0 skipped.
- `MastodonIntegrationTest`: 58 passed, 0 failed, 0 errors, 0 skipped.
- `MastodonSourceContractTest`: 6 passed, 0 failed, 0 errors, 0 skipped.
- `MastodonMapperTest`: 12 passed, 0 failed, 0 errors, 0 skipped.
- `ModerationServiceTest`: 13 passed, 0 failed, 0 errors, 0 skipped.
- `:app:lintDebug`: passed with BUILD SUCCESSFUL.
- Full `test assembleRelease` remains known red and was not run, per slice scope.
- The Python tool suite was not run. Source changes touch no tool.
- The tool shell offers no `GRADLE_OPTS` or stdin control. No `GRADLE_OPTS` was set. One PowerShell file probe was rejected. Verification continued through Gradle output and test XML reads.

## Not verified

- Live Misskey and Sharkey thread ordering.
- Physical-device rendering, font-scale behavior, and TalkBack order.
- Physical-device, API 29, RTL, and signed-release checks.
- Live-server behavior.

## Hygiene

- `docs/classic_navigation.md` remains staged and untouched. All other unrelated changes remain unstaged or untracked: modified `.opencode/*` and `importantdocs/writing_style.md`, deleted PNGs, and untracked `.opencode/agents` helpers, tools scripts, and caches.
- The task logs at `logs/260924-1E1-slice.txt`, `logs/260924-1E2-slice.txt`, and `logs/260924-1E3-slice.txt` remain ignored and unstaged.
- `docs/beeline_0.4.0.md` stays untouched.
- This records update stages nothing, commits nothing, amends nothing, and pushes nothing.

## Last safe boundary

The committed HEAD de5d58d with message "Remove unused mapper parameters" is the last safe committed boundary. The verified 1E3 work is implemented in the worktree and uncommitted. Git history is authoritative. The next step after this boundary is the Phase 1 closure audit.
