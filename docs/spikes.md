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
- **Optimization pass (2026-10-07, after 0.3.0-beta):** an outside patch, reviewed and applied with tidied imports. It doesn't change behaviour:
  - the bot's threat scan stops at the first windup in its cone, since `defend` only ever answers the first;
  - arc paths, and their mirrored copies, are cached per fighter by `ArcSpec` identity, so a datapack reload invalidates them;
  - the swing tracer builds each candidate's hitbox and centre once per trace instead of once per sub-step, and skips the entity query when the target limit is already reached;
  - `BotSkill` reuses the preset when an override changes nothing;
  - `PositionHistory` no longer allocates while searching.

  It adds tests for the threat limit, cache invalidation (variant, side, reloaded spec), unchanged presets and out-of-order history. Same machine, same code, run back to back with and without it:

  | Scene | ms/tick before → after | p99 before → after | KB/tick before → after |
  |---|---|---|---|
  | B: 20 dummies | 0.149 → 0.071 | 0.61 → 0.32 | 18.2 → 16.7 |
  | C: 50 dummies | 0.093 → 0.061 | 0.30 → 0.27 | 20.1 → 17.4 |
  | D: 1 vs 8 bots | 0.109 → 0.056 | 0.34 → 0.22 | 11.0 → 8.4 |
  | E: 1 vs 40 bots | 0.255 → 0.163 | 0.61 → 0.32 | 36.2 → 27.7 |
  | F: 1 vs 150 bots | 0.455 → 0.266 | 0.96 → 0.43 | 72.1 → 55.5 |

  The bot AI, the biggest cost, dropped by about 44% at 150 bots. One tick in F's run peaked at 3.8 ms (before: 1.1). That's a single outlier with p99 halved, consistent with a GC or JIT pause, not a regression.

  Of the review's four further candidates, two were done the same day:
  - **Mob pose once per frame.** Vanilla turns the body (`setupRotations`) before it poses the model (`setupAnim`), and both hooks computed the full `CombatPose`. `MobCombatPoses.applyBodyRotation` now hands its pose to `apply` in the same render call, keyed by entity and partial tick, with a fresh computation as the fallback. Checked with a temporary comparison during a husk pose sheet: 4,600 handed-on poses, all equal to a fresh one, and none needed recomputing. Screenshots alone can't show it: particles differ between runs.
  - **Broadcast check without a stream.** `sendToTrackingAndSelf` checks for fake players with a plain loop.

  Skipped: the telegraph labels are opt-in and cost microseconds. Per-entity position history is about 0.8 KB per entity, and recording only some entities risks missing the history a lagged swing needs.

### Follow-up optimization patch (2026-10-07, based on 1cfe922)

- `Blade.intersectsBox` now uses scalar slab data, and the tracer passes AABB bounds directly. This removes four temporary arrays and two temporary bounds vectors per candidate/sub-step at source level; actual allocation savings depend on the JVM's escape analysis.
- `SwingTracer` honors `environmentClank` before tracing clanks. Previously the caller suppressed the stagger but the tracer still clipped blocks and could return early. Visibility checks against targets remain enabled; empty traces skip blade sampling when clanks are off.
- `PoseClip` can sample, scale and mirror in one pass, avoiding intermediate maps/arrays for mirrored poses and fallback heavy windups. Clips and returned samples keep their existing ownership.
- The optional enemy telegraph uses a nearby living-entity query, reuses its finite set of labels and only flushes buffers after drawing. This is lower priority, and has no effect while the option is off.
- Applied 2026-10-08 together with the second follow-up and the bug audit below; build, JUnit and all GameTests pass (see the audit's validation).

### Second follow-up optimization patch (2026-10-08)

- Attack tokens now have an attacker-to-target reverse index, so `releaseAll` visits only that attacker's held targets. `CombatEvents` calls brain cleanup even when `manages` is false, releases old tokens on a target change, and removes target/attacker entries on server-side `EntityLeaveLevelEvent`. This fixes retained IDs/buckets after abandoned fights; the reverse index adds bookkeeping memory for active holders.
- The fighter tick reuses its combat attachment and tag classification across defense, aggression and sidearm handling. Defense/offense share one tick-local profile resolution, with a fresh lookup if a defense callback changes the main-hand stack/item. No cross-tick profile cache was added.
- `Combat.finishTick` records turn/pivot snapshots only while entering, continuing or leaving windup/release. `start` still initializes fresh snapshots, and stamina, queued attacks and phase synchronization still tick normally during idle.
- Player-animation `isActive` checks phase/spec availability instead of constructing and sampling an entire pose; `setupAnim` still computes the pose for rendering.
- Patrol members recheck chunk availability before each heightmap lookup. Their random walk can leave the originally checked 10-block area; reaching an unloaded chunk now ends the remaining spawn attempts instead of accessing it.
- **Benchmark: no measurable change.** On 2026-10-08 the machine was too noisy to measure savings this small. Alternating runs gave unpatched 1.04/1.39 and patched 0.30/1.36 ms per tick in scene F (unpatched had measured 0.27 earlier the same day). The bot-scene slowdown seen in the first runs was machine state, not the patch: unpatched code reproduced it. Re-measure on a quiet machine before quoting numbers.

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

## Team rules (report §21, architecture plan §19, 2026-10-07)
- **Allies** (`combat/Allies`): the same scoreboard team (`isAlliedTo`), or any two players with `playersAreAllies` (on by default: the mod is co-op PvE). Pets of the attacker or of its allies are never contacts. The rule that hostile mobs don't cut each other down unless fighting comes first, so allied mobs never get in each other's way.
- **Contact** (`SwingTracer.isValidTarget`): with `friendlyCollision` (on), allies are ordinary candidates, so they take up a target slot and their body can be the first one met. Before, scoreboard allies were skipped entirely.
- **Resolution** (`Combat.meetAlly`): kicks, jabs and throws pass by. Slashes, overheads, stabs and specials deal `friendlyDamageScale` (0.25, from the architecture plan's suggested config) through `CombatEvents.onIncomingDamage`, which skips parries, counters, blocks and stamina for ally hits. Stabs, light or heavy, always stop in the ally with a thwack (Chivalry 2 updates 2.9/2.10). Other attacks stop only where their contact policy would stop in any body. Ally hits don't count as landed hits, so a stab stopped in an ally gives no combo. Slams (`Specials.slam`) skip allies.
- **PvP:** with `playersAreAllies` on, two players fight as allies (a quarter damage, no parries). The guide says how to duel. Vanilla's `pvp=false` still blocks player damage either way.
- **Tests:** GameTests `stabStopsInATeammate`, `slashCarriesThroughATeammateForReducedDamage`, `noFriendlyFireStillBlocksTheStab`, `aTeammatesGuardNeverParries`, `kicksAndSlamsSpareTeammates`, `playersAreAlliesInCoop`. Mock players all share one scoreboard name, so these tests remove their teams before finishing. Mutation check: 10 of 10 mutants caught.
- **Not done:** friendly projectiles stay vanilla (arrows hit allies in full unless the server's team settings stop them). Bots don't steer around their own allies.

## Downed and revive (report §18.2, architecture plan §21.1, 2026-10-07)
- **Going down** (`combat/Downed.onLethal`, from `LivingDeathEvent`, so totems have already had their chance): players only. Not when downed already (that's the finishing blow), not for damage that bypasses invulnerability (`/kill`, the void) or for our own `steelclash:bled_out`, and only with a living, standing ally within `downedAllyRange` (48; 0 = always). The death is cancelled and health set to `downedHealth` (6).
- **Downed:** forced crawling pose (`Player.setForcedPose(SWIMMING)`, set on the client from the sync too, since the local player poses itself), −70% speed, no jump. The current attack or parry is dropped. `Combat.start`, `Combat.startParry` and `Dodge.perform` refuse. Item use, block breaking and placing, entity interaction and vanilla attacks are cancelled, and so is healing. Mobs can't take a downed player as a target and drop one they had (`mobsIgnoreDowned`); a stray swing still hits.
- **Bleed out:** `bleedOutSeconds` (30) counting down on the server, then `bled_out` damage. That damage type is in the bypass tags, and its death message is "X bled out". Logging out while downed bleeds out at once (the architecture plan: downed doesn't survive a reconnect).
- **Revive:** the closest ally holding sneak within `reviveRange` (2.5) who isn't fighting, downed or hurt this tick adds one tick of progress. A different ally, nobody, or a hit on the reviver restarts it. At `reviveSeconds` (3 s) the player stands with `reviveHealth` (30%) of max health. No self get-up: the reviewed sources give no formula for Chivalry 2's.
- **Sync:** `DownedPayload` (bleed ticks, revive ticks, reviver) goes to the player and everyone tracking them. It's sent on changes, every tick while a revive runs, and every half second for the countdown. The client drives the pose and `DownedHud` (countdown and hint for the downed player, progress bar for both). Protocol 9.
- **Fake players:** `ModNetwork.sendToTrackingAndSelf` used to broadcast to trackers blindly, and that throws at fake players whose connections never negotiated our channels. GameTest mock players hit it once they tracked each other, and so could other mods' fake players. It now checks the level's players and falls back to per-player sends when any of them can't take the payload.
- **Tests:** GameTests `lethalBlowDownsAPlayerWithAnAllyAround` (pose, health, no attack, parry, dodge, item use or healing, finishing blow), `aloneOrKilledOutrightYouDie`, `downedPlayersBleedOut` (plus logging out), `aCrouchingAllyRevives` (sneak needed, a hit restarts it, health, pose) and `mobsLeaveTheDownedAlone`. The GameTest world is creative, and new server players are invulnerable for 3 s (counted down only while they tick), so the tests' mock players are switched to survival and that timer is cleared by reflection. A mob damage source can't be used either: Steel Clash turns a fighter mob's instant hit into a telegraphed swing. Mutation check: 17 of 17 mutants caught. Two survived the first run and the tests were tightened: revive health equalled the default downed health, and the mock player couldn't dodge anyway because it wasn't on the ground.
- **Not done:** mobs don't go down. No punching from the knees, no self get-up, no limb loss.

## Hardening (2026-10-07)
- **Client input:** the three server-bound payloads already clamp or wrap every value: attack type by id, variant to ≥ 0 and wrapped to the profile, actions by ordinal. They ignore dead and spectator players, and the server traces every swing itself, so a client never claims a hit. The gap was volume: each input can trigger a combat-state broadcast to everyone tracking the player. `core/InputLimit` now caps a client at 8 inputs per tick. A guard release is never dropped, so a guard can't get stuck up. Unit test `InputLimitTest`.
- **Dedicated server:** covered by every GameTest run, since `runGameTestServer` is the dedicated-server build. Client classes are only referenced from `SteelClashClient` (`dist = CLIENT`), the client mixin section and `client/`; payload handlers reach client code through lambdas only.
- **Modpacks:** Better Combat and Epic Fight are declared incompatible in `neoforge.mods.toml`, so the loader refuses them with a message. Other mods' fake players are handled by the tracking-send fix (see "Downed and revive").
- **Not done:** a full modpack playtest and a two-machine dedicated-server session. Those are manual checks (M7/M8 in docs/testing.md).

## Report gaps, step A (Chivalry 2 report v2.1, 2026-10-08)

Five small rules from the report that the mod didn't have yet. Network protocol **11** (the counter flag below).

- **Blocking a special** (report §8.5, community-sourced): a weapon guard still stops it and pays its stamina (and disarms when out), but the blocker staggers (`specialBlockStaggerTicks`, 10) instead of opening a riposte, and the attacker isn't staggered. `Defense` marks the attacker's `CombatData.swingBlocked`, and the swing loop thwacks the blade at the contact point, so it stops on the guard without cleaving on. A held hit (lag compensation) stops at its current progress instead. Specials still can't be countered (they aren't weapon attacks), and an active parry still catches them. Shields are unchanged.
- **Specials stagger on hit** (§8.6, community): `specialHitStaggerTicks` (8) on any enemy the special damages. Allies are skipped, and the slam's area stagger is unchanged.
- **Missed sprint attack** (§8.2, official 2.10): a lunge that hits nothing gets `lungeWhiffRecoveryMs` (300) more recovery. Chivalry 2 doesn't publish the amount; 300 ms is our choice. Synced authoritatively, so the attacker's client recovers just as long.
- **Mounted damage** (§20.2, official): every melee attack scales with mount speed. Stabs and specials keep the couched curve (up to 2.5×); slashes and overheads get half the bonus (`DamageType.mountedSwingMultiplier`, up to 1.75×), our choice since the official notes give no numbers. Jabs and kicks get none.
- **No dodging out of a caught counter** (§7.3, official 2.6): `CombatStateMachine.counter` sets `countered`; `Dodge.canDodge` refuses while that windup lasts. Players dodge client-side, so the flag travels in `PredictionState` (bit 8 of its flags byte), hence protocol 11.
- **Arrows don't interrupt a throw** (§19.4, official 2.4.2): projectile damage skips the flinch during a throw windup.
- **Tests:** unit `aCounterThatCaughtItsAttackIsCommittedUntilTheNextAttack`, `extraRecoveryOnlyLengthensARecovery`, `mountedSwingsGetHalfTheChargeBonus`; GameTests `blockingASpecialStaggersTheBlockerNotTheAttacker`, `specialsStaggerWhatTheyHit`, `aMissedSprintAttackRecoversLonger`, `arrowsDontInterruptAThrow`, `noDodgingOutOfACounterThatCaughtItsAttack`. Mutation check: 11 of 11 caught. `arrowsDontInterruptAThrow` was flaky at first: the arrow sat at the world origin and zombies spawn facing anywhere, so the slashing control zombie sometimes deflected it (projectile counter). The zombies now face one way with the arrow behind them. The mounted damage path itself isn't integration-tested (only its formula).

## Report gaps, step B: release interrupts (report §2.8, 2026-10-08)

- **Rule:** a blow (damage from an entity: a swing, jab, projectile or thrown weapon, not fire or a fall) during the victim's release interrupts their attack with the ordinary flinch (`flinchTicks`). Heavies with hyper armour are immune, kicks never interrupt, and a throw windup still ignores arrows. `releaseInterrupt` (default on) restores the old always-trade rule when off.
- **Fairness:** fighters tick one after another, so an immediate interrupt would decide every simultaneous exchange by entity order. `Interrupts` collects the victims as the hits land and staggers them in `ServerTickEvent.Post`, after `LagCompensation.tick` delivers held hits (which may interrupt too). Blades meeting within the same tick both land and both flinch (a trade); a blade landing a tick earlier interrupts the other before it connects. A release that finishes during the tick isn't cut. The windup flinch stays immediate, as before.
- **Tests:** `aBlowDuringTheReleaseInterruptsItAtTheEndOfTheTick` (deferred to the end of the tick; hyper armour, fire and a release finishing in the tick are spared) and `bladesMeetingInTheSameTickTrade` (the player is ticked first, yet the dummy's blade still lands). The trade test needs an in-level player, since blades find targets with a level query: `DownedGameTests.player`, plus `TestSupport.applyWeaponModifiers`, because mock players never tick and a bare-handed attack speed made the player's slash land during the dummy's windup. Mutation check: 6 of 6 caught after adding the finishing-release case (the first run's survivor).
- **Test fix on the way:** `skeletonSwitchesOnItsOwn` was flaky. Its skeleton ran its own AI: a bow skeleton backs off to shooting range and could be out of drawing range (4 blocks) before its switch was due. It now spawns without free will; the switch runs in the entity tick either way. Three clean runs in a row afterwards.
- **Still to judge in play:** how exchanges feel against bots and in co-op (docs/testing.md).

## Earlier combo input and weapon retrieval (2026-10-08)

- **Combo input in the release** (Chivalry 2 buffers the combo press during the swing): `Combat.isBufferedPhase` adds RELEASE to the phases an attack press waits out, on the server and in the client's prediction. The buffered attack starts in `finishTick` once the release ends with `comboAllowed`, with the combo windup. A blocked, parried or interrupted swing staggers, and the stagger clears the buffer. `Combat.isComboInput` makes a press in the release pick the other side, as in recovery (`chooseSide` on the client, the two-argument `requestAttack` for mobs). Test `aComboPressedDuringTheReleaseStartsWhenItEnds`. The first version compared random sides, so a mutant that dropped the side flip could pass by luck; the test now checks `isComboInput` directly.
- **Weapon retrieval (continued below).**

## Smoother transitions (2026-10-08)

- **The problem, on pose sheets:** in first person, idle shows vanilla's hand (a big sword low at the right); the first frame of an attack switched PAL to the third-person model arms at weight 0, the rest pose, a small edge-on sword further away. The same cut came back at the end of the recovery. And every new windup started at weight 0, so a combo or riposte snapped from the end of the last swing back to rest before rising again (both views).
- **Ready stance** (first person only): `CombatPose.ready` aims the weapon 65° right of the view and 45° up (tuned on pose sheets with a sword and an axe: lower yaws showed the blade edge-on). `ProceduralSwingAnimation` uses it as the base instead of vanilla's arm pose in the first-person pass: arm, grip arm and shoulder, and the item's turn, so attacks blend out of it and back to it. Only for the local player, with the off hand empty (vanilla keeps drawing a shield or food) and no item in use. `firstPersonReadyStance` (client, on).
- **Hand-overs** (`core/PoseBlend`, `CombatPose.of`): a per-entity record of the pose last shown. When the state changes discontinuously (new attack serial, type or side, guard, stagger, feint), the pose blends from the last one shown over 4 ticks (smoothstep); a windup that starts while the arm was posed stays at full weight, going straight into the drawback. A feint fades out the same way. Natural continuations (windup to release to recovery, guard lowering, easing out) aren't blended, so the blade still matches the traced arc. Disabled while the pose sheet runs.
- **Pose sheet:** an `idle` shot (the GUI stays on there, since hiding it also hides vanilla's first-person hand), `ready:<yaw>:<pitch>` shots for tuning the stance, and `-PposeSheetPoints=windup:0,recovery:0.99` for custom points.
- **Tests:** `PoseBlendTest` (which transitions blend, short-way angles, part offsets). The blend inside a real frame sequence can't be photographed (the pose sheet pins single frames), so combos need a look in play.

## Weapon retrieval (2026-10-08)

- **Weapon retrieval:** `Disarm` remembers a mob's dropped weapon (`CombatData.lostWeapon`, its entity UUID, plus the time). `RetrieveWeaponGoal` (priority 0, MOVE and LOOK, above the spacing goal and vanilla melee) runs to it and equips it within 1.5 blocks. It gives up when the drop is gone (a player took it: players can pick up any disarmed weapon), more than 24 blocks away, or after 10 s. Only goal-based mobs do this; brain-based mobs (piglins) don't. `mobsRetrieveWeapons` (on). Test `aDisarmedMobGoesBackForItsOwnWeapon` (and leaves a player's dropped sword alone). Mutation check: 5 of 5 caught across both changes.

## Bug audit and fixes (2026-10-08)

Reviewed `1cfe922` plus both local optimization passes. Ten distinct findings were present:

1. `ClashBrain.defend` planned a `LATE_PARRY` in RELEASE, but `threats` admitted only WINDUP, so bots never late-parried. The patch admitted every RELEASE; applied narrower: a RELEASE is a threat only when it's the attack the bot planned to late-parry (same attacker and serial). Otherwise an attacker already mid-swing, which the bot can't answer any more, would hide a windup it can, since `defend` answers only the first threat (`anUnplannedReleaseDoesNotHideAWindup`). Counter-feint reactions stay restricted to WINDUP.
2. Attack selection assumed a nonempty set: a kick-only profile reached `nextInt(0)` in the basic mob controller, and an empty profile reached `iterator.next()` through a running spacing goal after a reload. Guard both paths and return an empty selection/fallback reach. Counted as one finding.
3. `Combat.startParry` feinted first, then rejected the parry during its cooldown, leaving an unpaid cancellation. `CombatStateMachine.cancelIntoParry` now rejects without changing the windup.
4. Release finalization required the tick to start in RELEASE. A WINDUP and short RELEASE can both finish inside one tick, skipping whiff stamina and slam effects. Detect the completed sweep instead, including when recovery already finished.
5. Combat snapshots omitted the rules needed to continue prediction: caught-parry count, guard recovery/cooldown, stagger permission, active parry, guard origin, counter-feint use and attack serial. A caught parry released to IDLE on the server but GUARD_RECOVERY on a fresh client. Serialize/apply `PredictionState`; network protocol is now **10**.
6. The client sent weapon-guard release only if its predicted phase was PARRY. If the server accepted a press that prediction rejected, that guard could stay held. Track the submitted press and always balance its release.
7. Friendly swings skipped weapon defense in `onIncomingDamage`, but the subsequent shield event still blocked, drained stamina and bounced the attacker. Disable that shield block for allied swing damage, preserving the ordinary damage hooks.
8. Revive interruption checked only whether a reviver was hurt in the patient's current tick. Damage after the patient's tick, especially a held hit delivered in ServerTick.Post, was missed next tick. Reset the matching revive at damage time.
9. Downed interaction cancellation covered EntityInteract but omitted EntityInteractSpecific. Armor-stand equipment interaction could succeed before the general event. Cancel both routes.
10. Lag grace ended for any matching windup, even after the counter window or outside its guard cone. Share the actual counter eligibility check with Defense before resolving early.

Validation (2026-10-08, Java 21, with both optimization follow-ups): build, JUnit and all 139 GameTests pass. Two of the audit's GameTests had never run and failed at first, both test mistakes, not code faults. `botCarriesItsLateParryPlanIntoRelease` attacked with a mock player, which isn't in the level, so the bot's threat query could never see it; it now uses a training dummy. `aTeammatesShieldNeverBlocksOrBouncesTheSwing` formed the team before an 8-tick wait for the shield, and every mock player shares one scoreboard name, so another test's cleanup took the player off the team; the team is now formed after the wait. Mutation check: 13 of 13 caught. Reverting each fix (threat narrowing both ways, cancel-into-parry cooldown, completed-release finalization, prediction state applied and serialized, ally shield, revive reset, precise interaction, counter grace, token cleanup on stop and on leave, clank switch) fails its test. Not covered by automation: the client guard-release bookkeeping (`ClientInput`) and the real LAN behaviour; see docs/testing.md.

## Animation presentation findings (2026-10-08)

- Holding a fractional tick does not hold a combat pose: the underlying phase elapsed time still advances.
  Presentation now captures an owned sample and shares it with the camera/model consumers.
- A 370 ms windup sampled at 390 ms needs to be in release at 20/180 progress, even while the current tick's
  simulation snapshot is still in windup. The read-only visual sampler agrees with a full simulation tick at
  partial tick 1 across 300 randomized attack timing sequences, without applying gameplay windows.
- All 33 original heavy windup endings differed from their shared release beginnings. The patched endpoints
  match across all channels; the heavy preparation peak is retained before the settle. New default bash and
  special-kind clips also have matching phase endpoints.
- A light-to-heavy upgrade changes normalized progress. Preserving the displayed pose at transition entry
  avoids the immediate rewind; a 0.2 s presentation blend follows the new windup without changing its duration.
- Euler branches can differ by almost a full turn for neighboring solves. Quaternion blending removes that
  branch dependence during partial-weight arm/item posing; full-weight solved orientations are preserved.
- The existing four-pixel shoulder-reach limit and rigid mob arm lengths still leave extreme grip endpoint
  error. Removing free-arm clip offsets from a solved mob grip does not resolve those geometry limitations.

Validation: build, JUnit and all 148 GameTests pass; mutation check 7 of 7; a before/after pose sheet changed only where expected. Details and manual checks: [animation.md](animation.md).

## Motion check with onion skins (2026-10-08)

Each attack was photographed every 50 ms of its windup, release and recovery (pose sheet with `-PposeSheetPoints` at
even time steps, `cameraMotion = 0` so the view stays still) and run through the onion-skin skill per camera view.

- **Found:** clips eased in and out of *every* keyframe, so a part stopped dead on intermediate keys. The slash
  release twists the body 45, 0, -50 degrees with a key at the middle: from behind, the subject moved 15, 22, **1, 25**,
  2 px per step: a stall at the key, then a lurch. `PoseClip` now uses monotone cubic tangents (Fritsch-Butland): the
  first and last keys still ease, a key where a part turns round still holds, nothing overshoots, and motion through a
  middle key keeps its speed. The same steps now read 13, 18, 9, 8, 4 px.
- **Checked and fine:** windup into release is seamless in every attack (stab: identical frames across the boundary;
  heavy slash: a 1% step). The heavy slash release is a clean bell (4, 8, 14, 16, 11, 8 px). The first-person ready
  stance turns continuously into each windup.
- **Fast but intended:** the windup draw (about 20 px per step for 150 ms) and the start of the overhead release,
  which follows the server's traced arc and must stay in step with it.
- **Not real:** single-step centroid jumps with an even changed share are the blade passing behind the body (seen
  from behind only), or pointing at the camera in first person.

Mutation check: 3 of 3 caught (every key stopping the motion, no hold at a turning key, no ease-in at the first key).

## Broader first-person swings (2026-10-08)

First person drew exactly the traced arc from the eye, which sits behind the hand: the slash (plus or minus 70
degrees) mostly pointed away from the camera, stayed bottom-centre and barely crossed the screen. In the first-person
pass only, the swing's angle from the view is now spread (`firstPersonSwingWidth`, yaw times the width, pitch times half
the extra) and raised (`firstPersonSwingLift`), and both arms slide toward the blade's side (12 px at width 2 and 90
degrees) and rise (0.1 px per degree of lift). Pose sheets at 1.0/0, 1.4/15 and 1.7/25: 1.4/15 starts the slash with
the hands at the right edge and ends with the blade out at the left; at 1.7/25 the arms fill the screen. An onion-skin
run at 1.4/15 shows continuous motion: the largest step is the arms crossing the centre at the fastest point of the
release, with elevated neighbours. The hit sweep, camera sway, third person and other players are unchanged.

## Arm motion by swing type (2026-10-08)

Filmstrips of slash, overhead, stab and heavy slash (front and back), side views from the mob pose sheet, and both with
`-PposeSheetDebug` so the traced blade is drawn on the same frame.

- **Mobs held the weapon about 0.7 blocks below the hit.** A mob's held item couldn't be turned in the hand, so
  `ArmAim.aimArmForBlade` pitched the whole arm about 80 degrees below the aim to point vanilla's handheld blade along
  it: the hand hung at the hip and every cut and thrust was drawn at waist height while the trace ran at eye height.
  Players were fine (blade along the trace, small offset). `ItemInHandLayerMixin` now turns the item in a mob's hand
  just before it is drawn, at the same point and in the same order (Z, Y, X of the hand-frame angles) as Player
  Animation Library does for players, so mobs use the player's `WeaponRig`. Side views with the trace: husk stab, slash
  and overhead blades lie on the red line; a vindicator's axe too. A mob type is only turned once the item layer has
  drawn an item for it; others keep the pitched arm. The mob pose sheet now marks its mob aggressive, as fighters are
  in game: a vindicator only shows its arms and weapon then.
- **Windups didn't read as loading the blow.** The slash drew back to 1.25 times its start (87.5 degrees for a sword:
  the arm straight out sideways, the blade just past the shoulder line); the overhead stopped at straight up because
  pose pitch was clamped to -90. `ArcPath.windup` now draws back against the arc's own direction of travel (40 degrees
  light, 55 heavy), so a slash cocks to about 110 degrees and an overhead leans back to about -110; a thrust, which
  extends rather than turns, is only pulled in and raised as before. Pose pitch may go to -125 (`ArmAim.MIN_POSE_PITCH`:
  up and behind); the traced blade keeps to -90..90. The first-person spread eases off past 100 degrees so a cocked
  slash stays at the screen edge.
- **Smoothness:** onion skins every 50 ms from behind: the slash's settle into the release is a smooth bump (4, 10, 11,
  5 px) with no step at the hand-over; the overhead is unchanged. In first person the overhead windup raises the arms
  into a V in 150 ms, continuously, with the blade up out of view.

Mutation check: 7 of 7 caught after one fix (the first run showed that clamping the arm aim back to -90 left the arm
leaning back but the blade upright, which no test checked; `theArmCanLeanBackOverTheHead` now checks the blade too).


## Counter and dodge fidelity patch (2026-10-09)

- Melee counter eligibility now requires the existing `CombatStateMachine.isFromGuard` flag, shared by the direct hit and lag-grace paths. A neutral matching attack takes the hit rather than acquiring a counter. Existing prediction snapshots already carry this flag; packet formats stay the same.
- A successful counter avoids the incoming block stamina charge. Feint/heavy/whiff action costs keep their existing accounting, and no stamina refund is added. A wrong counter caught by parry forgiveness still pays ordinary block stamina.
- Bots enter guard before starting their matching counter. A guard cooldown must not silently convert that defensive decision into an ordinary attack. Counter-feints retain their guard origin.
- `CombatStateMachine.canDodgeWindup` is the shared cancellation predicate for client prediction and server requests. Jab, kick, heavy, and already successful counter windups reject a dodge. Rejection preserves the attack, stamina, cooldowns, and queued input.
- Unconnected light counter attempts retain the existing dodge behavior. The exact late-attempt cutoff still needs measurement; no undocumented percentage is introduced here.
- Counter fixtures now enter guard explicitly. The expired-counter lag test lets the guard cooldown expire before simulating an old windup, so its later guard request tests latency grace rather than a fresh cooldown.

References: the supplied combat report sections 4.4-4.5 for counter origin/accounting; Torn Banner's [Fight Knight 2.2 notes](https://chivalry2.com/2021/10/25/chivalry-2-content-update-fight-knight-2-2/) for committed jabs/kicks/heavies, and [Reinforced 2.6 notes](https://chivalry2.com/2022/10/04/reinforced-update-patch-notes/) for counter commitment. Exact counter refunds and the late unsuccessful-counter dodge cutoff are not established by these sources.

Validation in an isolated copy: compileJava, 64 focused JUnit tests (state machine, defense math, turn limits), and all 151 GameTests with the Spartan integrations and again without them pass. Mutation check: 7 of 7 valid mutations caught by their intended tests (guard origin, counter impact stamina, jab/kick/heavy commitment, server dodge wiring, and bot guard entry). Client/LAN checks above remain manual.

Re-verified on this repository after applying: build, JUnit and all 151 GameTests pass; mutation check 6 of 6 caught by their intended tests (neutral matching attack countering, counter paying half a block, jab and heavy dodge-cancels, the server dodge ignoring the shared predicate, bots countering from neutral).

## Lower first-person arms (2026-10-09)

The arms took up a large part of the screen: they sat 1.5 px down, and the swing lift (0.1 px per degree, 1.5 px at the
default 15 degrees) raised them back to the shoulder line during swings. Default first-person `down` is now 3.5 px
(`AnimationSet.FirstPerson.DEFAULT`, the parser fallback and `default.json`) and the lift is 0.05 px per degree. Pose
sheets at 1.5, 3.5 and 5 px (idle ready stance, slash, overhead, stab): 3.5 keeps the weapon fully in view with clearly
less arm on screen; at 5 the ready-stance hands nearly leave the bottom edge. A resource pack can still set its own
`first_person.down`.

## Exhaustion, unarmed blows and shields (2026-10-09)

- **Exhaustion.** Reaching zero stamina only mattered on the hit that emptied it: `Stamina.spend` reported the drain and
  only the parry path used it, so feints and morphs were free at zero and the next tick of regeneration ended any
  consequence. The report treats zero as a state ("a high-value punish state with a restricted action set", 4.13,
  14.5). `Stamina` now has an exhausted flag: set on reaching zero, cleared once stamina regenerates back to
  `exhaustionRecoverFraction` (0.25; the report gives no number, and without a threshold the state would end on the
  next regeneration tick). While exhausted, a blocked melee blow breaks the guard (weapon: disarm and stagger; shield:
  guard break and cooldown), and feints, feints into kicks/jabs, morphs and dashes are refused. The flag rides on the
  stamina packet so the client predicts the same refusals; the HUD frame pulses red.
- **Unarmed blows.** Any damage during a windup flinched it and any blow during a release interrupted it, so a punch
  with an empty hand cancelled a mob's attack. `CombatEvents.unarmedBlow`: a direct melee hit from an attacker whose
  main hand has no weapon profile doesn't interrupt; weapon blows, Steel Clash swings and projectiles are unchanged.
  It applies to everyone, mobs and players alike.
- **Shields vs Chivalry 2 (report 9).** Checked: kicks break shield guards, attackers bounce off shields, cones cover
  the front (tower wider), vanilla durability wears shields out. Changed: block cost was 70% (basic) and 50% (tower) of
  the hit's stamina damage, less mitigation than even a Chivalry 2 light shield (~50%); now 35% and 25%, in line with the
  reported medium (~65%) and heavy (~75%) mitigation and the official "substantially less than a weapon parry".
  Arrows broke a shield guard when they drained the last stamina; official patch notes say they mustn't, so projectiles
  never break one now. Not applicable: Chivalry 2's shield on the back stopping arrows from behind (Minecraft keeps the
  shield in the offhand).

Tests: `StaminaTest` (3), GameTests `anUnarmedPunchDoesNotInterruptAnAttack`, `anExhaustedGuardBreaksBeforeTheBarIsEmpty`,
`anExhaustedFighterCannotFeintMorphOrDash`, `arrowsNeverBreakAShieldGuardButBlowsDo`; all 155 GameTests pass. Mutation
check: 9 of 9 caught by their intended tests after strengthening three tests (the first run showed the arrow test never
drained the shield, the dash check ran on an airborne fighter, and two duels close enough that a random-side slash hit
the wrong dummy). `skeletonSwitchesOnItsOwn` failed in some runs regardless of the change: still flaky.

## Beta combat/animation audit (2026-10-09)

User direction: Chivalry 2 combat fidelity, animation improvements and original styles per weapon family. See
`docs/combat-animation-goal.md`. The supplied full technical report describes a separate protocol-12 patch and
72 potential bugs; this checkout began at `1b1f992`, protocol 11. Findings must be evaluated against current code.

Confirmed and fixed against this checkout:
- **C03:** `allowCombo` accepted clean-hit notifications for every action. Require slash/overhead/stab so landed
  jabs, kicks, specials and throws retain recovery. Normal weapon whiffs and thwacks still combo. The old timing
  HUD test explicitly granted a kick a combo; replace that obsolete expectation and verify the weapon HUD case.
- **A02:** a dummy copies supplied practice gear, so its disarm must remove the copy without spawning loot or
  registering a recoverable lost weapon. The real-drop test now uses an armed husk, preserving actual mob drops.
- **A01:** a second lethal blow previously left downed/revive fields and crawl modifiers intact. Clear them when
  the finishing death proceeds; the downed tick also rejects a dead patient before completing a stale revive.

New real GameTests reproduced all three bugs before the fixes. After fixing: 184 unit tests and 159 GameTests
with Spartan integrations passed; the fresh build and all 159 optional-mod-free GameTests passed too.
Mutation check: 4/4 valid mutants caught by the intended tests (nonweapon
combo, copied dummy drop, finishing-death cleanup, dead-patient revive). Full rebuild and optional-mod-free
verification are recorded with fresh logs under `C:/dev/steelclash-beta-audit/20261009`.

Animation evidence: isolated dev directory and copied world, camera motion 0, sword slash sampled every 50 ms
(500/425/750 ms base phases). The first capture retained potion particles; it is not valid smoothness evidence.
Pose-sheet staging now clears effects. The repeated clean capture has 34 frames per view. Third-person back flags
0–150 ms and 650–700 ms by centroid displacement; the pose sheet shows a gradual raise and a continuous rotating
release with blade occlusion, not a proven snap. Keep this baseline for finer/live and before/after comparisons.
Weapon style clips currently vary largely in amplitudes on common patterns; authored style work remains open.

## Weapon style draft and downed ready-state check (2026-10-09)

Authored nine family styles in the existing phase-normalized pose files: family ready yaw/pitch, preparation
timing, torso/hip motion and follow-through. Daggers keep the free hand guarded; rapiers retain a side-on body;
axe and blunt drive peak at different release times. The live arc still solves the weapon arm. No weapon profile,
reach, hit sweep or packet changes were made in this style pass. Optional ready fields default to 65/-45 and are
finite/bounded; clip fallback preserves each pack's metadata.

Visual evidence: 351 new-style poses and 351 original-asset poses for nine items, idle/light/heavy slash/overhead/stab,
first/third person, right main arm, camera motion 0 in the isolated copied world. The valid original baseline uses
an external Gradle resource override and verifies all nine copied files against the saved original bytes before
launching. An earlier resource-pack capture loaded below mod resources and is invalid; the initial screenshot
copy also compared local/UTC times incorrectly. Corrected UTC copying and asset validation prevent silent reuse.
Focused differences show changed first-person ready orientation and body preparation, confined to the subject.
These static samples do not prove uniform motion or live transitions.

N11: ready-pose creation and PAL layer eligibility previously ignored downed state. Both now suppress standing
ready presentation while downed/dead. `PoseSheet` can stage a `downed` player client scene, supplying the same
downed fields/forced crawl pose as client snapshots, and asserts no ready pose or layer activation. Removing
each guard independently makes the real client fail with its corresponding `DownedReadyScene` exception.
This verifies rendering eligibility, not the complete LAN lifecycle.

Verification: fresh build and all 186 JUnit checks pass. Five valid mutants caught: ignored ready yaw, invalid
ready bounds, lost metadata during fallback, downed ready-pose creation, and downed layer activation. The last
two run actual client scenes, and failure logs were inspected for the intended exception. Source restored and
rebuilt afterward. Asset-number formatting was cleaned without changing any parsed animation values.

Remaining: uniform before/after motion capture, mob rendered-blade alignment, handedness/offhand/model/renderer
matrix and real input/network transitions. Blockbench is a useful body-motion authoring path, but its exports
need coordinate/bone/phase conversion into this format while preserving procedural weapon-arm ownership.

## Atomic built-in attack replacement (2026-10-09)

Confirmed C01 with real GameTests: a post-disarm jab cooldown rejected `start` only after `feintInto` had
cancelled the original windup and spent stamina. The windup request branch then returned without correcting
the client. The new tests failed on both intact-state and owning-client packet assertions before the fix.

Shared action eligibility now checks downed state, holster/item cooldown (retaining the kick exception), and
jab cooldown before cancelling a windup or charging the feint. Rejected built-in or changed-type/side requests
send an authoritative correction; a repeated unchanged attack input does not create extra correction traffic.
Accepted jab/kick replacements still start and pay exactly once at cooldown expiry.

`ActionReplacementGameTests` checks the complete attack snapshot, stamina, queued input and hit bookkeeping
under rejection; accepted cooldown boundaries and kick exceptions; and production dispatch through `ModNetwork`
to a recording owning-player listener. This tests correction contents/delivery paths, not negotiated network
transport or latency. All 162 with-compat GameTests passed. Mutation check: 3/3 caught by their intended tests
(late eligibility check, off-by-one cooldown, omitted correction). The restored build and no-compat results are
kept under `C:/dev/steelclash-beta-audit/20261009/action-replacement`. The fresh build passed all 186 JUnit checks,
and all 162 GameTests passed without the optional mods too. Mutation source was restored before the final build.

## Capture integrity and dagger/mob review (2026-10-09)

Recorded the actual PAL player-layer and mob-model samples before screenshot readback. Captures now reject
stale shot indices, wrong camera/phase/progress, and different held-item IDs. Unknown items and missing requested
mob types fail explicitly instead of substituting an iron sword or player. Mob reference shots hide the local
player's hands. These guards apply only to the dev capture workflow; combat rules/timings remain unchanged.

Dagger evidence: original/authored 32-frame sequences in three views at 50 ms, then a 24-frame 700–930 ms window
at 10 ms. Whole-frame onion reports used identical thresholds before/after. The fine first-person maximum is
part of a broad speed peak (neighboring steps increase too), not an established isolated snap. Recorded model
samples matched requested 0.875/0.975 release and zero-progress recovery in all views. No gameplay timing change
was justified by these measurements. They still do not prove live input/correction transitions.

Husk evidence: 162 release-reference poses, nine item families, slash/overhead/stab at 0/0.35/0.7, front and side,
with model-time assertions and debug blade lines. Direction alignment is broadly close in the reviewed side
sheets; small hilt/length offsets remain and are not a full endpoint calibration. Separate polearm and single
spear runs also passed requested-item checks. A spear-head resemblance initially suggested a wrong model;
the actual model lookup/render passes resolve `spartan_weaponry_unofficial:item/iron_spear`. The visual suspicion
was not supported by model data, so no speculative animation or optional-mod change was made for it.

The development evidence is under `C:/dev/steelclash-beta-audit/20261009/weapon-styles`. The approval service briefly
became unavailable due account usage limits; work resumed after the user's reset. Negative guard checks and the
restored build are recorded with the next checkpoint. The full renderer/handedness/offhand/live matrix remains open.

Capture validation mutation check: 2/2 caught in actual client runs (stale shot index, wrong item identity).
Failure logs were checked for the requested-shot mismatch exception. Optional `-PposeSheetInspectModel` makes
the model/sprite inspection reproducible through the normal dev command; the restored build is recorded below.

Restored full build: all 186 unit tests passed. These changes are confined to client capture validation and its
dev command; server combat behavior retains the previous successful 162-test verification. GitHub CI runs the
full build and both GameTest variants on the pushed checkpoint.

## Synchronized combat rules (2026-10-10)

N01 is confirmed: the gameplay spec was registered as `COMMON`, which NeoForge does not sync, while prediction,
camera turn limits and the debug HUD read those values on the client. The spec now registers as `SERVER` with
the explicit existing filename `steelclash-common.toml`. Client preferences retain their `CLIENT` registration.

Verified against NeoForge 21.1.252 sources and FML 4.0.44 bytecode, consistent with the
[1.21.1 configuration docs](https://docs.neoforged.net/docs/1.21.1/misc/config/): server configs load from the
global config directory before world startup; an existing same-name file in `<world>/serverconfig` overrides
it. No migration/copy of the user's global file is needed. NeoForge's configuration task sends the active file
before entering play. This is join-time sync; it does not broadcast later config edits to connected players.
Restart the world/server and reconnect guests when changing combat rules.

`ConfigSyncGameTests` exercises the framework's actual outgoing config selection and file bytes, then its public
in-memory receiver with different guard, stamina, cooldown, dodge-cost and turn-cap values. Cached values are
replaced, the guard/stamina callers use them, and saving the received config leaves the backing file unchanged.
The receiver test restores file-backed configs through FML before returning, without advancing a world tick.
Its outgoing check uses NeoForge's internal `ConfigSync` API only in the test; production uses normal config
registration. These tests do not negotiate a remote socket or validate latency.

An isolated server fixture used different global/world turn caps (251/137 degrees per second). All 164 tests
passed with the world override selected; both TOML files retained their SHA-256 hashes. The first override
attempt exposed a test-only path comparison mistake (`world/serverconfig` versus `world/./serverconfig`),
corrected with `Files.isSameFile`. Evidence is under `C:/dev/steelclash-beta-audit/20261010/config-sync`.

Final verification: restored build passed all 186 JUnit tests; all 164 GameTests passed with and without the
optional mods (including the separate with-compat override run). Both targeted mutants were caught after
the fixture correction: the old `COMMON` scope and the default `SERVER` filename that loses the existing file.
The actual client loaded the global combat file in the disposable singleplayer world and completed three
time-verified slash screenshots (back/front/first person). This is startup/pose smoke coverage, not a new
motion-smoothness or negotiated-network verdict.

## Heavy greatsword and polearm motion review (2026-10-10)

Compared original/authored assets with the same renderer code at source checkpoint `2d50777`, in the disposable
Dev1 world. The baseline Gradle override verified all nine original animation files byte-for-byte; authored
runs verified the runtime resources against current source. Viewport: 1920×1200, stored FOV option 0.625,
right-main-hand option, cameraMotion 0. The captured skin/equipment are visible in the sheets; model variant,
armor and offhand were not separately forced, so this does not complete that acceptance matrix.

Heavy overhead phase lengths at the pose-sheet's unscaled base speed were 925/525/1200 ms for the greatsword
and 950/500/1200 ms for the halberd. Each original/authored run captured 53 samples per view at 50 ms in back,
front and first person: 636 rendered screenshots. All requested phase/progress/index/view/item checks passed.
Two further windows, 360–560 and 1880–2240 ms, captured 11 and 19 samples per view at 20 ms: 360 more screenshots.
The same onion thresholds (30, flag ratio 2.5) were used before/after, with chronological manifests and fresh files.

The coarse authored greatsword back view flagged 2000–2050 and 2100–2150 ms. Finer sampling showed a burst over
several adjacent steps in both original and authored curves, with gradual head/torso/weapon movement in the
focused sheets. The polearm's authored first-person 1920–1980 ms return also spans neighboring frames; the
inspected sheet does not establish an isolated snap. No animation timing/asset change was justified by these
flags. This verifies selected frozen phase curves; live blending, corrections, hit-stop and other model/hand
conditions remain open. Evidence: `C:/dev/steelclash-beta-audit/20261010/heavy-motion` and `heavy-motion-fine`.
All nine current runtime style resources were restored and verified after the captures.

## Delayed specials and archer sidearm ordering (2026-10-10)

C02 was reproduced: an immediate sword special damaged and staggered an idle defender, while a held special
delivered the same damage after forced 150 ms grace without its hit stagger. `SpecialGraceGameTests` failed at
the delayed reaction assertion before the fix. Both delivery paths now share successful-hit feedback/stagger
using the captured `Hit` type and heavy flag, independently of the attacker's newer action. Ordinary delayed
hits do not inherit a newer special's reaction, and a guard arriving during grace retains only the blocked
special penalty. These checks exercise real contacts and vanilla damage on a server with forced latency;
they do not negotiate a delayed network connection or cover all historical guard/attack-ID issues.

An existing skeleton-switch test timed out during verification. A new deterministic entity-post-tick dispatch
test then showed the archer entering WINDUP while still holding its bow: `ClashBrain` chose the natural claw
fallback before `Sidearms.tick`, and the resulting busy state postponed the draw. Sidearm draw/stow now runs
before a new brain action. The test also checks that an already-committed attack keeps its original weapon;
the existing cooldown/switch and naturally ticking skeleton tests remain. The updated suite passed 168 tests
with optional integrations. Pre-fix failures and subsequent checks: `C:/dev/steelclash-beta-audit/20261010/special-grace`.

Final restored build passed all 186 JUnit tests; all 168 GameTests passed with and without optional mods.
Mutation check: 4/4 valid mutants caught by the intended tests (old delayed-hit omission, newer attacker type,
unguarded successful-hit consequences, original late sidearm order). The original skeleton timeout prompted
the ordering investigation; the deterministic reproducer guards that specific cause rather than relying on
another favorable random run. Live reaction/animation checks remain listed in `docs/testing.md`.

## Friendly pets and ground slams (2026-10-10)

C05 was reproduced with full mace specials: the ordinary arc already excluded friendly pets, but the secondary
slam query checked only `Allies.areAllies`. Without scoreboard teams, pets of the attacker and a co-op ally both lost stamina;
the scoreboard relationship did not identify them as allies. The area query now uses the same
`Allies.isFriendlyPet` exclusion as blade contacts, before stamina, stagger, item-use cancellation or knockback.
Its radius, damage pipeline and hostile-target effects retain their existing behavior.

`PetProtectionGameTests` verifies own/allied protection and enemy-owned eligibility with co-op disabled, with
an affected wild-wolf control in every test. It checks health/stamina, phase and velocity after the full slam.
The ally/enemy owner is an in-level player and must resolve through the wolf's owner lookup. Mock scoreboard
membership and co-op settings are changed/restored synchronously to prevent inherited teams from hiding the bug.
Both protected-pet tests failed before the fix; the enemy-owned case passed.

Restored build: all 186 JUnit tests and 171 GameTests in each optional-mod variant passed. Three valid mutants
were caught by their intended tests: missing pet exclusion, excluding all ownable creatures, and protecting
only the attacker's own UUID. Evidence: `C:/dev/steelclash-beta-audit/20261010/pet-slam`.
This fixes pet eligibility; slam cover/impact geometry and stagger precedence remain separate audit work.
