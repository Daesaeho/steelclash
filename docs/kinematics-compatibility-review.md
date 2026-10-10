# Kinematics and optional-mod review

Reviewed 2026-10-10 against Steel Clash `3d65b40`, Minecraft 1.21.1, NeoForge 21.1.252, Java 21 and Player Animation Library (PAL) 1.1.6. This is a source and artifact review, not an in-game compatibility certification. The proposed mods were not installed or added as dependencies.

The primary comparison is the supplied three-page **Chivalry 2 Melee Kinematics Specification**. Its numbers and proposed implementation are not developer-published Chivalry constants. The earlier expanded Chivalry report and Steel Clash bug inventory are used to reconcile conflicting claims, not to declare every historical bug newly verified. Existing user files and unrelated work are preserved.

## Recommendation

1. Add server-authoritative contact location and anatomical collision to Steel Clash before connecting a limb-health backend. Countered's inspected implementation cannot supply this on a dedicated server.
2. Trial PAL More Rotation for visible elbow/body bending, with an explicit item-axis adapter and blade-alignment checks. It is the closest fit to the current animation stack.
3. Treat BodyHealth and Legendary Survival Overhaul (LSO) as alternative, optional injury modes. Choose one health owner per player. Their injuries and survival rules extend the combat design; they are not necessary to reproduce Chivalry's melee rules.
4. Keep CPM as an optional cosmetic/animation backend. CPM Animator Utils helps author CPM assets; it does not add runtime collision or improve Steel Clash clips automatically.

## Inspected versions and sources

| Candidate | 1.21.1 NeoForge evidence | Review scope |
|---|---|---|
| [Countered's Accurate Hitboxes](https://modrinth.com/mod/countereds-accurate-hitboxes) | 2.0.1, Modrinth version `5HTWuuif`; requires Architectury 13.0.8+ | Inspected the distributed jar, mixin configuration, CFR 0.152 output and `javap` bytecode. |
| [BodyHealth](https://github.com/Smiley-droid/bodyhealth) | Root project 12.5.1, built against NeoForge 21.1.172 | Source commit `806e8bd77b1062bd298adda81d5288d557b09c34`. The nested `bodyhealth-1_21_1-fixed/bh_fixed` project is older 12.2.0; root findings do not certify every distributed jar. |
| [Legendary Survival Overhaul](https://modrinth.com/mod/legendary-survival-overhaul) | 1.21.1-2.4.7.2, version `AZzEduN3` | [Source](https://github.com/Alex-Hashtag/LegendarySurvivalOverhaul) commit `4abeefba3adb2087b4ff7aa7ace83f03189042cc`, NeoForge 1.21.1 tree. |
| [Customizable Player Models](https://modrinth.com/plugin/custom-player-models) | 1.21v0.6.27a NeoForge artifact, version `YXfPij2E` | [Source](https://github.com/tom5454/CustomPlayerModels) commit `1e94ed23f7d1d9b253d285c98acc3427bd71bdd0`; shared APIs. Use the NeoForge mod artifact for this project. |
| [CPM Animator Utils](https://modrinth.com/mod/cpm-animator-utils) | 1.1.0 NeoForge, version `F1ZsvLHJ` | Author's feature/dependency description and version metadata. Listed CPM dependency is 0.6.24-0.6.25; compatibility with CPM 0.6.27a has not been demonstrated here. |
| [PAL More Rotation](https://modrinth.com/mod/playeranimationlibrarymorerotation) | 1.1.0+mc1.21.1-neoforge, version `qhKVkTkf` | [Source](https://github.com/kltyton/PlayerAnimationLibraryMoreRotation) commit `e02f9924dee3217efc6f31c782f9f948638c74ff`, specifically `versions/1.21.1`. That tree targets PAL 1.1.6, Bendable Cuboids 2.0.0+alpha.1 and NeoForge 21.1.250. |

Countered jar SHA-256: `b74522ec6301cea7630cc0755a5d49340fbc5a74df90737874a3bbd9835c529d`.
Downloaded artifacts, extracted PDF text/pages, source clones and decompilation remain outside the repository at `C:/dev/steelclash-beta-audit/20261010/compat-research`. No third-party implementation was copied into Steel Clash.

## What each mod actually changes

### Countered: rendered-model picking, not Steel Clash's server sweep

The inspected jar puts all five mixins in the **client** list. `LivingEntityRendererMixin` and `ModelPartMixin` collect transformed model cubes while an entity renders. `EntityMixin` stores that list; it does not replace `Entity.getBoundingBox()`. `GameRendererMixin` replaces the client crosshair-picking ray with ray/OBB tests. Its hook skips candidates without captured boxes and has no original-ray fallback. The hook's internal ten-block ray is not proof of ten-block legal melee reach; the surrounding picker and server still matter.

`ModelOBB` has a name field, but `OBBCaptureContext.captureCube` names every captured box `"cube"`. The inspected capture path therefore does not provide semantic HEAD/ARM/TORSO labels. Nor does the jar provide a server bone-pose history or a server blade-versus-model sweep API.

Steel Clash's [SwingTracer](../src/main/java/com/steelclash/combat/SwingTracer.java) queries living entities and tests blade segments against `getBoundingBox()` on the server. It does not call `GameRenderer.pick`. Installing Countered consequently does not replace Steel Clash's collision. This is an implementation finding from the 2.0.1 artifact; the project's broader advertised client/server support does not change that path.

Useful possibilities: client selection/debug visualization and geometric reference for a separately implemented server collision provider. Render-derived boxes alone cannot decide dedicated-server damage. CPM/custom renderers may bypass the `ModelPart.compile` hook, so their boxes require a real rendering test rather than an assumption.

### BodyHealth: six injury pools and a different damage economy

The [root resolver](https://github.com/Smiley-droid/bodyhealth/blob/806e8bd77b1062bd298adda81d5288d557b09c34/src/main/java/com/bodyhealth/common/HitboxResolver.java) chooses a melee region from the **attacker's eye position** relative to target height and world X. It does not consume a blade intersection and does not rotate left/right selection into target-local space. Projectile routing uses the projectile position. This is substantially coarser than the PDF's posed-limb collision.

The [damage handler](https://github.com/Smiley-droid/bodyhealth/blob/806e8bd77b1062bd298adda81d5288d557b09c34/src/main/java/com/bodyhealth/events/DamageEventHandler.java) stores the direct entity during `LivingIncomingDamageEvent`, then applies injury after final vanilla damage in `LivingDamageEvent.Post`. Region scaling is head 1.5x, torso 1.0x, arms 0.85x, legs 0.90x, including melee. It also performs its own protection/effect adjustments. Reapplying reductions after final damage needs a controlled enchantment test before integration.

Its torso-death rule, injured-arm item loss and health-restoring player tick interact with Steel Clash's downed/revive state and stamina disarm. These are distinct causes of item loss/death, not the PDF's stamina-only disarm. The public [BodyHealthAPI](https://github.com/Smiley-droid/bodyhealth/blob/806e8bd77b1062bd298adda81d5288d557b09c34/src/main/java/com/bodyhealth/api/BodyHealthAPI.java) exposes part damage/healing but no contact-selection override was found in the inspected API. Calling its damage function in addition to its ordinary listener would risk applying injury twice. Its player-only pools also do not solve Steel Clash's PvE mob collision.

### LSO: configurable survival injuries with early damage processing

LSO adds temperature, thirst and a health overhaul alongside eight body regions. Its [damage event handler](https://github.com/Alex-Hashtag/LegendarySurvivalOverhaul/blob/4abeefba3adb2087b4ff7aa7ace83f03189042cc/src/main/java/sfiomn/legendarysurvivaloverhaul/common/events/CommonNeoForgeEvents.java) applies localized player injury in a LOWEST-priority `LivingIncomingDamageEvent` listener. It checks recently shield-blocked players and can change the incoming health damage. This operates before the final vanilla armor/enchantment result, unlike BodyHealth's Post handler.

Its [PlayerModelUtil](https://github.com/Alex-Hashtag/LegendarySurvivalOverhaul/blob/4abeefba3adb2087b4ff7aa7ace83f03189042cc/src/main/java/sfiomn/legendarysurvivaloverhaul/util/PlayerModelUtil.java) divides standing/crouching AABBs into height bands, with a swimming case. Melee estimates an intersection from an expanded attacker AABB and chooses one possible region, with random fallback. Projectile routing uses an intersection-box center. It does not track animated elbow/arm geometry or the weapon contact that Steel Clash traced.

The [body configuration](https://github.com/Alex-Hashtag/LegendarySurvivalOverhaul/blob/4abeefba3adb2087b4ff7aa7ace83f03189042cc/src/main/java/sfiomn/legendarysurvivaloverhaul/config/BodyDamageConfig.java) defaults to a 2x projectile headshot multiplier without a helmet. Steel Clash already applies its own 1.25x projectile modifier: both enabled could produce 2.5x health damage when both head checks succeed. The part injury and later health multiplier may also use different amounts. Assign exactly one modifier owner and test both routes.

The public [body-damage API](https://github.com/Alex-Hashtag/LegendarySurvivalOverhaul/blob/4abeefba3adb2087b4ff7aa7ace83f03189042cc/src/main/java/sfiomn/legendarysurvivaloverhaul/api/bodydamage/IBodyDamageUtil.java) supports part damage/healing; it is not a swept-contact selector. An adapter needs an upstream selection hook or a narrowly versioned integration, not an extra damage call alongside the automatic listener. LSO is the candidate when the broader survival systems are wanted; BodyHealth is the more focused injury candidate. Neither has passed a Steel Clash playtest.

### CPM and CPM Animator Utils: cosmetic rig and authoring tools

CPM can provide original cosmetic models and named poses/gestures for weapon styles, idle stances and reactions. Its [common API](https://github.com/tom5454/CustomPlayerModels/blob/1e94ed23f7d1d9b253d285c98acc3427bd71bdd0/CustomPlayerModels/src/shared/java/com/tom/cpm/api/ICommonAPI.java) has `playAnimation(playerClass, player, name, value)`; its [client API](https://github.com/tom5454/CustomPlayerModels/blob/1e94ed23f7d1d9b253d285c98acc3427bd71bdd0/CustomPlayerModels/src/shared/java/com/tom/cpm/api/IClientAPI.java) exposes model loading/rendering, poses and plugin messages. These are presentation APIs, not server hit detection.

A Steel Clash adapter must map combat phase/progress to CPM, stop interrupted gestures, handle tracking/reload, and define which backend owns each bone. Triggering an independently timed gesture is insufficient for heavies, feints or correction. Arbitrary cosmetic size, hidden limbs or extra parts should not silently change reach or legal hurtboxes. Retargeting must preserve hilt attachment on altered arm lengths and pivots.

[CPM's Blockbench plugin](https://github.com/tom5454/CustomPlayerModels/blob/1e94ed23f7d1d9b253d285c98acc3427bd71bdd0/Blockbench/README.MD) imports/exports CPM models and animations; the author labels animation import/export beta. CPM Animator Utils adds timeline scrubbing, frame organization and pivot editing inside CPM. It helps if we choose that authoring workflow; it does not convert Steel Clash's custom animation JSON or add runtime blending/IK by installation alone.

### PAL More Rotation: useful deformation, explicit axis conflict

The 1.21.1 source matches Steel Clash's PAL version and offers multi-axis bends, bend position/scale and playback/sync utilities. This could make elbows, torso anticipation and offhand poses more expressive while keeping the weapon arm tied to the authoritative arc.

There is a concrete integration conflict. Steel Clash's [itemRotation](../src/main/java/com/steelclash/client/ProceduralSwingAnimation.java) compensates for PAL's item Y/Z convention with `bone.addRot(-x, -z, -y)`. More Rotation's [PlayerItemRotationFixMixin](https://github.com/kltyton/PlayerAnimationLibraryMoreRotation/blob/e02f9924dee3217efc6f31c782f9f948638c74ff/versions/1.21.1/common/src/main/java/com/kltyton/playeranimationlibrarymorerotation/mixin/PlayerItemRotationFixMixin.java) removes the swapped PAL transform and applies standard item axes. Keeping both assumptions would change weapon orientation. Add a version-aware convention adapter and compare hilt/tip alignment in both stacks before claiming compatibility.

Its recommended [PAL Bend Player Tools](https://github.com/kltyton/Pal-bend-player-tools/blob/main/pal_bend_player_tools.js) Blockbench exporter produces PAL animation JSON under `player_animations`. Steel Clash uses its own normalized, phase-based `steelclash_animations` format and procedural arm solve. A converter or runtime adapter is required. Multi-axis bend metadata also needs an actual Bendable Cuboids backend; writing bend values alone does not demonstrate visible deformation. Keep Steel Clash's existing state packets authoritative rather than running a second combat clock through the library's generic playback sync.

## PDF versus current Steel Clash

Status means **present**, **partial/missing**, **different**, or **reference needs verification**. A mismatch alone is not permission to rewrite gameplay.

| PDF claim | Current implementation and difference | Status / next step |
|---|---|---|
| Tutorial-dummy, slow-motion and competitive measurements | Training/capture tools exist, including live ordinary inputs. No complete final-patch, per-weapon empirical dataset is established by these Minecraft captures. | Partial: record reference version, attack, timestamps and source confidence before tuning. |
| Windup / release / recovery | Server state machine, client prediction, phase clips and procedural weapon arm already exist. | Present. |
| Universal 150-350 ms windup | Base sword slash is 500 ms, dagger slash 450 ms, before attack-speed scaling/heavy/riposte changes. Profiles use microsecond simulation with millisecond overrides. | Different/verify: use per-weapon reference data, not a universal range. |
| Accels/drags move impact time | The trace follows turn-capped yaw/pitch through release. | Present; rendered-blade calibration and live opponent checks remain. |
| Recovery is defenseless unless comboing | `CombatStateMachine.canParry()` permits recovery defense; queued combos and ripostes have their own eligibility/timing. | Different: the PDF's blanket statement is too coarse to use as a new rule. |
| Feint windup into block; modular clips | Feint-to-idle, cancel-to-parry and morph already exist. Normalized per-phase clips and transition tails exist. Blockbench/PAL/CPM formats are not interchangeable. | Present mechanics; authoring adapters and broader transition acceptance incomplete. |
| 3-5 blade points and continuous mesh sweep | `SwingTracer` tests the complete hilt-tip segment at six temporal substeps per tick against inflated target AABBs. It uses a profile arc, not rendered mesh vertices. | Partial: sampled sweeps can still tunnel for extreme custom profiles; six temporal steps are not six points along the blade. |
| Limb/head/torso collision through Countered | Server target AABBs remain the collision shape. Contacts contain target and release progress, without point/normal/part. Countered hooks client picking. | Missing: server anatomical provider and contact metadata. |
| Active parry around 0.5 s, zero stamina, no flinch | Riposte guard defaults to 9 ticks (450 ms), counter guard 15 ticks (750 ms), +2 ticks per caught hit. Outgoing attack continues and active parry does not disarm, but the catch **spends block stamina**. | Different: resource cost is a fidelity candidate; exact durations/extensions are not official constants in the expanded report. |
| Frontal 140-180 degree geometry | Weapon guard is a normalized horizontal direction cone, sword default 140 degrees. Basic/tower shields default 150/180 degrees. | Partial: no full 3D blade/contact-facing guard volume. The PDF dot expression also needs a normalized attacker direction to be distance-independent. |
| Hook `LivingAttackEvent` / `LivingHurtEvent` | Current NeoForge 1.21.1 uses `LivingIncomingDamageEvent`, `LivingDamageEvent.Pre/Post` and `LivingShieldBlockEvent`. | Reference/API correction; keep vanilla damage dispatch and Spartan shield listeners intact. |
| CUT neutral; CHOP +17.5%/+25%; BLUNT +35%/+50% against Footman/Knight | Damage types currently multiply by Minecraft armor-point tiers **in addition to vanilla armor**. CUT: 1.20/1.10/0.90/0.70; CHOP: 1.10/1.05/1.00/0.90; BLUNT: 0.90/0.95/1.05/1.25. PIERCE adds another class. | Major difference: no Footman/Knight chassis or 150/175 HP mapping. A class-based mode needs an explicit balance policy. |
| Cut/chop cleave; light blunt stops except lethal/heavy | `ContactPolicy` defaults implement blunt cleave-on-kill and heavy cleave; other types default to cleave. Attack-specific `max_targets` still caps contacts (sword slash 3, stab 1). | Present with profile limits; not unlimited crowd hits. |
| Initiative after hit/block | Combo eligibility/timings, faster riposte paths and attacker stagger already model initiative without a separate meter. | Present; validate actual weapon matchups rather than assuming every return is faster. |
| Jab strictly interrupts windup | Jab uses the normal damage/flinch path, including deferred release interruption when enabled; heavy hyperarmor can exempt the defender. Jab startup is 5 ticks (250 ms), damage remains a weapon multiplier (default 0.25). | Different: strict windup-only behavior and weapon-independent low damage are not current rules. |
| Kick never interrupts strikes; guarded target cannot parry for 1 s | Kick protects attacking targets from interruption; guarded targets receive 20-tick hard stagger. Unguarded, non-striking targets receive a separate 12-tick soft stagger. | Core present. The hard window does not guarantee a slow/far follow-up physically lands. |
| No melee headshot; projectile 1.5x | Steel Clash has no added melee region bonus; projectile default is 1.25x. BodyHealth would add a melee head bonus; LSO could add a second projectile bonus. | PDF correction: official Fight Knight changed ordinary projectiles to 1.25x, with a 1.5x Sniper perk. |
| Ordinary backstab, Ambusher +50% | No Steel Clash Ambusher class/passive exists. Optional Spartan backstab traits are preserved by vanilla attack dispatch. | Missing class feature; imported traits are not an Ambusher implementation. |
| Block stamina, immediate zero-stamina disarm | Weapon/shield blocks spend profile/config stamina; zero-stamina melee block disarms with stagger. Drop is default; optional holster differs from the PDF. | Present translation; weapon mitigation coefficients still need reference calibration. |
| Counter avoids block cost and slightly refills stamina | A successful counter avoids incoming block cost but grants no separate refund. | Cost avoidance present; refund differs from PDF, but the expanded report explicitly marks additional restoration unresolved. |

For the projectile correction and cleave-on-kill history, see [Torn Banner's Fight Knight 2.2 notes](https://chivalry2.com/2021/10/25/chivalry-2-content-update-fight-knight-2-2/). This proves that patch's change, not that every final-patch balance value has been exhaustively checked.

## Reconciliation with the older reports

The expanded Chivalry report sections 4.5/4.7 distinguish established counter cost avoidance from unverified refunds and numeric active-parry windows. That uncertainty should survive the shorter PDF. Its source-tagged claims are a better tuning baseline than treating all PDF numbers as exact.

The historical Steel Clash report says a fixed `jabDamage` setting and jab-against-hyperarmor handling were implemented in its reviewed patch. Those settings/exception are **not in the current inspected tree**: `Jabs.spec` still uses `jabDamageMult`, and current flinch checks use `Combat.hasHyperArmor` without a jab exemption. Do not carry that historical verification forward as current coverage. This review identifies the divergence; it does not claim a newly executed jab reproducer or silently port the old patch.

Its broader known limitations also remain relevant: historical target positions are combined with current defense facing/state, same-tick releases can trade rather than being globally ordered by exact contact time, and rigid grips/rendered reach need calibration. These new mods do not solve those server rules. Distinct weapon-family style drafts and the torch/helmet live transition fixes are now present, but live opponent reactions, correction, tracking/reload, item swaps, locomotion and custom models remain acceptance work. Slam cover geometry (C04) and stagger precedence (C09) remain separate open audit/policy candidates, not completed by this compatibility research.

## Integration design and acceptance gates

The proposed boundary is: **server sweep -> authoritative contact -> defense -> normal vanilla damage -> one injury backend**. Keep contact identity through held/lagged hits: attacker/swing serial, target, progress/time, world contact point, anatomical region and collision-provider version. The present target/progress record is insufficient for dependable limb routing.

Start with deterministic vanilla humanoid regions derived from pose/yaw and bounded history, then a shared authoritative skeletal provider where needed. Keep server geometry independent of client visibility, frame rate and arbitrary cosmetic models. Define an explicit fallback for unsupported mobs, unloaded pose history and missing backends. The PvE path must remain usable without player-only injury APIs.

Use guarded optional classes under `compat`, and one owner for headshot scaling, part damage, death/downed state and item loss. Add a selector override rather than duplicate injury delivery. Prefer an upstream event/API; a required mixin must be version-gated and documented in `mixin-risk.md`. Do not cancel `LivingDamageEvent.Pre` to bypass shield processing: Spartan Spikes/Payback/energy costs depend on that event.

Before claiming support, test dedicated-server loading with each mod absent/present, and each health backend separately. Check blocked/countered hits produce no injuries, one accepted sweep produces one injury, lag-held hits retain the original region, enchanted damage is reduced once, projectile multipliers have one owner, and death/downed/revive/totems/save/reload remain consistent. Test rotated/crouched/swimming players, attacking limbs, mobs, wide CPM models and missing/stale render data. An API/source review cannot substitute for these tests.

For More Rotation/CPM, compare first/third person, both main arms, armor/offhand/shields and hilt-tip alignment with the addon enabled/disabled. Retiming, interruptions, combos and correction must follow Steel Clash's existing phase clock. Include normal live captures and actual renderer identity; discovery of a Sodium jar alone does not prove Sodium rendering.

Current verification for the separate animation checkpoint: fresh compile/build, 187 JUnit tests, seven successful live scenes totaling 970 frames, and two actual-client reversions caught by their intended assertions. The active Sodium renderer gate remains open. No compatibility GameTests or multiplayer certification for the researched candidates are claimed.
