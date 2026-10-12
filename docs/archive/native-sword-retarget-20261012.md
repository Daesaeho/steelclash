# Native sword retarget checkpoint — 2026-10-12

The editable source and actual exporter output now have a validated source-aware import path. The candidate is
QA-only; no shipped style, weapon timing, reach, collision, packet or damage dispatch changed. Step 4 remains open.

## Conversion and verification

The canonical-rig validator proves that the export's six child rotations equal the body/local source fold at every
union key time, then recovers body and local channels separately. Authored weapon/support arms, changed pivots,
parents or rotation modes, compensation translations and unsupported metadata fail with context. Exact linear
phase interpolation and explicit zero `hipLag` reproduce 112 actual Blockbench world bone matrices/joint positions
for the four authored bones across 14 poses in each take. This is not arbitrary-rig retargeting or a mesh endpoint proof.

The paired importer produces six slash phases with three keys each, preserves the base's grip/camera metadata and
other actions, and requires a paired take when replacing a nonempty authored heavy windup. An empty heavy windup
still uses the documented scaled-light fallback; it contains no authored motion requiring paired replacement.

The CLI generated `sword-candidate.json`, SHA-256
`54b52baa1c43147351a378adc262f39996b7db859f4d599dcf646493ed52610c`, under
`C:/dev/steelclash-beta-audit/20261012/retarget-check`. The source/export hashes remain those in the
[source contract](../animation-sources/sword-original.contract.json).

The final fresh `build --rerun-tasks` completed all eight tasks with **241 JUnit passes, zero failures/skips**.
Nine deliberate mutations failed their intended assertions: export provenance, protected zero weapon tracks,
linear interpolation, authored hip policy, heavy phase selection, paired import enforcement, native semantic flags,
the live missing-variant fallback bug and the overstrict empty-heavy import guard. Restored sources built cleanly.
Logs, the mutation report and actual UTC invocation timestamps are in the external evidence directory. No server
GameTests were required because this checkpoint changes client pose data/selection and offline import only.

## Native candidate evidence

Captures use actual Sodium, the slim player model, FOV 95, 854×480, empty offhand and no armor. The candidate is
one-handed during these runs: capture-only runtime overrides set `twoHandedSwords=false` and `cameraMotion=0`.
The runner restores QA options/config bytes afterward. Every captured frame records a hidden window and released
mouse. Initial loading-window focus/visibility remains unsupported.

Selecting a pack was insufficient: required NeoForge development mod packs were appended after it and won the
resource lookup. The corrected runner puts the explicit required packs before the override. The new optional capture
guard checks the selected resource SHA-256/source pack, compares sampled loaded clips with that resource and logs
the actual runtime config. Every candidate run below has this active-resource proof.

| Evidence directory/case | Complete frames | Verified scope |
|---|---:|---|
| `native-154702699508/source-right` | 54 | Light/heavy/mirrored frozen poses, three views, right main arm |
| `native-154702699508/source-left` | 54 | Same points/views with left main arm |
| `native-154702699508/live-morph` | 133 | Client/server morph outcome; 100 ms grid error at most 8.85 ms |
| `native-154702699508/live-reload` | 130 | Reload/generation and return to idle; overlay gap excludes whole-sequence motion |
| `native-155508510588/live-heavy` | 106 | Corrected variant-2 mirrored heavy; four full-weight release bodies match the heavy take within 6.70e-9 radians |
| `native-161302594499/live-heavy` | 821 | 120 FPS cap; 100 ms grid error at most 4.10 ms; 50 full-weight release bodies match within 2.93e-8 radians |
| `native-161302594499/live-morph` | 819 | 120 FPS cap; functional morph and active asset proof |

These seven clean runs contain **2,117 complete screenshots**. The 20 FPS heavy replay still has a 376 ms gap and
177.07 ms maximum grid error; it proves phase selection, not uniformly timed smoothness. High-FPS heavy and the
20 FPS morph satisfy the unique-frame/20 ms grid-error rule. Their onion-skin flags require inspection, not automatic
pass/fail classification. The high-FPS trace also exposes a separate tick-boundary cache rewind described below.

A clean committed `03015bd` snapshot and the modified legacy backend produced **18 pixel-identical pose pairs**
under `native-152235066232/fresh-baseline` and `legacy-shield-control`, with the combat FOV effect fixed to zero.
This is bounded old-pack pose parity, not a full gameplay/renderer acceptance matrix.

## Reproduced selection and clock issues

The first active heavy trace selected variant 2, whose specialized phase clip was absent. Although a generic heavy
release existed, lookup fell back to generic light. The corrected order preserves a present specialized heavy clip,
then a present ordinary variant, otherwise inherits the generic heavy phase. Tests cover those branches and explicit
empty release/recovery. Actual heavy telemetry distinguishes the corrected heavy body from the light body.

At 120 FPS, the first frame after some ticks reuses an earlier pose: release progress goes from 0.33176 to 0.23529
even while the machine advances from 100,000 to 150,000 microseconds. The cache keys frame/entity tick/partial but
not phase elapsed/duration; PAL's zero-partial tick sample can precede the client's combat-clock tick. This is a
reproducible presentation defect to address next, not accepted smooth motion or a change to server timing.

## Exclusions and remaining gates

- `native-151617420595`: comparison with an older projection control changed six first-person pictures. The fresh
  paired control replaced it; no code regression was established by the unmatched historical comparison.
- All candidate-labeled runs under `native-152235066232`: the default mod resource overrode the candidate. Exclude
  them from candidate acceptance even though the pack was listed and functional recordings completed.
- `native-154702699508/live-heavy` (114 frames): active candidate, but it exposed the light-release fallback. Preserve
  it as the reproducer, not the corrected heavy-motion result.
- The initial ten-mutation review included a proposed empty-heavy behavior change. The current docs explicitly
  preserve scaled-light fallback, so that proposal was reversed. The retained nine-mutation report is authoritative.

World-space rendered sword hilt/tip alignment, close seam/motion review after the cache fix, invalid-file retention,
candidate humanoid mob rendering, offhand/support-arm conditions, wider model/renderer/FOV cases and matched
old/new live scenes remain open. The body/free-arm/foot project is an original prototype requiring style acceptance;
dagger/spear/blunt and the other families/variants still need authoring and verification. PAL bending and CPM adoption
remain separate decisions. BodyHealth/LSO remain deferred.
