import json, math, os, collections, sys
from parse_seeds import parse, norm

OUT = "out"
os.makedirs(OUT + "/groups", exist_ok=True)
groups = parse("seeds.txt")
_PRI = {"head": 0, "syn": 1, "nar": 2, "rel": 3}
for _g in groups.values():
    merged = collections.OrderedDict()
    for m in _g["members"]:
        k = norm(m["tag"])
        if k in merged:
            cur = merged[k]
            for l in m["langs"]:
                if l not in cur["langs"]:
                    cur["langs"].append(l)
            if _PRI[m["relation"]] < _PRI[cur["relation"]]:
                cur["relation"] = m["relation"]
        else:
            merged[k] = {"tag": m["tag"], "langs": list(m["langs"]), "relation": m["relation"]}
    _g["members"] = list(merged.values())

# ---- observations ----
hist = collections.defaultdict(dict)   # tag -> host -> (accounts7, uses7)
langc = collections.defaultdict(collections.Counter)
posts = collections.Counter()
tl_hosts = collections.defaultdict(set)
for line in open("data/obs.jsonl", encoding="utf-8"):
    r = json.loads(line)
    if r["status"] != 200:
        continue
    t = r["tag"]
    if r["kind"] == "hist" and "accounts7" in r:
        hist[t][r["host"]] = (r["accounts7"], r["uses7"])
    elif r["kind"] == "tl":
        for l, n in r["langs"].items():
            langc[t][l] += n
        posts[t] += r["n"]
        tl_hosts[t].add(r["host"])

NORM_LANG = {"zh": "zh-TW?", "zh-CN": "zh-CN", "zh-TW": "zh-TW", "zh-HK": "zh-TW", "pt-BR": "pt", "en-US": "en", "en-GB": "en"}


def prim(l):
    return NORM_LANG.get(l, l.split("-")[0] if l not in ("zh-TW", "zh-CN", "zh-HK") else l)


def observed_langs(t):
    c = langc.get(t)
    if not c:
        return None
    nonnull = {l: n for l, n in c.items() if l != "null"}
    tot = sum(nonnull.values())
    agg = collections.Counter()
    for l, n in nonnull.items():
        agg[prim(l)] += n
    return {"non_null": tot, "null": c.get("null", 0), "counts": dict(agg.most_common(6))}


def stat(t):
    h = hist.get(t, {})
    acc = max((a for a, u in h.values()), default=0)
    uses = max((u for a, u in h.values()), default=0)
    return acc, uses, sorted(h)


# ---- per-tag stats ----
alltags = collections.OrderedDict()
for gid, g in groups.items():
    for m in g["members"]:
        alltags.setdefault(norm(m["tag"]), {"seed_langs": set(), "groups": set()})
        alltags[norm(m["tag"])]["seed_langs"].update(m["langs"])
        alltags[norm(m["tag"])]["groups"].add(gid)

tagstats = {}
for t, info in alltags.items():
    acc, uses, hosts = stat(t)
    ol = observed_langs(t)
    derived = None
    if ol and ol["non_null"] >= 20:
        tot = ol["non_null"]
        keep = [l for l, n in ol["counts"].items() if n / tot >= 0.25]
        many = [l for l, n in ol["counts"].items() if n / tot >= 0.10]
        derived = ["und"] if len(many) > 4 else keep
    tagstats[t] = {"tag": t, "accounts7": acc, "uses7": uses, "hist_hosts": hosts,
                   "tl_hosts": sorted(tl_hosts.get(t, [])), "posts_sampled": posts.get(t, 0),
                   "observed": ol, "derived_langs": derived,
                   "seed_langs": sorted(info["seed_langs"]), "groups": sorted(info["groups"])}

# status
for t, s in tagstats.items():
    if s["accounts7"] >= 3 or s["uses7"] >= 5:
        s["status"] = "seen"
    elif s["uses7"] > 0 or s["posts_sampled"] > 0:
        s["status"] = "low"
    else:
        s["status"] = "unseen"

# language conflict: ignore "en" (many accounts leave the posting language at its default),
# then flag when one other language holds >=60% of >=15 non-en, non-null posts and is not in the seed list.
mismatch = []
for t, s in tagstats.items():
    ol = s["observed"]
    s["conflict"] = None
    if not ol:
        continue
    rest = {l: n for l, n in ol["counts"].items() if l != "en"}
    tot = sum(rest.values())
    if tot < 15:
        continue
    top, n = max(rest.items(), key=lambda kv: kv[1])
    if n / tot >= 0.6:
        seed = set(s["seed_langs"])
        ok = top in seed or (top.startswith("zh") and "zh-TW" in seed)
        if not ok and "und" not in seed:
            s["conflict"] = top
            mismatch.append((t, sorted(seed), top, round(n / tot, 2), tot))


def weight(t):
    return round(math.log2(1 + tagstats[t]["accounts7"]), 1)


RELCODE = {"head": "h", "syn": "s", "nar": "n", "rel": "r"}


def q(s):
    return json.dumps(s, ensure_ascii=False)


for gid, g in groups.items():
    lines = [f"id: {gid}", f"request: {q(g['request'])}" if g["request"] else "request: null"]
    if g["excludes"]:
        lines.append("excludes: [" + ", ".join(g["excludes"]) + "]")
    if g["children"]:
        lines.append("children: [" + ", ".join(g["children"]) + "]")
    if g.get("note"):
        lines.append(f"note: {q(g['note'])}")
    lines.append("members:")
    seen = set()
    for m in g["members"]:
        t = norm(m["tag"])
        if t in seen:
            continue
        seen.add(t)
        s = tagstats[t]
        row = [f"  - tag: {q(t)}", f"    langs: [{', '.join(m['langs'])}]", f"    relation: {m['relation']}",
               f"    status: {s['status']}", f"    accounts7: {s['accounts7']}"]
        if t in [norm(a) for a in g["ambiguous"]]:
            row.append("    ambiguous: true")
        if s["conflict"]:
            row.append(f"    observed_other_lang: {s['conflict']}")
        lines += row
    open(f"{OUT}/groups/{gid}.yaml", "w", encoding="utf-8").write("\n".join(lines) + "\n")

with open(OUT + "/tag-stats.jsonl", "w", encoding="utf-8") as f:
    for t, s in sorted(tagstats.items(), key=lambda kv: -kv[1]["accounts7"]):
        f.write(json.dumps(s, ensure_ascii=False) + "\n")

# draft app catalog: only seen or low tags
cat = {"v": 1, "built": "2026-10-10", "groups": []}
for gid, g in groups.items():
    mem, seen = [], set()
    for m in g["members"]:
        t = norm(m["tag"])
        if t in seen or tagstats[t]["status"] == "unseen":
            continue
        seen.add(t)
        mem.append([t, m["langs"], RELCODE[m["relation"]], weight(t)])
    ent = {"id": gid, "m": mem}
    if g["excludes"]: ent["x"] = g["excludes"]
    if g["children"]: ent["c"] = g["children"]
    if t in [] : pass
    amb = [norm(a) for a in g["ambiguous"]]
    if amb: ent["amb"] = amb
    cat["groups"].append(ent)
json.dump(cat, open(OUT + "/hashtag-catalog-draft.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))

json.dump({"mismatch": mismatch}, open("data/mismatch.json", "w", encoding="utf-8"), ensure_ascii=False)
# summary numbers
c = collections.Counter(s["status"] for s in tagstats.values())
print("tags", len(tagstats), dict(c))
print("groups", len(groups), "members in draft", sum(len(g["m"]) for g in cat["groups"]))
print("mismatch", len(mismatch))
