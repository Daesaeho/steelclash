# Unreleased

**Combat**
- Chivalry 2 timings: every built-in weapon is retimed from the game's own data (as published by polehammer.net), in milliseconds. Windups include the 350 ms chamber, releases are about twice as long as before, recoveries are longer, and combos and ripostes have their own timings. Heavies add a fixed ~250 ms windup and a slightly longer recovery, ripostes and combos included. Overhead and stab damage follow the game's ratios to the slash.
- Combos after a miss (Chivalry 2): any attack that isn't blocked can be comboed, whiffs included. Blocked and parried attacks still can't.
- Weapon profiles take `combo_ms`, `riposte_ms` and `heavy.windup_extra_ms` / `heavy.recovery_extra_ms`.
- Projectile defence (Chivalry 2): starting an attack just as an arrow or thrown weapon arrives (0.25 s) deflects it; a held weapon guard takes 30% off and never breaks to arrows; shields still stop them.
- Projectile headshots deal 25% more, with a ding for the shooter.
- Getting hurt while drawing a bow or loading a crossbow loses the draw.

**Co-op**
- Team rules (Chivalry 2): all players are allies by default (`playersAreAllies`), or scoreboard teams decide. Allies' bodies are in the way of your swings: slashes carry on through them, stabs stop in them. Allies take 25% damage (`friendlyDamageScale`, 0 = off), their guards never parry you, and kicks, jabs and slams pass them by. **To duel another player, set `playersAreAllies = false`.**

**Mobs**
- Archer sidearms (Chivalry 2): skeletons, strays and bogged carry a dagger next to their bow. They draw it when you close within 4 blocks and go back to the bow once you're 9 blocks away. The daggers are Spartan Weaponry's; set with `#steelclash:sidearm_users`, `#steelclash:mob_sidearms/tier_1..3` and `sidearmChance`.

# 0.2.0-beta

**Combat**
- Cleave and thwack (Chivalry 2): cut and chop swings carry on through bodies up to their target limit. Light blunt swings stop in the first body (unless it dies) and recover straight from the impact, so a landed mace hit is over sooner. Heavies always cleave. Set per attack in weapon profiles (`contact`, `thwack_ms`).
- Counter-feints (Chivalry 2): while countering, follow the attacker's feint into their new attack, even after an earlier morph. With the two-slash-key scheme you can also switch the counter slash to the other side for a second try at the timing. Bots counter-feint after their reaction time.
- Sub-tick combat timing: attack phases are timed in microseconds and resolved inside each server tick, so weapon timings (and attack-speed differences) no longer round to 50 ms steps. Weapon profiles can give timings in milliseconds (`windup_ms`, `release_ms`, `recovery_ms`).
- More Chivalry 2 rules:
  - feint into a kick or jab;
  - jabs block jabs, and kicks block kicks;
  - kicks don't interrupt an attacker;
  - attacks pressed during a stagger start when it ends;
  - counter windows depend on the attacker's weapon speed;
  - jumping costs stamina mid-fight, and crouching pauses stamina regeneration;
  - chop and blunt weapons drain more stamina from a guard.

**Mobs**
- Animated mobs lean and twist with their whole body, so zombie torsos no longer come apart on some setups.
- Vindicators show every swing.

**Getting started**
- The controls help is shown in chat on every world join (turn off with `helpOnJoin`).

**Performance**
- A combat benchmark (`./gradlew runGameTestServer -Pbench`). With 150 fighting bots, combat stays far inside the server's tick budget.

# 0.1.0-beta

First public beta.

**Combat**
- Chivalry 2 controls by default (one slash key whose side alternates or follows your turn; right click parries), or a two-slash-key scheme with a key for each side; optional, customizable mouse-gesture attacks with an optional view lock.
- Directional attacks (slashes from either side, overhead, stab), each with a windup, release and recovery, traced as a swept blade along a real arc.
- Accels and drags: turning during a swing moves where it connects, under a Chivalry 2 style turn cap (360°/s during windup and release). Bots accel and drag too. Optionally, strafing picks the swing side.
- Chivalry 2 footwork and sustain: slower movement while attacking, parrying or staggered and when backpedalling; ducking under level slashes; health regeneration after six seconds out of combat (attacking or guarding counts as combat), up to 40% of max health.
- Heavies, feints, morphs, combos, flinch, heavy hyper armour, counters (answer an attack with the same type).
- Held weapon block (Chivalry 2): the guard stays up while held and drains stamina slowly; timed parries remain as an option (`blockMode`). Ripostes and counters carry an active parry against other frontal hits. Parry forgiveness turns a last-moment wrong counter into a block. Parry cone, one guard catching several hits. Shields block in a cone (tower shields stop arrows from the front only).
- Dodge (dash in the direction you move, 12 stamina, abandons a windup or guard) and jab (a quick, short interrupt; bots jab your heavies).
- Stamina: attacking, parrying and blocking cost it. Parrying with none left disarms you (or holsters the weapon, in config).
- Kick and shield bash, environment clanks, sprint lunges, jump attacks, weapon specials (lunge, slam, sweep), throwing any weapon, a mounted lance charge, and cut/blunt/chop damage types against armour.
- 12 weapon archetypes; Spartan Weaponry weapons map onto them automatically.

**Mobs**
- Zombies, skeletons, piglins, vindicators and other mobs (`#steelclash:fighters`) telegraph their attacks and can be parried.
- Bot brain: spacing and circling, reaction-time parries by difficulty, feints, ripostes, kicking turtles, adapting to how you fight, and attack tokens so groups take turns.
- Mobs spawn armed far more often (and sometimes with shields and helmets).
- Brigand footmen, knights and archers: night spawns, patrols led by a knight, and brigand camps with loot.

**Feel**
- Procedural first- and third-person swing animation (Player Animation Library): the blade follows the traced arc exactly while the body twists, steps and follows through under it (styled per weapon type: compact daggers, deep axe and mace chops, rapier lunges, wide grips on staves and polearms), the wrist is cocked, two-handed weapons are held with both hands on the grip, and the first-person view keeps the weapon clear of the camera; animated mob arms, sounds, particles, hit-stop and camera sway (adjustable).
- Stamina HUD and timing bar (heavy charge, windows, parry, riposte), weapon tooltips, optional enemy telegraph labels.

**Multiplayer**
- Lag compensation: lagged players' swings hit what they saw, and hits on them wait for their parry to arrive (up to 250 ms).

**Getting started**
- Craftable training dummy with passive, parry, attack and spar modes; `/steelclash_help`; a one-time controls hint; an in-game config screen.
