# Beeline 0.4.0 task state — 1D3 adapter boundary

## Objective

Execute Beeline 0.4.0 plan phases 0-2 in verified slices. This record covers the reviewed 1D3 adapter slice only.

## Invariants

- Keep one authoritative state owner per lifetime.
- Bind opaque cursors to account, query, route, and variant.
- Validate origins before authenticated requests.
- Keep protocol branches in adapters.
- Do not compare opaque identifiers.
- Preserve unrelated worktree changes.
- Keep one slice to one commit.

## Current boundary

1D3 adapter is complete and reviewed. Visible 1D3 UI is not complete. The next implementation slice is 1D3 UI: visible partial-state and retry presentation for bounded direct-message thread continuation.

The shared `DirectThreadResult` contract and mechanical repository and ViewModel plumbing are implemented. The ViewModel currently consumes only posts. It exposes no visible continuation or retry UI. That UI remains planned work, not implemented behavior.

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

- `domain/DirectMessageModels.kt`: `DirectThreadResult` and `ThreadLimitation.PendingLimit`.
- `domain/DirectMessageSource.kt`: `conversationThread` returns cursor plus `DirectThreadResult`.
- `data/misskey/MisskeyDirectMessageService.kt`: bounded BFS, v4 cursor, cumulative limitations.
- `data/misskey/MisskeySource.kt`: thread call propagation.
- `data/mastodon/MastodonDirectMessageService.kt` and `data/mastodon/MastodonSource.kt`: finished result, no continuation.
- `data/directmessages/DirectMessageRepository.kt`: continuation merge and preview rule.
- `ui/directmessages/DirectMessageViewModel.kt`: mechanical plumbing only; no visible UI change.
- `DirectMessageSourceTest`, `DirectMessageRepositoryTest`, `DirectMessageViewModelTest`: focused coverage.
- `docs/wiki/notifications-and-direct-messages.md` and `docs/agents/protocol-and-session-ownership.md`: contract notes.

## Verification

Prior repair evidence, carried forward without new commands from this session:

- `DirectMessageSourceTest`: 50 passed.
- `DirectMessageRepositoryTest`: 19 passed.
- `DirectMessageViewModelTest`: 16 passed.
- `MisskeyIntegrationTest`: 48 passed.
- `MastodonIntegrationTest`: 58 passed.
- `:app:lintDebug`: passed.
- Full `test assembleRelease` remains known red and was not run.

## Not verified

- Live Misskey and Sharkey thread ordering.
- Device rendering and visible continuation or retry behavior.
- Physical-device, API 29, RTL, TalkBack, and signed-release checks.

## Hygiene

- Staged `docs/classic_navigation.md`, `.opencode/*`, `importantdocs/writing_style.md`, deleted PNGs, and untracked tools scripts and caches remain untouched and unstaged.
- The task log at `logs/260924-180000.txt` remains ignored and unstaged.
- This session ran no commands, staged nothing, and committed nothing.

## Last safe boundary

The last safe boundary is the commit that contains this record. Get the exact hash with `git log -1`. Git history is authoritative. The next implementation slice is 1D3 UI.
