# Handoff

**Status:** `docs/agents/tasks/beeline-0.4.0.md` is the active durable task state.

## Current position

OAuth callback diagnostics now have a strict invalid-category matrix. Session tests cover callback
deferral during restore and ignoring callbacks without pending state. No protocol behavior changed.

The staged `docs/classic_navigation.md`, unrelated dirty files, helper scripts, images, and
interrupted 2C3 PhotoGrid/Search files remain outside the OAuth commit pathspec.

## Verification

The focused auth and session tests passed: 3 `AuthGatewayTest` tests and 15 `SessionViewModelTest`
tests. The existing SignInScreen test and `:app:lintDebug` also passed. The architecture
audit reports 613 findings against a 610 baseline. The three-count difference is unexplained, but no
OAuth-attributable regression exists; unchanged baseline regression lines remain in the audit log. The
audit command returned exit code 0.
The full gate command `test assembleRelease` timed out after 120 seconds. Release assembly completed
before the timeout. Known baseline failures remained.

The dummy device callback reached `onNewIntent`. The real redirect did not arrive. The canvas guidance
was absent from the accessibility tree. Screenshot capture was blocked by tool policy. The MainActivity
dispatch remains untestable in the unit harness; instrumentation is future work.

## Next slice

Run the focused auth, session, SignInScreen, and MainActivity verification that the available harness
supports. Keep the MainActivity harness limitation explicit if instrumentation remains unavailable.

## Staging boundary

The exact committable OAuth paths are:

- `app/src/main/java/me/foxtails/palustris/MainActivity.kt`
- `app/src/main/java/me/foxtails/palustris/ui/session/AccountManager.kt`
- `app/src/main/java/me/foxtails/palustris/data/auth/MastodonAuth.kt`
- `app/src/main/java/me/foxtails/palustris/ui/setup/SetupScreens.kt`
- `app/src/main/java/me/foxtails/palustris/ui/UiStrings.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/me/foxtails/palustris/data/auth/AuthGatewayTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/session/SessionViewModelTest.kt`
- `docs/agents/tasks/beeline-0.4.0.md`
- `docs/agents/handoff.md`

Append `app/src/test/java/me/foxtails/palustris/ui/SignInScreenTest.kt` only when it changes.
Use the matching `git add -- <paths>` and `git commit -- <paths>` commands in the task document.
Exclude `docs/classic_navigation.md`, the listed unrelated files, and the interrupted 2C3 PhotoGrid/Search files.

## Limits

Live-server, real-redirect, API 29, RTL, TalkBack, font-scale, signed-release, and physical-device
checks remain unverified except for the dummy callback result described above.
