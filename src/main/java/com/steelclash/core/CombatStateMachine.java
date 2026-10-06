package com.steelclash.core;

import org.jetbrains.annotations.Nullable;

/**
 * The combat lifecycle shared by players and mobs.
 * <pre>
 * attack:  IDLE → WINDUP → RELEASE → RECOVERY → IDLE
 *            WINDUP: feint → IDLE, morph → WINDUP (other type), heavy → longer WINDUP, counter → shorter WINDUP
 *            RECOVERY after a landed hit: attack again immediately (combo)
 * parry:   IDLE/RECOVERY → PARRY → (success) IDLE + riposte window
 *                                → (release/timeout) GUARD_RECOVERY → IDLE
 * stagger: any → STAGGER → IDLE   (parried, shield-blocked, flinched, kicked, clanked, guard broken)
 * </pre>
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
    private boolean staggerAllowsParry;
    private int riposteTicks;
    private int attackSerial;
    private boolean heavy;
    private boolean morphed;
    private boolean comboAllowed;

    public boolean canStartAttack() {
        return phase == Phase.IDLE || (phase == Phase.RECOVERY && comboAllowed);
    }

    public boolean startAttack(AttackType attackType, AttackTimings attackTimings) {
        if (!canStartAttack()) {
            return false;
        }
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
        if (phase != Phase.WINDUP || morphed || newType == type) {
            return false;
        }
        type = newType;
        timings = newTimings;
        morphed = true;
        heavy = false;
        enter(Phase.WINDUP, newTimings.windup());
        return true;
    }

    /** The windup countered an incoming attack: it releases after at most {@code ticksLeft} more ticks. */
    public boolean counter(int ticksLeft) {
        if (phase != Phase.WINDUP) {
            return false;
        }
        phaseDuration = Math.min(phaseDuration, phaseTick + Math.max(1, ticksLeft));
        return true;
    }

    /** The current swing landed a clean hit: attacking during recovery may skip it. */
    public void allowCombo() {
        if (phase == Phase.RELEASE || phase == Phase.RECOVERY) {
            comboAllowed = true;
        }
    }

    public boolean canParry() {
        return phase == Phase.IDLE || phase == Phase.RECOVERY || phase == Phase.GUARD_RECOVERY
                || (phase == Phase.STAGGER && staggerAllowsParry);
    }

    /** Raises a weapon parry for at most {@code maxTicks}; lowering it costs {@code recoveryTicks}. */
    public boolean startParry(int maxTicks, int recoveryTicks) {
        if (!canParry()) {
            return false;
        }
        this.guardRecovery = Math.max(1, recoveryTicks);
        enter(Phase.PARRY, Math.max(1, maxTicks));
        return true;
    }

    /** The player let go of block before anything was parried. */
    public void releaseParry() {
        if (phase == Phase.PARRY) {
            enter(Phase.GUARD_RECOVERY, guardRecovery);
        }
    }

    /** The parry caught an attack: drop the guard and open the riposte window. */
    public void parrySucceeded(int riposteWindow) {
        if (phase == Phase.PARRY) {
            enter(Phase.IDLE, 0);
            riposteTicks = Math.max(0, riposteWindow);
        }
    }

    /** @param allowParry whether the staggered fighter may still parry (true after being parried, false on guard break) */
    public void stagger(int ticks, boolean allowParry) {
        riposteTicks = 0;
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
        if (phase == Phase.IDLE) {
            if (riposteTicks > 0) {
                riposteTicks--;
            }
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
                    enter(Phase.GUARD_RECOVERY, guardRecovery);
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
                      boolean newComboAllowed) {
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
        return phase == Phase.IDLE && riposteTicks > 0;
    }

    public int riposteTicks() {
        return riposteTicks;
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

    /** Increments with every attack started; lets observers react once per attack. */
    public int attackSerial() {
        return attackSerial;
    }
}
