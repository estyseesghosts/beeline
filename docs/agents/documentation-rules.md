# Beeline Documentation Rules

Status: current
Owner: Maintainers
Last reviewed: 2026-09-30
Stale when: Documentation authority, coverage, or maintenance rules change.

Authority: [AGENTS.md](../../AGENTS.md) and [writing style](../../importantdocs/writing_style.md).
Current source, tests, Gradle files, manifests, resources, and CI verify implementation claims.
Logs record historical evidence and verification limits; they do not establish current architecture.

## Audiences and ownership

- Keep README short and user-facing. Keep stable contributor guidance in the human wiki.
- Keep comments and KDoc near code. Keep ownership and fragile invariants in the agent wiki.
- Cover owners, invariants, protocol boundaries, persistence contracts, affected tests, and verification limits.
- Do not copy source code into the wiki. Do not maintain two independent architecture descriptions.
- Keep generated API material in reference documentation with a repeatable generator or pinned source.
- Keep temporary plans and task history outside permanent architecture pages.
- Give maintained pages an owner, status, review date, and links to relevant authorities and checks.

## Evidence and status

Verify each implementation claim before publication. Do not treat old plans, reports, or TODO lists as evidence.
Do not describe planned behavior as implemented behavior. Do not describe partial behavior as complete behavior.
Use `source verified`, `test verified`, `device verified`, `live verified`, and `unverified` precisely.
State when runtime, device, or live-server evidence is unavailable.

Classify reviewed documents as current, planned, historical, reference, or stale.
Give planned documents an owner, review date, and a condition that makes them stale.
Update stale pages that still explain useful current behavior or contributor processes.
Archive useful history explicitly. Delete obsolete guidance with no current or historical value.
Prefer Git history and task logs over retained obsolete technical instructions.
Do not leave known stale guidance active beside a replacement presented as current.
Remove missing paths, obsolete file manifests, and stale status claims before publication.
Record deferred cleanup without treating its document as current.

## Coverage in each slice

Review documentation for every feature change. Update it when documented behavior or ownership changes.
Update both human and agent coverage for changed user behavior or architecture boundaries.
Explain each feature area in the human wiki: behavior, status, source owner, and limits.
Explain each architecture boundary in the agent wiki: ownership, invariants, protocols, persistence, tests, and limits.
Include build, release, migration, and test-process changes in the same implementation slice.
Do not edit documentation for private changes that leave documented behavior unchanged.
Perform a full source-to-documentation audit before release.
Confirm coverage in the completion check.

## Comments and boundary changes

Comment complicated functions. Explain reasons, invariants, and non-obvious branches, not obvious syntax.
Cover compatibility, concurrency, lifecycle, security, protocol quirks, fallbacks, bounds, and performance tradeoffs.
Use KDoc when types do not explain an API contract. Keep comments short and near the rule they explain.
Update or remove comments when behavior changes.
When a cleanup or refactor touches code, comment its complicated functions in that slice.
This rule does not authorize code edits in a documentation-only task.

Before changing a documented boundary, record its owner, callers, invariants, and tests.
Add characterization tests before risky decomposition.
Mark refactor notes as temporary until stable. Update the ownership map when the boundary becomes stable.
Update human documentation when user or contributor understanding changes.
Remove temporary notes after retaining any still-valid requirements.

## Writing

Follow the project writing-style file. Use Simplified Technical English and American English spelling.
Use active voice, short sentences, simple verbs, and one main instruction per sentence.
Do not use contractions or vary terms only to avoid repetition.
Keep exact code identifiers, protocol names, and API field names unchanged.
Use Beeline in public product text. Keep the internal codename out of user-facing content.
