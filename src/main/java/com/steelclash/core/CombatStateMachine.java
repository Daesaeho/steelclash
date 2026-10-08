package com.steelclash.core;

import org.jetbrains.annotations.Nullable;

/**
 * The combat lifecycle shared by players and mobs.
 * <pre>
 * attack:  IDLE → WINDUP → RELEASE → RECOVERY → IDLE
 *            WINDUP: feint → IDLE, morph → WINDUP (other type), heavy → longer WINDUP, counter → shorter WINDUP,
 *                    counter-feint → WINDUP (re-matching an incoming attack, once, even after a morph)
 *            RECOVERY after an unblocked slash, overhead or stab (hit or whiff): attack again immediately (combo)
 * parry:   IDLE/RECOVERY → PARRY (catches any number of hits while up; each opens the riposte window)
 *                            → attack = riposte straight out of the guard (or, before a catch, a counter attempt)
 *                            → (release/timeout) IDLE if it caught something, else GUARD_RECOVERY → IDLE
 * stagger: any → STAGGER → IDLE   (parried, shield-blocked, flinched, kicked, clanked, guard broken)
 * thwack:  RELEASE → RECOVERY early, at the contact, with the thwack recovery (hitstop: the blade stopped in a body)
 * </pre>
 * Ripostes and counters carry an <em>active parry</em> for a few ticks: hits from the front are parried while the
 * return attack winds up and swings. An attack started from the guard that is hit within the forgiveness window falls
 * back into the guard ({@link #forgiveIntoParry}).
 * Ticked once per game tick on the server (authoritative) and on clients (for visuals and prediction). Time inside
 * a phase is kept in microseconds and phase boundaries are resolved <em>inside</em> the tick (the architecture plan's
 * sub-tick timeline): an attack whose windup ends 18 ms into a tick spends the remaining 32 ms of that tick in its
 * release. During RELEASE each tick yields a {@link Sweep}: the slice of release progress the blade moved through
 * that tick, including a partial first or last slice. Timings that are whole ticks behave exactly as before.
 * Guards, staggers, cooldowns and windows are still counted in ticks.
 */
public final class CombatStateMachine {
    /** Release progress covered in one tick, both in [0, 1]. */
    public record Sweep(double from, double to) {
    }

    private Phase phase = Phase.IDLE;
    private AttackType type = AttackType.SLASH;
    private AttackTimings timings = AttackTimings.ofTicks(1, 1, 1);
    /** Time spent in the current phase and its length, microseconds. */
    private long phaseElapsedUs;
    private long phaseDurationUs;
    private int guardRecovery = 1;
    /** Ticks after a parry ends before another can be raised, and how many are left. */
    private int parryCooldown;
    private int parryCooldownLeft;
    /** Hits caught by the current parry. */
    private int parriedHits;
    /** After catching a hit, the parry stays up at least this many more ticks so a follow-up hit isn't missed. */
    private static final int PARRY_HOLD_AFTER_HIT = 4;
    private boolean staggerAllowsParry;
    private int riposteTicks;
    private int attackSerial;
    private boolean heavy;
    private boolean morphed;
    private boolean comboAllowed;
    /** Which of the attack's arc variants is being swung (0 = the profile's main arc). */
    private int variant;
    /** Swung from the other side (left to right). */
    private boolean mirrored;
    /** Ticks of active parry left (riposte or counter); only counts while attacking. */
    private int activeParryTicks;
    /** The current attack was started out of a raised guard (a riposte or a counter attempt). */
    private boolean fromGuard;
    /** The current windup has used its counter-feint. */
    private boolean counterFeinted;
    /** The current attack stopped in a body ({@link #thwack}). */
    private boolean thwacked;
    /** Release progress the recovery starts from: 1 normally, the contact point after a thwack. */
    private double recoverFrom = 1;

    public boolean canStartAttack() {
        // From a raised guard: a riposte after catching a hit, otherwise a counter attempt that drops the guard.
        return phase == Phase.IDLE || (phase == Phase.RECOVERY && comboAllowed) || phase == Phase.PARRY;
    }

    public boolean startAttack(AttackType attackType, AttackTimings attackTimings) {
        return startAttack(attackType, attackTimings, 0, false);
    }

    public boolean startAttack(AttackType attackType, AttackTimings attackTimings, int arcVariant, boolean mirror) {
        if (!canStartAttack()) {
            return false;
        }
        fromGuard = phase == Phase.PARRY;
        activeParryTicks = 0;
        if (phase == Phase.PARRY) {
            parryCooldownLeft = parryCooldown; // riposting out of the guard ends the parry
        }
        this.variant = Math.max(0, arcVariant);
        this.mirrored = mirror;
        this.type = attackType;
        this.timings = attackTimings;
        this.riposteTicks = 0;
        this.attackSerial++;
        this.heavy = false;
        this.morphed = false;
        this.comboAllowed = false;
        this.thwacked = false;
        this.recoverFrom = 1;
        this.counterFeinted = false;
        enterUs(Phase.WINDUP, attackTimings.windupUs());
        return true;
    }

    /** Turns the current windup into a heavy attack with the given total windup length, microseconds. */
    public boolean makeHeavy(int heavyWindupUs) {
        return makeHeavy(heavyWindupUs, timings.recoveryUs());
    }

    /** As above, with the heavy's own recovery length (Chivalry 2 heavies recover a little slower). */
    public boolean makeHeavy(int heavyWindupUs, int heavyRecoveryUs) {
        if (phase != Phase.WINDUP || heavy) {
            return false;
        }
        heavy = true;
        timings = timings.withWindupUs(heavyWindupUs).withRecoveryUs(heavyRecoveryUs);
        phaseDurationUs = Math.max(phaseElapsedUs + 1, timings.windupUs());
        return true;
    }

    /** Cancels a windup (Chivalry 2 feint). */
    public boolean feint() {
        if (phase != Phase.WINDUP) {
            return false;
        }
        enter(Phase.IDLE, 0);
        return true;
    }

    /** Switches the windup to a different attack, once per swing. The new attack winds up from the start. */
    public boolean morph(AttackType newType, AttackTimings newTimings) {
        return morph(newType, newTimings, 0, mirrored);
    }

    public boolean morph(AttackType newType, AttackTimings newTimings, int arcVariant, boolean mirror) {
        if (phase != Phase.WINDUP || morphed || newType == type) {
            return false;
        }
        variant = Math.max(0, arcVariant);
        mirrored = mirror;
        type = newType;
        timings = newTimings;
        morphed = true;
        heavy = false;
        enterUs(Phase.WINDUP, newTimings.windupUs());
        return true;
    }

    /**
     * Counter-feint (Chivalry 2): the attack being countered changed, so the counter changes with it. Like a morph the
     * windup starts over, but it is allowed once more after a morph, and may also switch to the other side of the same
     * attack (Chivalry 2's alternate counter: a second chance at the timing). The caller decides that an attack of the
     * new type is actually coming ({@code Combat.morph}).
     */
    public boolean counterFeint(AttackType newType, AttackTimings newTimings, int arcVariant, boolean mirror) {
        if (phase != Phase.WINDUP || counterFeinted || (newType == type && mirror == mirrored)) {
            return false;
        }
        counterFeinted = true;
        variant = Math.max(0, arcVariant);
        mirrored = mirror;
        type = newType;
        timings = newTimings;
        morphed = true;
        heavy = false;
        enterUs(Phase.WINDUP, newTimings.windupUs());
        return true;
    }

    /** The windup countered an incoming attack: it releases after at most {@code ticksLeft} more ticks. */
    public boolean counter(int ticksLeft) {
        return counter(ticksLeft, 0);
    }

    /** As {@link #counter(int)}, with an active parry of {@code activeParry} ticks for the rest of the swing. */
    public boolean counter(int ticksLeft, int activeParry) {
        if (phase != Phase.WINDUP) {
            return false;
        }
        phaseDurationUs = Math.min(phaseDurationUs, phaseElapsedUs + (long) Math.max(1, ticksLeft) * AttackTimings.TICK_US);
        activeParryTicks = Math.max(activeParryTicks, activeParry);
        return true;
    }

    /** Starts the active parry that comes with a riposte. */
    public void startActiveParry(int ticks) {
        if (isAttacking()) {
            activeParryTicks = Math.max(activeParryTicks, ticks);
        }
    }

    /** While attacking with an active parry: frontal hits are parried without dropping the attack. */
    public boolean isActiveParry() {
        return activeParryTicks > 0 && (phase == Phase.WINDUP || phase == Phase.RELEASE);
    }

    /** An active parry caught a hit: it lasts a little longer. */
    public void extendActiveParry(int ticks) {
        if (isActiveParry()) {
            activeParryTicks += Math.max(0, ticks);
        }
    }

    /** The current windup was started out of a raised guard (riposte or counter attempt). */
    public boolean isFromGuard() {
        return fromGuard && phase == Phase.WINDUP;
    }

    /**
     * Parry forgiveness: an attack started from the guard is hit within {@code windowTicks} of starting. Instead of
     * being hit, the fighter drops back into the guard (ignoring the parry cooldown), which then catches the hit.
     *
     * @return whether the guard is up again
     */
    public boolean forgiveIntoParry(int windowTicks, int recoveryTicks) {
        if (!isFromGuard() || phaseTick() > windowTicks) {
            return false;
        }
        fromGuard = false;
        activeParryTicks = 0;
        guardRecovery = Math.max(1, recoveryTicks);
        parriedHits = 0;
        enter(Phase.PARRY, PARRY_HOLD_AFTER_HIT);
        return true;
    }

    /**
     * Thwack (Chivalry 2 hitstop): the blade stopped in a body at release progress {@code at}. The rest of the release
     * is skipped and the thwack recovery, {@code thwackUs} long from the moment of contact, replaces the normal one.
     * Called in the tick the contact was traced, after {@link #tick} has advanced, so the release may already have run
     * out this tick; the time since the contact is counted into the thwack recovery.
     *
     * @return whether the swing thwacked (once per attack, only from the release or the recovery that just followed it)
     */
    public boolean thwack(int thwackUs, double at) {
        if (thwacked) {
            return false;
        }
        double contact = Math.max(0, Math.min(1, at));
        long sinceContact;
        if (phase == Phase.RELEASE) {
            sinceContact = phaseElapsedUs - Math.round(contact * phaseDurationUs);
        } else if (phase == Phase.RECOVERY) {
            sinceContact = Math.round((1 - contact) * timings.releaseUs()) + phaseElapsedUs;
        } else {
            return false;
        }
        thwacked = true;
        recoverFrom = contact;
        enterUs(Phase.RECOVERY, Math.max(1, thwackUs));
        phaseElapsedUs = Math.max(0, Math.min(sinceContact, phaseDurationUs));
        if (phaseElapsedUs >= phaseDurationUs) {
            endPhase();
        }
        return true;
    }

    /** The current swing landed a clean hit (a thwack ends the release early): attacking during recovery may skip it. */
    public void allowCombo() {
        if (phase == Phase.RELEASE || phase == Phase.RECOVERY) {
            comboAllowed = true;
        }
    }

    /** Free to raise a parry: not mid-attack or guard recovery, and the cooldown since the last parry has passed. */
    public boolean canParry() {
        return parryCooldownLeft == 0
                && (phase == Phase.IDLE || phase == Phase.RECOVERY || (phase == Phase.STAGGER && staggerAllowsParry));
    }

    /** Raises a weapon parry for at most {@code maxTicks}; lowering it costs {@code recoveryTicks}. */
    public boolean startParry(int maxTicks, int recoveryTicks) {
        return startParry(maxTicks, recoveryTicks, 0);
    }

    /** @param cooldownTicks after this parry ends (caught something, released or timed out), no new parry for this long */
    public boolean startParry(int maxTicks, int recoveryTicks, int cooldownTicks) {
        if (!canParry()) {
            return false;
        }
        this.parryCooldown = Math.max(0, cooldownTicks);
        this.guardRecovery = Math.max(1, recoveryTicks);
        this.parriedHits = 0;
        enter(Phase.PARRY, Math.max(1, maxTicks));
        return true;
    }

    /** Cancels a windup into a parry only if the parry can actually start. A rejected input leaves the attack intact. */
    public boolean cancelIntoParry(int maxTicks, int recoveryTicks, int cooldownTicks) {
        if (phase != Phase.WINDUP || parryCooldownLeft > 0) {
            return false;
        }
        feint();
        return startParry(maxTicks, recoveryTicks, cooldownTicks);
    }

    /** The player let go of block before anything was parried. */
    public void releaseParry() {
        if (phase == Phase.PARRY) {
            endParry();
        }
    }

    /** A parry that caught something drops straight to idle; one that caught nothing pays the guard recovery. */
    private void endParry() {
        parryCooldownLeft = parryCooldown;
        if (parriedHits > 0) {
            enter(Phase.IDLE, 0);
        } else {
            enter(Phase.GUARD_RECOVERY, guardRecovery);
        }
    }

    /**
     * The parry caught an attack. The guard stays up (it can catch more hits, e.g. from a second attacker) and the
     * riposte window opens; attacking now ripostes straight out of the guard.
     */
    public void parrySucceeded(int riposteWindow) {
        if (phase == Phase.PARRY) {
            parriedHits++;
            riposteTicks = Math.max(riposteTicks, Math.max(0, riposteWindow));
            phaseDurationUs = Math.max(phaseDurationUs, phaseElapsedUs + (long) PARRY_HOLD_AFTER_HIT * AttackTimings.TICK_US);
        }
    }

    /** @param allowParry whether the staggered fighter may still parry (true after being parried, false on guard break) */
    public void stagger(int ticks, boolean allowParry) {
        riposteTicks = 0;
        activeParryTicks = 0;
        staggerAllowsParry = allowParry;
        enter(Phase.STAGGER, Math.max(1, ticks));
    }

    public void cancel() {
        enter(Phase.IDLE, 0);
    }

    /**
     * Advances one tick (50 ms), crossing as many phase boundaries as fall inside it.
     *
     * @return the blade sweep for this tick if any of it was spent in the release, otherwise {@code null}
     */
    @Nullable
    public Sweep tick() {
        if (parryCooldownLeft > 0) {
            parryCooldownLeft--;
        }
        if (activeParryTicks > 0) {
            activeParryTicks = isAttacking() ? activeParryTicks - 1 : 0;
        }
        if ((phase == Phase.IDLE || phase == Phase.PARRY) && riposteTicks > 0) {
            riposteTicks--;
        }
        long remaining = AttackTimings.TICK_US;
        double sweepFrom = -1;
        double sweepTo = -1;
        while (remaining > 0 && phase != Phase.IDLE) {
            long step = Math.min(remaining, Math.max(0, phaseDurationUs - phaseElapsedUs));
            if (phase == Phase.RELEASE && phaseDurationUs > 0) { // a release is entered at most once per tick
                sweepFrom = phaseElapsedUs / (double) phaseDurationUs;
                sweepTo = (phaseElapsedUs + step) / (double) phaseDurationUs;
            }
            phaseElapsedUs += step;
            remaining -= step;
            if (phaseElapsedUs >= phaseDurationUs) {
                endPhase();
            }
        }
        return sweepFrom < 0 ? null : new Sweep(sweepFrom, Math.min(1, sweepTo));
    }

    /** The current phase has run its course: on to the next. */
    private void endPhase() {
        switch (phase) {
            case WINDUP -> enterUs(Phase.RELEASE, timings.releaseUs());
            case RELEASE -> {
                enterUs(Phase.RECOVERY, timings.recoveryUs());
                // Chivalry 2: any attack that ends unblocked can be comboed, a whiff included (a blocked one is
                // staggered instead and never gets here).
                if (type.isWeaponAttack()) {
                    comboAllowed = true;
                }
            }
            case PARRY -> endParry();
            default -> enter(Phase.IDLE, 0);
        }
    }

    /** Overwrites local state with an authoritative snapshot (client sync). */
    public void apply(Phase newPhase, AttackType newType, long newElapsedUs, long newDurationUs,
                      AttackTimings newTimings, int newRiposteTicks, boolean newHeavy, boolean newMorphed,
                      boolean newComboAllowed, int newVariant, boolean newMirrored) {
        apply(newPhase, newType, newElapsedUs, newDurationUs, newTimings, newRiposteTicks, newHeavy, newMorphed,
                newComboAllowed, newVariant, newMirrored, false, 1);
    }

    /** As above, with the thwack state. */
    public void apply(Phase newPhase, AttackType newType, long newElapsedUs, long newDurationUs,
                      AttackTimings newTimings, int newRiposteTicks, boolean newHeavy, boolean newMorphed,
                      boolean newComboAllowed, int newVariant, boolean newMirrored, boolean newThwacked,
                      double newRecoverFrom) {
        this.thwacked = newThwacked;
        this.recoverFrom = newThwacked ? Math.max(0, Math.min(1, newRecoverFrom)) : 1;
        this.variant = newVariant;
        this.mirrored = newMirrored;
        this.phase = newPhase;
        this.type = newType;
        this.phaseElapsedUs = newElapsedUs;
        this.phaseDurationUs = newDurationUs;
        this.timings = newTimings;
        this.riposteTicks = newRiposteTicks;
        this.heavy = newHeavy;
        this.morphed = newMorphed;
        this.comboAllowed = newComboAllowed;
    }

    /** State needed to continue an authoritative snapshot with the same parry and attack rules as the server. */
    public record PredictionState(int guardRecovery, int parryCooldown, int parryCooldownLeft, int parriedHits,
                                  boolean staggerAllowsParry, int activeParryTicks, boolean fromGuard,
                                  boolean counterFeinted, int attackSerial) {
    }

    public PredictionState predictionState() {
        return new PredictionState(guardRecovery, parryCooldown, parryCooldownLeft, parriedHits, staggerAllowsParry,
                activeParryTicks, fromGuard, counterFeinted, attackSerial);
    }

    /** Restores the lifecycle rules omitted by a phase/animation-only snapshot. */
    public void applyPredictionState(PredictionState state) {
        guardRecovery = state.guardRecovery();
        parryCooldown = state.parryCooldown();
        parryCooldownLeft = state.parryCooldownLeft();
        parriedHits = state.parriedHits();
        staggerAllowsParry = state.staggerAllowsParry();
        activeParryTicks = state.activeParryTicks();
        fromGuard = state.fromGuard();
        counterFeinted = state.counterFeinted();
        attackSerial = state.attackSerial();
    }

    /** Merges server-decided windows (combo, riposte) without disturbing the locally predicted phase. */
    public void applyWindows(int newRiposteTicks, boolean newComboAllowed) {
        this.riposteTicks = newRiposteTicks;
        if (newComboAllowed) {
            allowCombo();
        }
    }

    /** Progress through the current phase in [0, 1], interpolated within the tick. */
    public double phaseProgress(float partialTick) {
        if (phaseDurationUs <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, (phaseElapsedUs + partialTick * (double) AttackTimings.TICK_US) / phaseDurationUs));
    }

    /** Enters a phase lasting {@code ticks} game ticks. */
    private void enter(Phase next, long ticks) {
        enterUs(next, ticks * AttackTimings.TICK_US);
    }

    private void enterUs(Phase next, long durationUs) {
        phase = next;
        phaseElapsedUs = 0;
        phaseDurationUs = durationUs;
    }

    public Phase phase() {
        return phase;
    }

    public AttackType type() {
        return type;
    }

    public AttackTimings timings() {
        return timings;
    }

    /** Whole ticks spent in the current phase. */
    public int phaseTick() {
        return (int) (phaseElapsedUs / AttackTimings.TICK_US);
    }

    /** Length of the current phase in ticks, rounded up. */
    public int phaseDuration() {
        return (int) Math.min(Integer.MAX_VALUE, ceilTicks(phaseDurationUs));
    }

    /** Ticks until the current phase ends, rounded up. */
    public int ticksLeftInPhase() {
        return (int) Math.min(Integer.MAX_VALUE, ceilTicks(Math.max(0, phaseDurationUs - phaseElapsedUs)));
    }

    public long phaseElapsedUs() {
        return phaseElapsedUs;
    }

    public long phaseDurationUs() {
        return phaseDurationUs;
    }

    private static long ceilTicks(long us) {
        return (us + AttackTimings.TICK_US - 1) / AttackTimings.TICK_US;
    }

    public boolean isAttacking() {
        return phase.isAttack();
    }

    /** Anything other than standing idle (attacking, guarding, staggered). */
    public boolean isBusy() {
        return phase != Phase.IDLE;
    }

    public boolean isRiposteReady() {
        return (phase == Phase.IDLE || phase == Phase.PARRY) && riposteTicks > 0;
    }

    /** Hits caught by the current (or just-ended) parry. */
    public int parriedHits() {
        return parriedHits;
    }

    public int riposteTicks() {
        return riposteTicks;
    }

    public int parryCooldownLeft() {
        return parryCooldownLeft;
    }

    public boolean isHeavy() {
        return heavy;
    }

    public boolean isMorphed() {
        return morphed;
    }

    public boolean isComboAllowed() {
        return comboAllowed;
    }

    public boolean isCounterFeinted() {
        return counterFeinted;
    }

    /** The current attack stopped in a body and is in (or past) its thwack recovery. */
    public boolean isThwacked() {
        return thwacked;
    }

    /** Release progress the recovery returns from: the end of the arc, or where a thwack stopped the blade. */
    public double recoverFrom() {
        return recoverFrom;
    }

    public int variant() {
        return variant;
    }

    public boolean isMirrored() {
        return mirrored;
    }

    /** Increments with every attack started; lets observers react once per attack. */
    public int attackSerial() {
        return attackSerial;
    }
}
