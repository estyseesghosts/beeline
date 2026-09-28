# Handoff

**Status:** `docs/agents/tasks/beeline-0.4.0.md` is the active durable task state.

## Current position

Slice 2C2 gives Search an explicit connected `SearchOwner` and `SearchHost`. Search registers one
projection sink and one teardown key for each connected session. `FeedViewModel` retains Home and
Photo Grid only.

No staging, commit, or push occurred.

## Verification

Focused compilation and unit suites passed. `SearchOwnerTest` passed 8 tests. The combined Search,
Feed request, session, projection, and Search restoration selection passed 41 tests. `:app:lintDebug`
passed. The full gate reached release packaging, then timed out at 120 seconds with known DraftActions,
CapabilityCache, Misskey thread, and notification failures.

## Next slice

The next planned packet is 2C3: give Photo Grid its explicit connected lifetime. Keep Photo Grid
preferences, selection, and paging independent from Home.

## Staging boundary

Use the exact pathspec in `docs/agents/tasks/beeline-0.4.0.md`. Exclude all pre-existing dirty files,
including `.opencode/agents`, images, helper scripts, `importantdocs/writing_style.md`, and Python
cache directories. Keep ignored `logs/*` outside staging.

## Limits

Live, device, API 29, RTL, TalkBack, font-scale, and signed-release checks remain unverified. Known
baseline failures and the full-gate timeout remain unresolved.
