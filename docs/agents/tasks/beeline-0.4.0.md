# Beeline 0.4.0 task state — Phase 3C1

## Status

Phase 3C1 repairs post presentation at a 200% font scale. The change stays in shared,
protocol-neutral post presentation.

## Requirement and owner

Post metadata, filtered hashtag summaries, reaction chips, and action controls must remain
readable at font scale 2.0. The post list must keep final actions reachable through its existing
scroll container.

`PostContentPresentation.kt` owns metadata and filtered hashtag presentation.
`PostInteractionPresentation.kt` owns reaction and interaction row presentation.
`SinglePostScreen.kt` owns bottom scroll clearance.

Fixed heights now act as minimum heights. The filtered hashtag minimum also scales with font scale.
The 1.0 layout keeps its existing 32dp minimum. Width, ellipsis, semantics, callbacks, action
availability, and optimistic ownership remain unchanged.

## Device evidence

The run used emulator `emulator-5554`. The session stayed preserved. OAuth received no input.

* Home at 1.0 passed. Avatar, name, timestamp, and hashtag text had no clipping.
* Home at 2.0 passed for scaled text and the complete five-icon action row.
* Home at 2.0 showed bottom-edge clipping in hashtag chips in both captures.
* The new summary minimum addresses this residual in the implementation and test.
* The font scale returned to 1.0. Home restored successfully.
* Detail, Search, Photo Grid, Notifications, DMs, and Profile remain unverified.

## Verification

* `SinglePostScreenTest` passed 41 tests, including hashtag text bounds and action reachability.
* `:app:lintDebug` passed.
* The prior audit reported 615 checks with zero regressions.
* A reviewer audit rerun was denied.
* `test assembleRelease` reached release assembly, but unrelated existing unit failures remain.
* The full gate is therefore not green. The failures include draft actions, capability cache,
  Mastodon artwork defaults, Misskey continuation, and notification synchronization tests.

## Documentation review

The post boundary documentation remains accurate. No wiki update is required because ownership,
protocol behavior, persistence, navigation, and container behavior did not change.

## Preservation and continuation

The unrelated dirty worktree remains untouched. This subagent does not stage, commit, or push.

History: `7e7f774`.

Last safe commit: `7e7f774`. Next slice: Phase 3C2.
