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

### CI check commands
Run tool tests before the audit check. Run the audit check before Gradle steps. Use these exact commands in CI:

```bash
python3 -m unittest discover -s tools/tests
python3 tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check
```

Use this command for full JSON output during review:

```bash
python3 tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --json
```

The audit fails with exit 1 only when actionable regressions exist. Gradle behavior stays unchanged.

### Source-root scope
The audit scans production Kotlin only. It scans these roots in fixed order:

- `app/src/main/java`
- `app/src/main/kotlin`

It ignores missing roots. It ignores test sources. It ignores other source sets.

### Report fields
The JSON report has these fields:

- `parserVersion`: parser version. Use it to detect heuristic changes.
- `findings`: sorted rows. Each row has `id`, `kind`, `file`, `detail`, and `classification`. Some rows also have `symbol`.
- `fileMetrics`: map from relative file path to `lines`.
- `functionMetrics`: sorted list of `file`, `name`, `lines`, `params`, and `nesting` for each function body.
- `regressions`: subset of `findings` with a `classification` that starts with `regression`.

Finding `id` values stay stable across runs. The `id` uses the symbol name. It never uses a metric value.

### Classifications
The audit assigns one classification to each finding:

- `warning`: review signal only. It never fails CI by itself.
- `existing-baseline`: known debt listed in `existingFindings`. It does not fail CI.
- `regression`: new violation, enforced violation, file hotspot growth, or function complexity growth. It fails CI.
- `regression-returned-resolved`: violation listed in `resolvedFindings` that returned. It fails CI.

Warning kinds never fail `--check` alone:

- `ownership-class`
- `dependency-direction`
- `file-size-warning`
- `function-size-warning`
- `function-parameter-warning`
- `function-nesting-warning`

Size warnings do not fail CI. File growth and function growth still fail CI when growth is material. File growth needs both percentage growth and absolute growth. Function growth needs both percentage growth and absolute growth.

### Owner-like class documentation expectation
Any new Owner, Authority, Manager, Controller, or Coordinator reports an `ownership-class` warning. The warning does not fail CI. It asks for review. Document six items for each new owner-like class:

- State owned.
- Lifetime.
- Creation point.
- Release point.
- Who may mutate it.
- Why an existing owner cannot own this.

No answer means no new abstraction.

### Retention-rule requirements
Add a retention rule to the baseline for a long-lived map only when its owner, lifetime, and release or bound policy are documented in the source or architecture record. Use the relative file path as the key. Write a short reason as the value. Keep evidence near the map. Valid evidence includes `clear`, `remove`, `invalidate`, `release`, `evict`, expiry terms, prune terms, maximum terms, bound terms, or size limits in the same owner scope. An explicit retention comment near the map also counts.

### Parser heuristic limits
The parser uses lightweight regular expressions. It is not compiler-grade analysis. It has these limits:

- Regex-based scan only. It masks comments and strings before matching.
- Multiline function signatures supported. Multiline constructor defaults supported.
- Class-body excluded for `private-owner-construction`. The check scans primary constructor parameter lists and secondary constructor parameter lists only. Construction in class bodies and function bodies stays out of scope.
- Dependency check is import-based. It reads `import` lines only. It skips composition roots explicitly.
- Retention evidence stays in the same owner body. Evidence from an unrelated scope does not count.
- Results are review signals unless classified as regressions. Verify complex cases by reading the source.

### Controlled baseline update process
Update the baseline only from clean reviewed source. Review the diff before commit. Never absorb unrelated dirty files into the baseline. Use this command:

```bash
python3 tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --record-baseline tools/architecture-baseline.json
```

Record refreshes `schemaVersion` and `parserVersion` to current values. Record refreshes generated metrics. Record preserves manual sections: `thresholds`, `existingFindings`, `enforcedFindings`, `resolvedFindings`, `retentionRules`, and `allowlists`. Note: the current baseline holds hotspot-only `fileMetrics` and empty `functionMetrics`. Growth detection applies only to entries present in the baseline. New files still receive normal findings through the standard checks.

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

## New owner rule and Cohesion rule

Any new Owner, Authority, Manager, Controller, or Coordinator must document six items.
List State owned.
List Lifetime.
List Creation point.
List Release point.
List Who may mutate it.
State Why an existing owner cannot own this.
No answer means no new abstraction.
Size warnings follow AGENTS.md regression rules and do not force splits on their own.
