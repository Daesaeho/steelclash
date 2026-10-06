# 0.1.0-beta

First public beta.

**Combat**
- Chivalry 2 controls by default (one slash key whose side alternates or follows your turn; right click parries), or a two-slash-key scheme with a key for each side; optional, customizable mouse-gesture attacks with an optional view lock.
- Directional attacks (slashes from either side, overhead, stab), each with a windup, release and recovery, traced as a swept blade along a real arc.
- Accels and drags: turning during a swing moves where it connects, under a Chivalry 2 style turn cap (360°/s during windup and release). Bots accel and drag too. Optionally, strafing picks the swing side.
- Chivalry 2 footwork and sustain: slower movement while attacking, parrying or staggered and when backpedalling; ducking under level slashes; health regeneration after a few seconds out of combat.
- Heavies, feints, morphs, combos, flinch, heavy hyper armour, counters (answer an attack with the same type).
- Weapon parries with ripostes, a parry cone, and one parry catching several hits. Shields block in a cone (tower shields stop arrows from the front only).
- Stamina: attacking, parrying and blocking cost it. Parrying with none left disarms you (or holsters the weapon, in config).
- Kick and shield bash, environment clanks, sprint lunges, jump attacks, weapon specials (lunge, slam, sweep), throwing any weapon, a mounted lance charge, and cut/blunt/chop damage types against armour.
- 12 weapon archetypes; Spartan Weaponry weapons map onto them automatically.

**Mobs**
- Zombies, skeletons, piglins, vindicators and other mobs (`#steelclash:fighters`) telegraph their attacks and can be parried.
- Bot brain: spacing and circling, reaction-time parries by difficulty, feints, ripostes, kicking turtles, adapting to how you fight, and attack tokens so groups take turns.
- Mobs spawn armed far more often (and sometimes with shields and helmets).
- Brigand footmen, knights and archers: night spawns, patrols led by a knight, and brigand camps with loot.

**Feel**
- Procedural first- and third-person swing animation (playerAnimator), animated mob arms, sounds, particles, hit-stop and camera sway (adjustable).
- Stamina HUD and timing bar (heavy charge, windows, parry, riposte), weapon tooltips, optional enemy telegraph labels.

**Multiplayer**
- Lag compensation: lagged players' swings hit what they saw, and hits on them wait for their parry to arrive (up to 250 ms).

**Getting started**
- Craftable training dummy with passive, parry, attack and spar modes; `/steelclash_help`; a one-time controls hint; an in-game config screen.
