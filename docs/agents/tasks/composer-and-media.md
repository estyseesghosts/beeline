# Objective

Implement `docs/composer-and-media-upload-plan.md` slices 1 to 5: posting limits, upload transport, the Misskey drive permission, image preparation, and the compression setting.

# Invariants

- Shared domain stays protocol-neutral. Protocol behavior stays in adapters and probes.
- Capability schema version moves forward (now 6).
- Old stored sessions and settings load with safe defaults.

# Decisions

- Blocking questions use the plan's recommended option: Q1 labels, Q7 encrypt draft media, Q8 strip GPS, Q9 sensitive on CW.
- `PostLengthRule` names the length rule enum; `PostingCapabilities` lives in `domain/ServerCapabilities.kt`.

# Completed

- Slice 1: posting limits in capabilities.

# Current slice

Slice 2: upload transport and adapter uploads.

# Files involved

- `domain/ServerCapabilities.kt`, `domain/PostLengthCounter.kt`
- `data/mastodon/MastodonCapabilityProbe.kt`, `data/misskey/MisskeyCapabilityProbe.kt`
- `data/auth/AccountFileStore.kt`

# Verification

- Slice 1: focused tests and the full local gate pass. `lintAnalyzeDebugAndroidTest` crashed once inside the Kotlin analysis in a combined run; `lintDebug` passes when run alone. Architecture audit exits 1 on regressions that predate this task.

# Next

Slice 2.

# Blockers

None. Live-server and device checks are not yet done.

# Last safe commit

049cb765 Let a press and hold slide onto a Photo Grid quick-view entry and release to run it
