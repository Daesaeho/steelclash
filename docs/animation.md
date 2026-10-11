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
  immediately. Player PAL weapon/leg rig changes additionally blend displayed bone rotation/position/bend over
  0.2 s, with separate first-person/world histories. Weapon release and frozen sheets bypass that rig blend.
- Weapon, item and grip rotations blend with shortest-path quaternion interpolation. Authored additive clip
  channels retain their existing interpolation and ownership rules.
  PAL item-bone channels are converted to their physical hand frame before the additional rig interpolation.
  Item/main-arm changes, animation generation changes or a gap in rendered frames discard rig history.
- A known local attack-time item mismatch suppresses the stale attack/guard pose before server cancellation.
  Unknown/remote item snapshots keep their existing path; disarm recoil and empty-hand throw release/recovery
  remain valid. This does not cancel simulation or change packets. Item swaps use the new item's current pose.
- Preferred main arm selects the player/mob weapon arm and player item bone. The rig and limb channels reflect
  for left-handed fighters. An occupied off hand stays visible and does not get assigned a two-handed grip.
- During first-person weapon attacks, a carried non-shield item uses a separate holding arm that counters the
  weighted body rotation and follows view yaw/pitch. It stays raised as the weapon recovers. Shield-capable items,
  guards and kicks/bashes keep their existing choreography. Third-person posing and the solved weapon arm retain
  their existing behavior. Live vanilla/model hand transitions still require acceptance review.
- Carried non-shield items now retain the weapon's ready stance between attacks. PAL camera ownership is sampled
  on its animation tick rather than recalculated from pose fields being refreshed during the same render pass.
  This keeps armor visibility consistent as a pose expires. Alive/downed checks still take effect immediately.
- In first-person idle, a carried non-shield hand has a small breathing rotation (about +/-0.86 degrees pitch and
  +/-0.34 degrees roll, roughly a 4.2-second cycle). Its weight fades with combat-pose ownership, using the same
  aim/body compensation and mirror rule. This does not alter the weapon-arm solve, grip, reach or hit timing.
- The sword's first-person ready guard uses `ready_yaw: 45` and `ready_pitch: -75`. This is closer to upright than
  the previous 65/-45 stance, while retaining a readable blade. Lower-yaw 20/-60 and 35/-65 trials looked too
  edge-on. The angle mirrors with main arm and applies to the ready pose/transition; shield idle remains vanilla.
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

With ready stance enabled and a melee weapon held, offhand `EAT`/`DRINK` animations retain the same first-person
player model and the weapon's archetype ready pose. Only the carried hand blends toward a raised consumable pose,
then lowers over 250 ms. The presentation envelope retains its current weight when use stops/restarts; it samples
wall-clock time independently of simulation and render frequency. Camera/body compensation and quaternion rotation
blending keep the raised hand attached. A returned bottle/bowl uses the current stack while the hand lowers.
Weapon/main-arm changes, unsupported use, camera/ready eligibility loss reset the envelope. Main-hand use, bows,
shields and other use animations retain their existing paths. Vanilla still owns use duration, consumption, hunger,
effects and cancellation; this does not change combat timing, packets or hit geometry. Third-person poses are unchanged.

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

## Client acceptance checks

Use PAL 1.1.6 on NeoForge 1.21.1, with both default and slim skins, left/right main arm, first/third person,
and Sodium enabled/disabled. These checks are required before calling the rendering changes release-ready:

| Scene | Expected |
|---|---|
| Fractional 370 ms windup at low/high FPS | Pose and countdown enter release at the fractional boundary. |
| Upgrade halfway into a light windup; feint, morph, counter-feint and combo | No single-frame rewind/drop; release stays on the arc. Player weapon-to-kick handover has focused live evidence; wider variants remain. |
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

Scenes are `idle`, `attack`, `combo`, `heavy`, `feint`, `morph`, `parry`, `riposte`, `counter`, `hitstop`,
`interrupt-windup`, `interrupt-release`, `itemuse`, `use-attack`, `drinkuse`, `weapon-kick`, `kick-attack`,
`swap-weapon` and `swap-empty`.
Optional `liveCaptureItem` selects the main item (default iron sword, or mace for `hitstop`),
`liveCaptureOffhand` selects the carried item (default air, bread for food-use scenes, potion for `drinkuse`), and `liveCaptureView`
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
These scripted keyboard cases do not establish mouse gestures, real LAN latency or every model.

`-PliveCaptureBackground` hides the opted-in test window from its first client tick, disables focus-loss pausing
and skips mouse/screen grabs. `-PposeSheetBackground` provides the same hidden-window option for frozen sheets.
Use a windowed test client: both fail if GLFW cannot hide it. Pose sheets that finish without quitting restore
visibility with focus-on-show temporarily disabled. The initial loading window can precede the first client tick.
Actual hidden rendering/input/FPS acceptance is pending; existing foreground evidence does not verify this mode.

The `locomotion` scene uses normal movement keys for forward/backward movement, crouched strafing and sprinting,
then turns through 192 degrees without pinning body yaw. `locomotion-attack` starts a slash while crouched.
The larger disposable platform accommodates this route. `liveCaptureFps` (10–120), `liveCaptureFov` (30–110),
`liveCapturePitch` (-60–60) and `liveCaptureUsername` select capture conditions; FOV/frame-cap/toggle preferences
restore on completion. A different username's actual resolved skin model must be checked, not assumed.

First-person ready poses now solve in camera space before converting arm rotations, shoulder pivots and view
composition offsets into body space. This keeps the ready blade visible as body yaw catches up and preserves the
two-handed support grip. Active strike rigs and third-person blade alignment retain their existing solve.

`reload` invokes the normal resource reload during windup and requires a new animation generation and idle
completion on both sides. Loading-overlay/native-pause frames are excluded from pose screenshots; timestamps
retain the reload gap. This integrated-server reload includes Minecraft's normal loading pause.

`mob-attack`, `mob-morph` and `mob-kick` summon a controlled sword-equipped husk four blocks away and request
ordinary server attacks. These are rendering/transition fixtures, not tests of autonomous mob decisions.
Humanoid and illager model bones now share the 200 ms weapon/leg handover policy used by players. Mob held-item
rotations are already canonical, so they do not use PAL's item-axis conversion. Equipment, main-arm, generation
and render gaps reset history; ordinary weapon release and frozen pose-sheet samples bypass interpolation.

`mob-track-attack` and `mob-track-guard` move the observer 256 blocks away, start an ordinary actor action, then bring
the observer back while it is active. The attack scene requests the normal heavy upgrade. A temporary forced chunk
keeps the actor's server clock ticking; no-gravity staging prevents unrelated falls. The prior forced flag restores
after return. Guards require an unobserved interval followed by matching native client/server windup/guard state,
plus actual mob rendering later. Native state is recorded separately because chunk rebuild can delay visible poses.
The latest fixtures verify matching native state and later rendering; they do not establish real LAN latency
or immediate visible windup while chunks rebuild.

`active-parry` adds a separately tagged passive husk to the ordinary guard/riposte fixture. Its normal jab reaches
the front of the riposting player while active parry protects them. The capture records native server extensions,
owner packet-handler merges, actual client/server remaining ticks and relative monotonic timestamps. Acceptance
requires a positive confirmed merge within 100 ms of the server extension, an unharmed hero and continued release;
the earlier riposte grant alone cannot satisfy that observation. The matching-action merge preserves predicted
phase/elapsed and queued input. This integrated-server test does not establish arbitrary network latency.

`-PliveCaptureRenderer=sodium` requires an initialized `SodiumWorldRenderer`; `vanilla` requires its class to be
absent. The default `any` only records the result. Renderer discovery and the loaded-mod list are not substitutes
for this probe. `-PnoSodium` removes both the actual mod and its boot/FFA runtime dependencies from dev runs.

The `riposte` scene additionally switches the disposable player to survival, heals them during staging, and summons
one tagged, adult, NoAI husk with an iron sword. These world/game-mode changes persist in the disposable copy.
The integrated-server actor starts an ordinary deterministic slash after the player's real guard reaches the server.
After the client's caught-parry state arrives, normal attack input starts the riposte. No frozen state or synthetic
damage is applied. The capture requires caught-parry and active-parry attack outcomes on both sides, an actually
rendered opponent, reduced opponent health and no player health loss. This tests the specific integrated-server
exchange, not autonomous bot decision-making or real LAN correction. Contact effects/telegraph labels remain
visible and can pollute whole-image onion measurements.

`idle` sends no combat inputs and requires the player to stay idle throughout. `liveCaptureArm=RIGHT` or `LEFT`
temporarily changes the normal client main-arm option and broadcasts it to the server; it restores at completion.
Frames record actual rendered carried-arm rotation, plus server main arm, counter/thwack state and opponent phase
time. The opponent is bound only once capture is active, after staging cleanup/summoning has finished.
Each launch gives its actor a unique tag, so a stale saved opponent cannot be mistaken for the current fixture.

`interrupt-windup` and `interrupt-release` start a normal player slash and schedule the passive opponent's
ordinary jab to contact during the selected phase. A high-priority damage observer records the phase of real
positive damage before the normal flinch listener runs. Acceptance requires that contact phase, client/server
stagger and a rendered opponent; a windup interrupt must prevent release. Scheduling is tuned to the default
iron-sword fixture: changing weapon timings can invalidate the scene and must not be counted as a pass.

`itemuse` and `use-attack` switch the disposable player to survival, stage 64 offhand bread and set hunger to
14 with zero saturation. Vanilla item use is temporarily bound to numpad 6. `itemuse` holds it long enough to
consume food, then lowers the hand; `use-attack` sends an ordinary attack before consumption. Both require
client/server use to start and stop. Frames record use hand, hunger and stack count; the first scene requires
consumption and the second requires the stack to remain intact. Hunger/equipment changes persist in this world.
`drinkuse` stages one potion and requires a returned glass bottle on both sides. Consuming-hand blend weight and
rendered arm rotations support continuity checks; item IDs are recorded as strings, without loader-added fields.

`weapon-kick` replaces a normal slash windup through the kick binding. `kick-attack` starts a kick, buffers a slash
in recovery and requires its subsequent weapon release on both sides. `swap-weapon`/`swap-empty` stage an axe/air
in the next hotbar slot and press its normal vanilla hotbar key (temporarily numpad 7). These swaps must cancel
the old windup before release and finish with the expected equipment/idle state. Staged inventory/selection persist
in the disposable world; key bindings restore. Captures include main/attack item IDs, actual bone rotations/positions,
rig blending and native player-mesh presence. PAL combat-layer presence may be false during third-person idle
while the vanilla player mesh remains rendered. The first-person kick foot renderer remains separate work.

`counter` holds ordinary guard, observes the server opponent near the end of windup, then sends the matching slash
through ClientInput. Both sides must observe a successful counter, return damage and no player health loss.
`hitstop` strikes a passive, surviving opponent with a light mace; both sides must thwack. This is the blunt
contact stop. The rendered phase/progress/weight records also let us check a visual hit-stop sample stays held
while the simulated phase advances, then returns to the live timeline. These checks do not imply all correction,
reload, FPS or real-network hit-stop cases are verified.
