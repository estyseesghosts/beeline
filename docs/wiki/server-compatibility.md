# Server Compatibility

Status: planned  
Owner: Protocol maintainers  
Last reviewed: 2026-09-13  
Stale when: A supported server family, capability, endpoint, or verification result changes.

Sources: `AGENTS.md`, `domain/`, `data/misskey/`, `data/mastodon/`, adapter tests, and capability code.

## Purpose

<!-- Explain supported Mastodon-compatible and Misskey-family behavior. -->

## Entries

### Mastodon moderation paging

Source: `data/mastodon/MastodonModerationService.kt`,
`data/mastodon/MastodonSource.kt`, `ModerationServiceTest`,
`MastodonIntegrationTest`, and `ModerationViewModelTest`.

- Blocked and muted cursors bind account, kind, variant, route, session
  revision, source instance, and URL in a version-2 payload. Same-account
  replay on another source or revision fails before any request. Legacy
  version-1 and raw-URL cursors fail safely.
- The first-page self-Link check uses the exact initial request URL. A
  self-Link to the first page cannot become a continuation.
- A failed next-page load keeps current rows. Live-server moderation paging
  remains unverified.

### Mastodon report validation

Source: `data/mastodon/MastodonModerationService.kt` and `ModerationServiceTest`.

- `report` validates its target with `validateTarget` before it constructs
  fields. A same-origin Misskey target or a blank target ID fails as
  unsupported without a request. A foreign target keeps the foreign-origin
  error. The optional status ID keeps same-origin validation and rejects a
  blank value without a request.
- Valid account-only and account-plus-status reports keep the exact form
  fields. Reserved characters stay form-encoded. Live-server report delivery
  remains unverified.

<!-- Add the dated support matrix, capability states, protocol differences, and known unverified cases. -->
