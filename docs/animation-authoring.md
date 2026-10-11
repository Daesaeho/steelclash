# Animation authoring prototype

`PalAnimationConverter` and the offline `palAnimationImport` task convert a strict linear PAL JSON subset into
Steel Clash phase clips. The [roadmap](next-combat-animation-plan.md#4-build-a-blockbench-authoring-workflow-and-refine-weapon-styles)
still requires editable Blockbench projects, an actual exporter round-trip and rendered alignment/motion acceptance.
The original [sword sample](animation-sources/sword-slash-linear.pal.json) is a hand-authored regression fixture,
not a Blockbench export or a shipped style replacement.

## Accepted contract

| Input | Conversion |
|---|---|
| `format_version: "1.8.0"`, named `animations` entry | One animation; positive length in seconds; optional `loop: false`. |
| `body.rotation` | XYZ degrees; negate X/Y to compensate `ProceduralSwingAnimation.wholeBody`. |
| `torso`, `head`, `left_arm`, `right_arm`, `left_leg`, `right_leg` rotation | XYZ degree offsets on the matching runtime part. |
| Free-arm `bend` | Scalar degrees or `[x,0,0]`, mapped to `leftArmBend`/`rightArmBend`. |
| `palImportArm=left` | Source uses physical left-main bones; normalize limb names and Y/Z signs to the runtime's canonical right-main asset. |
| Constant vector or ascending timestamp map | Linear interpolation; key objects accept only `vector` and optional `easing: "linear"`. |
| Release/recovery boundaries | Require `0 < release < recovery < animation_length`; split and normalize each phase to `[0,1]`. |
| Base Steel Clash asset | Preserve grip, grip gap, heavy scale, first-person settings and all unrelated clips. Two-handed bases protect both arms. |

Bone rotations remain runtime Euler channels; the importer does not infer or retarget pivots, hierarchy or a different
rotation order. Position, scale, geometry/hierarchy metadata, item bones, weapon-arm edits, protected support-arm
edits, torso/leg bends, Y/Z bend, nonlinear curves, Molang, unordered/duplicate numeric timestamps, nonfinite values
and adjacent angle jumps of 180 degrees or more fail with context. The file importer also rejects repeated JSON
property names before tree parsing can discard them. Unwrap large rotations explicitly. Empty motion
fails instead of replacing clips with an empty pose.

The [PAL Bend Player Tools exporter](https://github.com/kltyton/Pal-bend-player-tools/blob/main/pal_bend_player_tools.js)
can emit source-rig metadata and compensation translations after hierarchy/body baking. Those transforms need
explicit retargeting; this prototype rejects them. Removing metadata or translations to force acceptance can change
the authored motion and is not a validated workflow. Its marker `runtime_local_v1` alone is accepted; accompanying
source-rig objects are rejected. Review the full export before extending the contract.

The converter retains source keys and phase boundaries, then bakes additional samples to keep the runtime monotone
cubic interpolator within the requested per-axis degree tolerance of the linear source. Adjacent sampled changes
are bounded by a quarter of that tolerance. Each phase is limited to 4,096 keys. This can produce dense clips;
performance, rotation-order parity and rendered bend appearance remain acceptance work before installing assets.

## Run an offline import

Run from the repository in PowerShell with Java 21. Choose an output path that does not exist. Quote each Gradle
property, including decimal values; unquoted properties can be split by PowerShell.

```powershell
.\gradlew.bat palAnimationImport `
  '-PpalImportSource=docs/animation-sources/sword-slash-linear.pal.json' `
  '-PpalImportBase=src/main/resources/assets/steelclash/steelclash_animations/sword.json' `
  '-PpalImportOutput=C:/dev/steelclash-beta-audit/sword-candidate.json' `
  '-PpalImportAnimation=steelclash.sword.slash' `
  '-PpalImportAttack=slash' `
  '-PpalImportArm=right' `
  '-PpalImportRelease=0.5' `
  '-PpalImportRecovery=0.925' `
  '-PpalImportTolerance=0.25'
```

The output parent directory must already exist. Validation completes before output creation. Existing outputs and
inputs cannot be overwritten, including filesystem aliases. Inputs are limited to 8 MiB. The task writes a candidate;
it does not install assets, reload Minecraft, launch a client or change weapon profiles. The boundaries partition
authoring time; gameplay phases continue to use the server's resolved weapon timing.

## Acceptance still required

Save the first original sword project as `.bbmodel`, export through the reviewed plugin, identify its supported
coordinate/metadata path and round-trip into a candidate. Prove source/runtime axes and mesh poses before replacing
the shipped sword clip. Compare matched old/new live transitions, hilt/tip alignment and evenly timed motion;
verify phase seams, heavy/mirrored behavior, F3+T and invalid-file retention. Continue dagger/spear/blunt and the
remaining families only after that workflow succeeds. Use [animation acceptance](animation.md#client-acceptance-checks)
and keep the optional bend adoption decision separate from importer unit results.
