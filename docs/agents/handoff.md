# Handoff

**Status:** Slice 2D2 and its adapter-test follow-up are implemented in the worktree and remain uncommitted.

The parent session must stage both record files as whole files. This is intentional.
The task-state file is `docs/agents/tasks/beeline-0.4.0.md`.

## Current position

Profile Liked eligibility now uses `SocialSource.profileCapability`.
The result carries a target and a distinct `CapabilityStatus`.
Profile ViewModel and pager no longer inspect `Protocol` for Liked eligibility.
Misskey supports self and other targets from capability evidence.
Mastodon supports self from capability evidence and rejects other targets as unsupported.

## Verification

- `ProfileViewModelTest`, `ProfileTimelinePagerTest`, and `ProfileSourceContractTest` passed.
  The adapter contract covers self and other targets and all capability statuses.
- The generic profile UI and ViewModel grep found zero `Protocol` references.
- Probe, cache, persistence, and schema code did not change.
- Profile source adapter contracts and `lintDebug` passed. `assembleRelease` passed. The full gate
  reported 16 known baseline test failures; the earlier timeout and those baseline failures remain
  unresolved.

## Modified slice files (10)

- `app/src/main/java/me/foxtails/palustris/domain/ServerCapabilities.kt`
- `app/src/main/java/me/foxtails/palustris/domain/SocialSource.kt`
- `app/src/main/java/me/foxtails/palustris/data/misskey/MisskeySource.kt`
- `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonSource.kt`
- `app/src/main/java/me/foxtails/palustris/ui/profile/ProfileViewModel.kt`
- `app/src/main/java/me/foxtails/palustris/ui/profile/ProfileTimelinePager.kt`
- `app/src/test/java/me/foxtails/palustris/ui/profile/ProfileViewModelTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/profile/ProfileTimelinePagerTest.kt`
- `docs/agents/tasks/beeline-0.4.0.md`
- `docs/agents/handoff.md`

The follow-up test path is `app/src/test/java/me/foxtails/palustris/ProfileSourceContractTest.kt`.

The ignored record is `logs/260928-profile-liked-eligibility.txt`.
Staged `docs/classic_navigation.md` remains untouched.
The ten paths above define the original 2D2 slice. The follow-up adds only the profile contract
test and these records.
`git diff HEAD --name-only` includes pre-existing unrelated paths. Exclude seven `.opencode` files,
deleted PNGs, helpers, caches, logs, and staged `docs/classic_navigation.md`.
Commit `92d15a8` contains the deleted `PhotoGridFeedViewModelTest`; that deletion predates and does
not belong to 2D2.

## Next slice

Run the focused adapter tests and full gate when available. Then stage the ten original 2D2 paths,
the adapter contract test, and two record paths. Do not stage the ignored implementation log or
unrelated files.

## Known limits

Live-server, device, API 29 physical, RTL, TalkBack, font-scale, signed, and wide or foldable
checks remain unverified. Other-account device checks need `@ctr` approval.
The architecture audit remains count-only at 611; it was not rerun. The full gate still has 16
known baseline failures. `TemporarilyUnavailable` preserves only the normalized status because no
UI or retry behavior needs the typed cause.
