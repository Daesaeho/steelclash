# Steel Clash player guide

Steel Clash replaces vanilla's click-spam melee with Chivalry 2's rules. Every attack telegraphs, and every attack can be answered. In game, `/steelclash_help` prints a short version of this guide with your own keybinds. It also appears every time you join a world; turn that off with `helpOnJoin` in the client config.

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

- **Heavy:** hold the attack key. It winds up about a quarter of a second longer, recovers a little slower, hits harder and drains more of a blocker's stamina. Some weapons get hyper armour (they don't flinch).
- **Feint** (X during a windup): cancels the attack. It costs stamina, and it baits parries.
- **Morph:** press a *different* attack key during a windup to switch to that attack.
- **Combo:** attack again during your recovery and the next attack replaces it, alternating sides. As in Chivalry 2 this works after a miss too, but not after a blocked or parried attack. A combo winds up a little longer than a fresh attack, but it's much sooner than waiting out the recovery.
- **Flinch:** taking damage during your windup interrupts it (except a heavy with hyper armour). So does a blow during your **release**, as in Chivalry 2: whoever lands first wins the exchange, which is what accels are for. Two blades landing at the same moment (within a twentieth of a second) both hit, a trade. Fire and falls don't cut your swing short. Turn this off with `releaseInterrupt` in the common config.
- **Lunge:** attack while sprinting for extra reach and damage. Miss with it and you recover 0.3 s longer, so don't sprint-swing blindly.
- **Jump attack:** an overhead started in mid-air hits harder.
- **Gesture attacks (experimental, client config `gestureAttacks`):** hold left click and drag the mouse toward where the attack should come from. Drag left for a slash from the left, right for a slash from the right, up for an overhead, down for a stab. A plain click still slashes, and holding on after the gesture makes it a heavy. Everything is customizable:
  - `gestureLeft`, `gestureRight`, `gestureUp` and `gestureDown` set each direction to a slash from the left, a slash from the right, an overhead, a stab, a kick, or NONE (ignore that direction). For example, swap them to "follow the blade" so that dragging left swings right to left.
  - `gestureKeys` chooses which key reads gestures: the slash key, the second slash key (`TWO_SLASH_KEYS`), or both.
  - `gestureLockView` freezes your view while you gesture, so the drag only picks the attack and doesn't turn you.
  - `gestureThreshold` sets how far you must drag, and `gestureWindowTicks` how long a still hold waits before slashing.
- **Clank:** a blade that hits a wall stops and you reel. Mind the corridors.

## Defending

- **Block** (right mouse; middle mouse in `TWO_SLASH_KEYS`): hold it and your weapon guard stays up, as in Chivalry 2. Holding it drains about 4 stamina a second, and stamina doesn't regenerate meanwhile, so don't turtle. Each weapon has its own parry cone, so attacks from behind get through. One guard can catch several hits. After lowering it there's a short delay before you can raise it again. If you prefer timing-based parries, set `blockMode = "TIMED"` in the common config: the guard then drops on its own after a moment. Mobs always parry that way.
- **Riposte:** attack after blocking a hit. Two-handed weapons riposte faster than they normally attack; one-handers riposte at their normal speed. While it winds up and swings, it also parries anyone else hitting your front (**active parry**). That's how you fight two at once.
- **Counter:** answer an attack with the **same** attack type, started just after theirs (from your guard or not). Their attack is parried, yours lands first, and it carries an active parry too.
- **Parry forgiveness:** attack out of your guard with the wrong type, or too late, in the last tenth of a second before a hit lands, and you still block it instead of getting hit.
- **Dodge** (Left Alt): a quick dash in the direction you're moving, or backwards when standing still. It costs 12 stamina, has a one-second cooldown, and drops a windup or a raised guard. You can't dodge out of a swing that's already coming down, out of a counter that has already caught its attack, or while staggered. Dodging a swing makes it whiff, leaving the attacker in recovery. You can't raise your guard until the dash is half done, and a jab straight after a dodge comes out a little slower.
- **Jab** (V): a quick, short thrust for a quarter of your weapon's damage. Use it to interrupt a slow heavy up close. It can be parried, and it can't be feinted, made heavy or cancelled. Two jabs meeting: the one already out blocks the other. Some mobs jab your heavies too.
- **Feint into a kick or jab:** during a weapon windup, press kick or jab and the attack turns into it (costs the feint's stamina).
- **Buffered retaliation:** press an attack while staggered (after being parried, say) and it starts the moment the stagger ends.
- **Shields:** with a shield in your offhand, the parry key raises it. Shields hold for as long as you keep the key down, but every block costs stamina. Tower shields (Spartan Shields) cover more and stop arrows from the front only.
- **Kick / shield bash** (Z): can't be parried. It breaks a raised parry or shield and staggers an idle target. Use it on turtles. It does **not** interrupt someone who is already attacking (they swing straight through it), and two kicks meeting cancel out.
- **Counter-feint:** if the attacker feints into a different attack while you're countering, switch your counter to match it. You get this switch even if you already changed attack once. Your windup starts over, so time it to their new attack. With the two-slash-key scheme you can also switch your counter slash to the other side, which gives you a second try at the timing. It only works against an attack actually coming at you. Bots counter-feint too, once they've seen your new windup.
- **Counter windows depend on the attacker's weapon:** against fast weapons (daggers) you have slightly less time to counter, against slow ones (greatswords, heavies) slightly more.
- **Arrows and thrown weapons** (Chivalry 2):
  - *Counter:* start a slash, overhead or stab just as a projectile reaches you from the front (within a quarter of a second) and it bounces off your blade. Your attack carries on.
  - *Weapon block:* a held weapon guard facing it takes 30% off the damage. It costs a little stamina but never breaks your guard or disarms you.
  - *Shield:* a raised shield stops it outright.
  - *Headshots:* arrows, bolts and thrown weapons that hit the head deal 25% more, and you hear a ding when yours do.
  - *Drawing a bow:* getting hurt while drawing a bow or loading a crossbow loses the draw, for you and for skeletons. Rush archers. Arrows don't interrupt a weapon throw, though.

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
- **Health regeneration:** after 6 seconds out of combat (not hurt, attacking, guarding or blocking), your health comes back at half a heart per second, whatever your hunger, up to 40% of your max health (as in Chivalry 2; `healthRegenCap` in the config). Disengage to recover. Food still heals you the vanilla way.

All of this is in the `movement` and `health` sections of the common config.

## Stamina

As in Chivalry 2: **jumping costs 12 stamina while you're fighting** (within 5 seconds of attacking, guarding or being hit; jumping around outside a fight is free), and **crouching pauses stamina regeneration**. Chop weapons drain 10% more stamina from a guard, blunt ones 25% more.

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

**Cleave and thwack.** Cutting and chopping swings carry on through a crowd, up to the weapon's target limit. A light swing with a blunt weapon stops in the first body it meets (a *thwack*) and you recover from the impact, and a combo can follow straight away; if that hit kills, the swing carries on. Heavy swings always cleave, so a heavy mace reaches the second enemy at the cost of its slower windup. In a crowd, pick which body your blunt light meets first.

- **Special** (R): an archetype move with a cooldown, such as the sword lunge, the hammer slam or the polearm sweep. A special staggers whatever it hits. It can't be countered, and blocking one with your weapon makes *you* reel instead of opening a riposte, so dodge it or step out of reach (a shield still takes it normally).
- **Throw** (G): throws your weapon. It does real damage and drops where it lands.
- **Mounted:** attacks from a moving mount hit harder the faster you ride: stabs and specials the most (couched lance), slashes and overheads half as much.

## Enemies

Zombies, skeletons, piglins, vindicators, endermen, spiders, ravagers, golems and others **telegraph their attacks** and can be parried. With the bot brain on (the default), they:

- keep their distance and circle;
- parry with reaction times that depend on difficulty (Easy is slow, Hard is sharp);
- feint, riposte, and kick players who hide behind shields;
- accel and drag their slashes by turning their heads mid-swing (Normal now and then, Hard often), so watch the blade, not just the windup;
- notice your habits: feint too often and they parry later, spam one attack and they start countering it (and follow a feint with their counter);
- take turns in groups (1/2/3 attackers at once on Easy/Normal/Hard) so big fights stay readable.

Mobs spawn armed far more often than in vanilla, and Spartan weapons join the pools if installed.

**Archers carry a sidearm.** Like Chivalry 2 archers, skeletons, strays and bogged carry a dagger next to their bow. Get within 4 blocks and they put the bow away and fight you with it (parrying, feinting and comboing like any other fighter). Back off past 9 blocks and they go back to shooting. The daggers come from Spartan Weaponry, since vanilla has none; without it, skeletons keep only their bow. `sidearmChance` in the mobs config.

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

**Fighting side by side (Chivalry 2 team rules).** By default all players are on one side (`playersAreAllies`); with it off, only players on the same scoreboard team are.
- Your swings don't ignore allies: their bodies are in the way. Slashes and overheads carry on through them; **stabs stop in the first ally they meet**, so don't stab past a friend's back.
- An ally you hit takes a quarter of the damage (`friendlyDamageScale`, 0 = no friendly fire). Their guard never parries you, and blocking you costs them nothing.
- Kicks, jabs and weapon slams pass allies by, and pets of you and your allies are never hit.
- **Want to duel a friend?** Set `playersAreAllies = false` in the common config (or put yourselves on different teams), otherwise you fight as allies: a quarter damage and no parries.

**Downed, not dead (Chivalry 2).** In multiplayer, a blow that would kill you puts you on the ground instead, as long as an ally is within 48 blocks. Downed, you crawl with 3 hearts, can't fight or use items, and mobs leave you alone. You bleed out after 30 seconds.
- **Revive:** an ally crouches next to you for 3 seconds and you're back up with 30% health. A hit on them restarts it. Both of you see the progress.
- Another lethal blow while you're down **finishes** you. Totems still work first; `/kill`, the void and logging out while downed kill outright.
- Singleplayer (nobody to revive you) works as before. All of it is in the common config (`downed`, `bleedOutSeconds`, `reviveSeconds`, `reviveHealth`, `mobsIgnoreDowned` and more).


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
