# Beeline 0.4.0 task state — Phase 3B

## Status

Phase 3B characterizes the shared motion contract. The implementation does not change.

The tests cover pressed, released, canceled, selected scale tokens, disabled controls, reduced-motion
snap behavior, and direct clicks when the animator scale is zero. PillAction and NotificationRow remain
unchanged.

## Ownership and scope

`MotionTokens.kt` remains the owner of motion specifications and scale tokens. `SpringyInteractions.kt`
remains the owner of press and selection interaction behavior. This slice adds no haptic API, event map,
visual change, layout change, protocol behavior, or persistence behavior.

## Verification

* Motion unit tests passed 9 tests.
* PillAction tests passed 4 tests.
* `lintDebug` passed.
* The architecture audit passed with 615 findings and zero regressions.
* Slice-only `diff --check` passed.
* The full gate remains red because Phase 10 owns the failure.
* The focused rerun was denied for the reviewer.

## Phase 3B device evidence

The evidence uses `emulator-5554` with the `@ctr` session preserved. The run did not touch OAuth,
install the application, change settings, or expose secrets.

* The device runs Android 16, SDK 36, at 1848x2448 and density 480, with density override 616.
* The first attempt found Firefox in the foreground at `mstdn.ca/oauth/authorize`.
* Beeline did not resume because a background OAuth task remained pending.
* No tap, scroll, or back action ran during the blocked attempt.
* `font_scale` was 1.0, `transition` was 1.0, and `animator_duration_scale` was null.
* `reduce_light` was null.
* Keyguard and IME state remain unverified because piped `dumpsys` access was denied.

The run restored Beeline with `am start -n me.foxtails.palustris/.MainActivity`. It did not interact with
Firefox. MainActivity resumed as task `t27`. Firefox remained visible=false and STOPPED as task `t24`.

The Home screenshot shows the Home, Local, and Federated chips without clipping. Post rows show the
avatar, name, timestamp, body, and photo. The bottom navigation and compose control remain visible.
The session remains intact without a login screen.

A safe swipe from `900,1800` to `900,800` confirmed scrolling. It caused no state change.

Back did not provide valid evidence. It popped MainActivity to Firefox task `t24`, and task `t27` ended.
This behavior relates to the pending background OAuth task, not to motion failure. Beeline restored
immediately with `am start`; screen 3 shows MainActivity on screen and Firefox obscured. Do not use back
for evidence while the OAuth task remains pending.

Coverage passes for compact Home light geometry and scrolling. Full scroll and card coverage, hierarchy
bounds, wide layout, and font-scale coverage remain unverified because of the blockers.

Screenshots are `phase3b-home.png`, `screen.png`, `screen2.png`, and `screen3.png`. The inspection
directory remains untracked.

## Documentation review

This task-state file and the handoff now record the device evidence and its verification limits. No
source or test file changed. No additional documentation page requires an update.

## Preservation and continuation

The unrelated dirty worktree remains untouched. This subagent does not stage, commit, or push.

History: `5b8d98d`, `e23972e`, `2c3a1a8`.

Last safe commit: `2c3a1a8`. Next slice: Phase 3C-1.
