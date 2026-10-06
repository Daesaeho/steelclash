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
| 1 | Sword in hand: right-click (hold) | Weapon parry raises (`/steelclash_debug`: blue). It drops by itself after ~0.6 s, or when you let go. |
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
