# Combat performance pass — 2026-10-12

Four changes on branch `perf/combat-optimizations`, based on `b6e3d52`. Each one removes work without changing a
combat rule; where an old side effect turned out to carry a rule, the rule was kept and is now explicit. Combat
timing, hit sweeps, damage, packet formats and animation are untouched.

## 1. Being hurt no longer makes a bystander a fighter

`CombatEvents.onDamageTaken` stamped `lastHurtAt` with `getData(COMBAT)`, which creates the attachment. Every
living thing that took damage (a cow, a villager, a mob burning in daylight) therefore got combat state, and from
then on `Combat.tickServer` ran on it every tick until it unloaded, and it was sent to every client that started
tracking it. The stamp is read for players only (regeneration, jump cost, revives).

Now only players and entities that already have combat state are stamped. A fighter mob gets its state on its first
server tick as before.

One rule depended on the old side effect. `Combat.onDamagingHit` staggers the target of a special "if it has combat
state", and it always had, because the damage had just created it. Two existing GameTests failed as soon as the
side effect went (`specialsStaggerWhatTheyHit`, `delayedSpecialRetainsItsHitStaggerAfterTheAttackerChangesAction`).
The stagger now creates the state itself, as kicks and slams always did, so a special still staggers whatever it
hits.

Every other server-side `hasData(COMBAT)` reader was checked for a bystander that used to have idle state and now
has none: `Defense.tryParry` and `RangedDefense.defend` returned "not defended" either way (no guard up),
`LagCompensation.readyToDefend` false either way, the threat scans need a windup, `ClashBrain.observe` records an
idle opponent either way (the attack serial it stores is only compared after a windup), `Downed.isDowned` false.
What changes is what was meant to: no per-tick combat update and no state packet on tracking for bystanders.

A bystander that is kicked, slammed or hit by a special still gets combat state and keeps it until it unloads.
That is rarer and was not changed.

## 2. Defenders read a list of live attacks instead of scanning the level

`ClashBrain` asked the level for every living entity within five blocks of every bot on every tick, to find a
windup facing it. Usually there was none. At 150 bots this scan was nearly all of the bot AI's time and garbage
(the earlier [baseline](findings-history.md#combat-benchmark-and-baseline-architecture-plan-3141-2026-10-07)
already named it as the cost that grows with the crowd).

`combat/LiveAttacks` is a server-side list of the fighters whose attack is in its windup or release:

- **In:** `CombatStateMachine` reports the start of every attack through `onAttackStart`, and a snapshot
  (`apply`) of a live one. `CombatData` installs the listener when it is created for a server-side entity, so no
  caller can start an attack around it. Starting an attack is the only way into a windup, and a windup the only way
  into a release; `LiveAttackListenerTest` walks long random operation sequences to hold the machine to that.
- **Out:** at the end of every server tick, after held hits and interrupts, attacks that are no longer live and
  fighters that were removed are dropped. An attack that ended this tick is still listed until then, so readers
  check the phase.
- **Read:** a bot takes the listed attacker that passes the same tests the level query applied: not itself, a
  threat (`isThreat`), its hitbox reaching into the bot's hitbox inflated by five blocks (the same strict box
  test), present in the bot's level (`level.getEntity(id) == attacker`, which is false for GameTest mock players
  and for a fighter in another dimension, neither of which the level query returned), and facing the bot.
- **Order:** the list is in attack-start order, the level query's order is the level's. The bot answers "the
  first the level finds", so when two listed attackers qualify at once the bot still asks the level. With none
  or one, the answer cannot depend on order. `LiveAttacks.levelQueries()` counts those fall-backs, and the
  benchmark report prints them per tick, so a change that quietly sends every defender back to the level shows up.

`MobCombat.tickDefense` (training dummies in a guard mode, fighters without the bot brain) uses the same list:
nobody winding up in reach means nothing to do, one is handled directly, several go to the level query as before.
`Combat.isIncoming` still queries the level: it also runs on clients and only on a refused morph.

Known differences from the level query, all outside ordinary play:

- The level query visits entity sections within a two-block margin (four below) of the box, so it can miss an
  entity wider than four blocks or taller than four whose hitbox does reach in. The list has no such margin.
- The list reads the combat state that announced the attack. Nothing replaces an entity's combat attachment today.

GameTest mock players that end a test mid-attack stay listed for the life of the test server (they are never
removed and never tick). The level test filters them out. The list was empty when the first benchmark scene
started.

## 3. The spacing goal reuses the brain's profile

`ClashSpacingGoal.tick` resolved the mob's weapon profile every tick to get its reach, a second lookup after the
brain's own (with Spartan Weaponry installed an unmapped item walks every Spartan weapon-type tag). Goals run
before the brain within a tick, so the brain now leaves the profile it resolved, the item it was for and the tick
in `BrainState`, and `ClashBrain.spacingReach` uses it when it is from the previous tick and the mob holds the
same item. A profile depends on the item only (item data map, Spartan classification and item tags), so the item
is enough to key it. A swap, a disarm, a thrown weapon or a tick the brain sat out gets a fresh lookup.

This is a one-tick hand-over, not the cross-tick cache the
[earlier pass](findings-history.md#second-follow-up-optimization-patch-2026-10-08) declined: the brain still
resolves every tick. The one visible difference is that after a datapack reload the spacing goal uses the old
profile's reach for one more tick.

The goal runs inside vanilla's goal selector, outside the benchmark's profiler sections, so this saving is not in
the numbers below.

## 4. Mobs that never fought skip the pose hooks (client)

`LivingEntityRendererMixin` calls `MobCombatPoses` twice for every rendered living entity. Without combat state
there is no pose, but each call still did weak-map removals and built a part list. Both hooks now return first.
Entries in those maps are only ever made for an entity with combat state, and client combat state is never
removed, so there is nothing to forget. Compiled and reasoned from source only; not rendered or measured.

## Benchmark

`bench-compare`, five alternating rounds, `HEAD` (`b6e3d52`, temporary worktree) against the working tree,
`./gradlew runGameTestServer -Pbench`, i5-12500H. The machine was busy with other work and almost out of free
memory: the scripted scene C, which these changes barely touch, ranged from 0.07 to 0.47 ms per tick in both
versions. The tool's rule is that a difference counts only when the two ranges don't overlap.

| Metric | Before: median (range) | After: median (range) | Verdict |
|---|---|---|---|
| F: 150 bots, bot AI garbage, KB/tick | 58.4 (39.0–61.6) | 12.7 (9.5–13.3) | better |
| E: 40 bots, bot AI garbage, KB/tick | 22.0 (15.7–24.2) | 6.2 (5.2–9.7) | better |
| F: bot AI, ms/tick | 1.21 (0.51–2.31) | 0.21 (0.08–0.59) | no clear difference: noisy |
| F: all combat, ms/tick | 1.45 (0.58–2.65) | 0.36 (0.13–0.90) | no clear difference: noisy |
| E: bot AI, ms/tick | 0.40 (0.14–0.91) | 0.10 (0.06–0.87) | no clear difference: noisy |
| E: all combat, ms/tick | 0.54 (0.17–1.17) | 0.14 (0.09–1.20) | no clear difference: noisy |
| D: 8 bots, all combat, ms/tick | 0.41 (0.08–0.57) | 0.12 (0.09–0.37) | no clear difference: noisy |
| C: 50 dummies, all combat, ms/tick | 0.39 (0.09–0.45) | 0.13 (0.07–0.47) | no clear difference: noisy |

Live blades per tick were 0.42–0.51 before and 0.43–0.49 after: the same workload.

What the timings do and don't support:

- The garbage figures are the measured result. The scan allocated on every bot every tick; that is gone.
- For time, the medians cannot be quoted as a speed-up: they mix quiet and loaded runs differently in the two
  versions. Two readings that take the load out, both made after seeing the data and so weaker evidence than a clean
  run: dividing each run's F bot-AI time by the same run's scene C gives 3.1–5.9 before and 0.7–2.7 after, which do
  not overlap; and the runs where scene C stayed under 0.15 ms show F bot AI at 0.51 and 0.52 ms before against
  0.08, 0.09 and 0.21 ms after.
- In the quietest run after the change, bot AI cost about the same at every crowd size (0.06 ms at 8 bots, 0.06
  at 40, 0.08 at 150); before, it grew to about 0.5 ms at 150. At 8 bots there is little to gain: all combat took
  0.08–0.09 ms in the quiet runs before and 0.09–0.12 ms after, which this session's noise does not separate.
- Re-measure on a quiet machine before quoting a time.

One later run with the fall-back line in the report: defenders asked the level 0.3 times per tick at 8 bots, 2.6
at 40 and 2.9 at 150, where before every bot that can guard asked on every tick. The remaining ones are bots with
two windups in reach at once. That is probably two bots swinging at the player together, each a windup that faces
the bots standing across from it; this was not traced further.

## Tests

Core: `LiveAttackListenerTest` (attack start announced once, refused start and guards not, live snapshots
announced, and the random-walk invariant).

GameTests, in `LiveAttackGameTests` unless noted: a windup out of reach, facing away, the bot's own, one that
just ended, a release without a plan, and one from a fighter outside the level are not answered; two at once
follow the level's order in both starting orders; an ended attack and a removed fighter leave the list and a new
attack is listed again; the reactive parry reacts in reach only, to windups only, and considers every one of
several; the level is asked exactly once for several attacks and not at all for none or one.
`FootworkGameTests.onlyPlayersAndFightersRememberBeingHurt` covers the bystander rule and
`BotGameTests.spacingReachFollowsTheWeaponInHand` the hand-over.

GameTests share one server, and other tests' attacks are on the same list. A mistake in the reach test therefore
tends to produce "several attacks" and the fall-back, which answers correctly: in the first mutation run the
reach mutants were caught only by luck or not at all. The fall-back counter is what makes those tests
deterministic.

## Mutation check

27 hand-picked mutants with the `mutation-check` skill, each undoing one rule: the three ways to get the
bystander stamp wrong and the special-stagger guard; the machine not announcing a start, a snapshot or a release;
state never listed, never swept, not un-flagged, kept after removal; the reach box, the level test, the threat
test, the cone, the bot's own attack and the level-order fall-back in the bot scan; phase, reach and "several" in
the reactive parry; the item, the tick, an off-by-one and the missing hand-over in the spacing reach; and the two
"always ask the level" regressions that keep every answer and lose the saving.

The first run of 24 caught 22 with the intended test. `inReach` without its box was caught only by a different
test, and the reactive parry without a reach test survived two runs, for the reason given under Tests. With the
fall-back counter asserted, those two and three new ones (the bot scan without a reach test and the two
regressions) are all caught by the intended tests. **Final: 27 of 27 caught.**

After restoring the files: 249 JUnit tests, fresh, no failures or skips; 187 required GameTests passed with the
Spartan mods and 187 with `-PnoCompat` (174 before, 13 new); `./gradlew build` succeeded. The client change was
compiled only.

## Not measured, not done

- **Position history for every living entity.** The planned gate ("record only while some attacker can be
  rewound") was dropped after reading `LagMath.rewindTicks`: any measured latency above zero adds the interpolation
  ticks, so the gate would be open whenever a player has a ping, flap on an integrated server whose host reads 0 to
  a few ms, and leave the first swings after it opens without history. The cost it would save is about 0.8 KB per
  living entity and one array write per tick.
- **Player animation layer** (`ProceduralSwingAnimation`: a record or two per bone per frame, a second profile
  lookup and a re-solved ready rig per frame in first person). Estimated at around a microsecond per frame, in files
  with other work in progress, and there is no client frame-time benchmark to show a gain. Left alone.
- **`Combat.isIncoming`** still queries the level.
- **Idle fighter tick and swing-tracer allocations.** Small (the state section is under 0.02 ms at 150 fighters;
  hit geometry under 0.01 ms) and left alone.
- **Client cost in general** and **the spacing goal's cost** are not covered by the combat benchmark.
