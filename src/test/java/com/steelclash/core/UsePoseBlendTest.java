package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class UsePoseBlendTest {
    @Test void raisingAndLoweringHaveNoEndpointJump() {
        var blend = new UsePoseBlend();
        long start = 1_000_000_000L, duration = UsePoseBlend.DURATION_NANOS;
        assertEquals(0, blend.update(true, start), 0);
        assertEquals(0.5, blend.update(true, start + duration / 2), 1e-12);
        assertEquals(1, blend.update(true, start + duration), 0);
        assertEquals(1, blend.update(false, start + duration), 0);
        assertEquals(0.5, blend.update(false, start + duration * 3 / 2), 1e-12);
        assertEquals(0, blend.update(false, start + duration * 2), 0);
    }

    @Test void cancellationAndRestartContinueFromTheLastWeight() {
        var blend = new UsePoseBlend();
        long start = 3_000_000_000L, duration = UsePoseBlend.DURATION_NANOS;
        blend.update(true, start);
        long cancel = start + duration / 3;
        double before = blend.weight(cancel);
        assertTrue(before > 0 && before < 1);
        assertEquals(before, blend.update(false, cancel), 0);
        assertTrue(blend.weight(cancel + duration / 4) < before);
        long restart = cancel + duration / 4;
        double lowered = blend.weight(restart);
        assertEquals(lowered, blend.update(true, restart), 0);
        assertTrue(blend.weight(restart + duration / 4) > lowered);
    }

    @Test void samplingFrequencyDoesNotChangeThePoseAndResetClearsIt() {
        var dense = new UsePoseBlend(); var sparse = new UsePoseBlend();
        long start = 5_000_000_000L;
        dense.update(true, start); sparse.update(true, start);
        for (int i = 1; i <= 20; i++) dense.update(true, start + i * 10_000_000L);
        assertEquals(dense.weight(start + 200_000_000L), sparse.update(true, start + 200_000_000L), 0);
        dense.reset();
        assertEquals(0, dense.weight(start + 200_000_000L), 0);
        assertEquals(0, dense.update(true, start + 200_000_000L), 0);
    }
}
