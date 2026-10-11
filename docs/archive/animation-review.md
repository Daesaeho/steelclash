# Animation review archive

Historical record from the 0.3.2-beta review checkpoint dated 2026-10-08. Its coverage table and validation results describe that checkpoint only; they are not a statement of current repository status.

## Coverage of the 56 review items

“Included” means code/assets are in this patch, not that Minecraft visual playback has passed. “Partial” identifies
the remaining part of the recommendation. “Deferred” means it is outside this implementation.

| # | Status | Coverage / remaining work |
|---|---|---|
| 1 | Included | Full-pose hit-stop across game ticks, with expiry and interrupt handling. |
| 2 | Included | Shared presentation path for camera, model and debug sampling. |
| 3 | Included | Read-only sub-tick boundary sampling, also used by enemy countdown labels. |
| 4 | Included | Light/heavy windup aims meet the exact release start. |
| 5 | Included | Repaired all 33 shipped heavy body-clip seams. |
| 6 | Included | Heavy upgrade starts from the displayed pose instead of snapping to rewound progress. |
| 7 | Partial | Player PAL weapon/kick rig handovers now blend; mob retargeting and wider live acceptance remain. |
| 8 | Included | Recovery-to-windup combo entry starts from the displayed pose. |
| 9 | Partial | Guard raise/hold uses three ticks independent of hold duration; lowering still uses existing clips/envelopes. |
| 10 | Included | Quaternion interpolation for solved arm, off-arm and item rotations. |
| 11 | Partial | First-person thrust retraction; third-person extension and rig geometry remain. |
| 12 | Deferred | Calibrate full rendered blade hilt/tip against traced segments. |
| 13 | Included | Main-arm selection and reflected rig/limb/item posing; client checks still required. |
| 14 | Included | Occupied off-hand arm/item visibility, with grip ownership preserved. |
| 15 | Included | Shield-bash selection and shared shield-arm clips without kick leg motion. |
| 16 | Deferred | First-person kick foot renderer. |
| 17 | Included | Forward throwing gesture completes at projectile launch. |
| 18 | Deferred | New pommel/punch/off-hand jab presentation beyond the existing jab clips. |
| 19 | Included | Special-kind selection and distinct shared lunge/slam/sweep silhouettes. |
| 20 | Included | Camera-entity scoping. |
| 21 | Included | Camera sway uses the sampled relative swing yaw, avoiding raw wrap-crossing subtraction. |
| 22 | Deferred | Render the server's capped/reconstructed aim. |
| 23 | Deferred | Timestamped remote presentation snapshots. |
| 24 | Included | Initial combat/downed snapshot when tracking starts. |
| 25 | Partial | Non-release correction blend and frozen-sample replacement; release reconciliation/netcode remains. |
| 26 | Deferred | Non-humanoid model adapters. |
| 27 | Partial | Removed free-arm offsets from solved mob grips; rigid reach limits and endpoint error remain. |
| 28 | Deferred | Model dimensions and item mount calibration. |
| 29 | Deferred | Locomotion-aware bone masks. |
| 30 | Deferred | Authored off-arm and footwork changes for alternate cuts. |
| 31 | Deferred | Foot planting during pivots/leans. |
| 32 | Deferred | A real elbow-bend rendering backend. |
| 33 | Deferred | Combat-ready idle stance. |
| 34 | Deferred | Distinct riposte/counter preparation accents. |
| 35 | Deferred | Reaction reason/direction and separate impact silhouettes. |
| 36 | Deferred | Heavy-specific release/recovery body clips; release entries are currently shared. |
| 37 | Partial | Variant-specific clip lookup; per-variant assets remain. |
| 38 | Partial | Per-archetype first-person composition metadata; FOV/aspect adaptation remains. |
| 39 | Deferred | Authored thwack compression/settle. |
| 40 | Deferred | Velocity-aligned/spinning thrown-weapon renderer. |
| 41 | Deferred | Effects at actual contact position/normal. |
| 42 | Deferred | Predicted swing sounds with server-echo deduplication. |
| 43 | Partial | Separate sway/shake controls and reset behavior; directional impulses remain. |
| 44 | Included | Monotone cubic tangents carry the speed through intermediate keys; first/last keys still ease. |
| 45 | Partial | Windup layer entry completes over the first 40% of the windup (at least two ticks); recovery still has authored and layer fading. |
| 46 | Included | Per-clip shared-default fallback and explicit empty action overrides. |
| 47 | Included | Format version, bounds, finite values, duplicate times, channel validation and reload fallback. |
| 48 | Included | Namespaced animation identifiers. |
| 49 | Included | Frame-keyed pose cache with state/reload invalidation; performance is unmeasured. |
| 50 | Deferred | Allocation changes pending profiling. |
| 51 | Partial | Cached phase/type/heavy clip keys; arc sweep caching remains. |
| 52 | Partial | Skip weapon rig solves during kick/bash; one-handed off-arm solve specialization remains. |
| 53 | Deferred | Model capability gating and nested-render handoff changes. |
| 54 | Deferred | Measured client benchmarks; test scenes are specified below. |
| 55 | Partial | Built-in/special spec resolution and visible failure; automated stagger/bash/thwack/handedness cases remain. |
| 56 | Partial | Math, timing, freeze, reflection, fallback and asset-seam regressions; runtime sequences remain. |

## Validation

Merged onto 0.3.2-beta plus the unreleased combo/ready-stance work (2026-10-08): build, JUnit (including the new
`VisualFrameTest`, `WindupContinuityTest`, `RotationBlendTest`, `PresentationFreezeTest` and
`AnimationSetValidationTest`) and all 148 GameTests pass. Mutation check, 7 of 7 caught, each by its own test: no
windup settle, an off-by-one phase boundary in the visual sampler, a frozen pose surviving a new attack or a server
correction, long-way rotation blending, no generic-clip fallback, and a wrong left-handed mirror.

A before/after pose sheet (slash, heavy slash, heavy overhead, stab, parry, idle; 90 shots) changed only where
expected: third-person releases and parries are pixel-identical, windups show the new settle and the repaired heavy
seams, and first-person shots shift slightly because the camera roll and the model now sample the same frame (before,
the pose sheet froze the model at partial tick 0 while the camera used the live partial tick).

Two choices differ from the patch as written:
- **Transitions** use the existing hand-over rules (`PoseBlend.continuous`, plus heavy upgrades and morphs) and last
  0.2 s rather than 80 ms. A combo or riposte windup that starts while the arm is still posed stays at full weight.
- **Windup entry** takes the first 40% of the windup (at least two ticks) rather than two ticks, so starting an
  attack eases in; the drawn-back telegraph is still reached well before the settle.

No client frame-time or allocation measurement has been made.
