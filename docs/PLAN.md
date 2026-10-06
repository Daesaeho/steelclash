# Plan: Chivalry 2-style melee combat for NeoForge 1.21.1

Working name: **Steel Clash** (mod id `steelclash`). Pick your own name, but keep "Chivalry" out of it: that name belongs to Torn Banner. Copying the *mechanics* is fine. Do not copy their names, sounds, animations or UI art.

Target: Minecraft 1.21.1, NeoForge 21.1.x, Java 21 (already installed: 21.0.8).
Integrations: [Spartan Weaponry Unofficial](https://github.com/Mai-xiyu/SpartanWeaponry-NeoForge) (`spartan_weaponry_unofficial`, API v15) and [Spartan Shields Unofficial](https://github.com/Mai-xiyu/SpartanShields-NeoForge) (`spartanshieldsunofficial`, the jar's real mod id; see docs/spikes.md). Both are Apache-2.0 and published on Modrinth and CurseForge.

### Decisions locked in
- **Faithfulness:** the combat should be as close to Chivalry 2 as possible. When fidelity and Minecraft convenience conflict, fidelity wins, with a config option where reasonable.
- **Focus: PvE.** Mobs fight with the same ruleset as players, like Chiv 2's bots. Multiplayer netcode work moves to the end.
- **SW/SS: optional.** The mod works with vanilla weapons. Spartan integration turns on automatically when either mod is installed.
- **Camera: first person is primary,** as in Chiv 2, with full third-person support as well.
- *(2026-10-06: the default `CHIVALRY` control scheme restores this: one alternating slash key on left click and parry on right click. The `TWO_SLASH_KEYS` scheme keeps the per-side slash keys; gestures are optional, with an optional view lock.)* **Right mouse is always block** for anything you can melee with, as in Chiv 2. Throwing uses its own key. Bows and crossbows fire on left mouse (see controls).
- **Parrying at 0 stamina = disarm,** as in Chiv 2. Your weapon is knocked out of your hands and drops in front of you. The config can switch this to a "holster" mode, where the weapon is locked for 3 s instead of dropping.

---

## 1. Design pillars

1. **Every swing is a committed action with readable phases:** windup → release → recovery. The opponent can see what's coming and react to it.
2. **Defense is active and costs stamina:** parry, riposte, counter and shield block. Running out of stamina gets you punished.
3. **Mind games:** feint, morph, drag/accel and combo.
4. **Physical hits:** a swing hits whatever its arc passes through, not just whatever the crosshair is on. Ducking, spacing and footwork matter.
5. **The data drives the weapons:** every weapon type is a JSON profile that can be tuned with `/reload`. Spartan Weaponry types map to profiles automatically.

## 2. Chivalry 2 mechanics → Minecraft translation

MC runs at 20 TPS (50 ms per tick). Chivalry 2 timings of roughly 300–700 ms map to 6–14 ticks. The client animates at frame rate, so the 50 ms tick granularity is fine for gameplay.

| Chiv 2 mechanic | Minecraft implementation | Milestone |
|---|---|---|
| Slash / Overhead / Stab | Three attack types. Each has its own arc path, timings, damage multiplier and stamina damage. | M1 |
| Heavy attack (hold) | Holding the attack input past ~4 ticks turns the attack into a heavy: longer windup, more damage and stamina damage. | M3 |
| Windup / Release / Recovery | A server-authoritative state machine on every combat-capable `LivingEntity`. | M1 |
| Hit detection along the arc | Sweep a capsule (blade segment) along the attack path with 3–4 sub-steps per tick. Test it against entity AABBs and remember who was already hit this swing. | M1 |
| Drag / Accel | Free: the arc is defined *relative to the player's current view*, so turning during release moves the blade. | M1 |
| Environment clank | A block raycast along the blade. If the blade hits a solid block before an entity, the swing stops into a "bounce" recovery. Configurable. | M3 |
| Parry (weapon) | The block key starts a parry that lasts for a limited time (~10–14 ticks), then drops automatically. It only covers a frontal cone. | M2 |
| Riposte | A successful parry opens a riposte window. The next attack gets a faster windup. | M2 |
| Counter | Pressing the matching attack type during an enemy's release, inside a small window, parries the hit and ripostes in one action. | M3 |
| Shield block | Shields can be held indefinitely and cover a wider cone. Tower shields cover the widest cone and also block projectiles. Shield blocks drain less stamina. | M2 |
| Stamina | A synced data attachment. Blocking, feints, morphs, kicks and missed swings cost stamina, and it regenerates after a short delay. Parrying at 0 stamina **disarms** you. | M2 |
| Disarm | The weapon drops as an `ItemEntity` in front of you, tagged so mobs can't pick it up. You fight with fists until you pick it up again or switch hotbar slots. **Mobs can be disarmed too**, and their weapon then drops for you to take. Config: `disarm_mode = drop / holster`. | M2 |
| Feint | During windup, the feint key cancels the attack back to idle for a stamina cost. Pressing block during windup cancels straight into a parry. | M3 |
| Morph | During windup, pressing a different attack type switches to it for a stamina cost. | M3 |
| Combo | Attacking during recovery after a hit skips the rest of the recovery. | M3 |
| Flinch | Getting hit during windup interrupts the attack. Heavy two-handers get "hyper armor" on heavies (per-profile flag). | M3 |
| Kick / Shield bash | Breaks guard, drains stamina and causes a short stagger. Low damage. | M3 |
| Duck | Mostly free: sneaking lowers the player hitbox (1.8 → 1.5), so high horizontal arcs pass over it. | M1 |
| Sprint attack / jump attack | Sprint + heavy = a lunge with extra reach. Airborne overhead = a jump attack. | M7 |
| Special attack | One special per class, e.g. spear lunge, or hammer slam using the existing SW `HAMMER_SLAM` trait. | M7 |
| Throw weapon | A dedicated throw key. SW throwables use their own entities. Every other weapon uses a generic `ThrownWeaponEntity` that drops back as an item. | M7 |
| Mounted / lance | Uses SW's `DAMAGE_BONUS_RIDING` and adds a charge state while mounted. | M7 |
| Cut / Blunt / Chop vs armor | Each profile has a damage type. Optional armor-class modifiers (light/medium/heavy) based on the target's total armor value. | M7 |

Out of scope for v1: objectives/teams game mode, dismemberment beyond SW's `DECAPITATE`, voice commands, revives.

### Default controls (all rebindable)

| Action | Default | Notes |
|---|---|---|
| Slash right→left | LMB | Hold for heavy. |
| Slash left→right | RMB | Hold for heavy. Right click on doors/chests/villagers/mounts, while sneaking, or with a bow/trident stays vanilla. (Changed 2026-10-06 at the user's request; parry moved off RMB.) |
| Overhead | Scroll up / Mouse 5 | While holding a combat weapon, scrolling triggers attacks instead of switching hotbar slots (config toggle). Number keys still switch slots. |
| Stab | Scroll down / Mouse 4 | |
| Block / Parry | Middle click (rebindable; also raises an offhand shield) — was RMB | Always block for anything you can melee with, including SW throwables. Their vanilla right-click throw moves to the throw key. |
| Bow / crossbow | LMB hold to draw, release to fire. RMB cancels the draw. | Implemented by sending LMB to the vanilla use action while holding a ranged weapon, so SW longbows and heavy crossbows keep their own logic. |
| Feint | X | Not Q, because Q is vanilla drop. |
| Kick | Z | Uses a shield bash when a bash-capable shield is in the offhand. |
| Special | R | |
| Throw | G (hold) | |
| Mining with a weapon | Sneak + LMB | Keeps SW `VERSATILE_*` weapons usable as tools. |

## 3. Architecture

```
steelclash/
  core/            pure Java, no Minecraft imports → unit-testable
    CombatStateMachine, Phase, AttackType, AttackSpec, Timings,
    StaminaModel, ArcPath (yaw/pitch keyframes), CapsuleMath
  combat/          MC glue (server)
    CombatantAttachment     (state + stamina, NeoForge Data Attachment, synced)
    SwingTracer             (capsule sweep vs entities & blocks, sub-ticks)
    HitResolver             (calls vanilla Player.attack, applies multipliers)
    DefenseHandler          (LivingShieldBlockEvent / LivingIncomingDamageEvent)
    LagCompensation         (ring buffer of entity AABBs, rewind by ping) [M5]
  profile/         WeaponProfile codec, datapack registry, data map, SW auto-mapping
  net/             CustomPacketPayload records + StreamCodecs
  client/          input interception, key mappings, prediction, HUD layers,
                   animation bridge (Player Animation Library; playerAnimator until 2026-10-06), debug renderer
  compat/spartanweaponry/   loaded only if ModList.isLoaded(...)
  compat/spartanshields/
  compat/shouldersurfing/   optional camera niceties
  entity/          TrainingDummy, ThrownWeaponEntity
  ai/              ClashMeleeGoal (mobs use the same state machine) [M6]
```

### 3.1 State machine (core)

`IDLE → WINDUP(type, heavy) → RELEASE → RECOVERY → IDLE`, plus `PARRY`, `RIPOSTE_WINDUP`, `STAGGER(ticks)`, `GUARD_BROKEN`, `KICK`, `FEINT_RECOVERY`.

- The machine is ticked server-side in `EntityTickEvent.Post`. The client runs the same code for local prediction and reconciles against the server's `CombatStateSync`.
- Transitions are pure functions of `(state, input, tick, profile)`. That makes the whole ruleset unit-testable without launching Minecraft.
- Attack speed scales timings: `ticks = base × (refSpeed / ATTACK_SPEED attribute)^k`. This lets SW's `HEAVY_n`, `LIGHTWEIGHT_n` and `QUICK_STRIKE` traits, which already change attributes, flow through automatically.

### 3.2 Input (client)

- `InputEvent.InteractionKeyMappingTriggered`: cancel attack and use and call `setSwingHand(false)` while holding a profiled weapon, then send a C2S intent payload instead.
- `InputEvent.MouseScrollingEvent`: cancel and map scrolling to overhead/stab while in a combat stance.
- `RenderGuiLayerEvent.Pre` on the crosshair/attack-indicator layer: hide the vanilla cooldown indicator.
- Register the new keys in `RegisterKeyMappingsEvent`.

### 3.3 Hit detection

- The blade is a segment from a pivot (shoulder) to `pivot + dir × reach`, with radius ~0.15.
- Reach = `player.entityInteractionRange()` + the profile's per-attack offset. SW's `REACH_*` traits should already show up through the interaction-range attribute (**verify in the M0 spike**).
- Each release tick, interpolate the arc keyframes at 3–4 sub-steps and test the swept capsule against target AABBs, inflated slightly.
- Slashes can hit several targets, with damage falling off for each extra target. Stabs hit one target and stop.
- Detection is server-authoritative. In M5, add rewind: keep ~20 ticks of entity AABB history and test against `now − attackerLatencyTicks`, capped at ~250 ms.

### 3.4 Damage pipeline (the key compat decision)

Apply hits by calling **vanilla `Player#attack(target)`**, with `attackStrengthTicker` forced to full first, instead of hurting the entity directly. That way you keep:
- enchantments (Sharpness, Fire Aspect, Knockback)
- SW traits that live in its `PlayerMixin`/`LivingEntityMixin` (armor piercing, backstab, chest/head/unarmored/undead bonuses, nausea, shield breach)
- other mods listening to `AttackEntityEvent`

Around that call:
- Store an `ActiveSwing` context (type, heavy, riposte, combo, target index) in a field or ThreadLocal so your event listeners know the damage came from this mod.
- Turn off vanilla sweep (`SweepAttackEvent`, verify it exists in 21.1.x) and vanilla crits (`CriticalHitEvent`). Your arcs replace both.
- Apply your multiplier in `LivingIncomingDamageEvent`.
- Reset `target.invulnerableTime = 0` before each hit. **Gotcha:** vanilla gives 10 ticks of i-frames, which would eat combos and multi-target hits.
- For mob attackers, which have no `Player#attack`, use `mob.doHurtTarget(target)` with the same context.

### 3.5 Defense

- `LivingShieldBlockEvent` (this is where SW's `MeleeBlockWeaponTrait` hooks in) handles shield items.
- `LivingIncomingDamageEvent` handles weapon parries, which aren't vanilla blocking.
- **Direction check:** compute the angle between the defender's look vector and the attacker or projectile. Cone half-angle by guard type: weapon ~60°, basic shield ~75°, tower ~90°.
- **Stamina cost** = the attacker profile's `stamina_damage` × the defender's guard multiplier, plus a bonus if the attacker has SW `SHIELD_BREACH`.
- **Order of events:** your listener runs first (`EventPriority.HIGH`) to decide whether the block happens. If it does, SS's own block handler runs normally, so energy shields, `IShieldBlockHandler` callbacks and tower durability keep working.

### 3.6 Networking (NeoForge 1.21.1 payload API)

Register handlers in `RegisterPayloadHandlersEvent` → `PayloadRegistrar`.
- C2S: `AttackIntent(type, pressed/released)`, `BlockIntent(down)`, `Feint`, `Kick`, `Special`, `Throw(charge)`.
- S2C: `CombatStateSync(entityId, phase, type, phaseTick, flags)` goes to the entity's trackers (`PacketDistributor.sendToPlayersTrackingEntityAndSelf`). Stamina syncs through the attachment's sync, or a small `StaminaSync` payload.
- Profiles reach the client through datapack registry sync. The client needs the timings for prediction.

### 3.7 Data-driven weapon profiles

- Profiles live in a **datapack registry** `steelclash:weapon_profile`, registered in `DataPackRegistryEvent.NewRegistry` with a network codec so they sync.
- Items are assigned profiles through a **NeoForge Data Map** `steelclash:weapon_profile` on items. That gives you tag/item matching, datapack overrides and sync without extra code.
- Fallback resolution: data map → SW `WeaponItemType` (via `SpartanWeaponryAPI.getWeaponClassification(item)`, which also covers SW addon weapons) → vanilla tag (`#minecraft:swords`, `#minecraft:axes`) → `fists`.

Example `data/steelclash/steelclash/weapon_profile/longsword.json`:
```json
{
  "archetype": "two_handed",
  "damage_type": "cut",
  "guard": { "cone": 60, "parry_ticks": 12, "stamina_mult": 1.0 },
  "attacks": {
    "slash":    { "windup": 9,  "release": 6, "recovery": 8,  "damage": 1.0, "stamina_damage": 15, "arc": "horizontal_wide", "max_targets": 3 },
    "overhead": { "windup": 11, "release": 5, "recovery": 9,  "damage": 1.2, "stamina_damage": 20, "arc": "vertical" },
    "stab":     { "windup": 8,  "release": 4, "recovery": 8,  "damage": 0.9, "stamina_damage": 12, "arc": "thrust", "reach_bonus": 0.5 }
  },
  "heavy": { "windup_mult": 1.6, "damage_mult": 1.5, "stamina_damage_mult": 1.5 },
  "riposte_windup_mult": 0.7,
  "hyper_armor_on_heavy": false,
  "special": "steelclash:lunge"
}
```

## 4. Spartan Weaponry integration (verified from the repo)

Compile against the **API package only**: `org.xiyu.spartanweaponryunofficial.api`. The port's docs say internals aren't stable. Call `SpartanWeaponryAPI.assertAPIVersion("steelclash", 15)` at init.

**WeaponItemType → archetype (default profiles to ship):**

| SW type | Archetype | Notes |
|---|---|---|
| DAGGER | dagger | Fast, short, low stamina damage. Strong backstab, since SW already has `backstab_damage_bonus`. |
| PARRYING_DAGGER | dagger | Has SW `BLOCK_MELEE`. Gets a longer parry window and cheaper parries. Meant to be paired with an offhand weapon. |
| LONGSWORD, KATANA, SABER | sword | Saber leans toward slashes, katana toward speed. |
| RAPIER | sword | Strong stab, weak slash. |
| GREATSWORD | two_handed | Slow, wide, hyper armor on heavies. |
| BATTLEAXE | axe (chop) | |
| BATTLE_HAMMER, WARHAMMER, FLANGED_MACE | blunt | High stamina damage. Warhammer stab = armor-piercing spike. |
| SPEAR, PIKE | polearm_thrust | Long stab, weak slash. Pike is very long and slow. |
| HALBERD, GLAIVE, SCYTHE | polearm | Strong overhead. |
| LANCE | lance | Mounted charge. |
| QUARTERSTAFF | staff | Blunt, fast, good guard. |
| THROWING_KNIFE, TOMAHAWK, JAVELIN | throwable | Melee profile plus SW's own throw on the throw key. |
| LONGBOW, HEAVY_CROSSBOW, BOOMERANG | — | Left as vanilla/SW behavior. |

**Traits** come from `IWeaponTraitContainer` (`hasWeaponTrait`, `getAllWeaponTraits`) together with the `WeaponTraits` constants:
- `SWEEP_1..3` → the slash arc's width and `max_targets`. Vanilla sweep itself is disabled.
- `REACH_*`, `HEAVY_*`, `LIGHTWEIGHT_*`, `QUICK_STRIKE` → handled through attributes. Confirm they're attributes and not mixin logic.
- `SHIELD_BREACH` → extra stamina damage against guards.
- `ARMOR_PIERCING` → keep SW's own handling, plus a bonus on stabs.
- `HAMMER_SLAM` → the hammer special. `KNOCKBACK` / `NAUSEA` → keep as-is.
- `BLOCK_MELEE` (`ModToolActions.MELEE_BLOCK`) → these weapons already "use" with `UseAnim.BLOCK`. Route them through your parry logic, and coordinate with `MeleeBlockWeaponTrait.onBlockEvent`, which cancels blocks against non-melee damage.
- `THROWABLE` → throw key.

**Watch out:**
- SW's `PlayerMixin` and `LivingEntityMixin` touch attack and armor internals; their `docs/mixin-risk.md` rates them high-risk. This is why the plan goes through vanilla `Player#attack` instead of fighting those mixins.
- SW already ships compat for **Shoulder Surfing** and **Footwork API**. Shoulder Surfing's third-person camera suits this style of combat, so support it rather than reinventing a camera.

## 5. Spartan Shields integration

API (`org.xiyu.spartanshieldsunofficial.api`):
- `ShieldTags.BASIC_SHIELDS`, `ShieldTags.TOWER_SHIELDS` and `ShieldTags.SHIELDS_WITH_BASH` → pick the guard type and whether kick becomes a bash.
- `ShieldType { BASIC, TOWER }`.
- `IShieldBlockHandler` → SS's block callback. It only fires for `LivingEntity` attackers when damage is ≥ 3.0. Don't cancel successful blocks, or energy shields and their callbacks break.

Rules:
- A shield guard is held, not timed. The parry window is shorter, and ripostes off a shield are configurable.
- Two-handed archetypes with a shield in the offhand can't raise the shield, or raise it with a heavy penalty (config).
- SS's own "alt shield bash" key is unbound by default (`-1`). Leave it unbound and drive the bash from your kick key.
- Vanilla needs ~5 ticks after you start using a shield before it actually blocks. Decide whether to keep that delay as the shield "raise time" or shorten it.
- Make SS **optional**. SS requires SW, so SS compat implies SW is present.

## 6. Animation, feedback, HUD

- **Animations:** [playerAnimator](https://github.com/KosmX/minecraftPlayerAnimator) (`player-animation-lib-forge 2.0.4+1.21.1`, the same library Better Combat uses) or the newer [Player Animation Library](https://modrinth.com/mod/player-animation-library) (`1.1.2+1.21.1-NeoForge`). Prototype both in M0. You need one animation per (archetype × attack type × phase), plus parry, stagger and kick. Author them in **Blockbench** with the animator export plugin.
- **Sync animation to gameplay:** drive animation playback speed from the actual phase tick counts, so an animation can never lie about timing.
- **One source of truth for swings:** derive each attack's arc keyframes (the hit-trace path) from the *same Blockbench animation file*. A small Gradle task or script samples the weapon bone at every tick and writes the `arc` data into the profile JSON. This copies the way Chiv 2 traces the actual weapon: what you see swinging is exactly what hits.
- **First person is required** (decided). Use playerAnimator's first-person arm rendering with first-person-specific animations. In Chiv 2's first person you mostly see the weapon arc across the screen, so author first-person animations separately from third-person ones for readability. This is the largest single art task, so it gets its own milestone (M4).
- **Camera feel:** small view sway during windups, a camera-roll tilt on heavy slashes, and a hit-stop freeze of a few frames. All of these can be turned down in the config for motion-sensitive players.
- **Hit feedback:** 1–2 ticks of client-side hit-stop, camera shake on heavy hits, spark particles and a distinct sound on parry, a "clank" when blocked.
- **HUD** (`RegisterGuiLayersEvent`): stamina bar, incoming-attack direction indicator, riposte window flash, hit marker.
- Your available `mc-asset` MCP tools can generate and pixelize the HUD sprites and icons.

## 7. PvE: mobs that fight like Chiv 2 bots (core feature)

Since PvE is the focus, mob combat is a core feature, not an add-on. The rule is **mobs play by the same rules as players**: same state machine, same profiles, same stamina, and they can be parried, disarmed, feinted and riposted.

### 7.1 Every melee mob gets a readable windup
Vanilla `MeleeAttackGoal` hits instantly when in range, which makes reactive parrying impossible. On `EntityJoinLevelEvent`, swap it for **`ClashMeleeGoal`**:
- **Humanoids** (zombie, husk, drowned, vindicator, piglin, piglin brute, wither skeleton, skeletons with melee weapons) get full weapon play. They use the profile of whatever they hold; SW's spawn-equipment mixins already hand mobs Spartan weapons. Their windups are animated with the same player animations, retargeted to the humanoid model.
- **Non-humanoids** (spider, wolf, hoglin, ravager, iron golem…) get a "lunge" attack spec with a generic telegraph (crouch or lean, sound cue, slight glow on hard difficulty). They can be parried and blocked like any other attack. Golems and ravagers count as heavies.
- Mobs we don't recognize from other mods keep vanilla behavior. A config list can opt them in.

### 7.2 Bot brain (`ClashBrain`, utility-scored decisions every few ticks)
- **Spacing:** circle at the edge of their weapon's reach, step back during their own recovery, close distance against archers and shield turtles.
- **Offense:** pick an attack type from the profile weights, combo after a hit, feint (and morph on hard difficulty), kick shield users or players who hold block too long, sprint-lunge from range.
- **Defense:** parry incoming attacks after a reaction delay (Easy ~400 ms, Normal ~280 ms, Hard ~200 ms, plus jitter). Ripostes are more likely on higher difficulty. They're stamina-aware: an exhausted mob backs off to regenerate.
- **Read the player:** if you feint a lot, they parry later. If you only use one attack type, they counter it. This is simple counting, not machine learning.
- **Group fights:** an **attack-token** system. Only N mobs (by difficulty: 1/2/3) may be in windup at once; the others circle and wait. This keeps a 1-vs-5 fight hard but readable, which is what makes Chiv 2 bot fights fun.

### 7.3 Training and content
- **Training dummy** (M2): idle / always block / attack every N ticks with a chosen type / random feints. Used for solo testing and as a player tutorial.
- **Soldier mobs** (M6): a humanoid footman, knight and archer that spawn on patrols and at pillager outposts, equipped from SW/SS tags. Without SW they use vanilla gear. They're the closest thing to Chiv 2 bots and the best showcase for the system.
- **Disarming works both ways:** draining a knight's stamina knocks its sword away, and you can pick it up.

### 7.4 Armed mobs (planned for M6, requested 2026-10-06)
Vanilla zombies almost never carry weapons, and Spartan Weaponry's own rates are low (`zombie_with_melee_spawn_chance_normal = 0.05`, `_hard = 0.25`). The bot brain shines when mobs fight with real weapons, so fighter mobs should spawn armed much more often:
- On `MobSpawnEvent.FinalizeSpawn`, give `#steelclash:fighters` humanoids a weapon with a configurable chance per difficulty (proposed: Easy 30%, Normal 50%, Hard 70%), and sometimes a shield and armor pieces too.
- Weapons come from data-driven loot pools (item tags or loot tables per mob type and difficulty), so packs can tune them. With Spartan Weaponry installed, the pools use its tags (`#spartan_weaponry_unofficial:weapons/longswords`, `.../spears`, ...); otherwise vanilla swords and axes. Material scales with difficulty (wood/stone → iron → diamond).
- Respect mobs that already got gear from vanilla or Spartan Weaponry (don't overwrite). Keep the vanilla drop chance for spawned gear low, so it isn't a loot fountain.
- Shields in the offhand make mobs block with them (M2 shield rules already apply to mobs).

### 7.5 More varied movement (requested 2026-10-06)
Fights currently look repetitive: every slash of an archetype is the same motion, and bots only circle and step in/out. Planned:

**Players and all fighters: attack variety**
- Several variants per attack type and archetype (e.g. 2–3 slashes, overheads and stabs), each with its own arc **and** matching pose clip. Variant choice: alternate on combos (Chivalry 2 alternates slash direction), random otherwise. Mirrored arcs come from the directional-slash backlog item, and variants extend `ArcSpec` to keyframe arcs instead of the three presets.
- Distinct heavy windups (not just an exaggerated light), and different riposte and counter motions.
- Footwork in the pose clips: step-in on stabs, pivot on slashes, recoil steps on stagger.

**Mobs: movement variety** (extends `ai/ClashSpacingGoal` and `ai/ClashBrain`)
- Footwork patterns instead of constant circling: hold, sidestep, backpedal, a feinted step in, and lunging in from just outside reach.
- Personality per mob type: zombies shamble straight in, vindicators rush and flank, skeletons with swords keep distance, piglin brutes press forward. Weighted by difficulty.
- Reacting with movement, not only parries: step back out of reach of a slow heavy, sidestep a stab, back off to regenerate stamina when low.
- Groups spread around the target (flanking positions) instead of bunching on one side; mobs waiting for an attack token pick open angles.
- Per-mob variation (stored random seed) so a group doesn't move in lockstep.

### 7.6 Timing HUD (requested 2026-10-06)
Players need to see how long their own actions last. Add a small timing indicator near the crosshair (next to the stamina bar, same style; scalable or hideable in the client config):
- **Heavy charge:** while holding an attack, a bar fills toward the point where it becomes a heavy (`HEAVY_HOLD_TICKS`), then shows the heavy's windup filling until release.
- **Windup → release → recovery:** a segmented bar for the current attack, so you can see when the blade goes live and when you can act again. The combo window lights up during recovery once a hit has landed.
- **Parry:** a draining bar for how long the raised parry lasts (`parry_ticks`), then the guard-recovery cooldown. The riposte window flashes after a successful parry (the stamina bar already flashes white; this makes its length visible).
- **Other timed states:** feint and morph availability (windup only), stagger and flinch duration, kick recovery, and the shield cooldown after a guard break.
- All of these are client-side reads of the predicted state machine (`phaseTick` / `phaseDuration` / `riposteTicks`), so the HUD costs no new networking.
- Optional (config): a small indicator over *enemies'* heads during their windup (attack type and remaining time), as an accessibility and learning aid. Off by default, to keep reading animations the core skill.

## 8. Milestones

Each milestone has a hard exit test.

| # | Scope | Exit criteria |
|---|---|---|
| **M0** Setup and spikes (2–3 days) *(done 2026-10-06, see docs/spikes.md)* | MDK and repo (see §9). SW, SS and the animator resolve through Modrinth Maven. Runs for client, client2 and server. **Spikes:** (a) does `SweepAttackEvent` exist in 21.1.x? (b) do SW reach/heavy traits apply as attributes? (c) read SS's `event/` handler: when does bash fire, and how does blocking order work? (d) playerAnimator first-person proof of concept. (e) Does cancelling the attack input break block mining? | All five spikes answered and written down in `docs/spikes.md`. |
| **M1** Core swing *(built 2026-10-06; automated tests pass, in-game checklist pending, see docs/testing.md)* | State machine, 3 attack types, input interception, server capsule tracing, damage through `Player#attack`, i-frame reset, debug renderer that draws capsules and phases. The state machine lives on `LivingEntity` from day one, so mobs can use it later. | Kill a zombie using all three types, and SW armor piercing and backstab still apply. Unit tests cover the state machine. |
| **M2** Defense and telegraphed mobs *(built 2026-10-06; automated tests pass, in-game checklist pending)* | Stamina attachment and HUD, weapon parry, riposte, directional cone, SS basic and tower shields, disarm, training dummy. **`ClashMeleeGoal` v1:** vanilla melee mobs wind up, then release, and can be parried. | Parry a zombie's telegraphed swing → riposte is faster. A tower shield blocks a skeleton's arrow from the front but not from the back. Draining a mob's stamina disarms it. |
| **M3** Mind games *(built 2026-10-06; automated tests pass, in-game checklist pending)* | Heavies, feint, morph, combo, flinch, hyper armor, kick and shield bash, counter, environment clank, sprint lunge, jump attack. | Scripted GameTests pass for each interaction, run against the dummy. |
| **M4** Feel and first person *(built 2026-10-06 with a changed approach, see docs/spikes.md "M4"; in-game look check pending)* | Blockbench animations (first and third person) with the arc-extraction script, retargeted to humanoid mobs, plus sounds, particles, hit-stop and camera sway. | Side-by-side comparison against Chiv 2 reference clips for each archetype. A playtester can predict which attack is coming from the windup alone. |
| **M5** Bot brain *(built 2026-10-06; automated tests pass, in-game feel check pending)* | `ClashBrain`: spacing, parry reaction by difficulty, feints and morphs, ripostes, kicking turtles, adapting to the player, attack tokens for groups. | 1v1 a Hard vindicator with a SW halberd and it feels like a Chiv 2 bot. A 1v4 zombie fight stays readable. |
| **M6** PvE content *(split: **M6a** built 2026-10-06 = timing HUD §7.6, armed mobs §7.4, mob movement §7.5; **M6b** built 2026-10-06 = attack variants, mirrored/alternating/turn-directed swings, heavy windup clips, two-handed swords; **M6c-1** built 2026-10-06 = specials (lunge/slam/sweep), throwing any weapon, damage types vs armour, mounted lance, tooltips; **M6c-2** built 2026-10-06 = brigand footman/knight/archer, patrols, night spawns, brigand camp structure; **M6c** the rest)* | **Timing HUD for heavies, parries and other timed actions** (see §7.6), **mobs spawn with gear far more often** (see §7.4), **more varied mob movement and attack variants** (see §7.5), soldier mobs (footman, knight, archer), patrols and outpost spawns, specials for each archetype (hammer slam, spear lunge…), throwing any weapon, mounted lance, cut/blunt/chop vs armor. | Per feature. |
| **Footwork and sustain** *(built 2026-10-06)* | Movement slowdown by phase (transient `MOVEMENT_SPEED` modifier, server and locally predicted; backpedal on the client), ducking (`duckHeight` 1.0: a crouching target's blade hitbox is capped, level slashes pass over), player health regeneration (5 s delay, 1 HP/s). Still open from the 2026-10-06 gap review: alternate weapon modes, projectile headshots, filling in timings from footage, and rules to check in Chiv 2 (holding a weapon parry, shield ripostes, feinting ripostes, special cooldowns). | `FootworkGameTests` (mutation-checked). |
| **Turning in swings** *(built 2026-10-06)* | Accels and drags emerge from view-relative tracing; turn cap (common `turnCapDegreesPerSecond`, 360) enforced on the server's traced view and the client camera; bots accel/drag on purpose (`BotSkill.swingTrickChance`: Easy 0, Normal 0.15, Hard 0.35); attack side from strafing (client `sideFromMovement`). | `TurnGameTests` (mutation-checked). |
| **M7** Multiplayer *(built 2026-10-06: target rewind for lagged attackers, parry grace for lagged defenders, client catch-up on corrections, `[network]` config; automated tests pass, clumsy check pending)* | Client prediction and reconciliation, lag compensation, latency tolerance config. Lower priority because PvE singleplayer runs at ~0 latency. | Co-op PvE at 150 ms simulated latency (clumsy) still allows reliable parries. |
| **M8** Release *(built 2026-10-06: MIT, 0.1.0-beta, config screen with every option named and commented, tutorial (`/steelclash_help`, first-join hint, craftable dummy), no-Spartan GameTest run, compat matrix, player guide, store page draft, CI artifacts and tag release workflow; publishing is manual)* | Config polish, compat matrix, wiki and in-game tutorial (the dummy), Modrinth/CurseForge pages, CI artifacts. | Public beta. |

## 9. Making the mod-making process better

### Project hygiene
- **Move the project off OneDrive.** `Skrivebord` is a OneDrive-synced folder. Gradle `build/`, `.gradle/` and `run/` churn thousands of files, which causes sync spam, "file in use" lock errors during builds and corrupted git indexes. Use something like `C:\dev\steelclash`.
- `git init` on day one, push to GitHub, and add a **GitHub Actions** workflow that runs `./gradlew build runGameTestServer`.
- Start from the official **NeoForge MDK (ModDevGradle)** for 1.21.1, with Parchment mappings for readable parameter names.

### Dependencies without pain
Use Modrinth Maven, scoped so it can't shadow other artifacts:
```gradle
repositories {
    exclusiveContent {
        forRepository { maven { url = "https://api.modrinth.com/maven" } }
        filter { includeGroup "maven.modrinth" }
    }
    maven { url = "https://maven.kosmx.dev/" }
}
dependencies {
    implementation "maven.modrinth:spartan-weaponry-unofficial:<version>"
    implementation "maven.modrinth:spartan-shields-unofficial:<version>"
    implementation "dev.kosmx.player-anim:player-animation-lib-forge:2.0.4+1.21.1"
}
```
- Declare SW and SS as `type = "optional"`, `ordering = "AFTER"` in `neoforge.mods.toml`.
- Declare Better Combat and Epic Fight as `type = "incompatible"`, since they take over the same input and attack flow.

### Fast iteration loop
- **Hot swap:** run the client on JetBrains Runtime 21 with `-XX:+AllowEnhancedClassRedefinition`. You can then change method bodies and add methods without restarting, which is ideal for tuning combat.
- **Tune with data, not code:** edit profile JSON, run `/reload`, and swing again.
- **Make config sync and reload live:** stamina rates, cones and windows should be adjustable mid-fight.
- **Two clients plus a server in one click:** add ModDevGradle runs `client2` (`--username Dev2`) and `server`, with `online-mode=false` on the dev server. Most of this mod is about two players interacting.
- **Debug overlay** (`/steelclash debug`): draw swept capsules, block cones and phase names above heads. Log each exchange (attack, defense, outcome, ticks) to CSV for balancing.
- **Latency testing:** use *clumsy* (Windows) to add 100–200 ms of lag and jitter. Parry timing has to stay fair under real ping.

### Testing
- Keep `core/` free of Minecraft classes, so **JUnit** tests of the state machine, stamina and arc math run in under a second. ModDevGradle's `unitTest` support can also run tests that need MC classes.
- Use **NeoForge GameTests** with mock players or training dummies for each interaction: parry → riposte, feint → parry punish, guard break, tower vs arrow. Run them in CI.
- Keep a manual test checklist like SW's `docs/manual-test-checklist.md`.

### Keep mixins rare and documented
- Prefer events: `InteractionKeyMappingTriggered`, `MouseScrollingEvent`, `RenderGuiLayerEvent`, `LivingIncomingDamageEvent`, `LivingShieldBlockEvent`, `CriticalHitEvent`, `SweepAttackEvent`.
- When a mixin is unavoidable, log it in `docs/mixin-risk.md` the way SW does, especially anything touching `Player#attack` or armor, which SW also mixes into.

### Working with Claude Code on this
- Add a project `CLAUDE.md` with the build and run commands, the package layout above, and rules: "compile-check with `./gradlew compileJava` after edits", "only use the SW/SS `api` packages".
- Run **graphify** on the SW and SS sources so you (and Claude) can query their internals, such as the SS event handler and the SW trait callbacks, without re-reading the repos.
- Use the `mc-asset` MCP tools for HUD sprites and item and icon textures.
- Optionally, add a Stop hook that runs `./gradlew compileJava -q` so broken code is flagged right away.

### Reference material
- Study Better Combat (OBB hit detection and the playerAnimator integration) and Epic Fight (a full combat-state overhaul). Check their licenses before reusing any code.
- Record your own Chiv 2 clips at 60 fps and step through them frame by frame to get windup and release timings for each weapon class.

## 10. Fidelity checklist

All decisions are recorded under "Decisions locked in" at the top. Before M4, record Chiv 2 reference footage at 60 fps for each archetype and fill in this table from frame-stepping. Then tune the profiles until the in-game timings match within ±1 tick:

| Archetype | Slash W/R/R (ms) | Overhead W/R/R | Stab W/R/R | Parry window | Riposte windup | Stamina dmg | Notes |
|---|---|---|---|---|---|---|---|
| dagger | | | | | | | |
| sword (1h) | | | | | | | |
| two_handed | | | | | | | |
| axe | | | | | | | |
| blunt | | | | | | | |
| polearm / polearm_thrust | | | | | | | |
| shield guard | — | — | — | | | | |

Mechanics to double-check against the game rather than from memory: whether ripostes can be feinted, the exact counter window, how long a weapon parry can be held before it drops, and whether shield blocks allow a riposte.

## 11. Backlog (ideas for later)

| Idea | Source | Notes |
|---|---|---|
| **Blade twist (edge leads the cut)** *(built for players 2026-10-06: computed from the arc's direction of travel via `ArcPath.edgeAngle`, so every variant and mirrored swing gets it; rolled about Z in the hand frame (verified from playerAnimator's bytecode: Z·Y·X after vanilla's -90° hand turn); client config `bladeTwist` (scale / sign) and `bladeTwistAxis`; mobs still pending the item-render mixin)*: roll the held weapon around its own length per attack (flat for slashes, upright for overheads). | User, 2026-10-06 | Players: a `rightItemTwist` part in the pose clips. Mobs: needs a second small client mixin on item-in-hand rendering. Probably needs one in-game sign check, like `weaponGripPitch`. |
| **Two-handed grip for one-handed swords + directional slashes:** with an empty offhand, hold the sword in both hands, and pick the slash direction (left→right or right→left) from which way you're turning when you attack. | User, 2026-10-06 | Grip: an override of the archetype's `grip` when the offhand is empty (the solved two-hand pose already exists), maybe with slightly different stats (stamina, guard cone). Direction: mirror the horizontal arc (negate keyframe yaw) based on the sign of the yaw change around the attack press. The client sends the choice with `AttackInputPayload`, and the server mirrors the arc it traces, so hits still match visuals. Chivalry 2 also alternates slash direction on combos, so mirrored arcs are useful beyond this feature. |
| **Dual wielding:** a weapon in each hand. One mouse button swings each hand's weapon, and parry moves to another key (Alt or middle click, rebindable). Without dual wielding, parry stays on right click. Which button maps to which hand should be a preference. | User, 2026-10-06 | Needs: an offhand weapon profile lookup, a mirrored arc for the left hand (same mirroring as directional slashes), and the off arm solved from the arc like the weapon arm. Probably one shared state machine where each attack records which hand swings (simpler than two machines, and it keeps one windup at a time, as in Chivalry 2). Input: a "dual wield" mode active only when both hands hold profiled weapons; parry key defaults to Alt in that mode. Middle click is vanilla pick-block, so it conflicts in creative unless rebound. Balance: probably lower damage or stamina per hand. The Spartan parrying dagger in the offhand could also mean "offhand parries better" instead of a second attack. |
| **Gesture attacks (experimental option)** *(built 2026-10-06: measures the view's turn since the press instead of raw cursor delta, so the camera isn't frozen; threshold 5°, 4-tick window; client config `gestureAttacks`, `gestureThreshold`, `gestureWindowTicks`; customizable per direction (`gestureLeft/Right/Up/Down`: slash from left/right, overhead, stab, kick, none) and per key (`gestureKeys`))*: hold left click and move the mouse in a direction to pick the attack. Drag sideways to slash from that side, drag up for an overhead, drag down (or a short forward flick) for a stab. Off by default; client config `gestureAttacks`. | User, 2026-10-06 | Input: while LMB is held, accumulate mouse delta (from `InputEvent.MouseButton`/`ViewportEvent`, or better a raw cursor delta) *before* it turns the camera (freeze or damp camera turning during the gesture, configurable). Once the delta passes a threshold (e.g. 20–40 px), classify the direction into a sector → attack type plus side, and start the attack. Releasing LMB before the threshold = a default slash. Conflicts with today's "hold LMB = heavy": in gesture mode, heavy becomes "keep holding after the gesture fires" (same `HEAVY_HOLD_TICKS` rule, counted from when the gesture fired), so both coexist. Needs mirrored arcs (directional-slash backlog item) for left vs right slashes, and the side travels in `AttackInputPayload`. Sensitivity, dead zone and sector angles go in the config; the timing HUD could show the chosen direction. Keep scroll and Mouse 4/5 attacks working alongside. |

