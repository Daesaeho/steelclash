package com.steelclash.core;

/**
 * Caps how many combat inputs one client gets through per server tick. A real client sends a handful at most (press,
 * heavy, block); a modified one spamming inputs would otherwise make the server re-sync combat state to everyone
 * around them over and over.
 */
public final class InputLimit {
    public static final int MAX_PER_TICK = 8;

    private long tick = Long.MIN_VALUE;
    private int count;

    /** Counts one input arriving during {@code gameTime}; false once this tick's budget is spent. */
    public boolean allow(long gameTime) {
        if (gameTime != tick) {
            tick = gameTime;
            count = 0;
        }
        return ++count <= MAX_PER_TICK;
    }
}
