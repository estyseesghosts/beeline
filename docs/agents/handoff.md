# Handoff

**Status:** `docs/agents/tasks/beeline-0.4.0.md` is the active durable task state.

## Current boundary

Slice 1B5-M is implemented, verified, low-reviewed, and recorded by the commit that includes these records, from base `9e977de`. The focused `MastodonIntegrationTest` and `MastodonSourceContractTest` gates passed. No UI behavior changed.

The implementation changed Mastodon response reads to use a 4 MiB operation cap. Neutral transport response-limit parameters default to `null`, so existing Misskey call sites retain their behavior. The oversized post test asserts `ResourceLimit("post")`.

## Next steps

The 1B5-M verification gates passed. The next implementation slice is 1B5-K for Misskey. Do not combine the adapters.

The full unit suite is known red with 16 failures. Do not run the known-red `test assembleRelease` gate for this slice. Pixel Fold emulator `emulator-5554` verified compact and wide rendering in CLOSED and OPENED postures. This does not verify physical-device behavior or live oversized-response handling. Physical-device, API 29, RTL, TalkBack, signed-release, and live-server oversized-response behavior remain unverified.

The emulator installed `app/build/outputs/apk/debug/app-debug.apk` with `adb install -r`. After a five-second adb-shell wait, CLOSED showed hinge 0.0, 1080x2092, `MainActivity` RESUMED, and the narrow single-column feed with bottom navigation. OPENED showed hinge 180.0, 2208x1840, `MainActivity` RESUMED, and the two-pane layout with navigation rail and detail pane. No crash or ANR evidence appeared.

## Hygiene

Never stage `docs/beeline_0.4.0.md`, `docs/260923_current_state.md`, `docs/*.png`, `tools/scripts/*`, `.opencode/*`, `logs/*`, or pre-existing dirty files. Leave `docs/classic_navigation.md` staged exactly as found and do not include it in the 1B5-M commit. Preserve all unrelated worktree changes.

## Verification scope

`:app:lintDebug`, Python tool tests, the architecture audit with `--check`, scoped diff checks, and `:app:assembleDebug` passed. The APK is `app/build/outputs/apk/debug/app-debug.apk`. The Gradle commands used `--no-daemon --console=plain` and explicit timeouts. The adb-only compact/foldable emulator check passed; live oversized-response behavior remains unverified.

## Last safe boundary

Git history is authoritative for the exact hash. The commit that includes this handoff is the 1B5-M slice boundary. It includes source, test, task-state, and handoff records. The task log remains current on disk, unstaged, and outside the commit under the `logs/*` rule. The base is `9e977de`. At the start of the next session, use `git log -1` to read the 1B5-M boundary hash. The next implementation slice is 1B5-K.
