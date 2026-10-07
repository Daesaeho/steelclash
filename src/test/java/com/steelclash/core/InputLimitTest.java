package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InputLimitTest {
    @Test
    void capsInputsPerTickAndResetsNextTick() {
        InputLimit limit = new InputLimit();
        for (int i = 0; i < InputLimit.MAX_PER_TICK; i++) {
            assertTrue(limit.allow(100), "input " + i + " fits the budget");
        }
        assertFalse(limit.allow(100), "the budget is spent for this tick");
        assertTrue(limit.allow(101), "a new tick, a new budget");
    }
}
