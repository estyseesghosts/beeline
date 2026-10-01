# Protocol And Session Ownership

**Owner:** Protocol, session, and persistence maintainers.

**Status:** current. Source verified. Slice 2A4 completes the authentication and dependency
injection migration started in slice 2A3.

**Last reviewed:** 2026-10-01.

**Source baseline:** Slice 16: `SessionLifecycle.kt` owns durable session transitions, and
`AccountManager.kt` owns session presentation and authentication UI state.

**Evidence:** source verified. Slice 16 focused session, connected-session, and notification tests
passed, as did `compileDebugKotlin` and the unit-test compile during implementation.
Device and live-server behavior remain unverified.

**Completion owner:** `docs/archive/agents/decomposition-01-02-completion.md`.

## Source Ownership

- `domain/SocialSource.kt` is the only transport contract. Each adapter implements it.
- `data/transport/HttpClientPool.kt` owns credential-free HTTP client reuse, timeouts, redirects,
  connection keying, and bounded retention for both protocol adapters.
- `data/transport/HttpResponse.kt` owns the generic HTTP body, headers, and Link cursor parsing.
- `data/transport/AuthenticatedHttpClient.kt` owns authenticated HTTP execution, origin checks,
  bounded reads, cancellation, WebSocket requests, and multipart streaming. It stores no token.
- `data/misskey/MisskeySource` and `data/mastodon/MastodonSource` are the two adapters.
- `data/transport/AuthenticatedHttpClient` owns protocol-neutral authenticated execution, bounded
  bodies, cancellation, origin checks, and status failures. Adapters own paths and failure mapping.
- `data/misskey/MisskeyApi` remains the Misskey JSON adapter. It owns the `/api/` prefix, maps
  Misskey error bodies, and delegates request execution to `AuthenticatedHttpClient`.
- `data/auth/MisskeyAuth` uses the neutral client with explicit `/api/` routes. `MastodonAuth` uses
  the neutral client and never constructs or accepts `MisskeyApi`.
- `di/AppModule` probes Misskey through the neutral client. `SocialSourceFactory` creates the
  Misskey adapter or Mastodon neutral client from the session connection protocol.
- `data/SourceFactory.kt` creates one source for a session. It branches on `Protocol` only there.
- `SocialSourceFactory` requires a `SessionStore`. It has no null-store path.
  `isCurrentSession(session)` compares the stored session revision with the
  source session revision. A missing, removed, or replaced session fails.
- Misskey sources receive that check as `isCurrentSession`. A late probe result
  cannot pass the factory-owned gate after session replacement or removal. The
  revision read is not atomic with adapter publication, so this is a gate
  rather than a transactional guarantee.
- Both adapters persist refreshed capabilities through
  `SessionStore.updateCapabilities` with the source session revision. A late
  callback cannot overwrite a replacement session.
- `data/AccountSourceRegistry.kt` stores one source for each `AccountId` together with a
  generation from `NotificationSyncToken`.
- `sourceFor(token)` returns null when the generation is stale. `sourceFor(accountId)` ignores
  the generation. `isCurrent` compares identity.
- `NotificationSyncController.register` registers a source and returns the `NotificationSyncToken`
  that owns it.
- `NotificationSyncOrchestrator.unregister` revokes synchronously with the exact generation.
  It calls `NotificationRepository.deactivate` for that token. It never advances the tombstone
  past the removed generation.
- `NotificationSyncOrchestrator.removeAccount` runs `unregister`, then `repository.remove`,
  then a trailing agreement check. Durable deletion runs outside the controller lock.
- `NotificationSyncOrchestrator.accept` requires an active controller entry that matches
  the token. The repository token alone never authorizes delivery.
- A replacement activates only with a strictly newer generation than the tombstone.
  Stale activation never materializes state.
- `SessionLifecycle` owns restoration, durable session transitions, source registration, push
  activation, account cleanup, and direct-message and draft writer generations.
- `AccountManager` owns authentication-screen state and publishes one `ConnectedSessionContext`
  for each accepted activation returned by `SessionLifecycle`.
- `AccountManager` receives only authentication, lifecycle, and UI-string dependencies. Unit tests
  use the test-only `AccountManagerFixtures.kt` factory with explicit lifecycle dependencies.
- Unsupported operations throw `SourceError.Unsupported` through the `unsupported` helper.

## Identity Invariants

Keep these three identities separate. Never substitute one for another.

| Identity | Owner | Changes when |
| --- | --- | --- |
| `sessionGeneration` | `AccountManager` | A session connects. It is a runtime presentation generation. |
| `sessionRevision` | `Session` | A session is replaced. It is a durable revision. |
| Registry generation | `NotificationSyncToken` | A source is registered. |

A stale callback must fail the generation or revision check. Do not treat the numbers as
interchangeable.

### Connected Identity

`SessionLifecycle` creates the source and registers it for notification sync. `AccountManager` then
publishes one `ConnectedSessionContext` for the accepted activation. The context joins the account
identity, visible account, durable revision, presentation generation, and registered source.

`ConnectedSessionHost` reads `connectedContext.source`. The unregistered
`sourceFactory.create(session)` fallback is removed. `SessionLifecycle` owns source construction.

`ui/session/ConnectedSessionContext.kt` owns the context type. `ConnectedSessionContextTest` covers
account switching, same-account replacement, profile updates, and retired registrations.

## Error Contract

`domain/SourceError.kt` defines the shared failure types:

- `Unauthorized` and `AccountMismatch` protect the session.
- `Unsupported` means the adapter does not implement the feature.
- `AccessDenied` and `UnsupportedCredential` mean the grant is insufficient.
- `ServerUnsupported` means the server rejects the feature.
- `RateLimited`, `ResourceLimit`, `NetworkUnavailable`, and `ServerError` are transient or bounded.
- `ForeignOrigin` protects authenticated requests against another origin.

Keep unknown, denied, and unsupported states separate. Do not mark a feature unsupported after one
failed request.

Data-layer owners resolve user text through `data/AppMessages`. It is the Context-backed sibling
of `ui.UiStrings`, so a transport, storage, or authentication owner keeps no user-facing English
and no `Context` in its signature. `ui/SourceErrorMessage.kt` resolves a source failure and its
feature identifier to a localized message. `timeline:*` uses the timeline label, `audience:*` uses
the audience label, and an unknown identifier uses a generic phrase, so a raw protocol code never
reaches the user.

## Origin Validation

`domain/Connection.kt` requires an HTTPS origin. `isValid()` rejects credentials, paths, queries,
and fragments. Validate the origin before an authenticated request. Validate pagination origins and
entity origins before account actions.

All Mastodon page routes (timeline, bookmarks, and hashtags) use adapter-owned opaque cursors in
`data/mastodon/MastodonPageCursor.kt`. They bind origin, account, session revision, source instance,
protocol variant, route, and query. The page client validates server Link URLs before it returns a
cursor and validates decoded cursors before authenticated replay. Timeline validation runs before
the capability probe. Bookmark and hashtag paths perform no capability or network work before the
page client validates a continuation. Legacy raw-URL cursors are rejected on every route. Owners:
`MastodonPageCursor.kt`, `MastodonPageClient.kt`, `MastodonTimelineService.kt`, and
`MastodonSource.kt`; regression coverage: `MastodonIntegrationTest`.

Mastodon moderation cursors use an adapter-owned Base64url payload in `MastodonModerationService`.
Blocked and muted routes bind account, kind, variant, route, and URL. Their continuations allow one
nonblank `max_id`, `since_id`, or `min_id`, plus `limit=40` when present. Link URLs are checked
against the exact route and query policy before becoming cursors. The cursor exists only in UI
memory and has no persisted-format migration. `MastodonSource` performs no I/O before moderation
cursor validation. `ModerationServiceTest` covers replay and Link rejection. Unlike
`MastodonPageCursor`, this payload does not bind `sessionRevision` or `sourceInstance`. The
ModerationViewModel keeps cursors in memory, and paging checks `AccountSourceRegistry.isCurrent`;
a direct `SocialSource` caller can replay a same-account cursor after source replacement. Session
and source-instance binding was consciously deferred because of the in-memory lifetime and the
current-account guard.

`MisskeySource` validates every entity identity through `validatePostId(id, feature)` before it
builds an authenticated request. The validator rejects a foreign connection origin and a blank
value. `post` and `delete` now call it, as do thread reads, reactions, favourites, reposts, saves,
repost undo, create reply origins, and create quote identities. A foreign public URL on a locally
fetched entity remains valid.

## Capability States

`domain/ServerCapabilities.kt` defines `CapabilityStatus`: `Supported`, `Denied`, `Unsupported`,
`TemporarilyUnavailable`, and `Unknown`. `effectiveCapabilityStatus` resolves the usable state from
the server status, the access status, and the implementation status. Keep `Unknown` separate from
`Unsupported`.

`SocialSource.observeCapabilities()` publishes a protocol-neutral capability snapshot. The default
returns the current snapshot once. `MisskeySource` and `MastodonSource` return their capability
state flow. `EmojiHost` collects that flow and passes `EmojiCapabilities` to `EmojiPresentation`, so
a refreshed probe can update reaction controls without a catalog or navigation change.

Mastodon reaction support comes from the recognized extension advertisement. `MastodonSource.react`
and `removeReaction` do not downgrade support on a resource failure. A resource 404, 403, 429,
network failure, or 5xx returns the normalized `SourceError` and keeps the advertised support.
`MastodonCapabilityProbe` owns the advertisement rules and the bounded metadata read. It first reads
instance metadata. When instance metadata has no recognized advertisement, it inspects
`/.well-known/nodeinfo` and fetches at most one same-origin NodeInfo document. Discovery URLs must
be same-origin with the validated connection origin, carry no credential, and carry no fragment.
The reads follow no redirect and are bounded. Every discovery failure stays Unknown; it is not
Unsupported evidence. `MastodonAuth` uses `probeCapabilities`, so login applies the same rules.

`ServerCapabilities.CURRENT_CAPABILITY_SCHEMA_VERSION` is `5`. Revision 5 invalidates snapshots
that recorded reaction support from the removed sentinel mutation probe. `refreshCapabilities`
re-probes a snapshot whose schema revision is not current.

A successful refresh publishes the snapshot through `onCapabilitiesUpdated`. `SocialSourceFactory`
wires that callback to `SessionStore.updateCapabilities` with the source `sessionRevision`.
`updateCapabilities` compares the stored session revision inside its transaction and writes nothing
when the revision differs. A stale source therefore cannot overwrite a replaced session.

`MastodonSource` bounds capability refresh retries. After a metadata failure it records
`capabilitiesRetryNotBefore = now + 30 seconds`. A request inside that window reuses the existing
capability evidence instead of re-probing. A successful probe clears the window.

## Persistence Contracts

| Data | Location | Protection |
| --- | --- | --- |
| Account sessions | `noBackupFilesDir/accounts` | AES-GCM with one Android Keystore key. |
| Pending authentication | `noBackupFilesDir/accounts/pending.enc` | Encrypted. Expires after 15 minutes. |
| Drafts | `noBackupFilesDir/drafts` | Same encrypted account storage key as sessions. |
| Notifications | Room `notifications.db` | Explicit migrations. |
| Direct messages | Room `directmessages.db` in `noBackupFilesDir` | Explicit migrations. Schema version 2. |
| Emoji catalog | `EmojiCacheDatabase` | Cache. |
| Preferences | File-backed repositories | Application, post, emoji-picker, and Photo Grid stores. |

Account identity in storage is the connection origin plus the local ID. The protocol is metadata.

The notification stored format is frozen by `NotificationJsonCodecTest` and the literal
fixtures in `app/src/test/resources/notifications/`. Fixture provenance is recorded in
`app/src/test/resources/notifications/PROVENANCE.md`. The fixtures are synthetic
characterization fixtures, not captured released files. Do not generate the expected
fixture content with the encoder under test.

`NotificationJsonCodec.kt` owns the state boundary and every recursive encode and decode
helper. `NotificationRepository.kt` keeps the merge, generation, query-validation, and
delivery-claim behavior. It keeps no JSON conversion helper. Both `FileNotificationStore` and
`RoomNotificationStore` use the same internal `encode` and `decode` boundary.

`AccountId.stableFileName` lives in `NotificationStore.kt`. Its SHA-256 input is the
compatibility contract for file names, Room keys, and import markers. `stableNotificationId`
lives in `NotificationPageMerge.kt` beside the delivery outbox. Its hash input is the
compatibility contract for outbox tags and replacement. Neither helper belongs in the codec,
and neither merges with `AndroidNotificationIds` without a separate identity migration.

Every persisted post carries `contentVisibility`, including nested quotes. A missing or
unknown value decodes to `Hidden`. Old blobs predate the field and cannot prove their
visibility, so their cached bodies stay withheld until an authenticated refresh replaces
them. The format stays at version 2: the field is additive, and old fixtures remain
readable. `NotificationDeliveryPlanner` prepares an Android preview only for `Visible`
public posts. `NotificationRow` never renders the body of a `Hidden` post.

`NotificationStore.read` returns `NotificationStoreRead`. The variants are `Absent`,
`Readable`, `Corrupt`, `Unsupported`, and `Unavailable`. A corrupt read never carries stored
bytes. A malformed row is corrupt, not unavailable. A database or disk failure is unavailable.
A version above `CURRENT_NOTIFICATION_STATE_VERSION` is unsupported, not corrupt.
`NotificationRepositoryState.hasReceivingAccount` validates the receiving-account fields
before a read becomes Readable. Only receiving-account fields participate. Remote actors,
post authors, and public URLs do not.

`NotificationRepository.observeStorageHealth` publishes one `NotificationStorageHealth` value
for each account. The variants are `Healthy`, `Recoverable`, `Unsupported`, and `Unavailable`.
Health stays outside the stored version-2 payload. A non-healthy account blocks page ingestion,
baseline replacement, local mutations, delivery claims and finishing, settings writes, and push
registration writes. `pendingDeliveries` and `pushRegistration` return nothing for a blocked
account. `NotificationRepository.retry` re-reads storage. It replaces the empty in-memory state
only after a readable load and leaves the original bytes untouched on another failure. A healthy
account returns immediately, so a normal refresh never replaces committed state. A retry cannot
clear an unsupported future format, because the format stays newer. The inbox surfaces the
failure through `NotificationsUiState.storageUnavailable`, and the settings presentation
surfaces it through `NotificationSettingsUiState.storageUnavailable` so it never shows
unconfirmed defaults.

`NotificationRepository.reset` is the approved destructive recovery. It advances the account
generation first, writes an empty readable state, clears the in-memory state, and sets health
`Healthy`. Writing the empty state prevents legacy reimport, clears stored settings, dismissals,
and delivery history, and lets the next page run as a baseline without alerts. Only
notification-local state changes. Authentication secrets and other accounts are untouched. The
approved policy is development-only discard: do not migrate old notification data.

`NotificationDatabase` exports its schema to `app/schemas`. Reproducible schema history exists
before any future migration. The registered `1` to `2` migration stays defensive because no
version-1 Room schema was ever released. Do not build a version-1 schema from the current
annotations.

`NotificationRepository` commits every notification-local mutation through durable acceptance.
Each transition computes from committed state, writes to the store on the injected IO
dispatcher, and publishes only after the write succeeds while the writer is still current.
A failed write marks the account `Unavailable`, publishes nothing, and returns failure, so no
failed operation is ever described as durably completed. Transitions for one account serialize
on a per-account lock; unrelated accounts never wait on each other. Cancellation propagates
without marking health. A failed delivery claim returns null, so the worker never presents it.
A failed dismissal stays retryable and never becomes server acknowledgement. A remote
acknowledgement followed by a local write failure returns failure without repeating the remote
call; the next sync reconciles from server state after storage retry. Row deletion during
`remove` is best effort: revocation and memory removal are authoritative, so a disk failure
never blocks local removal. `NotificationsViewModelTest` injects the test dispatcher into the
repository so committed state stays deterministic under `runTest`.

## Direct-Message Write Authority

`data/directmessages/DirectMessageWriteAuthority.kt` owns one writer generation for each account.

- `activate` returns the generation for a new writer.
- `isCurrent` compares a generation with the current value.
- `commitIfCurrent` runs a block under the account lock when the generation is current.
- `invalidate` revokes writers without deleting rows.
- `invalidateAndDelete` revokes writers and deletes rows in one serialized boundary.

One writer authority serves one account lifecycle owner. `DirectMessageViewModel` receives the
Hilt singleton as a required constructor argument and passes that same instance to
`DirectMessageRepository`. Neither declares a private authority. The assisted factory injects the
singleton, so the ViewModel, the repository, and `AccountManager` share the instance that account
removal invalidates. Slice 04-E1 removed the private defaults.

`SessionLifecycle.remove` stops delivery before cleanup. It revokes each writer before deleting
its rows, then commits the session index. A later activation receives new writer generations.

C-03 closed the direct-message gap. `DirectMessageRepository.markRead` routes its local write
through `commitIfCurrent` (commit `bfbd7ed`). Activation, revocation, deletion, and accepted writes
share one per-account boundary under `DirectMessageWriteAuthority`.

C-06c closed the draft gap. `DraftWriteAuthority` mirrors the direct-message authority.
`DraftActions` captures the writer generation and routes save and delete through `commitIfCurrent`.
A revoked writer writes nothing. Removal revokes the draft writer before it deletes rows
(commit `4454bae`). Only account lifecycle or explicit test setup may issue authority.
`DraftActions` never auto-activates a writer. Test fixtures create one authority,
call `activate` for the test account, and pass that generation to the action owner.
The fixture scope ends with the test. Another account cannot use a captured writer.

## Direct-Message Thread Anchor

`DirectMessageSource.conversationThread` takes
`DirectThreadRequest(conversationId, anchor)` and an optional opaque cursor. It
returns `DirectThreadResult(posts, nextCursor, limitations, acquisitionState)`.
The conversation identity and the post anchor are
separate identity spaces. The repository fills the anchor from the account-scoped stored
conversation `lastPost.id`. It throws `SourceError.Unsupported("direct.thread")` when no stored
conversation exists. The repository merges remote posts with the cached thread
and returns the continuation, the limitations, and the state. The ViewModel owns the open-thread cursor, limitations, continuing flag, and a thread error separate from inbox and send errors. The conversation screen owns the trailing footer. Selection, start, and close reset the thread fields; stop cancels thread and read jobs and advances selection authority, but does not reset state fields.

The Misskey adapter validates a continuation before any request. The cursor
binds version, variant, origin, account, session revision, source instance,
conversation, accepted descendant IDs, cumulative limitations, the chain
request count, and pending breadth-first work. A bad cursor throws
`SourceError.Unsupported("direct.thread.continuation")` with zero requests. A
fresh call loads the reply root, walks at most 20 ancestors with a cycle guard,
then runs a bounded breadth-first descent of at most 3 `notes/children`
requests. At most 40 authenticated thread requests run across the whole chain,
including the root and ancestor reads. The fresh call queues the root at depth 1. Each mapped direct child
is enqueued at depth plus one, up to a fixed depth limit of 10. Accepted IDs
travel in the cursor so overlapping pages never count twice, and carried
limitations keep a terminal call limited after earlier truncation. At most
200 accepted descendants are kept; the cap records `NodeLimit` with no
continuation. The bounded breadth-first frontier holds at most 200 pending
parents; a child that would exceed the frontier records `PendingLimit` with
the actual frontier size, clears pending work, and ends the batch with no
continuation and no parent re-queue. A drained terminal call with any carried
limitation reports `Limited`. Skipped depth
work records `DepthLimit`. A page that cannot advance records
`UncertainServerTruncation`. Source semantics follow the existing Misskey
`untilId` implementation. Live Misskey/Sharkey ordering is unverified. Only
structural absence or denied support becomes `UnavailableParent`. Cancellation
and the response limit propagate and never become limitations. Other child
failures throw normalized errors, so the repository keeps prior rows on a
failed call. A continuation call resumes the queued breadth-first work with
dedup seeded by the conversation root and the carried accepted IDs, so a
cyclic or hostile root row is never reaccepted. The conversation footer shows spinner, retry, load-more, and static partial states. A rejected continuation cursor clears so retry falls back to fresh. A finished Mastodon result shows no extra control.

The Mastodon adapter loads the anchor through `GET /api/v1/statuses/:id` and its context through
`/context`. It never calls `GET /api/v1/conversations/:id`. It normalizes 403, 404, and 410 to the
same unsupported error and rejects a non-direct anchor. It returns the result
as finished with no cursor and adds no continuation. The Misskey adapter keeps
`conversationId.value` as the reply root and does not adopt Mastodon conversation identity.

Slice 04-E2 removed the adapter `directLastPosts` map and the send-path insertion. The adapter
retains no conversation post cache. The repository owns account-scoped conversation state.

## Direct-Message Conversation Identity

`domain/DirectMessageModels.kt` defines `ConversationIdentity`. `Verified` means the server issued
the identity in a conversation response. `Provisional` means the client built a local placeholder
from a sent post value because no server conversation was known. Do not infer identity from the
identifier string shape.

- The Mastodon mapper and the Misskey conversation service emit `Verified`, because the server
  supplied the conversation identity.
- `DirectMessageRepository.send` marks a conversation `Provisional` only when it builds the
  identifier from the sent post value. It keeps the stored identity when the identifier names an
  existing conversation.
- `DirectMessageRepository.markRead` resolves the stored identity. It calls
  `source.markConversationRead` only for a `Verified` conversation. A provisional conversation
  clears local unread state and sends no server request. A server mark-read never uses a guessed
  conversation identity.
- `DirectConversationEntity` stores `identity TEXT NOT NULL DEFAULT 'PROVISIONAL'`.
  `DirectMessageDatabase` is version 2 and exports its schema to `app/schemas`. `MIGRATION_1_2`
  adds the column. A legacy row decodes as `Provisional` because the stored value cannot prove a
  server identity. The next conversation list rewrites the row as `Verified`. An unrecognized
  stored value also decodes as `Provisional`, so a decode failure never widens server write
  authority.

## Account Removal Coverage

`AccountManager.removeAccount` stops the notification stream, disables push, removes sync, removes
post preferences, removes Photo Grid preferences, revokes direct-message writers and deletes the
direct-message store, deletes drafts, removes the emoji catalog and picker preferences, deletes
the session, and updates the account index.

The account-removal draft gap from an earlier review is closed. `AccountManager.removeAccount`
revokes the draft writer and deletes rows in one serialized boundary (`AccountManager.kt:336-338`).
The stale claim in older ownership and bug records is removed.

## Affected Tests

- `SocialSourceContractTest`, `MastodonSourceContractTest`, `ProfileSourceContractTest`
- `SessionViewModelTest`, `ConnectedSessionContextTest`, `AuthGatewayTest`
- `DirectMessageRepositoryTest`, `DirectMessageViewModelTest`
- `NotificationRepositoryTest`, `NotificationAdapterContractTest`, `NotificationSynchronizerTest`
- `SourceFactoryTest` covers source routing and session authority. It verifies
  Misskey and Mastodon creation, current-session acceptance, and missing,
  removed, and replaced session rejection. The revision guard behind the
  capability callbacks is covered at the store level by `CrossCuttingTest`.
  Callback firing needs a live probe and stays unverified in unit tests.
- `NotificationSyncOrchestratorTest` covers generation fencing, removal revocation, late-event
  rejection, poll cancellation, and gated in-flight-write removal. A cancelled removal still
  revokes the old token.
- `NotificationStorageRecoveryTest` covers the recoverable health, the write block, the healthy
  second account, the retry reload, and the healthy-retry no-op.
- `NotificationWriteFailureTest` covers failing writes, deletes, delivery claims, dismissals,
  acknowledgement, concurrent mutations, and revocation during persistence.
- `PushRegistrationRepositoryTest`, `PushCancellationTest`
- `AccountSourceRegistry` behavior is exercised through the notification and session tests.

## Verification Limits

- The registry, error, and write-authority behavior are JVM tested. Live-server behavior is
  unverified.
- Completion slice C-01 added `ConnectedSessionContextTest`. It passed with the focused run and the
  full `test assembleRelease` gate.
- Completion slice C-03 added the direct-message write-authority tests. C-06c added the draft
  removal and late-write tests.
- A Room-backed removal and late-write instrumentation test is not written. Device behavior stays
  unverified.
