# Testing

## Automated
- `./gradlew test`: JUnit tests for the Minecraft-free core (state machine, timings, arc and blade geometry).
- `./gradlew runGameTestServer`: GameTests (`com.steelclash.gametest`) on a headless server with Spartan Weaponry and Spartan Shields loaded. A mock player swings at real zombies in the `steelclash:arena` structure. Both commands run in CI.

## Manual in-game checklist (M1)
Run `./gradlew runClient`, create a **creative** flat world, and type `/steelclash_debug` to see the swings (yellow = windup plus the upcoming arc, red = live blade, grey = recovery). Spawn a zombie or a husk to hit (`/summon zombie ~3 ~ ~ {NoAI:1b}` keeps it still).

| # | Check | Expected |
|---|---|---|
| 1 | Hold an iron sword and left-click | Slash: windup, then a right-to-left cut. No vanilla arm swing. |
| 2 | Scroll up / scroll down with the sword | Overhead / stab. The hotbar does **not** change slot. |
| 3 | Mouse 5 / Mouse 4 | Overhead / stab (rebindable under Controls → Steel Clash). |
| 4 | Number keys while holding the sword | Still switch hotbar slots. |
| 5 | Hold left-click on a block with the sword | Nothing breaks (no vanilla mining). |
| 6 | Sneak + left-click on a block | Mines normally. |
| 7 | Turn the camera quickly during a slash's release | The red blade follows your view (drag/accel). |
| 8 | Slash two NoAI zombies side by side; then stab two standing in a line | Slash hurts both; stab hurts only the front one. |
| 9 | Click again during recovery | The next attack starts right after the current one (buffered), with no stutter. |
| 10 | Switch hotbar slot mid-windup | The attack cancels. |
| 11 | Hold a stick / empty hand and left-click | Vanilla behavior. |
| 12 | First person during a swing | Your right arm appears and follows the arc. **Report:** does it move the right way? Does the item look acceptable? |
| 13 | Third person (F5) during a swing | Arm and torso follow the arc. Same questions as 12. |
| 14 | Spartan longsword, halberd, dagger | Different timings and reach (halberd reaches farther and swings slower; dagger is fast and short). |
| 15 | Spartan shield in offhand, sword in main hand: hold right-click to block, then left-click | You attack (the shield lowers). **No Spartan shield bash.** |
| 16 | Two players: `./gradlew runClient` + `./gradlew runClient2` on an opened-to-LAN world | Each sees the other's arm and debug blade move in sync. |

Report failures with the step number. Arm-direction problems (12/13) are expected to need sign flips.

## Manual in-game checklist (M2)
Use **survival** for stamina (creative works too). Get a **Training Dummy Spawn Egg** from the Spawn Eggs tab. **Sneak + right-click** the dummy with an empty hand to cycle its mode (the name tag shows it), or with an item to arm it (shields go in its offhand).

| # | Check | Expected |
|---|---|---|
| 1 | Sword in hand: right-click (hold) | Weapon parry raises (`/steelclash_debug`: blue). Since the held block (2026-10-07) it stays up until you let go; with `blockMode = TIMED` it drops by itself after ~0.6 s. |
| 2 | Dummy in **Attack** mode: parry its slash on time | No damage, sparks plus an anvil "clank". Its arm snaps back (staggered, magenta). Your stamina bar under the crosshair flashes **white** = riposte ready. |
| 3 | Attack right after a successful parry | Noticeably faster windup (riposte). |
| 4 | Parry too early, or stand beside/behind the dummy's swing | You get hit. |
| 5 | Hit the dummy in **Parry** mode repeatedly | It parries and you get staggered each time. Its stamina drains; eventually its **sword flies out of its hand**. |
| 6 | Walk over the dropped sword | You can pick it up; zombies standing on it can't. |
| 7 | Parry several attackers in quick succession (2–3 Spar dummies, or a group of zombies) until your stamina runs out | You get disarmed (your weapon drops). One dummy alone won't do it: stamina regenerates between its swings. `disarmMode = HOLSTER` in the common config locks the weapon for 3 s instead. |
| 8 | Shield in offhand + sword: hold right-click | Vanilla shield guard. Hits from the front cost stamina; from behind they go through. Run out → shield break sound, shield on cooldown, staggered. |
| 9 | Spartan tower shield vs a skeleton | Arrows from the front are stopped, arrows from behind are not. |
| 10 | Greatsword/halberd/spear + shield in offhand: right-click | Shield does **not** raise (two-handed). |
| 11 | Zombie / husk / spider / vindicator attacks you | Rustle sound, visible windup (debug: yellow), *then* the hit. Parrying it works. |
| 12 | Vindicator / armed zombie, Normal or Hard difficulty | Sometimes parries your attacks (35% / 55%). |
| 13 | Right-click a door, chest, villager or horse with a sword | Opens/trades/mounts as normal (no parry). Sneak + right-click always interacts. |
| 14 | Trident / bow / Spartan javelin: right-click | Vanilla behavior (throw key comes later). |

## Manual in-game checklist (M3)
New keys (Controls → Steel Clash): **Feint = X**, **Kick / Shield Bash = Z**. Use the training dummy (Parry / Attack / Spar modes) and `/steelclash_debug`.

| # | Check | Expected |
|---|---|---|
| 1 | Hold left-click (or Mouse 4/5) instead of tapping | Heavy: the weapon draws back further, the windup is longer, the swing sound is lower, it hits harder and drains more stamina when parried. Scroll attacks are always light. |
| 2 | Tap X during a windup | Feint: the attack cancels and costs stamina. The Parry dummy raises its guard, then gets punished. |
| 3 | Right-click during a windup | Cancels straight into a parry (costs stamina). |
| 4 | Start a slash, then scroll down/up before it releases | Morph into a stab/overhead. Works once per swing. |
| 5 | Hit something, then attack again during recovery | Combo: the next attack starts at once. After a **miss**, the next attack waits for the recovery to end. |
| 6 | Hit the Attack-mode dummy while it winds up | Its attack is interrupted (flinch). |
| 7 | Give the dummy a mace or Spartan greatsword, then hit it during a *heavy* | It keeps swinging (hyper armor). |
| 8 | Z at a shield-blocking or parrying dummy | Guard broken: shield drops, dummy staggered, stamina drained, no damage. With a shield in your offhand, Z is a stronger shield bash. |
| 9 | Z at an unguarded dummy/zombie | Short stagger, a little damage, a shove. |
| 10 | Attack-mode dummy slashes at you: slash back early | Counter: its slash is parried and yours lands first. Stab vs slash doesn't counter. |
| 11 | Slash next to a wall or tree | Clank: sparks, sound, short stagger. Overheads into the ground and swinging under a 3-block ceiling don't clank. |
| 12 | Sprint and attack | Lunge: you surge forward and reach farther. |
| 13 | Jump and overhead | Extra damage. |
| 14 | Zombies/vindicators on Normal/Hard | About 1 in 4 of their attacks is a slower heavy. |

## Manual in-game checklist (M4: feel)
This milestone is mostly *looks and feel*, so it can only be judged by eye. Try each archetype (sword, dagger, Spartan rapier, greatsword, halberd, spear, mace, quarterstaff) in first **and** third person (F5).

| # | Check | What to look for / report |
|---|---|---|
| 1 | Swing a sword in third person, with `/steelclash_debug` on | **Most important:** does the rendered sword lie along the red debug blade during the release? Verified: `weaponGripPitch = -80` (now the default). |
| 2 | Slash / overhead / stab in third person | Torso twists into slashes, leans back then chops on overheads, steps forward on stabs. The off arm counterbalances. |
| 3 | Greatsword / halberd / spear / staff | **Both hands** on the weapon. In first person, both arms are visible. |
| 4 | Hold for a heavy | Bigger, slower draw-back than a light. |
| 5 | Kick (Z) | The right leg kicks; the weapon arm stays put. |
| 6 | Parry / get parried / get kicked | Guard pose; reel back on stagger. |
| 7 | Land a hit | A brief freeze of your swing (hit-stop), a small camera shake, red particles, a fleshy hit sound. |
| 8 | Get parried / clank on a wall | A stronger shake and freeze. Sparks. |
| 9 | Swing in first person | The view rolls slightly into the swing. `cameraMotion = 0` turns all camera effects off; `hitStopMillis = 0` turns off hit-stop. |
| 10 | Zombie, husk, skeleton with a sword, vindicator, piglin, training dummy attacking you | **Their arms now wind up visibly.** Can you tell slash from overhead from stab before it lands? That's the M4 goal. |
| 11 | F3+T after editing `src/main/resources/assets/steelclash/steelclash_animations/sword.json` (or a resource pack copy) | Pose changes apply without restarting. |
| 12 | Sounds | Windup rustle, swing whoosh (deeper for heavies), hit, parry clang, clank, kick, feint. Subtitles show for each. |

## Manual in-game checklist (M5: bot brain)
Play in survival. Test on **Easy, Normal and Hard** (`/difficulty`). Turn on `/steelclash_debug` to see every swing.

Useful summons (the helmet stops zombies burning in daylight):
```
/summon zombie ~3 ~ ~ {HandItems:[{id:"minecraft:iron_sword",count:1},{}],ArmorItems:[{},{},{},{id:"minecraft:iron_helmet",count:1}]}
/summon zombie ~3 ~ ~ {HandItems:[{id:"spartan_weaponry_unofficial:iron_halberd",count:1},{}],ArmorItems:[{},{},{},{id:"minecraft:iron_helmet",count:1}]}
/summon vindicator ~3 ~ ~
/summon zombie ~3 ~ ~ {NoAI:1b,HandItems:[{id:"minecraft:iron_sword",count:1},{}]}
```
The last one stands still, which is useful for looking at poses. It won't fight back.

| # | Check | Expected |
|---|---|---|
| 1 | 1v1 an armed zombie or vindicator on Hard | It keeps just outside your reach, circles, steps in to attack, backs off afterwards. It mixes slashes, overheads, stabs and heavies, sometimes feints (the windup stops early) and sometimes morphs. |
| 2 | Parry its attack | It ripostes immediately if *it* parried you; if you parried it, your riposte should usually land. |
| 3 | Same attack type over and over (e.g. only stabs) on Normal/Hard | After a few it starts **countering** (answering with the same attack). |
| 4 | Feint repeatedly | It stops biting: it parries later, and only the real attack. |
| 5 | Hold your shield/parry up and wait | On Normal/Hard it **kicks** you (about 1.5 s of turtling on Hard, 2 s on Normal). Easy bots never kick. |
| 6 | Attack fast with a dagger on Easy | Easy bots can't parry 7-tick windups (reaction 8 ticks); Hard bots can (4 ticks). |
| 7 | 1v4 zombies | Only 1 (Easy) / 2 (Normal) / 3 (Hard) swing at you at a time; the rest circle at a distance and take turns. It should feel hard but readable. |
| 8 | Group of zombies near each other | They no longer hit each other with wide swings (no infighting). |
| 9 | `botBrain = false` in the common config | Mobs fall back to the M2 behaviour (telegraphed attacks plus reactive parries, no spacing). |

## Manual in-game checklist (M6a: timing HUD, armed mobs, mob movement)
| # | Check | Expected |
|---|---|---|
| 1 | Hold left-click | A grey bar under the stamina bar fills toward a white tick (the heavy point), then turns orange (heavy windup). Tap instead: yellow windup. |
| 2 | Watch one attack through | Yellow/orange windup → **red** release (blade live) → grey recovery draining. After a landed hit, recovery is **green** (combo available). |
| 3 | Parry | A **blue** bar drains for how long the parry stays up, then dim blue for the guard recovery. After a successful parry, a **white** bar shows the riposte window. |
| 4 | Get staggered (parried, kicked, clanked) | A **magenta** bar drains for the stagger. |
| 5 | `timingHud = false` (client config) | Bar hidden. |
| 6 | `enemyTelegraphs = true` (client config) | Enemies winding up show e.g. `HEAVY OVERHEAD ▮▮▮▯▯▯` above their heads, counting down. |
| 7 | Natural zombies on Normal/Hard | About half (Normal) / most (Hard) carry weapons: vanilla swords/axes, or Spartan weapons when installed, better materials on Hard. Some have shields and helmets. Spawn eggs too; `/summon` with NBT is untouched. |
| 8 | Zombie with a shield | Raises it when you attack and its reaction allows; keeps it up through your swing; kick it (Z) to break the guard. |
| 9 | Fight a group | Zombies shuffle in relentlessly; vindicators rush and lunge in from outside reach; spiders dart in and back off; skeletons with swords keep their distance. Waiting mobs spread around you instead of bunching up, and each moves a little differently. |
| 10 | Throw a slow heavy at a skeleton or spider | Sometimes it steps back out of reach instead of parrying. |
| 11 | Drain a mob's stamina (make it parry a lot) | It backs off until it has recovered. |
| 12 | Training dummy in Attack or Spar mode with no player nearby | It fights the nearest monster (handy for watching mobs defend). |

## Manual in-game checklist (M6b: attack variants and sides)
Use `/steelclash_debug` to see the arcs.

| # | Check | Expected |
|---|---|---|
| 1 | Slash repeatedly without turning | Some slashes are flat, some come down diagonally, some rise. Overheads sometimes come in at an angle; stabs sometimes go low or rise toward the head. |
| 2 | Turn the mouse **right** while pressing attack, then **left** | Turning right swings left→right; turning left swings right→left. Aim a slash at someone beside you by turning into them. |
| 3 | Land a hit, then combo | The follow-up comes from the other side (alternating, like Chivalry 2). |
| 4 | Watch your body during a mirrored swing (F5) | The torso twists and leans the other way too. |
| 5 | Hold for a heavy | A distinct heavy windup: leans back, elbow up, back foot planted (not just a bigger light windup). |
| 6 | Sword with an empty offhand | Held in both hands. Put anything in the offhand → one-handed. `twoHandedSwords = false` in the client config turns this off. |
| 7 | Mobs | They also use all variants and both sides (random), and alternate sides on combos. |
| 8 | Spam right-click | After each parry ends (caught a hit, released, or timed out) there's a short pause (5 ticks, `parryCooldownTicks` in the common config) before you can parry again. No parrying while lowering the guard. |
| 9 | Fight 2–3 zombies (or two Attack-mode dummies) and hold a parry as they swing together | One parry catches several hits while it's up (each costs stamina). After the first catch, the timing bar turns white (riposte window) and attacking ripostes straight out of the guard. A parry that caught something drops without the guard-recovery delay. |

## Manual in-game checklist (M6c-1: specials, throwing, damage types, lance)
New keys: **R = weapon special**, **G = throw weapon** (Controls → Steel Clash). Hover a weapon to see its tooltip.

| # | Check | Expected |
|---|---|---|
| 1 | Hover any weapon | A gold line like `Sword · Cut · Special: Lunge (R)`. |
| 2 | R with a sword, dagger, rapier or spear | **Lunge:** you dash forward with a long stab that out-reaches a normal stab. |
| 3 | R with a mace, hammer or axe | **Slam:** a big overhead; on impact everyone within ~2.5 blocks of the impact point is knocked back, staggered and drained (guarding or not). Dust and a heavy thud. |
| 4 | R with a greatsword, halberd/glaive or quarterstaff | **Sweep:** a very wide slash that can hit up to 5 enemies. |
| 5 | R again immediately | Nothing happens: specials have a cooldown (4–7 s depending on the weapon). |
| 6 | G with any weapon | A short overarm windup, then the weapon flies, hits for about 1.2× its melee damage, and drops where it lands so you can pick it up. Creative mode keeps the weapon in hand. |
| 7 | Sword vs a zombie in full diamond, then a mace vs the same | The mace does relatively much better: blunt beats plate, cuts glance off it. Stabs count as pierce (good against armour gaps). `damageTypes = false` in the common config turns it off. |
| 8 | Ride a horse with a spear or lance (Spartan) and stab while galloping | Much harder hits, scaling with the horse's speed (up to 2.5×). |
| 9 | Armed mobs (Normal/Hard) | Now and then they open with their weapon's special. |

## Parry key (added 2026-10-06)
| # | Check | Expected |
|---|---|---|
| 1 | Controls → Steel Clash | A **Parry (weapon guard)** binding, default right click, not shown as conflicting with vanilla "Use Item". |
| 2 | Leave it on right click | Exactly as before: right click parries with a weapon; doors, chests, villagers, sneaking and shields still use vanilla right click. |
| 3 | Rebind Parry to Left Alt (keyboard) | Hold Left Alt to parry (release to lower). Right click goes back to vanilla "use" (eat, place, interact) even while holding a sword. |
| 4 | Rebind Parry to a side mouse button | That button parries; right click is vanilla again. |

## Slash keys (added 2026-10-06)
*Now the `TWO_SLASH_KEYS` control scheme; the default is `CHIVALRY` (see "Control schemes and gesture attacks" below).*
Default controls are now: **left click = slash right→left, right click = slash left→right** (both hold for a heavy), **middle click = parry / raise shield**, Mouse 5 / scroll up = overhead, Mouse 4 / scroll down = stab, X feint, Z kick, R special, G throw. All are rebindable under Controls → Steel Clash. A parry you already rebound (e.g. to Left Alt) keeps your binding.

| # | Check | Expected |
|---|---|---|
| 1 | Left click / right click with a sword (`/steelclash_debug` on) | Left click swings right→left, right click swings left→right. Holding either makes a heavy. |
| 2 | Right click on a door, chest, villager or horse, or while sneaking, or holding a bow/trident | Vanilla behaviour, no slash. |
| 3 | Parry key with a sword | Weapon parry while held. |
| 4 | Parry key with a shield in the offhand | Raises the shield while held (right click no longer does, since it slashes). |
| 5 | Overheads and stabs | Side still follows your turning / alternates on combos. |

## Manual in-game checklist (M6c-2: brigand soldiers, patrols, camps)
Spawn eggs: **Brigand Footman / Knight / Archer** (Spawn Eggs tab). Survival, Normal or Hard.

| # | Check | Expected |
|---|---|---|
| 1 | Spawn one of each | Footman: red tabard, mail, helmet, a sword/axe/Spartan weapon, sometimes a shield. Knight: blue tabard with a gold cross, full iron (some diamond on Hard), a heavier weapon, often a shield. Archer: green hood, leather, a bow. |
| 2 | Fight a footman and a knight | They fight like the other bots (spacing, telegraphed swings, parries, shields, feints, specials). Knights rush and lunge in. |
| 3 | Stand back from an archer | It draws its bow (arm raised) and shoots; it keeps its distance. |
| 4 | Play a few in-game days in the overworld (or set `soldierPatrolIntervalTicks` low and `soldierPatrolChance = 1.0` in the common config) | A **patrol** appears 24–48 blocks away: a knight leading footmen and archers, walking toward you. Never inside villages. `doPatrolSpawning false` or `soldierPatrols = false` stops them. |
| 5 | Night time in plains/forest/taiga | Soldiers occasionally spawn among the zombies and skeletons (rarer). |
| 6 | `/locate structure steelclash:brigand_camp` in a new world, then go there | A camp: two tents, a campfire with log seats, hay, a barrel, a red banner, and a loot chest (food, arrows, iron, emeralds, sometimes a weapon), guarded by a knight, two footmen and an archer. More soldiers spawn there at night. |
| 7 | Kill soldiers | Small chance to drop their gear. |

## Manual in-game checklist (M7: multiplayer and latency)
Setup: `./gradlew runClient` (Dev1) opens a world to LAN; `./gradlew runClient2` (Dev2) joins it. Add latency with [clumsy](https://jagt.github.io/clumsy/): filter `udp or tcp and (tcp.DstPort == <lan port> or tcp.SrcPort == <lan port>)`, **Lag** 75 ms both ways (≈150 ms ping). Turn on `/steelclash_debug` on Dev2: the top-left shows ping, rewind ticks and parry grace ticks (from Dev2's own config copy).

| # | Check | Expected |
|---|---|---|
| 1 | Dev2 (lagged) parries a zombie's or footman's telegraphed swing, pressing parry when the swing visibly lands on screen | Parried reliably (aim for 9/10). Without compensation (`lagCompensation = false` in Dev1's common config, then rejoin) it should feel noticeably late. |
| 2 | Dev2 slashes a zombie walking sideways past them | Hits when the blade visibly crosses it on Dev2's screen, not where the server has it. |
| 3 | Dev2 gets parried by a bot, then attacks again as soon as the stagger visibly ends | The attack starts; no snap-back or eaten input. |
| 4 | Dev2 stands still with no parry while a mob hits them | The hit lands about a ping later than without lag, never lost. |
| 5 | Dev1 (host, no lag) fights normally next to Dev2 | Unchanged: no delayed hits on the host. |
| 6 | Both players watch each other swing and parry | The other player's arm, debug blade and parries match what they're doing, about a ping behind. |

## Manual in-game checklist (M8: release)
| # | Check | Expected |
|---|---|---|
| 1 | Delete `run/config/steelclash-client.toml`, join a world | One grey chat line with your slash and parry keys and `/steelclash_help`. It doesn't appear on the next join (`tutorialHint` turned itself off). |
| 2 | `/steelclash_help` | Nine lines; the key names match your bindings (rebind one and run it again). |
| 3 | Pick up a hay bale in survival, open the recipe book | Training Dummy recipe unlocked; crafting it gives the dummy, which places like a spawn egg. |
| 4 | Mods → Steel Clash → Config | Readable names for every option and section, with descriptions on hover; changing one takes effect. |
| 5 | `./gradlew runClient -PnoCompat` | Boots and plays without Spartan Weaponry/Shields; vanilla swords, axes, the mace and the trident all have movesets. |
| 6 | Drop `build/libs/steelclash-0.1.0-beta.jar` and Player Animation Library 1.1.6 into a normal NeoForge 21.1 instance | Loads outside the dev environment; the mod list shows MIT, 0.1.0-beta, the author and the credits. |

## Control schemes and gesture attacks (added 2026-10-06)
Client config (Mods → Steel Clash → Config → client).

| # | Check | Expected |
|---|---|---|
| 1 | Default `controlScheme = CHIVALRY`: slash several times in a row, standing still | Sides alternate every slash. Turning right while clicking swings from the left (and vice versa). Right click parries; with a shield in the offhand it raises it. A chat line says the scheme was applied (first launch). |
| 2 | Switch to `TWO_SLASH_KEYS` in the config screen | A chat line confirms; left click slashes right to left, right click left to right, middle click parries (see Controls). Switch back: right click parries again. Rebinding by hand afterwards sticks. |
| 3 | `gestureAttacks = true`: hold left click and flick the mouse left / right / up / down | Slash from the left / slash from the right / overhead / stab. |
| 4 | Quick click without moving | Normal right-to-left slash, no noticeable delay. |
| 5 | Gesture, then keep holding | The attack becomes a heavy (timing bar shows the charge). |
| 6 | Hold without moving | Slashes after about 0.2 s (`gestureWindowTicks`) and can still become a heavy. |
| 7 | Tune `gestureThreshold` (e.g. 3 and 10) | Smaller = more sensitive; normal aiming while holding the button shouldn't misfire at the default. |
| 8 | Set `gestureLeft = SLASH_FROM_RIGHT`, `gestureRight = KICK`, `gestureUp = NONE` | Drag left slashes right to left, drag right kicks, drag up does nothing special (the press slashes after the window). |
| 9 | `TWO_SLASH_KEYS` + `gestureKeys = BOTH` | Right click also reads gestures; a plain right click still slashes left to right. |
| 10 | `gestureLockView = true`: hold left click and drag | The view doesn't move while the gesture is read; the attack still matches the drag. Once it fires, the mouse turns you normally again. Works in third person too. |

## Blade twist (added 2026-10-06)
Hold a sword, third person (F5) and first person, `/steelclash_debug` on.

| # | Check | Expected |
|---|---|---|
| 1 | Slash both ways | The blade turns flat (edge facing the way it travels) during the windup and stays edge-first through the cut. Mirrored slashes turn the other way. |
| 2 | Overhead | The edge stays down: no roll, as before. |
| 3 | Stab | The blade is held flat. |
| 4 | Axe slash (single edge) | The cutting edge leads, not the back of the head. **If it trails, set `bladeTwist = -1`** and tell me, so the default can be flipped. |
| 5 | If the weapon tumbles end over end instead of rolling around its length | Try `bladeTwistAxis = Y` (then X) and report which one rolls correctly. |
| 6 | `bladeTwist = 0` | The old look, with no roll. |

## Accels, drags, turn cap, side from movement (added 2026-10-06)
Sword, dummy in Parry mode, then Attack mode; `/steelclash_debug` on.

| # | Check | Expected |
|---|---|---|
| 1 | Slash and whip the mouse the way the blade travels (accel) | The debug blade connects early in the release. Against a parrying dummy, a late parry misses it. |
| 2 | Slash and turn against the blade (drag) | It connects late, or carries past. |
| 3 | During a windup or release, flick the mouse hard | The camera turns at most about 360°/s and then catches up when the swing ends. No 180° hits behind you. Turning outside attacks is unaffected. |
| 4 | `turnCapDegreesPerSecond = 0` | No limit (the old feel). |
| 5 | Hard difficulty, fight a footman or a sword zombie for a while | Some of their slashes visibly turn their head mid-swing and land early or late. Parries timed purely to the windup sometimes fail. |
| 6 | `sideFromMovement = FROM_STRAFE_SIDE`, hold A and slash (CHIVALRY scheme) | Swings from the left every time; D swings from the right; no strafe = alternating again. `TOWARD_STRAFE` is the reverse. |

## Footwork, ducking, health regeneration (added 2026-10-06)

| # | Check | Expected |
|---|---|---|
| 1 | Walk forward while winding up and releasing a slash; parry while walking | Noticeably slower (65% / 75%), back to full speed after. Mobs slow down when they swing too. |
| 2 | Walk backwards holding a sword, then with an empty hand | Slower with the sword (80%), normal without. |
| 3 | Dummy in Attack mode: crouch as its slash comes | The level slash passes over you. |
| 4 | Second player (or a footman) slashes while you crouch, then looks down and slashes | The level slash misses; aimed low, it hits. Overheads always hit. |
| 5 | Take some damage, then step away | After about 6 s health refills at half a heart per second, even when hungry, and stops at 40% (8 health). `healthRegen = false` turns it off. |

## Player Animation Library parity (added 2026-10-06)
The swing animations now run on Player Animation Library instead of playerAnimator. They should look **the same as before**. A pixel comparison with the pose sheet confirmed this for third person and nearly so for first person (docs/spikes.md); these checks cover what screenshots can't (motion, other players, reload).

| # | Check | Expected |
|---|---|---|
| 1 | Third person (F5): slash both ways, overhead, stab, heavy, kick | Same arm paths and body lean as before; no twisted torso, no backwards lean (whole-body sign). |
| 2 | Sword grip and blade twist | The blade lies along the arm as before (grip −80), and the edge leads slashes. If the weapon now points the wrong way, report it (the item-axis conversion is the suspect). |
| 3 | First person, one-handed and two-handed sword | Arms and weapon visible during attacks, a single weapon, no left-hand duplicate. |
| 4 | Second player (runClient2) watching you | Your swings animate for them too. |
| 5 | F3+T | Pose clips still reload. |
| 6 | Mobs swinging | Unchanged (they don't use PAL). |

## Animation steps C–E: weapon rig, first person, archetype clips (added 2026-10-07)
Checked on pose sheets already (docs/spikes.md); these cover motion and what the pose sheet can't photograph.

| # | Check | Expected |
|---|---|---|
| 1 | Third person, slow-motion feel: slash both ways with a sword | The body winds up, steps and follows through; the blade still runs along the red debug line (`/steelclash_debug`). |
| 2 | First person: overhead and parry with a two-handed sword | Both hands on the hilt, the weapon clearly in view, arms not filling the screen. |
| 3 | Halberd / quarterstaff in both views | Hands spread along the shaft. |
| 4 | Watch an armed zombie or footman swing (third person) | Torso twists a little (half the player's), arms stay attached; two-handed mobs hold the grip with both hands. |
| 5 | F3+T after editing a clip or `grip_gap` | Reloads. |

## Chivalry 2 defence and movement (step F, added 2026-10-07)

| # | Check | Expected |
|---|---|---|
| 1 | Hold right-click with a sword for several seconds | The guard stays up; stamina drains slowly (about 4/s) and doesn't regenerate until you let go. |
| 2 | Block a dummy's slash, then attack | Fast riposte. Have a second dummy (Attack mode) swing at you during the riposte: it's parried too (active parry). |
| 3 | Two Attack dummies: counter one with the same attack | Countered; the second one's hit during your swing is parried. |
| 4 | Hold block, and press stab just as a slash arrives | Blocked anyway (parry forgiveness), not hit. Pressing it earlier gets you hit. |
| 5 | Left Alt standing still / holding A / mid-windup | Short dash backwards / left; a windup is abandoned. 12 stamina; a second dodge within 1 s doesn't happen. Not during a swing's release or a stagger. |
| 6 | V next to an Attack dummy winding up | Quick short thrust; it interrupts the dummy. Light damage. X (feint) and right-click don't cancel it. |
| 7 | Hard difficulty footman: wind up heavies up close | It sometimes jabs you out of them. |
| 8 | Fight, then step away hurt | Regeneration waits 6 s after your last swing or block, not just after your last hit, and stops at 40%. |
| 9 | `blockMode = "TIMED"` | The old parry: drops by itself after ~0.6 s. |
