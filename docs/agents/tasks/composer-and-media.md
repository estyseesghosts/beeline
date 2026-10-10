# Objective

Implement `docs/composer-and-media-upload-plan.md`. Slices 1 to 8 (limits, upload transport, drive permission, image preparation, compression setting, draft and editor model, thread publication, composer surface) are done. Slice 9 (composer body) is done. Slices 10 (thread editing) and 11 (media in the composer) are in progress.

# Invariants

- Shared domain stays protocol-neutral. Protocol behavior stays in adapters and probes.
- Capability schema version moves forward (now 6).
- Old stored sessions, settings, and drafts load with safe defaults. A v0.2.11 draft loads as one entry.
- The ktlint baseline is keyed by line number. Do not shift lines in files that have baseline entries (for example `di/AppModule.kt`).
- The architecture audit measures a bodiless interface function up to the end of the next class. Do not grow `ComposerOwner.kt` past 483 lines or add a parameter to an existing owner function. Put pure logic in extension files.

# Decisions

- Blocking questions use the plan's recommended option (the user has not answered them): Q1 plan labels ("Public", "Not in feeds", "Followers only", "Mentioned only"), Q2 (a) audience row shows while the first entry has focus, Q3 "Post replies as unlisted" applies to entry 1 only, Q4 "Drafts" replaces "Save draft", Q5 (a) no Clean links button and links are cleaned on publish, Q6 compact-wide is full screen, Q7 encrypt draft media, Q8 strip GPS, Q9 Misskey entry with a CW uploads files as sensitive, Q10 keep D08.
- `DraftMediaStore` is stateless, so `EncryptedDraftStore` builds its own instance and Hilt injects another. Both address the same directory.
- Entry operations that take an entry id have distinct names (`setEntryText`) so the single-post callers keep their signatures.
- Slice 7: `ComposerContract.Actions.publish(ThreadPublication, ThreadPublishListener)` keeps two parameters so the audit records no params-growth regression. `FeedViewModel.create` is a thin slot reservation; `ThreadPublicationRunner` owns the job. Ask compression maps to `compress=true` until slice 11 adds its dialog; effective compression also requires `posting.clientCompression`.

# Completed

- Slices 1 to 5 — commits 5f3135cf, 3a680d79, 5fddf6f9, 2c79fd50, feb26b2c.
- Slice 6: draft and editor model for threads and media — commit 893ce8a3.
- Slice 7: thread publication — commit a50298fa.
- Slice 9: composer body (`ComposerBody.kt`, `ComposerEntryRow.kt`, `ComposerAudienceRow.kt`, `ComposerEntryRules.kt`, `domain/PostLimits.kt`; `ComposerField` carries the entry id; `ComposerContract.limits` and `prepareText`; `ComposerScreen.kt` deleted) — commit recorded in the follow-up.
- Slice 8: composer surface (`ComposerSurface.kt` new, `ComposerSheet.kt` deleted, card flag through the shell, `composer_publish_progress` string, wiki section) — commit 3ac01ce5.

# Current slice

Slice 10: thread editing.

# Files involved

Slice 10 has not started; its owner records its files here.

# Verification

- Slice 7: ThreadPublisherTest 6/6, FeedViewModelThreadTest 3/3, ComposerOwnerTest 27/27, SessionViewModelTest 16/16; full unit suite 1927 tests, 0 failures; tools/tests 52 OK; audit with no new regressions; ktlintCheck, assembleDebug, assembleRelease pass; lintAnalyzeDebug passes while the unitTest/androidTest lint variants crash in the analyzer on untouched files (pre-existing); emulator-5554 launch smoke test passes with feed rendering (logs/slice7-launch2.png). See logs/20261010-120000.txt and the slice commit.
- Slice 8: ReplyComposerTest 4/4, ExpandedComposerSurfaceTest 2/2, plus NavigationTest (8 composer flows incl. new back-press autosave), SignInScreenTest, ShellCharacterizationTest, WideNavigationTest, LocalizationResourceTest; full unit suite 1933 tests, 0 failures; tools/tests 52 OK; audit with no new regressions (9 pre-existing); ktlintCheck, lintDebug, assembleDebug, assembleRelease pass; emulator-5554 phone smoke (feed, composer keyboard closed/open) in logs/s8-composer.png and logs/s8-keyboard.png. Tablet card placement unverified on hardware (Robolectric w900dp covers it). See logs/20261010-133000.txt and the slice commit.

- Slice 9: ComposerEntryRulesTest, ComposerBodyTest, ReplyComposerTest, ComposerOwnerTest, NavigationTest, SignInScreenTest; full unit suite 1954 tests, 0 failures; tools/tests 52 OK; audit zero new regressions; ktlintCheck, lintDebug, assembleDebug, assembleRelease pass; emulator-5554 phone smoke in logs/s9-*.png. Tablet hardware unverified. See logs/20261010-150000.txt.

# Next

Slice 10 of the plan (thread editing), then slice 11.

# Blockers

None. Tablet hardware check, live-server publish, and pre-existing API 29 instrumentation gaps remain open.

# Last safe commit

3ac01ce5 Show the composer full-screen or as a floating card (slice 9 commit follows in Git)
