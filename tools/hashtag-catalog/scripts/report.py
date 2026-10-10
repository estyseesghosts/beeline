import json, collections, re
from parse_seeds import parse

cat = json.load(open("out/hashtag-catalog-draft.json", encoding="utf-8"))
st = {}
for l in open("out/tag-stats.jsonl", encoding="utf-8"):
    r = json.loads(l); st[r["tag"]] = r
G = {g["id"]: g for g in cat["groups"]}
seeds = parse("seeds.txt")
L = ["en", "fr", "es", "de", "nl", "ja", "zh-TW"]
REQ = ["technology","foss","infosec","ai","noai","cats","reptiles","dogs","birds","art","artist","photography","music","tv","gaming","retro","movies","politics","climatechange","cars","trains","publictransit","fediverse","writing","blog","article","newspaper","academics","sports","basketball","baseball","football","futbol","eurovision"]
B2 = ["furry","fursuit","furryart","microsoft","apple","google","digitalart","animation","indie","indiegames","indiefilm","indiemusic"]
EXT = [g for g in G if g not in REQ and g not in B2]
c = collections.Counter(s["status"] for s in st.values())
totmem = sum(len(g["m"]) for g in cat["groups"])


def cell(gid, l):
    return sum(1 for m in G[gid]["m"] if l in m[1] or (l == "en" and "und" in m[1]))


def preview(gid, limit):
    g = G[gid]
    amb = set(g.get("amb", []))
    mem = [m for m in g["m"] if m[2] in "hsn" and m[0] not in amb]
    head = next(m for m in g["m"] if m[2] == "h")[0]
    mem = [m for m in mem if m[0] != head]
    s = sorted([m for m in mem if m[2] == "s"], key=lambda m: -m[3])
    n = sorted([m for m in mem if m[2] == "n"], key=lambda m: -m[3])
    out, seen_lang = [], set()
    for m in s:
        if m[1][0] not in seen_lang:
            seen_lang.add(m[1][0]); out.append(m)
    out += [m for m in s if m not in out]
    out += n
    return [m[0] for m in out[:limit]]


overl = collections.defaultdict(list)
for g in cat["groups"]:
    for m in g["m"]:
        overl[m[0]].append(g["id"] + ":" + m[2])
overl = {t: v for t, v in overl.items() if len(v) > 1}
sizes = sorted(((sum(1 for m in g["m"] if m[2] in "hsn"), gid) for gid, g in G.items()), reverse=True)
zt = [s for s in st.values() if "zh-TW" in s["seed_langs"]]
zu = [s["tag"] for s in zt if s["status"] == "unseen"]
zl = [s["tag"] for s in zt if s["status"] == "low"]
mism = json.load(open("data/mismatch.json", encoding="utf-8"))["mismatch"]

R = []
A = R.append
A("# Hashtag catalog: data research report")
A("")
A("Date: 2026-10-10. Repository: beeline `b8fc99c` (v0.2.13). The repo has no hashtag data yet. The implementation plan is `related-hashtags-implementation-plan.md`.")
A("")
A("Version 2 of the data. It adds 12 groups to the first release (62 groups): furry, fursuit, furryart, microsoft, apple, google, digitalart, animation, indie, indiegames, indiefilm and indiemusic.")
A("")
A("## Result")
A("")
A(f"- {len(G)} groups: the 34 topics you listed, {len(EXT)} added topics, and the {len(B2)} batch-2 groups.")
A(f"- {len(st)} unique hashtags checked: {c['seen']} in active use (3+ accounts or 5+ uses in 7 days), {c['low']} thin, {c['unseen']} not seen on any probed server.")
A(f"- Draft catalog has {totmem} members. It leaves out the {c['unseen']} unseen tags.")
A("- Languages: en, fr, es, de, nl, ja, zh-TW. Brand and community tags use `und`.")
A("")
A("## Batch 2: new groups")
A("")
A("Relations follow the implementation plan. `h` head and `s` synonym are entry points. `n` narrower members merge when the head or a synonym is searched. `r` related members only appear as chips. `children` (key `c`) lists sub-niche groups. Their heads show as related suggestions. Ambiguous tags (`amb`) never merge.")
A("")
A("| group | members | seen | thin | unseen | children |")
A("|---|---|---|---|---|---|")
for gid in B2:
    y = open(f"out/groups/{gid}.yaml", encoding="utf-8").read()
    sts = re.findall(r"    status: (\w+)", y)
    cc = collections.Counter(sts)
    A(f"| {gid} | {len(sts)} | {cc['seen']} | {cc['low']} | {cc['unseen']} | {', '.join(G[gid].get('c', [])) or '-'} |")
A("")
A("Merge preview: the extras that a search for the head would send, using the ranking rule in the plan. Mastodon accepts 3 extras. Misskey-family servers accept 10.")
A("")
A("| head | Mastodon (3) | Misskey family (10) |")
A("|---|---|---|")
for gid in B2:
    A(f"| `{gid}` | {', '.join('`'+t+'`' for t in preview(gid, 3))} | {', '.join('`'+t+'`' for t in preview(gid, 10))} |")
A("")
A("Notes:")
A("")
A("- **furry.** `fursuit` and `furryart` are narrower members of `furry` and also child groups with their own tags. On Mastodon only 3 extras fit, so a `#furry` search sends `furries`, `ケモノ` and the highest-weight narrower tag. Misskey-family servers get all of them. NSFW tags are not in the data.")
A("- **microsoft.** The product tags are narrower members. `windows`, `edge`, `teams`, `excel`, `outlook`, `copilot` and `surface` are generic words. They are related-only and ambiguous. A `#windows` search merges nothing.")
A("- **apple.** `apple` is the requested head. It also means the fruit. I did not flag it ambiguous, because flagging it would stop `#apple` from merging anything. `mac`, `safari`, `swift` and `蘋果` are flagged. Version tags (`ios26`, `ios27`, `iphone17`, `macostahoe`) are included. They will go stale every year.")
A("- **google.** `chrome`, `gemini` and `pixel` are related-only and ambiguous. Use `googlechrome`, `googlegemini` and `googlepixel` for merges. Google AI tags sit here, not in `ai`.")
A("- **digitalart and animation.** Both overlap with `art` and `anime`. `anime` is related-only in `animation`. `動畫` is a synonym in `animation` and in `anime`.")
A("- **indie.** `indiegames`, `indiefilm`, `indiemusic` and `bandcamp` are narrower members of `indie`, as you asked, and the first three are also child groups. `bandcamp` is narrower in `indiemusic` too. A search for `#bandcamp` alone merges nothing. It suggests `indiemusic`. `indieweb` is a different movement and is flagged.")
A("- **indiefilm is thin.** The head has 2 accounts in 7 days on the probed servers. Most members have low use. The group is correct, but its data is weak.")
A("- Overlaps with older groups: `indiegames` is also narrower in `gaming`, `indiemusic` is narrower in `music`, `bandcamp` is related in `music`, `digitalart` is narrower in `art`.")
A("")
A("## Method")
A("")
A("1. Wrote seed lists for each group and language from my own knowledge.")
A("2. Cross-checked head terms against Wikidata labels in all 7 languages. This found about 50 missing translations in version 1. I added them.")
A("3. Checked every tag on live servers with `GET /api/v1/tags/:name` (7-day accounts and uses) and `GET /api/v1/timelines/tag/:name` (40 posts, `language` field).")
A("4. Re-checked thin and unseen tags on extra servers.")
A("")
A("Servers (1 request per second each, no sign-in, aggregates only, no post text stored):")
A("")
A("- English and `und`: mastodon.social, mstdn.ca, mstdn.social, mastodon.world, mastodon.online, mas.to, hachyderm.io, fosstodon.org, techhub.social, ohai.social, infosec.exchange, sunny.garden, universeodon.com, toot.community. Akkoma: pleroma.envs.net (timelines only; it blocks the tag endpoint).")
A("- Batch 2 extras: furry.engineer and pawb.fun (furry), mastodon.art (digitalart, animation), mastodon.gamedev.place (indiegames).")
A("- fr: piaille.fr, mamot.fr. es: tkz.one, masto.es. de: troet.cafe, mastodontech.de. nl: mastodon.nl. ja: mstdn.jp. zh-TW: g0v.social, o3o.ca.")
A("- Probed but limited: mstdn.ca, mstdn.social, infosec.exchange, sunny.garden, g0v.social, meow.social, mastodon.art, tech.lgbt and masto.ai return 422 for tag timelines without sign-in, so only their tag counts were used. m.cmx.im and wxw.moe do the same and were not used. poa.st blocks both endpoints. mastodonners.nl timed out.")
A("")
A("## Files")
A("")
A("| File | Use |")
A("|---|---|")
A("| `groups/<id>.yaml` | One file per group. Source of truth. Each member has relation, languages, status, 7-day accounts. |")
A("| `hashtag-catalog-draft.json` | Compact build output in the format from the curation plan. Excludes unseen tags. The only file the app reads. |")
A("| `tag-stats.jsonl` | Per-tag stats: accounts, uses, post language counts, hosts. |")
A("| `seeds.txt` | Plain-text seed lists that generated the YAML. |")
A("| `observations.jsonl` | Raw per-server counts. |")
A("| `wikidata-labels.json` | Wikidata label cross-check. |")
A("| `scripts/` | Collection, build, and validation scripts. Research tooling only. |")
A("")
A("## Coverage (members per group and language)")
A("")
A("Counts are for the draft catalog (unseen tags excluded). Members marked `und` count under en.")
A("")
A("| group | total | " + " | ".join(L) + " |")
A("|---|---|" + "---|" * len(L))
for gid in REQ + EXT + B2:
    A(f"| {gid} | {len(G[gid]['m'])} | " + " | ".join(str(cell(gid, l)) for l in L) + " |")
A("")
A("## Findings")
A("")
A("1. **Post `language` is unreliable.** Many accounts keep the default. `いぬ`, `ゲーム`, `バスケ` and `野球` show 85 to 100% `en` on mstdn.jp. I did not use it to assign languages. Seed languages stand. I used the field only to flag a different language holding 60% or more of non-English posts. " + f"{len(mism)} tags are flagged (`observed_other_lang` in the YAML).")
A(f"2. **zh-TW is the weakest language.** There is little Taiwanese presence on servers that allow open reads. {len(zu)} of {len(zt)} zh-TW seeds are unseen and {len(zl)} more are thin. They are likely valid tags with low use. They need a Taiwanese server with open tag reads, or a sign-in crawl.")
A("3. **Han tags are shared between ja and zh-TW.** `政治`, `技術`, `音楽`/`音樂` style tags carry both languages. Check by script, not by server.")
A("4. **`#ai` and `#noai` do not overlap.** Verified by script. `aislop` and `aiwashing` sit in `noai` as related only. In the data, `ia` (283 accounts, posts in fr and es) and `ki` (367 accounts, posts all in de) are AI tags.")
A("5. **Football.** `football` is the head of the American football group as requested. It contains nfl, superbowl, collegefootball and others. It excludes `soccer`, `futbol`, `fußball`, `voetbal` and `サッカー`. French `#football` means soccer, so I left it out of `futbol`. A search for `#football` still returns what servers tag `#football`, including soccer. The exclusion applies only to merged members.")
A("6. **`#futbol` is Spanish.** In the data, 20 of 40 non-null posts are `es`. I changed its language from en to es. `soccer` is the English synonym.")
A("7. **Homographs flagged `ambiguous: true`:** art, cricket, esc, ev, film, football, gecko, ia, ki, mastodon, retro, security, turtle, vintage, plus the batch-2 flags above. `birdsite` means Twitter and must stay out of `birds`. `fotograf`, `fotos`, `videospel` and `chemindefer` are shared with sv or cs.")
A(f"8. **Overlaps.** {len(overl)} tags sit in more than one group. In every case one side is related-only, or both sides are the same concept (`cuisine`, `baking`, `recipes`, `新聞`).")
A("9. **Merge size.** Mastodon accepts 3 extra hashtags per query (the implementation plan measured this). Largest merge sets (head, synonyms, narrower): " + ", ".join(f"{gid} {n}" for n, gid in sizes[:8]) + ". The app ranks by weight and trims. Weight is `log2(1 + accounts7)`.")
A("")
A("## Gaps")
A("")
gaps = {gid: [l for l in L if not any(l in m[1] or (l == 'en' and 'und' in m[1]) for m in g['m'])] for gid, g in G.items()}
for gid, v in gaps.items():
    if v: A(f"- `{gid}`: no member for {', '.join(v)}.")
A("- Cricket has no fr, es, de or nl member because the sport is rare in those languages. This is expected.")
A("- Eurovision tags with a year (`eurovision2026`) follow a pattern. A regex member would cover them. Not added.")
A("")
A("## Not seen on any probed server (excluded from the draft catalog)")
A("")
A(", ".join("`" + t + "`" for t, s in st.items() if s["status"] == "unseen"))
A("")
A("## Decisions needed")
A("")
A("1. **Reviewer.** Every cross-language synonym and every narrower link still needs a person to approve it. I did not do this review. Status in the YAML is data-based only.")
A("2. **Taiwan sources.** Do you know a Taiwanese server with open tag reads, or should I use a signed-in crawl?")
A("3. **`#football` head.** Keep it as the head of American football (current), or rename the group head to `nfl` and keep `football` as a synonym only?")
A("4. **Year patterns.** Add regex members for yearly event tags such as Eurovision? The same applies to `ios27`, `iphone18` and `pixel10`.")
A(f"5. **Added topics.** Keep all {len(EXT)} added groups, or limit the first release to your list?")
A("6. **`#apple`.** Keep it unflagged (merges, but a fruit post can match), or flag it and give up the merge?")
open("out/report.md", "w", encoding="utf-8").write("\n".join(R) + "\n")
print(len(R), "lines")
print("\n".join(R[R.index("## Batch 2: new groups"):R.index("## Method")]))
