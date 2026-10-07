package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DefenseMathTest {
    @Test
    void attackerInFrontIsInCone() {
        // defender faces south (+z)
        assertTrue(Guard.inCone(0, 0, 0, 0, 3, 140));
    }

    @Test
    void attackerBehindIsOutsideCone() {
        assertFalse(Guard.inCone(0, 0, 0, 0, -3, 140));
    }

    @Test
    void coneEdgeRespectsWidth() {
        // attacker 60° to the side
        double x = -Math.sin(Math.toRadians(60)) * 3;
        double z = Math.cos(Math.toRadians(60)) * 3;
        assertTrue(Guard.inCone(0, 0, 0, x, z, 140), "60° is inside a 140° cone");
        assertFalse(Guard.inCone(0, 0, 0, x, z, 100), "60° is outside a 100° cone");
    }

    @Test
    void staminaSpendAndExhaust() {
        Stamina s = new Stamina(100);
        assertFalse(s.spend(30));
        assertEquals(70, s.current(), 1e-6);
        assertTrue(s.spend(70), "spending exactly what's left exhausts");
        assertEquals(0, s.current(), 1e-6);
    }

    @Test
    void staminaRegensOnlyAfterDelay() {
        Stamina s = new Stamina(100);
        s.spend(50);
        for (int i = 0; i < 9; i++) {
            s.tick(5, 10);
        }
        assertEquals(50, s.current(), 1e-6, "still within regen delay");
        s.tick(5, 10);
        assertEquals(55, s.current(), 1e-6);
        for (int i = 0; i < 100; i++) {
            s.tick(5, 10);
        }
        assertEquals(100, s.current(), 1e-6, "caps at max");
    }

    @Test
    void counterWindowShrinksAgainstFasterWeapons() {
        org.junit.jupiter.api.Assertions.assertEquals(7, Guard.counterWindow(7, Guard.COUNTER_REFERENCE_WINDUP_US), "sword: the base window");
        org.junit.jupiter.api.Assertions.assertTrue(Guard.counterWindow(7, 7 * AttackTimings.TICK_US) < 7, "dagger: smaller");
        org.junit.jupiter.api.Assertions.assertTrue(Guard.counterWindow(7, 13 * AttackTimings.TICK_US) > 7, "greatsword: bigger");
        org.junit.jupiter.api.Assertions.assertEquals(5, Guard.counterWindow(7, 1 * AttackTimings.TICK_US), "never below 70%");
        org.junit.jupiter.api.Assertions.assertEquals(9, Guard.counterWindow(7, 40 * AttackTimings.TICK_US), "never above 130%");
    }
}
