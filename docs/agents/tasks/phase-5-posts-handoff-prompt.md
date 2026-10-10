# Next-agent prompt — Complete Phase 5 of the Beeline 0.4.0 plan (post content and social actions)

The authoritative spec is `docs/beeline_0.4.0.md`, section "Phase 5 — post content and social actions"
(slices 5A, 5B, 5C, 5D). Read the plan's "Rules for every slice", "Execution map", and the
"Adaptive floating navigation direction" section first. Source, tests, and Git outrank every plan
and completion claim.

Do not confuse this with `docs/agents/tasks/phase-5-completion-prompt.md`. That file belongs to the finished
hardening plan (`docs/fix_0.4.0.md`, H14) and is stale.

## Required reading (AGENTS.md)

`docs/agents/workflow.md`, `agent-control.md`, `engineering-rules.md`, `documentation-rules.md`,
`operation-rules.md`, `importantdocs/writing_style.md`, `docs/agents/handoff.md`, and the ownership pages
for posts, composer, shell, and protocol/session in `docs/agents/README.md`.

## Subagents

- Use only the project agents `finder` (read-only tracing and sweeps) and `implementer` (bite-sized work packages).
- Keep subagent prompts short and specific: files, deliverable, non-goals. Never use general-purpose agents.
- You own design, ADB, Gradle, verification, and Git. Subagents never run ADB, Gradle, or Git.

## Current state (verify with Git; do not trust this list over it)

- Branch `main`, HEAD around `e02df3a7` (`Remove hardcoded version from ProductIdentityTest`).
- The active task file `docs/agents/tasks/beeline-0.4.0.md` still describes Phase 4D5/4E, which is complete
  (slices 1, 2, 2b, 3, 4 committed). Rewrite it for Phase 5 when you start; do not append.
- Last hardening safe commit: `51455c5`. Unrelated untracked captures, scripts, and prompt files exist.
  Leave them. Stage only reviewed paths with explicit `git add <path>`. Do not push.

## Gates to verify before starting

Phase 5 depends on **1C** (Phase 1 visibility defects), **3B** (motion and haptic authority), and **4A**
(back, modal priority, popup/back). Confirm each from source and Git history. If a gate is not met, stop and
report which one; do not implement it silently.

## Slices (one behavior, one reviewable diff, one commit each, in this order)

Order: 5A, then 5C (independent of 5A; integrate after 3B), then 5B (after 5A and 4A), then 5D (after 5A and 5B).
Work sequentially in this one working tree.

### 5A. Shared post content policy for row and detail

- Files: `ui/posts/PostRow.kt`, `SinglePostScreen.kt`, `PostContentPresentation.kt`, `ContentWarningPresentation.kt`,
  `ui/thread/ThreadedReplies.kt`, `ui/shell/DetailActionPolicy.kt`, `PostProjectionCoordinator.kt`,
  `ui/posts/PostRowCallSurface.kt`.
- Find the duplicated policy for warning, muted tag, quote, poll, reaction, and availability decisions.
  Replace the detail copy with the verified shared owner. Keep distinct Photo Grid photo geometry.
- Keep actions bound to `OwnedPost.fetchedBy`, session revision, and post identity. No protocol branches in generic Compose.
- Keep author, text, and media as thread anchors. Feed scroll must survive return from detail.
- Tests: `SinglePostScreenTest`, `PostInteractionExecutionAuthorityCrossSurfaceTest`, `HomeFeedTest`. Cover hidden and
  allowed content, quote availability, ancestor and reply pagination, stale post, session swap, failure, compact and wide details.
- Add a characterization test before any risky extraction.

### 5C. Hashtag and link bubbles

- Files: `ui/posts/PostContentPresentation.kt`, `HashtagBubble.kt`, `ui/emoji/InlineEmojiText.kt`, `ui/posts/PostTextPresentation.kt`.
- Inline tappable `#tag` has no logogram; bubble variants keep it. Show three to five large bubbles by measured height.
  `See all` shows the compact full list, `See less` returns. The selected tag goes to Search and stays visible.
- Inline links reserve measured icon width and icon-to-text space. The domain stays readable under long text, RTL mixed
  direction, and font scaling. Isolate display direction without changing the exact URL or copy target. Share press response.
  Do not duplicate link target parsing in UI.
- Tests: `PostTextPresentationTest`, `InlineEmojiTextTest`, `SearchPanelRestorationTest`. Cover 0, 1, 6+ tags, long domains,
  long translations, bidi, TalkBack semantics, and prefilled Search.

### 5B. Repost / Quote choice

- Files: `ui/posts/PostInteractionPresentation.kt`, `PostRepostConfirmationState.kt`, `PostRepostConfirmationCompositionLocal.kt`,
  `PostPopupOwner.kt`, `PostActionBubblePlacement.kt`, `ui/composer/ComposerOwner.kt`, `ui/shell/ComposerContract.kt`.
- Tap opens a source-anchored choice: `Repost` or `Undo repost`, and `Quote`, each only when the current source supports it.
  A tap alone never sends a repost. Long-press and accessibility quote stay reachable. Fall back to a sheet at edges or large font.
  Close on back, outside tap, stale post, account change, and session replacement. Provide a visible focus path.
- Quote routes into the existing target-aware composer. Optimistic repost and rollback stay in
  `PostInteractionMutationOwner` and `PostInteractionExecutionAuthority`, not popup state.
- Tests: `PostRepostConfirmationStateTest`, `PostPopupOwnerTest`, `PostInteractionIndicatorTest`, `ReplyComposerTest`.
  Cover both options, unsupported quote, undo, cancel, failed mutation, account swap.

### 5D. Optimistic action states across surfaces

- Files: `ui/posts/PostInteractionPresentation.kt`, `PostInteractionMutationOwner.kt`, `ui/shell/PostProjectionCoordinator.kt`,
  `ui/feed/FeedViewModel.kt`, and the feature projection consumers.
- Animate icon and known count together for favorite, reaction, bookmark, and repost. Unknown stays unknown. Favorite stays
  distinct from emoji reaction. Total reposts stay distinct from quotes. Pending must not look confirmed. On failure restore
  icon, count, and selection, and show a short error. Server responses remain the reconciliation authority.
- Extend the existing animation in `PostInteractionPresentation`; do not replace it. Share and reply keep transient press feedback.
- Tests: `PostInteractionMutationOwnerTest`, `PostInteractionExecutionAuthorityCrossSurfaceTest`, `FeedViewModelReactionTest`,
  and both adapter contracts. Cover rapid opposite taps, stale response, network failure, account switch, read-only reactions.
- Home, Search, Photo Grid, Profile, and detail must agree on one outcome.

## Per-slice procedure

1. At the start create `logs/YYMMDD-HHMMSS.txt` (goal, slices, files, risks). Rewrite `docs/agents/tasks/beeline-0.4.0.md` for Phase 5.
2. Inspect `git status`, recent commits, and the diff. Preserve unrelated changes.
3. Implement, then run the smallest named test first, then the relevant adapter and Compose suites. Run lint after any
   contract, adapter, or security change.
4. Run the full CI-parity gate before each commit that changes code (see `engineering-rules.md#verification`):

   ```text
   python -m unittest discover -s tools/tests
   python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check
   .\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug :app:ktlintCheck :app:assembleDebug :app:assembleRelease
   ```

   Use `GRADLE_OPTS=-Dorg.gradle.daemon=false`, closed stdin, and an explicit timeout. Recheck for external Java or Gradle
   activity first. Do not edit source during a gate run. Known flakes: `MastodonIntegrationTest` cancellation and
   `NotificationsViewModelTest` isolation; rerun alone, record in `logs/BUGS.txt`.
5. Update the affected `docs/wiki/` and `docs/agents/` ownership pages in the same slice. Rewrite `docs/agents/handoff.md`.
   Record remaining issues in `logs/BUGS.txt`.
6. Inspect the final diff, stage only that slice's files, and commit the slice with its records. One commit per slice.
7. Runtime check on `emulator-5554` (API 37) for UI slices: install the debug APK, launch `me.foxtails.palustris/.MainActivity`,
   capture to `logs/`. Check light, dark, and pure-black themes, 200% text, compact and wide, RTL, and reduced motion where
   the emulator allows. Use `tools/scripts/adb_*.py`. A SIGABRT from `android.hardwar` is an emulator HAL process, not the app;
   look for `FATAL EXCEPTION` with the app package. Reset any `wm size` or density override afterward.

## Invariants

- One mutable state, one owner. Extend existing owners (`PostInteractionMutationOwner`, `PostInteractionExecutionAuthority`,
  `ComposerOwner`, `PostPopupOwner`); do not invent a replacement architecture or a second popup or modal authority.
- Shared domain and generic Compose stay protocol-neutral. Capability differences go through `domain/ServerCapabilities.kt`
  and adapters. Do not add a global protocol flag to shared `Post`.
- Use `ui/motion` as the only motion authority. Respect system animator scale 0.
- Keep effective 48 dp targets, truthful semantics during pending and rollback, and account or session scoping of all
  transient state. No private thumbnail before reveal.
- Product text says **Beeline**. Preserve Palustris compatibility identifiers.
- No persisted-format migration, new dependency, new ktlint baseline exemption, weakened test, or tolerated audit finding.
- Never log secrets or full API responses. Do not push.

## Stop conditions

- A dependency gate (1C, 3B, 4A) is unmet.
- Two failed fixes for one root problem.
- A slice needs an unapproved dimension, a new preference, or a protocol or storage change. Ask the user.
- Never substitute mocks for live checks. Record unverified items instead.

## Unverified items to record (no accounts or hardware exist)

API 29 instrumentation, physical devices, TalkBack, signing, live Misskey and Mastodon behavior (repost, quote,
reaction, favorite), real RTL locale.

## Deliverables

- Four slice commits (5A, 5C, 5B, 5D), each gated and with records.
- `docs/agents/tasks/beeline-0.4.0.md` and `docs/agents/handoff.md` rewritten for the final Phase 5 state.
- Wiki and ownership pages current; `logs/` entry and `logs/BUGS.txt` updated.
- Final report: commit hashes, gate results, emulator evidence paths, and every unverified item with its reason.
