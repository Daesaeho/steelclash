# Steel Clash

Chivalry 2-style melee combat for Minecraft 1.21.1 (NeoForge). Attacks have a windup, release and recovery and come from a direction. Parry them, riposte, feint, morph, counter, kick through guards, and manage your stamina, or you'll be disarmed. Mobs fight by the same rules, with a bot brain that spaces, parries, feints and takes turns in groups.

**Status:** public beta (0.1.0-beta). PvE first; co-op multiplayer is lag-compensated.

## Requirements

| | |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.252+ |
| Required | [Player Animation Library](https://modrinth.com/mod/player-animation-library) 1.1.6+ |
| Optional | [Spartan Weaponry (Unofficial)](https://modrinth.com/mod/spartan-weaponry-unofficial) 1.2.3+, [Spartan Shields (Unofficial)](https://modrinth.com/mod/spartan-shields-unofficial) 1.0.0+ |
| Incompatible | Better Combat, Epic Fight (both replace melee) |

Install on both client and server. See [docs/compat.md](docs/compat.md) for details.

## Default controls

| Key | Action |
|---|---|
| Left mouse | Slash. Sides alternate every swing, as in Chivalry 2; turn while attacking to pick the side. |
| Right mouse | Parry (raises an offhand shield; still opens doors, chests and so on) |
| Mouse 5 / scroll up | Overhead |
| Mouse 4 / scroll down | Stab |
| *Hold* an attack key | Heavy attack |
| X | Feint (during a windup) |
| Z | Kick / shield bash |
| R | Weapon special |
| G | Throw weapon |

Prefer choosing the side yourself? Set the client config `controlScheme` to `TWO_SLASH_KEYS`: left click slashes right to left, right click slashes left to right, and parry moves to middle click (the keys are rebound for you). Experimental: with `gestureAttacks` on, hold left click and drag toward where the attack should come from (left, right, up = overhead, down = stab). Everything is rebindable under Controls. In game, `/steelclash_help` lists your bindings.

## Learn to play

- [Player guide](docs/guide.md): every mechanic, the mobs, the training dummy and the HUD.
- Craft a **Training Dummy** (carved pumpkin over a hay bale, with sticks) to practise parries and ripostes.
- Settings: *Mods → Steel Clash → Config*. Server rules are in `steelclash-common.toml`; the HUD, camera and controls feel are in `steelclash-client.toml`.

## For pack makers

Weapons, mob rosters and mob gear are all data-driven: weapon profiles, item and entity data maps, and tags. See [docs/compat.md](docs/compat.md#for-pack-makers).

## Building

```
./gradlew build                        # jar + unit tests (combat core)
./gradlew runGameTestServer            # in-game tests, with the Spartan mods
./gradlew runGameTestServer -PnoCompat # in-game tests without them
./gradlew runClient / runClient2       # two dev clients (Dev1, Dev2) for LAN testing
```

Developer docs: [plan](docs/PLAN.md), [findings](docs/spikes.md), [manual test checklists](docs/testing.md).

## License

[MIT](LICENSE). Steel Clash is a fan project inspired by Chivalry 2; it is not affiliated with Torn Banner Studios.
