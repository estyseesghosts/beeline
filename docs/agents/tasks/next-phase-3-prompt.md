# Next-agent prompt — start Phase 3 (Slice 3.1)

This is a self-contained dispatch for the next agent. Follow it in order.

## Current state

- `main` is at `2cc4d7a` — `Share notification filter-row presentation`.
- Phase 0, Phase 1, and Phase 2 are complete, committed, and gate-verified.
- The debug APK is installed on the running emulator (`emulator-5554`, `Pixel_10_Pro_Fold(AVD) - 17`).
- Do not push. Do not begin Phase 4 or Phase 5.

## Required reading (before any edit)

1. `AGENTS.md` and every page it links as required.
2. `docs/agents/workflow.md`, `docs/agents/agent-control.md`, `docs/agents/engineering-rules.md`,
   `docs/agents/operation-rules.md`, `docs/agents/documentation-rules.md`.
3. `docs/fix_0.4.0.md` — read the project-wide invariants (section 2), the verification contract
   (section 3), and Phase 3 (section "Phase 3 — Finish the Misskey source/service boundary").
4. `docs/agents/handoff.md` and `docs/agents/tasks/hardening-0.4.0-phase2.md` for the completed
   Phase 2 record and the last safe commit.
5. `importantdocs/writing_style.md` before writing any document.

## Objective — Slice 3.1: Extract Misskey thread acquisition into a service

`MisskeySource` is mostly a protocol facade that delegates to feature services. Thread acquisition
is the major exception: BFS state, request budgeting, and continuation mechanics are embedded in
the facade. Create the missing feature service.

### New file

`app/src/main/java/me/foxtails/palustris/data/misskey/MisskeyThreadService.kt`

Move into it from `MisskeySource`:

- fresh thread acquisition (`beginThreadAcquisition`);
- ancestor traversal;
- descendant breadth-first traversal (`acquireDescendants`);
- request budgeting (`reserveRequest`, `enqueue`);
- batch/time/depth/node limits;
- continuation validation/consumption (`continuationState`);
- thread-specific error normalization (`normalizeThreadError`);
- continuation creation (the `UUID` token + `continuationStore.insert` + `ThreadContinuation` build);
- the thread constants that constrain the behavior (`MAX_DESCENDANTS`, `MAX_ANCESTORS`,
  `MAX_DESCENDANT_DEPTH`, `MAX_BATCH_REQUESTS`, `MAX_REQUESTS`, `MAX_BATCH_TIME_MILLIS`,
  `CHILDREN_PAGE_LIMIT`, `MAX_THREAD_RESPONSE_BYTES`).

### `MisskeySource.kt`

- Construct one `MisskeyThreadService` for the source lifetime, alongside the other feature services.
- Replace the body of `threadContext` with delegation through the existing `request("thread")`
  error boundary. `MisskeySource` keeps `validatePostId(focalId, "thread")` and the
  `ThreadSessionKey`/`fetchingAccount` identity if the service does not already own them.
- `MisskeySource` continues owning: shared request normalization, capability refresh, adapter
  facade, and feature service construction.
- `MisskeySource` no longer owns BFS state or thread continuation mechanics.
- Remove the now-unused imports and the `continuationStore` field if the service fully owns it.

### `MisskeyThreadAcquisition.kt`

Keep the current acquisition/work DTOs (`ChildWork`, `ThreadAcquisition`) unless the service can make
any of them private without harming tests. Do not combine them with unrelated domain models.

### `MisskeyThreadContinuationStore.kt`

The new service becomes its natural lifetime owner. The store remains bounded:

- 16 entries;
- ten-minute idle expiry;
- consume-on-use semantics.

Do not weaken these bounds.

### Construction

`MisskeySource` constructs one `MisskeyThreadService` for the source lifetime. Inputs should be
explicit: origin, token/API, account identity, session revision, clocks where required, response
limit, and the current post loader or validation mechanism. Do not introduce a service locator.

## Invariants (do not change)

- No externally observable `SocialSource.threadContext` behavior changes.
- No persisted-format migration, no dependency additions.
- Protocol branches remain inside adapters and source construction.
- Account/session generations, revisions, and write authorities remain unchanged.
- Do not weaken or replace existing regression tests.
- Do not split `ShellContent`, `NotificationRepository`, `NotificationJsonCodec`,
  `DirectMessageViewModel`, `MisskeyDirectMessageService`, `SocialSource`, or `SvgIconPaths`.

## Tests

- `MisskeyThreadContinuationTest`
- `MisskeyIntegrationTest`
- `SocialSourceContractTest`

The continuation tests must continue proving: source/session binding; malformed cursor rejection;
stale continuation rejection; request budgets; descendant limits; resumed BFS behavior; and no
authenticated request before invalid-continuation rejection.

## Documentation

Update:

- `docs/agents/protocol-and-session-ownership.md`
- `docs/wiki/architecture.md`

Document `MisskeyThreadService` as the thread transport/acquisition owner.

## ktlint

`MisskeySource.kt` currently carries six baseline items (import ordering, unused imports, spacing).
Because this slice touches that file, fix those and remove its `app/ktlint-baseline.xml` entry, per
the project ktlint ratchet rule. Do not add new exemptions. Do not reformat unrelated files.

## Verification contract (run in order)

### Focused gate

```text
.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests "me.foxtails.palustris.data.misskey.MisskeyThreadContinuationTest" --tests "me.foxtails.palustris.data.misskey.MisskeyIntegrationTest" --tests "me.foxtails.palustris.data.misskey.SocialSourceContractTest"
```

### Local CI-parity completion gate

```text
python -m unittest discover -s tools/tests

python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check

.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug :app:ktlintCheck :app:assembleDebug :app:assembleRelease
```

Use the Windows wrapper. Use an explicit timeout and closed standard input. Known flakes:
`MastodonIntegrationTest` cancellation and `NotificationsViewModelTest` test-isolation. They pass
alone; record them and rerun the gate if only a known flake fails.

## Commit

Commit the slice separately with its tests and documentation. Use explicit reviewed file paths.
Inside the commit message, name the preceding safe commit (`2cc4d7a`) and the slice subject.

Commit subject: `Extract Misskey thread service`.

## Deliverables

- `MisskeySource` becomes a clear adapter facade instead of a facade plus one embedded feature
  implementation.
- `MisskeyThreadService` owns thread transport/acquisition and the continuation store lifetime.
- No new ktlint baseline debt; the `MisskeySource.kt` baseline entry is removed.
- All focused tests and the full CI-parity gate pass.
