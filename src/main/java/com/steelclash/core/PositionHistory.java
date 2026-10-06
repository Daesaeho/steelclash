package com.steelclash.core;

/** A short ring buffer of an entity's recent positions, one per game tick, for lag-compensated hit detection. */
public final class PositionHistory {
    private final long[] ticks;
    private final double[] xs;
    private final double[] ys;
    private final double[] zs;
    private int next;
    private int size;

    public PositionHistory(int capacity) {
        ticks = new long[capacity];
        xs = new double[capacity];
        ys = new double[capacity];
        zs = new double[capacity];
    }

    public void record(long tick, double x, double y, double z) {
        if (size > 0) {
            int last = Math.floorMod(next - 1, ticks.length);
            if (ticks[last] == tick) {
                xs[last] = x;
                ys[last] = y;
                zs[last] = z;
                return;
            }
        }
        ticks[next] = tick;
        xs[next] = x;
        ys[next] = y;
        zs[next] = z;
        next = (next + 1) % ticks.length;
        size = Math.min(size + 1, ticks.length);
    }

    /**
     * The position recorded at or just before {@code tick}; the oldest one if the history doesn't reach that far back,
     * or {@code null} if nothing has been recorded.
     */
    public Vec at(long tick) {
        if (size == 0) {
            return null;
        }
        Vec best = null;
        long bestTick = Long.MIN_VALUE;
        Vec oldest = null;
        long oldestTick = Long.MAX_VALUE;
        for (int i = 0; i < size; i++) {
            int idx = Math.floorMod(next - 1 - i, ticks.length);
            long t = ticks[idx];
            if (t <= tick && t > bestTick) {
                bestTick = t;
                best = new Vec(xs[idx], ys[idx], zs[idx]);
            }
            if (t < oldestTick) {
                oldestTick = t;
                oldest = new Vec(xs[idx], ys[idx], zs[idx]);
            }
        }
        return best != null ? best : oldest;
    }
}
