# 0.1.0-beta

First public beta.

**Combat**
- Chivalry 2 controls by default (one slash key whose side alternates or follows your turn; right click parries), or a two-slash-key scheme with a key for each side; optional, customizable mouse-gesture attacks with an optional view lock.
- Directional attacks (slashes from either side, overhead, stab), each with a windup, release and recovery, traced as a swept blade along a real arc.
- Accels and drags: turning during a swing moves where it connects, under a Chivalry 2 style turn cap (360°/s during windup and release). Bots accel and drag too. Optionally, strafing picks the swing side.
- Chivalry 2 footwork and sustain: slower movement while attacking, parrying or staggered and when backpedalling; ducking under level slashes; health regeneration after six seconds out of combat (attacking or guarding counts as combat), up to 40% of max health.
- Heavies, feints, morphs, combos, flinch, heavy hyper armour, counters (answer an attack with the same type).
- Held weapon block (Chivalry 2): the guard stays up while held and drains stamina slowly; timed parries remain as an option (`blockMode`). Ripostes and counters carry an active parry against other frontal hits. Parry forgiveness turns a last-moment wrong counter into a block. Parry cone, one guard catching several hits. Shields block in a cone (tower shields stop arrows from the front only).
- Dodge (dash in the direction you move, 12 stamina, abandons a windup or guard) and jab (a quick, short interrupt; bots jab your heavies). Feint into a kick or jab; jabs block jabs and kicks block kicks; kicks don't interrupt an attacker; attacks pressed during a stagger start when it ends.
- Sub-tick combat timing: attack phases are timed in microseconds and resolved inside each server tick, so weapon timings (and attack-speed differences) no longer round to 50 ms steps. Weapon profiles can give timings in milliseconds (`windup_ms`, `release_ms`, `recovery_ms`).
- Counter windows depend on the attacker's weapon speed. Jumping costs stamina mid-fight, crouching pauses stamina regeneration, chop and blunt weapons drain more stamina from a guard.
- Stamina: attacking, parrying and blocking cost it. Parrying with none left disarms you (or holsters the weapon, in config).
- Kick and shield bash, environment clanks, sprint lunges, jump attacks, weapon specials (lunge, slam, sweep), throwing any weapon, a mounted lance charge, and cut/blunt/chop damage types against armour.
- 12 weapon archetypes; Spartan Weaponry weapons map onto them automatically.

**Mobs**
- Zombies, skeletons, piglins, vindicators and other mobs (`#steelclash:fighters`) telegraph their attacks and can be parried.
- Bot brain: spacing and circling, reaction-time parries by difficulty, feints, ripostes, kicking turtles, adapting to how you fight, and attack tokens so groups take turns.
- Mobs spawn armed far more often (and sometimes with shields and helmets).
- Brigand footmen, knights and archers: night spawns, patrols led by a knight, and brigand camps with loot.

**Feel**
- Procedural first- and third-person swing animation (Player Animation Library): the blade follows the traced arc exactly while the body twists, steps and follows through under it (styled per weapon type: compact daggers, deep axe and mace chops, rapier lunges, wide grips on staves and polearms), the wrist is cocked, two-handed weapons are held with both hands on the grip, and the first-person view keeps the weapon clear of the camera; animated mobs (whole-body lean and twist, arms on the traced arc, vindicators show every swing), sounds, particles, hit-stop and camera sway (adjustable).
- Stamina HUD and timing bar (heavy charge, windows, parry, riposte), weapon tooltips, optional enemy telegraph labels.

**Multiplayer**
- Lag compensation: lagged players' swings hit what they saw, and hits on them wait for their parry to arrive (up to 250 ms).

**Getting started**
- Craftable training dummy with passive, parry, attack and spar modes; `/steelclash_help`; the controls help shown in chat on every world join (`helpOnJoin`); an in-game config screen.
