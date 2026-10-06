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
- playerAnimator part names (from the jar): `head`, `body` (upper body), `torso` (whole body), `rightArm`, `leftArm`, `rightLeg`, `leftLeg`, `rightItem`, `leftItem`. ROTATION is radians; `BEND` is `Vec3f(axis, angle, _)`.
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

