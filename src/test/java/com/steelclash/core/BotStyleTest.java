package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BotStyleTest {
    @Test
    void footworkFollowsTheWeights() {
        Map<BotStyle.Footwork, Integer> counts = new EnumMap<>(BotStyle.Footwork.class);
        int samples = 10_000;
        for (int i = 0; i < samples; i++) {
            counts.merge(BotStyle.BRUTE.chooseFootwork(i / (double) samples), 1, Integer::sum);
        }
        // BRUTE: hold 0.7, circle 0.2, backpedal 0.1, feint step 0
        assertEquals(0.7, counts.getOrDefault(BotStyle.Footwork.HOLD, 0) / (double) samples, 0.01);
        assertEquals(0.2, counts.getOrDefault(BotStyle.Footwork.CIRCLE, 0) / (double) samples, 0.01);
        assertEquals(0, counts.getOrDefault(BotStyle.Footwork.FEINT_STEP, 0), "zero weight is never chosen");
    }

    @Test
    void stylesByName() {
        assertEquals(BotStyle.SHAMBLER, BotStyle.byName("shambler"));
        assertEquals(BotStyle.RUSHER, BotStyle.byName("RUSHER"));
        assertEquals(BotStyle.DUELIST, BotStyle.byName("unknown"), "unknown falls back to duelist");
    }

    @Test
    void jitterIsDeterministicBoundedAndVaried() {
        assertEquals(BotStyle.jitter(42), BotStyle.jitter(42));
        assertNotEquals(BotStyle.jitter(42), BotStyle.jitter(43));
        for (long seed = -500; seed < 500; seed++) {
            double j = BotStyle.jitter(seed);
            assertTrue(j >= -1 && j <= 1, "jitter in [-1, 1]");
        }
    }

    @Test
    void shamblersAreMoreRelentlessThanDuelists() {
        assertTrue(BotStyle.SHAMBLER.cooldownMult() < BotStyle.DUELIST.cooldownMult());
        assertTrue(BotStyle.SHAMBLER.holdExtra() < BotStyle.DUELIST.holdExtra());
    }
}
