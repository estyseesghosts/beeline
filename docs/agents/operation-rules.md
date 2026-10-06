# Beeline Agent Operation Rules

Status: current
Owner: Maintainers
Last reviewed: 2026-10-05
Stale when: Git, Gradle, device responsibilities, or permission boundaries change.

Authority: [agent control](agent-control.md), [workflow](workflow.md), and [agent definitions](../../.opencode/README.md).
Build authority: [Gradle configuration](../../app/build.gradle.kts), [wrapper](../../gradlew.bat), and [CI](../../.github/workflows/).

## Operators and scope

The orchestrator may implement, edit records, run Gradle, inspect devices through ADB, and operate Git.
It has general shell access for task-required PowerShell commands, interpreters, diagnostics, and other development tools.
Its Gradle permission does not require exact flag order. Validation runs still require the project flags below.
Targeted_fixer may implement and validate its small work package. It may operate Git only under explicit assignment instructions.
Problem_solver only investigates and reviews source and evidence. It does not edit, run Gradle, inspect devices, or mutate Git.

Assign one implementation owner for each slice and one Git operator for each checkpoint.
Normally the orchestrator is the Git operator. Name the fixer explicitly when it should stage or commit.
Do not operate Git concurrently with a child session. Do not edit a delegated implementation scope concurrently.
Tool access does not authorize actions outside the user task or dispatch contract.

## Git checkpoint

1. Inspect Git status as a separate command.
2. Inspect staged and unstaged diffs separately.
3. Identify unrelated staged, modified, deleted, and untracked files.
4. Confirm that validation and required review pass for the slice.
5. Stage explicit reviewed file paths with `git add -- <paths>`.
6. Inspect the final staged slice diff, including its documentation and records.
7. Commit only the slice paths with a clear imperative message.
8. Report the commit hash and included files.

When unrelated content is already staged, preserve it. Do not unstage or discard it to simplify the commit.
Use `git commit --only ... -- <slice paths>` for separately owned files when that excludes the unrelated index content safely.
If a file contains mixed ownership changes, stop and resolve the boundary before staging or committing it.
Do not use broad staging, commit-all flags, or amend unless the user explicitly approves the exact operation.
Unknown Git operations ask for approval. Do not bypass that decision with another tool.
Common repository-root pathspecs also ask, including `.`, `./`, `.\`, and `:/`, alone or after explicit paths.
The matcher cannot validate every shell-expanded pathspec or distinguish files from directories.
Use literal reviewed file paths even when the permission rule would allow another form.
Never restore, reset, clean, stash, checkout, switch, rebase, merge, cherry-pick, or force-push to make a slice easier.
Do not amend existing history unless the user explicitly requests it.
Never push unless the user explicitly requests it. A fixer also needs the operation assigned by the orchestrator.

## Gradle

Use the repository wrapper for every Gradle command.

```text
.\gradlew.bat --no-daemon --console=plain <tasks>
./gradlew --no-daemon --console=plain <tasks>
```

PowerShell requires `.\gradlew.bat`; the current directory is not on PATH.
Use an explicit timeout and closed standard input for non-interactive runs.
Set `GRADLE_OPTS=-Dorg.gradle.daemon=false` in the agent environment when supported.
Do not bypass denied shell commands to set it. Report environment limitations instead.
Run the smallest relevant check first, then required gates from [engineering rules](engineering-rules.md#verification).
Run focused checks, then the complete local CI-parity gate before declaring a code task complete.
The [engineering verification contract](engineering-rules.md#verification) defines that gate. Run additional feature-required checks when applicable.
Do not weaken tests or use source-rewriting tasks to force a green result.
Record pre-existing failures separately from failures caused by the slice.
For documentation-only work, use document and configuration checks instead of Android builds.

## ADB and diagnostic scripts

The orchestrator owns device operations. Do not dispatch a device specialist agent.
Confirm the intended device or emulator before commands. Select the device explicitly when more than one can exist.
ADB is not on PATH in this environment. Use `C:\Users\julie\Documents\platform-tools\adb.exe` for raw commands.
Prefer these companion entry points when they exist under tools/scripts/:

- `adb_control.py`: bounded device input and device information.
- `adb_screenshot.py`: screenshot streaming to the host.
- `adb_inspect.py`: screenshot and device or activity state.
- `adb_flow.py`: an assigned sequence of device actions.

These scripts include untracked local work. A fresh clone does not contain them.
Do not add, rewrite, or replace scripts under a documentation-only task.
Use exec-out for screenshot streaming. Do not use adb pull for this workflow.
Use the diagnostic output location assigned by the task. Do not write source or configuration through scripts.
Dump the UI hierarchy only when element text, bounds, semantics, clickable state, or hierarchy is needed.
Do not capture credentials, tokens, complete API bodies, or unrelated private content.
Stop when device state differs from the task expectation.
Install, remove, reset, or change device state only when the user task explicitly requires that operation.

## Shell and evidence limits

Use one shell command per invocation. Do not chain commands to bypass permissions.
Use editing tools for file changes. Do not use redirection or interpreter payloads to evade edit or Git restrictions.
The fixer's named script permissions assume the script exists and that its behavior fits the task scope.
The orchestrator may use inline interpreter programs for task-required analysis and validation.
General shell access is not a sandbox. It can reach files, network resources, and Git indirectly.
Do not use that access to bypass Git safeguards, secret-read approval, external-directory approval, or denied operations.
Prompts and the work contract still constrain use. Direct-command permission probes cannot prove indirect-command safety.
Static permission probes model rule resolution; they do not prove live enforcement or command safety.
Do not treat mocked tests as live-server evidence, or Compose tests as physical rendering evidence.
Report device, live-server, signing, and interactive permission checks that remain unavailable.
