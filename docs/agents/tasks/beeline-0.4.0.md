# Beeline 0.4.0 task state — slice 2A4

## Objective

Remove the remaining Misskey-owned authentication and dependency-injection bridges. Keep protocol
routes, error mapping, token handling, source identity, capabilities, and session revisions unchanged.

## Status

- Source status: implemented and compile verified.
- Focused status: authentication, Misskey integration, API, and pool tests passed.
- Audit status: command exited 0 with 612 findings and zero regression lines. The 2A3 baseline had
  613 findings. The ignored audit record preserves only the count, so it cannot identify the removed
  finding row.
- Device status: unverified.
- Live-server status: unverified.
- Release status: `assembleRelease` passed. The combined `test assembleRelease` command completed
  release assembly but failed 16 unrelated full-suite tests.

## Changed files

- `app/src/main/java/me/foxtails/palustris/data/auth/MastodonAuth.kt`
- `app/src/main/java/me/foxtails/palustris/data/auth/MisskeyAuth.kt`
- `app/src/main/java/me/foxtails/palustris/di/AppModule.kt`
- `app/src/main/java/me/foxtails/palustris/data/SourceFactory.kt`
- `app/src/main/java/me/foxtails/palustris/data/misskey/MisskeyApi.kt`
- `app/src/test/java/me/foxtails/palustris/data/misskey/MisskeyIntegrationTest.kt`
- `docs/agents/protocol-and-session-ownership.md`
- `docs/agents/tasks/beeline-0.4.0.md`
- `docs/agents/handoff.md`

The current working diff contains five production files, one test file, and three documentation
files. No sixth production file appears in `git status` or `git diff`.

Exact production pathspec:

```text
app/src/main/java/me/foxtails/palustris/data/SourceFactory.kt
app/src/main/java/me/foxtails/palustris/data/auth/MastodonAuth.kt
app/src/main/java/me/foxtails/palustris/data/auth/MisskeyAuth.kt
app/src/main/java/me/foxtails/palustris/data/misskey/MisskeyApi.kt
app/src/main/java/me/foxtails/palustris/di/AppModule.kt
```

Exact test pathspec:

```text
app/src/test/java/me/foxtails/palustris/data/misskey/MisskeyIntegrationTest.kt
```

Exact documentation pathspec:

```text
docs/agents/protocol-and-session-ownership.md
docs/agents/tasks/beeline-0.4.0.md
docs/agents/handoff.md
```

`MastodonAuth` keeps the neutral and pooled constructors. `MisskeyAuth` now accepts the neutral
client and adds its own `/api/` route prefix. `MisskeyApi` keeps Misskey mapping and prefix ownership.

## Verification

- `:app:compileDebugKotlin`: passed.
- `:app:compileDebugUnitTestKotlin`: passed.
- Focused auth, Misskey integration, API, and pool tests: passed.
- `:app:lintDebug`: passed.
- Adapter contracts, WebSocket transport, and session/source revision tests: passed.
- Full `test assembleRelease`: failed 16 tests. Release assembly passed. Test-result XML files are
  unavailable in this workspace, so the following recorded names are not XML-verified and remain
  unattributed to this slice. A textual comparison with the 16 names in `logs/BUGS.txt` is not a
  substitute for XML evidence:
  - `me.foxtails.palustris.data.auth.DraftActionsTest.saveFailureReportsError`
  - `me.foxtails.palustris.data.auth.DraftActionsTest.deleteFailureStillCompletesAndReports`
  - `me.foxtails.palustris.data.misskey.CapabilityCacheTest.oldSourceReadAfterReplacementMissesAndPublishesNothing`
  - `me.foxtails.palustris.data.misskey.CapabilityCacheTest.lateProbeAfterRemoveAndReAddWithSameRevisionCannotPublish`
  - `me.foxtails.palustris.data.misskey.MisskeyThreadContinuationTest.sourceRejectsForeignAccountAndFocalWithoutNetworkOrConsumption`
  - `me.foxtails.palustris.data.misskey.MisskeyThreadContinuationTest.sourceContinuationTokenIsOpaqueUuidAndSingleUse`
  - `me.foxtails.palustris.data.misskey.MisskeyThreadContinuationTest.sourceReleasesContinuationStoreBeforeNetworkWork`
  - `me.foxtails.palustris.data.misskey.MisskeyThreadContinuationTest.sourcePreservesTransportOrderAcrossContinuations`
  - `me.foxtails.palustris.data.misskey.MisskeyThreadContinuationTest.sourceValidatesSessionBeforeConsumingContinuation`
  - `me.foxtails.palustris.data.notifications.NotificationSyncOrchestratorTest.reAddRejectsOldTokenAndAcceptsReplacement`
  - `me.foxtails.palustris.data.notifications.NotificationSyncOrchestratorTest.productionRemovalRejectsLateEventsAndDropsGenerationEntry`
  - `me.foxtails.palustris.data.notifications.NotificationSyncOrchestratorTest.generationsStayMonotonicAcrossRepeatedRemoval`
  - `me.foxtails.palustris.data.notifications.NotificationSyncOrchestratorTest.unregisterRejectsLateStreamEventsAndRemovesActiveEntry`
  - `me.foxtails.palustris.data.notifications.NotificationSyncOrchestratorTest.removalRightAfterPublishCancelsPollWithoutOrphan`
  - `me.foxtails.palustris.ui.navigation.NavigationTest.closingComposerAutosavesUnsavedText`
  - `me.foxtails.palustris.ui.navigation.NavigationTest.draftsSurviveActivityRecreationAndCanBeDeleted`
- The two known cancellation flakes passed when run alone. Mastodon teardown reported
  `java.io.IOException: Gave up waiting for queue to shut down` from `MockWebServer.shutdown`;
  Misskey reported `java.lang.AssertionError` at `MisskeyApiTest.kt:140` from
  `assertTrue(canceledCall.get())`. The standalone reruns passed.
- Audit record: `logs/architecture-audit-2a4.txt`, ignored and not staged. It records 612 findings
  and zero regressions.

## Staging boundary

Do not stage, commit, or push in this continuation. The slice files are currently UNSTAGED
worktree changes. The index currently holds only the unrelated staged `docs/classic_navigation.md`.
If `git_handler` stages this slice, it must use the explicit pathspec below: the five production
files, one test file, and three documentation files listed above. It must exclude staged
`docs/classic_navigation.md` by using that pathspec for the commit; do not reset the index.

Preserve all unrelated dirty work. Exclude modified `.opencode/agents/*`, `importantdocs/writing_style.md`,
the deleted screenshots, `docs/classic_navigation.md`, helper scripts, Python caches, and `screen.png`.

`git diff --check` reports whitespace in unrelated `.opencode` files and
`docs/classic_navigation.md`. The slice paths are clean when checked explicitly with
`git diff --check -- <slice paths>` using the nine paths above.

## Limits

Live-server, physical-device, API 29 physical, RTL, TalkBack, font-scale, signed-release, and
credentialed authentication callback checks remain unverified.
