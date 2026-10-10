# Objective

Implement `docs/composer-and-media-upload-plan.md`. Slices 1 to 5 (limits, upload transport, drive permission, image preparation, compression setting) are done. Slices 6 to 10 (draft and editor model, thread publication, composer surface, composer body, thread editing) are in progress. Slice 11 (media in the composer) is not requested yet.

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

# Completed

- Slices 1 to 5 — commits 5f3135cf, 3a680d79, 5fddf6f9, 2c79fd50, feb26b2c.
- Slice 6: draft and editor model for threads and media — the commit that follows feb26b2c.

# Current slice

Slice 7: thread publication.

# Files involved

- `domain/PostDraft.kt` (`DraftMedia`, `PostDraftEntry`, `PostDraft.media`, `PostDraft.followUps`)
- `data/auth/DraftStore.kt`, `EncryptedDraftStore.kt`, `DraftJson.kt`, `DraftMediaStore.kt`
- `ui/composer/ComposerEditorState.kt`, `ComposerEditorStateSaver.kt`, `ComposerEntryEdits.kt`, `ComposerTargetPreview.kt`, `ComposerOwner.kt`
- Tests: `DraftMediaStoreTest`, `ComposerOwnerTest`

# Verification

- Slice 6: focused tests and the full local gate; see the slice commit.

# Next

Slice 7 of the plan.

# Blockers

None. Live-server and device checks are not yet done.

# Last safe commit

feb26b2c Add an upload compression setting shown only where the server supports it
