# Objective

Complete Beeline 0.4.0 Phase 3B and Phase 5 (post content and social actions): slices 3B, 5A, 5C, 5B, 5D.
Spec: `docs/beeline_0.4.0.md`. Handoff prompt: [phase-5-posts-handoff-prompt.md](phase-5-posts-handoff-prompt.md).

Status: 3B committed (`Add the shared haptic event map (3B)`); 5A committed (`Share post content policy between row and Photo Grid detail (5A)`); 5C committed (`Space inline entity logograms from labels and isolate display direction (5C)`); 5B in verification; 5D not started.
Owner: orchestrator
Last reviewed: 2026-10-07
Authority: [AGENTS.md](../../../AGENTS.md), current source, tests, and Git.

# Invariants

- Extend `PostInteractionMutationOwner`, `PostInteractionExecutionAuthority`, `ComposerOwner`, `PostPopupOwner`. No second popup or modal authority.
- Shared domain and generic Compose stay protocol-neutral. `ui/motion` is the only motion and haptic authority.
- Effective 48 dp targets, account-scoped transient state, no private thumbnail before reveal.
- No new dependency, preference, persisted-format change, ktlint baseline exemption, or weakened test. Do not push.

# Decisions

- 3B adds `HapticEvent`, `platformConstant`, `PalustrisHaptics`, and `LocalPalustrisHaptics`. The platform call honors the system setting; no new preference.
- Haptic wiring into controls happens inside the Phase 5 slices that own the commit or long-press moment.

# Audit of earlier phases (2026-10-07)

Phases 0-2 are done in source (2E is incremental by design). Phases 3-4 are done except:
- 3C1 and 3C2 have no `fontScale = 2f` Compose test for post presentation or `NotificationRow`.
- 4E1 clearance tests for Home, Photo Grid, Notifications, and Profile have no 200% case (only Search does). 4E2 emulator evidence exists.
- 3A spacing scale stays implicit; no token object.
These are recorded in `logs/BUGS.txt` as a follow-up slice, outside Phase 5.

# Completed

- Hardening phases 0-5 (`51455c5`), Phases 4A-4E (through `e02df3a7`).

# Current slice

5A shared post content policy (`ui/posts/PostContentPolicy.kt`). 5C inline entity spacing, bidi isolation, press response, five-tag compact bubble. 5B Repost / Quote choice (`ui/posts/PostRepostChoice.kt`). Next: 5D.

# Verification

3B: `HapticEventsTest`, `SpringyInteractionsTest`, `MotionTokensTest` pass.

# Blockers

None. Unverified: device haptic feel, TalkBack, API 29, live Misskey and Mastodon behavior, real RTL locale.

# Last safe commit

`e02df3a7` (`Remove hardcoded version from ProductIdentityTest`).
