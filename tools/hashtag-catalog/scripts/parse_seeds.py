import re, json, sys, collections

LANGS = ["en", "fr", "es", "de", "nl", "ja", "zh-TW"]


def parse(path):
    groups = collections.OrderedDict()
    cur = None
    for raw in open(path, encoding="utf-8"):
        line = raw.rstrip("\n")
        if not line.strip() or line.startswith("#"):
            continue
        if line.startswith("@"):
            key, _, val = line[1:].partition(" ")
            val = val.strip()
            if key == "id":
                cur = {"id": val, "request": None, "excludes": [], "children": [],
                       "note": "", "ambiguous": [], "members": []}
                groups[val] = cur
            elif key in ("excludes", "children", "ambiguous"):
                cur[key] = val.split()
            else:
                cur[key] = val
            continue
        langs, rel, tags = line.split("|", 2)
        langs = [l for l in langs.split(",")]
        for t in tags.split():
            cur["members"].append({"tag": t, "langs": langs, "relation": rel})
    return groups


def norm(tag):
    return tag.lower()


if __name__ == "__main__":
    g = parse(sys.argv[1])
    tags = collections.OrderedDict()
    for gid, grp in g.items():
        for m in grp["members"]:
            tags.setdefault(norm(m["tag"]), set()).update(m["langs"])
    print(len(g), "groups", sum(len(x["members"]) for x in g.values()), "members", len(tags), "unique tags")
    c = collections.Counter()
    for t, ls in tags.items():
        for l in ls:
            c[l] += 1
    print(c)
