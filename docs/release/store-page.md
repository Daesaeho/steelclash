# Store page (Modrinth / CurseForge)

Drafts to paste when creating the projects. Nothing here is published automatically: `release.yml` uploads files only after you create the projects and add `MODRINTH_ID` / `CURSEFORGE_ID` (repository variables) and `MODRINTH_TOKEN` / `CURSEFORGE_TOKEN` (secrets).

## Basics

| Field | Value |
|---|---|
| Name | Steel Clash |
| Slug | `steel-clash` (check it's free) |
| Summary (Modrinth, max 256 chars) | Chivalry 2-style melee: directional attacks with windups, parries, ripostes, feints, stamina and disarms, and mobs that fight by the same rules. Optional Spartan Weaponry & Shields integration. |
| Categories | Modrinth: `adventure`, `equipment`, `game-mechanics`, `mobs`. CurseForge: Adventure and RPG, Armor/Tools/Weapons, Mobs. |
| Environment | Client: required. Server: required. |
| Loader / versions | NeoForge, 1.21.1 |
| License | MIT |
| Dependencies | Player Animation Library (required); Spartan Weaponry Unofficial, Spartan Shields Unofficial (optional); Better Combat, Epic Fight (incompatible) |
| Release channel | Beta (0.1.0-beta) |
| Source / issues | Link the GitHub repo once it's public. |

## Gallery (to capture)

1. A first-person parry of a brigand knight's overhead, with the riposte window visible on the timing bar.
2. A 1v3 against armed zombies, showing them taking turns.
3. A third-person duel between two players.
4. The brigand camp at dusk.
5. The training dummy in spar mode with `/steelclash_debug` arcs.
6. A short clip (GIF or YouTube): feint, then morph to a stab, then the kill.

## Description

```markdown
# Steel Clash

**Melee that rewards reading your opponent.** Steel Clash brings Chivalry 2's combat to Minecraft. Every swing has a windup you can see and a direction it comes from, and every swing can be answered.

## Fight
- **Four directional attacks:** two slashes, an overhead and a stab. Hold any of them for a **heavy**.
- **Feint** a windup, or **morph** it into a different attack.
- **Combo** after a hit; **lunge** off a sprint; **jump attack** from above.
- **Parry** just before the blade lands, then **riposte** with a lightning-fast reply.
- **Counter** an attack by answering with the same one.
- **Kick** through a turtle's guard.
- **Stamina** matters: parry on an empty bar and your weapon goes flying.
- Weapon **specials** (lunge, slam, sweep), **throw any weapon**, **couched lance** charges from horseback, and **cut / blunt / chop** damage against armour.

## Enemies that duel
Zombies, skeletons, piglins, vindicators and more telegraph their attacks and can be parried. With the bot brain they space and circle, parry by difficulty, feint, riposte, kick shield-turtles, adapt to your habits, and **take turns** in groups so a 1v4 stays fair.

- Mobs carry real weapons far more often.
- **Brigand footmen, knights and archers** patrol the countryside at night and hold **camps** full of loot.

## Learn it
- Craft a **training dummy** with passive, parry, attack and spar modes.
- Type `/steelclash_help` for your controls.
- A **timing bar** shows heavy charge, parry and riposte windows.
- An optional **telegraph label** helps you learn enemy wind-ups.

## Works with
- **Spartan Weaponry (Unofficial):** every weapon type gets its own moveset (rapiers stab fast, halberds sweep, hammers crush armour). Its traits still apply.
- **Spartan Shields (Unofficial):** tower shields block arrows from the front only.
- **Co-op:** lag compensation means high-ping players still parry reliably.
- Everything is **data-driven**: weapon profiles, mob rosters and gear pools are datapack-configurable.

**Not compatible with** Better Combat or Epic Fight (they replace the same systems).

*Fan project inspired by Chivalry 2; not affiliated with Torn Banner Studios.*
```
