# Beeline 0.4.0 task state — OAuth diagnostics continuation

## Objective

Close the OAuth diagnostics test gaps without weakening callback validation or changing protocol
behavior. Keep the interrupted Photo Grid work intact.

## Status sets

- Source status: OAuth dispatch categories, callback rejection categories, and the Mastodon missing
  callback message are implemented.
- Test status: the invalid callback matrix now covers scheme, host, path, missing, duplicate, and
  wrong state, blank and duplicate code, and expired pending state. Session tests cover deferred
  callbacks and ignored callbacks without pending state.
- MainActivity test status: unverified. The available unit-test harness cannot inject Hilt fields or
  observe the private dispatch method. This blocks direct coverage for saved-null and saved-non-null
  `onCreate`, `onNewIntent`, and notification-versus-auth fallback routing.
- Protocol status: Misskey matching remains unchanged. Mastodon keeps the existing redirect URI,
  strict state matching, single code requirement, and fifteen-minute lifetime.
- Audit status: count-only mismatch remains unexplained. The OAuth log reports 613 findings against
  a 610 baseline. It reports zero OAuth-attributable regressions; the listed regression lines are
  unchanged baseline files.
- Full gate status: unresolved. The full gate timed out at 120 seconds after known baseline test
  failures.
- Device status: dummy callback delivery through `onNewIntent` passed. The real redirect did not
  arrive. The stuck guidance UI was not in the accessibility tree because it uses a canvas. Screenshots
  remain blocked by tool policy.
- Live-server status: unverified.

## Exact OAuth commit pathspec

Stage only these files for the OAuth slice:

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

Add `app/src/test/java/me/foxtails/palustris/ui/SignInScreenTest.kt` only if this slice changes it.
Use the same explicit pathspec for staging and committing:

```text
git add -- app/src/main/java/me/foxtails/palustris/MainActivity.kt app/src/main/java/me/foxtails/palustris/ui/session/AccountManager.kt app/src/main/java/me/foxtails/palustris/data/auth/MastodonAuth.kt app/src/main/java/me/foxtails/palustris/ui/setup/SetupScreens.kt app/src/main/java/me/foxtails/palustris/ui/UiStrings.kt app/src/main/res/values/strings.xml app/src/test/java/me/foxtails/palustris/data/auth/AuthGatewayTest.kt app/src/test/java/me/foxtails/palustris/ui/session/SessionViewModelTest.kt docs/agents/tasks/beeline-0.4.0.md docs/agents/handoff.md
git commit -- app/src/main/java/me/foxtails/palustris/MainActivity.kt app/src/main/java/me/foxtails/palustris/ui/session/AccountManager.kt app/src/main/java/me/foxtails/palustris/data/auth/MastodonAuth.kt app/src/main/java/me/foxtails/palustris/ui/setup/SetupScreens.kt app/src/main/java/me/foxtails/palustris/ui/UiStrings.kt app/src/main/res/values/strings.xml app/src/test/java/me/foxtails/palustris/data/auth/AuthGatewayTest.kt app/src/test/java/me/foxtails/palustris/ui/session/SessionViewModelTest.kt docs/agents/tasks/beeline-0.4.0.md docs/agents/handoff.md
```

If `SignInScreenTest.kt` changes, append it to both commands.

Exclude the staged `docs/classic_navigation.md`. Exclude unrelated files: `.opencode/agents/code_reviewer_high.md`, `.opencode/agents/code_reviewer_low.md`, `.opencode/agents/git_handler.md`, `.opencode/agents/orchestrator.md`, `.opencode/agents/problem_solver_high.md`, `.opencode/agents/problem_solver_low.md`, `.opencode/agents/targeted_fixer.md`, `docs/agents/app-shell-ownership.md`, `importantdocs/writing_style.md`, `currentbehaviour.png`, `intendedbehaviour.png`, `.opencode/agents/adb_handler.md`, `.opencode/agents/codebase_explorer_android.md`, `auth_stuck.png`, `screen.png`, `screen_after_dummy.png`, `screen_pending.png`, `tools/scripts/adb_control.py`, `tools/scripts/adb_flow.py`, `tools/scripts/adb_inspect.py`, `tools/scripts/adb_screenshot.py`, `tools/scripts/__pycache__/`, `tools/tests/__pycache__/`, and `logs/*`.

Exclude the interrupted 2C3 PhotoGrid/Search files: `app/src/main/java/me/foxtails/palustris/ui/ConnectedApp.kt`, `app/src/main/java/me/foxtails/palustris/ui/feed/FeedHost.kt`, `app/src/main/java/me/foxtails/palustris/ui/feed/FeedViewModel.kt`, `app/src/main/java/me/foxtails/palustris/ui/session/ConnectedSessionHost.kt`, `app/src/test/java/me/foxtails/palustris/ui/photogrid/PhotoGridFeedViewModelTest.kt`, `app/src/main/java/me/foxtails/palustris/ui/photogrid/PhotoGridHost.kt`, `app/src/main/java/me/foxtails/palustris/ui/photogrid/PhotoGridOwner.kt`, `app/src/main/java/me/foxtails/palustris/ui/search/SearchScreen.kt`, `app/src/main/java/me/foxtails/palustris/ui/search/SearchOwner.kt`, `app/src/main/java/me/foxtails/palustris/ui/search/SearchHost.kt`, `app/src/main/java/me/foxtails/palustris/ui/search/SearchController.kt`, `app/src/main/java/me/foxtails/palustris/ui/search/AccountSearchState.kt`, `app/src/test/java/me/foxtails/palustris/ui/search/SearchOwnerTest.kt`, and `app/src/test/java/me/foxtails/palustris/ui/search/SearchPanelRestorationTest.kt`.

## Verification record

- Focused auth and session tests passed: 3 `AuthGatewayTest` tests and 15 `SessionViewModelTest`
  tests. The existing SignInScreen test also passed before this continuation.
- The OAuth audit log is `logs/260928-oauth-callback.txt`. It records 613 findings, baseline 610,
  and a count delta of three. It does not explain the count delta.
- The audit command was `python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check`.
  The command exit code was 0. The log records no OAuth-attributable regression.
- The full Gradle gate command was `test assembleRelease`. It timed out at 120 seconds after
  `assembleRelease` completed. Known baseline failures remained in unrelated tests.
- The MainActivity dispatch harness was not available in the unit-test harness. Instrumentation can
  cover this dispatch later. This gap does not block the OAuth commit.

## Boundaries

There is no redirect URI change, validation weakening, auto-polling, or pasted-code flow. No Misskey
adapter change is allowed in this slice.
