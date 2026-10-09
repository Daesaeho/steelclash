# Steel Clash

Chivalry 2-style melee combat for Minecraft **1.21.1 / NeoForge 21.1.x / Java 21**, with optional Spartan Weaponry and Spartan Shields integration. PvE-focused: mobs fight with the same rules as players.

- Design and milestones: `docs/PLAN.md`. Read the "Decisions locked in" section before changing behavior.
- Verified facts about NeoForge and the Spartan mods: `docs/spikes.md`. Add new findings there.
- Animation clips: `src/main/resources/assets/steelclash/steelclash_animations/*.json` (client, F3+T reload; parsed by `core/AnimationSet`). The weapon arm is solved from the arc in `core/ArmAim`, not authored.
- Mixins: `docs/mixin-risk.md` (client only: `LivingEntityRendererMixin` poses mobs, `ItemInHandLayerMixin` turns a mob's weapon in its hand).
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

## Workflow
- For each change: code, then unit tests, then GameTests, then a mutation check, then docs (`CHANGELOG.md` under `# Unreleased`, `docs/spikes.md`, `docs/testing.md` for anything only a playtest can check), then report.
- Run only the tests the change needs: unit tests for `core`, GameTests when server behaviour changes. Client-only rendering changes need neither GameTests nor a full suite.
- Commit only when the user says "commit"; push only when they say "push".
- Don't use ffmpeg.
- GameTest pitfalls: mock players aren't in the level (level queries can't find them; use `DownedGameTests.player` for an in-level one); all mock players share one scoreboard name; mock players never tick, so apply weapon attribute modifiers with `TestSupport.applyWeaponModifiers`; spawn yaw is random and mobs with AI wander, so pin facing and remove free will where the test depends on it.

## Skills (in `~/.claude/skills`, linked into `~/.codex/skills`)
- `mutation-check`: break the code on purpose and confirm a test fails (`scripts/mutate.py` with a JSON list of mutants).
- `bench-compare`: before/after performance, alternating runs; never trust one run per version.
- `screenshot-check`: contact sheets and before/after image diffs.
- `onion-skin`: find jumps in motion from frames captured evenly in time.

## Checking animations
- Pose sheet: `./gradlew runClient -PclientMemory=1536M -PquickPlay="New World" -PposeSheet=slash,overhead,stab` photographs frozen poses (behind, in front, first person) into `run/screenshots/pose_*.png`, then quits. `-PposeSheetPoints=windup:0.5,release:0.3` picks the moments; `-PposeSheetItem=<id>` the weapon; `-PposeSheetMob=minecraft:husk` poses a mob instead (front and side views); `-PposeSheetDebug` draws the traced blade, so you can see whether the drawn weapon matches the hit.
- For smoothness, capture every 50 ms (points spaced by each phase's duration from the weapon profile), set `cameraMotion = 0.0` in `run/config/steelclash-client.toml` for the capture, then run `onion-skin` per view.
