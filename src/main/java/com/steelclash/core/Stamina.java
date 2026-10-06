package com.steelclash.core;

/** Chivalry 2-style stamina: spent by defending and whiffing, regenerates after a short pause. */
public final class Stamina {
    private float max;
    private float current;
    private int ticksSinceSpent;

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
        boolean exhausted = amount >= current;
        current = Math.max(0, current - amount);
        return exhausted;
    }

    public void tick(float regenPerTick, int regenDelayTicks) {
        if (ticksSinceSpent < Integer.MAX_VALUE / 2) {
            ticksSinceSpent++;
        }
        if (ticksSinceSpent >= regenDelayTicks && current < max) {
            current = Math.min(max, current + regenPerTick);
        }
    }

    public float current() {
        return current;
    }

    public float max() {
        return max;
    }

    public void set(float value) {
        current = Math.max(0, Math.min(max, value));
    }

    public void setMax(float newMax) {
        max = Math.max(1, newMax);
        current = Math.min(current, max);
    }
}
