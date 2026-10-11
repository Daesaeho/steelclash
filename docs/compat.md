# Compatibility and pack configuration

Steel Clash 0.3.2-beta targets Minecraft 1.21.1 and NeoForge 21.1.252+. Install it on **both client and server**.

## Mods

| Mod | Status | Notes |
|---|---|---|
| Player Animation Library 1.1.6+ | Required | Player swing animations in first and third person. |
| Spartan Weaponry Unofficial 1.2.3+ | Optional, integrated | Weapon profiles, vanilla trait dispatch, mob gear and brigand loot; GameTests with/without it. |
| Spartan Shields Unofficial 1.0.0+ | Optional, integrated | Basic/tower guard cones of 150°/180°; tower arrow blocking from the front; GameTests with/without it. |
| Better Combat / Epic Fight | Incompatible | Both replace melee attacks; the game refuses to load with either alongside Steel Clash. |
| Other weapon mods | Unmapped items retain vanilla combat | Assign a profile through the item data map below. |
| Other shield mods | Usually works | `ItemAbilities.SHIELD_BLOCK` receives basic shield cone/stamina rules. |
| Mob AI mods | Case by case | Remove conflicting mobs from `#steelclash:fighters` if another mod owns their melee goals. |
| Sodium 0.8.13, NeoForge | Bounded live controls verified | Actual Sodium and `-PnoSodium` torch/shield/block-riposte scenes passed; [scope and evidence](archive/testing-history.md#verified-renderer-and-live-opponent-checkpoint-2026-10-10). |
| Shaders / other renderers | Further acceptance required | PAL and vanilla model hooks are used; altered renderer/model stacks need their own checks. See [mixin risks](mixin-risk.md). |

### Candidates under review

These candidates have source/artifact reviews but no implemented or playtest-certified Steel Clash adapter.

| Candidate | Finding | Planned decision |
|---|---|---|
| PAL More Rotation | Dev-only prototype; runtime callback order needs a gated item bridge | [Initial item/mesh evidence](archive/pal-more-prototype-20261011.md); full step-3 matrix and adoption remain open. |
| Countered's Accurate Hitboxes | Reviewed jar changes client picking, not the authoritative server sweep | [Step 5](next-combat-animation-plan.md#5-add-accurate-server-collision-in-two-stages): server-owned contact metadata and optional anatomical collision. |
| CPM / CPM Animator Utils | Cosmetic rig and authoring tools; no automatic clip conversion or collision | [Step 6](next-combat-animation-plan.md#6-evaluate-cpm-after-the-default-rig-is-stable): bone ownership, lifecycle and fallback prototype. |
| BodyHealth / Legendary Survival Overhaul | Injury systems require one damage/headshot/death owner | Deferred by user direction. |

Pinned sources, inspected versions and the PDF comparison are preserved in the
[dated compatibility review](archive/kinematics-compatibility-review.md). Source inspection establishes no runtime support.

### Weapon archetypes

| Archetype | Vanilla | Spartan Weaponry |
|---|---|---|
| sword | swords | longsword, katana, saber |
| rapier | | rapier |
| dagger | | dagger, parrying dagger, throwing knife |
| axe | axes | battleaxe, tomahawk |
| blunt | mace | battle hammer, warhammer, flanged mace, club |
| spear | trident | spear, pike, lance, javelin |
| polearm | | halberd, glaive, scythe |
| two_handed | | greatsword |
| staff | | quarterstaff |
| claw, beast, heavy_beast | | unarmed mobs such as zombies, spiders and ravagers |

Two-handed archetypes (`two_handed`, `polearm`, `spear`) cannot raise an offhand shield.

## Config

The server's `steelclash-common.toml` controls stamina, defense, offense, mobs and networking.
`steelclash-client.toml` controls HUD, camera motion, hit-stop, scroll attacks, telegraph labels and tutorial hints.
Edit both through **Mods → Steel Clash → Config**.

## For pack makers

### Weapon profiles

Profiles live at `data/<ns>/steelclash/weapon_profile/<name>.json` in registry `steelclash:weapon_profile`.
Start from a [built-in profile](../src/main/resources/data/steelclash/steelclash/weapon_profile/sword.json).

| Fields | Meaning |
|---|---|
| `archetype`, `reference_attack_speed`, `speed_scaling` | Family and attack-speed scaling. |
| `attacks.slash`, `.overhead`, `.stab` | Each attack's timing, damage, arc and contact settings below. |
| `guard.parry_ticks`, `.recovery`, `.cone`, `.stamina_mult` | Defense window, recovery, angle and stamina scaling. |
| `riposte_windup_mult` | Riposte startup fallback when an attack omits `riposte_ms`. |
| `heavy.windup_extra_ms`, `.recovery_extra_ms` | Added heavy startup/recovery; without `windup_extra_ms`, `windup_mult` is the fallback. |
| `heavy.damage_mult`, `.stamina_damage_mult` | Heavy damage and guard-stamina scaling. |
| `hyper_armor_on_heavy`, `damage_type`, `special` | Heavy protection, default `cut`/`blunt`/`chop`, and special attack. |

Per-attack fields:

| Fields | Meaning |
|---|---|
| `windup`, `release`, `recovery` | Tick-based durations. |
| `windup_ms`, `release_ms`, `recovery_ms` | Millisecond overrides; each overrides its tick equivalent, and units can be mixed. |
| `combo_ms`, `riposte_ms` | Chained/riposte windup; combo defaults to ordinary windup, riposte to ordinary windup × `riposte_windup_mult`. |
| `damage`, `reach_bonus`, `stamina_damage`, `stamina_cost`, `damage_type` | Damage multiplier, reach adjustment, guard damage, cost and damage-type override. |
| `arc`, `variants`, `max_targets` | Swing shape/width, optional variant keyframes and target limit. |
| `contact` | `cleave`, `thwack` or `cleave_on_kill`; unset blunt attacks use `cleave_on_kill`, other attacks cleave. |
| `thwack_ms` | Recovery measured from contact; defaults to ordinary recovery. |

### Item and entity mappings

An item profile mapping at `data/<ns>/data_maps/item/weapon_profile.json`:

```json
{"values": {"mymod:big_sword": {"profile": "steelclash:two_handed"}}}
```

| Purpose | Data path or tag | Value / notes |
|---|---|---|
| Fighting mobs | `#steelclash:fighters` | Telegraphed attacks and bot brain. |
| Unarmed mob profile | `data_maps/entity_type/mob_profile.json` | `{"profile": "steelclash:claw"}` |
| Bot personality | `data_maps/entity_type/bot_style.json` | `shambler`, `duelist`, `rusher`, `brute`, `skirmisher` |
| Armed spawns | `#steelclash:armable` | Difficulty-dependent chances in common config. |
| Gear pools | `#steelclash:mob_weapons/tier_1`, `tier_2`, `tier_3`; `#steelclash:mob_shields`; `#steelclash:soldier_weapons/footman`, `knight` | Optional-mod entries need `"required": false`. |
| Archer sidearms | `#steelclash:sidearm_users`; `#steelclash:mob_sidearms/tier_1`, `tier_2`, `tier_3` | Skeleton/stray/bogged bow or crossbow users; saved sidearm from the difficulty tier, controlled by `sidearmChance`. |
| Brigand camps | `worldgen/structure/brigand_camp.json`, `structure_set/brigand_camps.json`, `#steelclash:has_structure/brigand_camp` | Remove the structure set to disable camps. |
| Soldier night spawns | `neoforge/biome_modifier/soldier_spawns.json` | Override with an empty `spawners` list to disable. |
