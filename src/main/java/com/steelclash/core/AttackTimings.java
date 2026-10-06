package com.steelclash.core;

/**
 * Phase lengths in game ticks (20 per second). Every phase lasts at least one tick.
 */
public record AttackTimings(int windup, int release, int recovery) {
    public static final int MAX_TICKS = 200;

    public AttackTimings {
        windup = clamp(windup);
        release = clamp(release);
        recovery = clamp(recovery);
    }

    public int total() {
        return windup + release + recovery;
    }

    /**
     * Scales the timings for a wielder whose attack speed differs from the speed the profile was tuned for.
     * Faster wielders get shorter phases. {@code exponent} softens the effect (0 = ignore attack speed, 1 = fully
     * proportional), so attribute modifiers like Spartan Weaponry's heavy/lightweight traits nudge timings without
     * wrecking the readability of an attack.
     */
    public AttackTimings scaledForSpeed(double attackSpeed, double referenceSpeed, double exponent) {
        if (attackSpeed <= 0 || referenceSpeed <= 0 || exponent == 0) {
            return this;
        }
        double factor = Math.pow(referenceSpeed / attackSpeed, exponent);
        factor = Math.max(0.5, Math.min(2.0, factor));
        return new AttackTimings(
                (int) Math.round(windup * factor),
                (int) Math.round(release * factor),
                (int) Math.round(recovery * factor));
    }

    private static int clamp(int ticks) {
        return Math.max(1, Math.min(MAX_TICKS, ticks));
    }
}
