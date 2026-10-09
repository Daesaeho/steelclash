package com.steelclash.core;

/**
 * Chivalry 2-style stamina: spent by defending and whiffing, regenerates after a short pause.
 * <p>
 * Reaching zero is a state, not just an empty bar: the fighter is <b>exhausted</b> until stamina has regenerated back to
 * {@link #DEFAULT_RECOVER_FRACTION} of the pool (or the fraction given to {@link #tick(float, int, float)}). While
 * exhausted, a blocked blow breaks the guard and stamina-paid tricks (feints, morphs, dodges) are refused.
 */
public final class Stamina {
    /** Share of the pool an exhausted fighter has to regenerate before they're no longer exhausted. */
    public static final float DEFAULT_RECOVER_FRACTION = 0.25f;

    private float max;
    private float current;
    private int ticksSinceSpent;
    private boolean exhausted;

    public Stamina(float max) {
        this.max = max;
        this.current = max;
        this.ticksSinceSpent = Integer.MAX_VALUE / 2;
    }

    /**
     * Spends stamina.
     *
     * @return true if this exhausted the pool: there wasn't enough to pay in full. For a parry this means disarm.
     */
    public boolean spend(float amount) {
        if (amount <= 0) {
            return false;
        }
        ticksSinceSpent = 0;
        boolean drained = amount >= current;
        current = Math.max(0, current - amount);
        if (current <= 0) {
            exhausted = true;
        }
        return drained;
    }

    public void tick(float regenPerTick, int regenDelayTicks) {
        tick(regenPerTick, regenDelayTicks, DEFAULT_RECOVER_FRACTION);
    }

    /** @param recoverFraction share of the pool an exhausted fighter must regain to stop being exhausted */
    public void tick(float regenPerTick, int regenDelayTicks, float recoverFraction) {
        if (ticksSinceSpent < Integer.MAX_VALUE / 2) {
            ticksSinceSpent++;
        }
        if (ticksSinceSpent >= regenDelayTicks && current < max) {
            current = Math.min(max, current + regenPerTick);
        }
        if (exhausted && current >= max * recoverFraction) {
            exhausted = false;
        }
    }

    /** Ran out of stamina and hasn't recovered enough yet. */
    public boolean isExhausted() {
        return exhausted;
    }

    /** The server's word on it (clients), or a test fixture. */
    public void setExhausted(boolean value) {
        exhausted = value;
    }

    public float current() {
        return current;
    }

    public float max() {
        return max;
    }

    /** Sets the pool directly (a fixture, a sync, a revive): empty means exhausted, anything else clears it. */
    public void set(float value) {
        current = Math.max(0, Math.min(max, value));
        exhausted = current <= 0;
    }

    public void setMax(float newMax) {
        max = Math.max(1, newMax);
        current = Math.min(current, max);
    }
}
