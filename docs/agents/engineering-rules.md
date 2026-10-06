# Beeline Engineering Rules

Status: current
Owner: Maintainers
Last reviewed: 2026-10-05
Stale when: Project constraints or engineering policy change.

These rules are required for code changes. They preserve the detailed rules moved from root `AGENTS.md`.
Read [workflow.md](workflow.md) for task control and [agent-control.md](agent-control.md) for delegation.
The source, tests, Gradle files, manifests, and CI remain the implementation authorities.
This move does not certify every existing feature claim or runtime behavior.

Source links: [domain contracts](../../app/src/main/java/me/foxtails/palustris/domain/),
[data owners](../../app/src/main/java/me/foxtails/palustris/data/),
[UI owners](../../app/src/main/java/me/foxtails/palustris/ui/),
[tests](../../app/src/test/), [Android tests](../../app/src/androidTest/),
[build configuration](../../app/build.gradle.kts), and [CI](../../.github/workflows/).
These rules are policy. Verify current behavior and ownership against the linked source before edits.

## Architecture regression rules

One mutable state -> one authoritative owner.
One lifetime -> one explicit release rule.
Feature code belongs to its existing feature package.
Composition roots wire features; they do not implement features.
ViewModels do not privately construct production authorities or caches.
Do not introduce an abstraction without naming the responsibility and lifetime it owns.
Large files are warnings, not automatic split requirements.

## Preamble

- Follow these project rules within higher-priority instructions.
- Verify conflicts against the current source. Ask the user when a project decision is required.

## Product Identity

### Beeline

- The application is named Beeline.
- Use Beeline as the product name everywhere. Do not introduce another product name.
- Treat old product names as stale migration debt. Remove them when a task touches them.
- Use Beeline in build metadata.
- Use Beeline in application labels.
- Use Beeline in authentication registration names.
- Use Beeline in User-Agent values.
- Use Beeline in documentation.
- Use Beeline in release text.
- Use Beeline in user-facing strings.

### Palustris

- Palustris is an internal codename.
- Keep Palustris out of all user-facing content.
- Do not show Palustris in application labels.
- Do not show Palustris in accessibility text.
- Do not show Palustris in public documentation.
- Do not show Palustris in release text.
- Do not show Palustris in authentication application names.
- Do not show Palustris in User-Agent values.
- Existing package identifiers, internal class names, and protocol identifiers can use the
  codename. Protocol identifiers can use it when compatibility requires.
- Do not rename compatibility identifiers as unrelated cleanup.
- Use a dedicated migration for compatibility-sensitive identifiers.

## Project Constraints

- Beeline is a single-module Android application in `:app`.
- Beeline supports Android 10 and later. The minimum SDK is 29.
- Beeline uses Kotlin.
- Beeline uses Jetpack Compose.
- Beeline uses Material 3.
- Beeline supports Misskey-family servers. Misskey has first-class protocol support.
- Beeline supports Mastodon-compatible servers. Mastodon has first-class supported adapter
  behavior.
- Keep shared domain behavior protocol-neutral.
- Keep protocol differences behind protocol boundaries.
- Do not add XML layouts.
- Do not add AppCompat UI.
- Do not add Material 2 UI.
- Keep new UI in Compose Material 3.
- Existing XML resources can support Android platform requirements.

## Documentation and task control

Use [documentation rules](documentation-rules.md) for authority, maintenance, coverage, comments, and boundary changes.
Use [workflow](workflow.md) for task state, slices, logs, recovery, handoff, commits, and completion.
Use [agent control](agent-control.md) for implementation ownership, investigation, stop conditions, and review.

## Risk Review

- Consider the effects of a change before implementation. Do not consider only the requested
  happy path.
- Identify likely failure states and likely regression states.
- Identify interaction with existing features.
- Identify behavior during partial server support.
- Identify behavior during network failure.
- Identify behavior after process recreation.
- Identify behavior after account switching.
- Identify behavior after session replacement.
- Identify behavior with stale cached data.
- Identify behavior with empty data.
- Identify behavior with malformed remote data.
- Review protocol differences before shared model changes.
- Review storage effects before model changes.
- Review migration effects before stored-format changes.
- Review account isolation before state changes.
- Review pagination before timeline changes.
- Review navigation restoration before navigation changes.
- Review notification delivery before notification changes.
- Review background work before account lifecycle changes.
- Review authentication before origin or session changes.
- Review localization before user-facing text changes.
- Review accessibility before interaction changes.
- Review compact and wide layouts before layout changes.
- Review old Android behavior before platform-specific changes.
- Review performance before adding work to feed rendering.
- Add tests for important failure states.
- Do not add only happy-path tests.
- Record risks that cannot be verified locally.
- Include unresolved risks in the final handoff.

## Architecture And Boundaries

### Architecture

- `domain/` owns protocol-neutral models and contracts.
- `data/` owns data access and persistence implementations.
- `data/misskey/` owns Misskey transport and mapping behavior.
- `data/mastodon/` owns Mastodon transport and mapping behavior.
- `data/auth/` owns authentication and session storage.
- `data/notifications/` owns notification data behavior.
- `data/directmessages/` owns direct-message data behavior.
- `data/preferences/` owns persisted application preferences.
- `data/media/` owns media data behavior.
- `data/emoji/` owns emoji data behavior.
- `ui/` owns Compose presentation.
- Feature-specific UI packages own feature presentation.
- Hilt provides application dependencies through dependency injection.
- `SocialSource` is the shared social source contract.
- Protocol adapters implement shared source contracts.
- `SocialSourceFactory` creates sources for authenticated sessions.
- `AccountSourceRegistry` associates sources with accounts.
- `AccountManager` owns account and session state.
- Keep account ownership outside Compose functions.
- Keep transport behavior outside Compose functions.
- Keep protocol JSON outside Compose functions.
- Keep persistent storage outside Compose functions.
- Use dedicated ViewModels for substantial independent feature state.
- Do not make one ViewModel own unrelated destinations.
- Do not tie background synchronization to a screen lifetime.

### Protocol Boundary

- Keep shared domain models protocol-neutral.
- Do not put Misskey JSON structures into shared models.
- Do not put Mastodon JSON structures into shared models.
- Map protocol data at the adapter boundary.
- Normalize failures before they reach generic UI.
- Use `ServerCapabilities` for feature availability.
- Use capability probes for protocol support.
- Do not hard-code server software checks in UI.
- Do not choose protocol behavior from hostname strings when capabilities or account protocol state already exist.
- Do not add protocol branches to generic Compose UI.
- Do not add protocol branches to generic ViewModels.
- Keep protocol branches in adapters.
- Keep protocol branches in authentication.
- Keep protocol branches in source creation.
- Keep protocol branches in capability detection.
- Keep unknown capability state separate from unsupported state.
- Keep denied access separate from unsupported behavior.
- Keep temporary failure separate from unsupported behavior.
- Do not mark a feature unsupported after one failed request.
- Keep cursors opaque above adapters.
- Do not construct protocol pagination URLs in UI.
- Do not construct protocol pagination URLs in generic ViewModels.
- Bind cursors to their account and query.
- Bind cursors to their protocol variant.
- Preserve transport order during pagination.
- Do not compare opaque identifiers numerically.
- Do not compare opaque identifiers lexically.
- Do not infer time from opaque identifiers.

## Security And Privacy

### Network Security

- Validate origins before authenticated requests.
- Validate pagination origins before authenticated requests.
- Validate entity origins before account actions.
- Reject foreign authenticated pagination URLs.
- Reject authenticated URLs with credentials.
- Reject authenticated URLs with fragments.
- Keep authenticated redirects disabled unless a reviewed protocol flow requires them.
- Do not send credentials to an unvalidated origin.

### Storage And Privacy

- Keep account secrets in no-backup storage.
- Keep sessions account-scoped.
- Encrypt stored session secrets with Android Keystore protection.
- Keep pending authentication data encrypted.
- Keep drafts account-scoped.
- Keep notification data separate from authentication secrets.
- Keep preferences scoped correctly.
- Delete account-scoped data during account removal.
- Never log access tokens.
- Never log client secrets.
- Never log authorization codes.
- Never log push keys.
- Never log authorization headers.
- Never log complete API response bodies.
- Redact secrets from diagnostic URLs.
- Redact credentials from diagnostics.
- Redact long opaque identifiers when they can contain sensitive data.

## Domain Rules

### Authentication And Accounts

- Detect the protocol before protocol-specific authentication.
- Store servers as validated HTTPS origins.
- Reject origins with credentials, paths, queries, or fragments.
- Bind each account to its connection origin, its protocol, and its local account identifier.
- Scope registration credentials to their connection origin.
- Scope access grants to their account session.
- Scope tokens to their account session.
- Scope capabilities to their account session.
- Preserve existing sessions when the user adds an account.
- Preserve unrelated sessions during reauthentication.
- Verify an account before session replacement.
- Reject stale authentication callbacks.
- Stop account streams before account removal.
- Disable account push before account removal.
- Remove account-scoped local state during account removal.
- Treat remote cleanup as best effort.
- Do not block local sign-out because a server is unavailable.

### Direct Messages

- Beeline has direct-message inbox and conversation behavior.
- Do not describe direct messages as a placeholder feature.
- Keep direct-message domain contracts protocol-neutral.
- Keep protocol-specific direct-message behavior in adapters.
- Use capability or source support to determine availability.
- Do not assume native Misskey chat semantics.
- Do not present federated direct posts as encrypted messaging.
- Do not claim private messages are secure or encrypted.

### Interaction Data

- Keep post interaction counts protocol-neutral.
- Use `PostInteractionCounts` for normalized counts.
- Preserve unknown counts as unknown.
- Do not convert missing counts to zero without evidence.
- Keep favourites distinct from emoji reactions.
- Keep total reposts distinct from quote reposts.
- Preserve protocol differences during mapping.
- Update optimistic counts with their related post action.
- Reconcile optimistic values with server responses.

### Photo Grid

- Photo Grid belongs to the Search destination.
- Photo Grid has independent feed state.
- Do not reuse Home feed state for Photo Grid.
- Keep Photo Grid timeline selection independent from Home.
- Keep saved Photo Grid hashtags independent from Home.
- Use server capabilities for available Photo Grid timelines.
- Do not show unsupported timeline types.
- Keep Photo Grid media filtering explicit.
- Preserve Photo Grid navigation state across supported restoration paths.

### Preferences And Settings

- Keep persisted settings outside Compose functions.
- Use repository-owned preference state.
- Keep application-wide preferences separate from account-specific preferences.
- Keep post preferences separate from application display preferences.
- Apply settings through defined policy owners.
- Do not duplicate preference state in unrelated ViewModels.
- Make migrations explicit when stored preference formats change.
- Preserve safe defaults when a new preference has no stored value.

### Moderation

- Keep moderation contracts protocol-neutral.
- Keep moderation transport inside adapters.
- Use moderation capabilities for feature support.
- Keep blocked users separate from muted users.
- Keep muted hashtags separate from account mute data.
- Keep unsupported moderation types explicit.
- Validate account origins before moderation actions.
- Refresh affected state after successful moderation changes.

### Notifications

- Keep notification ingestion separate from presentation.
- Use the notification repository as the merge point for notification data.
- Bind writes to the correct account generation.
- Reject stale account writes.
- Preserve local seen state during refresh.
- Preserve server acknowledgement state during refresh.
- Preserve Android presentation state during refresh.
- Keep notification identity separate from group identity.
- Keep account identity separate from notification identity.
- Keep local dismissal separate from server acknowledgement.
- Do not alert for initial baseline imports.
- Use adapter unread state when available.
- Preserve unread precision.
- Do not infer exact unread counts from one loaded page.
- Use actor account identifiers for actor actions.
- Do not use notification identifiers as account identifiers.

### Synchronization And Delivery

- Keep REST reconciliation as the freshness authority where designed.
- Keep foreground streams owned by their stream controller.
- Reconcile after incomplete stream events.
- Use bounded retry behavior.
- Treat unsupported stream behavior as a terminal stream state.
- Use WorkManager for supported background work.
- Re-read account state when background work starts.
- Use unique work for account-scoped jobs.
- Keep push registration account-scoped.
- Validate push callback ownership.
- Validate session revisions.
- Validate endpoint generations.
- Keep notification presentation policy separate from ingestion.

## UI And Presentation

- Keep screen state ownership clear.
- Keep lifecycle, coroutine cancellation, and Flow ownership explicit.
- Do not run network, database, image, or other blocking I/O on the main thread.
- Keep user-visible strings in Android resources. Preserve accessibility, touch targets, keyboard behavior, and back navigation.
- Prefer stateless presentation components.
- Hoist state when a parent owns the behavior.
- Do not make reusable components depend on protocol types.
- Do not make reusable components depend on account managers.
- Keep floating controls independent from scroll content.
- Keep rows behind floating controls transparent where the design requires it.
- Keep final scroll items reachable above floating controls.
- Put obstruction clearance inside scroll content.
- Do not inset an entire full-screen viewport only to reserve a floating control.
- Preserve IME positioning for search and input controls.
- Preserve wide-layout behavior.
- Test compact layouts.
- Test wide layouts.
- Test system font scaling.
- Test long translated text where relevant.

## Code Quality

### Structure

- Optimize the project for long-term maintenance. Do not optimize only for the current patch.
- Keep each file responsible for a small coherent area.
- Keep each class responsible for a clear concept.
- Keep each function responsible for a clear operation.
- Keep state ownership explicit.
- Keep dependencies directional.
- Keep protocol boundaries visible.
- Do not make a large file larger only because it already contains related code.
- Existing complexity does not justify new complexity.
- Existing duplication does not justify new duplication.
- Existing mixed responsibilities do not define the preferred architecture.
- Treat large coordinator files as decomposition targets.
- Do not use a large coordinator file as the default location for new behavior.
- Extract a focused owner when a file gains another major responsibility.
- Extract reusable UI from screen coordinators.
- Extract state models from large presentation files.
- Extract navigation policy from feature UI.
- Extract protocol behavior from shared UI.
- Extract persistence behavior from ViewModels.
- Extract formatting logic when several screens use it.
- Do not create generic helper files without a clear domain.
- Do not create dumping-ground utility classes.
- Do not create broad `Utils` objects.
- Do not create broad `Managers` without defined ownership.
- Do not move complexity into a new file without improving boundaries.
- Prefer feature packages when a feature has several related files.
- Keep screen UI near its feature.
- Keep feature state near its feature.
- Keep feature ViewModels near their feature.
- Keep feature-specific presentation helpers near their feature.
- Prefer composition over one large configurable component.
- Prefer small state holders over one global mutable state object.
- Prefer explicit dependencies over hidden global access.
- Prefer clear domain types over loosely related primitive values.
- Prefer one canonical implementation over copied behavior.
- Refactor when a requested feature exposes an unsafe boundary.
- Keep refactors limited to the boundary required by the task.
- Do not perform unrelated architecture rewrites.
- Preserve behavior during structural refactors.
- Add characterization tests before risky decomposition.

### Coding Rules

- Follow official Kotlin coding conventions.
- Prefer imports over fully qualified names.
- Avoid wildcard imports.
- Keep related declarations together.
- Use one technical name for one concept.
- Preserve exact code identifiers in technical documentation.
- Do not copy protocol logic between adapters.
- Do not copy substantial UI behavior between screens.
- Extract shared behavior when it has one stable meaning.
- Do not abstract two pieces of code only because they look similar.
- Use clear names instead of explanatory comments where possible.
- Preserve unrelated worktree changes.
- Do not revert user changes.
- Do not reformat unrelated files.
- Do not modify unrelated code only to make the diff cleaner.

## Writing Style

Follow [the project writing style](../../importantdocs/writing_style.md) and [documentation rules](documentation-rules.md#writing).

## Verification

### Focused verification

Run the smallest relevant checks first. Focused verification establishes evidence for the changed behavior, not overall completion.
Use the affected feature suites and any additional device or protocol checks required by the task.

### Local CI-parity completion gate

The complete local gate matches the [CI build job](../../.github/workflows/android.yml).
Run every command below after focused verification for each code slice.
Use `python` instead of `python3` where that names the installed Python 3 interpreter.

```text
python3 -m unittest discover -s tools/tests
python3 tools/scripts/architecture_audit.py . --baseline tools/architecture-baseline.json --check
./gradlew --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug :app:ktlintCheck :app:assembleDebug :app:assembleRelease
```

On Windows, replace `./gradlew` with `.\gradlew.bat`.
Keep the timeout, closed standard input, and daemon rules below.
“Full gate” means this entire sequence, not only `test assembleRelease` or a task-specific subset.
CI remains authoritative. Update this contract if the CI build job changes.
API 29 instrumentation is a separate remote or device gate. Release signing and live-server checks require separate evidence.

### Execution and evidence rules

- Verify each implementation slice before you commit it.
- Run the smallest relevant test set first.
- Run focused unit tests for changed behavior.
- Run adapter contract tests after adapter changes.
- Run persistence tests after storage changes.
- Run account lifecycle tests after session changes.
- Run Compose tests after presentation changes.
- Run instrumented tests for Android-only behavior.
- Run lint after authentication changes.
- Run lint after storage changes.
- Run lint after adapter changes.
- Run lint after contract changes.
- Run lint after notification changes.
- Run lint after security-sensitive changes.
- Use the Gradle wrapper for every Gradle command.
- Add `--no-daemon --console=plain` to every agent Gradle command.
- Use `./gradlew --no-daemon --console=plain` in Unix shell examples.
- Use `.\gradlew.bat --no-daemon --console=plain` in Windows PowerShell examples.
- Do not run bare `gradlew` without `--no-daemon` in agent work.
- Set `GRADLE_OPTS=-Dorg.gradle.daemon=false` as a safety net for agent environments.
- Do not bypass denied shell commands to set environment values. Report limitations under [workflow](workflow.md).
- Set an explicit timeout for every Gradle tool call.
- Close standard input for non-interactive Gradle calls.
- Run the complete local CI-parity gate before you declare a coding task complete.
- Run additional required checks for the affected feature.
- Fix failures caused by the current slice.
- Do not hide failing tests.
- Do not disable tests only to complete a task.
- Do not treat mocked HTTP tests as proof of live server behavior.
- Do not treat Compose tests as proof of physical device rendering.
- Do not treat authentication tests as proof of browser callback behavior.
- Do not treat local notification tests as proof of real push delivery.
- Record unverified live-server checks in the final handoff.
- Record unverified device checks in the final handoff.

## Dependency Changes

- Do not add a dependency when existing platform APIs are sufficient.
- Check maintenance status before you add a dependency.
- Check Android version support before you add a dependency.
- Check license compatibility before you add a dependency.
- Record the reason for each new dependency in the slice commit message.
- Keep dependency scope as narrow as possible.
- Do not add AppCompat.
- Do not add Material 2.

## Completion

- Confirm that each requested behavior is implemented.
- Confirm that each completed slice has a commit.
- Confirm that required tests pass.
- Confirm that task logs are current.
- Confirm that no secrets entered the diff.
- Confirm that unrelated user changes remain intact.
- Confirm that protocol boundaries remain intact.
- Confirm that new files have clear responsibilities.
- Confirm that existing files did not gain unrelated responsibilities.
- Confirm that user-facing product text says Beeline.
- Confirm that the internal codename did not enter user-facing content.
- Confirm that `docs/agents/handoff.md` names the next slice and the last safe commit.
- Confirm that complicated new functions have comments or KDoc.
- Confirm that the wiki explains each touched feature area.
- Confirm that the agent folder explains each touched architecture boundary.
- Report remaining risks.
- Report checks that you could not perform.
