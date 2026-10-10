# Animation presentation

How combat poses are sampled, blended and loaded, and which items of the 56-item animation review are done. This work
covers timing, transitions, handedness, action selection and resource loading. Other recommendations need model/rendering work, new network data, authored motion,
or measurements. The table below records the exact scope; it does not claim that all 56 recommendations are finished.

## Presentation behavior

- A read-only `CombatStateMachine.visualFrame` resolves fractional-tick phase boundaries. Simulation state,
  hit sweeps, combat windows, damage, weapon tuning and packet codecs retain their existing behavior.
- `CombatPresentation` owns entity pose samples. Its cache includes render frame, entity tick, partial tick,
  attack serial, phase/type/heavy/side/variant, main arm, held item identities and animation reload generation.
  Camera roll, the player layer, mob posing and the debug view consume this presentation path.
- Hit-stop captures the complete pose for the current attack. Simulation and aim input keep advancing. A new
  attack, item change, resource reload, downed/dead subject or player replacement releases the old sample.
  An authoritative correction replaces a frozen sample without extending the pause. On expiry the pose returns
  to the live timeline; this patch does not delay release motion to compensate for the pause.
- Windups settle onto the live arc during their final 35%. All 33 shipped heavy windups retain their exaggerated
  preparation and end at the shared release entry. Missing heavy clips use a scaled light pose that also settles.
- Heavy upgrades, ordinary weapon morphs, combos and non-release corrections blend from the displayed pose over
  0.2 s. Feints have a 0.2 s visual tail. Release motion follows the live arc directly. Downed poses take effect
  immediately. Changes between leg-only and weapon actions do not yet have a shared transition rig.
- Weapon, item and grip rotations blend with shortest-path quaternion interpolation. Authored additive clip
  channels retain their existing interpolation and ownership rules.
- Preferred main arm selects the player/mob weapon arm and player item bone. The rig and limb channels reflect
  for left-handed fighters. An occupied off hand stays visible and does not get assigned a two-handed grip.
- During first-person weapon attacks, a carried non-shield item uses a separate holding arm that counters the
  weighted body rotation and follows view yaw/pitch. It stays raised as the weapon recovers. Shield-capable items,
  guards and kicks/bashes keep their existing choreography. Third-person posing and the solved weapon arm retain
  their existing behavior. Live vanilla/model hand transitions still require acceptance review.
- Carried non-shield items now retain the weapon's ready stance between attacks. PAL camera ownership is sampled
  on its animation tick rather than recalculated from pose fields being refreshed during the same render pass.
  This keeps armor visibility consistent as a pose expires. Alive/downed checks still take effect immediately.
- Thrust extension drives a small first-person arm translation. Third-person reach, blade length and rigid
  shoulder dimensions remain separate work. This is a presentation offset, not a change to traced reach.
- Throw preparation completes its forward gesture before release, when the existing server code launches the
  projectile. Shared bash clips animate the shield arm without kick leg channels. Special lunge/slam/sweep clips
  inherit different thrust/overhead/slash silhouettes. These new silhouettes still need artist and client review.
- Newly tracking players receive the fighter's current combat snapshot, plus its downed snapshot when applicable.
  Existing packet formats and the remote timeline delay policy are preserved.

## Resource-pack contract

Files remain at `assets/<namespace>/steelclash_animations/<archetype>.json`. Plain profile archetypes resolve in
the `steelclash` namespace; explicit IDs such as `othermod:sword` keep their namespace. Missing archetypes and
missing clip keys inherit `steelclash:default`. A resource-pack file still replaces the file at the same path:
inheritance is from the shared default, not from every lower-priority version of the same archetype.

An explicit empty action clip (`"slash.release": []`) suppresses additive body motion for that clip. An empty
heavy-windup clip uses the existing scaled-light fallback. Solved weapon aiming is independent of body clips.
Metadata uses the override's own values/defaults; only clip keys are inherited. Invalid edits keep the previous
valid set for that ID, if one exists. Otherwise the shared default is used. Errors name the file and offending
clip/channel where available. Removed resources disappear on reload, and presentation caches invalidate.

The optional `format_version` is 1; legacy files without it are accepted. Optional first-person composition uses
model pixels and defaults to four pixels forward and 3.5 pixels down (1.5 before 2026-10-09), plus thrust retraction:

```json
{
  "format_version": 1,
  "first_person": {"forward": 4, "down": 3.5, "retraction": 3, "ready_yaw": 65, "ready_pitch": -45}
}
```

Ready yaw/pitch are degrees relative to the view; the yaw mirrors for left-handed fighters. They default to the
previous 65/-45 stance when omitted, and packs keep their own ready metadata when inheriting missing clips.
Ready presentation and layer activation are suppressed while downed/dead.

Set `retraction` to 0 to disable the new translation. Bounds: forward/down -16..16, retraction 0..8,
ready yaw -120..120 and ready pitch -90..90 (all finite),
grip gap greater than 0 and at most 10, heavy scale 0..5. Keyframe times must be finite, unique and in 0..1;
part arrays must have 1..3 finite axes. Channels are body, torso, head, right/leftArm, right/leftLeg and
right/leftArmBend. Bend channels remain accepted but PAL's current 1.21.1 backend still does not draw elbows.

Optional specialized action keys use `bash.<phase>`, `special.lunge.<phase>`, `special.slam.<phase>`,
`special.sweep.<phase>` and `<attack>.variant_<index>.<phase>` for nonzero variants. Phases are windup,
release and recovery. Missing specialized keys use the generic action. The shipped default supplies bash and
special-kind keys, inherited by other sets; packs can replace those keys per archetype. Variant-specific motion
is enabled by the lookup but has not been authored in this patch.

The existing client `cameraMotion` master remains. New `cameraSway` and `impactShake` multipliers each default
to 1 and accept 0..2; either effect can be disabled independently. Camera effects follow the local player's camera
entity and reset when the local player changes. Directional impact impulses remain future work.

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
| 7 | Partial | Feints, weapon morphs and non-release interrupts blend; weapon-to-kick rig transitions remain. |
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

## Client acceptance checks

Use PAL 1.1.6 on NeoForge 1.21.1, with both default and slim skins, left/right main arm, first/third person,
and Sodium enabled/disabled. These checks are required before calling the rendering changes release-ready:

| Scene | Expected |
|---|---|
| Fractional 370 ms windup at low/high FPS | Pose and countdown enter release at the fractional boundary. |
| Upgrade halfway into a light windup; feint, morph, counter-feint and combo | No single-frame rewind/drop; release stays on the arc. Check weapon-to-kick separately as remaining work. |
| Long held guard and quick release | Body finishes its raise in three ticks, holds steady, then lowers without a long-duration raise. |
| Mace thwack spanning a tick, then authoritative stagger/disarm | Pose and camera sway pause together; an interrupt replaces the pause and items do not retain an old grip. |
| Sweep aim across +/-180 degrees at partial layer weight | No full-turn solved-arm/item interpolation. |
| Shield, torch and empty off hand; two-handed sword/polearm | Appropriate arm/item remains visible; occupied hand is not assigned to the weapon grip. |
| Kick, shield bash, jab, throw and all three special kinds | Bash uses shield arm and quiet legs; throw moves forward before the item leaves; specials have the correct silhouette. First-person kick foot remains pending. |
| Track an already guarding/attacking/downed fighter | Current state appears without waiting for another phase transition. |
| Reload partial/invalid/custom-namespace animation files during attacks | Missing clips inherit; empty action overrides stay empty; invalid edits log context and retain a valid fallback; removed packs invalidate caches. |
| Crouch, sprint, swim, ride and move while attacking; armored/slim/custom models | Check clipping, hand alignment and vanilla movement interaction. These model/locomotion limitations are not solved by the patch. |
| FOV changes, spectator camera and reconnect/world changes | Composition remains usable; sway/shake respect independent sliders and camera/player ownership. |

For built-in pose sheets, use `-PposeSheet=kick,jab,throw` with a compatible held item; `special` resolves the
profile's special spec. Unsupported actions now fail with the shot/item context rather than capturing a stale pose.
Review the shared bash/special motion in the actual client before refining it per weapon family.

Use `-PposeSheetArm=left|right` and `-PposeSheetOffhand=minecraft:air|minecraft:shield|minecraft:torch` to
select rig conditions (pass one item id, not the literal alternatives). Omitted settings preserve existing conditions.
The tool checks actual rendered handedness/offhand ownership and logs entity type, skin model/texture and armor;
an occupied offhand must not enter the two-handed grip. Overrides are client-local and restored when capture finishes.
Staging changes world gamerules, including disabling mob loot, so use a disposable world copy. Fire overlays are
cleared on the captured subject. These controls verify frozen poses; they do not exercise live input transitions.

For profiling, record 1/20/50/150 visible fighters, close-ups with armor/two-handed weapons, and first/third-person
views. Compare camera effects on/off and the same rendering stack. Capture p95/p99 frame time and allocation;
server GameTest timings do not measure client rendering.

## Live capture diagnostic

Use a disposable world with cheats and a fresh output directory:

```powershell
.\gradlew.bat runClient '-PquickPlay=Steelclash Animation QA' '-PliveCapture=combo' '-PliveCaptureOffhand=minecraft:torch' '-PliveCaptureOut=C:/dev/steelclash-live/combo'
```

Scenes are `attack`, `combo`, `heavy`, `feint`, `morph` and `parry`. Optional `liveCaptureItem` selects the main
item (default iron sword), `liveCaptureOffhand` selects the carried item (default air), and `liveCaptureView`
uses a `CameraType` name such as `FIRST_PERSON` or `THIRD_PERSON_BACK`.

The tool stages a sky platform, equips the real server player, temporarily maps numpad keys, then drives the
normal ClientInput keyboard path. Prediction, packets and simulation continue normally; no combat pose is
applied/frozen. The camera is held stationary and the capture caps rendering at 20 FPS. HUD layers are hidden
separately so vanilla first-person hands remain visible. It requires an active game window for normal input.
Bindings, view, frame cap and GUI preferences restore at completion; staging/world equipment changes persist
in the disposable world. Do not run this with PoseSheet.

`capture.json` records actual frame timestamps, rendered pose/ready presence, client state, a read-only server-tick
snapshot, input times, model/equipment/FOV and loaded mods. PNGs are chronologically numbered under `screenshots`.
Scene guards require the requested outcomes on client and server and complete screenshot files before quitting.
Use the recorded intervals when reviewing motion: the cap targets about 50 ms, not an exact fixed-step replay.
These scripted keyboard cases do not establish mouse gestures, real LAN latency, hit-stop, item use or every model.
