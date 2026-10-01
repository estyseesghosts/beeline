# Build, Test, and Release

Status: current
Owner: Maintainers
Last reviewed: 2026-10-01
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

The detailed command inventory remains pending.
