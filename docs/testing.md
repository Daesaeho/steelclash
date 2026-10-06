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
