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
