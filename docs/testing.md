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
| `253021b`, baseline | 20 accepted live cases, 2,937 frames; 22 focused rig tests; five caught mutants; hidden input and three-shot sheet | [Baseline record](archive/testing-history.md#broader-baseline-and-background-work-2026-10-11) |
| `c342391`, N07 | Full build, 213 JUnit; 174 required GameTests in each Spartan variant; live second-attacker extension/owner merge; five caught mutants | [AP record](archive/testing-history.md#active-parry-extension-synchronization-2026-10-11) |

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

### M5: bot brain and M6c-2 encounters

- Readable attacks, utility decisions, weapon profiles, patrol/camp encounters. [Bot brain](archive/testing-history.md#manual-in-game-checklist-m5-bot-brain), [brigands](archive/testing-history.md#manual-in-game-checklist-m6c-2-brigand-soldiers-patrols-camps).

### M7: multiplayer, team rules, downed and revive

- Tracking mid-action/guard/downed, config delivery, packet age/corrections, queued followups. [LAN](archive/testing-history.md#manual-in-game-checklist-m7-multiplayer-and-latency), [config](archive/testing-history.md#combat-config-synchronization-2026-10-10).
- Allies/pets, friendly fire, death/revive cleanup, server reconnect. [Teams](archive/testing-history.md#team-rules-added-2026-10-07-lan-with-two-players), [downed](archive/testing-history.md#downed-and-revive-added-2026-10-07-lan-with-two-players).

### Compatibility and release

- Optional-mod stacks, clean config/world, resource reload, dedicated server, release packaging. [Candidate gates](kinematics-compatibility-review.md#integration-design-and-acceptance-gates), [release](archive/testing-history.md#manual-in-game-checklist-m8-release).

## Remaining unsupported checks

- Actual LAN/remote-player latency, packet ordering/correction and immediate visible windup after chunk return.
- First-person kick feet, shield-bash and other/custom-model variants, broader camera effects, swimming/riding
  and renderer/shader combinations outside the recorded cases.
- Initial startup-window visibility/focus behavior before the first capture event.
- A full modpack/co-op feel playtest and release acceptance; existing capture and suite results cover bounded fixtures.
- PAL More Rotation, Blockbench importer, anatomical collision and CPM: apply their plan gates after implementation.
  Candidate source review alone establishes no support. BodyHealth/LSO remain deferred.

Unresolved gameplay-reference questions remain in [combat-rule-evidence.md](combat-rule-evidence.md).
