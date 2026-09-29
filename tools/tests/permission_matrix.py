"""Simulate the OpenCode V2 permission resolution for one agent file.

OpenCode V2 uses the last matching rule. This script reproduces that order:
the base policy first, then the agent rules. It reports the final effect for a
list of test operations.

Run it from the repository root:

    python tools/tests/permission_matrix.py .opencode/agents/orchestrator.md
"""

import re
import sys

try:
    import yaml
except ImportError:
    sys.exit("PyYAML is not available")

# Every agent starts with this ordered base policy.
BASE_POLICY = [
    {"action": "*", "resource": "*", "effect": "allow"},
    {"action": "external_directory", "resource": "*", "effect": "ask"},
    {"action": "read", "resource": "*.env", "effect": "ask"},
    {"action": "read", "resource": "*.env.*", "effect": "ask"},
    {"action": "read", "resource": "*.env.example", "effect": "allow"},
]

# build ships with one extra rule. Custom agents do not inherit it.
SHIPPED_BUILD = [
    {"action": "question", "resource": "*", "effect": "allow"},
]

CACHE = {}


def wildcard_match(pattern, value, shell_resource=False):
    """Match one V2 wildcard pattern. Only * and ? are special.

    For a shell resource, a pattern ending in " *" also matches the command
    without arguments. The OpenCode V2 permission guide states this rule, and it
    is why "git status *" covers both "git status" and "git status --short".
    """
    key = (pattern, value, shell_resource)
    if key in CACHE:
        return CACHE[key]

    if shell_resource and pattern.endswith(" *"):
        # The bare prefix is the same command with no arguments.
        prefix = pattern[:-2]
        prefix_regex = "".join(
            ".*" if c == "*" else "." if c == "?" else re.escape(c) for c in prefix
        )
        if re.fullmatch(prefix_regex, value, re.IGNORECASE):
            CACHE[key] = True
            return True

    regex = "".join(
        ".*" if c == "*" else "." if c == "?" else re.escape(c) for c in pattern
    )
    result = re.fullmatch(regex, value, re.IGNORECASE) is not None
    CACHE[key] = result
    return result


def resolve(rules, action, resource):
    """Return the last matching effect, or ask when nothing matches."""
    effect = "ask"
    for rule in rules:
        action_matches = wildcard_match(rule["action"], action)
        resource_matches = wildcard_match(
            rule["resource"], resource, shell_resource=(action == "shell")
        )
        if action_matches and resource_matches:
            effect = rule["effect"]
    return effect


def load_agent(path):
    text = open(path, encoding="utf-8").read()
    match = re.match(r"^---\r?\n(.*?)\r?\n---\r?\n", text, re.S)
    if not match:
        sys.exit(f"no frontmatter in {path}")
    return yaml.safe_load(match.group(1)).get("permissions", [])


# action, resource, expected effect
CASES = [
    ("read", "app/build.gradle.kts", "allow"),
    ("read", "local.properties", "allow"),
    ("read", ".env", "ask"),
    ("read", "secrets/.env", "ask"),
    ("read", ".env.production", "ask"),
    ("read", ".env.example", "allow"),
    ("glob", "**/*.kt", "allow"),
    ("grep", "TODO", "allow"),
    ("question", "*", "allow"),
    ("external_directory", "C:/Users/julie/reference/*", "ask"),
    ("shell", "git status", "allow"),
    ("shell", "git status --short", "allow"),
    ("shell", "git diff", "allow"),
    ("shell", "git diff --stat HEAD", "allow"),
    ("shell", "git log -5", "allow"),
    ("shell", "git show HEAD", "allow"),
    ("shell", "git rev-parse HEAD", "allow"),
    ("shell", "git ls-files", "allow"),
    ("shell", "git branch", "ask"),
    ("shell", "git config --list", "ask"),
    ("shell", "git commit -m test", "deny"),
    ("shell", "git add .", "deny"),
    ("shell", "git push origin main", "deny"),
    ("shell", "git reset --hard", "deny"),
    ("shell", "git stash", "deny"),
    ("shell", "ls -la", "deny"),
    ("shell", "gradlew.bat test", "deny"),
    ("edit", "AGENTS.md", "deny"),
    ("edit", "docs/agents/handoff.md", "deny"),
    ("subagent", "targeted_fixer", "allow"),
    ("subagent", "problem_solver_low", "allow"),
    ("subagent", "problem_solver_high", "allow"),
    ("subagent", "code_reviewer_low", "allow"),
    ("subagent", "code_reviewer_high", "allow"),
    ("subagent", "git_handler", "allow"),
    ("subagent", "adb_handler", "allow"),
    ("subagent", "codebase_explorer_android", "allow"),
    ("subagent", "general", "deny"),
    ("subagent", "explore", "deny"),
    ("webfetch", "https://opencode.ai/v2/docs/", "deny"),
    ("websearch", "misskey api", "deny"),
    ("skill", "report", "deny"),
]


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else ".opencode/agents/orchestrator.md"
    rules = BASE_POLICY + load_agent(path)

    print(f"agent file: {path}")
    print(f"total rules after merge: {len(rules)}")
    print()
    print(f"{'action':<20} {'resource':<34} {'result':<7} {'want':<7} ok")
    failures = 0
    for action, resource, want in CASES:
        got = resolve(rules, action, resource)
        ok = got == want
        if not ok:
            failures += 1
        print(f"{action:<20} {resource:<34} {got:<7} {want:<7} {'ok' if ok else 'FAIL'}")
    print()
    print(f"{len(CASES) - failures}/{len(CASES)} cases match the expected effect")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
