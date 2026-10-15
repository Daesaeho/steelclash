# Server contact metadata — 2026-10-15

This is roadmap step 5, Deliverable A: retain authoritative contact data while preserving the default AABB collision geometry. Anatomical narrow-phase collision, Countered compatibility and the full step-5 gate remain separate unfinished work.

## Contact frames and ownership

`SwingTracer.Contact` retains three distinct positions:

- `entryPoint`: the segment entry into the tested historical collision AABB, including its blade/pick-radius expansion.
- `capturedPoint`: that entry clamped to the ordinary historical body AABB.
- `point()`: the captured body-relative offset projected onto the target's current position for delivery and effects.

An optional unique face normal belongs to `entryPoint`; starts inside and ambiguous edge/corner entries have no face normal. Provenance identifies ordinary-body contact, inflated entry with body projection, block-shape contact, or unknown/manual input. These points are derived from server intersections and are not rendered-mesh contacts. Parry sparks retain their existing offset effect point; that presentation point is separate from the captured body/entry geometry.

The captured `AttackIdentity` includes serial, attack type, heavy/morph/counter-feint flags, variant and mirrored state. World game tick and original release progress remain immutable through held delivery. Resolution progress is separate, and thwack/combo consequences cannot be applied to a different later attack. A reproduced stale held hit previously granted a combo to a newer whiff; the identity guard fixes that case.

Feedback and hit-reaction packets retain these fields, use double precision for absolute points/progress, and use protocol 13 on both peers. Local hit-stop eligibility uses the recipient's action serial, independently of the attacker's identity. Legacy unknown tokens retain their fallback, and a defender's immediately predicted guard follow-up keeps its parry feedback. Wall clanks retain the engine block-clip point and meaningful face normal. Damage still goes through vanilla attack dispatch and existing shield event hooks.

Disabling detailed contact effects (`tracedContacts = false`) also retains the captured contact and original recipient token in ordinary landed feedback. Review found that `HitFeel` previously dropped those fields when falling back to `Feedback.hit`, allowing a held old hit to freeze a newer attack. The added `aHeldHitWithPlainEffectsKeepsOriginalFeedbackToken` regression uses the real held queue, starts another attack in the same game tick and raises a wrong-facing guard before delivery. It checks damage without detailed effects, preserved identity/tick/token and rejection by the newer attack's feedback gate. Both final server variants pass it; restoring the old fallback fails that exact test.

## Server verification

Evidence root: `C:/dev/steelclash-beta-audit/20261014/contact-metadata/`.

| Check | Result | Evidence |
|---|---|---|
| Focused compile and geometry/recipient tests | 6 passed | `20261015-062510-916-focused.log` |
| GameTests with Spartan mods | 214 required passed | `20261015-062636-345-gametest-default.log` |
| GameTests without Spartan mods | 214 required passed | `20261015-062753-677-gametest-no-compat.log` |
| Deliberate regressions | All 7 caught by intended assertions | `metadata-mutants.report.json` |
| Isolated restored build | 267 fresh JUnit passed | `20261015-171818-371-restored-build.log` and saved XML |
| Final integrated main build | 281 fresh JUnit passed | `20261015-173824-217189-final-integrated-build.log`, source hashes, saved XML and reporter output |
| Final main GameTests with Spartan mods | 215 required passed | `20261015-173829-738979-final-integrated-gametest-default.log` and reporter output |
| Final main GameTests without Spartan mods | 215 required passed | `20261015-173924-120066-final-integrated-gametest-no-compat.log` and reporter output |
| Effects-disabled fallback regression | 1/1 valid mutation caught by the new test | `plain-effects-mutant.report.json` |

The seven original caught regressions replace held identity, overwrite original progress, discard the historical capture frame, grant a later whiff a combo, return a normal beyond the finite segment, reverse the face normal, and accept stale feedback tokens. The eighth restores the effects-disabled fallback. GameTests also cover direct/deferred data, multiple contacts, shield/weapon defenses, wall clanks, radius boundaries, and large-coordinate codec round trips. The isolated 214-test counts above precede the effects-disabled regression; final source-associated main checks include it and ran after the mutation restored the fix.

## Client diagnostics and remaining acceptance

`/steelclash_debug` draws bounded contact markers, entry-to-body projection, meaningful entry normals and blade travel. It reports source identity/tick/progress alongside hit-stop source data. At most 24 markers live for 2.5 seconds; level replacement clears them in code. Runtime verification distinguishes receiving metadata, submitting marker geometry, requesting a freeze and actually holding a displayed sample. The last source's 2.5-second diagnostic lifetime is longer than the animation freeze itself.

Prior hidden ordinary-input runs used the active Sodium renderer, right main arm, slim player model, third-person rear view, FOV 95 and a 20 FPS cap. Each recorded client/server attack outcomes, submitted marker and normal geometry, equality between a received contact and the retained hit-stop source, and an actually held presentation sample:

| Run under the evidence root | Scene / frames | Received contacts |
|---|---|---|
| `native-20261015-172258350169/hitstop` | Mace hit-stop / 139 | 1 |
| `native-20261015-172258350169/riposte` | Sword riposte / 138 | 2 |
| `native-20261015-172809204295/riposte` | Sword riposte after HUD wrapping / 139 | 2 |
| `native-20261015-174011439437/riposte` | Final merged sword riposte, calibrated two-handed grip enabled / 139 | 2 |

Each `capture.json` ends with zero retained/drawn markers, no source within its metadata lifetime and no active presentation freeze, verifying natural expiry in these fixtures. The first three runs precede the effects-disabled fix. The final merged run uses the built-in Gradle probe mapping, shipped clips and the verified stock iron-sword attachment; it records the source hashes and restores QA configuration. Its 139 frames independently pass the same metadata/draw/source/freeze/expiry checks, and `frame_0045.png` shows that the wrapped readout fits the 854×480 window. All captured windows stayed hidden with the mouse released. These 555 diagnostic frames do not prove anatomical alignment or a full visual/motion gate; markers can be occluded by actors from this rear view.

The final capture reports runtime camera motion 1.0 despite the external helper's requested configuration value; use the recorded runtime conditions, not that request, when comparing it. It is a contact/lifetime fixture, not an evenly timed animation comparison.

World replacement has not been exercised at runtime. Real remote latency/ordering, first-person and broader renderer/model combinations remain unsupported by these captures. Deliverable B's anatomical narrow phase and the full six-step goal remain open.
