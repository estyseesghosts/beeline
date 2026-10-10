# Accounts and Sessions

Status: current  
Owner: Maintainers  
Last reviewed: 2026-10-09  
Sources: `SessionLifecycle.kt`, `AccountManager.kt`, and session tests.

## Ownership

`SessionLifecycle` owns durable account lifecycle work. It restores sessions, persists login and
profile changes, switches accounts, removes account-scoped data, registers sources, and activates
push, direct-message, and draft writers.

`AccountManager` owns presentation state. It owns authentication callbacks, browser state, user
intents, `SessionUi`, and failure-to-string mapping. It publishes one connected context per accepted
lifecycle activation.

## Session identity

The durable session revision changes when a token replaces an existing session. The presentation
generation changes when the ViewModel accepts an activation. Notification registry generations and
writer generations remain separate identities.

`SocialSourceFactory` requires the session store. A created source is current only while the
store holds its session revision. A missing, removed, or replaced session fails the check, so a
late Misskey probe result cannot pass the factory-owned gate. The revision read is not atomic
with adapter publication, so this is a gate rather than a transactional guarantee. Refreshed
capabilities persist through
`SessionStore.updateCapabilities` with the source revision. A late callback cannot overwrite a
replacement session. `SourceFactoryTest` covers routing and authority for both protocols. The
revision guard behind the callbacks is covered at the store level. Callback firing needs a live
probe and stays unverified in unit tests.

Push source selection stays bound to its notification token. A stale token miss ends as a
no-op and never escalates to the account registry entry or a transient factory source.
Token-null removal cleanup uses the stored session snapshot only while that snapshot still
matches the store.

## Account removal

Removal stops foreground delivery first. It disables push and removes notification state next. The
lifecycle then invalidates capability and writer state before deleting account-scoped rows. It
persists the new account index before activating the next account.

## Media upload access

`AccessScope.MediaUpload` records whether the token can upload media. A new Misskey sign-in requests
`write:drive` and records it as `Granted`. A Mastodon sign-in records `Granted` for the `write` or
`write:media` scope. `AccessGrant.mediaUploadStatus` reads a missing record as `Denied`, so every
Misskey account saved before this scope must sign in again before it can attach media. A Mastodon
session saved earlier that holds the `write` scope gains the record when it loads. `effectiveMediaUpload`
combines this access with `posting.mediaUpload` from the server capabilities.

## Limits

This page documents source and test behavior. Device and live-server behavior remain unverified.
