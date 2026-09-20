# Beeline codebase navigation/audit scripts

Python 3 only. No third-party packages are required.

Recommended location inside Beeline: `tools/scripts/`.

## repo_map.py
Maps Android modules, Kotlin/Java source sets, packages, broad architecture tags, declarations, and the largest source files.

## architecture_audit.py
Audits production Kotlin for package/path drift, root-UI feature files, ownership defaults,
dependency direction, long-lived maps, and size metrics. It is deterministic and uses
`tools/architecture-baseline.json` to separate existing debt from regressions. Large files are
reported as warnings and do not fail the check by themselves.

```bash
python3 tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check
python3 tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --json
```

The parser is heuristic. Add a retention rule to the baseline for a long-lived map only when its
owner, lifetime, and release or bound policy are documented in the source or architecture record.

```bash
python3 tools/scripts/repo_map.py .
python3 tools/scripts/repo_map.py . --top 80
python3 tools/scripts/repo_map.py . --json > /tmp/beeline-map.json
```

## function_audit.py
Finds Kotlin/Java functions that are unusually long, parameter-heavy, branch-heavy, or deeply nested.

```bash
python3 tools/scripts/function_audit.py .
python3 tools/scripts/function_audit.py . --max-lines 100 --max-params 8 --max-nesting 6
python3 tools/scripts/function_audit.py . --all --json > /tmp/functions.json
```

Default review thresholds: >80 lines, >7 parameters, >5 nesting levels, or >16 branch/control tokens. These are investigation triggers, not automatic refactor rules.

## file_audit.py
Finds files with multiple god-file risk signals: LOC, function count, type count, public declarations, and imports. Also shows Compose function count.

```bash
python3 tools/scripts/file_audit.py .
python3 tools/scripts/file_audit.py . --max-lines 700 --max-functions 35
python3 tools/scripts/file_audit.py . --all --top 200
```

Do not split a file only because it crosses a threshold. Inspect responsibility/cohesion first.

## trace_symbol.py
Searches code, resources, Gradle files, and docs for a symbol and prints line context.

```bash
python3 tools/scripts/trace_symbol.py TimelineViewModel .
python3 tools/scripts/trace_symbol.py mediaSensitive . -C 2
python3 tools/scripts/trace_symbol.py 'Favourite|Reaction' . --regex
```

## change_surface.py
Uses Git to list changed files and finds likely neighboring files by referenced declarations/file names.

```bash
python3 tools/scripts/change_surface.py .
python3 tools/scripts/change_surface.py . --base main
```

Useful before review to locate tests, UI callers, protocol adapters, and resources that may need verification.

## Suggested agent workflow

```bash
python3 tools/scripts/repo_map.py .
python3 tools/scripts/change_surface.py .
python3 tools/scripts/function_audit.py .
python3 tools/scripts/file_audit.py .
```

Then use `trace_symbol.py` for specific classes, functions, DTOs, routes, settings, resources, or API methods.

### Limits

These scripts use lightweight parsing/heuristics. Kotlin syntax can be complex. Results are navigation aids and review signals, not compiler-grade dependency or complexity analysis.
