# Handoff

**Status:** `docs/agents/tasks/beeline-0.4.0.md` is the active durable task state.

## Current position

The Mastodon callback repair is implemented in:

- `app/src/main/java/me/foxtails/palustris/data/auth/MastodonAuth.kt`
- `app/src/main/java/me/foxtails/palustris/data/mastodon/MastodonErrorMapper.kt`
- `app/src/main/java/me/foxtails/palustris/ui/session/AccountManager.kt`

Focused tests are:

- `app/src/test/java/me/foxtails/palustris/data/auth/AuthGatewayTest.kt`
- `app/src/test/java/me/foxtails/palustris/data/mastodon/MastodonIntegrationTest.kt`
- `app/src/test/java/me/foxtails/palustris/data/misskey/MisskeyIntegrationTest.kt`
- `app/src/test/java/me/foxtails/palustris/data/transport/AuthenticatedHttpClientTest.kt`
- `app/src/test/java/me/foxtails/palustris/ui/session/SessionViewModelTest.kt`

No resource files changed. The duplicate test checks the code and pending state before completion.

The repair uses `/api/v1/accounts/verify_credentials`, root `/oauth/token`, `/api/v1/apps`, and
`/api/v2/instance`. Stage-aware authentication 404 errors remain distinct from unsupported
capabilities. Misskey behavior remains unchanged.

The welcome scaling slice is in commit `47dbc36`. It replaces the fixed `520.dp` logo height
with a shared box using aspect ratio `1.25`. Scroll content keeps status-bar and navigation-bar
padding, and the server form keeps IME padding. Buttons use `64.dp` height and `480.dp` maximum
width. Tests cover compact, large, and pending states.

## Verification

Sixteen session tests, three auth gateway tests, 59 Mastodon integration tests, and 48 Misskey
integration tests passed. The prior `lintDebug` run passed after the test change. The command
`python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check`
exited 0 with 611 findings and zero baseline regressions. Historical slice counts of 610, 611, 612,
and 613 occurred across worktree states. Treat them as unstable count-only measurements, not
regressions or proof. The related logs are ignored by `/logs/*.txt`: `logs/260928-oauth-callback.txt`
and `logs/260928-mastodon-callback-repair.txt`. The full gate remains unresolved because the known
baseline suite reports unrelated failures.

The device installed the debug APK and passed welcome and dummy callback dispatch checks.
It had no pending request, so it did not perform an exchange. A fresh user approval remains needed.

A portrait device check at `1848x2448` passed for the welcome layout. The card stayed below the
status bar, the logo stayed centered, and the buttons stayed above the navigation bar. Wide,
foldable, large-font, and landscape checks remain unverified. The code reviewer reported no
`BLOCKING` or `REQUIRED` findings.

The architecture audit reported 611 findings against a 610 baseline. Treat this as a count-only
measurement. The full gate timed out, and known unrelated baseline test failures remain unresolved.
OAuth commit `80bfd25` and the preserved `MainActivity` lines remain intact. The interrupted 2C3
PhotoGrid work remains interrupted.

## Next slice

Use a fresh device approval to verify the real Mastodon exchange and verify request.
Continue the interrupted 2C3 PhotoGrid work separately.

## Staging pathspec

Use the exact Mastodon pathspec in `docs/agents/tasks/beeline-0.4.0.md`.
Exclude welcome UI, 2C3 PhotoGrid and Search, `docs/classic_navigation.md`, and unrelated dirty files.
Do not stage or commit in this subagent session.
