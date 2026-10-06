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
