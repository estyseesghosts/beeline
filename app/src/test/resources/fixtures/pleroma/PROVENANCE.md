# Pleroma Representative Fixtures

Owner: Mastodon adapter maintainers.

Status: current.

Last reviewed: 2026-10-14.

Stale when: the Mastodon paging validator, the runtime demotion table, or the
PleromaCreative route set changes.

These are synthetic representative-shape fixtures assembled by hand for Planorma
Slice 8. They are not live captures from a Pleroma instance: no live server was
available, so shapes follow the Pleroma API behavior described in
`docs/pleroma-pixelfed.md` and `docs/planorma.md`. Do not describe them as
verified live captures. All origins, accounts, and posts are synthetic. No real
user data, credential, or complete API response appears here.

| Fixture | Source | Capture date | Coverage |
| --- | --- | --- | --- |
| `instance.json` | Hand-assembled from the Pleroma `/api/v2/instance` shape | 2026-10-14 (synthetic) | Compatibility version marker and feature advertisements only. Reaction and posting-limit shapes stay in their existing fixtures; they are not duplicated here. |
| `home.json` | Hand-assembled from the Mastodon-compatible status shape Pleroma serves | 2026-10-14 (synthetic) | Two home-timeline statuses for the paging page flow. |
| `link.txt` | Hand-assembled Pleroma home-timeline `Link` header | 2026-10-14 (synthetic) | Extra query params (`limit`, `only_media`) beside the cursor. The paging relax test proves these parse after Slice 8. |
| `notifications.json` | Hand-assembled from the Mastodon-compatible notification shape Pleroma serves | 2026-10-14 (synthetic) | One mention and one follow. Reaction notification shapes stay in their existing fixtures; they are not duplicated here. |

Live-unverified: the exact extra params a real Pleroma `Link` header repeats, and
the streaming header-vs-query auth question (P8), both need a live instance.
