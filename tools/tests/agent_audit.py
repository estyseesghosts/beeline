"""Audit every Beeline subagent against its declared purpose.

The tool computes the effective OpenCode V2 permission for realistic operations
and compares the result with what each agent's prompt says the agent is for. A
read-only agent must be read-only. An agent that must edit files, use the shell,
use adb, or use Git must be able to do exactly that.

It reuses the rule resolution from permission_matrix, so both tools model the
V2 order: base policy first, then agent rules, last match wins.

Run it from the repository root:

    python tools/tests/agent_audit.py
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from permission_matrix import BASE_POLICY, load_agent, resolve  # noqa: E402

AGENT_DIR = ".opencode/agents"

# Windows resolves only ".\gradlew.bat". The bare name does not resolve because
# "." is not on PATH.
#
# AGENTS.md requires every agent Gradle command to start with the two flags, so
# the wrapper form that matters is ".\gradlew.bat --no-daemon --console=plain".
# A rule written as "gradlew.bat test *" matches neither the Windows path nor
# the flagged ordering, so both forms are probed here.
GRADLE_WIN = ".\\gradlew.bat --no-daemon --console=plain test"
GRADLE_UNIX = "./gradlew --no-daemon --console=plain test"
GRADLE_BARE = "gradlew.bat --no-daemon --console=plain test"

# Shell primitives that can change the filesystem. A read-only agent must
# resolve every one of them to deny, including through redirection.
WRITE_SHELL = [
    "echo hello > out.txt",
    "printf hello > out.txt",
    "tee out.txt",
    "cp a.kt b.kt",
    "mv a.kt b.kt",
    "rm -rf build",
    "mkdir newdir",
    "touch newfile",
    "sed -i s/a/b/ file.kt",
]

GIT_MUTATIONS = [
    "git add .",
    "git commit -m x",
    "git push origin main",
    "git reset --hard",
    "git checkout -- .",
    "git stash",
    "git restore .",
    "git worktree add ../x",
]

GIT_READ = [
    ("shell", "git status", "allow"),
    ("shell", "git diff", "allow"),
    ("shell", "git log -5", "allow"),
]

# Every agent must be able to read project source but must not read environment
# files without approval. The base policy asks; a broad read allow erases that.
SECRET_OPS = [
    ("read", ".env", "ask"),
    ("read", ".env.production", "ask"),
]

BASE_OPS = [
    ("read", "app/build.gradle.kts", "allow"),
    ("glob", "**/*.kt", "allow"),
    ("grep", "TODO", "allow"),
]

# A reviewer may run the listed verification tasks. It must still be blocked
# from Gradle tasks outside that set, so the new rules must not over-permit.
REVIEWER_GRADLE_DENY = [
    ("shell", ".\\gradlew.bat --no-daemon --console=plain clean", "deny"),
    ("shell", ".\\gradlew.bat --no-daemon --console=plain publish *", "deny"),
    ("shell", ".\\gradlew.bat --no-daemon --console=plain sign*", "deny"),
]

ANALYSIS_SCRIPTS = [
    ("shell", "python tools/scripts/repo_map.py --root .", "allow"),
    ("shell", "python3 tools/scripts/file_audit.py --root .", "allow"),
]


def read_only_checks(gradle="deny", scripts=True):
    """Operations that must be denied for a read-only agent."""
    checks = [("edit", "app/src/Main.kt", "deny")]
    checks += [("shell", cmd, "deny") for cmd in WRITE_SHELL]
    checks += [("shell", cmd, "deny") for cmd in GIT_MUTATIONS]
    checks += [
        ("shell", GRADLE_WIN, gradle),
        ("shell", GRADLE_UNIX, gradle),
        ("shell", GRADLE_BARE, gradle),
    ]
    if scripts:
        checks += ANALYSIS_SCRIPTS
    return checks


PROFILES = {
    "problem_solver_low": {
        "role": "analyze and plan only",
        "git_read": True,
        "checks": read_only_checks(),
    },
    "problem_solver_high": {
        "role": "analyze and plan only",
        "git_read": True,
        "checks": read_only_checks(),
    },
    "targeted_fixer": {
        "role": "implement changes and build, never stage or commit",
        "git_read": True,
        "checks": [
            ("edit", "app/src/Main.kt", "allow"),
            ("shell", GRADLE_WIN, "allow"),
            ("shell", GRADLE_UNIX, "allow"),
            ("shell", "python tools/scripts/repo_map.py --root .", "allow"),
        ]
        + [("shell", cmd, "deny") for cmd in GIT_MUTATIONS]
        + [("shell", cmd, "deny") for cmd in WRITE_SHELL]
        + ANALYSIS_SCRIPTS,
    },
    "code_reviewer_low": {
        "role": "audit changes and run verification, never edit",
        "git_read": True,
        "checks": read_only_checks(gradle="allow") + REVIEWER_GRADLE_DENY,
    },
    "code_reviewer_high": {
        "role": "deep audit and run verification, never edit",
        "git_read": True,
        "checks": read_only_checks(gradle="allow") + REVIEWER_GRADLE_DENY,
    },
    "git_handler": {
        "role": "Git state only, never touch project files",
        "git_read": True,
        "checks": [
            ("edit", "app/src/Main.kt", "deny"),
            ("shell", "git add -- app/src/Main.kt", "allow"),
            ("shell", "git commit -m x", "allow"),
            ("shell", "git push origin main", "ask"),
            ("shell", "git add -A", "ask"),
            ("shell", "git commit --amend -m x", "ask"),
            ("shell", "git push --force origin main", "deny"),
            ("shell", "git reset --hard", "deny"),
            ("shell", "git checkout -- .", "deny"),
            ("shell", GRADLE_WIN, "deny"),
            ("shell", GRADLE_UNIX, "deny"),
        ]
        + [("shell", cmd, "deny") for cmd in WRITE_SHELL],
    },
    "adb_handler": {
        # The prompt states "Never run non-adb shell commands, Git, or Gradle",
        # so Git must stay denied here. This is the one agent with no Git rules.
        "role": "device checks through adb only, never touch project files",
        "git_read": False,
        "checks": [
            ("edit", "app/src/Main.kt", "deny"),
            ("shell", "adb devices", "allow"),
            (
                "shell",
                "C:\\Users\\julie\\Documents\\platform-tools\\adb.exe devices",
                "allow",
            ),
            ("shell", "python tools/scripts/adb_inspect.py --full", "allow"),
            ("shell", "git status", "deny"),
            ("shell", GRADLE_WIN, "deny"),
            ("shell", GRADLE_UNIX, "deny"),
        ]
        + [("shell", cmd, "deny") for cmd in GIT_MUTATIONS]
        + [("shell", cmd, "deny") for cmd in WRITE_SHELL],
    },
    "codebase_explorer_android": {
        "role": "read-only exploration",
        "git_read": True,
        "checks": [
            ("edit", "app/src/Main.kt", "deny"),
            ("shell", "cat settings.gradle.kts", "allow"),
            # The explorer's own prompt documents these unflagged forms.
            ("shell", "./gradlew projects", "allow"),
            ("shell", ".\\gradlew.bat projects", "allow"),
        ]
        + [("shell", cmd, "deny") for cmd in GIT_MUTATIONS]
        + [("shell", cmd, "deny") for cmd in WRITE_SHELL],
    },
}


def run():
    files = sorted(
        f for f in os.listdir(AGENT_DIR) if f.endswith(".md") and f != "README.md"
    )
    total = 0
    failures = 0
    per_agent = {}

    for filename in files:
        name = filename[:-3]
        profile = PROFILES.get(name)
        if not profile:
            continue
        rules = BASE_POLICY + load_agent(os.path.join(AGENT_DIR, filename))
        cases = list(BASE_OPS) + list(SECRET_OPS) + list(profile["checks"])
        if profile["git_read"]:
            cases = list(BASE_OPS) + list(GIT_READ) + list(SECRET_OPS) + list(
                profile["checks"]
            )
        rows = []
        for action, resource, want in cases:
            got = resolve(rules, action, resource)
            ok = got == want
            total += 1
            if not ok:
                failures += 1
            rows.append((action, resource, got, want, ok))
        per_agent[name] = (profile["role"], rows)

    for name, (role, rows) in per_agent.items():
        bad = [r for r in rows if not r[4]]
        state = "OK" if not bad else f"{len(bad)} PROBLEM(S)"
        print(f"\n=== {name} -- {role} -- {state} ===")
        for action, resource, got, want, _ in bad:
            print(f"  {action:<7} {resource:<50} got={got:<6} want={want}")

    print(f"\n{'=' * 78}")
    print(f"{total - failures}/{total} expectations met across {len(per_agent)} agents")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(run())
