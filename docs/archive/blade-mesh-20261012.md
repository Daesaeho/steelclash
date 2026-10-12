# Actual sword mesh and placement experiment — 2026-10-12

`-PbladeMeshOut=<new absolute .jsonl path>` now records the actual submitted vanilla iron-sword baked quads,
their post-hand/display draw matrices, projection/model-view matrices, camera and actor positions, socket position
and actual PAL render partial. It is opt-in, local-player/iron-sword only, bounded to 512 samples and 256 unique
quads per draw, and refuses to overwrite an output. Ordinary launches skip both diagnostic mixins.

## Evidence and interpretation

The initial method-head hook produced no mesh file with active Sodium, despite 54 completed pictures. Sodium's
reviewed fast path bypasses that method. `native-192703145548` is excluded from mesh evidence. The corrected
callsite wrapper observes the same quads before standard or Sodium submission without disabling its renderer.
The runner now rejects a requested probe with no evidence instead of accepting screenshots alone.

`native-193156819446/source-right` records 54 draws, 18 in the actual PAL first-person model pass, each with 50
vanilla sword quads. The hook covers PAL's model hands in both views through `ItemInHandRenderer`, rather than
assuming first person only uses vanilla hand rendering. The live active-Sodium original at `native-193742001123`
completed 797 screenshots and 220 mesh draws. The verified absent-Sodium/vanilla run at `native-200419712714`
completed 707 screenshots through the same probe. All captured windows were hidden with the mouse released.
Its 163 mesh draws reproduce the original windup visibility failure across 37 fully weighted samples, and its
live release-axis discrepancy stays below 0.00020°. This supports both reviewed submission paths; it does not
diagnose the small active-Sodium live discrepancy or establish support for every renderer replacement.

The local analysis checks every serialized vertex against its draw/clip matrices. For the reviewed unchanged
16×16 vanilla sprite, it uses the crossguard centre `(5.5/16,5.5/16,0.5)` and opaque upper tip corner `(1,1,0.5)`.
The tip corner is witnessed in the submitted extruded edge geometry; transparent front-face bounding corners are
not used to infer a generic model's tip. These are specific mesh landmarks, not a general item-geometry detector.

The actual camera-relative render coordinates can be located relative to the captured camera. First-person points
include intentional composition offsets and must not be treated as authoritative gameplay endpoints. The debug
blade remains a gameplay reach ray, which is longer than this mesh. Socket-to-crossguard distance is approximately
8.54 cm in the reviewed model; that socket measurement is not proof of the player's physical palm or support grip.

## Placement trials

The original candidate's variant-2 mirrored heavy has zero visible crossguards/tips across 48 fully weighted
first-person windup mesh samples. The corresponding screenshots show the weapon leaving the view entirely.
Two temporary resource-pack variants change only `first_person.forward` / `down`:

| Placement | Complete live pictures | Mesh draws | Visible crossguards / full-weight windup samples | Tip samples inside view |
|---|---:|---:|---:|---:|
| Original 4 / 3.5 | 797 | 220 | 0 / 48 | 0 |
| 8 / −2 | 692 | 171 | 36 / 36 | 0 |
| 12 / 0 | 684 | 141 | 31 / 31 | 1 |

The 8/−2 trial exposes the hand but places too much forearm near the camera. The 12/0 trial reduces that obstruction
and remains the next placement candidate, not an adopted style. Blade tips can still extend beyond the field of view
in the low ascending preparation; hilt visibility alone does not close the full readability gate.

The 8/−2 frozen comparison (`native-194722911696/source-right`) preserves **36 pixel-identical third-person
poses** while all 18 first-person views change. Its 54 measured mesh draws retain release axes within 0.00002°
of the appropriate arc (the intentionally composed view arc in first person). Live release measurements show
approximately 0.09–0.14° maximum discrepancy; initial live metadata used the global timer rather than PAL's actual
render partial, so do not treat those series as exact alignment acceptance or as a diagnosed renderer defect.

The 8/−2 recording misses the existing uniform-grid rule by a maximum error of 20.59 ms; it is excluded from
whole-sequence smoothness acceptance. No threshold was relaxed. Probe-enabled runs are geometry evidence,
not performance comparisons. Replays without mesh collection are needed for motion review.
The restored 12/0 replay without the probe (`native-201345871196`) completed 728 screenshots and a valid
100 ms selection with maximum error 14.39 ms. Inspection shows adjacent fast preparation/release movement,
with flags at 1/3/4/5/6/7/8/9/10. The forearm still occupies a substantial part of the lower view; this does not
establish final composition or smoothness acceptance. Refine the presentation and check closer seams before adoption.

Evidence, actual commands/source hashes, raw JSONL, analyses, screenshots and the placement contact sheet are in
`C:/dev/steelclash-beta-audit/20261012/retarget-check`. No shipped animation, source-project motion, gameplay
timing, reach, collision or attack dispatch changed. World hilt/tip-versus-reach offsets, actual palm/support grip,
left-main/occupied-hand conditions, other weapons/custom renderers and the full style/motion matrix remain open.

The restored fresh build passes **245 JUnit tests with zero failures/skips**. A native mutation inverts the configured
output gate and disables collection; the intended `bakedMeshEvidence` assertion catches the missing file despite
completed pictures. The healthy baseline requires 54 draws/18 first-person passes with the reviewed 50-quads mesh
and validates its vertex/draw/clip associations. The mutation was restored. Server rules were untouched, so no new
GameTests were required. The completed fresh 38-file JUnit report confirms the build's scope.
