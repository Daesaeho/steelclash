# Combat and animation goal

Improve Steel Clash so combat feels close to Chivalry 2 while each weapon family has its own original fighting style. This goal covers **all six steps** in the canonical [implementation plan](next-combat-animation-plan.md); its deliverables and “Done when” clauses are required completion gates.

## Scope and constraints

- Preserve committed windup → release → recovery, directional swings, defense and reaction behavior, weapon timing, reach and collision. Separate verified reference claims, measured behavior and unresolved tuning.
- Give dagger, sword, two-handed sword, axe, blunt, polearm, spear, rapier and staff recognizable preparation, body/offhand motion and follow-through. Keep ready-to-attack transitions readable and the weapon arm tied to the traced arc, preserving solved grip, hilt/tip alignment and light/heavy/mirrored identities.
- Require live transitions and opponent-driven evidence alongside reproducible pose, alignment and evenly timed motion captures. Cover first/third person, humanoid mobs, hands, handedness, renderer conditions, FOV/FPS, reload/invalid-file retention and lifecycle transitions; frozen poses alone do not close a gate.
- Reproduce proposed defects against the current checkout before fixing them. Use relevant build, unit/GameTests, mutation checks, visual evidence and documentation as specified by the plan.
- BodyHealth and Legendary Survival Overhaul remain deferred. Commits and pushes are authorized for this goal; release publishing and tagging require a separate instruction.

## Weapon style priorities

These describe original style direction, not exact Chivalry choreography or gameplay bonuses: dagger compact and guarded; sword balanced and readable; two-handed sword broad and committed; axe shoulder-led and diagonal; blunt compressed and weighty; polearm long-lever and body-led; spear thrust-focused; rapier side-on and precise; staff wide-gripped with coordinated circular recovery. Authoring and implementation details are in the plan.

## Current status

The [canonical plan](next-combat-animation-plan.md#current-progress) owns current progress and the next action. [Testing](testing.md#current-verified-checkpoints) owns verified checkpoint counts and [unsupported checks](testing.md#remaining-unsupported-checks); those limits remain acceptance work. [Combat-rule evidence](combat-rule-evidence.md) owns unresolved fidelity questions. Historical dated records and early style drafts are preserved in [combat-animation-checkpoints.md](archive/combat-animation-checkpoints.md).
