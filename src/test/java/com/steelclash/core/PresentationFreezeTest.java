package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PresentationFreezeTest {
    @Test
    void anArbitraryNegativeClockOriginDoesNotStartAnUnrequestedPause() {
        PresentationFreeze<String> freeze = new PresentationFreeze<>();
        assertEquals("first", freeze.sample(1, -100, () -> "first"));
        assertEquals("second", freeze.sample(1, -90, () -> "second"));
        freeze.request(-80, 70);
        assertEquals("held", freeze.sample(1, -80, () -> "held"));
        assertEquals("held", freeze.sample(1, -20, () -> "live"));
        assertEquals("live", freeze.sample(1, -10, () -> "live"));
    }

    @Test
    void holdsTheFullSampleWhileSimulationCrossesATickBoundary() {
        CombatStateMachine machine = new CombatStateMachine();
        machine.startAttack(AttackType.SLASH, AttackTimings.ofTicks(8, 5, 9));
        machine.tick();
        machine.tick();
        PresentationFreeze<CombatStateMachine.VisualFrame> freeze = new PresentationFreeze<>();
        freeze.request(1_000_000_000L, 70_000_000L);
        var held = freeze.sample(machine.attackSerial(), 1_000_000_000L, () -> machine.visualFrame(0.6f));
        machine.tick();
        assertEquals(held, freeze.sample(machine.attackSerial(), 1_050_000_000L, () -> machine.visualFrame(0.6f)));
        assertTrue(freeze.holding(1_050_000_000L));
        assertEquals(machine.visualFrame(0.6f), freeze.sample(machine.attackSerial(), 1_070_000_000L, () -> machine.visualFrame(0.6f)));
        assertFalse(freeze.holding(1_070_000_000L));
    }

    @Test
    void newAttackCannotInheritAPreviousAttacksPause() {
        PresentationFreeze<String> freeze = new PresentationFreeze<>();
        freeze.request(100, 70);
        assertEquals("old", freeze.sample(1, 100, () -> "old"));
        assertEquals("new", freeze.sample(2, 120, () -> "new"));
        assertFalse(freeze.holding(120));
    }

    @Test
    void authoritativeCorrectionReplacesTheHeldSampleWithoutExtendingThePause() {
        PresentationFreeze<String> freeze = new PresentationFreeze<>();
        freeze.request(100, 70);
        freeze.sample(1, 100, () -> "release");
        freeze.corrected();
        assertEquals("stagger", freeze.sample(1, 150, () -> "stagger"));
        assertEquals("live", freeze.sample(1, 170, () -> "live"));
        assertFalse(freeze.holding(170));
    }

    @Test
    void zeroDurationAndNullSamplesAreHandledWithoutResampling() {
        PresentationFreeze<String> freeze = new PresentationFreeze<>();
        freeze.request(100, 0);
        assertEquals("live", freeze.sample(1, 100, () -> "live"));
        freeze.request(100, 70);
        assertNull(freeze.sample(1, 100, () -> null));
        assertNull(freeze.sample(1, 120, () -> { throw new AssertionError("held null must not be resampled"); }));
        freeze.clear();
        assertFalse(freeze.holding(120));
    }
}
