# Beeline 0.4.0 task state — Profile Liked eligibility

The parent session must stage both task records as whole files. This is intentional.
This rewrite records the 2D2 adapter-test follow-up and preserves committed slice 2D1 at `752f380`.

## Objective

Move profile Liked eligibility behind a target-aware, protocol-neutral source query.

## Status sets

- 2D1 status: committed at `752f380`. `PostThreadViewModel` requires `PostPreferencesRepository`.
- 2D2 status: `SocialSource.profileCapability` returns a target-aware result with `CapabilityStatus`.
  Misskey uses capability evidence for self and other accounts. Mastodon uses evidence for self and
  returns unsupported for other accounts. The profile ViewModel and pager use this result.
- Status set: unknown, denied, unsupported, temporarily unavailable, and supported remain distinct.
- Persistence status: no probe, cache, schema, or serialized format changed. No schema bump was needed.
- Focused tests: `ProfileViewModelTest`, `ProfileTimelinePagerTest`, and
  `ProfileSourceContractTest` passed, including adapter target and status coverage.
- The generic profile UI and ViewModel grep found zero `Protocol` references.
- Profile source adapter contracts and `lintDebug` passed. `assembleRelease` passed. The full gate
  reported 16 known baseline test failures. The earlier timeout and those baseline failures remain
  unresolved.

## Slice 2D2 files (10)

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

The adapter-test follow-up also changes `app/src/test/java/me/foxtails/palustris/ProfileSourceContractTest.kt`.

## Preservation

- The original 2D2 slice has the ten paths listed above. `git diff HEAD --name-only` also shows
  pre-existing or unrelated work, including seven `.opencode` files, deleted PNGs, helpers, caches,
  staged `docs/classic_navigation.md`, and the already committed Photo Grid deletion.
- Commit `92d15a8` records the `PhotoGridFeedViewModelTest` deletion. It is not a 2D2 change.
- The adapter-test follow-up changes only the listed profile contract test and these two records.
- No files were staged or committed in this session.

## Exact staging pathspec for the parent session

Stage only the ten original code and test paths listed above, the adapter-test follow-up path,
plus these records:

`app/src/test/java/me/foxtails/palustris/ProfileSourceContractTest.kt`
`docs/agents/tasks/beeline-0.4.0.md`

`docs/agents/handoff.md`

Do not stage `logs/260928-profile-liked-eligibility.txt` or unrelated files.

## Verification limits

Live-server behavior remains unverified. Profile Liked chips remain unverified on a device.
Other-account device checks require `@ctr` approval and remain blocked unless signed in.
API 29 physical, RTL, TalkBack, font-scale, signed, and wide or foldable checks remain unverified.

## Records

- Ignored implementation log: `logs/260928-profile-liked-eligibility.txt`.
- Audit was not rerun. The prior count-only architecture audit was 611 with zero baseline
  regressions.
- `TemporarilyUnavailable` maps rate limits, network failures, server errors, and resource limits
  to one status. The typed cause remains at the `SourceError` boundary because no UI or retry
  behavior currently needs a more specific profile capability cause.
- No secrets, tokens, callback values, or response bodies belong in these records.
