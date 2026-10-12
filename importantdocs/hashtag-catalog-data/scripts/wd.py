import json, time, urllib.request, urllib.parse, unicodedata
from parse_seeds import parse, norm

UA = "beeline-catalog-research/0.1 (+https://github.com/estyseesghosts/beeline)"
TERMS = {
 "technology": "technology", "foss": "free and open-source software", "infosec": "information security",
 "ai": "artificial intelligence", "cats": "cat", "reptiles": "reptile", "dogs": "dog", "birds": "bird",
 "art": "art", "artist": "artist", "photography": "photography", "music": "music", "tv": "television",
 "gaming": "video game", "movies": "film", "politics": "politics", "climatechange": "climate change",
 "cars": "car", "trains": "train", "publictransit": "public transport", "fediverse": "fediverse",
 "writing": "writing", "blog": "blog", "article": "article", "newspaper": "newspaper",
 "academics": "academia", "sports": "sport", "basketball": "basketball", "baseball": "baseball",
 "football": "American football", "futbol": "association football", "eurovision": "Eurovision Song Contest",
 "linux": "Linux", "programming": "computer programming", "selfhosting": "self-hosting", "privacy": "privacy",
 "science": "science", "space": "outer space", "news": "news", "books": "book", "history": "history",
 "food": "food", "cooking": "cooking", "gardening": "gardening", "travel": "travel", "nature": "nature",
 "anime": "anime", "manga": "manga", "boardgames": "board game", "ttrpg": "tabletop role-playing game",
 "cycling": "cycling", "running": "running", "hiking": "hiking", "tennis": "tennis", "hockey": "ice hockey",
 "cricket": "cricket", "rugby": "rugby union", "golf": "golf", "motorsport": "motorsport", "olympics": "Olympic Games",
}
OVERRIDE = {"cats": "Q146", "dogs": "Q144", "birds": "Q5113", "technology": "Q11016", "art": "Q735", "artist": "Q483501", "cars": "Q1420", "news": "Q1193438", "books": "Q571", "space": "Q1", "nature": "Q7860"}
LK = ["en", "fr", "es", "de", "nl", "ja", "zh-tw", "zh-hant"]


def get(params):
    url = "https://www.wikidata.org/w/api.php?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for _ in range(3):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.load(r)
        except Exception:
            time.sleep(3)
    return {}


def slug(s):
    s = unicodedata.normalize("NFC", s).lower()
    return "".join(ch for ch in s if ch.isalnum())


groups = parse("seeds.txt")
res = {}
for gid, term in TERMS.items():
    s = get({"action": "wbsearchentities", "search": term, "language": "en", "format": "json", "limit": 5, "type": "item"})
    time.sleep(1)
    ids = [x["id"] for x in s.get("search", [])]
    if gid in OVERRIDE:
        ids = [OVERRIDE[gid]]
    if not ids:
        print("NOIDS", gid, flush=True); continue
    e = get({"action": "wbgetentities", "ids": "|".join(ids), "props": "sitelinks|labels|aliases|descriptions",
             "languages": "|".join(LK), "format": "json"})
    time.sleep(1)
    if "entities" not in e:
        print("NOENT", gid, flush=True); continue
    best = ids[0]
    ent = e["entities"][best]
    labels = {l: ent["labels"][l]["value"] for l in LK if l in ent.get("labels", {})}
    aliases = {l: [a["value"] for a in ent.get("aliases", {}).get(l, [])] for l in LK}
    members = {slug(norm(m["tag"])) for m in groups[gid]["members"]}
    rec = {"group": gid, "qid": best, "sitelinks": len(ent.get("sitelinks", {})),
           "desc": ent.get("descriptions", {}).get("en", {}).get("value"), "labels": labels, "aliases": aliases}
    rec["covered"] = {l: (slug(labels[l]) in members) for l in labels}
    res[gid] = rec
    print(gid, best, rec["desc"], {l: (labels[l], rec["covered"][l]) for l in labels}, flush=True)
json.dump(res, open("data/wikidata.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
