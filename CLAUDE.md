# Steel Clash

Chivalry 2-style melee combat for Minecraft **1.21.1 / NeoForge 21.1.x / Java 21**, with optional Spartan Weaponry and Spartan Shields integration. PvE-focused: mobs fight with the same rules as players.

- Design and milestones: `docs/PLAN.md`. Read the "Decisions locked in" section before changing behavior.
- Verified facts about NeoForge and the Spartan mods: `docs/spikes.md`. Add new findings there.
- Weapon tuning: `src/main/resources/data/steelclash/steelclash/weapon_profile/*.json` (timings in ticks); item → profile mapping: `data/steelclash/data_maps/item/weapon_profile.json`, then Spartan weapon type (`compat/SpartanWeaponryCompat`), then `#minecraft:swords`/`#minecraft:axes`.
- Use the `minecraft-modding` and `minecraft-testing` skills for loader and test patterns. Their 1.21.x references apply; ignore the 26.x sections.

## Commands (Git Bash from the repo root; use `gradlew.bat` in PowerShell)
- Compile check after every change: `./gradlew compileJava -q`
- Unit tests (Minecraft-free core): `./gradlew test`
- Full build: `./gradlew build`
- Game: `./gradlew runClient` (user Dev1), `./gradlew runClient2` (Dev2, separate game dir), `./gradlew runServer`
- Game tests: `./gradlew runGameTestServer` (GameTests live in `com.steelclash.gametest`; arena structure `data/steelclash/structure/arena.nbt`)
- GameTests: entities stand at relative y = 2 (floor is y = 1); see `gametest/TestSupport`.
- In game: `/steelclash_debug` toggles the swing debug view. Manual checklist: `docs/testing.md`

## Rules
- `com.steelclash.core` must not import `net.minecraft.*` or `net.neoforged.*`. All combat rules live there and are unit-tested.
- Only reference Spartan Weaponry/Shields classes from `com.steelclash.compat.*`, and only behind `Compat.X.isLoaded()`. Only use their `api` packages (`org.xiyu.spartanweaponryunofficial.api`, `org.xiyu.spartanshieldsunofficial.api`).
- Prefer NeoForge events to mixins. Every mixin gets an entry in `docs/mixin-risk.md` (target, why, conflict risk).
- Weapon tuning lives in datapack JSON (weapon profiles), not Java constants.
- Damage from our swings goes through vanilla `Player#attack` / `Mob#doHurtTarget`. That keeps Spartan traits and enchantments working. Reset `invulnerableTime` before each hit.
- Never cancel `LivingDamageEvent.Pre` for shield users: Spartan Shields hooks Spikes, Payback and energy-shield costs there.
