# Unreleased

**Fixes**
- Ground slams spare the attacker's and allies' pets from secondary stamina drain, stagger and knockback. Hostile creatures and enemy-owned pets remain eligible targets.
- Delayed special hits apply their on-hit stagger after parry grace, using the captured attack type even if the attacker has started another action. Fully blocked specials retain only their guard penalty.
- Archer mobs draw or stow their sidearm before the combat brain chooses a new attack, preventing a fallback attack with the bow from repeatedly postponing the switch. Existing attacks and switch cooldowns still prevent equipment changes.
- Combat rules now synchronize from the server before joining, so guard mode, turn caps and stamina costs agree with client prediction. Existing `config/steelclash-common.toml` settings are retained; controls and camera settings remain local.
- A rejected jab/kick feint preserves the original windup and stamina. Eligibility is checked before cancellation, and rejected predicted windup replacements send the owning client an authoritative correction.
- Downed and dead players no longer retain a standing first-person combat-ready pose or activate its animation layer.
- Landed jabs, kicks, specials and throws retain their recovery instead of unlocking an unintended weapon combo. The timing HUD keeps showing recovery; normal cuts and thrusts still combo after a hit or miss.
- Disarming a training dummy removes its copied practice weapon without creating recoverable loot. Actual mobs and players still drop their real weapons.
- Finishing off a downed player clears crawling and revive progress; an ally cannot revive a dead player through stale downed state.

**Development**
- Record the ordered combat/animation plan, deferring BodyHealth and Legendary Survival Overhaul while prioritizing live acceptance, confirmed blockers, PAL bending, Blockbench authoring and authoritative collision.
- Document the kinematics-specification comparison and optional hitbox, injury and animation integration candidates. Correct the Sodium status to require verification of an initialized renderer rather than distribution-jar discovery.
- Add an opt-in live animation capture tool that drives normal keyboard inputs and records actual rendered frames, frame times and integrated-server outcomes for attacks, combos, heavy upgrades, feints, morphs and held guard.
- Pose sheets can select the main arm and offhand item, validate the rendered hand ownership, and log the actual skin/model and armor. Capture staging suppresses mob loot and fire overlays for clearer rig comparisons.
- Record the combat/animation goal and original weapon-family style directions in `docs/combat-animation-goal.md`. Pose-sheet staging clears player effects so potion particles do not pollute motion captures.
- Add three focused Codex skills for project context, change verification, and animation review, with helpers for current source routes, fresh test-result summaries, and chronologically ordered pose captures.

**Combat** (from the Chivalry 2 report)
- Running out of stamina leaves you **exhausted** until it regenerates back to a quarter (`exhaustionRecoverFraction`): any blow you block breaks your guard (weapon: disarm and stagger; shield: guard broken), and you can't feint, morph or dash. The stamina bar pulses red meanwhile. Before, an empty bar only mattered on the exact hit that emptied it, and feints were free at zero.
- A bare-handed punch no longer interrupts anyone's windup or release.
- Shields cost much less stamina to block with, as in Chivalry 2: 35% of the hit's stamina damage for basic shields and 25% for tower shields (were 70% and 50%). Arrows and bolts never break a shield guard, even at zero stamina.
- Specials stagger whatever they hit. Blocking one with a weapon guard makes the blocker reel instead of opening a riposte, and the attacker isn't staggered; specials still can't be countered.
- A sprint attack that misses recovers 0.3 s longer (`lungeWhiffRecoveryMs`).
- Mounted: every melee attack hits harder the faster the mount goes, not just stabs; slashes and overheads get half the couched-lance bonus.
- Counters now require an attack started from guard and avoid the incoming block stamina cost; bots raise guard before countering too. Existing action costs remain, with no counter stamina refund.
- Dodges no longer cancel jab, kick, or heavy windups. Rejected dodges preserve the attack, stamina, cooldowns, and queued input.
- A counter that has already caught its attack can't be dodged out of.
- Arrows and bolts no longer interrupt a weapon throw.
- Combos can be pressed during the release, a little earlier, as in Chivalry 2: the combo starts the moment the swing ends.
- A disarmed mob runs back for its own dropped weapon and picks it up (gives up after 10 seconds; `mobsRetrieveWeapons`). Pick it up first and it's yours.
- A blow during your release interrupts your attack (Chivalry 2): whoever lands first wins the exchange. Blades landing in the same tick trade. Heavies with hyper armour are immune, and fire or falls don't count. `releaseInterrupt` turns it off.

**Animation**
- First person: a carried non-shield item keeps the weapon ready stance active between attacks, avoiding the abrupt vanilla/model hand swap. Item use still yields to vanilla.
- Keep PAL's camera ownership stable through its animation tick so an expiring pose cannot expose the player's helmet inside the first-person camera.
- First person: carried non-shield offhand items hold a camera-relative pose during weapon attacks, rather than following the weapon's translation and torso sweep below the view. Shields retain their existing choreography.
- Original weapon-family style draft: distinct first-person ready positions, body preparation and follow-through rhythm for daggers, swords, greatswords, axes, blunt weapons, polearms, spears, rapiers and staves. Pack authors can set `first_person.ready_yaw` and `ready_pitch`; omitted values preserve the old stance. Gameplay timing and hit arcs retain their existing behavior.
- First person: a ready stance while holding a weapon (off hand empty), so attacks start from it and return to it instead of cutting from vanilla's hand (`firstPersonReadyStance`).
- Combos, ripostes, morphs, feints, staggers and guards blend from the arm's last pose over 0.2 s instead of snapping back to rest first; a combo goes straight from the end of one swing into the next windup. Heavy upgrades and weapon morphs blend too.
- Windups ease in, telegraph, then settle exactly onto the start of the swing: no jump at the release, including all 33 heavy windup clips.
- Hit-stop freezes the whole pose (model and camera together) and lets go cleanly on a new attack, a disarm or a server correction.
- Left-handed fighters (main arm set to left) swing with their left arm; an occupied off hand stays visible.
- Shield bash, the three special kinds (lunge, slam, sweep) and throws have their own motion; a throw finishes its forward gesture as the weapon leaves.
- First person: thrusts pull the arms back slightly before extending.
- First person: the arms sit 2 pixels lower (3.5 instead of 1.5) and rise half as much during swings, so they take up less of the screen.
- First person: swings are broader. The blade sweeps across the screen from one side to the other and the hands travel with it, a little higher in view (`firstPersonSwingWidth`, default 1.4; `firstPersonSwingLift`, default 15°). Visual only: hits follow the real arc, and third person is unchanged.
- Mobs hold their weapon on the line it actually hits along: a natural arm with the weapon turned in the hand (like players), instead of the arm pitched down and the blade passing at hip height, about 0.7 blocks below the hit. Mobs whose renderer draws held items some other way (some modded mobs) keep the old pose.
- Windups draw the weapon back the way the swing will come from: a slash cocks behind the shoulder, an overhead leans back over the head, heavies further. Thrusts are pulled in and raised as before.
- Body motion no longer stops on every keyframe: a slash's body twist sweeps through the middle of the swing instead of pausing there and lurching on.
- Players who come into view show their current guard, attack or downed state straight away.
- Animation packs: missing clips inherit the default set, `"clip": []` turns a clip off, files are validated with clear errors, a bad edit keeps the last good set, and IDs can be namespaced (`othermod:sword`). See docs/animation.md.

**Settings**
- `cameraSway` and `impactShake` (0–2) scale the swing lean and the hit shake separately.

**Network**
- Protocol 11: server and clients must update together.

# 0.3.2-beta

**Performance**
- Combat on the server costs about 40% less at large fights: bots stop scanning for threats once they've found the one they'll answer, swing hitboxes and arc paths are computed once instead of every sub-step, and less garbage is made per tick (150-bot benchmark: 0.46 → 0.27 ms per tick).
- Fighting mobs on screen work out their combat pose once per frame instead of twice (body lean and arms shared it), and combat packets no longer make garbage checking for fake players.
- Less garbage in hit detection, pose sampling, attack-slot bookkeeping and the optional telegraph labels.

**Fixes**
- Bots now use their late parry (waiting for the blade to actually come, against feinters); before, they never saw the release it waits for.
- Parrying during a riposte's cooldown no longer cancels the attack for free.
- After a caught parry, lowering the guard no longer leaves your client in a guard recovery the server doesn't have, so the next attack isn't delayed.
- The parry key's release always reaches the server, so a guard can't stay up after you let go.
- A teammate's shield no longer blocks your friendly-fire swing, drains their stamina or bounces you.
- Hurting a reviver always resets the revive, whenever in the tick the damage lands.
- Downed players can no longer use armor stands.
- Lag grace for a parry still on its way is no longer cut short by a counter that can't happen any more.
- Datapack attacks so short that the windup and release fit in one tick still charge whiff stamina and run slams.
- Profiles with no selectable attack (guard-only, or kick-only for basic mobs) no longer crash the mob AI.
- Bots release their attack slot when they lose or switch targets, or when the target leaves.
- With `environmentClank` off, blades no longer stop at walls (line of sight still blocks hits through them).
- Patrols no longer spawn members into unloaded chunks.

**Network**
- Protocol 10: combat state packets carry the parry and attack state the client needs to keep predicting. Server and clients must update together.

# 0.3.0-beta

**Combat**
- Chivalry 2 timings: every built-in weapon is retimed from the game's own data (as published by polehammer.net), in milliseconds. Windups include the 350 ms chamber, releases are about twice as long as before, recoveries are longer, and combos and ripostes have their own timings. Heavies add a fixed ~250 ms windup and a slightly longer recovery, ripostes and combos included. Overhead and stab damage follow the game's ratios to the slash.
- Combos after a miss (Chivalry 2): any attack that isn't blocked can be comboed, whiffs included. Blocked and parried attacks still can't.
- Weapon profiles take `combo_ms`, `riposte_ms` and `heavy.windup_extra_ms` / `heavy.recovery_extra_ms`.
- Projectile defence (Chivalry 2): starting an attack just as an arrow or thrown weapon arrives (0.25 s) deflects it; a held weapon guard takes 30% off and never breaks to arrows; shields still stop them.
- Projectile headshots deal 25% more, with a ding for the shooter.
- Getting hurt while drawing a bow or loading a crossbow loses the draw.

**Co-op**
- Team rules (Chivalry 2): all players are allies by default (`playersAreAllies`), or scoreboard teams decide. Allies' bodies are in the way of your swings: slashes carry on through them, stabs stop in them. Allies take 25% damage (`friendlyDamageScale`, 0 = off), their guards never parry you, and kicks, jabs and slams pass them by. **To duel another player, set `playersAreAllies = false`.**
- Downed and revive (Chivalry 2): with an ally within 48 blocks, a lethal blow downs a player instead of killing them. Downed players crawl, can't fight, and bleed out after 30 s; an ally crouching beside them for 3 s revives them at 30% health. A second lethal blow finishes them; mobs leave the downed alone. `/kill`, the void and logging out still kill. Protocol 9.

**Hardening**
- The server takes at most 8 combat inputs per player per tick (guard releases always go through), so a modified client can't flood everyone nearby with combat-state updates.
- Combat-state broadcasts skip fake players (other mods' machines) that can't receive them, instead of failing.

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
