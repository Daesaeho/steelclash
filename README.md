# Steel Clash

Chivalry 2-style melee combat for Minecraft 1.21.1 (NeoForge). Attacks have a windup, release and recovery and come from a direction. Parry them, riposte, feint, morph, counter, kick through guards, and manage your stamina, or you'll be disarmed. Mobs fight by the same rules, with a bot brain that spaces, parries, feints and takes turns in groups.

**Status:** public beta (0.2.0-beta). PvE first; co-op multiplayer is lag-compensated.

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
./gradlew runGameTestServer -Pbench    # combat benchmark scenes; reports in run-gametest/steelclash-bench/
```

Developer docs: [plan](docs/PLAN.md), [findings](docs/spikes.md), [manual test checklists](docs/testing.md).

## Releasing

Every push and pull request is built and tested by GitHub Actions (`build.yml`); the jar is attached to the run as an artifact.

To publish a version:

1. Set `mod_version` in `gradle.properties` and add a `# <version>` section at the top of `CHANGELOG.md`.
2. Commit, then either push a matching tag (`git tag v0.2.0-beta && git push origin v0.2.0-beta`) or start **Actions > Release > Run workflow**, which creates the tag itself.

`release.yml` then runs every test and creates the GitHub release, with the jar and that version's changelog section. Versions with `alpha` or `beta` in the name are marked as pre-releases. A tag that doesn't match `mod_version` fails the run. Once `MODRINTH_ID` / `CURSEFORGE_ID` (repository variables) and `MODRINTH_TOKEN` / `CURSEFORGE_TOKEN` (secrets) are set, the same run also uploads to Modrinth and CurseForge.

## License

[Steel Clash License](LICENSE), source available. You may play it anywhere, put it in modpacks and publish forks or modified versions, as long as you credit Daesaeho (forks under a different name). Re-uploading the mod itself, or a copy with only trivial changes, to Modrinth, CurseForge or elsewhere is not allowed. Versions up to commit 027c479 were published under MIT. Steel Clash is a fan project inspired by Chivalry 2; it is not affiliated with Torn Banner Studios.
