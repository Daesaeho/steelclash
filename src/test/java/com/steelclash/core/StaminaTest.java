package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StaminaTest {
    @Test
    void runningOutLeavesYouExhaustedUntilYouRecoverAQuarter() {
        Stamina s = new Stamina(100);
        assertFalse(s.spend(60), "60 of 100 is paid in full");
        assertFalse(s.isExhausted(), "spending isn't exhaustion");
        assertTrue(s.spend(50), "the rest can't be paid");
        assertTrue(s.isExhausted(), "hitting zero exhausts");
        for (int i = 0; i < 24; i++) {
            s.tick(1, 0, 0.25f);
        }
        assertTrue(s.isExhausted(), "still exhausted at 24 of 100");
        s.tick(1, 0, 0.25f);
        assertFalse(s.isExhausted(), "a quarter of the pool back ends it");
    }

    @Test
    void exhaustionWaitsForTheRegenDelay() {
        Stamina s = new Stamina(100);
        s.spend(100);
        for (int i = 0; i < 40; i++) {
            s.tick(5, 50, 0.25f);
        }
        assertTrue(s.isExhausted(), "nothing regenerates during the delay, so it lasts");
    }

    @Test
    void settingThePoolDecidesExhaustion() {
        Stamina s = new Stamina(100);
        s.set(0);
        assertTrue(s.isExhausted(), "an empty pool is exhausted");
        s.set(10);
        assertFalse(s.isExhausted(), "a set value above zero is a fresh state (a sync or a revive)");
        s.setExhausted(true);
        assertTrue(s.isExhausted(), "the server's word overrides");
    }
}
