# Tick-boundary pose refresh — 2026-10-12

The native source-import replay exposed a presentation rewind: an advancing heavy release could reuse a sample
from before the client combat tick. This checkpoint refreshes the rendered Steelclash layer and shared pose cache;
combat timing, hit sweeps, damage, packets and PAL's clock retain their existing behavior.

## Reproducer and source ordering

`native-161302594499/live-heavy` contains three visible sample rewinds across 50 release frames. One advances the
machine from 100,000 to 150,000 microseconds while rendered progress falls from 0.33176 to 0.23529. The maximum
rendered/raw-clock progress error is 0.11765. The body rotations match that stale rendered progress, so the error
is not merely an incorrect diagnostic label.

The installed PAL 1.1.6 source confirms `AbstractClientPlayerMixin` samples at partial zero inside the entity tick.
Steelclash advances its client clock in `EntityTickEvent.Post`. PAL's `PlayerAnimationProcessor` then deduplicates
using the float `tickCount + partial`; a near-zero partial can round to the same value and skip render setup after
the clock advanced. Steelclash's shared cache also previously omitted phase elapsed/duration from its validity key.

## Change and checks

`PoseSampleStamp` checks frame, entity tick, partial, phase elapsed and duration without allocating a key per lookup.
`CombatPresentation` retains its action/equipment/generation checks and explicit correction/hit-stop invalidation.
`ProceduralSwingAnimation` refreshes its own pose/rig when PAL supplies a stale sample at the actual bone call;
it reads the manager's render partial and checks animation generation. Further bones reuse that sample. It does
not retick PAL, change its animation clock or recompute the tick-owned first-person camera eligibility.

The restored fresh full build passes **245 JUnit tests with zero failures/skips**. Four focused stamp tests exercise
pre/post combat-tick ordering, same-frame clock advance, duration extension and frame/partial/invalidation. Three
core mutants each fail the intended test. A fourth mutant removes the actual layer's render refresh and fails the
deterministic native PAL probe. The retained result is **four of four valid mutations caught**.

An initial release-only native mutation replay survived twice: both recordings lacked the float-rounded zero-partial
case during the active attack. Those runs remain in `clock-mutations.report.json` and are not proof that the removed
refresh was safe. `clock-probe-mutations.report.json` is the final report with deterministic coverage.

## Deterministic native probe

`-PliveCaptureClockProbe` is opt-in for the heavy QA scene. After the client combat tick, it requires PAL's last update
to equal the player's integer tick, then sends exact partial zero through the real manager/processor and body/head
bone path. The manager's render partial is restored in `finally`; no actor/world state or animation clock is changed.
Probe observations are separate from screenshot/model diagnostics and are labeled in `capture.json`.

The healthy baseline at `native-190016149458/live-heavy` completed 821 screenshots and nine probe observations.
All nine matched the current clock, refreshed once at partial zero, reused the same sample across both bones and
preserved first-person mode/configuration. Its 50 ordinary rendered release frames also had zero progress error
and no rewind. These are distinct assertions: the component probe guarantees the awkward ordering; the screenshots
exercise normal rendered input. The deleted-refresh mutant is caught by the probe rather than by chance scheduling.

After restoration, `native-190307325064` completed heavy (822), morph (803) and reload (688): **2,313 screenshots**.
The heavy replay again has nine passing component probes and 50 ordinary release frames with zero rewind/progress
error; its body samples remain within 2.93e-8 radians of the authored heavy take. Client/server morph and reload
outcomes pass; reload advances the animation generation and returns to idle. The overlay interrupts recording, so
reload is functional evidence rather than a uniform motion sequence.

The heavy/morph 100 ms grids meet the existing unique-frame/20 ms error rule with maximum errors 4.44/3.84 ms.
Onion flags remain at 1/3/8/9/10/11 for heavy and 3/4/9/10 for morph. Inspected sheets show the first-person blade
leaving the view during heavy preparation, then returning during release, and adjacent fast morph/release motion.
The blade's offscreen preparation is a readability limitation requiring further visual work; these flags do not close
the candidate's full smoothness gate. Fine morph telemetry's largest body step is 3.43 degrees over 13.27 ms at
the deliberate windup replacement, rather than a large release-entry root teleport. Closer arm/item seam checks
remain required.

`native-190634538831` compares the fresh committed `03015bd` snapshot with the restored clock-fixed code using
armored right-main sword/trident shield poses: **18 pixel-identical pairs**. It preserves the earlier bounded legacy
pose control with combat FOV effects disabled. This does not establish wider renderer/modpack parity.

The external evidence root is `C:/dev/steelclash-beta-audit/20261012/retarget-check`; logs include the original
reproducer, failed and successful mutation attempts, actual commands/source hashes and restored checks.

## Acceptance scope

The probe establishes the reviewed default PAL body/head ordering and camera ownership in this heavy/empty-offhand
fixture. Wider feedback hit-stop, renderer/modpack, altered model and remote-player cases remain acceptance work.
It is not world-space sword hilt/tip evidence or a full weapon-family smoothness pass. Continue the native source
mesh/seam/lifecycle checks and contrasting family authoring under all six roadmap gates.
