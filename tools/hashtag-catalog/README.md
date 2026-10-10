# Hashtag catalog research data

Status: reference  
Owner: Maintainers  
Last reviewed: 2026-10-10  
Stale when: A person reviews the catalog, or the scripts are wired to the repository.

## Origin

This directory holds the research behind `app/src/main/assets/hashtag-catalog.json`.
A crawl on 2026-10-10 produced the data. No person has reviewed it.
The cross-language links can be wrong. See `report.md` and the release gate in `docs/related-tags.md`.

## Files

| File | Content |
|---|---|
| `groups/*.yaml` | One file for each of the 74 topic groups. The source of the asset. |
| `seeds.txt` | The seed hashtags for the crawl. |
| `report.md` | The data report with counts and open decisions. |
| `tag-stats.jsonl` | Seven-day account and use counts for each hashtag. |
| `wikidata-labels.json` | Labels that the research used for cross-language links. |
| `observations.jsonl` | Raw observations from the crawl. |
| `scripts/` | The research scripts. |

## The app reads one file

The app reads only `app/src/main/assets/hashtag-catalog.json`. It never reads this directory.
The app does not use the network for the catalog. Updates ship with app releases.

## Scripts

The scripts are not wired to the repository.
They read paths such as `data/obs.jsonl` that this directory does not contain.
Do not run them as part of the build or a test.
