package com.steelclash.core;

/** Presentation-only envelope for raising/lowering a carried consumable; reversals retain the displayed weight. */
public final class UsePoseBlend {
    public static final long DURATION_NANOS = 250_000_000L;
    private double from, target;
    private long changedAt;

    public double update(boolean using, long now) {
        double next = using ? 1 : 0;
        if (next != target) {
            from = weight(now);
            target = next;
            changedAt = now;
        }
        return weight(now);
    }

    public double weight(long now) {
        double t = Math.max(0, Math.min(1, (now - changedAt) / (double) DURATION_NANOS));
        double eased = t * t * (3 - 2 * t);
        return from + (target - from) * eased;
    }

    public void reset() { from = target = 0; changedAt = 0; }
}
