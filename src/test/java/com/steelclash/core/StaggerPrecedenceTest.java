package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StaggerPrecedenceTest {
    @Test
    void weakerHitCannotRelaxOrShortenGuardBreak() {
        var machine = new CombatStateMachine();
        machine.stagger(24, false);
        machine.tick();
        machine.stagger(8, true);
        assertFalse(machine.canParry(), "a later special cannot open the kicked guard");
        assertFalse(machine.predictionState().staggerAllowsParry());
        assertEquals(23L * AttackTimings.TICK_US, remaining(machine));
        assertEquals(AttackTimings.TICK_US, machine.phaseElapsedUs(), "ignored effects do not restart the visual clock");
        for (int i = 0; i < 23; i++) machine.tick();
        assertEquals(Phase.IDLE, machine.phase());
        assertTrue(machine.canParry());
    }

    @Test
    void longerSoftEffectDoesNotExtendHardLockout() {
        var machine = new CombatStateMachine();
        machine.stagger(10, false);
        machine.tick();
        machine.stagger(40, true);
        assertEquals(9L * AttackTimings.TICK_US, remaining(machine));
        assertFalse(machine.canParry());
    }

    @Test
    void sameStrengthNeverShortensRemainingStaggerAndCanExtendIt() {
        for (boolean soft : new boolean[]{false, true}) {
            var machine = new CombatStateMachine();
            machine.stagger(20, soft);
            machine.tick();
            machine.stagger(4, soft);
            assertEquals(19L * AttackTimings.TICK_US, remaining(machine));
            assertEquals(AttackTimings.TICK_US, machine.phaseElapsedUs());
            machine.stagger(25, soft);
            assertEquals(25L * AttackTimings.TICK_US, remaining(machine));
            assertEquals(soft, machine.canParry());
        }
    }

    private static long remaining(CombatStateMachine machine) {
        return machine.phaseDurationUs() - machine.phaseElapsedUs();
    }
}
