# Server Compatibility

Status: planned  
Owner: Protocol maintainers  
Last reviewed: 2026-09-13  
Stale when: A supported server family, capability, endpoint, or verification result changes.

Sources: `AGENTS.md`, `domain/`, `data/misskey/`, `data/mastodon/`, adapter tests, and capability code.

## Purpose

<!-- Explain supported Mastodon-compatible and Misskey-family behavior. -->

## Entries

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
