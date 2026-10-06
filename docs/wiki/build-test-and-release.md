# Build, Test, and Release

Status: current
Owner: Maintainers
Last reviewed: 2026-10-05
Stale when: Gradle, CI, signing, release, or verification requirements change.

Sources: `AGENTS.md`, `app/build.gradle.kts`, `.github/workflows/`, and test configuration.

## Purpose

Explain how contributors build, test, lint, and release Beeline.

## Verified local procedure

The application uses the repository wrapper with `--no-daemon --console=plain`.
Windows runs use `.\gradlew.bat`. Unix runs use `./gradlew`.
Each agent run sets an explicit timeout and closes standard input.
Gradle 9.6.0 is current. Unit tests use Java toolchain 21.
The SDK path comes from `local.properties`.
Fresh unit-test XML lives under `app/build/test-results/testDebugUnitTest/`.
Focused runs overwrite that directory, so R00 preserves sanitized attribution in `logs/261001-010000.txt`.
R00 verified this procedure for grouped, isolated, and full-gate runs.
Device, live-server, and signing checks remain separate and unverified here.

## Detailed command inventory

### Focused verification

Run affected test classes before the completion gate. For example, on Windows:

```powershell
cmd /c '.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests "*AppLayoutDirectionTest" --tests "*SettingsDisplayTest" < NUL'
```

On Unix:

```sh
./gradlew --no-daemon --console=plain :app:testDebugUnitTest --tests '*AppLayoutDirectionTest' --tests '*SettingsDisplayTest' < /dev/null
```

Choose the affected suites for the actual change. Focused checks do not replace the completion gate.

### Local CI-parity completion gate

The [CI build job](../../.github/workflows/android.yml) defines the complete gate.
“Full gate” means architecture-tool tests, architecture audit, all debug unit tests, lint, ktlint, and both assemblies.

Windows PowerShell:

```powershell
$env:GRADLE_OPTS = '-Dorg.gradle.daemon=false'
python -m unittest discover -s tools/tests
python tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check
cmd /c '.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug :app:ktlintCheck :app:assembleDebug :app:assembleRelease < NUL'
```

Unix:

```sh
export GRADLE_OPTS=-Dorg.gradle.daemon=false
python3 -m unittest discover -s tools/tests
python3 tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check
./gradlew --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug :app:ktlintCheck :app:assembleDebug :app:assembleRelease < /dev/null
```

Require exit code zero from each command. Stop on failure rather than treating later successes as a passing gate.
Agents also set an explicit tool timeout. Additional feature-specific checks remain required when applicable.
Documentation-only changes require document and configuration checks instead of Android builds.

### Runtime and release evidence

The `instrumentation-api29` CI job runs `:app:connectedDebugAndroidTest` on an API 29 emulator after the build job passes.
Local emulator or selected-device runs use the same task with the wrapper and required execution rules.
Do not push just to obtain remote evidence without user authorization.

JVM, Robolectric, and mocked HTTP tests verify local behavior. They do not establish physical rendering or live-server compatibility.
Emulator instrumentation verifies that emulator configuration. Physical-device checks establish evidence only for the selected device and tested flows.
Live-server checks require authorized accounts and separate protocol evidence. Record unavailable checks explicitly.

Release assembly can produce an unsigned APK. Signing is a separate release concern.
Protected release jobs supply the signing inputs configured in [app/build.gradle.kts](../../app/build.gradle.kts).
Successful local assembly does not establish signing, distribution, API 29, TalkBack, or live-server acceptance.
