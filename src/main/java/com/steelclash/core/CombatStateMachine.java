package com.steelclash.core;

import org.jetbrains.annotations.Nullable;

/**
 * The combat lifecycle shared by players and mobs.
 * <pre>
 * attack:  IDLE → WINDUP → RELEASE → RECOVERY → IDLE
 *            WINDUP: feint → IDLE, morph → WINDUP (other type), heavy → longer WINDUP, counter → shorter WINDUP
 *            RECOVERY after a landed hit: attack again immediately (combo)
 * parry:   IDLE/RECOVERY → PARRY (catches any number of hits while up; each opens the riposte window)
 *                            → attack = riposte straight out of the guard (or, before a catch, a counter attempt)
 *                            → (release/timeout) IDLE if it caught something, else GUARD_RECOVERY → IDLE
 * stagger: any → STAGGER → IDLE   (parried, shield-blocked, flinched, kicked, clanked, guard broken)
 * </pre>
 * Ripostes and counters carry an <em>active parry</em> for a few ticks: hits from the front are parried while the
 * return attack winds up and swings. An attack started from the guard that is hit within the forgiveness window falls
 * back into the guard ({@link #forgiveIntoParry}).
 * Ticked once per game tick on the server (authoritative) and on clients (for visuals and prediction). During
 * RELEASE each tick yields a {@link Sweep}: the slice of release progress the blade moved through that tick.
 */
public final class CombatStateMachine {
    /** Release progress covered in one tick, both in [0, 1]. */
    public record Sweep(double from, double to) {
    }

    private Phase phase = Phase.IDLE;
    private AttackType type = AttackType.SLASH;
    private AttackTimings timings = new AttackTimings(1, 1, 1);
    private int phaseTick;
    private int phaseDuration;
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
        enter(Phase.WINDUP, attackTimings.windup());
        return true;
    }

    /** Turns the current windup into a heavy attack with the given total windup length. */
    public boolean makeHeavy(int heavyWindup) {
        if (phase != Phase.WINDUP || heavy) {
            return false;
        }
        heavy = true;
        timings = new AttackTimings(heavyWindup, timings.release(), timings.recovery());
        phaseDuration = Math.max(phaseTick + 1, heavyWindup);
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
        enter(Phase.WINDUP, newTimings.windup());
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
        phaseDuration = Math.min(phaseDuration, phaseTick + Math.max(1, ticksLeft));
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
        if (!isFromGuard() || phaseTick > windowTicks) {
            return false;
        }
        fromGuard = false;
        activeParryTicks = 0;
        guardRecovery = Math.max(1, recoveryTicks);
        parriedHits = 0;
        enter(Phase.PARRY, PARRY_HOLD_AFTER_HIT);
        return true;
    }

    /** The current swing landed a clean hit: attacking during recovery may skip it. */
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
            phaseDuration = Math.max(phaseDuration, phaseTick + PARRY_HOLD_AFTER_HIT);
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
     * Advances one tick.
     *
     * @return the blade sweep for this tick if the attack is in its release, otherwise {@code null}
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
        if (phase == Phase.IDLE) {
            return null;
        }
        phaseTick++;
        boolean done = phaseTick >= phaseDuration;
        switch (phase) {
            case WINDUP -> {
                if (done) {
                    enter(Phase.RELEASE, timings.release());
                }
            }
            case RELEASE -> {
                Sweep sweep = new Sweep((phaseTick - 1) / (double) phaseDuration,
                        Math.min(phaseTick, phaseDuration) / (double) phaseDuration);
                if (done) {
                    enter(Phase.RECOVERY, timings.recovery());
                }
                return sweep;
            }
            case PARRY -> {
                if (done) {
                    endParry();
                }
            }
            default -> {
                if (done) {
                    enter(Phase.IDLE, 0);
                }
            }
        }
        return null;
    }

    /** Overwrites local state with an authoritative snapshot (client sync). */
    public void apply(Phase newPhase, AttackType newType, int newPhaseTick, int newPhaseDuration,
                      AttackTimings newTimings, int newRiposteTicks, boolean newHeavy, boolean newMorphed,
                      boolean newComboAllowed, int newVariant, boolean newMirrored) {
        this.variant = newVariant;
        this.mirrored = newMirrored;
        this.phase = newPhase;
        this.type = newType;
        this.phaseTick = newPhaseTick;
        this.phaseDuration = newPhaseDuration;
        this.timings = newTimings;
        this.riposteTicks = newRiposteTicks;
        this.heavy = newHeavy;
        this.morphed = newMorphed;
        this.comboAllowed = newComboAllowed;
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
        if (phaseDuration <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, (phaseTick + partialTick) / phaseDuration));
    }

    private void enter(Phase next, int duration) {
        phase = next;
        phaseTick = 0;
        phaseDuration = duration;
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

    public int phaseTick() {
        return phaseTick;
    }

    public int phaseDuration() {
        return phaseDuration;
    }

    public int ticksLeftInPhase() {
        return Math.max(0, phaseDuration - phaseTick);
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
