# PAL More Rotation prototype — 2026-10-11

This is an opt-in development experiment, not adopted compatibility. Evidence is under
`C:/dev/steelclash-beta-audit/20261011/pal-more-prototype`. The [roadmap](../next-combat-animation-plan.md#3-prototype-pal-more-rotation-on-the-existing-rig)
retains the full acceptance matrix and adoption gate.

## Stack and initial findings

The dev flag pins Modrinth `wPBs7ljG:qhKVkTkf`: PAL More Rotation 1.1.0 for NeoForge 1.21.1, with bundled
Bendable Cuboids 2.0.0+alpha.1 and MixinSquared 0.3.7-beta.3. PAL remains 1.1.6. The resolved artifact's SHA-512
matches the inspected metadata: `2a870c34f9a7dd4622b51ed8f072de597d295895c2af3bc0f2df47a82f6f960b8c18a4f282c74ac82705aeb68dab034d76db51972b49f8a03b34e5d5db57b8ff`.

The source review correctly identified swapped item channels, but idealized transform tests alone missed runtime
ordering. The exported transformed `ItemInHandLayer` calls the addon's undo/reapply **before** PAL's original
rotation/scale callback. A direct standard-axis adapter changed 3.79% of the unbent first-person image, including
the blade. The reviewed source also has opposite-axis guards in its zero-Y/Z undo cases; a tiny guard workaround
did not resolve the ordering failure and is not retained.

The explicit dev bridge saves the pre-callback item pose and normal matrices, then restores them after those
callbacks and applies the same-frame standard-axis item sample once. It acts only while this player's registered
Steel Clash layer owns the pose. Runtime export verifies capture → addon → PAL → apply order. Entity ownership
uses weak identity keys/values: ordinary weak-map keys lost registration when Minecraft assigned a new entity id
and changed its hash. The corrected registry retains object identity without retaining departed players.

## Verified initial rendering and mesh

The corrected, unbent iron-sword/torch windup at 50% matches the addon-absent first-person screenshot **exactly**;
both third-person views contain only sub-threshold specks. Conditions: right main arm, SLIM Ari, iron helmet,
actual dev Sodium stack, hidden window. This is one controlled pose, not the full matrix.

The dev bend probe follows the existing phase progress/weight. It bends the torso and free arm; weapon/support
arm deformation remains disabled in this prototype to preserve the solved grip. With the probe enabled, the
first-person change lies in the carried arm/torch region while the blade retains its orientation.
Read-only post-render inspection confirms actual `BendableCuboid` vertices on the same rendered player model:
64 third-person posed samples, body bend 0.12 with maximum vertex displacement 0.758491, free-arm bend 0.3 with
displacement 1.890260, and weapon-arm bend/displacement zero. Distances are backend-local coordinates.
These are mesh measurements, not just animation-channel values or movement of the held torch.

Final restored-source build passes **218 fresh JUnit tests**. Dedicated-server runs pass **174 required GameTests**
with the default Spartan/no-addon stack and with the pinned addon plus Spartan integrations absent. Client bridges
remain disabled on dedicated servers. Four core mutations catch lost axis conversion, incorrect item blending and
mutable-hash ownership; a fifth actual-client mutation catches removal of the render bridge. The first render report
could not parse the fixture's failure label, although the image guard failed; the corrected label rerun identifies
`palItemOrientationGuard` as the intended killer. Temporary edits are restored, and healthy rendering is rechecked.

## Remaining acceptance

Sword/spear, both main arms, empty/torch/shield offhands, armor, first/third person, humanoid mob fallback,
hilt/tip debug alignment and ordinary live transitions at low/high FPS still need the roadmap's full comparison.
Onion-skin review, tracking/reload/lifecycle and broader renderer/model combinations remain open.
The bridge discards intervening item transforms, so other mods modifying the same render interval are an explicit
conflict risk. No generic play/sync clock, gameplay timing, reach, collision or packet changes are introduced.
Keep the default backend and no-addon launch usable; do not claim adoption from this initial checkpoint.

## Expanded pose comparisons and capture-clock correction

On 2026-10-11, 108 paired sword/trident slash poses covered both main arms, empty/torch offhands without armor,
and shield offhands with full iron armor. Each case included windup 50%, release 35% and recovery 30%, in first
person and both third-person views. The dev Sodium renderer and SLIM Ari skin were fixed. Evidence:
`C:/dev/steelclash-beta-audit/20261011/pal-more-prototype/matrix-140002642334`.

Seven first-person shield images initially differed; other pairs were identical or below the image-noise threshold.
This did not establish an addon regression: repeating the default backend alone changed the sword shield images
by 0.73% in windup and 0.99% in release. Bounded runtime diagnostics showed the shield item channels were neutral.
The pose harness froze combat progress but allowed vanilla model age and free-arm breathing to keep advancing.

The capture-only `LivingEntityRenderer#render` hook now passes age 100 to `EntityModel#setupAnim` while a pose sheet
runs. It preserves ordinary rendering. Repeating both armored shield cases with that clock fixed produced **36
matching pairs**: 24 pixel-identical and 12 with sub-threshold specks. Evidence:
`C:/dev/steelclash-beta-audit/20261011/pal-more-prototype/matrix-150629002230`.
The new armor presets and per-shot window/mouse assertions also passed in these native clients. Source hashes,
commands, actual rendered equipment/handedness, logs, screenshots and diff reports accompany the captures.

An actual-client regression guard compared 18 fresh poses with the frozen default reference. Restoring the old
ambient-age behavior was caught by `PoseSheetShieldTest > frozenShieldMatrix`; restored source passed another
18-shot native run. The mutation report is `shield-mutants.report.json`, and the healthy restored run is
`shield-guard-151340516353`, under the same prototype evidence directory. Native compilation passed with both
backend variants. No server behavior or combat timing changed, so no new GameTests were needed for this checkpoint.

These comparisons establish backend pose parity for the sampled conditions. They do not prove exact world-space
mesh hilt/tip coordinates, live transitions, low/high FPS behavior, mob fallback, lifecycle/reload, or improvement
from bending. Those acceptance items and the adoption decision remain open. The initial unfrozen shield differences
are exploratory evidence and must not be counted as backend failures.

## Live transitions and lifecycle checkpoint, 2026-10-12

The original eight live runs under `live-151648572057` completed 3,324 frames: heavy iron-sword/right-main/torch
in first person and morphing trident/left-main/empty offhand in third person, each at 20/120 FPS caps with default
and bend-probe backends. Client/server heavy and morph outcomes agreed. Actual median frame intervals were about
50 ms at the low cap and 8.5–8.7 ms at the high cap; these are capture conditions, not a performance comparison.

Four original recordings had gaps unsuitable for a 100 ms motion grid. Repeats under
`C:/dev/steelclash-beta-audit/20261012/pal-live/capture-062444778621` retained the ordinary render-frame screenshot
capture and omitted the additional reflective mesh diagnostic. The poor recordings remain excluded; this condition
change does not establish that the diagnostic caused their gaps. The repeated heavy-20 bend and morph-20
default/bend/morph-120 bend runs all met the existing unique-frame, 20 ms maximum grid-error rule. Their maximum
grid errors were 7.21/11.27/18.59/4.39 ms respectively. Onion-skin sheets and timestamp selections accompany them.
The high-FPS morph's flagged steps match the earlier default backend's 1/3/4/11/12 flags. Inspection shows the fast
windup/morph and release movements; no addon-only cut was established. This is not a full motion/alignment pass.

Four additional native runs exercised ordinary humanoid mob morphing and resource reload with the addon absent
and enabled. Both stacks rendered the requested mob transition and completed reload with a new animation
generation and client/server return to idle. Reload overlays interrupt screenshot collection for several seconds,
so those recordings are functional reload evidence, not uniformly timed motion evidence. The addon mob-morph
recording also has a 483 ms gap and remains unsuitable for a whole-sequence motion comparison.

Across the original eight and eight completed repeat/lifecycle runs, **16 native functional captures and 5,051
complete screenshots** are retained. The actual dev Sodium renderer was active; captured windows were hidden and
the mouse released. Main arm, items, FOV, armor, model, commands, source hashes, timestamps and outcomes are in
each report. Initial loading-window behavior and a full renderer/modpack matrix remain unsupported.

Live post-render mesh data from the original addon runs confirms visible free-arm deformation in first person
(maximum bend approximately 0.3, local vertex displacement approximately 1.89024), and visible body deformation
in third person (approximately 0.12 / 0.75847). The weapon arm had zero bend/displacement. The left-main spear's
support arm also remained unbent, as required by the prototype. These are actual posed mesh vertices, not just
channel values; they do not establish world-space blade endpoints or a production style improvement.

The next default late-guard-tracking run recorded 139 screenshots, then stalled while the integrated server
rescheduled chunk-unload futures during shutdown. Two `jcmd` thread dumps confirmed that loop while FastQuit
waited for the server. The exact owned disposable JVM was stopped after retaining the capture and diagnostics;
world-save completion and clean exit were not established. Its addon counterpart was not started, and neither is
counted in the 16 completed runs. This is a QA stack/lifecycle limitation, not a diagnosed PAL or Steelclash cause.

The bridge's intervening-transform conflict, exact hilt/tip alignment, clean tracking/lifecycle comparison and
broader acceptance remain open. PAL More Rotation is still not adopted; the normal backend remains the default.
