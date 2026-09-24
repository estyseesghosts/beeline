# Beeline 0.4.0 task state — 1E2 boundary

## Objective

Execute Beeline 0.4.0 plan phases 0-2 in verified slices. This record covers the reviewed 1D3 adapter and 1D3 UI slices, the completed 1E1 adapter residue cleanup, and the 1E2 mapper argument cleanup.

## Invariants

- Keep one authoritative state owner per lifetime.
- Bind opaque cursors to account, query, route, and variant.
- Validate origins before authenticated requests.
- Keep protocol branches in adapters.
- Do not compare opaque identifiers.
- Preserve unrelated worktree changes.
- Keep one slice to one commit.

## Current boundary

1D3 adapter and 1D3 UI are complete and reviewed. 1E1 adapter residue cleanup is committed as commit d5da867 with message "Remove unused adapter constants". 1E2 mapper argument cleanup is implemented and verified in the worktree and awaits commit. The next implementation slice after 1E2 is 1E3 (Review the remaining Phase 1 Mastodon path encoders and JSON parsers).

The visible footer and retry behavior from 1D3 stays unchanged. The 1E1 slice removes only proven-unused private constants from the two source facades. The 1E2 slice removes only the proven-unused origin parameters from the two self-profile mappers. Both slices change no behavior, no protocol branch, and no shared UI or domain code.

## 1E2 change

- `data/mastodon/MastodonMapper.kt`: removed the unused `origin` parameter from `editableProfile(json)` and `legacyEditableProfile(json)`. Both bodies never read `origin`. Output is unchanged.
- `data/mastodon/MastodonSelfProfileService.kt`: updated the four mapper call sites in `load` and `update`. The service keeps its own `origin` field. Origin validation, account matching, and request behavior stay unchanged.
- `data/mastodon/MastodonMapperTest.kt`: updated the four mapper call sites in the editable-profile tests. Assertions stay unchanged.
- Repo-wide caller recheck found mapper callers only in `MastodonSelfProfileService` and `MastodonMapperTest`. `ProfileViewModelTest` defines a local helper with a similar name. It does not call the mapper. It stays untouched.
- All other used `origin` parameters stay untouched. Query encoders stay untouched. Misskey code stays untouched. Shared domain and UI code stay untouched. No new abstraction was added.

## Changed ownership

Ownership is unchanged by 1E2. For the record, 1D3 ownership still stands:

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

- 1E2 keeps all protocol behavior in adapters. It moves no logic between adapters.
- The footer still derives from `DirectThreadResult` state only. No protocol branch exists in generic Compose UI or the generic ViewModel.
- Mastodon `Finished` has no extra control.
- Misskey continuation and limited states stay visible through the same footer.

## Verification

Focused gate evidence from this 1E2 session, run after the removal:

- `MastodonMapperTest`: 12 passed, 0 failed, 0 errors, 0 skipped.
- `MastodonIntegrationTest`: 58 passed, 0 failed, 0 errors, 0 skipped.
- `MastodonSourceContractTest`: 6 passed, 0 failed, 0 errors, 0 skipped.
- `:app:lintDebug`: passed with BUILD SUCCESSFUL and zero Error-severity issues.
- Full `test assembleRelease` remains known red and was not run, per slice scope.
- The Python tool suite was not run. Source changes touch no tool.

## Not verified

- Live Misskey and Sharkey thread ordering.
- Physical-device rendering, font-scale behavior, and TalkBack order.
- Physical-device, API 29, RTL, and signed-release checks.
- Live-server behavior.

## Hygiene

- `docs/classic_navigation.md` remains staged and untouched. All other unrelated changes remain unstaged or untracked: modified `.opencode/*` and `importantdocs/writing_style.md`, deleted PNGs, and untracked `.opencode/agents` helpers, tools scripts, and caches.
- The task logs at `logs/260924-1E1-slice.txt` and `logs/260924-1E2-slice.txt` remain ignored and unstaged.
- `docs/beeline_0.4.0.md` stays untouched.
- This records update stages nothing, commits nothing, amends nothing, and pushes nothing.

## Last safe boundary

The committed 1E1 commit d5da867 with message "Remove unused adapter constants" is the last safe committed boundary. The verified 1E2 work is implemented in the worktree and uncommitted. Git history is authoritative. The next implementation slice after this boundary is 1E3.
