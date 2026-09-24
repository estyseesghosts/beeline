# Beeline 0.4.0 task state — Phase 1 closure audit

## Objective

Execute Beeline 0.4.0 plan phases 0-2 in verified slices. Phase 1 implementation and review are complete. The 1C-c repair commit is a189ab58467aeeb52e82386de07e9b68306d4941 with message "Fix Photo Grid parity coverage". This Phase 1 closure-audit record commit is the current safe boundary after commit. The next implementation slice is 2A, starting with 2A1 transport characterization per the plan. This record is review-only. It creates no source or test slice.

## Invariants

- Keep one authoritative state owner per lifetime.
- Bind opaque cursors to account, query, route, and variant.
- Validate origins before authenticated requests.
- Keep protocol branches in adapters.
- Do not compare opaque identifiers.
- Preserve unrelated worktree changes.
- Keep one slice to one commit.

## Current boundary

Phase 1 implementation and review are complete. The 1C-c repair commit is a189ab58467aeeb52e82386de07e9b68306d4941 with message "Fix Photo Grid parity coverage". The complete set is 1A, 1B1-1B5, 1C-a/b/c including the 1C-c repair approval, 1D1/1D2/1D3 adapter and 1D3 UI, and 1E1/1E2/1E3. The 1C-c repair is committed in a189ab5, not pending. This Phase 1 closure-audit record commit is the current safe boundary after commit. The next step is slice 2A1: characterize shared transport request, response, cancellation, origin, and Link contracts in tests, per the plan.

The visible footer and retry behavior from 1D3 stays unchanged. The 1E1 slice removes only proven-unused private constants from the two source facades. The 1E2 slice removes only proven-unused origin parameters from the two self-profile mappers. The 1E3 slice replaces only the four duplicate Mastodon path encoders with one helper. All three slices change no behavior, no protocol branch, and no shared UI or domain code.

## 1C-c repair approval

- The fresh review found that `SinglePostScreenTest.mutedQuoteWarningAndRevealMatchAcrossPostRowAndPhotoGridDetail` built its parent post without image attachments. `SinglePostScreen` routes PhotoGrid presentation with empty photos through `PostRow`, so the PhotoGrid half never exercised the detail branch.
- Committed test-only repair in `app/src/test/java/me/foxtails/palustris/ui/SinglePostScreenTest.kt` (commit a189ab58467aeeb52e82386de07e9b68306d4941): the parent fixture now includes `attachments = listOf(image("muted-parity-parent"))`, matching the `sameHiddenPostUsesTheSameQuoteDecisionInPhotoGridDetailAndPostRow` pattern. The test name and purpose are unchanged.
- The PhotoGrid half asserts `single_post_photo_pager` is displayed, which proves the Photo Grid detail branch renders. Both halves assert the parity quote body is absent before reveal and present after `Show content`. Both halves assert the `muted word: #muted` warning.
- Production behavior is unchanged. No unrelated test is changed.
- Status: 1C-c repair is approved and committed. Phase 1 review is complete. Detail is in ignored `logs/260924-1C-c-repair.txt`.

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

Ownership is unchanged by the closure audit. For the record, Phase 1 ownership still stands:

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

- Phase 1 keeps all protocol behavior in adapters. It moves no logic between adapters.
- The 1E3 helper lives in `data/mastodon`. It encodes Mastodon path segments only.
- The footer still derives from `DirectThreadResult` state only. No protocol branch exists in generic Compose UI or the generic ViewModel.
- Mastodon `Finished` has no extra control.
- Misskey continuation and limited states stay visible through the same footer.

## Verification

Focused gate evidence from the 1C-c repair session, reviewed against commit a189ab58467aeeb52e82386de07e9b68306d4941:

- `SinglePostScreenTest`: 40 passed, 0 failed, 0 errors, 0 skipped.
- `ContentWarningPolicyTest`: 3 passed, 0 failed, 0 errors, 0 skipped.
- `:app:lintDebug`: passed with BUILD SUCCESSFUL.
- Full `test assembleRelease` remains known red and was not run, per scope. The blocker is owned by `logs/BUGS.txt`.
- The Python tool suite was not run. Source changes touch no tool.
- The tool shell offers no `GRADLE_OPTS` or stdin control. No `GRADLE_OPTS` was set. One shell file probe was rejected. Verification continued through Gradle output and test XML reads.
- This audit ran no new tests. It is review-only.

## Not verified

- Live-server behavior on any protocol.
- Physical-device rendering.
- API 29 device behavior.
- RTL layout behavior.
- TalkBack order.
- Font-scale behavior.
- Signed-release checks.

## Hygiene

- `docs/classic_navigation.md` remains staged and untouched. All other unrelated changes remain unstaged or untracked: modified `.opencode/*` and `importantdocs/writing_style.md`, deleted PNGs, and untracked `.opencode/agents` helpers, tools scripts, and caches.
- The task logs at `logs/260924-1E1-slice.txt`, `logs/260924-1E2-slice.txt`, `logs/260924-1E3-slice.txt`, and `logs/260924-1C-c-repair.txt` remain ignored and unstaged.
- No new task log was created. This audit needs none.
- `docs/beeline_0.4.0.md` stays untouched.
- This closure-audit record commit contains only these two tracked records. It stages nothing else, amends nothing, and pushes nothing.

## Last safe boundary

The 1C-c repair commit is a189ab58467aeeb52e82386de07e9b68306d4941 with message "Fix Photo Grid parity coverage". This Phase 1 closure-audit record commit is the last safe committed boundary after commit. Phase 1 implementation and review are complete. Git history is authoritative. The next step after this boundary is slice 2A1.
