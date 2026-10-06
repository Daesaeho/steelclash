package com.steelclash.core;

/**
 * Latency compensation maths. Inputs in milliseconds (round-trip latency, as the server measures it); results in game
 * ticks (50 ms).
 * <p>
 * A client runs its own actions one way ahead of the server and shows the world one way plus its interpolation behind
 * it. Seen from the server, a lagged player therefore acts on a world a whole round trip (plus interpolation) old:
 * <ul>
 *     <li><b>Rewind</b> (attacker side): when a lagged player's swing is traced, targets are moved back to where that
 *     player saw them.</li>
 *     <li><b>Grace</b> (defender side): a lagged player sees an incoming swing a one-way delay late and their parry
 *     arrives a one-way delay late, so hits on them are held up to a round trip before landing.</li>
 * </ul>
 */
public final class LagMath {
    public static final int MS_PER_TICK = 50;

    private LagMath() {
    }

    /** How many ticks to rewind targets for an attacker with this round-trip latency. */
    public static int rewindTicks(int latencyMs, int interpolationTicks, int maxMs) {
        if (latencyMs <= 0) {
            return 0;
        }
        int ms = Math.min(maxMs, latencyMs + interpolationTicks * MS_PER_TICK);
        return Math.max(0, Math.round(ms / (float) MS_PER_TICK));
    }

    /** One-way delay in ticks (half the round trip, rounded): how stale a server snapshot is when it arrives. */
    public static int oneWayTicks(int latencyMs) {
        return latencyMs <= 0 ? 0 : Math.round(latencyMs / 2f / MS_PER_TICK);
    }

    /** How long to hold a hit on a lagged defender so their parry input can still arrive (0 = apply at once). */
    public static int graceTicks(int latencyMs, int maxMs) {
        if (latencyMs <= 0) {
            return 0;
        }
        int ms = Math.min(maxMs, latencyMs + MS_PER_TICK / 2);
        return Math.max(0, Math.round(ms / (float) MS_PER_TICK));
    }
}
