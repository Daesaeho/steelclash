# Historical roadmap checkpoints

Dated progress from the original plan. Use the [current roadmap](../next-combat-animation-plan.md) for status and gates.

## Original planning context

User scope, 2026-10-10: plan the next Steel Clash work; defer BodyHealth and Legendary Survival Overhaul (LSO).
Continue toward readable Chivalry-style combat with distinct original weapon-family styles and PvE priority.
This document plans implementation; it does not install candidates or change gameplay rules.

Original planning baseline: `2f4d121`, Minecraft 1.21.1, NeoForge 21.1.252, Java 21, PAL 1.1.6. The latest build and both Spartan
GameTest variants passed in GitHub CI. The animation checkpoint has 187 fresh local JUnit passes and seven
ordinary-input live capture scenes, totaling 970 frames. These do not establish every live transition or active
Sodium rendering. Preserve the existing local `AGENTS.md` and `tools/` work.

## 1. Close the important baseline gaps

Checkpoint progress, 2026-10-10: actual Sodium loading is repaired; torch/shield controls and the first live
block/riposte opponent scene pass with verified Sodium and vanilla renderers. Evidence and remaining cases are
in [testing.md](testing-history.md#verified-renderer-and-live-opponent-checkpoint-2026-10-10). The rest of this step is open.

Next checkpoint: matching counter, light-blunt thwack/visual hold and occupied-hand idle are now captured. C04
wall/floor cover and C09 stagger downgrade were reproduced and fixed under explicit rules. See
[testing.md](testing-history.md#carried-idle-counterthwack-and-c04c09-2026-10-10). Newer evidence follows below.

Next progress: ordinary opponent jabs now exercise incoming windup and release interruption, and vanilla offhand
food use exercises consumption and attack cancellation. The first four successful scenes contain 555 actual frames.
The vanilla eating/ready renderer handover visibly changes the hand/weapon pose; its presentation polish remains
open at that checkpoint. See [testing.md](testing-history.md#incoming-interruption-and-food-use-2026-10-10).

The following checkpoint replaces the offhand eating/drinking renderer handover with a continuous carried-hand
pose while retaining the weapon ready stance. Both main arms, attack cancellation, returned bottles, Sodium/vanilla
and a normal torch attack are captured; see [testing.md](testing-history.md#offhand-consumable-blend-2026-10-10).
The next checkpoint reproduces and fixes the local old-weapon pose after hotbar swaps and the abrupt player
weapon-to-kick rig reset. First/third-person and left-arm/reverse controls accompany the reproductions; see
[testing.md](testing-history.md#hotbar-swaps-and-player-kick-rig-handover-2026-10-10).
Next: moving/crouching/turning and the wider acceptance matrix before bending. First-person foot rendering,
mob rig handovers and tracking/reload/LAN acceptance remain separate gates.

Progress, 2026-10-11: fourteen successful baseline cases now cover movement/crouch/sprint/turn, both main arms,
SLIM/WIDE models, shield/third-person controls, 10/60 FPS and FOV 70/110, resource reload and controlled humanoid
mob slash/morph/kick. The turning ready-blade and mob kick handover bugs are reproduced and fixed in live captures.
See [testing.md](testing-history.md#broader-baseline-and-background-work-2026-10-11). Tracking fixtures, pitched-camera and
two-handed controls, final mutations and background-mode verification were still pending at that checkpoint.

**Step 1 baseline gate closed, 2026-10-11:** the additional tracking, pitched-camera and trident controls pass.
Twenty latest accepted cases contain 2,937 actual frames. Hidden keyboard combat, window visibility and mouse
ownership are verified; five targeted mutants are caught after strengthening the renderer test to avoid starting-yaw
dependence. Remaining unsupported LAN/model/camera/foot-renderer/startup-window cases are explicitly retained in
[testing.md](testing-history.md#broader-baseline-and-background-work-2026-10-11). This closes the bounded baseline gate,
not every human playtest or the beta goal. Step 2 now starts with confirmed active-parry extension synchronization
(report N07); stamina cost and other reference-fidelity questions remain evidence-led investigations.

## 2. Fix confirmed bugs and resolve the highest-impact fidelity questions

First verified checkpoint, 2026-10-11: report N07 is reproduced in the current server/client code and fixed.
Active-parry extensions now reach the predicted owner without restarting the action. Full build: 213 JUnit passes;
174 required GameTests pass with and without Spartan integrations; the real second-attacker capture and five
mutants verify server dispatch, client merge and stale/expired guards. See
[combat-rule-evidence.md](../combat-rule-evidence.md) and [testing.md](testing-history.md#active-parry-extension-synchronization-2026-10-11).
Step 2 remains open for live matchup measurements and reference-fidelity decisions; active-parry stamina,
jab normalization and narrower hyperarmor windows are not changed without the required evidence.

## Original first-checkpoint instruction

The first implementation checkpoint should therefore combine the actual Sodium-loading diagnosis with the smallest
opponent-driven live capture/reproducer. Confirmed combat blockers take priority, followed by the PAL bending trial.
## Offline authoring prototype 2026-10-11

Preparatory step 4 work: strict linear PAL-to-phase converter plus new-file-only import task, base settings/other
clip preservation, physical left-main to canonical runtime limb conversion and free-arm scalar bend support.
The original JSON sword fixture is hand-authored; no shipped assets, weapon timing or human test-world data changed.

Verification: Java 21 repository `compileJava build`, started 2026-10-11T13:53:40Z, exit 0; **226 fresh tests**,
zero failures/skips, including eight importer tests. An independent build in
`C:/dev/steelclash-beta-audit/20261011/authoring-check` initially lacked JetBrains annotations; adding that external
compile dependency fixed the harness. Tests exposed and verified constant scalar bend support. Review also found
and fixed the runtime's left-main limb swap/reflection requirement before candidate generation.

Five valid mutations caught by the intended tests: bypass solved-arm rotation protection, remove body X/Y
compensation, omit left-main normalization, overwrite existing output/source files, and drop duplicate JSON
properties. Production source passed the repository build; the post-mutation compile check also passed.
Mutation configs/reports and the generated candidate are in the external evidence directory.

The actual `palAnimationImport` task generated `sword-candidate.json` in that directory while preserving the base
asset. First unquoted PowerShell invocation failed during task selection; the quoted documented command passed,
including configuration-cache storage. This is converter/command evidence, not visible animation or performance proof.
Repeating the command reused its configuration cache and correctly failed with `FileAlreadyExistsException`;
the candidate's SHA-256 remained `b796f94026213d755a0774aa4bf262f55cf1214b4f7a5262d69990e1892580d9`.
No GameTests were required: no server behavior changed. Minecraft remained available for the user's manual test.

Open gates: actual editable `.bbmodel`, plugin export/coordinate retargeting, source/runtime rotation-order parity,
rendered round-trip, dense-clip performance, alignment/onion-skin review, resource reload/invalid-file retention and
all nine family styles. PAL More Rotation's broader step 3 matrix and adoption decision remain open as well.
