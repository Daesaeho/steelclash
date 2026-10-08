package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class VisualFrameTest {
    @Test
    void fractionalSampleCrossesWindupWithoutMutatingTheSimulation() {
        CombatStateMachine machine = new CombatStateMachine();
        machine.startAttack(AttackType.SLASH, AttackTimings.ofMillis(370, 180, 300));
        for (int i = 0; i < 7; i++) machine.tick();
        var before = machine.predictionState();
        var frame = machine.visualFrame(0.8f);
        assertEquals(Phase.RELEASE, frame.phase());
        assertEquals(20_000, frame.elapsedUs());
        assertEquals(20.0 / 180, frame.progress(), 1e-9);
        assertEquals(Phase.WINDUP, machine.phase());
        assertEquals(350_000, machine.phaseElapsedUs());
        assertEquals(before, machine.predictionState());
        assertFalse(machine.isComboAllowed());
    }

    @Test
    void oneSampleCanCrossEveryAttackPhase() {
        CombatStateMachine machine = new CombatStateMachine();
        machine.startAttack(AttackType.STAB, AttackTimings.ofMillis(10, 15, 10));
        var recovery = machine.visualFrame(0.6f);
        assertEquals(Phase.RECOVERY, recovery.phase());
        assertEquals(5_000, recovery.elapsedUs());
        assertEquals(Phase.IDLE, machine.visualFrame(1).phase());
        assertEquals(Phase.WINDUP, machine.phase());
    }

    @Test
    void fullTickSamplesAgreeWithTheNextSimulationTick() {
        Random random = new Random(27);
        for (int run = 0; run < 300; run++) {
            CombatStateMachine machine = new CombatStateMachine();
            machine.startAttack(AttackType.SLASH, AttackTimings.ofMillis(
                    1 + random.nextInt(700), 1 + random.nextInt(500), 1 + random.nextInt(700)));
            for (int tick = 0; tick < 40; tick++) {
                var frame = machine.visualFrame(1);
                machine.tick();
                assertEquals(machine.phase(), frame.phase());
                assertEquals(machine.phaseElapsedUs(), frame.elapsedUs());
                assertEquals(machine.phaseDurationUs(), frame.durationUs());
            }
        }
    }

    @Test
    void parryTimeoutChoosesTheSameNextPhaseAsGameplay() {
        CombatStateMachine machine = new CombatStateMachine();
        assertTrue(machine.startParry(3, 2, 1));
        machine.tick();
        machine.tick();
        assertEquals(Phase.GUARD_RECOVERY, machine.visualFrame(1).phase());
        assertEquals(Phase.PARRY, machine.phase());
        machine.parrySucceeded(3);
        for (int i = 0; i < 3; i++) machine.tick();
        assertEquals(Phase.IDLE, machine.visualFrame(1).phase());
        assertEquals(0, machine.visualFrame(1).durationUs());
        assertEquals(Phase.PARRY, machine.phase());
    }

    @Test
    void partialTicksAreBoundedAndStaggersTerminate() {
        CombatStateMachine machine = new CombatStateMachine();
        machine.stagger(1, false);
        assertEquals(machine.visualFrame(0), machine.visualFrame(-4));
        assertEquals(machine.visualFrame(1), machine.visualFrame(4));
        assertEquals(Phase.IDLE, machine.visualFrame(1).phase());
        assertEquals(Phase.STAGGER, machine.phase());
    }
}
