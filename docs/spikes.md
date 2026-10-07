# M0 spikes

Answers to the questions in PLAN.md §8 (M0). Each finding says how it was verified.

## (a) Does `SweepAttackEvent` exist in NeoForge 21.1.x? — YES
Verified in NeoForge source, branch `1.21.1`: `net.neoforged.neoforge.event.entity.player.SweepAttackEvent`.
- Fires after `CriticalHitEvent`, on both logical sides.
- `isVanillaSweep()`, `isSweeping()`, `setSweeping(boolean)`, `getTarget()`.
- **Use:** `setSweeping(false)` whenever the attack comes from one of our swings. Our arcs replace vanilla sweep.

## (b) Are SW reach/speed traits attributes? — YES
Verified in SW source (`api/trait/ReachWeaponTrait.java`, `api/trait/SpeedModifierWeaponTrait.java`).
- `REACH_*` → `Attributes.ENTITY_INTERACTION_RANGE`, `ADD_VALUE` of `magnitude - 5.0`. **Use `player.entityInteractionRange()` as the blade length base; no SW-specific code needed.**
- `HEAVY_*` / `LIGHTWEIGHT_*` (`SpeedModifierWeaponTrait`) → `Attributes.ATTACK_SPEED`, `ADD_MULTIPLIED_BASE`. **Scale phase timings from the ATTACK_SPEED attribute; SW speed traits then work automatically.**
- `QUICK_STRIKE` is **not** an attribute. SW's config has `traits.quick_strike.hurt_resistance_ticks = 14`, so it changes the target's invulnerability frames after a hit. **Decision (M1):** our swings reset `invulnerableTime` to 0 before every hit, since each swing hits a target at most once. That makes QUICK_STRIKE's i-frame tweak irrelevant to Steel Clash swings. Its "faster weapon" intent is expressed through faster profile timings instead (daggers and rapiers have short windups). The tweak still affects other damage sources, which is SW's normal behavior.
- SW config also has mob weapon spawn chances (`zombie_with_melee_spawn_chance_normal = 0.05`, `_hard = 0.25`; the same for piglins and wither skeletons). This is relevant for PvE: modpacks can raise these so more mobs carry Spartan weapons and use their profiles.

## (c) How do SS block and bash work? — CONFLICT FOUND
Verified in SS source (`event/CommonEventHandler.java`, `event/ClientEventHandler.java`).

**Bash (client):** `InputEvent.MouseButton.Post` and `InputEvent.Key` → `checkForShieldBash()`. While the player is blocking:
```java
ModKeyBinds.KEY_ALT_SHIELD_BASH.isUnbound() ? mc.options.keyAttack.consumeClick() : ModKeyBinds.KEY_ALT_SHIELD_BASH.isDown()
```
→ sends `ShieldBashPacket(hand, targetId, hit)`. The alt key is **unbound by default**, so **attack while blocking = SS bash**. It consumes the attack click.

**Problem:** with a shield raised, our left-click attack would be eaten by SS. In Chiv 2, attacking with the shield up lowers the shield and attacks.

**Fix (M2):** handle left-click in `InputEvent.MouseButton.Pre` (cancellable, fires before the click reaches `KeyMapping`) while holding a profiled weapon. Cancel it and send our own attack intent. The click count never increments, so SS's `consumeClick()` gets nothing. Our **kick key** performs the bash with our own implementation (`ShieldBashPacket` is SS-internal). Document that players should leave SS's alt-bash key unbound.

**Damage (server):** one listener, `LivingDamageEvent.Pre` (default priority). It doesn't cancel anything. When the player's *active use item* is a SS shield, it runs:
- the Spikes enchantment (reflects damage to the attacker)
- the Payback enchantment (stores absorbed damage)
- `IDamageShield.damageShield()` when `damage >= 3.0`, which handles energy-shield costs

**Use:** keep the player actually *using* the shield item while guarding (`isUsingItem()`, `getUseItem()` = shield). Otherwise these SS features silently stop working. Never cancel `LivingDamageEvent.Pre` for shield users.

**Still to verify in game:** whether `LivingDamageEvent.Pre` fires at all when vanilla blocking negates 100% of the damage. That decides whether Spikes/Payback trigger on full blocks today.

## (d) playerAnimator first-person — IMPLEMENTED, needs a visual check
Verified from the playerAnimator 2.0.4 jar (`javap`): `IAnimation` can be implemented **procedurally**. `get3DTransform(part, type, tickDelta, value)` returns rotations per body part ("rightArm", "body", ...). `getFirstPersonMode()` returning `THIRD_PERSON_MODEL` renders the model's arms in first person, and `FirstPersonConfiguration(showRightArm, showLeftArm, showRightItem, showLeftItem)` picks which parts show. Layers are registered with `PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(id, priority, factory)`.

M1 uses this in `client/ProceduralSwingAnimation`: the right arm points along the live arc (same math as the server trace), the torso twists into the swing, and the arm shows in first person. **Still to confirm by eye in game:** the arm direction signs, and how the held item looks (the item points "up" out of the fist, not along the arm, so it won't line up with the traced blade until M4's authored animations).

## (e) Does cancelling the attack input break block mining? — ANSWERED (code reading), confirm in game
`MouseHandler.onPress` fires `InputEvent.MouseButton.Pre` *before* `KeyMapping.set()`/`click()`. Cancelling it means `keyAttack` never goes down, so `Minecraft.startAttack()` and `continueAttack()` (block mining) never run, and neither does Spartan Shields' `consumeClick()` bash. `ClientInput` cancels left-click only while a profiled weapon is held, **except sneak + left click on a block**, which passes through so SW `VERSATILE_*` weapons can still mine. The matching release is swallowed only if we swallowed the press. Note: this only intercepts the attack key when it's bound to a mouse button, which is the default.

## Mod IDs (verified from the jars' `META-INF/neoforge.mods.toml`)
| Mod | modId | Jar version | Tag namespace |
|---|---|---|---|
| Spartan Weaponry Unofficial | `spartan_weaponry_unofficial` | 1.21.1-1.2.3 | `spartan_weaponry_unofficial` |
| Spartan Shields Unofficial | **`spartanshieldsunofficial`** (no underscores) | 1.0.0 | `spartanshieldsunofficial` |
| playerAnimator | `playeranimator` | 2.0.4+1.21.1-forge (MIT) | — |

- SS declares an *optional* dependency on `spartanweaponryunofficial`. That doesn't match SW's real id, so SS's own SW integration probably never activates. SS's README says it *requires* SW, but nothing enforces that. **Check that both load together in the dev client** (SS 1.0.0 is from 2026-02; SW 1.2.3 is from 2026-08).
- SW declares an optional dependency on `spartanshieldsunofficial` (correct id).

## Load smoke test — PASS (2026-10-06)
`./gradlew runClient` with Steel Clash + SW 1.2.3 + SS 1.0.0 + playerAnimator 2.0.4 reached the title screen without errors. The log contains `Steel Clash loaded. Spartan Weaponry: true, Spartan Shields: true`. The only warnings were vanilla dev-environment noise and SW auto-correcting `traits.damage_bonus.max_armor_value` in its own config. That's harmless.

## M2 findings (verified 2026-10-06)
- **Damage pipeline order** (`LivingEntity.hurt`, NeoForge 21.1.252): `LivingIncomingDamageEvent` fires **before** shield blocking, and `LivingShieldBlockEvent` is posted on every hit (with `getBlocked()` = vanilla's verdict). So: weapon parries and mob-attack interception cancel in `LivingIncomingDamageEvent`; shield cones and stamina live in `LivingShieldBlockEvent`. Cancelling incoming damage makes `hurt()` return false, which skips knockback and Spartan post-attack effects.
- **Vanilla shields** only count as blocking after 5 ticks of use (`isBlocking()`), cover 180° (based on `getYHeadRot()`), and handle projectiles. We only narrow the cone.
- **Telegraphing mob attacks without touching AI goals:** every vanilla melee mob ends in `Mob#doHurtTarget` → `hurt(mob_attack)`. Intercepting that damage for `#steelclash:fighters` mobs and starting a windup instead works for goal-based mobs (zombies, vindicators) *and* brain-based ones (piglins, hoglins). The vanilla attack cooldown (20 ticks) keeps them from spamming. Downside: a windup only starts once the mob is in vanilla melee range, so mob reach is kept short (hitbox edge + 1.8 blocks).
- **GameTest coordinates** are relative to the structure block. The template sits one block above it, so the arena floor is at relative y = 1 and entities stand at y = 2. Spawning at y = 1 suffocates them (`in_wall` damage), which silently makes "is hurt" assertions pass or fail for the wrong reason.
- `zombieAttacksAreTelegraphed` failed once (1 of 11 runs) before its diagnostics were improved: most likely the zombie's real AI didn't reach the dummy within 200 ticks. If it fails again, its message now reports the damage source, distance and target.

## M3 findings (2026-10-06)
- **Clank geometry:** checking walls along the full hit-detection reach (3+ blocks) made every overhead clank on low ceilings and GameTest's barrier box. Clank now checks only the physical weapon (2 blocks from the shoulder), and only between 15% and 85% of the release. Top faces (floors) never clank.
- **Heavy is decided by the client:** when the attack input is still held 3 ticks into the windup, the client converts its own prediction and sends `ActionPayload(HEAVY)`. The server applies it if the attack is still winding up. This avoids a correction snapshot on every heavy.
- **Combo eligibility is server-only** (only the server traces hits). It reaches the predicting client through non-authoritative snapshots that only *merge* windows (`CombatStateMachine.applyWindows`), without resetting the predicted phase.
- **Flinch** is applied in `LivingDamageEvent.Post` (real damage taken), so parried, blocked and cancelled hits never flinch.

## M4: animation approach (decided 2026-10-06)
The plan said "author animations in Blockbench, extract the arcs from them". Changed because a hand-authored arm animation has no guarantee its sword ends up where the server traces, and authoring needs visual tools Claude can't drive. Instead:
- **The arc stays the single source of truth for hits.** The weapon arm is *solved* from the arc every frame (`core/ArmAim`, unit-tested round trip). Vanilla holds a handheld item with its blade about 80° from the arm's axis; that was derived from `ItemInHandLayer` (`Rx(-90)·Ry(180)`) plus `item/handheld.json`'s `thirdperson_righthand` rotation `[0, -90, 55]`. Players: the arm aims along the arc and playerAnimator's `rightItem` rotation (applied in the hand frame by its `HeldItemMixin`) turns the weapon 80° to match; client config `weaponGripPitch`. **Verified in game (2026-10-06): the correct value is -80** (the derived +80 had the sign flipped; the cause, probably the rotation convention playerAnimator uses for `rightItem`, wasn't investigated). It's now the default. Mobs: there's no item hook, so the arm is pitched 80° below the aim instead (`aimArmForBlade`).
- **Everything else is data:** additive pose clips per archetype (`assets/steelclash/steelclash_animations/*.json`, parsed by `core/AnimationSet`, reloaded with F3+T). Torso, head, legs, off arm (or a solved two-handed grip), and elbow bends via playerAnimator `BEND`. Blockbench-authored clips can replace these later without touching the hit system.
- playerAnimator part names (from the jar): `head`, `torso` (the upper-body model part), `body` (the whole model, applied by the renderer; corrected 2026-10-06 from bytecode, this line previously had them swapped), `rightArm`, `leftArm`, `rightLeg`, `leftLeg`, `rightItem`, `leftItem`. ROTATION is radians; `BEND` is `Vec3f(axis, angle, _)`.
- **Mobs** are posed by one client mixin on `LivingEntityRenderer#render` after `setupAnim` (see docs/mixin-risk.md). It coexists with playerAnimator's own mixin on that class (verified in the debug log).
- **Sounds** are our own events (`assets/steelclash/sounds.json`) mapped to vanilla files, so resource packs can swap in real recordings.

## M5 findings (2026-10-06)
- **The brain is layered on vanilla AI, not a replacement.** Vanilla goals still path toward the target. `ai/ClashSpacingGoal` (priority 1, MOVE+LOOK flags) interrupts the vanilla melee goal only while `BrainState.wantsSpace` is set, holding range and strafing with `MoveControl#strafe`. For managed mobs the brain starts attacks itself, and the vanilla `doHurtTarget` hit is just cancelled. Brain-AI mobs (piglins, hoglins) have no `getTarget()`, so they keep the M2 behaviour.
- **Monster infighting:** with wide arcs, zombies cut each other, and vanilla hurt-by-target retaliation then split the horde across targets, which defeated per-target attack tokens. Fix: a hostile mob's swing only hurts another hostile mob if that's its target (`SwingTracer.isValidTarget`).
- **`GameTestHelper.spawnWithNoFreeWill` removes *all* goals**, including ones added in `EntityJoinLevelEvent`. Use `spawn` when a test needs AI goals.
- Circling bots broke an M2 test that pinned the defender's facing. Defenders in AI tests should turn to face their attacker.

## M6a findings (2026-10-06)
- **When to hand out gear:** `GameTestHelper#spawn` skips `finalizeSpawn` (so test mobs stay unarmed), while natural spawns, spawn eggs and `/summon` without NBT fire `FinalizeSpawnEvent` *before* vanilla's `finalizeSpawn` equipment roll. So `MobGear` marks the mob in `FinalizeSpawnEvent` and arms it in `EntityJoinLevelEvent` (not `loadedFromDisk`), after vanilla. That way it can respect gear vanilla or Spartan Weaponry already gave.
- **Bots must not lower a raised shield to attack:** a vanilla shield only blocks after 5 ticks raised, so a bot that swings as soon as it's free never actually blocks. The brain holds the shield until `shieldDownAt` (the incoming attack's release plus a margin).
- **Bot parry-cancel:** a bot mid-windup cancels into a parry or shield if the incoming hit lands *before* its own; otherwise it keeps swinging and trades.
- **Test isolation for AI behaviour:** a relentless attacker flinches its opponent out of every attack, so "does it defend?" tests must stop the defender from attacking (pin its brain cooldown). Diagnosing this took failure messages that report what happened (attack counts, phases during the opponent's windup, distance), which is now the pattern for AI tests.
- Minecraft's sideways movement input (`xxa`, `MoveControl#strafe` right) is positive toward the entity's **left**.

## M6b findings (2026-10-06)
- **Variant and side are chosen by whoever predicts the attack.** Players' clients pick them (random variant; side from turning direction, combo alternation, or the last side used) and send them in `AttackInputPayload`. The server wraps the variant to the profile's count and traces that exact arc. Mobs pick on the server. Queued attacks carry their choice too, so prediction and server never disagree.
- **Profiles now accept keyframe arcs** (`"keyframes": [[t, yaw, pitch, extension], ...]`) and per-attack `variants`; presets still work. Mirroring negates keyframe yaw (`ArcPath#mirrored`).
- **"Does every variant hit?" isn't enough to prove mirroring works:** a slash hits a target in front from either side. `mirroredSlashReachesTheRightSideLater` checks *when* a side target is reached, and fails if mirroring is ignored (verified by mutation).

## M6c-1 findings (2026-10-06)
- **Mob armour only counts after the mob ticks:** equipment attribute modifiers (armour, damage) are applied in the entity tick, so armour equipped in the same tick reads `getArmorValue() == 0`, for vanilla's reduction and our damage types alike. Tests must wait a few ticks after equipping (as must any code reading armour right after spawning gear).
- **GameTest zombies burn in daylight** (`onFire` damage) unless they wear a helmet, which can make "was it hurt?" checks pass for the wrong reason over multi-tick tests. Long-running tests should helmet their zombies *and* assert the damage source.
- Thrown weapons reuse vanilla `ThrowableItemProjectile` + `ThrownItemRenderer`. Damage is the item's own `ATTACK_DAMAGE` modifiers plus 1 (bare hand), ×1.2.

## M6c-2 findings (2026-10-06)
- **`Mob`'s constructor calls `registerGoals()` before the subclass constructor body runs.** A field assigned in the subclass constructor (like a soldier's rank) is still unset while goals are chosen; every soldier, archers included, got the melee goal. Derive such things from `getType()` instead. Caught by the `archerShoots` GameTest: the archer meleed the dummy and never drew its bow.
- **1.21.1 jigsaw structures require `use_expansion_hack`** (no default). Missing it fails registry loading for the whole world ("Unbound values in registry ... worldgen/structure"). The skill's worldgen validator checks references but not codecs, so a real server load (GameTests) is the actual test; `campWorldgenDataLoads` checks every worldgen entry is registered.
- The worldgen validator rejects inline `"processors": {"processors": []}` (which vanilla itself uses). `"processors": "minecraft:empty"` is equivalent and passes both.
- Entities saved in a jigsaw template are finalized on placement (`SinglePoolElement` sets `setFinalizeEntities(true)` → `finalizeSpawn(STRUCTURE)`), so camp soldiers equip themselves.
- Vanilla `PatrollingMonster#finalizeSpawn` puts the illager banner on patrol leaders; `Soldier` restores its helmet afterwards.
- Soldier skins are generated procedurally (PIL, 64×64 player layout) as placeholders; replace them in `assets/steelclash/textures/entity/soldier/`.

## M7 findings (2026-10-06)
- **A lagged player is a whole round trip behind the server, not half.** Their own actions run a one-way delay ahead of the server copy, and they see the world a one-way delay (plus interpolation) behind it. So the rewind for their swings is `ping + interpolation` (capped at `maxRewindMs`, default 300 ms). Parry grace on hits against them is `ping + 25 ms` (capped at `maxParryGraceMs`, default 250 ms).
- **Grace holds the hit rather than backdating the parry.** Vanilla damage can't be undone, so a hit on a lagged player who isn't defending is held (`LagCompensation`). It is delivered as soon as they parry, block with a shield, or wind up a matching counter, or when the grace runs out. Its numbers (damage multiplier, stamina damage, heavy) are fixed when the blade connects (`Combat.Hit`). Kicks aren't held: holding only lets a guard go up for the kick to break.
- **Remote entities' combat snapshots are deliberately not fast-forwarded.** They would cut the defender's reaction time, and the grace already covers the delay. Only the local player's own authoritative corrections catch up by the one-way delay, so a stagger ends locally when it ends on the server.
- Mock players and mobs have no connection, so GameTests force a latency with `LagCompensation.forceLatency`.
- `runClient` once died with a native access violation (0xC0000005) while opening FML's early window, before any mod code ran. It booted normally on retry: an environment or driver hiccup.

## M8 findings (2026-10-06)
- **Gradle configuration cache vs. `jar` customisation:** a `rename { "${it}_${mod_id}" }` closure fails at execution ("> mod_id") because project properties aren't readable then. Resolve the value into a local variable at configuration time.
- **Optional mods really are optional:** `-PnoCompat` drops Spartan Weaponry/Shields from `localRuntime`. All GameTests pass without them, and the log has no data errors (every optional tag entry is `"required": false`). CI runs both variants.
- GameTest classes ship in the jar (they live in `main`). That's harmless: they only register when NeoForge's GameTest system is enabled.
- NeoForge's `ConfigurationScreen` shows raw keys unless `<modid>.configuration.<key>` translations exist. Generate them from the spec; the `.comment(...)` texts become the hover tooltips.

- **GameTests shared `run/config` with the dev client.** Toggling `lagCompensation` off in game (as the M7 checklist suggests) made the lag GameTests fail. `runGameTestServer` now uses its own `run-gametest/` directory with default configs.
- **playerAnimator's held item rotation order** (`HeldItemMixin`, bytecode): `mulPose(ZP z)`, then `YP y`, then `XP x`, injected just before `renderItem`, i.e. after vanilla's hand rotation (-90° about X). So X (`weaponGripPitch`) acts on the item first, and the arm's length lies on Z: blade twist rolls about Z. The sign still needs an in-game look (`bladeTwist = -1` flips it).

## PAL: moved from playerAnimator to Player Animation Library (2026-10-06)
Verified from the PAL 1.1.6 source (`PlayerAnimationLibrary-1.21.1`) and the published jar.
- **Coordinates:** `com.zigythebird.playeranim:PlayerAnimationLibNeo:1.1.6+mc.1.21.1` from `https://repo.redlance.org/public`; mod id `player_animation_library`. Depend on it **non-transitively**. The jar already contains the core classes and bundles mochafloats and javassist (jar-in-jar). Its published POM asks for gson 2.11, which NeoForge's strict gson 2.10 rejects ("Cannot find a version of gson…" when resolving the runtime classpath).
- **Registration:** `PlayerAnimationRegisterEvent` on the game bus, once per client player: `getAnimManager().addAnimLayer(1000, layer)`.
- **Bones:** `head`, `torso` (upper-body part), `right_arm`, `left_arm`, `right_leg`, `left_leg` are seeded from vanilla's pose (radians, `copyVanillaPart`), exactly like playerAnimator's parts, so part rotations carry over unchanged. `body` is the whole model, and `right_item`/`left_item` start at zero.
- **Whole body:** playerAnimator applied `body` as Z·Y·X about 0.7 blocks up. PAL negates X and Y, then applies Z·Y·X about 0.75 blocks up. Our clips' body X and Y are fed in negated.
- **Held item:** playerAnimator applied `ZP(z)·YP(y)·XP(x)`. PAL applies `ZP(-rotY)·YP(-rotZ)·XP(-rotX)`. So the hand-frame grip pitch and twist go in as `rotX = -x`, `rotY = -z`, `rotZ = -y` (`ProceduralSwingAnimation#itemRotation`). This should look identical; it needs a visual check.
- **Bends are not drawn on 1.21.1, and never were.** PAL stores `bend` but rendering it needs the separate bendable-cuboids mod (Minecraft 26.x only). The pose-sheet comparison shows playerAnimator 2.0.4 didn't draw our clips' bends either (it needs bendy-lib, which isn't bundled here). So nothing was lost; arms have always been straight.
- **Verified by pixel comparison (pose sheet, below):** the same 87 frozen poses photographed on the old playerAnimator build and the new PAL build, with identical settings. Mean pixel difference per attack: parry 0.00, slash 0.06, slash mirrored 0.03, stab 0.24, overhead 0.93. The only visible difference is a slight off-arm shift in first-person overhead windups. Third person, the held-item axis conversion and the whole-body lean are identical.

## Pose sheet: checking animations without a player (2026-10-06)
`./gradlew runClient -PclientMemory=1536M -PquickPlay="New World" -PposeSheet=slash,slash_mirrored,overhead,stab,parry` loads straight into the dev world. It builds a stone platform in the sky at noon (needs cheats), gives the player a sword (`-PposeSheetItem=<id>` for another weapon), and freezes the local player client-side at fixed points of each attack. Each pose is photographed from behind, in front and in first person with Minecraft's own screenshot code (`run/screenshots/pose_*.png`), and then the game quits. About 87 shots take 20 s.
- The animation partial tick is pinned to 0 while it runs (`ClientFeel.animationPartialTick`), so poses are exact.
- `fov` in `options.txt` changes the framing: compare runs only with the same `options.txt`.
- `-PclientMemory` caps the heap; this machine runs low on RAM with a browser open.
- `AnimationData.getPartialTick()` replaces playerAnimator's `tickDelta` argument. `getFirstPersonMode()` and `getFirstPersonConfiguration()` take no arguments; `FirstPersonConfiguration(showRightArm, showLeftArm, showRightItem, showLeftItem)` is unchanged.
- The dedicated GameTest server starts cleanly with PAL present (65/65, with and without the Spartan mods). The layer class is client-only (`@EventBusSubscriber(value = Dist.CLIENT)`).
- `-PposeSheetDebug` also draws the traced blade (`/steelclash_debug`) on the same animation clock as the model, so each shot shows whether the drawn weapon lies along the arc the server traces. Before the clock was shared, the line ran up to a tick ahead of the model (about 20% of a sword slash's release).

## Weapon rig: body motion under an arc-locked blade (step C, 2026-10-06)
The weapon arm used to point straight along the blade, so arm and sword formed one straight line, like a spear, and the whole-body twist from the clips swung the arc off the traced one. `core/WeaponRig` now solves both (unit-tested in `WeaponRigTest`, including 2000 random poses):
- **The blade orientation is fixed first:** it's what the old straight arm gave, so the edge roll and grip are unchanged.
- **The arm is free:** the hand is drawn 45% of the way toward a point in front of the chest (`CombatPose.WRIST_RELAX`), so the wrist shows a cocked angle. The arm also counters the whole-body rotation, and the held item is rotated in the hand to bring the blade back onto the arc.
- **Whole-body rotation in model space** is `Rz(z)·Ry(-y)·Rx(-x)` of the clip's `body` angles: the renderer applies it in entity space, before the model flips X and Y. A pose sheet with `-PposeSheetDebug` confirms the blade runs parallel to the traced line in every slash, overhead and stab shot. The hilt sits a little in front of the trace's pivot, because the hand is drawn in.
- **Legs take back half the body's turn** (`ProceduralSwingAnimation.HIP_LAG`), so the shoulders twist over the hips instead of the fighter pivoting on the spot.
- **Sword slash clips re-authored** (step E later gave every archetype the same treatment): a 45° wind-up turn with weight on the back foot, square and leaning in mid-cut with a front-foot step, and a 50° follow-through that settles before recovering. Other archetypes keep their clips but get the rig (wrist and arc lock) for free.
- **Mobs are unchanged** (`weaponArmForFixedItem`); their model hook can't rotate the held item.

## First person and the two-hand grip (step D, 2026-10-07)
Checked on pose sheets (slash, mirrored slash, overhead, stab, parry; all three views).
- **Two-hand grip:** the off arm used to copy the weapon arm turned 28° inward, so the off hand floated beside the weapon instead of holding it. In first person it was a loose slab in the lower left. Now the off hand reaches for the grip 2.5 px behind the weapon hand, toward the pommel (`WeaponRig.reach`). The off shoulder slides up to 4 px across the chest when the arm alone is too short, as a real shoulder does. Mobs use the same reach (`CombatPose.gripArm`).
- **First-person arm offset:** in the first-person pass only, both arms sit 4 px forward and 1.5 px down (`ProceduralSwingAnimation.FIRST_PERSON_FORWARD`/`_DOWN`), the usual first-person trick. Before this, a raised overhead or parry put both arms across the whole lower half of the screen. Now the weapon reads clearly in every wind-up. The blade's direction is unchanged.
- **Tried and dropped:** less wrist bend in first person (0.15). It brought the raised arms right up to the eye (overhead, parry) and pushed slash wind-ups off screen.
- **Still as designed:** the backhand (mirrored) wind-up shows the blade large on the left, because it passes close to the head. The slash follow-through ends low and centre-left, which is where the traced blade really is from eye height.

## Every archetype's body motion (step E, 2026-10-07)
- **Before:** every archetype except the sword had a scaled copy of the default clip set.
- **Now:** slash, overhead and stab clips (plus heavy wind-ups) for sword, dagger, rapier, axe, blunt, two_handed, polearm, spear and staff all share one structure:
  - wind-up (turned or reared back, weight on the back foot);
  - mid-cut (square, leaning in, front foot stepping);
  - follow-through (carried round, or over the front foot);
  - a settle at 30% of recovery.

  Per-archetype styles vary twist, lean, step, overhead rear-back and chop, the thrust's side-on turn and lunge, and the off-arm pose. Examples: daggers are compact and leaning in; axes and blunt weapons swing wide and chop deep; rapiers turn side-on for long lunges with the off arm hanging back; spears lunge far. Kick, parry, stagger, special and throw clips are unchanged. `default` and `claw` (mobs, unmapped items) are unchanged.
- **Grip width:** `grip_gap` in an animation file sets the pixels between the hands on a two-handed grip (default 2.5). Staff 8, polearm 7, spear 6, two_handed 3.5, so long weapons are held with the hands spread along the shaft (`AnimationSet.gripGap`, checked by `AnimationFilesTest`).
- ~~Mobs take half the body turn~~: replaced the same day, see "Mob whole-body rotation" below.
- **Pose sheet:** it now takes several items (`-PposeSheetItem=a,b,c`, file names prefixed with the item's path) and warns about unknown ids instead of quietly using a sword. Spartan Weaponry's namespace is `spartan_weaponry_unofficial`.
- **Checked on pose sheets:** iron axe, mace, trident, and the Spartan dagger, rapier, greatsword, halberd, quarterstaff and spear, for slash, overhead and stab, in all three views.

## Chivalry 2 mechanics from the report (step F, 2026-10-07)
Sourced from `C:\dev\Chivalry_2_Combat_System_Maximum_Detail_Report_v2-1.md`. Its evidence tags are kept here: [O] official, [D] data, [W] wiki, [S] supplied/community.
- **Held block** (§4.1 [S]): the player's weapon guard stays up while held (`blockMode = HELD`, the default) and drains `heldBlockDrainPerSecond` = 4 [S] stamina, with no regeneration while held. Mobs keep timed parries: their brain never "lets go", so a held guard would make them turtle forever. `TIMED` restores the old 12-tick parry.
- **Attacking from the guard:** any attack can now start from a raised guard (`canStartAttack`). After a catch it's a riposte; before one it's a counter attempt that drops the guard. Before this, attacking out of a guard that had caught nothing was refused, which made counters from a held block impossible.
- **Active parry** (§4.3, §4.7 [O] exists; durations [S]): a riposte carries 9 ticks (0.45 s, report 0.4–0.5 s) and a counter 15 ticks (0.75 s), +2 ticks per caught hit (report +0.1 s [S/T, inconsistent]). Frontal hits inside the weapon's parry cone are parried without stopping the attack. The defender pays the block stamina but is never disarmed by it (later patches prevent disarm during active parry [O]).
- **Parry forgiveness** (§4.12 [O], 0.10 s in Hotfix 2.2.3): an attack started from the guard, hit within 2 ticks of starting, drops back into the guard (ignoring the parry cooldown) and blocks. A correct counter still takes precedence.
- **Dodge** (§7.3 [O], 12 stamina [O 2.2]): a burst in the movement direction (backwards with no input), 20-tick cooldown, ground only. It can abandon a windup or a guard, but not a release, recovery or stagger. Player movement is client-side, so the client checks the rules and moves itself; the server re-checks them to charge stamina and cancel the attack or guard. Dash-cancel subtleties (50% of dash before parry [O 2.11], slower jab after dodge [O 2.9]) are not modelled.
- **Jab** (§5.2: [W] 10 damage, i.e. about a tenth of a health bar; [O] not cancelable): a new `AttackType.JAB`, appended last so earlier ids are unchanged. 5/2/8 ticks, a short thrust (~2.2 blocks), a quarter of the weapon's damage, 6 stamina, parryable. It can't be feinted, made heavy or parry-cancelled, and it interrupts through the normal flinch rule. Bots answer a close-range heavy with a jab at 60% of their counter chance. "Jabs block jabs" [O] is not modelled.
- **Health regeneration** (§18.1 [W]/[O]): 6 s delay (was 5 s), a cap of 40% of max health (`healthRegenCap`), and attacking, guarding or shield-blocking now pause it like taking damage does ([O 2.2]: regen stops on engaging).
- **Not done, on purpose:**
  - The fixed 350 ms *Holding* phase (§1.2 [D]) would add latency to every attack. The report itself calls it partly a measurement convention, and our windups are already tuned to feel right.
  - *Thwack* recovery (§1.8) is not done either.
  - Combo-replaces-recovery already exists.
  - Variable counter windows were a possible follow-up; they're done now (part 2, below).

### Part 2 (2026-10-07)
- **Kicks** (§2.8, §5.1 [O]): a kick no longer interrupts a target that is winding up or releasing an attack. It still hurts and knocks back, but there's no stagger. The kick's damage is also exempt from the general flinch rule. Kicks block kicks [O]: a kick landing on someone mid-kick does nothing.
- **Jabs block jabs** [O]: a jab hitting someone whose own jab is winding up or out is turned aside. The jabber reels back for half a parried stagger; the defender's jab carries on.
- **Feint into a kick or jab** (§2.4 [O]): the kick or jab key during a weapon windup drops the attack, paying the feint's stamina, and starts the kick or jab. Predicted on the client like a morph.
- **Variable counter windows** (§4.4 [O]): `Guard.counterWindow` scales `counterWindowTicks` by the attacker's actual windup relative to the sword's (10 ticks), clamped to 70–130%. A dagger slash gets 5 ticks, a sword 7, a greatsword 9.
- **Dodge rules:** no guard during the first half of the 6-tick dash (2.11 [O]); a jab within 10 ticks of a dodge winds up 3 ticks slower (2.9 [O]); no dodge or jab for 30 ticks after being disarmed (2.5 [O]). The tick values are ours: the patches give no numbers.
- **Queued retaliation** (§4.9 [O]): attack input during a stagger is buffered and starts the moment the stagger ends, on both the server and the client.
- **Stamina** (§14 [O]/[W]): a jump costs 12 while fighting (within 100 ticks of attacking, guarding or being hit, so ordinary jumping stays free), crouching pauses regeneration, and stamina damage against a guard is +10% for chop and +25% for blunt (2.4.2 [O]).
- **Fake players:** server-to-client packets now go only to connections that negotiated our channels (`ModNetwork.sendTo`). A GameTest with a mock server player crashed the server when the mod synced stamina to it, and other mods' fake players (machines) would hit the same crash.
- **Still not done:**
  - *Holding*, *Thwack* (no numbers in the report).
  - Counter-feints already work through the existing morph.
  - Spear/lance input families.
  - Katar exceptions.
  - No dodging during late counter windups (2.6).

## Mob whole-body rotation (2026-10-07)
A playtester saw zombies with "a weird skin". A screenshot showed the cause: during an attack the zombie's torso (the blue shirt) swung far away from its own head, arms and legs.
- **Cause:** for players, a clip's `body` channel is the whole-model rotation (Player Animation Library rotates the pose stack). For mobs, `MobCombatPoses` rotated the `body` model part instead, which is only the torso cuboid, pivoting at the neck. Pitch swung its bottom out and yaw twisted it off the shoulders. Step E's bigger twists made it obvious; it was there before at smaller angles.
- **Fix:** the mixin now also injects at the end of `LivingEntityRenderer#setupRotations` and applies the clip's body rotation to the pose stack exactly as PAL does for players (Z, Y, X about a point 0.75 blocks up). The weapon arm counters it like the player's rig does (`CombatPose.mobWeaponArm`, `bodyRotation`ᵀ times the arm), so the blade stays on the traced arc. The two-handed grip uses the blade direction in the rotated body frame. The legs take back half the turn (hip lag, as for players). The torso part only gets the clip's `torso` channel.
- **Checked** with a new pose-sheet mode, `-PposeSheetMob=minecraft:husk` (a husk, because zombies burn at noon on the stage). It summons the mob without AI three blocks in front of the player, holding the item, clears last run's model first, and photographs it from the front and the side in first person. Slash, overhead and stab, wind-up to follow-through: the husk stays in one piece, leaning and twisting as a whole.
- **Same day:** fighters are kept flagged aggressive while fighting (`MobCombat.keepAggressive`). Vindicators otherwise cross their arms, hiding the real arms and the axe, so their swings were invisible. As a fallback, illagers show their real arms whenever a combat pose is active.

## Sub-tick combat timing (architecture plan §5, 2026-10-07)
Attack phases are now timed in integer **microseconds** and resolved **inside** each 50 ms server tick, as the architecture report recommends. Before this, every phase was a whole number of ticks, so different weapon timings collapsed together: 330, 350 and 370 ms all became 7 ticks.
- **`AttackTimings`** holds `windupUs`/`releaseUs`/`recoveryUs`, from 1 ms up to 10 s. `ofTicks`/`ofMillis` build it. The tick accessors (`windup()` and friends, rounded) remain for code that thinks in ticks: AI reaction times and HUDs.
- **`CombatStateMachine.tick()`** spends 50 000 µs per tick, crossing as many phase boundaries as fall inside it. A windup ending 20 ms into a tick spends the remaining 30 ms in the release. The tick's `Sweep` covers exactly the release progress inside the tick, including a partial first or last slice, or the whole release when it is shorter than a tick. The blade tracer already swept intervals (`from`..`to`), so contact is sub-tick with no second game loop. `phaseTick()` rounds down, `phaseDuration()`/`ticksLeftInPhase()` round up.
- **Unchanged:** guards, staggers, cooldowns, riposte and active-parry windows are still counted in ticks. Their boundaries fall on tick edges, so they behave as before. **Timings in whole ticks behave exactly as before** (unit-tested), which is why every existing test still passes unchanged.
- **Now exact instead of rounded to ticks:** attack-speed scaling (Spartan Weaponry's lighter or heavier variants now differ by milliseconds), heavy windups, riposte windups, the slower jab after a dodge, and the counter window's weapon-speed scaling.
- **Profiles:** `windup_ms`, `release_ms` and `recovery_ms` sit beside the tick fields. Milliseconds win, the two can be mixed, and an attack missing any phase is rejected (`profilesTakeMillisecondTimings` GameTest). The built-in profiles keep their tick values, so nothing about current weapons changes until someone retimes them in milliseconds.
- **Network:** `CombatStatePayload` carries the elapsed time, duration and timings in microseconds, and the protocol version went from 5 to 6.
- **Tests:** six new unit tests:
  - whole-tick timings behave exactly as before;
  - a release starts mid-tick;
  - 350 vs 370 ms stay distinct;
  - a release shorter than a tick is swept whole;
  - the sweeps cover a release exactly once for 20–333 ms releases;
  - progress interpolates in microseconds.

  In a mutation check, all four mutants that changed behaviour were caught. A fifth was equivalent, and the redundant code it exposed was removed.
- **Not done:** sub-tick *input* timestamps (§7.4). Inputs still take effect on the tick they arrive.

## Combat benchmark and baseline (architecture plan §31/§41, 2026-10-07)
`./gradlew runGameTestServer -Pbench` runs fixed scenes with `combat/CombatProfiler` switched on. Reports go to `run-gametest/steelclash-bench/<scene>.txt`. Without `-Pbench` the scenes pass at once, so ordinary test runs don't slow down.
- **Profiler:** off by default (one field read per hook). It times server-side combat in exclusive sections (AI, state, broad, narrow, world, resolve, lag history, sync), counts live blades, candidates, contacts and packets, samples bytes allocated per section (`ThreadMXBean`), and keeps per-tick totals for percentiles.
- **Scenes:**
  - A–C: 2, 20 and 50 training dummies in facing pairs, attacking and parrying nonstop with no AI.
  - D: 8 armed bots on one player.
  - E: 40 bots.
  - F: 150 bots, a stress scene beyond the plan.

  Health and stamina are topped up so nobody dies or is disarmed. Each scene runs a 200-tick warm-up (1200 for the first, which also warms up the JIT) and then measures 600 ticks.
- **Gotchas found while building it:**
  - GameTest batches overlapped, and a scene in an earlier chunk deadlocked waiting for its turn. The scenes now share one batch and take turns, starting after the ordinary tests.
  - GameTest can't take new per-tick callbacks while it runs them, so each scene has one callback driving its whole run.
  - Training dummies count as monsters, which only cut the target they're fighting, so each dummy targets its partner.
- **Baseline** (i5-12500H, Java 21; whole-scene average and worst tick; the plan's budget is 2.5 ms per tick):

  | Scene | ms/tick | p99 | max | KB/tick | Biggest part |
  |---|---|---|---|---|---|
  | A: 2 dummies | 0.06 | 0.75 | 1.34 | 3 | resolve (cold path; tiny sample) |
  | B: 20 dummies | 0.17 | 0.91 | 1.62 | 16 | resolve, narrow |
  | C: 50 dummies | 0.11 | 0.48 | 1.21 | 25 | resolve, narrow (13 KB/tick there) |
  | D: 1 vs 8 bots | 0.11 | 0.46 | 0.87 | 10 | AI |
  | E: 1 vs 40 bots | 0.21 | 0.64 | 0.92 | 32 | AI (0.16 ms, 27 KB) |
  | F: 1 vs 150 bots | 0.37 | 0.84 | 1.03 | 70 | AI (0.33 ms, 61 KB) |

- **Verdict, by the plan's own rules:** every scene stays far inside the budget. Hit geometry (broad + narrow + world) never reaches 0.05 ms per tick, against the plan's 1–2 ms threshold for even considering Rust. **No native code; stay in Java.** The one cost that grows with crowd size is the bot brain, about 2 µs and about 0.4 KB of garbage per bot per tick, mostly its per-tick scan for incoming attacks. That's the first place to optimize if hordes grow much bigger. Bots mostly circle, because attack tokens let 2–3 swing at once, so blades stay few even at 150.
- **Not measured:** packet encoding and sending (no real clients in GameTests), client-side rendering cost, and projectiles (plan scene E).

## Cleave and thwack (architecture plan §18, 2026-10-07)
- **Rule** (`core/ContactPolicy`): each weapon attack (slash, overhead, stab) is `cleave`, `thwack` or `cleave_on_kill`. Unset, blunt attacks are `cleave_on_kill` (Chivalry 2 since Fight Knight) and everything else cleaves. Heavies always cleave. Kicks, jabs, throws and specials are untouched.
- **Thwack** (`CombatStateMachine.thwack`): the release ends at the contact and the thwack recovery replaces the normal one, timed from the moment of contact. It's sub-tick, so if the contact was 20 ms into a tick, the remaining 30 ms already count. It works even when the release ran out later in the same tick. The time comes from `thwack_ms` (scaled for attack speed like the other phases), or by default the normal recovery, so a thwack saves the rest of the release. Bodies behind the first are spared, the clank check is skipped for a thwacked swing, and a combo can follow straight away.
- **Tracer:** contacts now carry the release progress where the blade met each body (`SwingTracer.Contact`). A lagged defender whose hit is held for their parry still stops the blade.
- **Sync:** the thwack is sent authoritatively, so the attacker's own predicted client snaps into it. The state packet carries a thwack flag and the contact point (protocol 7). The client recovers the blade and body pose from the contact point instead of the end of the arc.
- **Tests:** unit tests `ContactPolicyTest` and the thwack cases in `CombatStateMachineTest`; GameTests `bluntLightStopsInTheFirstBody`, `bluntLightSparesABodyMetInTheSameInstant`, `bluntLightCleavesOnAKill`, `bluntHeavyCleaves`, `thwackSkipsTheRestOfTheRelease` and `profilesTakeContactAndThwackTimings`. Mutation check: 13 of 13 mutants caught (the same-instant test was added after removing the hitstop `break` survived: bodies met in different ticks never reached it).
- **Not done:** stabs thwacking on teammates (Chivalry 2 2.9/2.10; this mod has no teams yet) and constructibles. Built-in blunt profiles got their `thwack_ms` in the Chivalry 2 retiming below.

## Counter-feint (architecture plan §13.1, 2026-10-07)
- **What was already there:** a counter is checked at impact against the defender's windup age, and a morph restarts the windup. So re-matching a feinted attack shortly before it lands already countered. Three things were missing: a second switch after the defender had already morphed, switching to the other side of the same attack (Chivalry 2's alternate counter), and bots following feints.
- **Rule** (`CombatStateMachine.counterFeint`): one extra windup switch per attack, allowed after a morph and also to the same attack from the other side. Like a morph, it restarts the windup and costs the morph stamina. `Combat.morph` tries a plain morph first, and falls back to a counter-feint only if an attack of the new type is winding up at the fighter (`Combat.isIncoming`: within 5 blocks, facing them within 120°). That's the plan's "counter-feint window". It works on both sides of the connection, because clients know other fighters' phases from sync.
- **Client:** the other-side switch only comes from an explicit side key (two-slash-key scheme). A second press of the single slash key could otherwise restart the windup by accident.
- **Bots** (`ClashBrain.defend`): a bot that started a counter remembers the attacker and its own attack serial. When that attacker's windup changes type, the bot morphs its counter to match once it has seen the new windup for its reaction time.
- **Tests:** unit tests for the counter-feint cases in `CombatStateMachineTest`. GameTests `counterFeintFollowsAFeintedAttack` (the countered stab staggers the attacker), `counterFeintToTheOtherSide` and `botCounterFeints`. Mock players aren't in the level, so `isIncoming` can't see them; the attackers in these tests are training dummies. Mutation check: 10 of 10 mutants caught.

## Chivalry 2 timings (2026-10-07)
- **Source:** the `chivalry2-weapons` data package (v1.0.6), which polehammer.net builds on. Its damage and speed values are read from the game's `AbilitiesOverride.json`. Only the numbers are used; no data files are bundled. The retiming script is not in the repo; the mapping below is enough to redo it.
- **Phase model (polehammer.net's definitions):** Holding (a fixed 350 ms chamber) → Windup → Release → Recovery. A riposte replaces Windup with Riposte. A combo replaces the previous attack's Recovery with Holding + Combo, then plays the Windup. Thwack replaces Recovery after a hitstop. In this mod's single windup: `windup_ms` = holding + windup, `riposte_ms` = holding + riposte, `combo_ms` = holding + combo + windup, `release_ms` and `recovery_ms` as given, `thwack_ms` = thwack (blunt profiles only). Light attacks only. A heavy adds a fixed time in Chivalry 2 (almost always +250 ms windup, +100 to +150 ms recovery). That became `heavy.windup_extra_ms` / `recovery_extra_ms` (each profile's mean over slash, overhead and stab), and it applies on top of riposte and combo windups too, as in the game.
- **Reference weapons:** dagger → Dagger, sword → Sword, two_handed → Greatsword, axe → Axe, blunt → Mace, polearm → Halberd, spear → Spear, rapier → Rapier, staff → Quarterstaff. Damage and stamina damage keep each profile's slash value; overheads and stabs take the game's ratios to the slash. Heavy damage multipliers are the game's mean heavy/light ratio. The mob profiles (beast, claw, heavy_beast), specials, guards, stamina costs and `riposte_windup_mult` (now only a fallback) are unchanged.
- **Rule change:** any slash, overhead or stab whose release ends unblocked can be comboed, a whiff included (Chivalry 2 wiki: "Inputting an attack immediately after a first, unblocked attack ends will result in a combo… Blocked attacks cannot be comboed"). Before, only a landed hit allowed a combo. A blocked or parried attack is staggered and never reaches recovery, so it can't combo. Kicks, jabs, throws and specials don't combo.
- **What changed in feel:** releases are about twice as long as before (300–600 ms), so swings sweep visibly and leave room for accels and drags. Recoveries are longer (700–1100 ms), which makes whiffs punishable unless you combo. One-handed ripostes are no faster than a normal attack; two-handed ones are (greatsword 600 vs 675 ms). A mace thwack (1050 ms from contact) takes about as long as finishing the swing; its gain is the combo right after it.
- **Tests:** unit tests `comboSkipsRecoveryAfterAnUnblockedAttack` and `kicksAndBlockedAttacksDontCombo`. GameTests `whiffCanBeComboed`, `whiffComboUsesComboTiming`, `riposteAndHeavyUseChivalryTimings` (with a wielder faster than the reference, so every scaled time is checked), `profilesTakeComboRiposteAndHeavyTimings`, and the reworked `thwackSkipsTheRestOfTheRelease` (the thwack recovery is `thwack_ms` from the contact) and `riposteHasAShorterWindup` (now with a spear). Mutation check: 11 of 11 mutants caught.
- **Not done:** heavy releases (+0 to +50 ms in the game) keep the light release; per-attack heavy damage uses one multiplier per profile; Chivalry 2's per-attack turn limits, ranges and sprint attacks aren't mapped.

## Archer sidearms (2026-10-07)
- **Rule** (`combat/Sidearms`): mobs in `#steelclash:sidearm_users` (skeleton, stray, bogged) that spawn for real holding a bow or crossbow get a sidearm from `#steelclash:mob_sidearms/tier_N`, rolled like other mob gear by difficulty (`sidearmChance`, default 1). Chivalry 2 archers all carry one. The item not in hand lives in the `steelclash:sidearm` attachment, saved with the mob.
- **Switching:** a target within 4 blocks → bow away, sidearm out; target past 9 blocks or gone → back to the bow. Never mid-attack, and 1.5 s between switches so a target at the edge doesn't cause juggling. A half-drawn bow is let down, not fired. `AbstractSkeleton.setItemSlot` re-picks the bow or melee goal by itself, and the bot brain fights with whatever profiled weapon is in hand.
- **Vanilla:** has no dagger, so the tier tags list only Spartan Weaponry daggers (`required: false`); without it, skeletons keep just the bow. The mod's own soldier archers aren't sidearm users yet (their bow goal is fixed at creation).
- **Loot:** killed with the sidearm out, a skeleton drops the sidearm at the usual equipment chance; the stowed bow isn't dropped.
- **Tests:** GameTests `spawnedSkeletonsCarryASidearm` (dagger with Spartan Weaponry, none without), `skeletonDrawsItsSidearmUpClose` (distances, cooldown, no switch mid-swing) and `skeletonSwitchesOnItsOwn` (the tick hook). Mutation check: 10 of 10 mutants caught.

## Projectile defence and headshots (architecture plan §22, report §19, 2026-10-07)
- **Three outcomes** (`combat/RangedDefense`), decided in `LivingIncomingDamageEvent` for any damage whose direct entity is a projectile (arrows, bolts, tridents, Steel Clash thrown weapons):
  1. *Counter:* the defender's slash, overhead or stab started at most `projectileCounterMillis` (250 ms, the Fight Knight value) ago, and the projectile comes from inside the weapon's guard cone → the damage is cancelled, so vanilla bounces the arrow back. Half the projectile stamina cost. The attack carries on. This is not the melee counter rule: any weapon attack counters any projectile.
  2. *Weapon block:* a held weapon guard (PARRY phase) facing it → damage × (1 − `projectileWeaponBlockReduction`, 0.3; update 2.4.2). It costs `projectileStaminaDamage`, capped at what's left, and never disarms, staggers or opens a riposte.
  3. *Shield:* untouched. A raised shield (`isBlocking`) skips both of the above, and vanilla's block plus `Defense.onShieldBlock` handle it.
- **Front:** judged from where the projectile is flying from (4 blocks back along its motion), since at impact it's already at the body.
- **Headshots:** the projectile's path this tick is clipped against the target's box (inflated by 0.3, as projectiles test it). A hit point at or above "eyes minus 0.25 blocks per 1.8 of height" is a headshot: × `headshotMultiplier` (1.25, the Archer perk value). Applies to every living target, so players' arrows headshot mobs too. The shooting player gets a `HEADSHOT` feedback (vanilla's arrow ding, pitched up). Protocol 8.
- **Draws:** any real damage while using a bow or crossbow stops the use (`interruptDrawOnHit`). Players get a `DRAW_INTERRUPTED` feedback, so the local client drops the draw too; holding the key starts a new one.
- **Tests:** GameTests `attackStartedJustBeforeDeflectsAnArrow` (front, behind, too late), `weaponGuardBluntsAnArrowButNeverBreaks` (−30%, one stamina left, back unguarded), `headshotsHitHarder` (villagers: no armour), `gettingHurtDropsABowDraw`. They hurt the target with an arrow damage source directly, so they don't depend on arrow flight. Mutation check: 9 of 9 mutants caught.
- **Not done:** bots reacting to incoming arrows (they only benefit when they happen to be guarding or winding up), shields on the back, crossbow reload states beyond the draw interrupt.

