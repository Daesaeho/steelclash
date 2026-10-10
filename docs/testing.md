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
| 5 | Hit the dummy in **Parry** mode repeatedly | It parries and you get staggered each time. Its stamina drains; eventually it loses its copied practice sword. The dummy creates no weapon loot. |
| 6 | Disarm an armed zombie, then walk over its dropped sword | You can pick up the real weapon; other zombies standing on it can't. Its former owner may retrieve it first. |
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
| 5 | Slash/overhead/stab, then attack again during recovery after a hit or miss | Combo: the next weapon attack starts at once in either case. Landed jabs, kicks, specials and throws retain their recovery. |
| 6 | Hit the Attack-mode dummy while it winds up | Its attack is interrupted (flinch). |
| 7 | Give the dummy a mace or Spartan greatsword, then hit it during a *heavy* | It keeps swinging (hyper armor). |
| 8 | Z at a shield-blocking or parrying dummy | Guard broken: shield drops, dummy staggered, stamina drained, no damage. With a shield in your offhand, Z is a stronger shield bash. |
| 9 | Z at an unguarded dummy/zombie | Short stagger, a little damage, a shove. |
| 10 | Attack-mode dummy slashes at you: raise guard, then slash back within the counter window | Counter: its slash is parried and yours lands first. A neutral slash or a mismatched stab does not counter. |
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
Setup: `./gradlew runClient` (Dev1) opens a world to LAN; `./gradlew runClient2` (Dev2) joins it. Add latency with [clumsy](https://jagt.github.io/clumsy/): filter `udp or tcp and (tcp.DstPort == <lan port> or tcp.SrcPort == <lan port>)`, **Lag** 75 ms both ways (≈150 ms ping). Turn on `/steelclash_debug` on Dev2: the top-left shows ping, rewind ticks and parry grace ticks using the host's synchronized combat settings.

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
| 1 | Join a world, leave, join again | Both times the full controls help (your actual key bindings) appears in chat, ending with how to turn it off. `helpOnJoin = false` in the client config stops it. |
| 2 | `/steelclash_help` | Nine lines; the key names match your bindings (rebind one and run it again). |
| 3 | Pick up a hay bale in survival, open the recipe book | Training Dummy recipe unlocked; crafting it gives the dummy, which places like a spawn egg. |
| 4 | Mods → Steel Clash → Config | Readable names for every option and section, with descriptions on hover; changing one takes effect. |
| 5 | `./gradlew runClient -PnoCompat` | Boots and plays without Spartan Weaponry/Shields; vanilla swords, axes, the mace and the trident all have movesets. |
| 6 | Drop `build/libs/steelclash-0.3.2-beta.jar` and Player Animation Library 1.1.6 into a normal NeoForge 21.1 instance | Loads outside the dev environment; the mod list shows the Steel Clash License, 0.3.2-beta, the author and the credits. |

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
| 4 | Watch an armed zombie, vindicator or footman swing (third person) | The whole body leans and twists as one piece (torso stays on the legs, arms and head attached); vindicators show their arms and axe on every swing; two-handed mobs hold the grip with both hands. |
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
| 10 | Kick (Z) an Attack dummy while it winds up | It swings through the kick (no stagger). Kick it while idle: staggered. |
| 11 | Start a slash, then press Z (or V) before it releases | The slash turns into a kick (or jab). |
| 12 | Get parried, then mash attack during the stagger | Your attack starts the moment the stagger ends. |
| 13 | Fight, then jump; walk away 5 s, then jump | The first jump costs stamina, the second doesn't. Crouching stops stamina from refilling. |
| 14 | Counter a dagger, then a greatsword | Timing the dagger is noticeably tighter. |

## Cleave, thwack and counter-feint (0.2.0-beta, added 2026-10-07)

| # | Check | Expected |
|---|---|---|
| 1 | Light slash with a mace into two zombies standing side by side | The mace stops in the first zombie (it jolts, the swing ends there and recovers early); the second one isn't hit. |
| 2 | Same, but the first zombie dies from the hit | The mace carries on and hits the second one too. |
| 3 | Heavy (hold) slash with the mace into two zombies | Both hit (heavies always cleave). |
| 4 | Sword slash into two zombies | Both hit; no early stop (swords cleave). |
| 5 | Mace thwack into a wall right behind a zombie | No clank: the swing stopped in the body before reaching the wall. |
| 6 | Attack dummy winding up an overhead at you: start a slash, then press overhead | You morph into an overhead. Press slash again: a second switch (counter-feint) restarts the windup. A third switch isn't allowed. |
| 7 | Dummy winding up a slash at you while you wind up a slash: tap the other side's slash key | Your slash switches side (counter-feint), costs stamina, windup restarts. With nobody attacking you this doesn't work. |
| 8 | Hard footman: wind up a slash, then morph to an overhead | The footman sometimes switches its own attack to follow yours. |
| 9 | Multiplayer (LAN): thwack with a mace | The other player sees the swing stop in the body too; no snap-back. |

## Chivalry 2 timings (added 2026-10-07)

| # | Check | Expected |
|---|---|---|
| 1 | `/steelclash_debug` timing bar, sword slash into the air | Windup ~0.5 s, the blade sweeps for ~0.4 s, recovery ~0.75 s. A greatsword or halberd is clearly slower in every phase. |
| 2 | Whiff a slash, then press stab during the recovery | The stab starts at once (combo), from the other side. Bar shows the combo window right after the release. |
| 3 | Swing into a dummy's raised guard, then attack again | No combo: you're staggered first. |
| 4 | Hold for a heavy with a sword | Windup ~0.25 s longer than the light; recovery a bit longer. |
| 5 | Parry, then riposte with a sword, then with a halberd/spear | The sword riposte winds up like a normal attack; the halberd/spear riposte is quicker than its normal attack. |
| 6 | Bots (Hard footman) whiff, then follow up | They sometimes combo after a whiff too. |

## Archer sidearms (added 2026-10-07, needs Spartan Weaponry)

| # | Check | Expected |
|---|---|---|
| 1 | Spawn-egg a skeleton, let it shoot, then walk up to it | Within ~4 blocks it swaps the bow for a dagger and fights with the bot brain (parries, feints). |
| 2 | Back off past ~9 blocks | It swaps back to the bow and shoots again. |
| 3 | Stand right at ~5–8 blocks | No back-and-forth swapping. |
| 4 | Save and reload with a skeleton that has its dagger out | After the reload it still has the dagger in hand and goes back to its bow once you leave. |
| 5 | Without Spartan Weaponry | Skeletons only have their bow; nothing breaks. |

## Projectile defence and headshots (added 2026-10-07)

| # | Check | Expected |
|---|---|---|
| 1 | Face a skeleton; start a slash just as its arrow arrives | The arrow bounces off; parry sound; you take nothing and the slash continues. |
| 2 | Hold the weapon guard against its arrows | You take about 30% less; stamina drops a little; even at zero stamina you're never disarmed. |
| 3 | Turn your back with the guard up | Full damage. |
| 4 | Shoot a zombie in the head, then in the body | Head: a ding and noticeably more damage. |
| 5 | Draw a bow and let a zombie hit you | The draw drops; keep holding to start again. |
| 6 | Hit a skeleton that's drawing | Its shot is lost. |

## Team rules (added 2026-10-07, LAN with two players)

| # | Check | Expected |
|---|---|---|
| 1 | Stand behind your friend and stab at a zombie in front of them | The stab stops in your friend (light damage to them); the zombie is untouched. |
| 2 | Slash across your friend and a zombie | Both are hit; your friend takes about a quarter. |
| 3 | Friend holds block; you slash them | No parry: you're not staggered, they lose no stamina. |
| 4 | Kick your friend | Nothing happens. |
| 5 | `playersAreAllies = false`, duel | Full damage, parries and ripostes work as against bots. |

## Downed and revive (added 2026-10-07, LAN with two players)

| # | Check | Expected |
|---|---|---|
| 1 | Let zombies get you to zero health with your friend nearby | You crawl on the ground with a countdown; your friend gets "X is down!". Zombies wander off you. |
| 2 | Try to attack, block, eat, place a block while down | Nothing happens. |
| 3 | Friend crouches next to you | Both see a progress bar; after 3 s you stand up with 30% health. |
| 4 | Hit the friend while they're reviving | The bar restarts. |
| 5 | Let the countdown run out | "X bled out". |
| 6 | Downed, get hit again | You die. |
| 7 | Singleplayer, die | You die as before. |
| 8 | Downed, disconnect | You're dead when you rejoin. |

## Release interrupts (2026-10-08)

| Check | Expected |
|---|---|
| Duel a Normal bot for a few minutes | Exchanges feel fair: an accel that lands first wins, simultaneous swings trade, and you're not cut off unexpectedly. |
| Swing at a mace knight's heavy as it releases | The heavy carries on (hyper armour). |
| Co-op with latency, both swinging at each other's allies | No one-sided trades that depend on who joined first. |
| Fight while standing in fire | Burning doesn't cut your swings short. |
| First person with a sword: stand still, then slash | The weapon rests in a ready stance; the slash grows out of it and returns to it, no cut. |
| Combo slash into slash, and a riposte after a parry | The arm moves straight from the end of one swing into the next windup, no drop to rest. |
| Put a shield in the off hand | Vanilla's first-person hand and shield come back; attacks still work. |

## Bug audit checks (2026-10-08)

The server-side fixes have GameTests. These need real clients with the same current protocol on both sides:

| Check | Expected |
|---|---|
| LAN with latency: catch a hit on a weapon guard, release it, then attack | No extra guard-recovery delay after the caught hit; cooldown and riposte behaviour agree with the server. |
| Mouse-bound and keyboard-bound parry: press while client and server disagree about the guard (e.g. right after a riposte), then release | The server gets the release even if your client didn't raise the guard; stamina stops draining. |
| While downed, right-click different parts of an equipped armor stand | No equipment can be taken or put on. |

## Animation presentation patch (2026-10-08)

See [animation.md](animation.md) for review coverage, resource-pack settings and the client acceptance matrix. Build, JUnit and GameTests pass; the client checks there are still to do.

## Broader first-person swings (added 2026-10-08)

| # | Check | Expected |
|---|---|---|
| 1 | First person: slash both ways, overhead, stab, with a sword and a two-handed weapon | The blade sweeps across the screen and the hands travel with it; nothing pops at the start or end of a swing. |
| 2 | Set `firstPersonSwingWidth` to 1 and `firstPersonSwingLift` to 0 | Swings look as before this change. |
| 3 | `/steelclash_debug` in first person | The drawn blade is wider than the red trace (expected: visual only); hits still land where the trace goes. |

## Mob arms and windups (added 2026-10-08)

| # | Check | Expected |
|---|---|---|
| 1 | `/steelclash_debug`, watch a zombie, skeleton with a sword, vindicator and piglin attack (third person, from the side) | The weapon lies along the red line at shoulder height through the release, not low at the hip. |
| 2 | Same mobs idle and walking | Vanilla poses, weapon held as normal. |
| 3 | Slash and overhead, light and heavy, third person | The slash cocks behind the shoulder; the overhead leans back over the head; both swing smoothly into the release. |
| 4 | A modded mob, if any are installed | It still swings; if it draws its weapon its own way, its arm pose is the old one. |


## Counter and dodge fidelity (2026-10-09)

| Check | Expected |
|---|---|
| Start a matching light attack from neutral while a dummy attacks you, then repeat after raising guard | The neutral attack can be hit; a correctly timed guard-origin attack counters. |
| Counter repeatedly with partly depleted stamina; compare ordinary blocks and a wrong counter caught by forgiveness | A true counter has no incoming block charge or refund. Ordinary and forgiven blocks still cost stamina; other action costs retain their existing behavior. |
| Dodge during a light windup, then during a heavy upgrade, jab, and kick | The ordinary light windup can be abandoned. The other three remain committed, with no dodge movement or stamina/cooldown/queued-input change on rejection. |
| Fight a bot that counters and counter-feints | Its defensive matching attack starts from guard and can still follow a feint into another attack family. |
| Repeat guard-to-counter inputs on a LAN connection with latency | Prediction and authoritative correction agree on guard origin; delayed guards and expired counter windows still use the existing lag grace. |

The existing dodge timing for unconnected light counter attempts is retained. Final Chivalry timing for the late-attempt cutoff needs a separate measured comparison.

## Exhaustion, unarmed blows and shields (added 2026-10-09)

| # | Check | Expected |
|---|---|---|
| 1 | Spend all your stamina (feints, holding block) | The bar pulses red until about a quarter has come back; feints, morphs and dashes don't work meanwhile. |
| 2 | While exhausted, block a zombie's or a soldier's blow | Your guard breaks: disarmed and staggered (with a shield: lowered, can't raise it for a moment). |
| 3 | Punch a winding-up mob with an empty hand | It keeps swinging. With a sword in hand the same hit interrupts it. |
| 4 | Block a few hits with a shield, then the same hits with a sword guard | The shield costs much less stamina. |
| 5 | Let a skeleton shoot your raised shield with an empty bar | The arrow is blocked and the shield stays up. |

## Beta regression checks (2026-10-09)

Automated: `RecoveryCommitmentTest`, the timing HUD recovery/weapon-combo checks,
`landedNonweaponActionsRetainRecovery`, `trainingDummyDisarmDoesNotDuplicateEquipment`,
`aFinishedOffPlayerCannotBeRevived`, `aDeadPatientCannotFinishARevive`, and the real-husk disarm test.
The three reported bugs failed before their fixes; all four targeted mutation checks are caught.

| Check | Expected |
|---|---|
| Land a jab, kick or special, then immediately request a slash | The timing HUD stays in recovery and the slash waits; a normal unblocked weapon swing still combos after a hit or miss. |
| Give a dummy valuable practice gear, disarm it, rearm and repeat | The dummy loses its copied weapon without spawning collectible loot; disarming a real armed mob still drops its weapon. |
| LAN: begin reviving a downed ally, then finish the ally off | Crawl/revive presentation clears and the nearby reviver cannot bring the corpse back. |

Weapon-family style directions and remaining animation gates are in [combat-animation-goal.md](combat-animation-goal.md).
For pose-sheet motion evidence use a disposable world. Staging clears player effects; GUI/camera changes, equipping,
teleporting, platform building and nearby-entity cleanup also affect that world. Keep particles and camera effects out
of uniformly timed sequences, and do not infer live transition/latency correctness from frozen pose sheets.

## Weapon-family style and ready-state checks (2026-10-09)

`AnimationFilesTest` verifies all shipped light/heavy windup and recovery seams. `AnimationSetValidationTest`
covers ready yaw/pitch defaults, finite bounds and metadata retention when clips inherit. The staged client
`-PposeSheet=downed` scene checks both ready-pose eligibility and ready-layer activation; its two guards were
individually removed and the actual client failed for the intended reason. This does not replace a LAN downing test.

| Check | Expected |
|---|---|
| First person with each family, empty offhand | Distinct ready position; sword keeps its familiar stance, spear/rapier use a narrower preparation and greatsword a higher one. |
| Light/heavy slash, overhead and stab, both sides, first/third person | Family preparation and follow-through are readable; the windup/release and release/recovery boundaries do not snap. |
| Downed with a melee weapon; then revived or finished off | The standing ready override clears immediately. Revival restores eligible presentation; death cannot keep a ready layer. |
| Left main arm, shield/torch in offhand, default/slim skins; Sodium on/off | Ready yaw mirrors appropriately, occupied offhand stays visible and grip/clip behavior remains usable. |
| Watch armed humanoid mobs with debug blade on | Body style must preserve release-blade alignment; check long weapons and strong torso leans closely. |

Static comparisons: 351 original-asset and 351 authored-style poses in the isolated evidence directory. Ready
and body changes are visible in focused diffs. Uniform motion captures, live transitions and the full model/renderer
matrix are still pending; static comparisons are not a smoothness verdict.

## Rejected action replacement (2026-10-09)

`ActionReplacementGameTests` reproduces C01. Rejected jab replacements under the post-disarm cooldown, holster,
or downed state must preserve the complete attack snapshot, stamina, queued input and hit bookkeeping. Eligible
jab/kick replacements must still start and pay once, including at exact cooldown expiry and the kick holster
exception. A recording server packet listener verifies actual correction dispatch through `ModNetwork` to the
owning player; it does not emulate round-trip latency or connection negotiation.

Manual LAN check: disarm, equip a spare weapon after the stagger, start a slash while the jab cooldown remains,
then press jab. The slash continues, the feint is not charged, and prediction returns to the authoritative slash.
Repeat rejected morph/replacement inputs under latency and watch for stale animation or queued-input behavior.

## Trustworthy pose-sheet captures (2026-10-09)

Moving captures assert that the actual model sample matches the requested shot, camera, phase/progress and held
item. Missing requested mobs and unknown items abort instead of producing mislabeled substitute images. Mob
captures hide the local player's hands, including idle reference views. Preserve the console log with the images.

Add `-PposeSheetInspectModel` to log the held item, resolved model class and render-pass sprite IDs. This is a
read-only diagnostic for distinguishing asset selection from pose/rig issues. Two actual-client mutations
(stale shot index and wrong recorded item) failed at the intended capture guard; temporary source was restored.

Reviewed evidence includes 50 ms dagger sequences and a 10 ms release/recovery subwindow, plus nine-family husk
release sheets with debug blades. Fine-window motion flags are not an isolated-snap verdict; hilt/tip calibration,
other humanoid models, handedness/offhand, renderer combinations and live transitions still need acceptance work.

## Combat config synchronization (2026-10-10)

`ConfigSyncGameTests` verifies actual login-file selection, exclusion of the client-preferences file, received
values replacing cached gameplay values, guard/stamina callers using them, and no local file overwrite.
The tests call the framework serializer/receiver directly; a negotiated two-client session remains to check.
An isolated GameTest world also verified per-world override loading with unchanged global/override file hashes.
Use the same current build on the server and guests for the following manual checks:

| Check | Expected |
|---|---|
| Before startup, give the host `defense.blockMode = "TIMED"`, `offense.turnCapDegreesPerSecond = 137.0` and `network.maxRewindMs = 123`; give the guest different local values. Join LAN/dedicated server and enable debug. | Guest guard duration, camera limit and rewind calculation follow the host. The guest's local combat TOML remains unchanged. |
| Give host and guest different `steelclash-client.toml` controls, HUD and camera preferences. | Each retains their own preferences. The guest cannot edit server rules through the config screen. |
| Add `serverconfig/steelclash-common.toml` to a disposable world, with different rules from its global file; restart and join. | That world's rules take priority and sync. A different world without that override uses the global file. |
| Leave and join another server/world with different combat settings. | The next session uses its own authoritative settings, with no previous-session rule leakage. |

Change combat rules between sessions. Login sync does not update already-connected guests after later file edits;
restart the world/server and reconnect guests. Existing `config/steelclash-common.toml` settings still load.

Recorded verification: build/186 JUnit tests, 164 GameTests in each optional-mod variant, 164 in a separate
world-override fixture with unchanged file hashes, and 2/2 targeted mutations caught. Client startup plus
three staged slash captures passed. Logs and fixture evidence: `C:/dev/steelclash-beta-audit/20261010/config-sync`.

## Heavy motion and delayed reactions (2026-10-10)

Original/authored heavy greatsword and halberd overheads now have 53 frames per view at 50 ms, plus 20 ms
windup/recovery windows, in back/front/first-person views. Requested samples and runtime asset identity were
verified. Selected flags spread across neighboring frames; no isolated snap was confirmed. These are frozen
base-speed phase samples, not live transitions or a model/offhand/renderer acceptance pass. Evidence and sheets:
`C:/dev/steelclash-beta-audit/20261010/heavy-motion` and `heavy-motion-fine`.

`SpecialGraceGameTests` reproduces C02 and guards captured-type reactions, ordinary hits and grace-period
guard arrival. `GearGameTests.sidearmDrawPrecedesTheBrainsAttackChoice` checks actual entity-tick dispatch and
retains the no-swap-during-an-attack rule. Remaining real-client checks:

| Check | Expected |
|---|---|
| On a lagged LAN client, take an unguarded sword/polearm special after its grace period. | Damage and special stagger arrive together; attacker's newer action does not change that reaction. |
| Repeat with a normal slash, and with a guard raised during a special's grace period. | Ordinary hits do not gain special stagger; the blocked special retains its guard penalty. |
| Close to a skeleton/stray/bogged with a bow and stowed sidearm, then retreat beyond nine blocks. | It draws melee gear before beginning a new close attack and later stows it, respecting current attacks/cooldown. |
| Greatsword/halberd heavy overheads, live first/third person, empty/shield/torch offhand and both main arms. | Phase curves remain readable; actual entry/return and arm ownership need live acceptance. |

Fresh verification: restored build/186 JUnit tests, 168 GameTests in each optional-mod variant, and all four
targeted mutants caught by their intended tests. Pre-fix C02 and sidearm-order failures are retained with the
final logs under `C:/dev/steelclash-beta-audit/20261010/special-grace`.

## Ground slam pet protection (2026-10-10)

Three full-mace-special GameTests cover the attacker's pet, a co-op ally's pet, and an enemy-owned pet with
co-op disabled. A wild-wolf control receives the slam in every case. Protection includes health/stamina,
stagger and velocity; enemy eligibility remains. Both friendly cases failed before the fix. Fresh restored
build: 186 JUnit tests; 171 GameTests per optional-mod variant; 3/3 intended mutations caught.
Evidence: `C:/dev/steelclash-beta-audit/20261010/pet-slam`.

| Live check | Expected |
|---|---|
| Mace slam beside your tame wolf, without scoreboard teams. | No pet stamina drain, stagger or knockback; nearby enemies still receive the area effect. |
| LAN co-op: slam beside your friend's tame wolf. | The friend's pet receives the same protection. |
| With co-op disabled and no allied team, slam beside an opponent's tame wolf. | It remains an eligible enemy target. |

## Representative motion coverage by family (2026-10-10)

Matched original/authored back/front/first-person sequences are available at 50 ms for one representative action
per family. This table describes sampled phase curves; it does not mark live transition or renderer acceptance done.

| Family | Representative action | Samples per view/version | Evidence folder under `C:/dev/steelclash-beta-audit` |
|---|---|---:|---|
| Dagger | Slash | 32 | `20261009/weapon-styles/motion/dagger-*` |
| Sword | Slash | 34 | `20261010/family-motion/sword-*` |
| Two-handed | Heavy overhead | 53 | `20261010/heavy-motion/two_handed-*` |
| Axe | Heavy overhead | 45 | `20261010/family-motion/axe-*` |
| Blunt | Heavy overhead | 43 | `20261010/family-motion/blunt-*` |
| Polearm | Heavy overhead | 53 | `20261010/heavy-motion/polearm-*` |
| Spear | Stab | 39 | `20261010/family-motion/spear-*` |
| Rapier | Stab | 32 | `20261010/family-motion/rapier-*` |
| Staff | Slash | 32 | `20261010/family-motion/staff-*` |

The new six-family batch produced 1,350 requested-sample-verified screenshots and 36 onion reports. Focused sheets
inspected newly flagged intervals without establishing an isolated snap, so curves/timings were preserved.
The original-resource override and final current-resource restoration were byte-verified. Additional 10 ms dagger
and 20 ms greatsword/polearm windows are documented above. Remaining: live blends/corrections/hit-stop, other
actions/variants, explicit main-arm/offhand/model control, humanoid alignment calibration and renderer combinations.

## Controlled rig captures (2026-10-10)

`-PposeSheetArm=left|right` and `-PposeSheetOffhand=<item-id>` select main-arm and offhand conditions.
Use `minecraft:air` for an empty hand. Omitted properties preserve existing conditions. The capture guard checks
actual rendered arm/item ownership and rejects assigning an occupied offhand to the two-handed grip. Each shot
logs entity type, main arm, offhand, actual skin model/texture, armor, pose handedness and grip mode.

Evidence: `C:/dev/steelclash-beta-audit/20261010/rig-matrix`. The initial matrix has 312 verified frozen samples:
left/right sword with empty/shield hands; left polearm with empty/torch hand; husk, skeleton, vindicator and piglin.
Four actions (slash, mirrored slash, heavy overhead, stab) use windup 0.6, release 0.35 and recovery 0.4.
Dev1 is SLIM Ari, with an iron helmet and no other armor. A separate SteelQA1 run logs WIDE Ari and adds 36
left-main-arm/shield samples. These are partial combinations, not an exhaustive skin/entity/renderer matrix.

Two actual-client mutations (omitting the main-arm setter or offhand setter) failed at the requested-rig guard.
The mutation runner could not extract test names from client crashes; exact diagnostic/stack checks were audited
separately in `mutation-diagnostic-audit.json`. Baseline client capture and restored full build passed (186 JUnit
tests). After suppressing capture-subject fire and stage mob loot, a fresh 24-shot skeleton run passed; inspected
release sheets are clear of flames. This cleanup affects the disposable capture stage, not combat behavior.

A 15-shot idle/guard/attack comparison shows the torch largely below the first-person view during attacks,
although the occupied-hand ownership checks pass. Torch visibility needs a rendering fix/review. Shield poses
and slim/wide hand ownership look reasonable at the sampled points; exact blade/hilt calibration, live blends,
Sodium combinations, movement/armor/custom models and further actions remain open. Server tests were not rerun
for this client development-tool change; the last gameplay checkpoint passed 171 GameTests per dependency variant.

## Optional local loading mods (2026-10-10)

The user-supplied Ksyxis 1.4.6, FastQuit 3.0.1, Lightspeed 1.21.1-2.0.2hotx2 and ModernFix
5.27.26+mc1.21.1, Smooth Boot 1.0.0 and FerriteCore 7.0.3 are installed in `run/mods`, `run-client2/mods` and the isolated animation QA client's
`mods` folder. FastQuit requires Cloth Config; the matching NeoForge 15.0.140 jar is also installed.
Jars stay local/ignored rather than becoming Steelclash release dependencies. Startup/world loading,
three requested pose captures and completed world saving passed with all mod ids present in the loader log.
Evidence and file hashes: `C:/dev/steelclash-beta-audit/20261010/loading-mods`.

When comparing future captures, match this addon stack in both versions and record the actual mod list.
The normal dev runtime also includes Sodium unless `-PnoSodium` is passed; earlier visual success must not be
interpreted as a Sodium-disabled check. These additions do not change the isolated GameTest runtime.

## Carried offhand visibility (2026-10-10)

The torch composition issue found in the controlled-rig checkpoint is addressed by a camera-relative carried
arm for occupied non-shield hands during first-person attacks. It counters body turns and stays raised through
recovery. Shields and the weapon arm retain their existing paths. Fixed/reverted runs match the initial loading
addons, Sodium 0.8.13, SLIM Ari with an iron helmet, 1920x1200 viewport, stored FOV 0.625 and cameraMotion 0.
Evidence is in `C:/dev/steelclash-beta-audit/20261010/torch-visibility`, with run IDs in `matching-stack.json`.

The matched runs contain 144 static samples each and 47 polearm-slash frames per view/version at 50 ms.
No meaningful change was found in 96 third-person pairs or the 36 shield-control pairs. Torch windup/release/
recovery composition improves in both sword main-arm preferences and the occupied-hand halberd. The selected
new onion flags have gradual neighboring motion; full live transition acceptance is still required.

Restored build: 187 fresh JUnit tests passed. Two valid mutations were caught: missing inverse body rotation
by `carriedHandStaysForwardDespiteBodyTurnAndViewAim`, and disabling the holding branch by the actual-client
fixed-scene image assertion. Its declared region contains 978 torch-head pixels when fixed and zero when
reverted. This is a specific camera/model/pose test, not a general detector for every item or FOV. The helper's
unittest-name limitation and the separate named diagnostic audit are recorded in `spikes.md`.

After adding Smooth Boot and FerriteCore, three fresh heavy-overhead views plus startup/completed saving passed
with the final seven local mods/dependency jars, both with Sodium and without it. This establishes that selected
scene in both renderer variants; it does not complete the full model/action/renderer matrix.

| Live follow-up | Expected |
|---|---|
| Torch plus sword/polearm, both main-arm preferences: attack from idle and return; feint/combo. | The carried item remains readable without a single-frame handover pop. |
| Turn through +/-180 degrees and look up/down while attacking; try different FOVs. | The carried arm follows the view without a full-turn rewind or excessive central occlusion. |
| Swap/use the offhand item during an attack; switch empty/torch/shield. | Ownership changes cleanly, and shields keep their guard/bash choreography. |
| Repeat with wide/armored/custom models while crouching, moving or mounted. | Check clipping and hand attachment; these combinations are not established by the frozen matrix. |

## Live keyboard transition review (2026-10-10)

The new `-PliveCapture=<scene>` diagnostic records actual input-driven rendering, including normal prediction
and integrated-server packets. Commands and side effects are in [animation.md](animation.md#live-capture-diagnostic).
Successful captures require actual client/server outcomes and complete PNG files before shutdown. Fresh output
directories prevent stale frames. Evidence: `C:/dev/steelclash-beta-audit/20261010/live-transitions`.

Final-source first-person scenes use an iron sword, right main arm, SLIM Ari, iron helmet, 1920x1200 viewport,
actual FOV 95, cameraMotion 0, the seven local loading/dependency jars and the standard dev classpath. Sodium's outer jar was discovered, but the runtime renderer probe reports it absent; active Sodium compatibility remains
open. Rendering caps at 20 FPS;
actual median intervals are about 50 ms (observed range across these runs approximately 39-63 ms). Keep the
recorded timestamps; do not label these as exact 50 ms frozen samples or treat every onion flag as a snap.

| Scene | Actual frames | Confirmed client/server outcome |
|---|---:|---|
| Attack, empty offhand | 139 | One attack serial; release reached. |
| Attack, shield | 138 | One attack; final idle returns to vanilla without the reproduced helmet flash. |
| Combo, torch | 139 | Two attack serials, with a stab buffered during the slash release. |
| Heavy upgrade, torch | 139 | Heavy flag and release observed on both sides. |
| Feint, torch | 138 | Windup then idle; neither side entered release. |
| Morph, torch | 139 | Morph flag observed on both sides after slash-to-stab input. |
| Held guard, torch | 138 | Guard observed on both sides, followed by lowering and idle. |

The torch ready stance removes the vanilla/model replacement at attack entry and the large return-to-idle
jump. The shield control identified the independent camera-mode/armor visibility inconsistency; stable PAL
ownership removes its one-frame helmet occlusion. Before/after sheets, actual timestamps, onion reports and
reversion diagnostics are saved. Selected combo/heavy/feint/morph neighbors show continuous pose handover;
this is scoped visual evidence, not completion of every transition acceptance case.

Two valid actual-client mutations were caught by their intended named assertions (audited separately because
the helper does not extract unittest names). Remaining live work includes hit-stop and authoritative interrupts,
counters/ripostes against an opponent, weapon-to-kick, item use/swap, reload/tracking, left main arm, wide/custom
models, movement/mounting, low/high FPS and real LAN latency/correction. Frozen family/blade evidence does not
close these gates, and the overall beta goal remains active.

Final restored build passed all 187 JUnit tests (25 fresh XML reports, no skipped/stale tests). The three-shot
downed ready-stance/layer-activation guard passed after the ownership change. A fresh `-PnoSodium` torch live
attack captured 138 model/ready frames and completed normally. Its success does not prove Sodium-on rendering;
the explicit runtime probe found Sodium's renderer class absent in the standard dev run.

## Optional integration candidates (2026-10-10)

The [kinematics review](kinematics-compatibility-review.md#integration-design-and-acceptance-gates) defines the
required future checks for server anatomical contacts, one injury backend, and PAL More Rotation/CPM adapters.
Research/source inspection is complete for the stated scope; installation, dedicated-server loading, blocked-hit
injuries, double damage/headshot reduction, death/downed/revive and custom-model alignment remain unverified.
Run these checks only once an adapter is implemented; the current 187 unit tests do not establish candidate
compatibility. Verify active renderer identity when comparing Sodium runs.

## Verified renderer and live opponent checkpoint (2026-10-10)

The corrected default dev dependencies load the actual Sodium renderer and its boot workarounds. Use
`-PliveCaptureRenderer=sodium`, or `-PnoSodium -PliveCaptureRenderer=vanilla`, to make renderer identity a required
condition. The official unwrapped mod, service wrapper and matching FFA modules remain dev-only dependencies.

Fresh first-person torch and shield controls pass with both verified renderer stacks (139 frames per scene).
Torch idle retains model/ready presence. Shield return releases model ownership and stays below 0.43% upper-half
gray pixels in the helmet assertion. This establishes actual Sodium use, not merely a discovered jar.

The ordinary-input `riposte` scene passes with vanilla (138 frames) and Sodium (139). Both sides observe the raised
guard, caught parry and active-parry return attack. The rendered NoAI husk swings once, is staggered by the block,
and loses health from 20 to 12.915199; the survival player retains 20 health. Command/world side effects and the
capture boundary are documented in [animation.md](animation.md#live-capture-diagnostic). This is an integrated-server
scripted exchange, not autonomous AI, real LAN or all defensive transitions.

Evidence and reproducible assertion runner: `C:/dev/steelclash-beta-audit/20261010/renderer-baseline`.
Keep actual timestamps when reviewing the motion. Contact sparks, hurt tint and telegraph labels affect onion
flags; inspect neighboring poses rather than calling all flagged image changes animation snaps.

Verification: fresh restored build, 187 JUnit tests (25 fresh reports, zero failed/skipped/stale), and all 171
required GameTests in each Spartan dependency variant. The initial global graphics-bootstrap startup failure
was repaired by client-only scope; both final headless runs completed. Two valid live mutations were caught;
the wrapped-dependency mutation was repeated after the final client-only scoping change and was caught again.
Saved diagnostics distinguish the intended renderer/outcome failures from compile or unrelated startup errors.

## Carried idle, counter/thwack and C04/C09 (2026-10-10)

Evidence: `C:/dev/steelclash-beta-audit/20261010/next-live-offhand`. Fresh baseline reproducers fail at their desired
assertions for wall/floor slam effects and real-special hard-stagger downgrade, alongside three core precedence
failures. The cover policy and stagger precedence are now explicit in PLAN.md and fixed in production code.

The final eight active-Sodium captures include left/right torch idle, shield/empty idle controls, torch attack
handover, matching counter, light-mace thwack and bread idle (1,111 actual frames). Conditions: SLIM Ari, iron
helmet, 1920x1200, FOV 95, camera motion 0, 20 FPS cap; main arm/equipment and actual frame times are recorded.
Main-arm changes use normal settings synchronization and restore after capture. The first stale-actor hit-stop
prototype is excluded; the rebound and final cases track the newly staged living husk.

Rendered torch idle is no longer rigid: pitch range is approximately 1.72 degrees, largest frame step about
0.065 degrees on both main arms. Both matched 50-frame onion windows have no flags. The subtle motion retains
item visibility and mirrored holding geometry, with fade-to-attack rather than a second weapon-arm solve.
Shield/empty controls and the torch attack-to-idle sequence still pass their relevant scene/ownership checks.

The counter observes success on client/server and return damage without player injury. The blunt scene observes
client/server thwack and a surviving target. Its visual sample holds through an advancing simulated phase and
then resumes, as recorded in `visual-hold-audit.json`. This covers the specific default exchange, not all hit-stop
correction, FPS, model replacement or real LAN behavior.

Remaining manual checks: idle while turning/moving/crouching, flat/large/custom items and models, item use/swap,
other FOV/FPS, incoming interruptions, reload/tracking and LAN correction. Existing original-family style and
blade calibration gates remain visible in the goal document.

Restored verification: 193 fresh JUnit tests (26 XML reports, zero failed/skipped/stale) and all 174 required
GameTests with Spartan integrations and all 174 without. Four valid mutations were caught by the intended core,
wall/floor and actual-client assertions. The renderer-hook mutation has a separate saved-frame audit because
the mutation helper does not extract unittest names: moving baseline versus zero-variation reverted solve.

Vanilla-renderer torch idle/attack were also replayed. The first idle audit found seven setup-only `model_seen`
flags false despite actual carried-bone transforms continuing. This was an observer-order issue; presence is now
recorded in the render-bone callback instead of setup. The affected controls were repeated with that probe.
Animation/core geometry and server behavior were not changed to repair the diagnostic.

Rendered arm angles include legitimate view/body compensation. The final vanilla motion audit therefore records
the rendered matrix relative to the static carry solve (`carried_idle_delta`) and bounds that breathing component,
rather than treating all local Euler-angle changes as idle snaps. The final idle replay has zero render-presence
misses, roughly 0.03 radians relative pitch range and maximum relative frame step about 0.00114 radians. The
ordinary torch attack replay also retains idle model/ready ownership. This observer/reference work changes no
animation math or gameplay rules.

The final camera-relative renderer assertion was mutation-checked again: moving baseline passed; reverting the
renderer call to the static solve failed. The original four-rule report is retained as `all-rule-mutants.report.json`,
with the final renderer rerun in `render-reference-mutation.log`. All temporary code was restored before compilation.
