# Compatibility

Steel Clash 0.3.2-beta, Minecraft 1.21.1, NeoForge 21.1.252+. Install it on **both** the client and the server.

## Mods

| Mod | Status | Notes |
|---|---|---|
| Player Animation Library 1.1.6+ | **Required** | Swing animations in first and third person. Other mods animating the player through it layer cleanly with ours. |
| Spartan Weaponry (Unofficial) 1.2.3+ | Optional, integrated | Each Spartan weapon type maps to an archetype (table below). Its traits (armour piercing, backstab, reach and so on) still apply, because hits go through vanilla attack code. Its weapons join mob gear pools and the brigand loot. Tested by GameTests with and without it. |
| Spartan Shields (Unofficial) 1.0.0+ | Optional, integrated | Basic shields block in a 150° cone and tower shields in a 180° cone; towers stop arrows from the front only. The parry key raises them. Tested with and without it. |
| Better Combat | **Incompatible** | Both replace melee attacks; the game refuses to load with both. |
| Epic Fight | **Incompatible** | Same. |
| Other weapon mods | Works, unmapped | Items without a weapon profile keep vanilla combat. Add a profile through the item data map (below). |
| Other shield mods | Usually works | Anything that performs `ItemAbilities.SHIELD_BLOCK` gets the basic shield cone and stamina rules. |
| Mob AI mods | Case by case | Mobs in `#steelclash:fighters` get a spacing goal and the bot brain on top of their goals. Mods that replace a mob's melee goal may fight it; take the mob out of the tag. |
| Sodium 0.8.13 (NeoForge) | Works | Tested: the dev runs include it (`-PnoSodium` leaves it out), the pose sheet renders identically with it, and all GameTests pass with it loaded. |
| Shaders / other renderers | Expected to work | Steel Clash renders through Player Animation Library and vanilla model hooks. One mixin, on `LivingEntityRenderer`, poses mob models: after `setupAnim` (arms, legs, head) and at the end of `setupRotations` (whole-body lean and twist). |

### Weapon archetypes

| Archetype | Vanilla | Spartan Weaponry |
|---|---|---|
| sword | swords | longsword, katana, saber |
| rapier | | rapier (fast, light stabs) |
| dagger | | dagger, parrying dagger, throwing knife |
| axe | axes | battleaxe, tomahawk |
| blunt | mace | battle hammer, warhammer, flanged mace, club |
| spear | trident | spear, pike, lance, javelin |
| polearm | | halberd, glaive, scythe |
| two_handed | | greatsword |
| staff | | quarterstaff |
| claw, beast, heavy_beast | | used by unarmed mobs (zombie hands, spiders, ravagers…) |

Two-handed archetypes (two_handed, polearm, spear) can't raise an offhand shield.

## Config

The common config (`steelclash-common.toml`, the server's copy counts) has sections for stamina, defense, offense, mobs and network. The client config (`steelclash-client.toml`) covers the HUD, camera motion, hit-stop, scroll attacks, telegraph labels and the tutorial hint. Both can be edited in game under *Mods → Steel Clash → Config*.

## For pack makers

| What | Where | Format |
|---|---|---|
| Weapon profiles | `data/<ns>/steelclash/weapon_profile/<name>.json` (datapack registry `steelclash:weapon_profile`) | `archetype`, `reference_attack_speed`, `speed_scaling`, `attacks` (`slash`/`overhead`/`stab`, each with `windup`/`release`/`recovery` in ticks or, more precisely, `windup_ms`/`release_ms`/`recovery_ms` in milliseconds (milliseconds win where both are given, and you can mix them), `damage` multiplier, `arc` shape and width, optional `variants` keyframes, `max_targets`, `contact` (`cleave`, `thwack` or `cleave_on_kill`; unset, blunt attacks use `cleave_on_kill` and the rest cleave), `thwack_ms` (recovery after a thwack, from the contact; defaults to the normal recovery), `combo_ms` (windup when the attack is chained out of the previous attack's recovery; defaults to the normal windup), `riposte_ms` (windup as a riposte; defaults to the windup times `riposte_windup_mult`), `reach_bonus`, `stamina_damage`, `stamina_cost`, `damage_type`), `guard` (`parry_ticks`, `recovery`, `cone`, `stamina_mult`), `riposte_windup_mult`, `heavy` (`windup_extra_ms` and `recovery_extra_ms` added to the attack's windup and recovery, or, without `windup_extra_ms`, `windup_mult`; plus `damage_mult` and `stamina_damage_mult`), `hyper_armor_on_heavy`, `damage_type` (`cut`/`blunt`/`chop`), `special`. Copy one of the built-in profiles in `data/steelclash/steelclash/weapon_profile/` as a starting point. |
| Item → profile | `data/<ns>/data_maps/item/weapon_profile.json` | `{"values": {"mymod:big_sword": {"profile": "steelclash:two_handed"}}}` |
| Which mobs fight | `#steelclash:fighters` (entity tag) | Telegraphed, parryable attacks and the bot brain. |
| Mob's unarmed profile | `data_maps/entity_type/mob_profile.json` | `{"profile": "steelclash:claw"}` |
| Mob personality | `data_maps/entity_type/bot_style.json` | `shambler`, `duelist`, `rusher`, `brute`, `skirmisher` |
| Mobs that spawn armed | `#steelclash:armable` | Chances per difficulty are in the mobs config. |
| Mob gear pools | `#steelclash:mob_weapons/tier_1`, `tier_2`, `tier_3`, `#steelclash:mob_shields`, `#steelclash:soldier_weapons/footman`, `knight` | Item tags; entries from optional mods need `"required": false`. |
| Archer sidearms | `#steelclash:sidearm_users` (entity types: skeleton, stray, bogged), `#steelclash:mob_sidearms/tier_1`, `tier_2`, `tier_3` (items: Spartan Weaponry daggers) | A sidearm user spawned with a bow or crossbow also gets a sidearm from the tier its difficulty rolls; it's saved with the mob. Chance: `sidearmChance`. |
| Brigand camp | `worldgen/structure/brigand_camp.json`, `structure_set/brigand_camps.json`, biome tag `#steelclash:has_structure/brigand_camp` | Remove the structure set to disable camps. |
| Soldier night spawns | `neoforge/biome_modifier/soldier_spawns.json` | Override it with an empty `spawners` list to disable them. |
