# Objective

Implement docs/planorma.md (Pleroma/Akkoma/Pixelfed, 8 slices) and docs/dvd-recs.md (DVD Sharkey recommendations, 4 slices) in order. Planorma Slice 1 first (schema 13->14), then DVD Slice 1 (14->15).

# Invariants

- domain/ stays protocol-neutral; protocol branches in adapters/auth/probe only.
- Unknown separate from Unsupported; Denied separate from Unsupported.
- One slice, one owner, one commit. Preserve unrelated worktree changes. Never push unless asked.

# Decisions

- Slice order: Planorma first (user confirmed).
- Routing: finder + problem_solver read-only, implementer bite-sized edits, orchestrator verifies/commits (user confirmed).
- Records: docs/agents/* already un-ignored in .gitignore; logs stay local-only.
- orchestrator.md left untouched by me (worktree deletion preserved as-is, excluded from slice commits via --only).

# Completed

- Planorma Slice 1 — server flavor detection + capability table, schema 13->14. Commit `d90c9eca` "Detect-server-flavor-S1".
- Planorma Slice 2 — PKCE detection path fix. Commit `25806db2` "Fix-PKCE-probe-path-S2".
- Planorma Slice 3 — Pleroma limits + content_type composer. Commit `78b796d2` "Add-content-type-composer-S3".
- Planorma Slice 4 — emoji reactions (mapper/probe/mutation/row/picker + wiring + review repairs). Commit `813c99ae` "Add-pleroma-reactions-S4".
- Planorma Slice 5 — reaction notifications + push. Commit `30167f53` "Add-reaction-notifications-S5".
- Planorma Slice 6 — Pixelfed notification/push/streaming/report gates. Commit `21c2ec18` "Gate-Pixelfed-routes-S6".
- Planorma Slice 7 — Pixelfed composer/discovery/edit/timeline gating + Bubble route. Commit `20cb0083` "Gate-composer-timelines-S7".
- Planorma Slice 8 — paging relax + 404/405 demotion + fixtures/contract tests. Commit: (this checkpoint).

# Current slice

DVD Slice 1 — domain and capability (amended scope). Owner: TBD at dispatch.

# Files involved

Slice 1 (committing now):
- app/src/main/java/me/foxtails/palustris/domain/ServerCapabilities.kt
- app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonCapabilityProbe.kt
- app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonSource.kt
- app/src/main/java/me/foxtails/palustris/data/auth/AccountFileStore.kt
- app/src/test/java/me/foxtails/palustris/data/mastodon/MastodonCapabilityProbeFlavorTest.kt (new)
- docs/agents/tasks/planorma-dvd-recs.md (this file)
- docs/agents/handoff.md (slice entry)

# Verification

- compileDebugKotlin + compileDebugUnitTestKotlin: pass (post R-1/R-2).
- ktlintCheck (main+test source sets): pass.
- lintDebug: pass (pre and post R-1/R-2).
- testDebugUnitTest full: pass (-q post-repair; earlier exit-1 resolved after repairs; --tests filtering broken project-wide, pre-existing).
- assembleDebug + assembleRelease: pass.
- tools/tests: 52 pass.
- arch audit --check: exit 1, 1079 findings. Pre-existing exit-1 baseline plus NEW slice regressions: hotspot-growth on AccountFileStore/MastodonCapabilityProbe/MastodonSource/ServerCapabilities (+ Misskey/MisskeyMapper/MisskeySource/SocialModels/SocialSource/ComposerEditorState/ComposerOwner/HashtagBubble/PostShareSheet/ConnectedSessionHost/ShellOverlayHost from full-file metrics) and function-complexity-growth on MastodonSource.refreshCapabilities (R-2 re-derivation). Recorded; baseline untouched per project rule. Plan "no new violations" tension noted.
- problem_solver review: BLOCKING (verification gap) cleared by gate above; REQUIRED R-1/R-2 repaired by owner; OPTIONAL O-1/O-2/O-3 deferred.

# Next

Planorma Slice 2 (PKCE path api/v2/instance + auth tests). Then Slices 3-8, then DVD Slices 1-4 (DVD Slice 1 needs amended scope per freshness review: exhaustive-when arms + strings + anonymous exclusion).

# Blockers

- arch audit new hotspot/complexity growth on touched files vs plan "no new violations" (recorded, not hidden).
- --tests filtering finds no tests project-wide (pre-existing infra; full suite used instead).
- Untracked screenshots/tmp files and pre-existing agent-config modifications excluded from slice commit.
- No device or live-server checks ran.

# Last safe commit

3374a288 Hide boosts from chosen accounts (local main, ahead of origin/main; Slice 1 commit to follow).
