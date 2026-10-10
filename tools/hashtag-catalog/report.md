# Hashtag catalog: data research report

Date: 2026-10-10. Repository: beeline `b8fc99c` (v0.2.13). The repo has no hashtag data yet. The implementation plan is `related-hashtags-implementation-plan.md`.

Version 2 of the data. It adds 12 groups to the first release (62 groups): furry, fursuit, furryart, microsoft, apple, google, digitalart, animation, indie, indiegames, indiefilm and indiemusic.

## Result

- 74 groups: the 34 topics you listed, 28 added topics, and the 12 batch-2 groups.
- 1831 unique hashtags checked: 1192 in active use (3+ accounts or 5+ uses in 7 days), 569 thin, 70 not seen on any probed server.
- Draft catalog has 1814 members. It leaves out the 70 unseen tags.
- Languages: en, fr, es, de, nl, ja, zh-TW. Brand and community tags use `und`.

## Batch 2: new groups

Relations follow the implementation plan. `h` head and `s` synonym are entry points. `n` narrower members merge when the head or a synonym is searched. `r` related members only appear as chips. `children` (key `c`) lists sub-niche groups. Their heads show as related suggestions. Ambiguous tags (`amb`) never merge.

| group | members | seen | thin | unseen | children |
|---|---|---|---|---|---|
| furry | 22 | 13 | 7 | 2 | fursuit, furryart |
| fursuit | 20 | 9 | 8 | 3 | - |
| furryart | 21 | 9 | 11 | 1 | - |
| microsoft | 45 | 37 | 8 | 0 | - |
| apple | 57 | 48 | 9 | 0 | - |
| google | 50 | 31 | 19 | 0 | - |
| digitalart | 56 | 17 | 34 | 5 | - |
| animation | 53 | 18 | 34 | 1 | - |
| indie | 29 | 13 | 13 | 3 | indiegames, indiefilm, indiemusic |
| indiegames | 40 | 17 | 20 | 3 | - |
| indiefilm | 38 | 5 | 30 | 3 | - |
| indiemusic | 38 | 13 | 21 | 4 | - |

Merge preview: the extras that a search for the head would send, using the ranking rule in the plan. Mastodon accepts 3 extras. Misskey-family servers accept 10.

| head | Mastodon (3) | Misskey family (10) |
|---|---|---|
| `furry` | `furries`, `ケモノ`, `furryart` | `furries`, `ケモノ`, `furryart`, `fursuit`, `anthro`, `furryfandom`, `fursona`, `furrycommunity`, `anthropomorphic`, `eurofurence` |
| `fursuit` | `fursuiter`, `fursuiting`, `fursuits` | `fursuiter`, `fursuiting`, `fursuits`, `fursuiters`, `fursuitfriday`, `fursuitphotography`, `fursuitmaker`, `fursuitmakers`, `fursuitmaking`, `fursuitbuilder` |
| `furryart` | `furryartist`, `ケモノイラスト`, `furryartwork` | `furryartist`, `ケモノイラスト`, `furryartwork`, `anthroart`, `furrydrawing`, `furryillustration`, `fursonaart`, `refsheet`, `furrycommissions`, `furrycomic` |
| `microsoft` | `windows11`, `azure`, `microsoft365` | `windows11`, `azure`, `microsoft365`, `vscode`, `onedrive`, `wsl`, `windows10`, `microsoftteams`, `windowsxp`, `powershell` |
| `apple` | `ios`, `macos`, `iphone` | `ios`, `macos`, `iphone`, `ipad`, `ios27`, `applewatch`, `appletv`, `appleintelligence`, `swiftui`, `macbookpro` |
| `google` | `android`, `googlepixel`, `googlemaps` | `android`, `googlepixel`, `googlemaps`, `googlegemini`, `chromebook`, `googleplay`, `androiddev`, `androidauto`, `chromium`, `googlesearch` |
| `digitalart` | `digitalpainting`, `artnumérique`, `デジタルアート` | `digitalpainting`, `artnumérique`, `デジタルアート`, `artedigital`, `digitalekunst`, `數位藝術`, `digitalillustration`, `digitalartist`, `artnumerique`, `デジタルイラスト` |
| `animation` | `animación`, `アニメーション`, `動畫` | `animación`, `アニメーション`, `動畫`, `trickfilm`, `animatie`, `animations`, `dessinanimé`, `animacion`, `animationsfilm`, `dessinsanimés` |
| `indie` | `indiedev`, `indiegames`, `indiemusic` | `indiedev`, `indiegames`, `indiemusic`, `bandcamp`, `indieapp`, `インディー`, `インディーズ`, `indiecreator`, `indiedevs`, `indiefilm` |
| `indiegames` | `indiedev`, `インディーゲーム`, `jeuindépendant` | `indiedev`, `インディーゲーム`, `jeuindépendant`, `videojuegosindie`, `獨立遊戲`, `indiespiele`, `indiegame`, `indiegamedev`, `solodev`, `jeuindependant` |
| `indiefilm` | `cinémaindépendant`, `independentcinema`, `cineindependiente` | `cinémaindépendant`, `independentcinema`, `cineindependiente`, `onafhankelijkefilm`, `インディペンデント映画`, `獨立電影`, `cinemaindependant`, `indiefilmmaker`, `cineindie`, `indiefilms` |
| `indiemusic` | `independentmusic`, `インディーズ`, `musiqueindépendante` | `independentmusic`, `インディーズ`, `musiqueindépendante`, `músicaindependiente`, `indiemusik`, `獨立音樂`, `indiemusicians`, `musiqueindependante`, `musicaindependiente`, `indiemusician` |

Notes:

- **furry.** `fursuit` and `furryart` are narrower members of `furry` and also child groups with their own tags. On Mastodon only 3 extras fit, so a `#furry` search sends `furries`, `ケモノ` and the highest-weight narrower tag. Misskey-family servers get all of them. NSFW tags are not in the data.
- **microsoft.** The product tags are narrower members. `windows`, `edge`, `teams`, `excel`, `outlook`, `copilot` and `surface` are generic words. They are related-only and ambiguous. A `#windows` search merges nothing.
- **apple.** `apple` is the requested head. It also means the fruit. I did not flag it ambiguous, because flagging it would stop `#apple` from merging anything. `mac`, `safari`, `swift` and `蘋果` are flagged. Version tags (`ios26`, `ios27`, `iphone17`, `macostahoe`) are included. They will go stale every year.
- **google.** `chrome`, `gemini` and `pixel` are related-only and ambiguous. Use `googlechrome`, `googlegemini` and `googlepixel` for merges. Google AI tags sit here, not in `ai`.
- **digitalart and animation.** Both overlap with `art` and `anime`. `anime` is related-only in `animation`. `動畫` is a synonym in `animation` and in `anime`.
- **indie.** `indiegames`, `indiefilm`, `indiemusic` and `bandcamp` are narrower members of `indie`, as you asked, and the first three are also child groups. `bandcamp` is narrower in `indiemusic` too. A search for `#bandcamp` alone merges nothing. It suggests `indiemusic`. `indieweb` is a different movement and is flagged.
- **indiefilm is thin.** The head has 2 accounts in 7 days on the probed servers. Most members have low use. The group is correct, but its data is weak.
- Overlaps with older groups: `indiegames` is also narrower in `gaming`, `indiemusic` is narrower in `music`, `bandcamp` is related in `music`, `digitalart` is narrower in `art`.

## Method

1. Wrote seed lists for each group and language from my own knowledge.
2. Cross-checked head terms against Wikidata labels in all 7 languages. This found about 50 missing translations in version 1. I added them.
3. Checked every tag on live servers with `GET /api/v1/tags/:name` (7-day accounts and uses) and `GET /api/v1/timelines/tag/:name` (40 posts, `language` field).
4. Re-checked thin and unseen tags on extra servers.

Servers (1 request per second each, no sign-in, aggregates only, no post text stored):

- English and `und`: mastodon.social, mstdn.ca, mstdn.social, mastodon.world, mastodon.online, mas.to, hachyderm.io, fosstodon.org, techhub.social, ohai.social, infosec.exchange, sunny.garden, universeodon.com, toot.community. Akkoma: pleroma.envs.net (timelines only; it blocks the tag endpoint).
- Batch 2 extras: furry.engineer and pawb.fun (furry), mastodon.art (digitalart, animation), mastodon.gamedev.place (indiegames).
- fr: piaille.fr, mamot.fr. es: tkz.one, masto.es. de: troet.cafe, mastodontech.de. nl: mastodon.nl. ja: mstdn.jp. zh-TW: g0v.social, o3o.ca.
- Probed but limited: mstdn.ca, mstdn.social, infosec.exchange, sunny.garden, g0v.social, meow.social, mastodon.art, tech.lgbt and masto.ai return 422 for tag timelines without sign-in, so only their tag counts were used. m.cmx.im and wxw.moe do the same and were not used. poa.st blocks both endpoints. mastodonners.nl timed out.

## Files

| File | Use |
|---|---|
| `groups/<id>.yaml` | One file per group. Source of truth. Each member has relation, languages, status, 7-day accounts. |
| `hashtag-catalog-draft.json` | Compact build output in the format from the curation plan. Excludes unseen tags. The only file the app reads. |
| `tag-stats.jsonl` | Per-tag stats: accounts, uses, post language counts, hosts. |
| `seeds.txt` | Plain-text seed lists that generated the YAML. |
| `observations.jsonl` | Raw per-server counts. |
| `wikidata-labels.json` | Wikidata label cross-check. |
| `scripts/` | Collection, build, and validation scripts. Research tooling only. |

## Coverage (members per group and language)

Counts are for the draft catalog (unseen tags excluded). Members marked `und` count under en.

| group | total | en | fr | es | de | nl | ja | zh-TW |
|---|---|---|---|---|---|---|---|---|
| technology | 36 | 13 | 5 | 7 | 4 | 4 | 4 | 4 |
| foss | 27 | 11 | 4 | 3 | 2 | 1 | 3 | 4 |
| infosec | 50 | 21 | 6 | 4 | 5 | 4 | 6 | 6 |
| ai | 42 | 18 | 5 | 5 | 4 | 2 | 6 | 6 |
| noai | 25 | 16 | 2 | 2 | 2 | 1 | 3 | 1 |
| cats | 43 | 13 | 4 | 4 | 7 | 5 | 6 | 5 |
| reptiles | 46 | 22 | 5 | 3 | 4 | 4 | 5 | 6 |
| dogs | 30 | 9 | 4 | 3 | 5 | 3 | 5 | 3 |
| birds | 36 | 15 | 3 | 4 | 5 | 3 | 5 | 4 |
| art | 43 | 13 | 5 | 6 | 4 | 4 | 6 | 7 |
| artist | 27 | 9 | 2 | 2 | 3 | 2 | 4 | 5 |
| photography | 42 | 17 | 4 | 6 | 6 | 3 | 7 | 4 |
| music | 41 | 18 | 4 | 4 | 4 | 3 | 5 | 5 |
| tv | 29 | 8 | 6 | 5 | 5 | 2 | 4 | 4 |
| gaming | 41 | 18 | 5 | 3 | 5 | 3 | 3 | 5 |
| retro | 30 | 16 | 3 | 2 | 2 | 1 | 4 | 4 |
| movies | 25 | 12 | 4 | 4 | 2 | 1 | 3 | 2 |
| politics | 33 | 13 | 4 | 3 | 4 | 3 | 3 | 4 |
| climatechange | 36 | 13 | 5 | 5 | 4 | 3 | 3 | 3 |
| cars | 35 | 14 | 4 | 5 | 5 | 3 | 4 | 2 |
| trains | 36 | 14 | 4 | 3 | 4 | 3 | 5 | 5 |
| publictransit | 37 | 13 | 4 | 4 | 6 | 2 | 5 | 4 |
| fediverse | 20 | 10 | 3 | 1 | 1 | 1 | 3 | 2 |
| writing | 35 | 16 | 5 | 3 | 3 | 3 | 2 | 3 |
| blog | 14 | 7 | 2 | 1 | 1 | 1 | 2 | 2 |
| article | 13 | 6 | 2 | 3 | 1 | 2 | 1 | 1 |
| newspaper | 17 | 6 | 3 | 2 | 2 | 2 | 1 | 1 |
| academics | 39 | 16 | 5 | 3 | 5 | 3 | 6 | 4 |
| sports | 7 | 2 | 2 | 2 | 1 | 1 | 1 | 2 |
| basketball | 16 | 7 | 1 | 3 | 1 | 1 | 3 | 1 |
| baseball | 14 | 4 | 1 | 2 | 1 | 1 | 3 | 4 |
| football | 17 | 10 | 2 | 2 | 1 | 1 | 3 | 0 |
| futbol | 20 | 6 | 2 | 3 | 3 | 2 | 2 | 2 |
| eurovision | 13 | 4 | 1 | 4 | 1 | 2 | 1 | 0 |
| linux | 11 | 11 | 1 | 1 | 1 | 0 | 1 | 1 |
| programming | 23 | 11 | 2 | 3 | 2 | 1 | 1 | 3 |
| selfhosting | 12 | 7 | 2 | 1 | 1 | 0 | 1 | 1 |
| privacy | 12 | 4 | 2 | 1 | 2 | 1 | 1 | 2 |
| science | 13 | 6 | 2 | 1 | 1 | 1 | 2 | 1 |
| space | 20 | 9 | 2 | 3 | 3 | 2 | 2 | 2 |
| news | 13 | 5 | 3 | 1 | 1 | 1 | 1 | 1 |
| books | 25 | 8 | 3 | 3 | 3 | 3 | 2 | 3 |
| history | 10 | 4 | 1 | 1 | 1 | 1 | 1 | 1 |
| food | 16 | 5 | 2 | 1 | 2 | 2 | 3 | 2 |
| cooking | 25 | 7 | 3 | 3 | 3 | 3 | 3 | 4 |
| gardening | 18 | 6 | 2 | 3 | 2 | 2 | 2 | 1 |
| travel | 17 | 6 | 2 | 2 | 2 | 2 | 2 | 2 |
| nature | 8 | 4 | 1 | 1 | 1 | 1 | 1 | 1 |
| anime | 8 | 4 | 2 | 1 | 1 | 1 | 1 | 2 |
| manga | 5 | 2 | 1 | 1 | 1 | 0 | 2 | 1 |
| boardgames | 13 | 4 | 2 | 1 | 2 | 2 | 1 | 1 |
| ttrpg | 16 | 6 | 2 | 2 | 2 | 1 | 2 | 1 |
| cycling | 21 | 8 | 3 | 2 | 2 | 2 | 2 | 2 |
| running | 9 | 3 | 1 | 1 | 1 | 1 | 1 | 1 |
| hiking | 13 | 5 | 2 | 1 | 1 | 1 | 2 | 2 |
| tennis | 4 | 1 | 1 | 1 | 1 | 1 | 1 | 1 |
| hockey | 9 | 3 | 2 | 1 | 1 | 1 | 2 | 1 |
| cricket | 4 | 2 | 0 | 0 | 0 | 0 | 1 | 1 |
| rugby | 4 | 3 | 1 | 1 | 1 | 1 | 1 | 0 |
| golf | 2 | 1 | 1 | 1 | 1 | 1 | 1 | 0 |
| motorsport | 13 | 9 | 1 | 1 | 1 | 1 | 2 | 1 |
| olympics | 11 | 3 | 1 | 2 | 2 | 1 | 1 | 1 |
| furry | 20 | 16 | 0 | 0 | 0 | 0 | 3 | 1 |
| fursuit | 17 | 16 | 0 | 0 | 0 | 0 | 1 | 0 |
| furryart | 20 | 17 | 0 | 0 | 0 | 0 | 3 | 0 |
| microsoft | 45 | 43 | 0 | 0 | 0 | 0 | 1 | 1 |
| apple | 57 | 55 | 0 | 0 | 0 | 0 | 1 | 1 |
| google | 50 | 48 | 0 | 0 | 0 | 0 | 1 | 1 |
| digitalart | 51 | 24 | 8 | 4 | 3 | 1 | 7 | 5 |
| animation | 52 | 28 | 6 | 4 | 3 | 3 | 5 | 5 |
| indie | 26 | 21 | 0 | 0 | 0 | 0 | 3 | 2 |
| indiegames | 37 | 19 | 4 | 3 | 2 | 0 | 5 | 4 |
| indiefilm | 35 | 16 | 5 | 3 | 1 | 2 | 5 | 3 |
| indiemusic | 34 | 23 | 3 | 4 | 1 | 0 | 2 | 1 |

## Findings

1. **Post `language` is unreliable.** Many accounts keep the default. `いぬ`, `ゲーム`, `バスケ` and `野球` show 85 to 100% `en` on mstdn.jp. I did not use it to assign languages. Seed languages stand. I used the field only to flag a different language holding 60% or more of non-English posts. 30 tags are flagged (`observed_other_lang` in the YAML).
2. **zh-TW is the weakest language.** There is little Taiwanese presence on servers that allow open reads. 30 of 207 zh-TW seeds are unseen and 144 more are thin. They are likely valid tags with low use. They need a Taiwanese server with open tag reads, or a sign-in crawl.
3. **Han tags are shared between ja and zh-TW.** `政治`, `技術`, `音楽`/`音樂` style tags carry both languages. Check by script, not by server.
4. **`#ai` and `#noai` do not overlap.** Verified by script. `aislop` and `aiwashing` sit in `noai` as related only. In the data, `ia` (283 accounts, posts in fr and es) and `ki` (367 accounts, posts all in de) are AI tags.
5. **Football.** `football` is the head of the American football group as requested. It contains nfl, superbowl, collegefootball and others. It excludes `soccer`, `futbol`, `fußball`, `voetbal` and `サッカー`. French `#football` means soccer, so I left it out of `futbol`. A search for `#football` still returns what servers tag `#football`, including soccer. The exclusion applies only to merged members.
6. **`#futbol` is Spanish.** In the data, 20 of 40 non-null posts are `es`. I changed its language from en to es. `soccer` is the English synonym.
7. **Homographs flagged `ambiguous: true`:** art, cricket, esc, ev, film, football, gecko, ia, ki, mastodon, retro, security, turtle, vintage, plus the batch-2 flags above. `birdsite` means Twitter and must stay out of `birds`. `fotograf`, `fotos`, `videospel` and `chemindefer` are shared with sv or cs.
8. **Overlaps.** 49 tags sit in more than one group. In every case one side is related-only, or both sides are the same concept (`cuisine`, `baking`, `recipes`, `新聞`).
9. **Merge size.** Mastodon accepts 3 extra hashtags per query (the implementation plan measured this). Largest merge sets (head, synonyms, narrower): apple 50, reptiles 46, infosec 45, digitalart 45, cats 43, art 43, animation 43, ai 42. The app ranks by weight and trims. Weight is `log2(1 + accounts7)`.

## Gaps

- `football`: no member for zh-TW.
- `eurovision`: no member for zh-TW.
- `linux`: no member for nl.
- `selfhosting`: no member for nl.
- `manga`: no member for nl.
- `cricket`: no member for fr, es, de, nl.
- `rugby`: no member for zh-TW.
- `golf`: no member for zh-TW.
- `furry`: no member for fr, es, de, nl.
- `fursuit`: no member for fr, es, de, nl, zh-TW.
- `furryart`: no member for fr, es, de, nl, zh-TW.
- `microsoft`: no member for fr, es, de, nl.
- `apple`: no member for fr, es, de, nl.
- `google`: no member for fr, es, de, nl.
- `indie`: no member for fr, es, de, nl.
- `indiegames`: no member for nl.
- `indiemusic`: no member for nl.
- Cricket has no fr, es, de or nl member because the sport is rare in those languages. This is expected.
- Eurovision tags with a year (`eurovision2026`) follow a pattern. A regex member would cover them. Not added.

## Not seen on any probed server (excluded from the draft catalog)

`3c`, `sécuritédelinformation`, `seguridaddelainformación`, `kigenerativ`, `noaiartist`, `noaiscrape`, `無断学習禁止`, `ai学習反対`, `生成ai反対`, `ai絵反対`, `学習禁止`, `拒絕ai`, `禁止ai學習`, `反生成式ai`, `chatsdumastodon`, `浪貓`, `gatodoméstico`, `serpientes`, `lagartos`, `爬蟲類`, `cachorro`, `毛孩`, `汪星人`, `observacióndeaves`, `相片`, `コンピュータゲーム`, `戲院`, `愛車`, `transportenommun`, `periódico`, `periodico`, `學界`, `博士班`, `職籃`, `美式足球`, `超級盃`, `歐洲歌唱大賽`, `ユーロビジョンソングコンテスト`, `alimento`, `圖版遊戲`, `桌上角色扮演`, `hockeysobrehielo`, `críquet`, `橄欖球`, `高爾夫`, `獸圈`, `獸迷`, `fursuitbuild`, `獸裝`, `獸裝製作`, `獸人插畫`, `pinturadigital`, `digitalmalerei`, `digitaletekening`, `digitaleillustratie`, `digitaalkunst`, `定格動畫`, `indiecommunity`, `indieprojects`, `indiereleases`, `jeuxvidéoindépendants`, `juegosindependientes`, `indiespellen`, `microbudgetfilm`, `indiefeature`, `微電影`, `indiemuziek`, `インディーズ音楽`, `獨立樂團`, `獨立音樂人`

## Decisions needed

1. **Reviewer.** Every cross-language synonym and every narrower link still needs a person to approve it. I did not do this review. Status in the YAML is data-based only.
2. **Taiwan sources.** Do you know a Taiwanese server with open tag reads, or should I use a signed-in crawl?
3. **`#football` head.** Keep it as the head of American football (current), or rename the group head to `nfl` and keep `football` as a synonym only?
4. **Year patterns.** Add regex members for yearly event tags such as Eurovision? The same applies to `ios27`, `iphone18` and `pixel10`.
5. **Added topics.** Keep all 28 added groups, or limit the first release to your list?
6. **`#apple`.** Keep it unflagged (merges, but a fruit post can match), or flag it and give up the merge?
