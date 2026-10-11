# Design history: milestones, fidelity checklist, and backlog

This archive preserves the original planning notes for historical context. Milestone status and implementation details reflect the document's earlier snapshot and may be stale; consult [current testing](../testing.md) and [next combat and animation plan](../next-combat-animation-plan.md) for active work. The backlog below remains deferred unless a current plan explicitly picks up an item.

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
| **Footwork and sustain** *(built 2026-10-06)* | Movement slowdown by phase (transient `MOVEMENT_SPEED` modifier, server and locally predicted; backpedal on the client), ducking (`duckHeight` 1.0: a crouching target's blade hitbox is capped, level slashes pass over), player health regeneration (5 s delay, 1 HP/s). Still open from the 2026-10-06 gap review: alternate weapon modes, projectile headshots *(built 2026-10-07)*, filling in timings from footage *(done from game data 2026-10-07)*, and rules to check in Chiv 2 (holding a weapon parry, shield ripostes, feinting ripostes, special cooldowns). | `FootworkGameTests` (mutation-checked). |
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

Filled in on 2026-10-07 from Chivalry 2's own data (the `chivalry2-weapons` package behind polehammer.net) instead of footage; see docs/spikes.md "Chivalry 2 timings". Windup includes the 350 ms chamber. Light attacks, in ms.

| Archetype (reference) | Slash W/R/R | Overhead W/R/R | Stab W/R/R | Combo windup (S/O/St) | Riposte windup (S/O/St) | Heavy | Notes |
|---|---|---|---|---|---|---|---|
| dagger (Dagger) | 450/400/750 | 450/400/750 | 515/300/750 | 700/700/765 | 450/450/515 | +250 / +100 | |
| sword (Sword) | 500/425/750 | 500/400/750 | 575/300/750 | 725/725/800 | 500/500/575 | +250 / +100 | |
| two_handed (Greatsword) | 675/550/1100 | 675/525/1100 | 700/425/1000 | 850/850/900 | 600/600/600 | +250 / +100 | |
| axe (Axe) | 525/450/850 | 525/450/850 | 550/350/900 | 775/775/800 | 525/525/550 | +250 / +150 | |
| blunt (Mace) | 500/475/750 | 500/450/800 | 525/300/800 | 750/750/775 | 500/500/525 | +250 / +150 | thwack 1050 |
| polearm (Halberd) | 700/600/1050 | 700/500/1050 | 750/370/1000 | 850/850/950 | 600/600/600 | +250 / +150 | |
| spear (Spear) | 700/350/900 | 600/450/850 | 700/350/900 | 1025/925/1025 | 600/600/600 | +250 / +150 | |
| rapier (Rapier) | 470/425/750 | 470/400/750 | 550/300/750 | 670/670/750 | 470/470/550 | +220 / +100 | |
| staff (Quarterstaff) | 475/400/700 | 550/500/800 | 700/350/900 | 700/700/1025 | 600/600/600 | +235 / +150 | thwack 800/900/900 |
| shield guard | — | — | — | — | — | — | not in the data; unchanged |

Heavy = windup / recovery added. Parry windows and stamina damage aren't in this data the same way and are unchanged.

Mechanics to double-check against the game rather than from memory: whether ripostes can be feinted, the exact counter window, how long a weapon parry can be held before it drops, and whether shield blocks allow a riposte.

## 11. Backlog (ideas for later)

| Idea | Source | Notes |
|---|---|---|
| **Blade twist (edge leads the cut)** *(built for players 2026-10-06: computed from the arc's direction of travel via `ArcPath.edgeAngle`, so every variant and mirrored swing gets it; rolled about Z in the hand frame (verified from playerAnimator's bytecode: Z·Y·X after vanilla's -90° hand turn); client config `bladeTwist` (scale / sign) and `bladeTwistAxis`; mobs still pending the item-render mixin)*: roll the held weapon around its own length per attack (flat for slashes, upright for overheads). | User, 2026-10-06 | Players: a `rightItemTwist` part in the pose clips. Mobs: needs a second small client mixin on item-in-hand rendering. Probably needs one in-game sign check, like `weaponGripPitch`. |
| **Two-handed grip for one-handed swords + directional slashes:** with an empty offhand, hold the sword in both hands, and pick the slash direction (left→right or right→left) from which way you're turning when you attack. | User, 2026-10-06 | Grip: an override of the archetype's `grip` when the offhand is empty (the solved two-hand pose already exists), maybe with slightly different stats (stamina, guard cone). Direction: mirror the horizontal arc (negate keyframe yaw) based on the sign of the yaw change around the attack press. The client sends the choice with `AttackInputPayload`, and the server mirrors the arc it traces, so hits still match visuals. Chivalry 2 also alternates slash direction on combos, so mirrored arcs are useful beyond this feature. |
| **Dual wielding:** a weapon in each hand. One mouse button swings each hand's weapon, and parry moves to another key (Alt or middle click, rebindable). Without dual wielding, parry stays on right click. Which button maps to which hand should be a preference. | User, 2026-10-06 | Needs: an offhand weapon profile lookup, a mirrored arc for the left hand (same mirroring as directional slashes), and the off arm solved from the arc like the weapon arm. Probably one shared state machine where each attack records which hand swings (simpler than two machines, and it keeps one windup at a time, as in Chivalry 2). Input: a "dual wield" mode active only when both hands hold profiled weapons; parry key defaults to Alt in that mode. Middle click is vanilla pick-block, so it conflicts in creative unless rebound. Balance: probably lower damage or stamina per hand. The Spartan parrying dagger in the offhand could also mean "offhand parries better" instead of a second attack. |
| **Gesture attacks (experimental option)** *(built 2026-10-06: measures the view's turn since the press instead of raw cursor delta, so the camera isn't frozen; threshold 5°, 4-tick window; client config `gestureAttacks`, `gestureThreshold`, `gestureWindowTicks`; customizable per direction (`gestureLeft/Right/Up/Down`: slash from left/right, overhead, stab, kick, none) and per key (`gestureKeys`))*: hold left click and move the mouse in a direction to pick the attack. Drag sideways to slash from that side, drag up for an overhead, drag down (or a short forward flick) for a stab. Off by default; client config `gestureAttacks`. | User, 2026-10-06 | Input: while LMB is held, accumulate mouse delta (from `InputEvent.MouseButton`/`ViewportEvent`, or better a raw cursor delta) *before* it turns the camera (freeze or damp camera turning during the gesture, configurable). Once the delta passes a threshold (e.g. 20–40 px), classify the direction into a sector → attack type plus side, and start the attack. Releasing LMB before the threshold = a default slash. Conflicts with today's "hold LMB = heavy": in gesture mode, heavy becomes "keep holding after the gesture fires" (same `HEAVY_HOLD_TICKS` rule, counted from when the gesture fired), so both coexist. Needs mirrored arcs (directional-slash backlog item) for left vs right slashes, and the side travels in `AttackInputPayload`. Sensitivity, dead zone and sector angles go in the config; the timing HUD could show the chosen direction. Keep scroll and Mouse 4/5 attacks working alongside. |
