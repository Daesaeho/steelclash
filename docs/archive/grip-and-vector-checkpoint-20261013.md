# Player grip refinement and Vector API experiment — 2026-10-13

This is an intermediate animation checkpoint. It does not close the Blockbench/style or renderer acceptance gates.

Verification before commit: required compile check and a full build passed; all 254 JUnit tests ran freshly with
zero failures or skips; three of three targeted mutations were caught and restored; documentation links and all six
roadmap gates passed preservation checks. No server behavior changed, so GameTests were not rerun for this checkpoint.
Build and mutation logs are under `C:/dev/steelclash-beta-audit/20261012/retarget-check/commit-*`.

## Player reach constraint

`CombatPose.rig` passes the two-handed flag to `WeaponRig.solve`. The solver projects the main hand to a reachable
support-grip shell, uses a nine-pixel support palm target and at most four pixels of shoulder travel, and compensates
the item rotation to preserve the blade orientation. Existing one-handed overloads retain their previous solve.
The rendered arm length remains ten pixels. Attack timing, server reach and damage dispatch are unchanged.

Five new deterministic tests cover the deep sword windup, 1,500 angle/body/grip-gap combinations, legacy overload
parity, mirrored arm length, and densely sampled constraint-boundary continuity. Three mutations restore the
unbounded hand, restore fingertip shoulder travel, or omit wrist compensation. The intended tests catch each.

The opt-in `BladeMeshArmMixin` reads both actual player arm-part matrices before hand/socket rotations. The mesh
probe matches the frame, entity, tick and hand, records the skin model and exposes missing transforms explicitly.
It is loaded only with `-PbladeMeshOut`; ordinary clients skip this diagnostic mixin.

Existing disposable native evidence is under
`C:/dev/steelclash-beta-audit/20261012/retarget-check/native-204424719325`:

| Main arm | Draws | Full-weight poses | Main-hand handle contact | Support-hand handle contact | Maximum release axis error |
|---|---:|---:|---:|---:|---:|
| Right | 54 | 45 | 45/45 | 39/45 | 0.000063 degrees |
| Left | 54 | 45 | 45/45 | 45/45 | 0.000018 degrees |

These are the native sword candidate with an empty offhand, two-handed swords enabled, the reviewed slim player
model and standard sword geometry. Contact uses opaque extruded handle/outline/pommel texels against the last two
pixels of the actual undeformed vanilla hand. It is not a finger model, anatomical collision, or proof for arbitrary
rigs. The right-hand support misses remain open. The original first-person placement also shows only 4/6 full-weight
windup hilts and 1/6 tips inside the view for each main arm.

`MobCombatPoses` still derives its support arm through `pose.gripArm` and does not apply the new solver's bounded
support-shoulder offset. Mob support, ordinary-input motion, occupied-offhand controls, other skins/weapon families,
and wider renderer/model compatibility require further acceptance. Do not describe this checkpoint as a complete
two-handed grip fix.

## Isolated Vector API benchmark

Java 21 `DoubleVector` uses four double lanes (256 bits) on the tested machine. Compare it with a scalar sampler
using identical prepared values/tangents, padded layout, arithmetic grouping, output objects and checksum consumption.
Preparation is excluded. Normal scalar JVM optimizations remain enabled; no fused multiply-add is introduced.

Seven fresh JVM runs per version alternate order, with eight seconds warmup and four seconds measurement per run.
Java uses its default tiered JIT and GC, a fixed 256 MB heap and OS scheduling without processor affinity.

| Metric | Scalar median | Scalar range | Vector median | Vector range |
|---|---:|---:|---:|---:|
| Elapsed ns/pose | 351.48 | 141.74–365.45 | 339.72 | 331.15–350.23 |
| Sampling thread CPU ns/pose | 340.49 | 138.41–364.02 | 331.38 | 320.75–346.12 |
| Allocated bytes/pose | 750.45 | 750.45–750.45 | 750.45 | 750.45–750.45 |

All 14 JVMs completed successfully. The ranges overlap and scalar timings exceed the comparison helper's 25% noise
threshold: **no reliable Vector API speedup was established**. All 47,963,520 checked axis values match the unchanged
working-tree sampler exactly, covering linear/cubic modes, mirroring, scaling, exact keys and neighboring doubles,
random/out-of-range progress and empty/missing/short/duplicate-time channels.

The snapshot contains 309 shipped clips and six linear candidate phases. This microbenchmark excludes arm solving,
blending, PAL rendering, GPU work, AI and Minecraft frame time. It justifies no in-game FPS claim or runtime adoption.
The experiment and JVM flags remain outside the mod. Source hashes, raw fork logs, validation and comparison JSON
are in `C:/dev/steelclash-beta-audit/20261012/vector-animation/trial-20261012-224922`.
