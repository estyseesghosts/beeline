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

- Slice 1: posting limits in capabilities — commit 5f3135cf
- Slice 2: upload transport and adapter uploads — see the commit that follows 5f3135cf

# Current slice

Slice 3: Misskey drive permission.

# Files involved

- `domain/ServerCapabilities.kt`, `domain/PostLengthCounter.kt`
- `data/mastodon/MastodonCapabilityProbe.kt`, `data/misskey/MisskeyCapabilityProbe.kt`
- `data/auth/AccountFileStore.kt`
- `data/mastodon/MastodonMediaService.kt`, `data/misskey/MisskeyMediaService.kt`, `domain/MediaUploadRequest.kt`

# Verification

- Slice 1: focused tests and the full local gate pass. `lintAnalyzeDebugAndroidTest` crashed once inside the Kotlin analysis in a combined run; `lintDebug` passes when run alone. Architecture audit exits 1 on regressions that predate this task; slice 2 adds none.
- Slice 2: full gate passes. Debug build installs and launches on emulator-5554 (signed-in Mastodon account) without a crash. Live-server uploads are unverified.

# Next

Slice 3.

# Blockers

None. Live-server and device checks are not yet done.

# Last safe commit

5f3135cf Record posting and media limits in server capabilities
