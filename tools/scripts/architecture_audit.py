#!/usr/bin/env python3
"""Deterministic, lightweight architecture regression audit for production Kotlin."""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

PARSER_VERSION = "2"
OWNER_SUFFIXES = ("Manager", "Coordinator", "Controller", "Owner", "Authority")
# Files that wire features instead of implementing them. Composition roots may
# import across feature boundaries, so dependency checks skip them explicitly.
COMPOSITION_ROOT_STEMS = frozenset({"MainActivity", "PalustrisApp", "PalustrisApplication"})
MAP_RE = re.compile(
    r"\b(?:val|var)\s+([A-Za-z_]\w*)\s*(?::\s*[^=\n]+)?\s*=\s*"
    r"(?:mutableMapOf|mapOf|ConcurrentHashMap|HashMap|LinkedHashMap|mutableStateMapOf)"
    r"\b(?:\s*<[^\n>]*>)?\s*(?:\([^\n]*\))?"
)
CLASS_RE = re.compile(r"\b(?:class|object|interface)\s+([A-Za-z_]\w*)")
CLASS_HEADER_RE = re.compile(r"\bclass\s+[A-Za-z_]\w*")
SECONDARY_CONSTRUCTOR_RE = re.compile(r"(?<!\w)constructor\s*\(")
HEADER_PREFIX_RE = re.compile(r"(?:\s*(?:@[\w.]+(?:\s*\([^()]*\))?|(?:public|private|protected|internal)|constructor))*\s*\(")
OWNER_DEFAULT_CONSTRUCTION_RE = re.compile(
    r"=\s*(?!\w*MutationOwner\b)[A-Z]\w*(?:Authority|Cache|Pool|Controller|Owner|Manager)\s*\("
)
FUN_RE = re.compile(r'(?m)^\s*(?:@[\w.()", =:-]+\s*)*(?:(?:public|private|protected|internal|inline|tailrec|operator|infix|suspend|override|open|final|abstract)\s+)*fun\s+(?:<[^>{}]+>\s*)?(?:[\w?.<>]+\.)?([A-Za-z_]\w*)\s*\(')
DEFAULT_THRESHOLDS = {
    "fileWarningLines": 700,
    "functionWarningLines": 80,
    "functionWarningParameters": 7,
    "functionWarningNesting": 5,
    "hotspotGrowthPercent": 20,
    "hotspotGrowthLines": 50,
}
RETENTION_KEYWORDS = r"(?:maximum|max\b|bound|expiry|expires|expire|prune|clear|release|remove|invalidate|evict|size\s*[<>])"
RETENTION_COMMENT_LINES = 3


def source_roots(root: Path):
    """Return existing production Kotlin source roots in a fixed order."""
    candidates = [root / "app" / "src" / "main" / "java", root / "app" / "src" / "main" / "kotlin"]
    return [candidate for candidate in candidates if candidate.exists()]


def kotlin_files(root: Path):
    files = []
    for source in source_roots(root):
        files.extend(source.rglob("*.kt"))
    return sorted(files, key=lambda p: p.relative_to(root).as_posix())


def expected_package(path: Path, root: Path) -> str:
    """Derive the expected package from the source root that contains the file."""
    for source in source_roots(root):
        try:
            return ".".join(path.relative_to(source).parts[:-1])
        except ValueError:
            continue
    return ""


def is_composition_root(rel: str, package_name: str, stem: str, allowlists=None) -> bool:
    """Check whether a file wires features instead of implementing them."""
    configured = set((allowlists or {}).get("compositionRootFiles", ()))
    if rel in configured:
        return True
    if package_name == "me.foxtails.palustris.di" or ".di." in package_name or package_name.endswith(".di"):
        return True
    return stem in COMPOSITION_ROOT_STEMS


def mask(text: str) -> str:
    """Hide comments and strings while preserving positions for simple scans."""
    out, i, state = list(text), 0, "code"
    while i < len(text):
        c, n = text[i], text[i + 1] if i + 1 < len(text) else ""
        if state == "code":
            if c == "/" and n == "/": out[i] = out[i + 1] = " "; i += 2; state = "line"; continue
            if c == "/" and n == "*": out[i] = out[i + 1] = " "; i += 2; state = "block"; continue
            if text.startswith('"""', i): out[i:i + 3] = [" "] * 3; i += 3; state = "triple"; continue
            if c == '"': out[i] = " "; state = "string"
            elif c == "'": out[i] = " "; state = "char"
        elif state == "line":
            if c == "\n": state = "code"
            else: out[i] = " "
        elif state == "block":
            if c == "*" and n == "/": out[i] = out[i + 1] = " "; i += 2; state = "code"; continue
            if c != "\n": out[i] = " "
        elif state == "triple":
            if text.startswith('"""', i): out[i:i + 3] = [" "] * 3; i += 3; state = "code"; continue
            if c != "\n": out[i] = " "
        else:
            if c == "\\": out[i] = " "; i += 1
            elif c == ('"' if state == "string" else "'"): state = "code"
            elif c != "\n": out[i] = " "
        i += 1
    return "".join(out)


def balanced_end(text: str, start: int, opening: str = "(", closing: str = ")"):
    depth = 0
    for i in range(start, len(text)):
        if text[i] == opening: depth += 1
        elif text[i] == closing:
            depth -= 1
            if depth == 0: return i
    return None


def params(text: str, start: int) -> int:
    end = balanced_end(text, start)
    if end is None or not text[start + 1:end].strip(): return 0
    depth, count = 0, 1
    for c in text[start + 1:end]:
        if c in "(<[{": depth += 1
        elif c in ")>]}" : depth = max(0, depth - 1)
        elif c == "," and depth == 0: count += 1
    return count


def body_metrics(text: str, sig_end: int):
    i = sig_end + 1
    while i < len(text) and text[i].isspace(): i += 1
    while i < min(len(text), i + 900) and text[i] not in "{=;": i += 1
    if i >= len(text) or text[i] == ";": return None
    if text[i] == "=":
        end = text.find("\n", i)
        return (i, len(text) if end < 0 else end, 0)
    end = balanced_end(text, i, "{", "}")
    if end is None: return None
    depth = max_depth(text[i:end + 1])
    return i, end + 1, depth


def max_depth(text: str) -> int:
    depth = result = 0
    for c in text:
        if c == "{": depth += 1; result = max(result, depth)
        elif c == "}": depth = max(0, depth - 1)
    return max(0, result - 1)


def finding(kind, file, detail, **extra):
    # Keep IDs stable across runs: use the symbol name, never a metric value,
    # so baseline entries match until the violation itself changes.
    symbol = extra.pop("symbol", None)
    row = {"id": f"{kind}:{file}:{symbol if symbol is not None else detail}", "kind": kind, "file": file, "detail": detail}
    if symbol is not None:
        row["symbol"] = symbol
    row.update(extra)
    return row


def owner_scope(text: str, position: int):
    """Return the active owner body so retention evidence does not leak across declarations."""
    openings = []
    for index, character in enumerate(text[:position]):
        if character == "{":
            openings.append(index)
        elif character == "}" and openings:
            openings.pop()
    if not openings:
        return ""
    opening = openings[-1]
    end = balanced_end(text, opening, "{", "}")
    return text[opening:end + 1] if end is not None else text[opening:]


def declaration_block(text: str, position: int) -> str:
    """Return only the top-level declaration line when no owner body encloses it."""
    start = text.rfind("\n", 0, position) + 1
    end = text.find("\n", position)
    return text[start:] if end < 0 else text[start:end]


def nearby_retention_comment(raw_text: str, symbol: str, position: int) -> bool:
    """Accept explicit retention comments or annotations near an otherwise isolated map."""
    lines = raw_text.splitlines()
    line_number = raw_text.count("\n", 0, position)
    symbol_use = re.escape(symbol)
    for line in lines[max(0, line_number - RETENTION_COMMENT_LINES):line_number + RETENTION_COMMENT_LINES + 1]:
        if re.search(rf"\b{symbol_use}\b", line, re.IGNORECASE) and re.search(RETENTION_KEYWORDS, line, re.IGNORECASE):
            if "//" in line or "/*" in line or "*" in line or re.search(r"@\w+", line):
                return True
    return False


def has_retention_evidence(text: str, symbol: str, position: int, raw_text=None) -> bool:
    scope = owner_scope(text, position)
    if not scope:
        scope = declaration_block(text, position)
    symbol_use = re.escape(symbol)
    tied = re.search(
        rf"\b{symbol_use}\b[\s\S]{{0,240}}(?:\.clear\s*\(|\.remove\s*\(|\.invalidate\s*\(|\.release\s*\(|\.evict\s*\(|expire|expiry|expires|prune|maximum|max\b|bound|size\s*[<>])",
        scope,
        re.IGNORECASE,
    )
    return bool(tied or re.search(RETENTION_KEYWORDS, scope, re.IGNORECASE) or (raw_text and nearby_retention_comment(raw_text, symbol, position)))


def has_constructor_default_construction(text: str) -> bool:
    """Check constructor parameter defaults only for directly created owners."""
    # Scan primary and explicit constructor parameter lists only.
    # Class-body and function-body construction stays out of scope.
    for header in CLASS_HEADER_RE.finditer(text):
        cursor = header.end()
        while cursor < len(text) and text[cursor].isspace():
            cursor += 1
        if cursor < len(text) and text[cursor] == "<":
            depth = 0
            while cursor < len(text):
                if text[cursor] == "<":
                    depth += 1
                elif text[cursor] == ">":
                    depth -= 1
                    if depth == 0:
                        cursor += 1
                        break
                cursor += 1
        prefix = HEADER_PREFIX_RE.match(text, cursor)
        if not prefix:
            continue
        start = cursor + prefix.end() - 1
        end = balanced_end(text, start)
        if end is not None and OWNER_DEFAULT_CONSTRUCTION_RE.search(text[start + 1:end]):
            return True
    for secondary in SECONDARY_CONSTRUCTOR_RE.finditer(text):
        start = secondary.end() - 1
        end = balanced_end(text, start)
        if end is not None and OWNER_DEFAULT_CONSTRUCTION_RE.search(text[start + 1:end]):
            return True
    return False


def audit(root: Path, retention_rules=None, thresholds=None, allowlists=None) -> dict:
    root = root.resolve()
    thresholds = {**DEFAULT_THRESHOLDS, **(thresholds or {})}
    allowlists = allowlists or {}
    rows, files, functions = [], {}, []
    for path in kotlin_files(root):
        rel = path.relative_to(root).as_posix()
        raw, text = path.read_text(encoding="utf-8", errors="replace"), None
        text = mask(raw)
        lines = raw.count("\n") + (1 if raw else 0)
        files[rel] = {"lines": lines}
        package = re.search(r"(?m)^\s*package\s+([\w.]+)", text)
        expected = expected_package(path, root)
        actual_package = package.group(1) if package else "<missing>"
        if not package or actual_package != expected:
            rows.append(finding("package-path-mismatch", rel, f"{actual_package} != {expected}"))
        global_ui = set(allowlists.get("rootUiGlobalFiles", ())) or {"AppIcons", "BeelineSvgPaths", "Components", "Theme", "UiStrings", "SystemBars", "SourceErrorMessage", "DetailActionPolicy", "ConnectedApp", "PalustrisApp", "PalustrisAppPreview"}
        feature_name = path.stem
        is_feature_root = path.parent.name == "ui" and feature_name not in global_ui
        if is_feature_root:
            rows.append(finding("root-ui-feature-file", rel, path.stem))
        classes = [m.group(1) for m in CLASS_RE.finditer(text)]
        owner_classes = [c for c in classes if c.endswith(OWNER_SUFFIXES)]
        for cls in owner_classes:
            rows.append(finding("ownership-class", rel, cls))
        if has_constructor_default_construction(text):
            rows.append(finding("private-owner-construction", rel, "default constructor construction"))
        for map_match in MAP_RE.finditer(text):
            symbol = map_match.group(1)
            if not has_retention_evidence(text, symbol, map_match.start(), raw) and rel not in (retention_rules or {}):
                rows.append(finding("unretained-long-lived-map", rel, symbol))
        package_name = package.group(1) if package else ""
        composition_root = is_composition_root(rel, package_name, path.stem, allowlists)
        for imp in re.findall(r"(?m)^\s*import\s+([\w.]+)", text):
            if composition_root:
                break
            if package_name.startswith("me.foxtails.palustris.domain") and ".data." in imp or package_name.startswith("me.foxtails.palustris.domain") and ".ui." in imp:
                rows.append(finding("dependency-direction", rel, imp))
            if ".data." in package_name and ".ui." in imp:
                rows.append(finding("dependency-direction", rel, imp))
            if package_name.startswith("me.foxtails.palustris.ui.") and imp.startswith("me.foxtails.palustris.ui."):
                other = imp.split(".ui.", 1)[1].split(".", 1)[0]
                own = package_name.split(".ui.", 1)[1].split(".", 1)[0]
                if other != own and other not in {"shell", "common"}:
                    rows.append(finding("dependency-direction", rel, imp))
        for match in FUN_RE.finditer(text):
            opening = text.find("(", match.start(), match.end())
            end = balanced_end(text, opening)
            if end is None: continue
            body = body_metrics(text, end)
            if not body: continue
            start, finish, nesting = body
            metric = {"file": rel, "name": match.group(1), "lines": text.count("\n", match.start(), finish) + 1,
                      "params": params(text, opening), "nesting": nesting}
            functions.append(metric)
            if metric["lines"] > thresholds["functionWarningLines"]:
                rows.append(finding("function-size-warning", rel, f"{metric['name']} has {metric['lines']} lines", symbol=metric["name"]))
            if metric["params"] > thresholds["functionWarningParameters"]:
                rows.append(finding("function-parameter-warning", rel, f"{metric['name']} has {metric['params']} parameters", symbol=metric["name"]))
            if metric["nesting"] > thresholds["functionWarningNesting"]:
                rows.append(finding("function-nesting-warning", rel, f"{metric['name']} has nesting {metric['nesting']}", symbol=metric["name"]))
        if lines > thresholds["fileWarningLines"]:
            rows.append(finding("file-size-warning", rel, f"{lines} lines", symbol="<file>"))
    for row in rows: row["classification"] = "warning"
    return {"parserVersion": PARSER_VERSION, "findings": sorted(rows, key=lambda x: x["id"]),
            "fileMetrics": dict(sorted(files.items())), "functionMetrics": sorted(functions, key=lambda x: (x["file"], x["name"]))}


def classify(report: dict, baseline: dict) -> dict:
    existing = set(baseline.get("existingFindings", []))
    enforced = set(baseline.get("enforcedFindings", []))
    resolved = set(baseline.get("resolvedFindings", []))
    result = []
    for row in report["findings"]:
        item = dict(row)
        if row["kind"] in {"ownership-class", "dependency-direction", "file-size-warning", "function-size-warning", "function-parameter-warning", "function-nesting-warning"}: item["classification"] = "warning"
        elif row["id"] in resolved: item["classification"] = "regression-returned-resolved"
        elif row["id"] in enforced: item["classification"] = "regression"
        elif row["id"] in existing: item["classification"] = "existing-baseline"
        else: item["classification"] = "regression"
        result.append(item)
    result.extend(hotspot_regressions(report, baseline))
    report["findings"] = sorted(result, key=lambda x: x["id"])
    report["regressions"] = [x for x in report["findings"] if x["classification"].startswith("regression")]
    return report


def hotspot_regressions(report, baseline):
    out = []
    thresholds = {**DEFAULT_THRESHOLDS, **baseline.get("thresholds", {})}
    for file, old in baseline.get("fileMetrics", {}).items():
        new = report["fileMetrics"].get(file)
        if new and old.get("lines", 0) and new["lines"] > old["lines"] * (1 + thresholds["hotspotGrowthPercent"] / 100) and new["lines"] - old["lines"] >= thresholds["hotspotGrowthLines"]:
            out.append(finding("hotspot-growth", file, f"{old['lines']} -> {new['lines']} lines", symbol="<file>", classification="regression"))
    old_functions = {(x.get("file"), x.get("name")): x for x in baseline.get("functionMetrics", [])}
    for new in report.get("functionMetrics", []):
        old = old_functions.get((new.get("file"), new.get("name")))
        if not old:
            continue
        changed = []
        for metric in ("lines", "params", "nesting"):
            before, after = old.get(metric, 0), new.get(metric, 0)
            if after > before and (after > thresholds.get({"lines": "functionWarningLines", "params": "functionWarningParameters", "nesting": "functionWarningNesting"}[metric]) or (before and after > before * (1 + thresholds["hotspotGrowthPercent"] / 100) and after - before >= 1)):
                changed.append(f"{metric} {before}->{after}")
        if changed:
            out.append(finding("function-complexity-growth", new["file"], f"{new['name']}: {', '.join(changed)}", symbol=new["name"], classification="regression"))
    return out


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("root", nargs="?", default=".")
    ap.add_argument("--baseline", type=Path)
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args(argv)
    baseline_data = json.loads(args.baseline.read_text(encoding="utf-8")) if args.baseline and args.baseline.exists() else {}
    report = audit(Path(args.root), baseline_data.get("retentionRules"), baseline_data.get("thresholds"), baseline_data.get("allowlists"))
    if args.baseline and args.baseline.exists():
        report = classify(report, baseline_data)
    if args.json: print(json.dumps(report, indent=2, sort_keys=True))
    else:
        for row in report["findings"]: print(f"{row['classification']}: {row['id']}")
        print(f"Architecture audit: {len(report['findings'])} findings")
    return 1 if args.check and report.get("regressions") else 0


if __name__ == "__main__": sys.exit(main())
