# Pixelfed Representative Fixtures

Owner: Mastodon adapter maintainers.

Status: current.

Last reviewed: 2026-10-14.

Stale when: the Mastodon paging validator, the runtime demotion table, or the
Pixelfed route set changes.

These are synthetic representative-shape fixtures assembled by hand for Planorma
Slice 8. They are not live captures from a Pixelfed instance: no live server was
available, so shapes follow the Pixelfed API behavior described in
`docs/pleroma-pixelfed.md` and `docs/planorma.md`. Do not describe them as
verified live captures. All origins, accounts, and posts are synthetic. No real
user data, credential, or complete API response appears here.

| Fixture | Source | Capture date | Coverage |
| --- | --- | --- | --- |
| `instance.json` | Hand-assembled from the Pixelfed `/api/v2/instance` shape | 2026-10-14 (synthetic) | Compatibility version marker only. Configuration and upload-limit shapes stay in their existing fixtures; they are not duplicated here. |
| `home.json` | Hand-assembled from the Mastodon-compatible status shape Pixelfed serves | 2026-10-14 (synthetic) | Two home-timeline photo posts for the paging page flow. |
| `link.txt` | Hand-assembled Pixelfed home-timeline `Link` header | 2026-10-14 (synthetic) | Extra query params (`limit`, `only_media`) beside the cursor. The paging relax test proves these parse after Slice 8. |
| `notifications.json` | Hand-assembled from the Mastodon-compatible notification shape Pixelfed serves | 2026-10-14 (synthetic) | One mention and one follow. |
| `media.json` | Hand-assembled from the Mastodon-compatible media attachment shape Pixelfed serves | 2026-10-14 (synthetic) | One processed photo attachment for the upload contract test. |

Pixelfed 404 envelopes for the missing routes (`notifications/unread_count`,
per-notification `dismiss`, v1 push subscription routes, the v1 streaming route,
and v1 reports) use the Mastodon-API error shape `{"error":"Record not found"}`.
That representative envelope is asserted inline by the runtime demotion tests;
the Slice 6 gates prove the client never issues those requests once probed.

Live-unverified: the exact extra params a real Pixelfed `Link` header repeats,
the 202 media-polling behavior (X11), the `push` scope acceptance at app
registration, and the `scheduled_at` immediate-publish risk (X6) all need a live
instance.
