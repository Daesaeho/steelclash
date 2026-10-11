# Representative rule measurements — 2026-10-11

Evidence: `C:/dev/steelclash-beta-audit/20261011/weapon-measurements`. Native reports and actual screenshots
are local audit artifacts, not release assets. Current interpretation is in [combat-rule evidence](../combat-rule-evidence.md#measured-representative-weapons).

## Accepted scope

`measurements.json` records 18 cases and 2,496 complete PNG frames: each of iron sword, trident and mace with
attack, heavy, combo, riposte, turn-cap and active-parry scenes. Every accepted run has an invocation record,
UTC start, command/exit code, selected source hashes and native client/server diagnostics. `run.py` checks
resolved archetype, survival conditions, real attack outcomes, screenshot completeness and hidden/ungrabbed frames.

`analyze.py` independently scales JSON timings using measured attributes, checks native phase-duration/timing
values on both sides and requires actual release. It measures the second serial for slash→stab combos and only
heavy samples after the ordinary upgrade. Server samples are deduplicated by tick for trace/catch measurement.
Captured trace yaw is the view actually committed by Combat.tickServer, not a hypothetical second limiter step.
All three turn scenes reach 360°/s and stay within that configured cap. AP catches isolate stamina decreases
while the same action remains protected: 8 / 8 / 7.2, with no HP loss and real second-attacker/owner-merge acceptance.

## Background focus correction

The first sword turn-cap run (`sword-turn-cap-023038814427`) failed because vanilla re-grabbed the mouse after
closing its loading GUI. It is retained and excluded from the accepted count. GLFW hiding alone can leave
Minecraft's internal window-active flag true. The opted-in live/pose diagnostics now set that flag false and
release an unexpected mouse grab while keeping their window hidden. Ordinary gameplay is outside these branches.
The successful turn replay and subsequent captures verify the live path; a fresh three-shot frozen sheet checks
the matching pose path. Initial startup-window visibility before the first diagnostic event remains unverified.

## Limits and next step

The native resolved clock values are precise; screenshots target 50 ms and actual timestamps must be used for
wall-clock analysis. This is integrated-server evidence with controlled opponents, not LAN latency certification,
every weapon item, or measurement of current Chivalry 2. Remaining AP cost/jab/hyperarmor reference questions
are explicit, and no speculative balance edits are included. Step 3 can now proceed with an isolated PAL More
Rotation prototype while those reference questions remain open.
