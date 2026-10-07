package com.steelclash.core;

/**
 * Phase lengths in integer microseconds, so weapons keep their exact timings instead of collapsing onto 50 ms game
 * ticks (a 370 ms windup and a 350 ms one stay different). The state machine resolves phase boundaries inside a tick
 * (see {@link CombatStateMachine#tick}). Every phase lasts at least {@link #MIN_US}.
 *
 * @param windupUs   windup length, microseconds
 * @param releaseUs  release (blade live) length, microseconds
 * @param recoveryUs recovery length, microseconds
 */
public record AttackTimings(int windupUs, int releaseUs, int recoveryUs) {
    /** One game tick (20 per second). */
    public static final int TICK_US = 50_000;
    /** Longest phase a profile may define: 200 ticks (10 s). */
    public static final int MAX_TICKS = 200;
    public static final int MAX_US = MAX_TICKS * TICK_US;
    /** Shortest phase: 1 ms. */
    public static final int MIN_US = 1_000;

    public AttackTimings {
        windupUs = clamp(windupUs);
        releaseUs = clamp(releaseUs);
        recoveryUs = clamp(recoveryUs);
    }

    /** Timings given in game ticks (the original profile format). */
    public static AttackTimings ofTicks(int windup, int release, int recovery) {
        return new AttackTimings(windup * TICK_US, release * TICK_US, recovery * TICK_US);
    }

    /** Timings given in milliseconds. */
    public static AttackTimings ofMillis(int windup, int release, int recovery) {
        return new AttackTimings(windup * 1000, release * 1000, recovery * 1000);
    }

    /** Windup in whole ticks (rounded), for code that thinks in ticks: AI reaction times, HUDs. */
    public int windup() {
        return toTicks(windupUs);
    }

    public int release() {
        return toTicks(releaseUs);
    }

    public int recovery() {
        return toTicks(recoveryUs);
    }

    /** Whole attack in ticks (rounded). */
    public int total() {
        return toTicks((long) windupUs + releaseUs + recoveryUs);
    }

    public AttackTimings withWindupUs(long newWindupUs) {
        return new AttackTimings((int) Math.min(Integer.MAX_VALUE, newWindupUs), releaseUs, recoveryUs);
    }

    /**
     * Scales the timings for a wielder whose attack speed differs from the speed the profile was tuned for.
     * Faster wielders get shorter phases. {@code exponent} softens the effect (0 = ignore attack speed, 1 = fully
     * proportional), so attribute modifiers like Spartan Weaponry's heavy/lightweight traits nudge timings without
     * wrecking the readability of an attack. Scaled in microseconds: no rounding to ticks.
     */
    public AttackTimings scaledForSpeed(double attackSpeed, double referenceSpeed, double exponent) {
        if (attackSpeed <= 0 || referenceSpeed <= 0 || exponent == 0) {
            return this;
        }
        double factor = Math.pow(referenceSpeed / attackSpeed, exponent);
        factor = Math.max(0.5, Math.min(2.0, factor));
        return new AttackTimings(
                (int) Math.round(windupUs * factor),
                (int) Math.round(releaseUs * factor),
                (int) Math.round(recoveryUs * factor));
    }

    /** Ticks for a duration in microseconds, rounded to the nearest tick, at least 1. */
    public static int toTicks(long us) {
        return (int) Math.max(1, Math.round(us / (double) TICK_US));
    }

    private static int clamp(int us) {
        return Math.max(MIN_US, Math.min(MAX_US, us));
    }
}
