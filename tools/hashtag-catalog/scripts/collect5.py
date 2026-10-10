import json, os, sys, time, threading, queue, urllib.request, urllib.parse, urllib.error, collections
from parse_seeds import parse, norm

UA = "beeline-catalog-research/0.1 (+https://github.com/estyseesghosts/beeline)"
HOSTS = {
    "en": ("mastodon.social", "mastodon.social"),
    "und": ("mastodon.social", "mastodon.social"),
    "fr": ("piaille.fr", "piaille.fr"),
    "es": ("tkz.one", "tkz.one"),
    "de": ("troet.cafe", "troet.cafe"),
    "nl": ("mastodon.nl", "mastodon.nl"),
    "ja": ("mstdn.jp", "mstdn.jp"),
    "zh-TW": ("g0v.social", "o3o.ca"),
}
OUT = "data/obs.jsonl"
os.makedirs("data", exist_ok=True)

done = set()
if os.path.exists(OUT):
    for line in open(OUT, encoding="utf-8"):
        try:
            r = json.loads(line)
            done.add((r["host"], r["tag"], r["kind"]))
        except Exception:
            pass

groups = parse("seeds.txt")
tags = collections.OrderedDict()
for g in groups.values():
    for m in g["members"]:
        tags.setdefault(norm(m["tag"]), set()).update(m["langs"])


ACC = collections.defaultdict(int); USES = collections.defaultdict(int); HAS = set(); HASTL = set()
for line in open(OUT, encoding="utf-8"):
    r = json.loads(line)
    if r["kind"] == "hist" and "accounts7" in r:
        HAS.add(r["tag"]); ACC[r["tag"]] = max(ACC[r["tag"]], r["accounts7"]); USES[r["tag"]] = max(USES[r["tag"]], r["uses7"])
    if r["kind"] == "tl" and r["status"] == 200:
        HASTL.add(r["tag"])
EN_H = ["mastodon.social", "mstdn.ca", "mstdn.social", "mastodon.world", "mastodon.online", "mas.to", "hachyderm.io", "fosstodon.org", "techhub.social", "ohai.social", "universeodon.com", "toot.community"]
EN_T = ["mastodon.social", "mastodon.world", "mastodon.online", "mas.to", "hachyderm.io", "fosstodon.org", "techhub.social", "ohai.social", "universeodon.com", "toot.community", "pleroma.envs.net"]
EXTRA = {"zh-TW": ["o3o.ca", "mastodon.social"], "es": ["masto.es"], "fr": ["mamot.fr"], "de": ["mastodontech.de"], "nl": [], "ja": ["mastodon.social"], "en": ["mastodon.world", "mstdn.ca"], "und": ["mastodon.world"]}
jobs = collections.defaultdict(list)
i = 0
def add(h, t, k):
    if (h, t, k) not in done:
        jobs[h].append((h, t, k))
for tag, langs in tags.items():
    i += 1
    if tag not in HAS:   # never collected: first pass routing
        for l in langs:
            hh, th = HOSTS[l]
            if l in ("en", "und"):
                hh, th = EN_H[i % len(EN_H)], EN_T[i % len(EN_T)]
            add(hh, tag, "hist")
            if tag not in HASTL: add(th, tag, "tl")
    elif ACC[tag] < 3 and USES[tag] < 5:   # thin or unseen: ask extra servers
        for l in langs:
            for h in EXTRA[l]:
                add(h, tag, "hist")

# extra specialised servers for the batch-2 groups
SPECIAL = {
    "furry": (["furry.engineer", "pawb.fun"], ["furry.engineer", "pawb.fun"]),
    "fursuit": (["furry.engineer", "pawb.fun"], ["furry.engineer", "pawb.fun"]),
    "furryart": (["furry.engineer", "pawb.fun"], ["furry.engineer", "pawb.fun"]),
    "digitalart": (["mastodon.art"], []),
    "animation": (["mastodon.art"], []),
    "indiegames": (["mastodon.gamedev.place"], ["mastodon.gamedev.place"]),
    "indie": ([], []),
}
for gid in SPECIAL:
    for m in groups[gid]["members"]:
        t = norm(m["tag"])
        hs, ts = SPECIAL[gid]
        for h in hs: add(h, t, "hist")
        for h in ts: add(h, t, "tl")
for h in jobs:
    seen = set(); uniq = []
    for j in jobs[h]:
        if j not in seen:
            seen.add(j); uniq.append(j)
    jobs[h] = uniq

lock = threading.Lock()
out = open(OUT, "a", encoding="utf-8")


def fetch(host, path):
    url = f"https://{host}{path}"
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=25) as r:
                return r.status, json.load(r)
        except urllib.error.HTTPError as e:
            if e.code == 429:
                wait = int(e.headers.get("Retry-After", "30") or 30)
                time.sleep(min(wait, 120)); continue
            return e.code, None
        except Exception:
            time.sleep(3)
    return 0, None


def run(host):
    for (_, tag, kind) in jobs[host]:
        q = urllib.parse.quote(tag, safe="")
        if kind == "hist":
            st, d = fetch(host, f"/api/v1/tags/{q}")
            rec = {"host": host, "tag": tag, "kind": kind, "status": st}
            if d is not None:
                h = d.get("history", [])
                rec["accounts7"] = sum(int(x["accounts"]) for x in h)
                rec["uses7"] = sum(int(x["uses"]) for x in h)
                rec["days"] = len(h)
        else:
            st, d = fetch(host, f"/api/v1/timelines/tag/{q}?limit=40")
            rec = {"host": host, "tag": tag, "kind": kind, "status": st}
            if d is not None:
                c = collections.Counter((p.get("language") or "null") for p in d)
                rec["n"] = len(d)
                rec["langs"] = dict(c)
                rec["accts"] = len({p["account"]["acct"] for p in d})
                rec["servers"] = len({p["account"]["acct"].split("@")[-1] if "@" in p["account"]["acct"] else host for p in d})
        with lock:
            out.write(json.dumps(rec, ensure_ascii=False) + "\n"); out.flush()
        time.sleep(1.05)


ths = [threading.Thread(target=run, args=(h,)) for h in jobs]
for t in ths: t.start()
for t in ths: t.join()
print("done")
