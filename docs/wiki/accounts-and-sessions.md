# Accounts and Sessions

Status: current  
Owner: Maintainers  
Last reviewed: 2026-09-20  
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

## Account removal

Removal stops foreground delivery first. It disables push and removes notification state next. The
lifecycle then invalidates capability and writer state before deleting account-scoped rows. It
persists the new account index before activating the next account.

## Limits

This page documents source and test behavior. Device and live-server behavior remain unverified.
