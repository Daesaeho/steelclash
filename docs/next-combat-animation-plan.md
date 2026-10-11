# Next combat and animation work

User scope, 2026-10-10: plan the next Steel Clash work; defer BodyHealth and Legendary Survival Overhaul (LSO).
Continue toward readable Chivalry-style combat with distinct original weapon-family styles and PvE priority.
This document plans implementation; it does not install candidates or change gameplay rules.

Original planning baseline: `2f4d121`, Minecraft 1.21.1, NeoForge 21.1.252, Java 21, PAL 1.1.6. The latest build and both Spartan
GameTest variants passed in GitHub CI. The animation checkpoint has 187 fresh local JUnit passes and seven
ordinary-input live capture scenes, totaling 970 frames. These do not establish every live transition or active
Sodium rendering. Preserve the existing local `AGENTS.md` and `tools/` work.

## 1. Close the important baseline gaps

Checkpoint progress, 2026-10-10: actual Sodium loading is repaired; torch/shield controls and the first live
block/riposte opponent scene pass with verified Sodium and vanilla renderers. Evidence and remaining cases are
in [testing.md](testing.md#verified-renderer-and-live-opponent-checkpoint-2026-10-10). The rest of this step is open.

Next checkpoint: matching counter, light-blunt thwack/visual hold and occupied-hand idle are now captured. C04
wall/floor cover and C09 stagger downgrade were reproduced and fixed under explicit rules. See
[testing.md](testing.md#carried-idle-counterthwack-and-c04c09-2026-10-10). Newer evidence follows below.

Next progress: ordinary opponent jabs now exercise incoming windup and release interruption, and vanilla offhand
food use exercises consumption and attack cancellation. The first four successful scenes contain 555 actual frames.
The vanilla eating/ready renderer handover visibly changes the hand/weapon pose; its presentation polish remains
open at that checkpoint. See [testing.md](testing.md#incoming-interruption-and-food-use-2026-10-10).

The following checkpoint replaces the offhand eating/drinking renderer handover with a continuous carried-hand
pose while retaining the weapon ready stance. Both main arms, attack cancellation, returned bottles, Sodium/vanilla
and a normal torch attack are captured; see [testing.md](testing.md#offhand-consumable-blend-2026-10-10).
The next checkpoint reproduces and fixes the local old-weapon pose after hotbar swaps and the abrupt player
weapon-to-kick rig reset. First/third-person and left-arm/reverse controls accompany the reproductions; see
[testing.md](testing.md#hotbar-swaps-and-player-kick-rig-handover-2026-10-10).
Next: moving/crouching/turning and the wider acceptance matrix before bending. First-person foot rendering,
mob rig handovers and tracking/reload/LAN acceptance remain separate gates.

Progress, 2026-10-11: fourteen successful baseline cases now cover movement/crouch/sprint/turn, both main arms,
SLIM/WIDE models, shield/third-person controls, 10/60 FPS and FOV 70/110, resource reload and controlled humanoid
mob slash/morph/kick. The turning ready-blade and mob kick handover bugs are reproduced and fixed in live captures.
See [testing.md](testing.md#broader-baseline-and-background-work-2026-10-11). Tracking fixtures, pitched-camera and
two-handed controls, final mutations and background-mode verification were still pending at that checkpoint.

**Step 1 baseline gate closed, 2026-10-11:** the additional tracking, pitched-camera and trident controls pass.
Twenty latest accepted cases contain 2,937 actual frames. Hidden keyboard combat, window visibility and mouse
ownership are verified; five targeted mutants are caught after strengthening the renderer test to avoid starting-yaw
dependence. Remaining unsupported LAN/model/camera/foot-renderer/startup-window cases are explicitly retained in
[testing.md](testing.md#broader-baseline-and-background-work-2026-10-11). This closes the bounded baseline gate,
not every human playtest or the beta goal. Step 2 now starts with confirmed active-parry extension synchronization
(report N07); stamina cost and other reference-fidelity questions remain evidence-led investigations.

**Deliverable:** a reliable renderer comparison and reproducible opponent-driven acceptance scenes.

- Repair the dev Sodium dependency/service loading and require the runtime renderer probe to report `active`.
  Repeat the torch attack and shield-return controls with the actual renderer enabled and disabled. A discovered
  distribution jar is insufficient. Other work can continue on the verified vanilla renderer while this is investigated.
- Extend the ordinary-input capture harness with a deterministic opponent for block/riposte, matching counter,
  blunt hit-stop, and incoming windup/release interruption. Record client/server outcome, actual frame times,
  equipment, model, camera and renderer conditions. Keep staging in disposable worlds.
- Add focused item-use/swap and weapon-to-kick cases, then left-main-arm and moving/crouching cases. Review
  first/third person and humanoid mobs; use onion-skin analysis and closer samples around suspected jumps.
- Reproduce C04 (slam cover) and C09 (hard/soft stagger precedence) against current source. Record the expected
  rule and minimal fixture before changing either. Promote a confirmed beta blocker ahead of optional animation work.

**Done when:** outcomes agree on client/server, captures expose the actual transition being tested, valid replays
pass, and every remaining unsupported case is recorded. A frozen pose sheet alone does not close a live gate.

## 2. Fix confirmed bugs and resolve the highest-impact fidelity questions

First verified checkpoint, 2026-10-11: report N07 is reproduced in the current server/client code and fixed.
Active-parry extensions now reach the predicted owner without restarting the action. Full build: 213 JUnit passes;
174 required GameTests pass with and without Spartan integrations; the real second-attacker capture and five
mutants verify server dispatch, client merge and stale/expired guards. See
[combat-rule-evidence.md](combat-rule-evidence.md) and [testing.md](testing.md#active-parry-extension-synchronization-2026-10-11).
Step 2 remains open for live matchup measurements and reference-fidelity decisions; active-parry stamina,
jab normalization and narrower hyperarmor windows are not changed without the required evidence.

**Deliverable:** small, independently reviewable combat fixes and a measured rule table.

- Fix reproduced blockers from step 1 with focused GameTests. For stagger precedence, explicitly define whether
  a later soft stagger can shorten or relax an existing hard guard break. For slams, define which cover blocks the
  area effect while preserving ally/pet protections.
- Investigate active-parry stamina: current catches pay block stamina, whereas the reference baseline describes
  free catches. Verify the intended rule with a traceable reference/reproducer before changing cost. Keep exact
  riposte/counter duration and extension values labeled as tuning estimates unless measured.
- Reconcile jab damage, release interruption and heavy-hyperarmor interactions. The current code differs from the
  older report's claimed patch; do not assume that patch is present or adopt the PDF's windup-only restriction blindly.
- Measure representative sword, spear and blunt matchups: startup, heavy upgrade, combo, riposte and turn cap.
  Use per-weapon data rather than replacing all windups with the PDF's generic 150-350 ms range.
- Preserve the 1.25x ordinary projectile headshot baseline and existing counter cost avoidance. Additional counter
  refunds, a Footman/Knight class system and new armor multipliers require their own evidence/balance decision.

**Done when:** each changed rule has a minimal reproducer, affected unit/GameTests and a mutation that the intended
assertion catches. Run Spartan integrations on/off for changed damage/event paths. Update the rule table with
reference confidence and test evidence; unresolved questions remain explicit.

## 3. Prototype PAL More Rotation on the existing rig

**Deliverable:** a reversible dev-only compatibility prototype, with a decision on whether to adopt it.

- Pin the reviewed 1.21.1 release and matching PAL/Bendable Cuboids dependencies in an isolated dev variant.
  Keep the normal build usable with the addon absent; do not turn the experiment into a mandatory dependency.
- Add one guarded item-axis convention adapter shared by the player layer and `RigPoseBlend`. Both compensate for PAL's swapped Y/Z item
  axes; More Rotation changes that transform. Cover both conventions with deterministic orientation checks.
- Start visible bends on torso and the non-weapon arm. Any weapon/support-arm deformation must preserve the
  solved grip and traced blade; prove hilt/tip alignment before extending it to two-handed styles.
- Compare sword and spear, both main arms, empty/torch/shield offhands, armor, first/third person and humanoid
  mobs. Verify the backend actually deforms the rendered mesh. Repeat key live transitions at low and high FPS.
- Keep Steel Clash's combat state/packets as the animation clock. The addon's generic play/sync API must not
  independently decide release, hit timing or interruptions. Document any integration mixins and their conflict risks.

**Done when:** enabled/disabled stacks preserve weapon orientation, grip, collision timing and clean hand/camera
ownership, and bending produces a visible improvement. Adopt only after those checks pass; otherwise retain
the existing backend and the findings from the experiment.

## 4. Build a Blockbench authoring workflow and refine weapon styles

**Deliverable:** editable original source projects and a validated import/conversion path into Steel Clash clips.

- Use the reviewed PAL Bend Player Tools workflow as the first prototype. Define the exact bone names, units,
  rotation order, bend channels and phase-boundary mapping accepted by Steel Clash's custom animation format.
- Implement a converter or importer for supported body/free-hand/foot channels. Reject unsupported data with
  useful diagnostics; validate finite values, ordered keys, phase boundaries and fallback behavior.
- Keep the weapon arm driven by `ArcPath`/`ArmAim`. Authored body motion and any bend adapter must respect its
  grip constraint. Scale/reach edits in an animation asset must not silently alter server collision.
- Round-trip one sword clip first, then refine three contrasting families: compact dagger, thrust-led spear and
  weighty blunt. Once the workflow works, extend it to the other six families and light/heavy/mirrored variants.
- Compare the same old/new poses and live transitions. Preserve original family identities, readable windup,
  contact-aligned release and recovery. Check F3+T reload and invalid-file retention without stale captures.

**Done when:** exported assets reproduce the intended motion, phase seams remain continuous, original projects are
editable, invalid imports fail clearly, and alignment/smoothness checks pass without changing gameplay timing.

## 5. Add accurate server collision in two stages

**Deliverable A:** authoritative contact metadata while preserving the current default collision behavior.

- Extend contact/captured-hit data with the actual collision point, normal where meaningful, attack identity and
  progress/time. Derive these from the intersection, not the target center. Distinguish an inflated collision surface
  from an exact rendered-mesh point.
- Carry that identity through lag-held delivery, cleave, block and hit-stop. Preserve vanilla `Player.attack` /
  `Mob.doHurtTarget` dispatch, enchantments and Spartan shield event hooks. Add contact-point debug visualization.
- Test direct/deferred consistency, multiple contacts, blocked hits, wall clanks and collision-radius boundaries.

**Deliverable B:** an optional anatomical narrow phase for supported humanoids, including PvE mobs.

- Keep entity AABBs for broadphase, then test canonical server-owned head/torso/limb volumes. Share deterministic
  pose/geometry math where practical; dedicated servers cannot depend on client render classes or visibility.
- Start with ordinary player and zombie/husk dimensions and poses. Establish consistent crouching/downed shapes
  and bounded historical geometry. Define a fallback for unsupported entities and missing history before rollout.
- Keep cosmetic CPM sizes/hidden parts separate from legal hurtboxes unless an explicit server policy supports
  them. Test blade-versus-volume alignment and fast/long custom arcs; six fixed temporal samples are not proof
  that every sweep is continuous.
- Test Countered's optional client picking/visualization alongside Steel Clash. It can remain useful on the client,
  but its inspected render-captured boxes are not the authoritative server collision provider. Preserve a usable
  client/server setup with Countered absent.

**Done when:** hits follow the selected server geometry, unsupported mobs retain an explicit fallback, dedicated
server loading works, and contacts/lag/cleave/defense tests pass with and without optional mods. Anatomical regions
initially locate hits; they do not introduce limb HP, melee headshot bonuses or new injury effects.

## 6. Evaluate CPM after the default rig is stable

**Deliverable:** one cosmetic model/animation proof of concept and an adoption decision.

- Retarget a sword-ready pose and one attack/recovery to a simple altered CPM rig. Define which backend owns each
  bone and how combat phase/progress drives named poses. Avoid concurrent PAL/CPM transforms of the same bone.
- Verify feint/interruption/authoritative correction, item attachment, tracking, model replacement and reload.
  Keep collision/reach independent of arbitrary cosmetic geometry.
- Use CPM Animator Utils only if the CPM authoring workflow is adopted, after verifying a matching CPM version.
  It is not required for the Blockbench/PAL workflow in step 4.

**Done when:** the prototype preserves attack readability and grip across the altered rig and cleanly falls back
when CPM/model assets are absent. Broader model support remains conditional on that result.

## Checkpoints and scope boundaries

After each coherent implementation step, run the checks relevant to the change, update the changelog/findings/manual
checklist, then commit and push under the user's standing authorization. Preserve unrelated local work. Publishing
and release tags still require explicit authorization. Documentation-only planning needs the repository compile
check and link/diff validation; it does not require new gameplay tests.

BodyHealth and LSO are **deferred**: no installation, adapter, injury/death integration or additional investigation in
this sequence. Full class/loadout systems, uncertain stamina refunds and wholesale netcode replacement are also
separate decisions. Keep the existing beta goal's human playtests and unfinished audit candidates visible.

The first implementation checkpoint should therefore combine the actual Sodium-loading diagnosis with the smallest
opponent-driven live capture/reproducer. Confirmed combat blockers take priority, followed by the PAL bending trial.
