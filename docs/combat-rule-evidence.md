# Combat rule evidence and fidelity work

Step 2 of [next-combat-animation-plan.md](next-combat-animation-plan.md), started 2026-10-11. The two supplied
reports describe reference behavior and another source snapshot. Their claims are compared with this checkout;
they do not prove all 72 historical candidates remain present. BodyHealth/LSO remain deferred.

| Rule | Current implementation | Reference and difference | Decision / evidence |
|---|---|---|---|
| Active-parry timer extension | A catch extends the server timer and the matching owner's active timer without restarting prediction | Full technical report N07 identified the missing synchronization; no balance inference was required | Fixed with a timer-only matching-action merge; verification below. |
| Active-parry catch stamina | Additional frontal catches spend attack stamina damage × guard multiplier; the active attack continues and cannot be disarmed by this catch | Detailed report's outcome matrix describes no separate cost in baseline descriptions. Its main AP section establishes directional protection/no disarm, not a current controlled stamina measurement | Source-versus-code discrepancy remains open. Preserve cost for this synchronization fix and measure before tuning. |
| Successful counter stamina | Counter avoids the incoming block cost; action/feint costs remain, with no refund | Detailed report §§4.4–4.5 supports avoided impact cost and explicitly leaves extra refunds unresolved | Keep current rule; no speculative refund. |
| Jab damage | Configurable multiplier (default 0.25) applied through vanilla attack dispatch | Detailed report's jab section reports 10 Chivalry damage with community/shield-dependent variation; the technical report's fixed-damage patch is absent | Do not transplant raw cross-game HP values. Material/enchantment scaling and intended normalization need a separate measured decision. |
| Release interruption | Real damaging release contacts queue a flinch for the end of the server tick; same-tick trades are retained | Detailed report's release-interruption discussion supports release contacts, with a Katars exception; the PDF's windup-only proposal conflicts | Existing focused tests cover the behavior. Do not remove release interruption from an unverified proposal. |
| Heavy hyperarmor | Opted-in heavy attacks suppress flinch during windup and release | The older report proposes narrower windows but labels them unverified | Preserve current windows pending weapon-specific evidence. |
| Hard/soft stagger | Later soft stagger cannot relax or lengthen an existing hard guard break; same strength retains longer duration | C09 was reproduced during step 1; the project now records an explicit precedence policy | Already fixed and verified in the prior C04/C09 checkpoint. |
| Slam cover | Solid collision shapes along the ground path stop secondary effects; allies/pets retain protections | C04 was reproduced; this is an explicit Minecraft cover policy, not a measured Chivalry constant | Already fixed and verified in the prior C04/C09 checkpoint. |
| Projectile headshot | Ordinary projectile baseline stays at 1.25× | Plan explicitly preserves this baseline while anatomical regions are investigated later | No new melee headshot bonus, limb HP or injury rule. |

Reference locations: `C:/dev/Chivalry_2_Combat_System_Maximum_Detail_Report_v2-1.md` (AP §§4.3–4.7 and outcome
matrix around line 2035; jab around line 495), `C:/Users/tange/Downloads/steelclash-full-report.md` (N07 around
line 537, historical fixed-jab patch around lines 125–135). Claims retain those reports' confidence labels.

## Timing inputs retained for measurement

These are **shipped profile inputs at reference attack speed**, not measured Chivalry timings or every item's actual
in-game duration. `CombatMath.timings` applies weapon/entity speed scaling; configured values may also differ.
Resolved native client/server durations are measured below; inputs and actual item speeds are distinct.

| Slash family | Windup / release / recovery (ms) | Combo windup (ms) | Riposte windup (ms) | Heavy extra windup / recovery (ms) |
|---|---|---|---|---|
| Sword | 500 / 425 / 750 | 725 | 500 | 250 / 100 |
| Spear | 700 / 350 / 900 | 1025 | 600 | 250 / 150 |
| Blunt | 500 / 475 / 750 | 750 | 500 | 250 / 150 |

Source: `src/main/resources/data/steelclash/steelclash/weapon_profile/{sword,spear,blunt}.json`. Preserve these
family differences; do not replace every windup with the PDF's generic range. Exact reference counter/AP durations,
extension values, turn limits and active-parry stamina accounting remain tuning questions.

## Measured representative weapons

Eighteen accepted ordinary-input cases contain 2,496 actual frames, covering slash, heavy upgrade, slash→stab
combo, opponent riposte, turn cap and a second-attacker AP catch for each family. Conditions: survival,
torch offhand, actual Sodium, hidden window, ungrabbed mouse; per-run source hashes and timestamps accompany
the [measurement record](archive/rule-measurements-20261011.md).

These are Steel Clash's native resolved phase durations in milliseconds, verified independently from profile
inputs and measured attack speed. They are not new measurements of Chivalry 2 or exact wall-clock/frame durations.

| Weapon / actual attack speed | Slash W / release / recovery | Heavy slash W / recovery | Chained stab W | Riposte slash W |
|---|---|---|---|---|
| Iron sword / 1.6 | 500 / 425 / 750 | 750 / 850 | 800 | 500 |
| Trident / 1.1 | 789.707 / 394.853 / 1,015.337 | 1,071.745 / 1,184.560 | 1,156.357 | 676.892 |
| Mace / 0.6 | 645.497 / 613.222 / 968.246 | 968.245 / 1,161.895 | 1,000.521 | 645.497 |

Heavy release retains the normal slash duration. Combo measures the second **stab**, not the profile's slash
combo input in the preceding table. Riposte uses the ordinary slash release/recovery. All three actual server
sweeps reach the configured 360°/s turn limit without exceeding it under repeated 90° requests during windup/release.

The pinned reference mapping includes the holding/chamber period in windup and replaces the previous recovery
with combo startup. A larger combo windup or unchanged one-handed riposte startup alone is therefore not a bug;
see the [original timing mapping](archive/findings-history.md#chivalry-2-timings-2026-10-07). No timing bug or
balance change is justified by these measured cases.

The additional ordinary jab caught during AP costs **8 / 8 / 7.2 stamina** for sword/spear/blunt, matching the
configured jab guard damage and guard multipliers. This isolates the extra catch from the initial parry cost;
the normal riposte still reaches release without HP loss. Free-catch fidelity remains unresolved. The official
[2.9 live-test notes](https://chivalry2.com/2023/07/18/chivalry-2-reclamation-update-patch-notes-2-9-live-test/)
confirm AP disarm protection and a corrected-riposte double-cost fix, but do not quantify this separate catch cost.
Preserve it until controlled reference evidence resolves the difference. Jab normalization, weapon-specific
hyperarmor and exact AP constants remain explicit tuning questions.

## N07 synchronization acceptance

The corrected merge must match attack serial, type, heavy/morph/counter-feint flags, variant and mirrored side.
Both snapshot/local phases must permit AP; a packet may cross windup to release. Deduct nonnegative estimated
one-way latency, ignore expired/stale confirmations, and never shorten a longer local timer. Merge only AP ticks;
retain the predicted phase clock, lifecycle flags, presentation and queued action. Reuse the existing wire fields.

Acceptance requires focused lifecycle tests, real catch/codec GameTests in both optional-mod variants, an actual
second-attacker live packet confirmation, and mutations that remove server dispatch/client merge. Until that
evidence is recorded, the patch remains an implementation checkpoint rather than a resolved claim.

Verified N07 checkpoint, 2026-10-11: full build and 213 fresh JUnit checks pass; both 174-required-test GameTest
variants pass. An ordinary second-attacker jab extends the sampled server timer from 3 to 4 ticks (net of the
tick decrement), and a handler-confirmed client 3→4 merge follows about 20 ms later without HP loss or interrupted
release. All five selected mutations are caught, including independently omitting dispatch and omitting owner
merge. Existing payload fields are reused; balance, costs, damage, timing inputs and packet format are unchanged.
The separate client timer becoming zero before a later one-tick server window is confirmed can legitimately be
restored while that same attack is still protected; confirmations expired after transport are rejected.
