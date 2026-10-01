# Objective

Restore a trustworthy test gate. Repair the confirmed restoration, validation, session-selection, and upload defects. Then resume Phase 4A.

# Invariants

Preserve unrelated worktree and index content. Do not weaken tests. Do not implement Phase 4B through 4E. Do not change the approved navigation design. Do not add speculative DM pagination or notification eviction.

# Decisions

The recovery plan at C:\Users\julie\.opencode\plan\beeline-0.4.0-phase4-recovery.md controls slice order. R00 through R15 run serially with one reviewed commit per slice. Phase 4A stays blocked until R15 return criteria pass. The deleted Photo Grid test needs owner approval before committed-suite comparison. New slice R15a splits the Mastodon favourite artwork contract per protocol before R15.

# Completed

- R00 — record the current recovery baseline and failure attribution. Fresh grouped, isolated, and full-gate evidence replaces the historical count.
- R01 — activate draft writers in account-bound unit fixtures. All 13 DraftActionsTest tests pass. DraftWriteAuthorityTest passes with no regression.
- R02 — bind shell draft fixtures to an active writer. Full NavigationTest passes with 30 tests. ComposerOwnerTest, ShellCharacterizationTest, and SignInScreenTest pass with no regression.

# Current slice

R02 is complete. Production draft, composer, and shell rules remain unchanged. No R02b product slice was needed.

# Files involved

- app/src/test/java/me/foxtails/palustris/ui/shell/AppShellFixtures.kt holds ShellDrafts and the account-null preview.
- app/src/test/java/me/foxtails/palustris/ui/navigation/NavigationTest.kt holds the active fixture sessions and new draft coverage.
- app/src/test/java/me/foxtails/palustris/ui/shell/ShellCharacterizationTest.kt holds the active reply-publish fixture.
- app/src/test/java/me/foxtails/palustris/ui/SignInScreenTest.kt holds the active publish fixture.
- docs/agents/app-shell-ownership.md holds the fixture lifetime contract.
- docs/wiki/ui-and-navigation.md holds the draft restoration section.
- docs/agents/tasks/beeline-0.4.0.md holds this state.
- docs/agents/handoff.md points to this recovery task.
- logs/261001-030000.txt holds R02 evidence and exact checks.

# Verification

Pre-repair rerun of both historical Navigation draft methods reports 2 tests and 2 failures. Each historical method also passes alone after the repair. Post-repair rerun of all draft methods reports BUILD SUCCESSFUL. Full NavigationTest reports BUILD SUCCESSFUL with 30 tests. ComposerOwnerTest, ShellCharacterizationTest, and SignInScreenTest report BUILD SUCCESSFUL. ktlintTestSourceSetCheck reports only pre-existing NavigationTest findings; AppShellFixtures has no new finding. Post-repair test assembleRelease reports 1466 tests and 13 failures with release assembly complete; both historical Navigation failures are resolved and no new failure appears. Source revision is 97e2499 plus the R02 worktree changes. Full evidence lives in logs/261001-030000.txt.

# Next

Execute R03. Repair the Misskey continuation test runtime or proven defect. Retain all five failing invariants.

# Blockers

The deleted Photo Grid test excludes that test from the available suite. A clean-snapshot comparison remains pending because git worktree access is denied. The timestamped logs and BUGS.txt edits need explicit force-add approval because /logs/*.txt is Git-ignored. Device, API 29, live-server, signing, and environmental checks remain unverified.

# Last safe commit

97e2499 Activate draft writers in account-bound unit fixtures.
