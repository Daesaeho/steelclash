# Testing and acceptance

Use checks appropriate to the change. The [roadmap](next-combat-animation-plan.md) defines completion gates;
[animation.md](animation.md#client-acceptance-checks) defines rendering acceptance. Complete historical
milestone checklists, fixtures, commands, evidence counts and dated outcomes remain in
[testing history](archive/testing-history.md). Unchecked historical items are not passes.

## Automated checks

Run from the repository with Java 21. Use `gradlew.bat` in PowerShell or `./gradlew` in Git Bash.

| Command | Use |
|---|---|
| `./gradlew compileJava` | Compile after implementation changes. |
| `./gradlew test` | Minecraft-free core behavior. Select focused tests while iterating. |
| `./gradlew build` | Full build and unit checks for a checkpoint. |
| `./gradlew runGameTestServer` | Server behavior with Spartan integrations. |
| `./gradlew runGameTestServer -PnoCompat` | Server behavior with those optional mods absent. |
| `./gradlew runClient` / `runClient2` | Client acceptance and two-player LAN checks. |

Client presentation changes require actual rendered evidence; they do not alone require server GameTests.
For new rules or bug fixes, mutation checks must fail the intended assertion, followed by restoration and a
healthy verification. Do not infer visible rendering, multiplayer behavior or performance from unit passes.
Documentation-only changes need link, preservation and diff checks.

## Fixtures and evidence

- GameTest arena entities stand at relative Y=2; the floor is Y=1. Mock players are not level-query actors,
  do not tick, and share a scoreboard name. Use an in-level fixture when discovery matters, apply weapon
  attributes explicitly and form teams after waits. Pin facing and remove AI only when the fixture requires it.
- Use disposable worlds for captures. Record actual client/server outcomes, renderer identity, equipment,
  main arm, skin model, camera/FOV, frame timestamps and screenshot completeness. Keep generated evidence
  outside tracked source and link its local path from the checkpoint record.
- Frozen sheets verify pose/alignment; ordinary-input live scenes verify transitions and contacts. Use
  evenly timed onion-skin review for motion and matched conditions for screenshot comparisons. Do not use ffmpeg.
- Hidden live/pose captures are opt-in and windowed. Window/mouse diagnostics verify those runs; the initial
  loading-window flash remains unverified. See [background capture instructions](agent-workflow.md#background-animation-captures).

## Current verified checkpoints

These are the latest committed scopes, not a claim that every checklist below has passed.

| Checkpoint | Verified scope | Evidence |
|---|---|---|
| Contact metadata, 2026-10-15 | Final integrated build: 281 fresh JUnit; 215 required GameTests in each Spartan variant; 7 original mutations plus the effects-disabled fallback mutation caught; 555 hidden diagnostic frames, including the final merged 139-frame riposte, cover received/drawn metadata, source association, actual freeze and natural expiry. World replacement and real remote delivery remain open | [Contracts, counts and acceptance limits](archive/contact-metadata-20261015.md) |
| Player physical grip, 2026-10-15 | Full build; 277 fresh JUnit; 6/6 intended mutations caught; corrected opposite-yaw poses have 60/60 region contact for both hands; binding rejection/recovery and live heavy/morph/reload checks passed; 18 shield controls pixel-identical; displayed-weight capture verified. Blend, pitched main-hand and near-plane limits remain open | [Counts, paths and acceptance limits](archive/player-physical-grip-20261014.md) |
| Hit feel and mob weapon speed, 2026-10-14 | 209 required GameTests in each Spartan variant (four clean Spartan runs after the death-drop fix); 263 JUnit; 27 of 27 mutations caught; full build; code review findings fixed; hidden live captures of blood, flinch, parry bounce, aim lean on/off and (through a temporary hook) a thrown husk head; a 378-weapon Spartan timing probe; not yet played on the final build | [Hit feel findings](spikes.md#hit-feel), [mob weapon speed](spikes.md#mob-weapon-speed), [playtest list](#hit-feel) |
| Mob kick hand-swing dispatch, 2026-10-14 | Fresh compilation; 197 required GameTests passed in each optional-mod variant; both intended mutations caught; hidden live husk capture confirms the extra weapon-arm cycle is absent | [Dispatch finding and evidence](spikes.md#mob-kick-hand-swing-dispatch), [rendering checks](#mob-kick-and-shield-bash-hand-animation) |
| Two-handed mob grip, 2026-10-14 | Compile/full build; 254 fresh JUnit; two caught native mutations; both main arms contact the handle in 40/40 full-weight husk poses; shipped/candidate live release checks; 48 identical shield controls | [Pivot correction, captures and remaining scope](archive/mob-grip-20261014.md) |
| Melee knockback setting, 2026-10-13 | 195 required GameTests in each Spartan variant (one new); four of four mutations caught; default unchanged; not played in a client | [Playtest list](#melee-knockback-setting) |
| Ranged stamina, 2026-10-13 | 194 required GameTests in each Spartan variant (seven new); nine of nine mutations caught; full build; not played in a client | [Rule and findings](spikes.md#ranged-stamina), [playtest list](#ranged-stamina) |
| Player grip and Vector API, 2026-10-13 | Compile/full build; 254 fresh JUnit; three caught mutations; 108 existing native draws, incomplete right-main support contact; SIMD timing inconclusive | [Reach constraint, rendered gaps and benchmark limits](archive/grip-and-vector-checkpoint-20261013.md) |
| Sword mesh probe, 2026-10-12 | 245 fresh JUnit; native missing-evidence mutation caught; actual standard/Sodium geometry; 36 unchanged world-view placement poses; candidate adoption open | [Baked mesh and placement evidence](archive/blade-mesh-20261012.md) |
| Combat performance pass, 2026-10-12 | 249 fresh JUnit; 187 required GameTests in each Spartan variant; 27 of 27 mutations caught; bot-AI garbage down in five alternating benchmark rounds, timings too noisy to quote; client early exit compiled only | [Changes, equivalence checks and limits](archive/combat-performance-20261012.md) |
| Pose clock refresh, 2026-10-12 | 245 fresh JUnit; four caught mutations including a native PAL probe; 2,313 restored screenshots; nine deterministic zero-partial samples; 18 identical legacy pose pairs | [Clock ordering, coverage and limits](archive/pose-clock-refresh-20261012.md) |
| Native sword retarget, 2026-10-12 | 241 fresh JUnit; nine caught mutations; 112 source/runtime authored bone matrices; 2,117 candidate screenshots; legacy 18-pair pose parity; mesh/motion gates open | [Retarget evidence and exclusions](archive/native-sword-retarget-20261012.md) |
| Native sword source, 2026-10-12 | Two editable light/heavy takes, 56 keys, 420 unchanged native bone matrices after save/reopen; PAL-only import still rejects the body-folded export safely | [Source/export scope](animation-authoring.md#native-sword-source-checkpoint-2026-10-12) |
| PAL live checkpoint, 2026-10-12 | 16 clean native captures across original/repeated heavy/morph, mob morph and reload cases; 5,051 complete frames; late-tracking shutdown excluded | [Live evidence and limits](archive/pal-more-prototype-20261011.md#live-transitions-and-lifecycle-checkpoint-2026-10-12) |
| PAL pose matrix, 2026-10-11 | 36 stable paired armored sword/spear poses; both main arms; captured old idle-clock mutant and healthy restored 18-shot guard | [Capture-clock correction](archive/pal-more-prototype-20261011.md#expanded-pose-comparisons-and-capture-clock-correction) |
| Authoring prototype, 2026-10-11 | Compile/full build; 226 fresh unit tests including eight importer tests; five caught mutations; actual offline candidate command | [Importer checkpoint](archive/roadmap-checkpoints.md#offline-authoring-prototype-2026-10-11) |
| `253021b`, baseline | 20 accepted live cases, 2,937 frames; 22 focused rig tests; five caught mutants; hidden input and three-shot sheet | [Baseline record](archive/testing-history.md#broader-baseline-and-background-work-2026-10-11) |
| `c342391`, N07 | Full build, 213 JUnit; 174 required GameTests in each Spartan variant; live second-attacker extension/owner merge; five caught mutants | [AP record](archive/testing-history.md#active-parry-extension-synchronization-2026-10-11) |
| Step 2 measurements, 2026-10-11 | 18 sword/spear/blunt cases, 2,496 frames; native timings, 360°/s trace caps and AP catch stamina; failed initial focus case excluded | [Measurement record](archive/rule-measurements-20261011.md) |
| Step 3 initial dev prototype, 2026-10-11 | 218 fresh JUnit; 174 GameTests in default and addon/no-Spartan stacks; five caught mutations; one sword pose alignment and actual mesh deformation | [Scope and remaining matrix](archive/pal-more-prototype-20261011.md) |

## Focused acceptance checklists

Select the relevant checks below; preserve the player guide's normal controls and the server's authoritative rules.

### Control schemes, parry key and slash keys

- Rebindings, alternating/mirrored attacks, mining exception, item use, guard release. [Controls](archive/testing-history.md#control-schemes-and-gesture-attacks-added-2026-10-06), [parry](archive/testing-history.md#parry-key-added-2026-10-06), [slash](archive/testing-history.md#slash-keys-added-2026-10-06).

### Chivalry 2 timings, cleave and release interrupts

- Light/heavy, combo/riposte, accels/drags, one hit per target, blunt thwack. [Timings](archive/testing-history.md#chivalry-2-timings-added-2026-10-07), [turn cap](archive/testing-history.md#accels-drags-turn-cap-side-from-movement-added-2026-10-06), [cleave](archive/testing-history.md#cleave-thwack-and-counter-feint-020-beta-added-2026-10-07).
- Matching counter, AP, guard cones/stamina, jab/kick/heavy interactions, hard/soft stagger. [Defense](archive/testing-history.md#chivalry-2-defence-and-movement-step-f-added-2026-10-07), [interrupts](archive/testing-history.md#release-interrupts-2026-10-08), [AP](archive/testing-history.md#active-parry-extension-synchronization-2026-10-11).

### Broader first-person animation presentation and mob arms

- Readable ready/windup, arc-aligned release, continuous recovery/morph/heavy/weapon-kick. [Client contract](animation.md#client-acceptance-checks), [mob arms](archive/testing-history.md#mob-arms-and-windups-added-2026-10-08), [baseline](archive/testing-history.md#broader-baseline-and-background-work-2026-10-11).
- Torch/shield/empty, eat/drink/cancel/bottle return, hotbar swaps, both main arms. [Consumption](archive/testing-history.md#offhand-consumable-blend-2026-10-10), [swaps](archive/testing-history.md#hotbar-swaps-and-player-kick-rig-handover-2026-10-10).
- Nine families, light/heavy/mirrored, controlled model/armor/handedness, hilt/tip alignment. [Family motion](archive/testing-history.md#representative-motion-coverage-by-family-2026-10-10), [capture integrity](archive/testing-history.md#trustworthy-pose-sheet-captures-2026-10-09).
- Physical player grip: finish pitched main-hand contact, blended recovery and first-person camera clearance. Check other model/configuration stacks and weapon families separately. [Verified cases and open checks](archive/player-physical-grip-20261014.md).

### Mob kick and shield-bash hand animation

- `MobReleaseAnimationGameTests` drives ordinary requests through the first production release sweep on fresh
  passive husks. Kick and vanilla-shield bash must leave `swinging` false and `swingTime` zero; slash, overhead,
  stab, jab, special and throw must retain main-hand swing dispatch. Throw also verifies item removal.
- All 197 required GameTests passed in both optional-mod variants. The unconditional-swing mutation failed
  `mobkickandshieldbashdonotstartvanillahandswings`; the `isWeaponAttack()`-only mutation failed
  `mobhandattacksretainvanillaswingdispatch`. Restored source compiled and passed the no-compat suite.
- The hidden ordinary-input `mob-kick` capture completes release/recovery with no extra vanilla weapon-arm
  cycle: all 59 captured samples retain -0.100-radian main-arm yaw. Inspected 50 ms onion pairs and the
  [comparison evidence](spikes.md#mob-kick-hand-swing-dispatch) support this bounded correction.
- Still check shield-bash appearance separately and inspect weapon-to-kick/recovery-to-idle transitions,
  including entry and final-reset onion flags. First-person kick feet and broader model/lifecycle acceptance
  remain open; the dispatch tests and this husk fixture do not close them.

### Ranged stamina

- In game, not yet played: the stamina bar falls while a bow is drawn, a crossbow is loaded and a trident is held
  back; at zero the weapon visibly comes down, no arrow flies, and holding the button does not raise it again until
  the bar stops pulsing. A loaded crossbow fires at any stamina. Skeletons, strays and bogged still shoot, with a
  short pause when they run dry; piglins still load. A drowned keeps its trident raised for a whole fight, so it
  will arrive exhausted: check that this reads as fair and that it still throws. Soldier archers and pillagers are
  not in the fighter roster and pay nothing. Spartan longbow, heavy crossbow and javelin behave the same. On a LAN
  client the bow should not twitch up while exhausted.

### Melee knockback setting

- In game, not yet played: with `meleeKnockback = 0`, sword hits and punches leave zombies and players where they
  stand while kicks, shield bashes and the mace slam still push them; a Knockback-enchanted sword does not push
  either. Judge whether hordes become too hard when nothing is knocked away, and whether 0 or a low value such as
  0.25 should become the default.

### Hit feel

- Contact metadata: [the checkpoint](archive/contact-metadata-20261015.md) separates server contact frames,
  received/debug-drawn data, retained source diagnostics and actual freeze. Exercise world replacement, real remote delivery and broader model/view
  stacks. The recorded natural expiry does not establish runtime world-replacement cleanup or anatomical collision.
- In game, not yet played: blood sprays from the spot the blade crossed and flies the way it travelled; parry
  sparks appear between the bodies where the blades met. A zombie hit on the arm flings that arm, on the head snaps
  the head, in the chest tips back; the reaction reads as caused by the blow, not as a twitch. Plate and mail ring
  differently and a mace sounds lower than a sword. A killing slash or overhead to a bare head removes it and the
  body's own head (husk, stray, piglin, vindicator, soldier, a player's skin) tumbles away and shrinks out after
  about three seconds; with a helmet it stays on. Already seen in a capture: a husk's head leaving upright along the
  blade with the body drawn headless; not seen: a real kill, other models, a player's skin and hat layer, the head
  coming to rest on the ground, an illusioner's hat. Killed bodies slide along the blow and fall that way (check
  spiders flip over the right way). A slash through a crowd visibly slows after each body without stuttering in first
  person, and a combo pressed during it still comes; on a LAN client with ping the blade must not jump back. A
  parried attacker's blade bounces back before the stagger pose; a kick right after doesn't replay it. Looking down
  while attacking bends the body over without the head dropping further than the view; walking or strafing while
  attacking keeps the legs walking. Check other players on a LAN client see the same, and that your own first-person
  view never tips or jerks.

### M5: bot brain and M6c-2 encounters

- Readable attacks, utility decisions, weapon profiles, patrol/camp encounters. [Bot brain](archive/testing-history.md#manual-in-game-checklist-m5-bot-brain), [brigands](archive/testing-history.md#manual-in-game-checklist-m6c-2-brigand-soldiers-patrols-camps).

### M7: multiplayer, team rules, downed and revive

- Tracking mid-action/guard/downed, config delivery, packet age/corrections, queued followups. [LAN](archive/testing-history.md#manual-in-game-checklist-m7-multiplayer-and-latency), [config](archive/testing-history.md#combat-config-synchronization-2026-10-10).
- Allies/pets, friendly fire, death/revive cleanup, server reconnect. [Teams](archive/testing-history.md#team-rules-added-2026-10-07-lan-with-two-players), [downed](archive/testing-history.md#downed-and-revive-added-2026-10-07-lan-with-two-players).

### Compatibility and release

- Optional-mod stacks, clean config/world, resource reload, dedicated server, release packaging. [Candidate gates](archive/kinematics-compatibility-review.md#integration-design-and-acceptance-gates), [release](archive/testing-history.md#manual-in-game-checklist-m8-release).

## Remaining unsupported checks

- Finish first-person composition, ordinary-input motion, live-input controls and the remaining binding case for the
  bounded player grip correction. The stock iron-sword right −70°/left +70° support/main contact follow-ups have
  [native evidence](archive/player-physical-grip-20261014.md), but the heavy-motion capture flags near-plane crossings
  and intermediate first-person support error, so smoothness remains open. Husk support integration has [native and live evidence](archive/mob-grip-20261014.md); broader mob models,
  custom/bent rigs and other weapon families need their own acceptance.
- Actual LAN/remote-player latency, packet ordering/correction and immediate visible windup after chunk return.
- First-person kick feet, shield-bash and other/custom-model variants, broader camera effects, swimming/riding
  and renderer/shader combinations outside the recorded cases.
- Initial startup-window visibility/focus behavior before the first capture event.
- Clean exit and addon comparison for late guard tracking in the current QA loading-mod stack: one completed
  capture stalled in chunk-unload future rescheduling during shutdown and required stopping its disposable JVM.
- Reproduce the intermittent `shieldbearingbotblocks` failure seen after one of three unchanged-code combat
  benchmark passes; the other two passes completed all 174 required tests. Its cause is not diagnosed.
- Broaden the verified tick-boundary pose refresh to feedback hit-stop, modpack/renderer, altered model and remote
  cases; complete source-aware candidate seam/motion and rendered hilt/tip acceptance. [Clock scope](archive/pose-clock-refresh-20261012.md).
- Client cost of combat presentation: no frame-time or allocation benchmark exists. The mob-pose early exit for
  entities without combat state was compiled and reasoned from source, not rendered or measured; the other
  per-frame candidates were left alone. The combat benchmark does not time the bot spacing goal either, so its
  profile hand-over is tested for behaviour only. [Scope](archive/combat-performance-20261012.md#not-measured-not-done).
- A full modpack/co-op feel playtest and release acceptance; existing capture and suite results cover bounded fixtures.
- PAL More Rotation, Blockbench importer, anatomical collision and CPM: apply their plan gates after implementation.
  Candidate source review alone establishes no support. BodyHealth/LSO remain deferred.

PAL More Rotation has [initial item/mesh proof](archive/pal-more-prototype-20261011.md), with the full
enabled/disabled weapon, hand, renderer and live-motion matrix still pending. Its dev bridge is not adopted support.

Unresolved gameplay-reference questions remain in [combat-rule-evidence.md](combat-rule-evidence.md).
