# Steel Clash player guide

Steel Clash replaces vanilla's click-spam melee with Chivalry 2's rules. Every attack telegraphs, and every attack can be answered. In game, `/steelclash_help` prints a short version of this guide with your own keybinds.

## Attacking

Each attack goes through three phases:

1. **Windup.** You can see it coming. You can still change your mind.
2. **Release.** The blade is live and hits whatever it sweeps through, up to the weapon's target limit.
3. **Recovery.** You're committed; you can't parry yet.

| Attack | Default key | Notes |
|---|---|---|
| Slash | Left mouse | Wide horizontal arc. As in Chivalry 2, each slash comes from the other side; turning while you attack picks the side instead (turning right swings from the left). |
| Overhead | Mouse 5 or scroll up | Slower, harder, vertical. |
| Stab | Mouse 4 or scroll down | Fast, narrow, the longest reach. |

**Control schemes** (client config `controlScheme`): the default `CHIVALRY` scheme works like Chivalry 2, with one slash key and right click to parry. `TWO_SLASH_KEYS` gives each side its own key instead: left click slashes right to left, right click slashes left to right, and parry moves to middle click. Switching rebinds those keys once; you can rebind anything afterwards.

Turning while you swing drags the arc with you, as in Chivalry 2:

- **Accel:** turn *with* the swing and the blade connects early in the release, cutting the defender's reaction time.
- **Drag:** turn *against* it and the blade connects late, after an early parry or counter has been used up.
- **Turn cap:** during windup and release you can only turn 360°/s (common config `turnCapDegreesPerSecond`, 0 = no limit). Accels and drags still work, but you can't flick 180° onto someone behind you. The camera itself is held to the cap, so what you see is what hits.

**Attack side from movement** (client config `sideFromMovement`, off by default): while strafing, swings whose side isn't fixed by their key take it from your movement. `FROM_STRAFE_SIDE`: strafing left swings from the left. `TOWARD_STRAFE`: strafing left swings toward the left.

- **Heavy:** hold the attack key. It winds up longer and hits harder, drains more of a blocker's stamina, and some weapons get hyper armour (they don't flinch).
- **Feint** (X during a windup): cancels the attack. It costs stamina, and it baits parries.
- **Morph:** press a *different* attack key during a windup to switch to that attack.
- **Combo:** land a hit and your next attack can start during recovery, alternating sides.
- **Flinch:** taking damage during your windup interrupts it (except a heavy with hyper armour).
- **Lunge:** attack while sprinting for extra reach and damage.
- **Jump attack:** an overhead started in mid-air hits harder.
- **Gesture attacks (experimental, client config `gestureAttacks`):** hold left click and drag the mouse toward where the attack should come from. Drag left for a slash from the left, right for a slash from the right, up for an overhead, down for a stab. A plain click still slashes, and holding on after the gesture makes it a heavy. Everything is customizable:
  - `gestureLeft`, `gestureRight`, `gestureUp` and `gestureDown` set each direction to a slash from the left, a slash from the right, an overhead, a stab, a kick, or NONE (ignore that direction). For example, swap them to "follow the blade" so that dragging left swings right to left.
  - `gestureKeys` chooses which key reads gestures: the slash key, the second slash key (`TWO_SLASH_KEYS`), or both.
  - `gestureLockView` freezes your view while you gesture, so the drag only picks the attack and doesn't turn you.
  - `gestureThreshold` sets how far you must drag, and `gestureWindowTicks` how long a still hold waits before slashing.
- **Clank:** a blade that hits a wall stops and you reel. Mind the corridors.

## Defending

- **Parry** (right mouse; middle mouse in `TWO_SLASH_KEYS`): raise your guard just before the blade lands. Each weapon has its own parry cone, so attacks from behind get through. One parry can catch several hits. After a parry there's a short delay before you can raise it again.
- **Riposte:** attack right after a successful parry and your attack winds up much faster.
- **Counter:** answer an attack with the **same** attack type, started just after theirs. Their attack is parried and yours lands first.
- **Shields:** with a shield in your offhand, the parry key raises it. Shields hold for as long as you keep the key down, but every block costs stamina. Tower shields (Spartan Shields) cover more and stop arrows from the front only.
- **Kick / shield bash** (Z): can't be parried. It breaks a raised parry or shield and staggers the target. Use it on turtles.

## Footwork

- **You're slower while fighting:**

  | While | Speed |
  |---|---|
  | Winding up or releasing an attack | 65% |
  | Recovering | 85% |
  | Holding a weapon parry | 75% |
  | Staggered | 50% |
  | Backpedalling with a weapon | 80% |

  Shields use vanilla's own item-use slowdown. Position yourself *before* you swing.
- **Duck under slashes:** crouch and you only count about one block tall to blades, so level slashes pass over you. Overheads, kicks and slashes aimed downward still hit. An attacker who sees you duck can look down and catch you.
- **Health regeneration:** after 5 seconds without taking damage, your health comes back at half a heart per second, whatever your hunger. Disengage to recover.

All of this is in the `movement` and `health` sections of the common config.

## Stamina

Attacking, whiffing, feinting, parrying and blocking all cost stamina (the bar above your hotbar). It regenerates after a short pause. **Parrying or blocking with no stamina left breaks your guard.** A parry knocks your weapon out of your hand (walk over it to pick it back up); a shield is lowered and goes on cooldown. Mobs follow the same rule, so drain a knight's stamina to disarm him.

## Weapons

There are 12 archetypes, each with its own timings, arcs, reach, parry cone and special:

- Swords are balanced.
- Rapiers are fast, light stabbers.
- Daggers are very fast with short reach.
- Axes chop.
- Blunt weapons crush armour.
- Spears and polearms have long reach.
- Greatswords hit hard and slowly.

Damage types (**cut**, **blunt**, **chop**) do more or less damage depending on the target's armour: cuts glance off plate, and blunt weapons don't care. Hover a weapon to see its profile in the tooltip.

- **Special** (R): an archetype move with a cooldown, such as the sword lunge, the hammer slam or the polearm sweep.
- **Throw** (G): throws your weapon. It does real damage and drops where it lands.
- **Mounted:** stabs from a moving mount hit harder the faster you ride (couched lance).

## Enemies

Zombies, skeletons, piglins, vindicators, endermen, spiders, ravagers, golems and others **telegraph their attacks** and can be parried. With the bot brain on (the default), they:

- keep their distance and circle;
- parry with reaction times that depend on difficulty (Easy is slow, Hard is sharp);
- feint, riposte, and kick players who hide behind shields;
- accel and drag their slashes by turning their heads mid-swing (Normal now and then, Hard often), so watch the blade, not just the windup;
- notice your habits: feint too often and they parry later, spam one attack and they start countering it;
- take turns in groups (1/2/3 attackers at once on Easy/Normal/Hard) so big fights stay readable.

Mobs spawn armed far more often than in vanilla, and Spartan weapons join the pools if installed.

**Brigands** are human soldiers:

- footmen (balanced duelists);
- knights (armoured rushers, often with a shield);
- archers (keep their distance).

They spawn at night, roam in **patrols** led by a knight, and guard **brigand camps** with loot (`/locate structure steelclash:brigand_camp`).

## The training dummy

Craft it with a carved pumpkin on top, a hay bale in the middle with a stick on each side, and a stick below. Place it like a spawn egg. It can't die.

- Sneak + use with an **item** to arm it (shields go in its offhand).
- Sneak + use with an **empty hand** to cycle modes:
  - **Passive:** takes hits.
  - **Parry:** parries everything; practise feints, kicks and disarms.
  - **Attack:** slashes at you every few seconds; practise parry timing.
  - **Spar:** random attacks, parries half the time.

## HUD

- **Stamina bar** above the hotbar.
- **Timing bar** under the crosshair: heavy charge, windup, release and recovery, the combo window, parry duration, guard recovery, stagger, and the riposte window.
- Optional **telegraph labels** over enemies (client config `enemyTelegraphs`) to learn their wind-ups.
- `/steelclash_debug` draws every blade and arc. In multiplayer it also shows your ping and lag compensation.

## Multiplayer

Co-op works, with lag compensation. When you have a high ping, your swings hit what your screen showed. Hits on you wait (up to a round trip, 250 ms at most) so your parry can still arrive. Server owners can tune or disable this in the `network` section of the common config.

## Settings worth knowing

*Mods → Steel Clash → Config*:

- Disarm mode (drop or holster)
- Stamina costs
- Parry and riposte windows
- Mob parry chances and attacker counts
- Armed mob chances
- Camera motion
- Hit-stop
- Scroll attacks
- Two-handed sword grip
