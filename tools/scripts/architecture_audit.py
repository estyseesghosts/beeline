#!/usr/bin/env python3
"""Deterministic, lightweight architecture regression audit for production Kotlin."""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

PARSER_VERSION = "2"
# Current baseline schema version. Record always writes this value.
# Old baselines stay readable, but record refreshes them forward.
SCHEMA_VERSION = 2
# Kinds that report review signals but never fail --check by themselves.
# New Owner-like names stay visible without failing every existing Owner.
# Size and dependency signals stay visible without blocking unrelated work.
WARNING_KINDS = frozenset({
    "ownership-class",
    "dependency-direction",
    "file-size-warning",
    "function-size-warning",
    "function-parameter-warning",
    "function-nesting-warning",
})
# Minimum absolute growth for a function metric to count as material.
# File growth uses thresholds["hotspotGrowthLines"]. Function growth uses
# these smaller bounds because functions are short by design.
FUNCTION_GROWTH_LINES = 5
FUNCTION_GROWTH_STEPS = 1
OWNER_SUFFIXES = ("Manager", "Coordinator", "Controller", "Owner", "Authority")
# Slice 19 zero-tolerance stub detectors. Subtask B owns full detectors for
# these categories. These narrow checks encode only the Slice 1 and Slice 14
# exit conditions, so a cleaned-up violation cannot silently return.
# ProfileViewModel must not reintroduce paging-job or cursor tracking.
# ProfileTimelinePager stays the only profile paging owner. Only production
# roots are scanned, so test fixtures stay allowed.
DUPLICATE_PAGING_OWNER_SUFFIX = "ui/profile/ProfileViewModel.kt"
DUPLICATE_PAGING_RE = re.compile(r"\b(pageJobs|requestedCursors|loadPage|publishPageFailure)\b")
# PostInteractionExecutionAuthority must be constructed once per connected
# session at the canonical session wiring point, never per feature surface
# (feed, profile, thread, saved, search, notification detail, single-post).
# Only production roots are scanned, so test fixtures stay allowed.
EXECUTION_AUTHORITY_CONSTRUCTION_RE = re.compile(r"\bPostInteractionExecutionAuthority\s*\(")
EXECUTION_AUTHORITY_CANONICAL_FILES = frozenset({
    "app/src/main/java/me/foxtails/palustris/ui/session/ConnectedSessionHost.kt",
})
# Files that wire features instead of implementing them. Composition roots may
# import across feature boundaries, so dependency checks skip them explicitly.
COMPOSITION_ROOT_STEMS = frozenset({"MainActivity", "PalustrisApp", "PalustrisApplication"})
MAP_RE = re.compile(
    r"\b(?:val|var)\s+([A-Za-z_]\w*)\s*(?::\s*[^=\n]+)?\s*=\s*"
    r"(?:mutableMapOf|mapOf|ConcurrentHashMap|HashMap|LinkedHashMap|mutableStateMapOf)"
    r"\b(?:\s*<[^\n>]*>)?\s*(?:\([^\n]*\))?"
)
# Compose remembered maps share the screen lifetime and need the same
# retention audit as direct maps. Covers both remember { mutableStateMapOf }
# and remember(key) { mutableStateMapOf } so MediaViewerScreen symbol keys
# are actually audited.
REMEMBERED_MAP_RE = re.compile(
    r"\b(?:val|var)\s+([A-Za-z_]\w*)\s*(?::\s*[^=\n]+)?\s*=\s*"
    r"remember\s*(?:\([^()]*\))?\s*\{\s*mutableStateMapOf\b"
)
CLASS_RE = re.compile(r"\b(?:class|object|interface)\s+([A-Za-z_]\w*)")
CLASS_HEADER_RE = re.compile(r"\bclass\s+[A-Za-z_]\w*")
SECONDARY_CONSTRUCTOR_RE = re.compile(r"(?<!\w)constructor\s*\(")
HEADER_PREFIX_RE = re.compile(r"(?:\s*(?:@[\w.]+(?:\s*\([^()]*\))?|(?:public|private|protected|internal)|constructor))*\s*\(")
OWNER_DEFAULT_CONSTRUCTION_RE = re.compile(
    r"=\s*(?!\w*MutationOwner\b)(?:[A-Z]\w*)?(?:Authority|Cache|Pool|Controller|Coordinator|Owner|Manager)\s*\("
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
    # A remembered map declaration sits inside its own remember lambda, but
    # retention calls (clear/remove/...) live in the enclosing function or
    # composable body. Step out of the remember lambda so that evidence stays
    # visible. Match only the direct remember form to keep the scan exact.
    while openings:
        before = text[max(0, opening - 64):opening]
        if re.search(r"remember\s*(?:\([^()]*\))?\s*$", before):
            openings.pop()
            if not openings:
                return ""
            opening = openings[-1]
            continue
        break
    end = balanced_end(text, opening, "{", "}")
    return text[opening:end + 1] if end is not None else text[opening:]


# Top-level lines read around an isolated map declaration when no owner
# body encloses it. Small so distant uses cannot leak across declarations.
TOP_LEVEL_SCOPE_LINES = 3


def _line_depths(text: str) -> list[int]:
    """Return the brace depth at the start of each masked-text line."""
    depths, depth = [], 0
    for line in text.splitlines():
        depths.append(depth)
        for character in line:
            if character == "{":
                depth += 1
            elif character == "}":
                depth = max(0, depth - 1)
    return depths


def _is_top_level_statement(lines: list[str], depths: list[int], index: int) -> bool:
    """Check whether a line is a top-level statement outside any body."""
    if index < 0 or index >= len(lines) or depths[index] != 0:
        return False
    stripped = lines[index].strip()
    return bool(stripped) and "{" not in lines[index] and "}" not in lines[index]


def is_map_declaration(line: str) -> bool:
    """Check direct and Compose remembered map declarations on one line."""
    return bool(MAP_RE.search(line) or REMEMBERED_MAP_RE.search(line))


def declaration_block(text: str, position: int) -> str:
    """Return the top-level region around a declaration outside any owner body."""
    lines = text.splitlines()
    if not lines:
        return ""
    depths = _line_depths(text)
    decl = min(text.count("\n", 0, position), len(lines) - 1)
    declaration = lines[decl]
    for neighbor in (decl - 1, decl + 1):
        # Consecutively declared maps keep single-line scopes so one map's
        # trailing use cannot exempt another map through loose tied matching.
        cursor = neighbor
        step = -1 if neighbor < decl else 1
        while 0 <= cursor < len(lines) and not lines[cursor].strip():
            cursor += step
        if _is_top_level_statement(lines, depths, cursor) and is_map_declaration(lines[cursor]):
            return declaration
    collected = [declaration]
    backward = []
    cursor = decl - 1
    while len(backward) < TOP_LEVEL_SCOPE_LINES and cursor >= 0:
        if not lines[cursor].strip() or not _is_top_level_statement(lines, depths, cursor):
            break
        if is_map_declaration(lines[cursor]):
            break
        backward.append(lines[cursor])
        cursor -= 1
    forward = []
    cursor = decl + 1
    while len(forward) < TOP_LEVEL_SCOPE_LINES and cursor < len(lines):
        if not lines[cursor].strip() or not _is_top_level_statement(lines, depths, cursor):
            break
        if is_map_declaration(lines[cursor]):
            break
        forward.append(lines[cursor])
        cursor += 1
    return "\n".join(backward[::-1] + collected + forward)


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


def is_retention_exempt(rel: str, symbol: str, retention_rules) -> bool:
    """Check file-level (legacy) or symbol-level retention exemptions."""
    # Symbol keys use "relative/path:mapSymbol". File keys exempt every map
    # in the file. Slice 19 narrows exemptions to symbol keys where feasible.
    rules = retention_rules or {}
    return rel in rules or f"{rel}:{symbol}" in rules


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
        # match() with pos returns absolute offsets, so use prefix.end() directly.
        start = prefix.end() - 1
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
        map_matches = sorted(
            list(MAP_RE.finditer(text)) + list(REMEMBERED_MAP_RE.finditer(text)),
            key=lambda m: m.start(),
        )
        seen_maps = set()
        for map_match in map_matches:
            symbol = map_match.group(1)
            key = (symbol, map_match.start())
            if key in seen_maps:
                continue
            seen_maps.add(key)
            if not has_retention_evidence(text, symbol, map_match.start(), raw) and not is_retention_exempt(rel, symbol, retention_rules):
                rows.append(finding("unretained-long-lived-map", rel, symbol))
        if rel.endswith(DUPLICATE_PAGING_OWNER_SUFFIX):
            seen_paging = set()
            for paging in DUPLICATE_PAGING_RE.finditer(text):
                symbol = paging.group(1)
                if symbol in seen_paging:
                    continue
                seen_paging.add(symbol)
                rows.append(finding("duplicate-paging-ownership", rel, symbol, symbol=symbol))
        constructions = []
        for authority in EXECUTION_AUTHORITY_CONSTRUCTION_RE.finditer(text):
            line_start = text.rfind("\n", 0, authority.start()) + 1
            line_end = text.find("\n", authority.end())
            line = text[line_start:] if line_end < 0 else text[line_start:line_end]
            if "class PostInteractionExecutionAuthority" in line:
                continue
            constructions.append(authority)
        if constructions and (
            rel not in EXECUTION_AUTHORITY_CANONICAL_FILES or len(constructions) > 1
        ):
            rows.append(finding("post-execution-duplication", rel, "PostInteractionExecutionAuthority", symbol="PostInteractionExecutionAuthority"))
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
        if row["kind"] in WARNING_KINDS: item["classification"] = "warning"
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
    percent = thresholds["hotspotGrowthPercent"] / 100
    for file, old in baseline.get("fileMetrics", {}).items():
        new = report["fileMetrics"].get(file)
        # File hotspot needs both percentage growth and absolute growth.
        if new and old.get("lines", 0) and new["lines"] > old["lines"] * (1 + percent) and new["lines"] - old["lines"] >= thresholds["hotspotGrowthLines"]:
            out.append(finding("hotspot-growth", file, f"{old['lines']} -> {new['lines']} lines", symbol="<file>", classification="regression"))
    old_functions = {(x.get("file"), x.get("name")): x for x in baseline.get("functionMetrics", [])}
    for new in report.get("functionMetrics", []):
        old = old_functions.get((new.get("file"), new.get("name")))
        if not old:
            continue
        changed = []
        # Function growth needs both percentage growth and absolute growth.
        # A small change above a warning threshold is not material by itself.
        for metric, absolute in (("lines", FUNCTION_GROWTH_LINES), ("params", FUNCTION_GROWTH_STEPS), ("nesting", FUNCTION_GROWTH_STEPS)):
            before, after = old.get(metric, 0), new.get(metric, 0)
            if after > before and before and after > before * (1 + percent) and after - before >= absolute:
                changed.append(f"{metric} {before}->{after}")
        if changed:
            out.append(finding("function-complexity-growth", new["file"], f"{new['name']}: {', '.join(changed)}", symbol=new["name"], classification="regression"))
    return out


def build_baseline(report: dict, previous: dict) -> dict:
    """Build a deterministic baseline from a reviewed report."""
    # Refresh schemaVersion and parserVersion to current values.
    # Keep manual review sections from the previous baseline.
    # Refresh only the generated metrics and versions.
    # Note: the current baseline holds hotspot-only fileMetrics and empty functionMetrics.
    # Record full metrics with `python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --record-baseline tools/architecture-baseline.json` on clean reviewed source.
    # Review the diff before commit.
    # Growth detection applies only to entries present in the baseline.
    # New files still receive normal findings through the standard checks.
    return {
        "schemaVersion": SCHEMA_VERSION,
        "parserVersion": PARSER_VERSION,
        "thresholds": previous.get("thresholds", dict(DEFAULT_THRESHOLDS)),
        "existingFindings": sorted(previous.get("existingFindings", [])),
        "enforcedFindings": sorted(previous.get("enforcedFindings", [])),
        "resolvedFindings": sorted(previous.get("resolvedFindings", [])),
        "fileMetrics": {key: report["fileMetrics"][key] for key in sorted(report["fileMetrics"])},
        "functionMetrics": sorted(report.get("functionMetrics", []), key=lambda x: (x.get("file"), x.get("name"))),
        "retentionRules": {key: previous.get("retentionRules", {}).get(key, "") for key in sorted(previous.get("retentionRules", {}))} if previous.get("retentionRules") else {},
        "allowlists": {key: sorted(value) if isinstance(value, list) else value for key, value in sorted(previous.get("allowlists", {}).items())},
    }


def write_baseline(path: Path, data: dict) -> None:
    # Use sorted keys so repeated records stay byte-identical.
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("root", nargs="?", default=".")
    ap.add_argument("--baseline", type=Path)
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--json", action="store_true")
    # Controlled record path. Review the diff before commit.
    # Never absorb unrelated dirty files into the baseline.
    ap.add_argument("--record-baseline", type=Path, default=None)
    args = ap.parse_args(argv)
    baseline_data = json.loads(args.baseline.read_text(encoding="utf-8")) if args.baseline and args.baseline.exists() else {}
    report = audit(Path(args.root), baseline_data.get("retentionRules"), baseline_data.get("thresholds"), baseline_data.get("allowlists"))
    if args.baseline and args.baseline.exists():
        report = classify(report, baseline_data)
    elif args.check and args.record_baseline is None:
        report = classify(report, baseline_data)
    if args.record_baseline is not None:
        # Record from clean reviewed source only. Keep --check unchanged
        # when this flag is absent. Exit 0 so record never fails a review.
        fresh = audit(Path(args.root), baseline_data.get("retentionRules"), baseline_data.get("thresholds"), baseline_data.get("allowlists"))
        write_baseline(args.record_baseline, build_baseline(fresh, baseline_data))
        if args.json: print(json.dumps(report, indent=2, sort_keys=True))
        else:
            for row in report["findings"]: print(f"{row['classification']}: {row['id']}")
            print(f"Architecture audit: {len(report['findings'])} findings")
        return 0
    if args.json: print(json.dumps(report, indent=2, sort_keys=True))
    else:
        for row in report["findings"]: print(f"{row['classification']}: {row['id']}")
        print(f"Architecture audit: {len(report['findings'])} findings")
    return 1 if args.check and report.get("regressions") else 0


if __name__ == "__main__": sys.exit(main())
