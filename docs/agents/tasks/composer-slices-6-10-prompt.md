# Handoff prompt: composer and media plan, slices 6 to 10

Read `AGENTS.md` and every document it links before you start. Then read
`docs/composer-and-media-upload-plan.md` in full, `docs/agents/tasks/composer-and-media.md`, and
`docs/agents/handoff.md`. Complete slices 6, 7, 8, 9, and 10 of the plan, in order. Do not start slice 11.

Test on the emulator as you go. Commit once per slice, with that slice's records. Do not push.

## State when you start

Slices 1 to 5 are committed on `main`:

- `5f3135cf` posting limits in `ServerCapabilities.posting` (`PostLengthCounter`, capability schema version 6).
- `3a680d79` `SocialSource.uploadMedia(MediaUploadRequest)`, `CreatePostRequest.attachments` and `idempotencyKey`, `MastodonMediaService`, `MisskeyMediaService`.
- `5fddf6f9` `AccessScope.MediaUpload`, `ServerCapabilities.effectiveMediaUpload`, Misskey `write:drive`.
- `2c79fd50` `UploadImagePreparer` (`data/media/`): `prepare(source, mimeType, fileName, compress, limits)` returns a `PreparedImage` that the caller must `release()`. It never edits the draft's file.
- `feb26b2c` `UploadCompression { Always, Never, Ask }` in `PostPreferences`, shown in Posting settings only when `posting.clientCompression` is true.

The working tree holds many unrelated untracked files (screenshots, old prompts, `logs/`, `.claude/worktrees/`). Never stage them. Stage explicit paths only.

## Decisions to use

Part 6 of the plan lists blocking questions. The user has not answered them. Use the recommended answer for each, and say so in the task record:

- Q1 ask the user or use the plan's labels ("Public", "Not in feeds", "Followers only", "Mentioned only") if the user stays silent.
- Q2 (a): the audience row shows while the first entry has focus.
- Q3: "Post replies as unlisted" does not apply to entries 2 and later.
- Q4: replace "Save draft" with "Drafts", which opens `DraftsScreen` inside the composer. Opening a draft saves the current editor first.
- Q5 (a): remove the Clean links button. Clean links on publish when `cleanTrackingParameters` is on.
- Q6: compact-wide uses the full-screen surface.
- Q7: encrypt draft image copies with the same key as draft text.
- Q9: a Misskey entry with a CW uploads its files with `isSensitive: true`.
- Q10: keep D08. The surface grows from its trigger.

## Slices

Follow the deliverables and exit gates in Part 5 of the plan exactly. In short:

- **Slice 6, draft and editor model.** `PostDraftEntry`, `DraftMedia`, `DraftStore` JSON with backward compatibility (a v0.2.11 draft loads as one entry), `DraftMediaStore` (deletion on draft delete, account removal, and orphan cleanup), `ComposerEditorState.entries`, the saver and dirty check, `ComposerOwner` entry operations. Update the `data-and-privacy.md` draft media section. No visible UI change.
- **Slice 7, thread publication.** `ThreadPublication`, `ThreadPublisher`, `ComposerContract.Actions.publish`, `FeedViewModel.create` runs it and exposes progress, progress written to the draft after each posted entry, best-effort cleanup of unused uploads. Use `UploadImagePreparer` and release each `PreparedImage` after its upload. Use `CreatePostRequest.idempotencyKey` for retries. Cover the failure, upload-failure, and session-change cases with a fake source.
- **Slice 8, composer surface.** Full-screen on compact, floating card on expanded (at most 600 dp wide), top bar with Close, Drafts, and Post with progress, guarded close, focus returns to the trigger. Update `ui-and-navigation.md`. Device check on a phone and a tablet emulator, keyboard open and closed.
- **Slice 9, composer body.** Entry layout, CW field, audience row, toolbar (photo disabled until slice 11), counter via `PostLengthCounter`, emoji insertion into the focused entry (`ComposerField` gains the entry ID), strings in `values`, `values-fr`, `values-de`, `values-b+es+419`, and `values-es-rES`.
- **Slice 10, thread editing.** "+" button, insertion after the focused entry, remove button, thread line, CW reuse dialog, audience row for the first entry only.

## Process

- Create `logs/YYMMDD-HHMMSS.txt` when coding starts.
- Keep `docs/agents/tasks/composer-and-media.md` and `docs/agents/handoff.md` current in each slice's commit.
- Gradle: `./gradlew --no-daemon --console=plain`, an explicit timeout under 600 s, standard input closed (`< /dev/null`), output to `logs/`.
- Full gate per slice: `:app:testDebugUnitTest :app:lintDebug :app:ktlintCheck :app:assembleDebug :app:assembleRelease`, plus the Python tests and the architecture audit as `docs/agents/engineering-rules.md` requires.
- Commit messages end with the `Co-Authored-By` trailer from the session reminder and a `Previous safe commit:` line.

## Known quirks

- `lintAnalyzeDebug*` sometimes crashes inside Kotlin analysis in a combined run. Rerun `:app:lintDebug` alone. It passes.
- `MastodonIntegrationTest.cancelingTimelinePageCancelsRequestAndAllowsRetry` can fail under load. It passes alone.
- The architecture audit exits 1 on regressions that predate this task. Do not edit the baseline. Add none.
- `adb exec-out screencap` fails when the emulator has several displays. Pass `-d <display id>` (see `adb shell dumpsys SurfaceFlinger --display-id`).
- No Python on the PATH in the Bash tool: use Edit, `sed`, or PowerShell.
- On Windows, Robolectric NATIVE graphics keeps decoded files locked, so do not assert file deletion after `BitmapFactory.decodeFile`.
- Windows line endings: Git warns about LF to CRLF. That is expected.

## Report

End with what you did per slice, the commit hashes, and what is unverified (live Misskey sign-in showing drive access, live uploads, the compression row on a Misskey account, anything not seen on a device).
