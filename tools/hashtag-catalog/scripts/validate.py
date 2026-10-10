import json, collections, sys
cat = json.load(open(sys.argv[1], encoding="utf-8"))
G = {g["id"]: g for g in cat["groups"]}
err = []
def tags(gid, rels="hsnr"): return {m[0] for m in G[gid]["m"] if m[2] in rels}
for gid, g in G.items():
    heads = [m for m in g["m"] if m[2] == "h"]
    if len(heads) != 1: err.append(f"{gid}: {len(heads)} heads")
    t = [m[0] for m in g["m"]]
    if len(t) != len(set(t)): err.append(f"{gid}: duplicate members")
    for k in ("x", "c"):
        for o in g.get(k, []):
            if o not in G: err.append(f"{gid}: {k} references missing group {o}")
    for a in g.get("amb", []):
        if a not in t: pass
    for m in g["m"]:
        if m[0] != m[0].lower(): err.append(f"{gid}: not lowercase {m[0]}")
        if m[2] not in "hsnr": err.append(f"{gid}: bad relation {m}")
        if not m[1]: err.append(f"{gid}: no langs {m}")
if tags("ai") & tags("noai"): err.append("ai and noai overlap: %s" % (tags("ai") & tags("noai")))
if tags("football") & tags("futbol"): err.append("football and futbol overlap")
for need in ("nfl", "superbowl"):
    if need not in tags("football"): err.append(f"football lacks {need}")
for bad in ("soccer", "futbol"):
    if bad in tags("football"): err.append(f"football contains {bad}")
print("groups", len(G), "members", sum(len(g["m"]) for g in G.values()))
print("ERRORS" if err else "OK", *err, sep="\n")
