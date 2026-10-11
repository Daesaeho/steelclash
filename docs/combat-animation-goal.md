# Combat and animation goal

User direction, 2026-10-09: improve combat animations and fix bugs so Steel Clash feels very similar to Chivalry 2,
while giving different weapon families their own original fighting styles.

Next-work scope, 2026-10-10: BodyHealth and Legendary Survival Overhaul are deferred. The ordered implementation
plan is [next-combat-animation-plan.md](next-combat-animation-plan.md): live acceptance and confirmed blockers,
PAL bending/axis compatibility, Blockbench authoring, authoritative contact/anatomical collision, then a CPM pilot.

## Required implementation roadmap (2026-10-11)

The user explicitly includes **all six steps** of [next-combat-animation-plan.md](next-combat-animation-plan.md)
in this project goal. That file is the implementation order and its deliverables and "Done when" clauses are
completion gates, not optional suggestions:

1. Close the important baseline gaps with real renderer/opponent-driven evidence and explicit unsupported cases.
2. Fix confirmed combat defects and resolve the highest-impact fidelity questions with reproductions and a rule table.
3. Evaluate PAL More Rotation through an optional prototype before adopting it.
4. Establish Blockbench source projects/import validation and refine distinct original weapon-family styles.
5. Add authoritative contact metadata, then optional server-owned humanoid anatomical collision with fallback.
6. Evaluate CPM cosmetics on the stable default rig, retaining normal fallback and independent legal collision.

An experiment's adoption remains conditional on its plan criteria. BodyHealth/LSO stay deferred. Complete relevant
tests, mutation checks, visual evidence and documentation for each checkpoint, then commit/push under the existing
authorization. Releases/tags require a separate explicit instruction. Record measured facts separately from
reference claims and unresolved tuning; never mark a step complete solely from frozen poses or untested proposals.

Current roadmap checkpoint: step 1's bounded baseline gate is closed (20 accepted cases, five mutation guards),
with unsupported human/feature checks retained in the testing document. Step 2 is in progress: active-parry
extension synchronization (N07) is fixed, while rule/reference measurements remain open. Steps 3–6 remain pending.

The first step-2 checkpoint now fixes N07 with a timer-only matching-action merge, live second-attacker packet
evidence, 213 full-build JUnit passes, both 174-test GameTest variants and five caught mutants. The
[rule/reference evidence table](combat-rule-evidence.md) retains unresolved stamina, jab and hyperarmor questions.
Step 2 and the overall goal remain unfinished; remaining human playtests are not converted into passed checks.

## Acceptance criteria

- Preserve readable, committed windup → release → recovery, directional swings, guard/counter/riposte reactions,
  and weapon timings. Compare behavior with the supplied Chivalry combat report; distinguish official claims,
  measured data and unresolved tuning proposals.
- Evaluate the supplied technical report against the current checkout. Its historical review and protocol-12 patch
  describe another source snapshot; its 72 candidates are leads, not 72 proven current defects. Reproduce fixes
  with focused tests and mutation checks before declaring them resolved.
- Give dagger, sword, two-handed sword, axe, blunt, polearm, spear, rapier and staff distinct preparation,
  body/offhand motion and follow-through. Keep the weapon arm tied to the traced arc; original style must not
  move the hit timing or silently change reach. Light/heavy and mirrored variants must remain recognizable.
- Verify first/third person and humanoid mobs with reproducible poses, blade alignment views and evenly timed
  motion captures. Check live transitions, occupied offhand, left-handed fighters and renderer compatibility;
  frozen pose sheets alone do not prove these cases.
- Run the build and affected unit/GameTests, including optional integrations on/off when relevant. Record the
  actual remaining human playtests. Keep existing local work. The user authorized commits and pushes to GitHub
  on 2026-10-09; release publishing and tagging still require an explicit instruction.

## Weapon style direction

These are original animation directions, not claims about exact Chivalry animations or new gameplay bonuses.

| Family | Intended motion identity |
|---|---|
| Dagger | Compact close-range preparation, guarded free hand, short body movement and a quick reset. |
| Sword | Balanced cuts, readable shoulder chamber, measured torso rotation; empty-offhand two-hand support. |
| Two-handed sword | Broad shoulder/torso preparation, both hands coupled, deliberate follow-through. |
| Axe | Leading shoulder and committed diagonal body drive; visible recovery from the cut. |
| Blunt | Compressed heavy preparation, weighty downward drive and settled follow-through. |
| Polearm | Long-lever preparation, wide grip, body-led cuts and controlled recovery. |
| Spear | Narrow thrust silhouette, rearward preparation, forward body extension and withdrawal. |
| Rapier | Side-on fencing silhouette, quiet free hand, precise thrust and restrained cuts. |
| Staff | Wide two-handed grip, coordinated opposing body motion and smooth circular recovery. |

## Current evidence and next work

Baseline at commit `1b1f992`: fresh build and 181 JUnit checks passed; 155 GameTests passed both with and without
Spartan mods on 2026-10-09. The baseline does not prove rendering or gameplay feel.

Three report candidates were reproduced in newly added real GameTests before fixes: C03 (nonweapon recovery
skipped after landing), A01 (finished-off player keeps downed/revive state), A02 (copied dummy equipment becomes
disarm loot). The fixes passed a fresh full build, 184 unit tests, 159 GameTests with Spartan integrations and
159 without them. Four targeted mutants were caught by the intended tests. Source was restored and rebuilt
after mutation testing. These results cover the three fixes, not the report's remaining inventory.

Capture evidence is stored outside the repository at `C:/dev/steelclash-beta-audit/20261009`. The capture world is a
disposable copy, with camera motion disabled. The initial sword motion capture contained potion particles and
is unsuitable for a smoothness verdict. The repeated clean capture produced 34 frames per view at 50 ms intervals.
The third-person back analysis flags 0–150 ms and 650–700 ms by silhouette-centroid displacement. Visual inspection
shows a gradual arm raise and a continuous rotating release, with weapon occlusion near the camera-facing portion.
These flags are not confirmed discontinuities; inspect finer local samples/live transitions before changing timing.

Next: capture a clean baseline across weapon families, identify the weakest style/readability
and transition problems, author targeted improvements, and compare the same views/timestamps after changes.

C01 was reproduced and fixed: replacement eligibility is validated before cancelling a windup or charging the
feint. Rejected predicted windup replacements send an authoritative correction to the owning client. The tests
cover cooldown/holster/downed rejection, intact state/stamina/queued input, successful replacement at cooldown
expiry, the kick holster exception, and production correction dispatch. Mutation/variant checks are recorded below.
N11 is fixed and verified in the staged downed client scene;
the live LAN downing/finishing sequence remains an acceptance check.

## Weapon-style draft (2026-10-09)

The nine families now have original first-person ready orientations and authored body timing. Daggers keep a
compact guarding free hand; rapiers stay side-on with restrained free-hand movement; axes emphasize an early
shoulder drive; blunt weapons transfer weight later; polearms/staves use different torso rotation and wide grips.
Sword, greatsword and spear preparation/recovery retain separate silhouettes. The solved weapon arm and all
gameplay profile timings, reach, hit sweeps and packet formats are preserved.

All shipped light/heavy phase seams and metadata parsing passed 22 focused JUnit checks. Full visual review is
in progress. The original style assets are saved in the evidence directory; valid comparisons use
`before-original-assets` versus `after` (351 poses each). A Gradle resource override verifies all nine original files
byte-for-byte before capture. The earlier resource-pack attempt loaded below mod resources and is invalid as a
baseline. The restored full build passed 186 JUnit checks; five valid mutations were caught (three metadata/fallback
rules and two actual-client downed ready-pose/layer guards). Client checks use staged network-equivalent state,
not a live network downing. Uniform motion, mob blade alignment, handedness/offhand and renderer matrix remain open.

Blockbench is a suitable next authoring workflow for body, torso, free-hand and foot motion. PAL supports
Blockbench exports, but this mod samples its own phase-normalized pose format. A converter must map bones,
coordinate conventions and phase times, reject unsupported channels, and preserve endpoints and weapon-arm
ownership; using a whole exported player animation directly would bypass the present arc-linked rig.

Unresolved reference measurements (counter/active-parry durations, hyperarmor windows, exact turn limits) and
larger architectural proposals (3D parry geometry, ordered sub-tick contacts, full historical guard snapshots)
must remain explicitly unverified until the required evidence exists.

## Verified source checkpoint (2026-10-09)

The current build passes 186 unit tests and 162 GameTests with and without Spartan mods. The three C01 mutants
were caught by their intended tests; earlier lifecycle and ready-style checks caught four and five mutations
respectively. The user's commit/push authorization applies to this goal's source and documentation changes.
Pre-existing local `AGENTS.md` and `tools/codex-skills` work is preserved separately.

Remaining next work is uniform before/after weapon-style motion and humanoid blade alignment, followed by the
handedness/offhand/model/renderer and live transition checks above. This checkpoint is not a release-ready claim.

## Capture-review follow-up

The actual model sample is now checked against the requested shot/time/view/item before screenshot capture.
Unknown items and missing requested mob types fail instead of silently substituting references. Two real-client
mutations confirmed stale indices and incorrect item identities are rejected. Mob shots hide local hands, and
optional model/sprite inspection is available with `-PposeSheetInspectModel`.

Dagger original/authored motion has 32 frames per view at 50 ms and a 24-frame 10 ms boundary window. The
fine-window flags form a broad speed peak; no isolated snap was established, so attack timings were preserved.
Nine-family husk release sheets contain 162 time-verified poses; reviewed directions are broadly aligned, with
hilt/length offsets still requiring calibration. The spear model lookup selects the correct spear sprite; an
earlier visual suspicion of a halberd model was not supported by that diagnostic.

Remaining: additional family motion, other humanoid models, handedness/occupied-offhand/default-slim/Sodium
matrix, and real input/network transitions. These are open acceptance gates, not implied by static pose success.

## Combat-rule sync checkpoint (2026-10-10)

N01 was source-confirmed: client prediction read unsynchronized common settings. Combat rules now use NeoForge's
server config sync while retaining the existing global filename and optional per-world overrides. Controls,
HUD and camera preferences remain client-local. Regression tests exercise outgoing file selection, cached-value
replacement and real guard/stamina callers, with no local file overwrite. A separate world-override fixture
passed all 164 GameTests with unchanged hashes for both settings files.

See [spikes.md](spikes.md#synchronized-combat-rules-2026-10-10) for framework evidence and
[testing.md](testing.md#combat-config-synchronization-2026-10-10) for the remaining negotiated-session checks.
Join-time rule agreement is covered by the serializer/receiver tests; live network/input acceptance remains open.

Restored full build: 186 JUnit tests passed. GameTests: 164 passed in each dependency variant; the additional
world-override fixture passed all 164 with unchanged files. The two config-registration mutants were caught
by their intended tests. Client startup and three staged slash captures passed in the disposable world.
Evidence is under `C:/dev/steelclash-beta-audit/20261010/config-sync`; existing animation acceptance gates remain.

## Heavy motion and reaction follow-up (2026-10-10)

Added original/authored greatsword and halberd heavy-overhead motion evidence: 53 samples per view at 50 ms,
plus 11/19 samples in two 20 ms windows, across three views (996 rendered screenshots). Resource identities
and actual rendered samples were checked. Focused inspection found gradual movement and multi-step peaks;
an isolated snap was not established, so gameplay timing and style curves were preserved. These are frozen
base-speed samples; additional families and live transition/model/offhand/renderer acceptance remain open.

C02 was reproduced and fixed through shared successful-hit consequences: delayed specials retain their
stagger from the captured hit, ordinary hits cannot inherit a newer special, and blocked hits retain their
guard penalty. A deterministic tick-dispatch test also reproduced archer sidearm starvation; equipment is
now selected before the brain starts a new action, preserving committed attacks and switch cooldowns.
See [spikes.md](spikes.md#delayed-specials-and-archer-sidearm-ordering-2026-10-10) and
[testing.md](testing.md#heavy-motion-and-delayed-reactions-2026-10-10) for evidence and remaining live checks.

Restored build: 186 JUnit tests passed; 168 GameTests passed with and without the optional mods. All four valid
mutants were caught by their intended tests. The review expanded verified phase-curve coverage without changing
the weapon timings or assets. Remaining family motion, live transitions, model/hand/renderer matrix and broader
report-candidate audit stay open; this checkpoint does not establish release readiness.

## Ground slam eligibility follow-up (2026-10-10)

C05 was reproduced and fixed: the full mace slam's secondary area effect now shares the blade's friendly-pet
exclusion. Own/allied pets retain health, stamina, phase and velocity; hostile and enemy-owned pets still receive
the effect. The new tests include real owner lookup, no-team conditions and hostile controls. Restored build:
186 JUnit tests, 171 GameTests in each optional-mod variant, and all three targeted mutations caught.
Evidence and live co-op checks are in [spikes.md](spikes.md#friendly-pets-and-ground-slams-2026-10-10) and
[testing.md](testing.md#ground-slam-pet-protection-2026-10-10). Cover/impact geometry and stagger precedence are
separate unresolved audit candidates; the overall animation/bug goal remains in progress.

## Nine-family representative motion coverage (2026-10-10)

The remaining six-family batch added 1,350 time-verified original/authored screenshots: sword slash, axe/blunt heavy
overheads, spear/rapier stabs and staff slash, across three views at 50 ms. Together with dagger and heavy
greatsword/polearm evidence, all nine families now have a representative phase-motion comparison. The table in
[testing.md](testing.md#representative-motion-coverage-by-family-2026-10-10) records the actual actions/counts.
Focused inspection did not establish new isolated snaps; gameplay timing and style curves were preserved.

Current runtime resources were restored and byte-verified after the baseline overrides. This closes the missing
representative-family capture gap, while live transition/hit-stop/correction, additional actions/variants,
explicit handedness/offhand/model/renderer matrix and humanoid alignment calibration remain open.

## Controlled hand/model checkpoint (2026-10-10)

Capture controls and rendered-condition guards now reproduce left/right main arm and explicit empty/shield/torch
hands. The matrix includes SLIM/WIDE Ari player samples and four humanoid mob types; logs record actual equipment.
Two actual-client setter mutations failed at the intended guard, with crash diagnostics checked separately because
the mutation helper does not extract client test names. Capture-stage fire/loot cleanup produced clear skeleton
references. Commands, sample counts and scope are in [testing.md](testing.md#controlled-rig-captures-2026-10-10).

Occupied hands remain outside the weapon grip, but the torch sits largely below the first-person attack view.
That visibility issue, live transition/correction/hit-stop acceptance, additional model/renderer combinations and
blade/hilt calibration remain open. This checkpoint improves evidence reliability; the beta goal stays active.

## Carried-hand and local loading-mod checkpoint (2026-10-10)

The offhand torch now has a camera-relative holding arm during first-person attacks, countering body turns and
remaining raised through recovery. Shield choreography and weapon/third-person posing retain their existing
paths. Matched static/motion comparisons and the actual-client original-behavior reversion are recorded in
[testing.md](testing.md#carried-offhand-visibility-2026-10-10): 144 static samples per version, 47 polearm-slash
samples per view/version at 50 ms, two valid mutations caught and a restored build with 187 passing JUnit tests.

The six requested local loading/memory mods and FastQuit's Cloth Config dependency are installed in both dev
clients and the disposable QA client. Fresh startup, selected rendering and completed-save checks passed with
Sodium enabled and disabled. Jars remain local/ignored and are not release dependencies. No controlled loading
benchmark is claimed. The staged torch-composition gap is addressed; live vanilla/model handover, input/network
transitions, additional model/locomotion/FOV combinations and wider report-candidate audit remain active work.

## Live transition checkpoint (2026-10-10)

Live input/render capture now goes through ordinary keyboard bindings, local prediction and integrated-server
packets. It records actual frame times and outcome evidence rather than freezing the state machine. Seven
final-source scenes cover empty/shield attack controls and torch combo, heavy upgrade, feint, morph and held
guard. Counts/outcomes and remaining acceptance cases are in
[testing.md](testing.md#live-keyboard-transition-review-2026-10-10).

This exposed and fixed two runtime gaps that frozen sheets missed: an occupied non-shield hand excluded the
weapon ready stance, producing a vanilla/model hand swap, and pose cleanup could change PAL camera ownership
mid-pass and expose the helmet in first person. Both real-client reversions fail the intended assertion.
The verified scenes are concrete progress toward transition acceptance. Opponent-driven reactions, live
correction/hit-stop, other input/model/FPS/locomotion cases and the broader report-candidate audit remain open.

## Active renderer and opponent checkpoint (2026-10-10)

The dev Sodium setup now loads its actual renderer, confirmed by the instance probe. Torch/shield regressions
and the first ordinary-input block/riposte opponent exchange pass with Sodium active and absent. The latter
records a real server swing/catch/return, rendered-opponent presence and unchanged player health. Counts,
conditions, evidence and limits are in [testing.md](testing.md#verified-renderer-and-live-opponent-checkpoint-2026-10-10).
Counter/hit-stop/correction and broader acceptance remain open; this is progress on step 1 of the next-work plan.
BodyHealth and LSO remain deferred.

## Counter, thwack, idle and cover/stagger checkpoint (2026-10-10)

Matching-counter and light-blunt thwack/visual-hold scenes now run through ordinary player input and server
combat. Offhand idle inspection found a rigid carry pose and added bounded, mirrored breathing that fades into
the attack carry. Left/right torch, bread, shield/empty controls and attack return are captured. C04 wall/floor
secondary slam effects and C09 hard-stagger relaxation were reproduced before fixes and resolved under explicit
cover/precedence rules. Current evidence, scope and remaining playtests:
[testing.md](testing.md#carried-idle-counterthwack-and-c04c09-2026-10-10).
