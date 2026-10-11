# Implementation findings

This page indexes the findings that constrain current work. The complete dated research, reproductions,
measurements and earlier checkpoints are retained in [findings history](archive/findings-history.md).
The [roadmap](next-combat-animation-plan.md) owns progress; [combat-rule evidence](combat-rule-evidence.md)
owns reference confidence and unresolved fidelity questions.

## Mod IDs and Spartan integration

Spartan Weaponry is `spartan_weaponry_unofficial`; Spartan Shields is `spartanshieldsunofficial`.
Their integrations are optional. Reach and attack-speed traits use vanilla attributes. Only guarded compatibility
classes reference their APIs. Keep shield use active and preserve `LivingDamageEvent.Pre` so Spikes, Payback
and energy costs retain their hooks. See [mod IDs](archive/findings-history.md#mod-ids-verified-from-the-jars-meta-infneoforgemodstoml),
[reach/speed traits](archive/findings-history.md#b-are-sw-reachspeed-traits-attributes--yes) and
[shield/input findings](archive/findings-history.md#c-how-do-ss-block-and-bash-work--conflict-found).

## Sub-tick combat and Chivalry 2 timings

The simulation uses microseconds; profiles can override phase durations in milliseconds. The pinned Chivalry
data mapping includes the holding/chamber period in windup. Combo replaces the prior recovery rather than
requiring every combo windup to be shorter than a standalone windup. One-handed ripostes can retain standalone
startup. Review the [timing mapping](archive/findings-history.md#chivalry-2-timings-2026-10-07) and
[sub-tick architecture](archive/findings-history.md#sub-tick-combat-timing-architecture-plan-5-2026-10-07)
before changing balance. Existing [benchmarks](archive/findings-history.md#combat-benchmark-and-baseline-architecture-plan-3141-2026-10-07)
describe their measured versions, not current performance claims.
Representative native sword/spear/blunt timings, actual trace caps and AP catch costs are now recorded in the
[measured rule table](combat-rule-evidence.md#measured-representative-weapons); no new balance inference is made.

## Defense, counter-feint, cleave and release interrupts

Damage continues through vanilla `Player.attack` / `Mob.doHurtTarget`; accepted swings reset invulnerability
before their one-per-target dispatch. Defense, turn limits and collision belong to the server/core rules.
See [cleave/thwack](archive/findings-history.md#cleave-and-thwack-architecture-plan-18-2026-10-07),
[counter-feint](archive/findings-history.md#counter-feint-architecture-plan-131-2026-10-07),
[release interrupts](archive/findings-history.md#report-gaps-step-b-release-interrupts-report-28-2026-10-08)
and [bug audit](archive/findings-history.md#bug-audit-and-fixes-2026-10-08).

## Animation presentation and first-person rigs

The weapon arm follows the hit arc; authored body and free-hand motion must preserve grip and blade alignment.
Ready rigs solve in camera space, including shoulder pivots, before body-space composition. Player and mob
held-item rotation conventions differ. Live transitions, rendered transforms and focused motion comparisons
supplement frozen pose sheets. Current contracts and capture instructions live in [animation.md](animation.md).
The [optional bend prototype](archive/pal-more-prototype-20261011.md) demonstrates why runtime injection order
and actual mesh vertices must be checked alongside idealized axis tests.
Relevant evidence: [early presentation](archive/findings-history.md#animation-presentation-findings-2026-10-08),
[arm motion](archive/findings-history.md#arm-motion-by-swing-type-2026-10-08),
[view-space ready rigs](archive/findings-history.md#view-space-ready-rigs-and-humanoid-handovers-2026-10-11)
and [baseline acceptance](archive/testing-history.md#broader-baseline-and-background-work-2026-10-11).

## Bug audit and active-parry acknowledgments

The [earlier bug audit](archive/findings-history.md#bug-audit-and-fixes-2026-10-08) records input, prediction,
network lifecycle and fixture findings. Its counts describe that checkpoint.

N07 is fixed at `c342391`: positive server extensions dispatch the existing snapshot and the owner merges only
the matching active action's remaining timer, accounting for packet age. Queued input and predicted phase stay
intact. Stamina-cost fidelity is a separate open question. See [rule evidence](combat-rule-evidence.md),
[implementation findings](archive/findings-history.md#active-parry-window-acknowledgments-2026-10-11)
and [verification](archive/testing-history.md#active-parry-extension-synchronization-2026-10-11).

## PvE weapon retrieval, team rules, downed and revive

Mobs share the combat rules. [Weapon retrieval](archive/findings-history.md#weapon-retrieval-2026-10-08)
is a separate PvE behavior. Test entities need deliberate position, facing, attributes and AI staging;
mock players are not automatically visible to level queries. See [team rules](archive/findings-history.md#team-rules-report-21-architecture-plan-19-2026-10-07),
[downed/revive](archive/findings-history.md#downed-and-revive-report-182-architecture-plan-211-2026-10-07)
and [GameTest fixture guidance](testing.md#fixtures-and-evidence).

## Optional compatibility and local loading mods

Candidate inspection is recorded in the [kinematics review](archive/kinematics-compatibility-review.md); support requires
the roadmap's runtime gates. BodyHealth/LSO are deferred. User-installed loading mods remain local and ignored,
not release dependencies; record the actual addon stack when comparing captures. See
[loading-mod evidence](archive/testing-history.md#optional-local-loading-mods-2026-10-10) and
[compatibility policy](compat.md).
