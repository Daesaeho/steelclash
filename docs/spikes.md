# Implementation findings

This page indexes the findings that constrain current work. The complete dated research, reproductions,
measurements and earlier checkpoints are retained in [findings history](archive/findings-history.md).
The [roadmap](next-combat-animation-plan.md) owns progress; [combat-rule evidence](combat-rule-evidence.md)
owns reference confidence and unresolved fidelity questions.

The [player grip and Vector API checkpoint](archive/grip-and-vector-checkpoint-20261013.md) records a bounded
player reach constraint, remaining rendered/mob grip gaps, and an isolated clip-sampling experiment. Overlapping,
noisy timings establish no Vector API benefit; the runtime retains its existing sampler.
The [physical player-grip checkpoint](archive/player-physical-grip-20261014.md) records bounded iron-sword contact,
heavy/morph/reload live controls, restored binding checks and unchanged occupied-shield controls. Pitched first-person
contact, blended recovery, near-plane crossings and broader model/family acceptance remain open.
Its [blended shoulder measurements](archive/player-physical-grip-20261014.md#separating-blended-shoulder-reach--2026-10-15)
separate total reach from an additive correction: six captured recovery targets exceed the current reach shell.
An offline joint socket/support solve is feasible for those rows; its runtime item-transform and motion checks remain open.

The [mob grip checkpoint](archive/mob-grip-20261014.md) reproduces both a discarded support-shoulder solve and
vanilla `attackTime` pivot interference. Rendered husk hand/handle contact, ordinary live morph release, handedness
and occupied-offhand controls verify the correction. Other rigs and remaining player grip gaps stay explicit.

## Mod IDs and Spartan integration

Spartan Weaponry is `spartan_weaponry_unofficial`; Spartan Shields is `spartanshieldsunofficial`.
Their integrations are optional. Reach and attack-speed traits use vanilla attributes. Only guarded compatibility
classes reference their APIs. Keep shield use active and preserve `LivingDamageEvent.Pre` so Spikes, Payback
and energy costs retain their hooks. See [mod IDs](archive/findings-history.md#mod-ids-verified-from-the-jars-meta-infneoforgemodstoml),
[reach/speed traits](archive/findings-history.md#b-are-sw-reachspeed-traits-attributes--yes) and
[shield/input findings](archive/findings-history.md#c-how-do-ss-block-and-bash-work--conflict-found).

## Sub-tick combat and Chivalry 2 timings

The simulation uses microseconds; profiles can override phase durations in milliseconds. The pinned Chivalry
data mapping includes the holding/chamber period in windup. Combo replaces the prior recovery rather than
requiring every combo windup to be shorter than a standalone windup. One-handed ripostes can retain standalone
startup. Review the [timing mapping](archive/findings-history.md#chivalry-2-timings-2026-10-07) and
[sub-tick architecture](archive/findings-history.md#sub-tick-combat-timing-architecture-plan-5-2026-10-07)
before changing balance. Existing [benchmarks](archive/findings-history.md#combat-benchmark-and-baseline-architecture-plan-3141-2026-10-07)
describe their measured versions, not current performance claims.
Representative native sword/spear/blunt timings, actual trace caps and AP catch costs are now recorded in the
[measured rule table](combat-rule-evidence.md#measured-representative-weapons); no new balance inference is made.

## Defense, counter-feint, cleave and release interrupts

Damage continues through vanilla `Player.attack` / `Mob.doHurtTarget`; accepted swings reset invulnerability
before their one-per-target dispatch. Defense, turn limits and collision belong to the server/core rules.
See [cleave/thwack](archive/findings-history.md#cleave-and-thwack-architecture-plan-18-2026-10-07),
[counter-feint](archive/findings-history.md#counter-feint-architecture-plan-131-2026-10-07),
[release interrupts](archive/findings-history.md#report-gaps-step-b-release-interrupts-report-28-2026-10-08)
and [bug audit](archive/findings-history.md#bug-audit-and-fixes-2026-10-08).

## Ranged stamina

`combat/RangedStamina` tells ranged weapons apart by use animation, not class: `BOW` is a draw, `CROSSBOW` a load
(a loaded crossbow fires from `use` without ever being "in use"), `SPEAR` a held-back throw. Spartan Weaponry's
longbows, heavy crossbows, javelins, throwing knives, tomahawks and boomerangs report those same animations
(GameTest `spartanRangedWeaponsFollowTheSameRule`). The drain runs in `Combat.tickServer`, so only entities with
combat state pay: players and the fighter roster, which does not include pillagers or Soldier archers. Running out
calls `stopUsingItem`, never `releaseUsingItem`, so nothing is shot or loaded. The owning client is told with the
existing `DRAW_INTERRUPTED` feedback at strength 0, which needed no protocol change. Vanilla's drowned keeps
`startUsingItem` active for its whole trident goal, so it pays the throw rate for as long as it has a target.

## Hit feel

- **Contact point.** `SwingTracer` tested the whole hilt-to-tip segment at each sample, so a hit was traced along
  the blade; contact metadata now distinguishes the historical collision entry, clamped historical body point
  and projection onto the target's current body. Original attack identity/tick/progress survive held delivery;
  resolution progress is separate. Old held hits cannot grant a later whiff a combo, and plain effects retain
  the source token used to reject stale hit-stop. These are AABB intersections, not rendered anatomical contacts.
  [Contact-frame contracts, protocol 13, verification and remaining checks](archive/contact-metadata-20261015.md)
  own the detailed evidence. Final main checks passed 281 fresh JUnit, 215 required GameTests per Spartan variant,
  the effects-disabled mutation and a merged 139-frame native riposte capture.
- **Swept blade.** Samples alone only test the blade where it is at each one: before, the fixed 6 a tick kept the gap
  under a mob's width at normal speeds. Now, when the tip would move more than 0.5 blocks between two samples, up
  to 16 more are taken on the true arc between them (`sweptBlade`). At default settings (360°/s turn cap, 6
  samples) the tip moves under 0.5 a sample, so ordinary swings cost nothing extra; the samples go in for flicks
  with the cap off, long reach or fewer samples. GameTest `aFlickedBladeIsSweptThroughTheBodyItPassed` flicks a stab
  90° in one tick at 2 samples: unswept it steps over a zombie, swept it meets it.
- **Hit location** (`core/HitLocation`) is proportions of a biped in its box: legs below 12/32 of the height, the
  head from 0.12 below the eye fraction, arms past 85% of the half width at chest height. Because the blade enters
  from the side it travels from, sweeping slashes often land on an arm; a player's level slash rises 8° at its
  start, so it meets a taller zombie at the neck (GameTest `tracedHitsLandWhereTheBladeCrossed`). Damage ignores
  the location (the step 2 decision of no melee headshot bonus still holds).
- **Model-space signs**, needed for the client reactions: model forward is -Z, the body's left +X, down +Y.
  `xRot` + swings a limb back and nods the head down; `zRot` + swings a limb to the body's right and tips the head
  to its left. The clip `body` channel is `Rz(z)·Ry(-y)·Rx(-x)` in model space (`WeaponRig.bodyRotation`), so a
  positive body X leans *back*, and the leg angles that undo a body rotation (x, y, z) are (x, y, -z). The aim lean
  bends the body by pitch × `aimLean` and takes the same off the head, which vanilla already pitches to the full
  view. In the frame `setupRotations` leaves (before the model flip), forward is -Z and the body's left -X; vanilla's
  death turn of +90° about Z lays the body on its left side.
- **Hooks.** Flinches, the death fall and head hiding use the existing `LivingEntityRendererMixin` injections, which
  run for players too (after Player Animation Library's `setupAnim`). `getFlipDegrees` is shadowed to undo vanilla's
  fall exactly. Parts changed after `setupAnim` must be copied to `hat`/sleeves/pants again in `HumanoidModel`,
  where they are siblings; `IllagerModel`'s hat is a child of the head and must not be (it would turn twice, and
  `setupAnim` never resets it). Hidden head parts are restored in `RenderLivingEvent.Post`, since models are shared
  between bodies. The body lean and leg footwork are applied to the pose before `CombatPresentation` blends or
  freezes it, so transitions and hit-stop include them.
- **Flying heads** draw the body's own head part (and the humanoid hat layer) from its renderer's model with its
  texture, reset to the modelled pose and restored after, at `AFTER_ENTITIES`, then end only that render type's
  batch so translucent batches keep their place. A level change clears them. Checked once in a live capture with a
  temporary hook that made every hit throw a head: a husk's head leaves upright along the blade and the body is
  drawn headless.
- **Cleave drag** (`CombatStateMachine.slowRelease`) keeps the release fraction and lengthens the remaining time,
  so the blade slows instead of jumping back; it is limited to slashes and overheads after it lengthened a landed
  sprint stab (`aMissedSprintAttackRecoversLonger`). It is applied once per tick after the contacts are resolved
  and sent non-authoritatively: an authoritative correction cleared the owner's queued combo and rewound its blade
  by half a round trip. The owner merges it instead (`mergeRelease`: same attack, both releasing, the longer
  release at the owner's own progress). `releaseSpanUs` remembers the slowed length, so a thwack traced after the
  release ended measures its contact on the release as it ran.
- **Gear before the blow** (`HitFeel.Struck`): the part, its armour and whether the head is bare are read before
  the hit is delivered. A killing blow runs the death drop inside vanilla's damage call, which empties the slots it
  drops (a player's always without keepInventory, a mob's by its drop chance, 8.5% for armour put on in code), so
  reading afterwards severed helmeted heads and played flesh instead of plate. It showed as a decapitation GameTest
  failing about one run in ten; the test now gives the helmet a guaranteed drop. The same tests swing one fixed arc
  each (`TestSupport.swing` picks a random variant and side, which moves where a blade lands).
- **Parry bounce** carries the server's attack serial; a client plays it only in that attack's stagger and drops it
  once that stagger has shown and ended. A special blocked by a weapon guard sparks but sends no bounce: its
  attacker isn't staggered.
- **Blood spray** is spawned by clients from `HitReactionPayload` (its `spray` count); `sendParticles` with a count
  of 0 sends one packet per particle per player, so the server now sends only the puff.
- Contact metadata extends `HitReactionPayload` and `FeedbackPayload`; both peers now use protocol 13.

## Mob weapon speed

Zombies, skeletons, piglins and the other fighters have no `ATTACK_SPEED` attribute, so a held weapon's speed
modifier never reached them and `CombatMath.timings` fell back to the profile's `reference_attack_speed`. Players
have the attribute (base 4.0) with the weapon's modifier applied. `CombatMath.attackSpeed` now applies the main-hand
stack's `ATTACK_SPEED` modifiers to a fresh `AttributeInstance` and reads its value, so vanilla combines them exactly
as for players (only when an attack starts, so the allocation doesn't matter). A stack with no such modifier keeps
the reference speed, so bare-handed claw and beast profiles (speed scaling 0) are unaffected (GameTest
`mobsSwingAtTheirWeaponsSpeed`). Measured before the fix, slash windup/release/recovery in ms for player | zombie:
stone axe 586/503/950 | 525/450/850, mace 645/613/968 | 500/475/750, trident 789/394/1015 | 700/350/900. A one-off
probe over all 378 Spartan Weaponry weapons with a profile found player and zombie timings equal for every one after
the fix. Before it, mobs ignored Spartan's per-type and per-material speeds both ways: a lance 700 ms windup against
the player's 828, a lead glaive 700 against 857, but an aluminum katana 500 against 408.

## Server combat cost

Being hurt does not give an entity combat state. Players and fighter mobs have it; a bystander gets it only from
what staggers it (a kick, a slam, a special). Code that needs a target's state must create it with `getData`,
not assume an earlier hit did. Defenders find incoming attacks in `combat/LiveAttacks`, which every server-side
`CombatStateMachine` feeds as an attack starts: a new route into a windup must announce itself there, or bots and
guarding dummies will not see it. The list is not in the level's order, so the choice between two simultaneous
attacks still asks the level. The spacing goal trusts the brain's weapon profile for one tick and one item.
The [2026-10-12 performance record](archive/combat-performance-20261012.md) has the equivalence checks, a
benchmark whose timings were too noisy to quote, and what was deliberately left alone.

## Animation presentation and first-person rigs

The weapon arm follows the hit arc; authored body and free-hand motion must preserve grip and blade alignment.
The [offline authoring prototype](animation-authoring.md) preserves base grip/camera settings, protects solved
arms and rejects unsupported transforms. Its scalar/handedness/seam checks do not prove Blockbench/render parity.
Ready rigs solve in camera space, including shoulder pivots, before body-space composition. Player and mob
held-item rotation conventions differ. Live transitions, rendered transforms and focused motion comparisons
supplement frozen pose sheets. Current contracts and capture instructions live in [animation.md](animation.md).
The [optional bend prototype](archive/pal-more-prototype-20261011.md) demonstrates why runtime injection order
and actual mesh vertices must be checked alongside idealized axis tests.
Frozen combat progress alone does not freeze vanilla free-arm breathing. Repeated shield shots differed by up to
0.99% without any backend change; fixing the pose-sheet model age removed the discrepancy. See the expanded
[pose comparison evidence](archive/pal-more-prototype-20261011.md#expanded-pose-comparisons-and-capture-clock-correction).
Relevant evidence: [early presentation](archive/findings-history.md#animation-presentation-findings-2026-10-08),
[arm motion](archive/findings-history.md#arm-motion-by-swing-type-2026-10-08),
[view-space ready rigs](archive/findings-history.md#view-space-ready-rigs-and-humanoid-handovers-2026-10-11)
and [baseline acceptance](archive/testing-history.md#broader-baseline-and-background-work-2026-10-11).

The first [native Blockbench sword source](animation-authoring.md#native-sword-source-checkpoint-2026-10-12)
survives save/reopen with explicit body/free-arm/leg tracks. The installed exporter folds body motion into the
protected weapon-arm channel. [Source-aware retargeting](animation-authoring.md#source-aware-native-import)
now validates that complete fold against the canonical project before recovering separate channels; the
PAL-only path continues to reject it. [Runtime evidence](archive/native-sword-retarget-20261012.md) records
active resource provenance and the corrected generic-heavy fallback; mesh/motion acceptance remains open.
The [tick-boundary pose refresh](archive/pose-clock-refresh-20261012.md) fixes stale pre-combat-tick samples when PAL
deduplicates a near-zero render partial. A deterministic native component probe complements ordinary captures;
it keeps PAL's clock and first-person ownership unchanged.
The [baked sword mesh probe](archive/blade-mesh-20261012.md) records actual submission matrices/vertices in
standard and Sodium paths. Sprite landmarks establish mesh axes and projected visibility; they do not turn the
debug reach ray into a physical mesh endpoint or prove palm/support-hand attachment.
PAL's [live checkpoint](archive/pal-more-prototype-20261011.md#live-transitions-and-lifecycle-checkpoint-2026-10-12)
adds heavy/morph, mob and reload evidence while retaining tracking/shutdown and alignment limits.

### Mob kick hand-swing dispatch

The pre-correction husk kick capture at `7e8c74c` showed continuous fast leg motion and a separate vanilla
weapon-hand swing during recovery. Finer bone samples resolve the coarse onion-skin flags into intervening
poses; the arm cycle fits vanilla zombie `attackTime` motion. This does not establish a rig-handover snap.
The local audit and 50 ms selection are retained in
`C:/dev/steelclash-beta-audit/20261014/kick-audit/AUDIT.md`.

`Combat.onReleaseStarted` now excludes non-player `KICK` from `LivingEntity.swing`; `KICK` also represents
shield bash, and both have dedicated clips. This prevents a new conventional hand animation without clearing
an already active legitimate swing. Slash, overhead, stab, jab, special and throw still dispatch their existing
main-hand animation and item swing hooks. Restricting the call to `isWeaponAttack()` would wrongly exclude
jab, special and throw. Player dispatch and damage rules retain their existing paths.

Two production GameTests use fresh passive husks and ordinary attack requests/ticks: kick/bash must start no
hand swing, while the other six releases must retain it and throw must also remove the held weapon. All 197
required GameTests passed with optional integrations and again after restoration with `-PnoCompat`, including
fresh compilation. Both mutations were caught by their intended tests: unconditional swing failed
`mobkickandshieldbashdonotstartvanillahandswings`; an `isWeaponAttack()`-only guard failed
`mobhandattacksretainvanillaswingdispatch`. Logs and the mutation report are retained beside the audit as
`gametest-default.log`, `gametest-161703814270.log` and `mutants.report.json`.

The post-correction ordinary-input husk capture retained 737 frames with the window hidden and mouse released at
`C:/dev/steelclash-beta-audit/20261014/mob-grip/kick-checkpoint-161802732404/live-kick`.
Across kick release/recovery, the weapon arm's yaw previously ranged from -0.100 to +0.500 radians;
all 59 post-correction samples stay at -0.100 radians. Its pitch now stays within ordinary idle bob rather
than tracing the conventional hand attack. A 26-frame selection on a 50 ms grid (maximum timestamp error
6.98 ms) and its inspected onion pairs confirm the extra arm cycle is absent. The selection still flags
fast leg motion, entry changes and the fixture's final reset; this is a bounded dispatch/rendering fix,
not acceptance of every transition, shield-bash appearance or first-person kick foot. Comparison evidence is
`kick-audit/after-comparison.json` and `kick-audit/after-50ms-onion/pairs.png` under the same day's audit root.

## Bug audit and active-parry acknowledgments

The [earlier bug audit](archive/findings-history.md#bug-audit-and-fixes-2026-10-08) records input, prediction,
network lifecycle and fixture findings. Its counts describe that checkpoint.

N07 is fixed at `c342391`: positive server extensions dispatch the existing snapshot and the owner merges only
the matching active action's remaining timer, accounting for packet age. Queued input and predicted phase stay
intact. Stamina-cost fidelity is a separate open question. See [rule evidence](combat-rule-evidence.md),
[implementation findings](archive/findings-history.md#active-parry-window-acknowledgments-2026-10-11)
and [verification](archive/testing-history.md#active-parry-extension-synchronization-2026-10-11).

## PvE weapon retrieval, team rules, downed and revive

Mobs share the combat rules. [Weapon retrieval](archive/findings-history.md#weapon-retrieval-2026-10-08)
is a separate PvE behavior. Test entities need deliberate position, facing, attributes and AI staging;
mock players are not automatically visible to level queries. See [team rules](archive/findings-history.md#team-rules-report-21-architecture-plan-19-2026-10-07),
[downed/revive](archive/findings-history.md#downed-and-revive-report-182-architecture-plan-211-2026-10-07)
and [GameTest fixture guidance](testing.md#fixtures-and-evidence).

## Optional compatibility and local loading mods

Candidate inspection is recorded in the [kinematics review](archive/kinematics-compatibility-review.md); support requires
the roadmap's runtime gates. BodyHealth/LSO are deferred. User-installed loading mods remain local and ignored,
not release dependencies; record the actual addon stack when comparing captures. See
[loading-mod evidence](archive/testing-history.md#optional-local-loading-mods-2026-10-10) and
[compatibility policy](compat.md).
